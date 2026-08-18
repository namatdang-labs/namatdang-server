package com.namatdang.namatdang.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.namatdang.namatdang.push.entity.FcmRegistration;
import com.namatdang.namatdang.push.entity.PushDeviceType;
import com.namatdang.namatdang.push.repository.FcmRegistrationRepository;
import com.namatdang.namatdang.push.service.PushTokenService;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import com.namatdang.namatdang.user.service.UserService;
import jakarta.persistence.EntityManager;
import java.util.List;
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
class PushTokenApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FcmRegistrationRepository fcmRegistrationRepository;

    @Autowired
    private PushTokenService pushTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registerIosPushToken() throws Exception {
        User user = saveUser("register");
        String registrationToken = uniqueToken("register");

        mockMvc.perform(put("/api/v1/push-tokens")
                        .requestAttr("userId", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(registrationToken, "IOS", "SAFARI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.deviceType").value("IOS"))
                .andExpect(jsonPath("$.browser").value("SAFARI"))
                .andExpect(jsonPath("$.lastRegisteredAt").exists())
                .andExpect(jsonPath("$.active").doesNotExist())
                .andExpect(jsonPath("$.registrationToken").doesNotExist());

        FcmRegistration registration = findByToken(registrationToken);
        assertThat(registration.getUserId()).isEqualTo(user.getId());
        assertThat(registration.getDeviceType()).isEqualTo(PushDeviceType.IOS);
    }

    @Test
    void repeatedRegistrationUpdatesExistingToken() throws Exception {
        User user = saveUser("repeat");
        String registrationToken = uniqueToken("repeat");

        register(user, registrationToken, "SAFARI");
        FcmRegistration firstRegistration = findByToken(registrationToken);

        register(user, registrationToken, "chrome");
        FcmRegistration updatedRegistration = findByToken(registrationToken);

        assertThat(fcmRegistrationRepository.count()).isEqualTo(1);
        assertThat(updatedRegistration.getId()).isEqualTo(firstRegistration.getId());
        assertThat(updatedRegistration.getBrowser()).isEqualTo("CHROME");
    }

    @Test
    void deletedTokenCanBeRegisteredAgain() throws Exception {
        User user = saveUser("reregister");
        String registrationToken = uniqueToken("reregister");
        register(user, registrationToken, "SAFARI");
        FcmRegistration registration = findByToken(registrationToken);

        mockMvc.perform(delete("/api/v1/push-tokens/{pushTokenId}", registration.getId())
                        .requestAttr("userId", user.getId()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(fcmRegistrationRepository.findByRegistrationToken(registrationToken)).isEmpty();

        mockMvc.perform(delete("/api/v1/push-tokens/{pushTokenId}", registration.getId())
                        .requestAttr("userId", user.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PUSH_TOKEN_NOT_FOUND"));

        register(user, registrationToken, "CHROME");

        FcmRegistration reregisteredRegistration = findByToken(registrationToken);
        assertThat(fcmRegistrationRepository.count()).isEqualTo(1);
        assertThat(reregisteredRegistration.getId()).isNotEqualTo(registration.getId());
        assertThat(reregisteredRegistration.getBrowser()).isEqualTo("CHROME");
    }

    @Test
    void oneUserCanRegisterMultipleDeviceTokens() throws Exception {
        User user = saveUser("multiple");
        register(user, uniqueToken("ios"), "IOS", "SAFARI");
        register(user, uniqueToken("android"), "ANDROID", "CHROME");
        register(user, uniqueToken("desktop"), "DESKTOP", "CHROME");

        List<FcmRegistration> registrations = pushTokenService.getRegistrations(user.getId());

        assertThat(registrations)
                .extracting(FcmRegistration::getDeviceType)
                .containsExactly(
                        PushDeviceType.IOS,
                        PushDeviceType.ANDROID,
                        PushDeviceType.DESKTOP
                );
    }

    @Test
    void otherUserCannotDeletePushToken() throws Exception {
        User owner = saveUser("owner");
        User otherUser = saveUser("other");
        String registrationToken = uniqueToken("ownership");
        register(owner, registrationToken, "SAFARI");
        FcmRegistration registration = findByToken(registrationToken);

        mockMvc.perform(delete("/api/v1/push-tokens/{pushTokenId}", registration.getId())
                        .requestAttr("userId", otherUser.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PUSH_TOKEN_NOT_FOUND"));

        assertThat(fcmRegistrationRepository.findByRegistrationToken(registrationToken)).isPresent();
    }

    @Test
    void registrationTokenMovesToCurrentUserWhenBrowserLoginChanges() throws Exception {
        User firstUser = saveUser("first-user");
        User currentUser = saveUser("current-user");
        String registrationToken = uniqueToken("shared-browser");
        register(firstUser, registrationToken, "SAFARI");
        FcmRegistration firstRegistration = findByToken(registrationToken);

        register(currentUser, registrationToken, "SAFARI");

        FcmRegistration movedRegistration = findByToken(registrationToken);
        assertThat(fcmRegistrationRepository.count()).isEqualTo(1);
        assertThat(movedRegistration.getId()).isEqualTo(firstRegistration.getId());
        assertThat(movedRegistration.getUserId()).isEqualTo(currentUser.getId());
        assertThat(pushTokenService.getRegistrations(firstUser.getId())).isEmpty();
        assertThat(pushTokenService.getRegistrations(currentUser.getId()))
                .extracting(FcmRegistration::getId)
                .containsExactly(movedRegistration.getId());
    }

    @Test
    void invalidFirebaseTokenIsDeletedAndExcludedFromTargets() throws Exception {
        User user = saveUser("invalid-firebase");
        String registrationToken = uniqueToken("invalid-firebase");
        register(user, registrationToken, "SAFARI");

        pushTokenService.deleteInvalidToken(registrationToken);

        assertThat(fcmRegistrationRepository.findByRegistrationToken(registrationToken)).isEmpty();
        assertThat(pushTokenService.getRegistrations(user.getId())).isEmpty();
    }

    @Test
    void deletingUserDeletesAllPushTokens() throws Exception {
        User user = saveUser("delete-user");
        Long userId = user.getId();
        register(user, uniqueToken("delete-user-ios"), "IOS", "SAFARI");
        register(user, uniqueToken("delete-user-desktop"), "DESKTOP", "CHROME");

        userService.deleteUser(userId);
        userRepository.flush();
        entityManager.clear();

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(pushTokenService.getRegistrations(userId)).isEmpty();
    }

    @Test
    void invalidRequestIsRejected() throws Exception {
        User user = saveUser("invalid-request");

        mockMvc.perform(put("/api/v1/push-tokens")
                        .requestAttr("userId", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(" ", "DESKTOP", "SAFARI")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(put("/api/v1/push-tokens")
                        .requestAttr("userId", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(uniqueToken("invalid-device"), "MOBILE", "SAFARI")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private void register(User user, String registrationToken, String browser) throws Exception {
        register(user, registrationToken, "DESKTOP", browser);
    }

    private void register(
            User user,
            String registrationToken,
            String deviceType,
            String browser
    ) throws Exception {
        mockMvc.perform(put("/api/v1/push-tokens")
                        .requestAttr("userId", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(registrationToken, deviceType, browser)))
                .andExpect(status().isOk());
    }

    private String requestBody(String registrationToken, String deviceType, String browser) {
        return """
                {
                  "registrationToken": "%s",
                  "deviceType": "%s",
                  "browser": "%s"
                }
                """.formatted(registrationToken, deviceType, browser);
    }

    private FcmRegistration findByToken(String registrationToken) {
        return fcmRegistrationRepository.findByRegistrationToken(registrationToken).orElseThrow();
    }

    private String uniqueToken(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private User saveUser(String prefix) {
        User user = new User(
                prefix + "-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("password123"),
                "테스트회원",
                "010-1234-5678",
                UserRole.CONSUMER
        );
        return userRepository.saveAndFlush(user);
    }
}
