package com.namatdang.namatdang.security;

import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final UserRepository userRepository;
    private final SecurityErrorWriter errorWriter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");

        if (authorization != null) {
            if (!authorization.startsWith(BEARER_PREFIX) || authorization.length() == BEARER_PREFIX.length()) {
                errorWriter.write(response, ExceptionCode.INVALID_TOKEN);
                return;
            }

            try {
                authenticate(jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length())));
                request.setAttribute("userId", currentUser().getUserId());
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                errorWriter.write(response, ExceptionCode.INVALID_TOKEN);
                return;
            }
        } else {
            authenticateLegacyRequestAttribute(request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        UserRole role = userRepository.findById(userId)
                .map(User::getRole)
                .orElseThrow(() -> new BadJwtException("탈퇴했거나 존재하지 않는 회원의 토큰입니다."));
        setAuthentication(new AuthUser(userId, role));
    }

    private void authenticateLegacyRequestAttribute(HttpServletRequest request) {
        Object userIdAttribute = request.getAttribute("userId");
        if (!(userIdAttribute instanceof Long userId)) {
            return;
        }

        userRepository.findById(userId)
                .map(User::getRole)
                .map(role -> new AuthUser(userId, role))
                .ifPresent(this::setAuthentication);
    }

    private void setAuthentication(AuthUser authUser) {
        var authority = new SimpleGrantedAuthority("ROLE_" + authUser.getRole().name());
        var authentication = new UsernamePasswordAuthenticationToken(authUser, null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private AuthUser currentUser() {
        return (AuthUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
