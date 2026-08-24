package com.namatdang.namatdang.notification.push;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActivePushRegistrationReader {

    List<PushTarget> findAllByUserIds(Collection<Long> userIds);

    Optional<PushTarget> findById(Long registrationId);
}
