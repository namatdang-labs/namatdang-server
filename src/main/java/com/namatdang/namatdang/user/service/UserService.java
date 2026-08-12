package com.namatdang.namatdang.user.service;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.user.dto.UserResponseDto;
import com.namatdang.namatdang.user.dto.UserSignUpRequestDto;
import com.namatdang.namatdang.user.dto.UserSignUpResponseDto;
import com.namatdang.namatdang.user.dto.UserUpdateRequestDto;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserSignUpResponseDto signUpUser(UserSignUpRequestDto request) {
        String email = request.getEmail().strip().toLowerCase(Locale.ROOT);
        validateEmailNotExists(email);

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User(
                email,
                encodedPassword,
                request.getName().strip(),
                request.getPhoneNumber().strip(),
                request.getRole()
        );

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessLogicException(ExceptionCode.USER_EMAIL_EXISTS);
        }

        return new UserSignUpResponseDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhoneNumber(),
                user.getRole()
        );
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUser(Long userId) {
        User user = findUserById(userId);
        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    @Transactional
    public UserResponseDto updateUser(Long userId, UserUpdateRequestDto request) {
        if (request.getName() == null && request.getPhoneNumber() == null) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
        }

        User user = findUserById(userId);
        String name = request.getName() == null ? null : request.getName().strip();
        String phoneNumber = request.getPhoneNumber() == null ? null : request.getPhoneNumber().strip();
        user.update(name, phoneNumber);
        userRepository.flush();

        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = findUserById(userId);
        user.delete();
    }

    private User findUserById(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.USER_NOT_FOUND));
    }

    private void validateEmailNotExists(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessLogicException(ExceptionCode.USER_EMAIL_EXISTS);
        }
    }

}
