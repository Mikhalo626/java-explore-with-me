package ru.practicum.service.request;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.dto.request.EventRequestStatusUpdate;
import ru.practicum.dto.request.EventRequestStatusUpdateRequest;
import ru.practicum.dto.request.EventRequestStatusUpdateResult;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.exception.DataConflictException;
import ru.practicum.exception.EventNotFoundException;
import ru.practicum.exception.RequestNotFoundException;
import ru.practicum.exception.UserNotFoundException;
import ru.practicum.mapper.RequestMapper;
import ru.practicum.model.Event;
import ru.practicum.model.EventState;
import ru.practicum.model.Request;
import ru.practicum.model.RequestStatus;
import ru.practicum.model.User;
import ru.practicum.storage.event.EventRepository;
import ru.practicum.storage.request.RequestRepository;
import ru.practicum.storage.user.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RequestServiceImpl implements RequestService {

    private static final long NO_PARTICIPANT_LIMIT = 0L;

    private static final String USER_NOT_FOUND_MESSAGE =
            "Пользователя с таким id не существует";
    private static final String EVENT_NOT_FOUND_MESSAGE =
            "События с таким id не существует";
    private static final String REQUEST_NOT_FOUND_MESSAGE =
            "Запроса с таким id не существует";
    private static final String EVENT_NOT_PUBLISHED_MESSAGE =
            "Нельзя участвовать в событии которое ещё не опубликовано";
    private static final String DUPLICATE_REQUEST_MESSAGE =
            "Невозможно оставить повторный запрос на событие";
    private static final String OWN_EVENT_REQUEST_MESSAGE =
            "Нельзя оставлять запрос на участие в своём же событии";
    private static final String PARTICIPANT_LIMIT_REACHED_MESSAGE =
            "Лимит заявок на участие заполнен";
    private static final String CONFIRMED_LIMIT_REACHED_MESSAGE =
            "Лимит подтверждённых заявок уже достигнут";
    private static final String ONLY_INITIATOR_VIEW_MESSAGE =
            "Просматривать запросы на участие в событии может только инициатор";
    private static final String ONLY_INITIATOR_UPDATE_MESSAGE =
            "Рассматривать запросы на участие в событии может только инициатор";
    private static final String REQUEST_NOT_PENDING_MESSAGE =
            "Заявка с id=%d не находится в статусе рассмотрения";

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Override
    public List<ParticipationRequestDto> findRequestsByUserId(Long userId) {
        List<Request> requests = requestRepository.findByRequesterId(userId);

        return requests.stream()
                .map(RequestMapper::mapToParticipantRequestDto)
                .toList();
    }

    @Override
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_MESSAGE));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_MESSAGE));

        if (event.getState() != EventState.PUBLISHED) {
            throw new DataConflictException(EVENT_NOT_PUBLISHED_MESSAGE);
        }

        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new DataConflictException(DUPLICATE_REQUEST_MESSAGE);
        }

        if (event.getInitiator().getId().equals(userId)) {
            throw new DataConflictException(OWN_EVENT_REQUEST_MESSAGE);
        }

        if (event.getParticipantLimit() > NO_PARTICIPANT_LIMIT
                && requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED)
                >= event.getParticipantLimit()) {
            throw new DataConflictException(PARTICIPANT_LIMIT_REACHED_MESSAGE);
        }

        Request request = new Request();
        request.setEvent(event);
        request.setRequester(user);
        request.setCreated(LocalDateTime.now());

        if (!event.getRequestModeration()
                || event.getParticipantLimit() == NO_PARTICIPANT_LIMIT) {
            request.setStatus(RequestStatus.CONFIRMED);
        } else {
            request.setStatus(RequestStatus.PENDING);
        }

        requestRepository.save(request);

        return RequestMapper.mapToParticipantRequestDto(request);
    }

    @Override
    public ParticipationRequestDto canceledRequest(Long requestId, Long userId) {
        Request request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> new RequestNotFoundException(REQUEST_NOT_FOUND_MESSAGE));

        request.setStatus(RequestStatus.CANCELED);

        requestRepository.save(request);

        return RequestMapper.mapToParticipantRequestDto(request);
    }

    @Override
    public List<ParticipationRequestDto> findRequestsByEventIdForInitiator(
            Long userId, Long eventId) {

        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_MESSAGE));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_MESSAGE));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new DataConflictException(ONLY_INITIATOR_VIEW_MESSAGE);
        }

        List<Request> requests = requestRepository.findByEventId(eventId);

        return requests.stream()
                .map(RequestMapper::mapToParticipantRequestDto)
                .toList();
    }

    @Override
    public EventRequestStatusUpdateResult updateEventRequestsStatus(
            Long userId, Long eventId, EventRequestStatusUpdateRequest dto) {

        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_MESSAGE));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_MESSAGE));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new DataConflictException(ONLY_INITIATOR_UPDATE_MESSAGE);
        }

        List<Request> requests = requestRepository.findByIdInAndEventId(
                dto.getRequestIds(), eventId);

        for (Request request : requests) {
            if (request.getStatus() != RequestStatus.PENDING) {
                throw new DataConflictException(
                        String.format(REQUEST_NOT_PENDING_MESSAGE, request.getId()));
            }
        }

        List<Request> confirmedRequests = new ArrayList<>();
        List<Request> rejectedRequests = new ArrayList<>();

        long participantLimit = event.getParticipantLimit();

        if (dto.getStatus() == EventRequestStatusUpdate.REJECTED) {
            for (Request request : requests) {
                request.setStatus(RequestStatus.REJECTED);
                rejectedRequests.add(request);
            }

            requestRepository.saveAll(requests);

            return RequestMapper.mapToStatusUpdateResult(
                    confirmedRequests, rejectedRequests);
        }

        long currentCount = requestRepository.countByEventIdAndStatus(
                eventId, RequestStatus.CONFIRMED);

        if (participantLimit > NO_PARTICIPANT_LIMIT
                && currentCount >= participantLimit) {
            throw new DataConflictException(CONFIRMED_LIMIT_REACHED_MESSAGE);
        }

        for (Request request : requests) {
            if (participantLimit == NO_PARTICIPANT_LIMIT
                    || currentCount < participantLimit) {
                request.setStatus(RequestStatus.CONFIRMED);
                confirmedRequests.add(request);
                currentCount++;
            } else {
                request.setStatus(RequestStatus.REJECTED);
                rejectedRequests.add(request);
            }
        }

        requestRepository.saveAll(requests);

        if (participantLimit > NO_PARTICIPANT_LIMIT
                && currentCount >= participantLimit) {

            List<Request> allPending = requestRepository.findByEventIdAndStatus(
                    eventId, RequestStatus.PENDING);

            for (Request pending : allPending) {
                pending.setStatus(RequestStatus.REJECTED);
                rejectedRequests.add(pending);
            }

            requestRepository.saveAll(allPending);
        }

        return RequestMapper.mapToStatusUpdateResult(
                confirmedRequests, rejectedRequests);
    }
}
