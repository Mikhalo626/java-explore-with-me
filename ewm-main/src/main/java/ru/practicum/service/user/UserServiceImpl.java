package ru.practicum.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.dto.user.NewUserRequest;
import ru.practicum.dto.user.UserDto;
import ru.practicum.exception.BadRequestException;
import ru.practicum.exception.UserEmailConflictException;
import ru.practicum.exception.UserNotFoundException;
import ru.practicum.mapper.UserMapper;
import ru.practicum.model.User;
import ru.practicum.storage.user.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String EMAIL_ALREADY_EXISTS_MESSAGE =
            "Пользователь с таким email уже существует";

    private static final String USER_NOT_FOUND_MESSAGE =
            "Пользователя с таким id не существует";

    private static final String INVALID_PAGE_PARAMETERS_MESSAGE =
            "Параметры размеров выборки не корректны";

    private static final int MIN_OFFSET = 0;
    private static final int MIN_PAGE_SIZE = 1;

    private final UserRepository userRepository;

    @Override
    public UserDto createUser(NewUserRequest newUserRequest) {
        if (userRepository.existsByEmail(newUserRequest.getEmail())) {
            throw new UserEmailConflictException(EMAIL_ALREADY_EXISTS_MESSAGE);
        }

        User userToSave = UserMapper.mapToUser(newUserRequest);

        userRepository.save(userToSave);

        return UserMapper.mapToUserDto(userToSave);
    }

    @Override
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(USER_NOT_FOUND_MESSAGE);
        }

        userRepository.deleteById(id);
    }

    @Override
    public List<UserDto> getUsers(List<Long> ids, int from, int size) {
        if (from < MIN_OFFSET || size < MIN_PAGE_SIZE) {
            throw new BadRequestException(INVALID_PAGE_PARAMETERS_MESSAGE);
        }

        List<User> users;

        if (ids == null || ids.isEmpty()) {
            users = userRepository.findUsersWithOffsetAndSize(from, size);
        } else {
            users = userRepository.findUsersWithOffsetAndSizeWhereIdIn(
                    ids, from, size);
        }

        return users.stream()
                .map(UserMapper::mapToUserDto)
                .toList();
    }
}
