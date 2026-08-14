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
    private PasswordEncoder passwordEncoder;

    @Test
    void registerWebPushToken() throws Exception {
        User user = saveUser("register");
        String registrationToken = uniqueToken("register");

        mockMvc.perform(put("/api/v1/push-tokens")
                        .requestAttr("userId", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(registrationToken, "WEB", "SAFARI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.deviceType").value("WEB"))
                .andExpect(jsonPath("$.browser").value("SAFARI"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.lastRegisteredAt").exists())
                .andExpect(jsonPath("$.registrationToken").doesNotExist());

        FcmRegistration registration = findByToken(registrationToken);
        assertThat(registration.getUserId()).isEqualTo(user.getId());
        assertThat(registration.getDeviceType()).isEqualTo(PushDeviceType.WEB);
        assertThat(registration.isActive()).isTrue();
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
        assertThat(updatedRegistration.isActive()).isTrue();
    }

    @Test
    void inactiveTokenIsReactivatedWithoutCreatingDuplicate() throws Exception {
        User user = saveUser("reactivate");
        String registrationToken = uniqueToken("reactivate");
        register(user, registrationToken, "SAFARI");
        FcmRegistration registration = findByToken(registrationToken);

        mockMvc.perform(delete("/api/v1/push-tokens/{pushTokenId}", registration.getId())
                        .requestAttr("userId", user.getId()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(delete("/api/v1/push-tokens/{pushTokenId}", registration.getId())
                        .requestAttr("userId", user.getId()))
                .andExpect(status().isNoContent());

        assertThat(findByToken(registrationToken).isActive()).isFalse();

        register(user, registrationToken, "CHROME");

        FcmRegistration reactivatedRegistration = findByToken(registrationToken);
        assertThat(fcmRegistrationRepository.count()).isEqualTo(1);
        assertThat(reactivatedRegistration.getId()).isEqualTo(registration.getId());
        assertThat(reactivatedRegistration.isActive()).isTrue();
    }

    @Test
    void oneUserCanRegisterMultipleBrowserTokens() throws Exception {
        User user = saveUser("multiple");
        register(user, uniqueToken("safari"), "SAFARI");
        register(user, uniqueToken("chrome"), "CHROME");

        List<FcmRegistration> activeRegistrations = pushTokenService.getActiveRegistrations(user.getId());

        assertThat(activeRegistrations).hasSize(2);
    }

    @Test
    void otherUserCannotDeactivatePushToken() throws Exception {
        User owner = saveUser("owner");
        User otherUser = saveUser("other");
        String registrationToken = uniqueToken("ownership");
        register(owner, registrationToken, "SAFARI");
        FcmRegistration registration = findByToken(registrationToken);

        mockMvc.perform(delete("/api/v1/push-tokens/{pushTokenId}", registration.getId())
                        .requestAttr("userId", otherUser.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PUSH_TOKEN_NOT_FOUND"));

        assertThat(findByToken(registrationToken).isActive()).isTrue();
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
        assertThat(pushTokenService.getActiveRegistrations(firstUser.getId())).isEmpty();
        assertThat(pushTokenService.getActiveRegistrations(currentUser.getId()))
                .extracting(FcmRegistration::getId)
                .containsExactly(movedRegistration.getId());
    }

    @Test
    void invalidFirebaseTokenIsDeactivatedAndExcludedFromTargets() throws Exception {
        User user = saveUser("invalid-firebase");
        String registrationToken = uniqueToken("invalid-firebase");
        register(user, registrationToken, "SAFARI");

        pushTokenService.deactivateInvalidToken(registrationToken);

        assertThat(findByToken(registrationToken).isActive()).isFalse();
        assertThat(pushTokenService.getActiveRegistrations(user.getId())).isEmpty();
    }

    void invalidRequestIsRejected() throws Exception {
        User user = saveUser("invalid-request");

        mockMvc.perform(put("/api/v1/push-tokens")
                        .requestAttr("userId", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(" ", "WEB", "SAFARI")))
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
        mockMvc.perform(put("/api/v1/push-tokens")
                        .requestAttr("userId", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(registrationToken, "WEB", browser)))
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
