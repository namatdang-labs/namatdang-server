package com.namatdang.namatdang.auth.service;

import com.namatdang.namatdang.auth.dto.LoginRequestDto;
import com.namatdang.namatdang.auth.dto.LoginResponseDto;
import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.security.JwtTokenProvider;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(readOnly = true)
    public LoginResponseDto login(LoginRequestDto loginRequestDto) {
        String email = loginRequestDto.getEmail().strip().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(email)
                .orElseThrow(this::authenticationFailed);

        if (!passwordEncoder.matches(loginRequestDto.getPassword(), user.getPassword())) {
            throw authenticationFailed();
        }

        String accessToken = jwtTokenProvider.issue(user.getId());
        return LoginResponseDto.of(accessToken, jwtTokenProvider.expirationSeconds(), user);
    }

    private BusinessLogicException authenticationFailed() {
        return new BusinessLogicException(ExceptionCode.AUTHENTICATION_FAILED);
    }
}
