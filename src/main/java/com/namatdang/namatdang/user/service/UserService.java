package com.namatdang.namatdang.user.service;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.user.dto.UserResponseDto;
import com.namatdang.namatdang.user.dto.UserSignUpRequestDto;
import com.namatdang.namatdang.user.dto.UserSignUpResponseDto;
import com.namatdang.namatdang.user.dto.UserUpdateRequestDto;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserSignUpResponseDto signUpUser(UserSignUpRequestDto requestDto) {
        validateEmailNotExists(requestDto.normalizedEmail());

        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());
        User user = requestDto.toEntity(encodedPassword);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessLogicException(ExceptionCode.USER_EMAIL_EXISTS);
        }

        return UserSignUpResponseDto.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUser(Long userId) {
        User user = findUserById(userId);
        return UserResponseDto.from(user);
    }

    @Transactional
    public UserResponseDto updateUser(Long userId, UserUpdateRequestDto requestDto) {
        User user = findUserById(userId);
        validateUpdateRequest(requestDto);

        user.update(requestDto);
        userRepository.flush();

        return UserResponseDto.from(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = findUserById(userId);
        validateUserHasNoStore(userId);
        userRepository.delete(user);
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.USER_NOT_FOUND));
    }

    private void validateEmailNotExists(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessLogicException(ExceptionCode.USER_EMAIL_EXISTS);
        }
    }

    private void validateUpdateRequest(UserUpdateRequestDto requestDto) {
        if (!requestDto.hasUpdates()) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateUserHasNoStore(Long userId) {
        if (storeRepository.existsByOwnerId(userId)) {
            throw new BusinessLogicException(ExceptionCode.OWNER_HAS_STORES);
        }
    }

}
