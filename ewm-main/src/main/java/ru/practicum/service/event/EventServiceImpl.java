package ru.practicum.service.event;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.EndpointHit;
import ru.practicum.ViewStats;
import ru.practicum.client.StatsClient;
import ru.practicum.dto.event.AdminStateAction;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.dto.event.EventSortValue;
import ru.practicum.dto.event.NewEventDto;
import ru.practicum.dto.event.UpdateEventAdminRequest;
import ru.practicum.dto.event.UpdateEventUserRequest;
import ru.practicum.exception.BadRequestException;
import ru.practicum.exception.CategoryNotFoundException;
import ru.practicum.exception.DataConflictException;
import ru.practicum.exception.EventNotFoundException;
import ru.practicum.exception.UserNotFoundException;
import ru.practicum.mapper.EventMapper;
import ru.practicum.model.Category;
import ru.practicum.model.Event;
import ru.practicum.model.EventState;
import ru.practicum.model.QEvent;
import ru.practicum.model.RequestStatus;
import ru.practicum.model.User;
import ru.practicum.storage.category.CategoryRepository;
import ru.practicum.storage.event.EventRepository;
import ru.practicum.storage.request.RequestRepository;
import ru.practicum.storage.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private static final long MIN_HOURS_BEFORE_EVENT = 2;
    private static final long MIN_HOURS_BEFORE_PUBLICATION = 1;
    private static final long STATISTICS_PERIOD_YEARS = 1;

    private static final int NO_PARTICIPANT_LIMIT = 0;
    private static final long DEFAULT_VIEWS = 0L;

    private static final String APP_NAME = "ewm-main-service";
    private static final String EVENT_URI_PREFIX = "/events/";

    private static final String USER_NOT_FOUND_MESSAGE =
            "Пользователя с таким id не существует";
    private static final String CATEGORY_NOT_FOUND_MESSAGE =
            "Категории с таким id не существует";
    private static final String EVENT_NOT_FOUND_MESSAGE =
            "Событие не найдено";
    private static final String EVENT_NOT_FOUND_BY_ID_MESSAGE =
            "События с таким id не существует";
    private static final String EVENT_TIME_VALIDATION_MESSAGE =
            "Ошибка валидации времени";

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final StatsClient statsClient;
    private final RequestRepository requestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public EventFullDto createEvent(NewEventDto newEventDto, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_MESSAGE));

        Category category = categoryRepository.findById(newEventDto.getCategory())
                .orElseThrow(() -> new CategoryNotFoundException(CATEGORY_NOT_FOUND_MESSAGE));

        if (newEventDto.getEventDate().isBefore(
                LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
            throw new BadRequestException(
                    "Событие не может начинаться раньше чем через два часа после его создания");
        }

        Event eventToSave = EventMapper.mapToEvent(newEventDto, category, user);
        eventRepository.save(eventToSave);

        return EventMapper.mapToEventFullDto(eventToSave);
    }

    @Override
    public List<EventFullDto> getEventsByInitiatorId(Long userId, int from, int size) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(USER_NOT_FOUND_MESSAGE);
        }

        List<Event> events = eventRepository.getEventByInitiatorId(userId, from, size);

        return events.stream()
                .map(EventMapper::mapToEventFullDto)
                .toList();
    }

    @Override
    public EventFullDto findEventByInitiatorIdAndEventId(Long userId, Long eventId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(USER_NOT_FOUND_MESSAGE);
        }

        Event event = eventRepository.findEventByInitiatorIdAndId(userId, eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_MESSAGE));

        return EventMapper.mapToEventFullDto(event);
    }

    @Override
    public EventFullDto findEventById(Long eventId, HttpServletRequest request) {
        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_BY_ID_MESSAGE));

        sendHit(request);

        String uri = EVENT_URI_PREFIX + eventId;

        List<ViewStats> stats = statsClient.getStats(
                LocalDateTime.now().minusYears(STATISTICS_PERIOD_YEARS),
                LocalDateTime.now().plusYears(STATISTICS_PERIOD_YEARS),
                List.of(uri),
                true
        );

        if (stats == null || stats.isEmpty()) {
            return EventMapper.mapToEventFullDto(event);
        }

        return EventMapper.mapToEventFullDto(event, stats.getFirst().getHits());
    }

    @Override
    public EventFullDto updateEvent(UpdateEventUserRequest dto, Long userId, Long eventId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(USER_NOT_FOUND_MESSAGE);
        }

        if (dto.getEventDate() != null && dto.getEventDate().isBefore(
                LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
            throw new BadRequestException(EVENT_TIME_VALIDATION_MESSAGE);
        }

        Event event = eventRepository.findEventByInitiatorIdAndId(userId, eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_MESSAGE));

        if (event.getState() == EventState.PUBLISHED) {
            throw new DataConflictException(
                    "Нельзя изменять событие которое уже опубликовано");
        }

        EventMapper.updateEventFields(event, dto);

        if (dto.getCategory() != null
                && !dto.getCategory().equals(event.getCategory().getId())) {
            Category category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new CategoryNotFoundException(CATEGORY_NOT_FOUND_MESSAGE));

            event.setCategory(category);
        }

        eventRepository.save(event);

        return EventMapper.mapToEventFullDto(event);
    }

    @Override
    public EventFullDto updateEventByAdmin(UpdateEventAdminRequest dto, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_BY_ID_MESSAGE));

        if (dto.getCategory() != null
                && !dto.getCategory().equals(event.getCategory().getId())) {
            Category category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new CategoryNotFoundException(CATEGORY_NOT_FOUND_MESSAGE));

            event.setCategory(category);
        }

        if (dto.getStateAction() == AdminStateAction.PUBLISH_EVENT
                && event.getState() != EventState.PENDING) {
            throw new DataConflictException(
                    "Можно опубликовать событие только если оно в ожидании публикации");
        }

        if (dto.getStateAction() == AdminStateAction.REJECT_EVENT
                && event.getState() == EventState.PUBLISHED) {
            throw new DataConflictException(
                    "Нельзя отменить событие которое уже опубликовано");
        }

        if (dto.getStateAction() == AdminStateAction.PUBLISH_EVENT) {
            LocalDateTime eventDate = dto.getEventDate() != null
                    ? dto.getEventDate()
                    : event.getEventDate();

            if (eventDate.isBefore(
                    LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_PUBLICATION))) {
                throw new DataConflictException(
                        "Начало события не может быть раньше чем через час от текущего момента");
            }
        }

        if (dto.getEventDate() != null && dto.getEventDate().isBefore(
                LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
            throw new BadRequestException(EVENT_TIME_VALIDATION_MESSAGE);
        }

        EventMapper.updateEventFields(event, dto);

        return EventMapper.mapToEventFullDto(eventRepository.save(event));
    }

    @Override
    public List<EventFullDto> findAllEventForAdmin(
            List<Long> users,
            List<EventState> states,
            List<Long> categories,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            int from,
            int size) {

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new DataConflictException("rangeStart не может быть позже rangeEnd");
        }

        QEvent qEvent = QEvent.event;
        BooleanBuilder builder = new BooleanBuilder();

        if (users != null && !users.isEmpty()) {
            builder.and(qEvent.initiator.id.in(users));
        }

        if (states != null && !states.isEmpty()) {
            builder.and(qEvent.state.in(states));
        }

        if (categories != null && !categories.isEmpty()) {
            builder.and(qEvent.category.id.in(categories));
        }

        if (rangeStart != null) {
            builder.and(qEvent.eventDate.goe(rangeStart));
        }

        if (rangeEnd != null) {
            builder.and(qEvent.eventDate.loe(rangeEnd));
        }

        JPAQueryFactory queryFactory = new JPAQueryFactory(entityManager);

        JPAQuery<Event> query = queryFactory
                .selectFrom(qEvent)
                .where(builder)
                .orderBy(qEvent.id.asc())
                .offset(from)
                .limit(size);

        List<Event> events = query.fetch();

        if (events.isEmpty()) {
            return List.of();
        }

        List<Object[]> confirmedCount = requestRepository.countByEventIdsAndStatus(
                events.stream()
                        .map(Event::getId)
                        .toList(),
                RequestStatus.CONFIRMED
        );

        Map<Long, Long> confirmedMap = confirmedCount.stream()
                .collect(Collectors.toMap(
                        obj -> (Long) obj[0],
                        obj -> (Long) obj[1]
                ));

        return events.stream()
                .map(event -> EventMapper.mapToEventFullDto(
                        event,
                        DEFAULT_VIEWS,
                        confirmedMap.getOrDefault(event.getId(), 0L)
                ))
                .toList();
    }

    @Override
    public List<EventShortDto> findEventsForPublic(
            String text,
            List<Long> categories,
            Boolean paid,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            boolean onlyAvailable,
            EventSortValue sort,
            int from,
            int size,
            HttpServletRequest request) {

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new BadRequestException("rangeStart не может быть позже rangeEnd");
        }

        QEvent qEvent = QEvent.event;
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(qEvent.state.eq(EventState.PUBLISHED));

        if (text != null && !text.isBlank()) {
            builder.and(
                    qEvent.annotation.containsIgnoreCase(text)
                            .or(qEvent.description.containsIgnoreCase(text))
            );
        }

        if (categories != null && !categories.isEmpty()) {
            builder.and(qEvent.category.id.in(categories));
        }

        if (paid != null) {
            builder.and(qEvent.paid.eq(paid));
        }

        if (rangeStart == null && rangeEnd == null) {
            builder.and(qEvent.eventDate.goe(LocalDateTime.now()));
        }

        if (rangeStart != null) {
            builder.and(qEvent.eventDate.goe(rangeStart));
        }

        if (rangeEnd != null) {
            builder.and(qEvent.eventDate.loe(rangeEnd));
        }

        JPAQueryFactory queryFactory = new JPAQueryFactory(entityManager);

        JPAQuery<Event> query = queryFactory
                .selectFrom(qEvent)
                .where(builder);

        if (sort == EventSortValue.EVENT_DATE) {
            query.orderBy(qEvent.eventDate.asc());
        }

        List<Event> events = query.fetch();

        if (onlyAvailable) {
            events = events.stream()
                    .filter(event -> {
                        if (event.getParticipantLimit() == NO_PARTICIPANT_LIMIT) {
                            return true;
                        }

                        long confirmedCount = requestRepository.countByEventIdAndStatus(
                                event.getId(),
                                RequestStatus.CONFIRMED
                        );

                        return confirmedCount < event.getParticipantLimit();
                    })
                    .toList();
        }

        List<String> uris = events.stream()
                .map(event -> EVENT_URI_PREFIX + event.getId())
                .toList();

        sendHit(request);

        List<ViewStats> stats = statsClient.getStats(
                LocalDateTime.now().minusYears(STATISTICS_PERIOD_YEARS),
                LocalDateTime.now().plusYears(STATISTICS_PERIOD_YEARS),
                uris,
                true
        );

        Map<String, Long> viewsMap = stats == null
                ? Map.of()
                : stats.stream()
                .collect(Collectors.toMap(
                        ViewStats::getUri,
                        ViewStats::getHits
                ));

        if (sort == EventSortValue.VIEWS) {
            events = events.stream()
                    .sorted((event1, event2) -> {
                        Long views1 = viewsMap.getOrDefault(
                                EVENT_URI_PREFIX + event1.getId(),
                                DEFAULT_VIEWS
                        );

                        Long views2 = viewsMap.getOrDefault(
                                EVENT_URI_PREFIX + event2.getId(),
                                DEFAULT_VIEWS
                        );

                        return views2.compareTo(views1);
                    })
                    .toList();
        }

        List<Event> paginatedEvents = events.stream()
                .skip(from)
                .limit(size)
                .toList();

        return paginatedEvents.stream()
                .map(event -> {
                    String uri = EVENT_URI_PREFIX + event.getId();
                    Long views = viewsMap.getOrDefault(uri, DEFAULT_VIEWS);

                    return EventMapper.mapToEventShortDto(event, views);
                })
                .toList();
    }

    private void sendHit(HttpServletRequest request) {
        EndpointHit hit = new EndpointHit();
        hit.setApp(APP_NAME);
        hit.setUri(request.getRequestURI());
        hit.setIp(request.getRemoteAddr());
        hit.setTimestamp(LocalDateTime.now());

        statsClient.hit(hit);
    }
}
