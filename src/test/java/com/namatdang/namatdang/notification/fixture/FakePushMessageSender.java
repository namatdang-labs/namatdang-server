package com.namatdang.namatdang.notification.fixture;

import com.namatdang.namatdang.notification.push.PushMessage;
import com.namatdang.namatdang.notification.push.PushMessageSender;
import com.namatdang.namatdang.notification.push.PushSendResult;
import com.namatdang.namatdang.notification.push.PushTarget;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakePushMessageSender implements PushMessageSender {

    private final Map<String, Deque<PushSendResult>> resultsByToken = new HashMap<>();
    private final List<String> attemptedTokens = new ArrayList<>();

    @Override
    public PushSendResult send(PushTarget target, PushMessage message) {
        attemptedTokens.add(target.registrationToken());
        Deque<PushSendResult> results = resultsByToken.get(target.registrationToken());
        if (results == null || results.isEmpty()) {
            return PushSendResult.succeeded();
        }
        return results.removeFirst();
    }

    public void willReturn(String token, PushSendResult... results) {
        resultsByToken.put(token, new ArrayDeque<>(Arrays.asList(results)));
    }

    public List<String> attemptedTokens() {
        return List.copyOf(attemptedTokens);
    }

    public void reset() {
        resultsByToken.clear();
        attemptedTokens.clear();
    }
}
