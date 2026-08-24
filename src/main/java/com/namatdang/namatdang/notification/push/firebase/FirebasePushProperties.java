package com.namatdang.namatdang.notification.push.firebase;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.push.firebase")
public class FirebasePushProperties {

    private String projectId;

    String requiredProjectId() {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException(
                    "Firebase Push를 활성화하려면 FIREBASE_PROJECT_ID가 필요합니다."
            );
        }
        return projectId.strip();
    }
}
