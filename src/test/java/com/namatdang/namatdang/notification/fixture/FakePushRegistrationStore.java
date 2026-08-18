package com.namatdang.namatdang.notification.fixture;

import com.namatdang.namatdang.notification.push.ActivePushRegistrationReader;
import com.namatdang.namatdang.notification.push.InvalidPushRegistrationHandler;
import com.namatdang.namatdang.notification.push.PushTarget;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FakePushRegistrationStore
        implements ActivePushRegistrationReader, InvalidPushRegistrationHandler {

    private final Map<Long, PushTarget> targetsById = new LinkedHashMap<>();
    private boolean failBulkRead;
    private boolean failInvalidation;

    @Override
    public List<PushTarget> findAllByUserIds(Collection<Long> userIds) {
        if (failBulkRead) {
            throw new IllegalStateException("Push 등록정보 조회 실패");
        }
        return targetsById.values().stream()
                .filter(target -> userIds.contains(target.userId()))
                .toList();
    }

    @Override
    public Optional<PushTarget> findById(Long registrationId) {
        return Optional.ofNullable(targetsById.get(registrationId));
    }

    @Override
    public void invalidate(Long registrationId) {
        if (failInvalidation) {
            throw new IllegalStateException("Push 등록정보 삭제 실패");
        }
        targetsById.remove(registrationId);
    }

    public void add(PushTarget target) {
        targetsById.put(target.registrationId(), target);
    }

    public void failBulkRead() {
        failBulkRead = true;
    }

    public void failInvalidation() {
        failInvalidation = true;
    }

    public void reset() {
        targetsById.clear();
        failBulkRead = false;
        failInvalidation = false;
    }
}
