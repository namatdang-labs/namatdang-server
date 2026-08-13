package com.namatdang.namatdang.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.user.dto.UserSignUpRequestDto;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import com.namatdang.namatdang.user.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserSignUpRequestDto requestDto;

    @InjectMocks
    private UserService userService;

    @Test
    void databaseEmailConflictReturnsUserEmailExists() {
        String email = "duplicate@example.com";
        String rawPassword = "password123";
        String encodedPassword = "encoded-password";
        User user = new User(email, encodedPassword, "테스트 회원", "010-1234-5678", UserRole.CONSUMER);

        given(requestDto.normalizedEmail()).willReturn(email);
        given(requestDto.getPassword()).willReturn(rawPassword);
        given(requestDto.toEntity(encodedPassword)).willReturn(user);
        given(passwordEncoder.encode(rawPassword)).willReturn(encodedPassword);
        given(userRepository.saveAndFlush(user)).willThrow(new DataIntegrityViolationException("duplicate email"));

        BusinessLogicException exception = catchThrowableOfType(BusinessLogicException.class,
                                                                 () -> userService.signUp(requestDto));

        assertThat(exception.getExceptionCode()).isEqualTo(ExceptionCode.USER_EMAIL_EXISTS);
    }

    @Test
    void databaseStoreConflictPreventsUserDeletion() {
        User user = new User("owner@example.com",
                             "encoded-password",
                             "테스트 사장님",
                             "010-1234-5678",
                             UserRole.OWNER);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(storeRepository.existsByOwnerId(1L)).willReturn(false);
        willThrow(new DataIntegrityViolationException("store foreign key"))
                .given(userRepository)
                .flush();

        BusinessLogicException exception = catchThrowableOfType(BusinessLogicException.class,
                                                                 () -> userService.deleteUser(1L));

        assertThat(exception.getExceptionCode()).isEqualTo(ExceptionCode.OWNER_HAS_STORES);
        then(userRepository).should().delete(user);
    }
}
