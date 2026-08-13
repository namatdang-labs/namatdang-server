package com.namatdang.namatdang.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void ownerRoleSignUp() throws Exception {
        String email = uniqueEmail("owner");

        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "name": "사장님",
                                  "phoneNumber": "010-1234-5678",
                                  "role": "OWNER"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", user.getPassword())).isTrue();
    }

    @Test
    void consumerRoleSignUp() throws Exception {
        String email = uniqueEmail("consumer");

        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "name": "소비자",
                                  "phoneNumber": "010-5678-1234",
                                  "role": "CONSUMER"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CONSUMER"));
    }

    @Test
    void duplicatedEmailCannotSignUp() throws Exception {
        String email = uniqueEmail("duplicate");
        saveUser(email);

        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "name": "중복회원",
                                  "phoneNumber": "010-1234-5678",
                                  "role": "CONSUMER"
                                }
                                """.formatted(email.toUpperCase(Locale.ROOT))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void getMyInfo() throws Exception {
        String email = uniqueEmail("get");
        User user = saveUser(email);

        mockMvc.perform(get("/api/v1/users/me")
                        .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void updateMyInfo() throws Exception {
        User user = saveUser(uniqueEmail("update"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .requestAttr("userId", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "수정이름",
                                  "phoneNumber": "010-9999-9999"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("수정이름"))
                .andExpect(jsonPath("$.phoneNumber").value("010-9999-9999"));
    }

    @Test
    void emptyUpdateRequestIsRejected() throws Exception {
        User user = saveUser(uniqueEmail("empty-update"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .requestAttr("userId", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void deleteUserAndCannotAccessAgain() throws Exception {
        User user = saveUser(uniqueEmail("delete"));

        mockMvc.perform(delete("/api/v1/users/me")
                        .requestAttr("userId", user.getId()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/v1/users/me")
                        .requestAttr("userId", user.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/users/me")
                        .requestAttr("userId", user.getId()))
                .andExpect(status().isNotFound());

        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void unknownUserReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .requestAttr("userId", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void ownerWithStoreCannotDeleteAccount() throws Exception {
        User owner = saveUser(uniqueEmail("owner-with-store"), UserRole.OWNER);
        Store store = new Store(owner,
                                "탈퇴 거절 매장",
                                "대구광역시 중구 종로 1",
                                null,
                                null,
                                null,
                                new BigDecimal("35.8714354"),
                                new BigDecimal("128.6014450"));
        storeRepository.saveAndFlush(store);

        mockMvc.perform(delete("/api/v1/users/me")
                        .requestAttr("userId", owner.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_HAS_STORES"));

        assertThat(userRepository.findById(owner.getId())).isPresent();
        assertThat(storeRepository.findById(store.getId())).isPresent();
    }

    private User saveUser(String email) {
        return saveUser(email, UserRole.CONSUMER);
    }

    private User saveUser(String email, UserRole role) {
        User user = new User(email,
                             passwordEncoder.encode("password123"),
                             "테스트회원",
                             "010-1234-5678",
                             role);
        return userRepository.saveAndFlush(user);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
