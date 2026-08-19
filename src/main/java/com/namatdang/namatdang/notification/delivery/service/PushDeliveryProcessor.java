package com.namatdang.namatdang.notification.delivery.service;

import com.namatdang.namatdang.notification.push.ActivePushRegistrationReader;
import com.namatdang.namatdang.notification.push.InvalidPushRegistrationHandler;
import com.namatdang.namatdang.notification.push.PushMessageSender;
import com.namatdang.namatdang.notification.push.PushSendResult;
import com.namatdang.namatdang.notification.push.PushTarget;
import java.time.Duration;
import java.util.List;

public class PushDeliveryProcessor {

    private final PushDeliveryService pushDeliveryService;
    private final ActivePushRegistrationReader pushRegistrationReader;
    private final PushMessageSender pushMessageSender;
    private final InvalidPushRegistrationHandler invalidPushRegistrationHandler;

    public PushDeliveryProcessor(
            PushDeliveryService pushDeliveryService,
            ActivePushRegistrationReader pushRegistrationReader,
            PushMessageSender pushMessageSender,
            InvalidPushRegistrationHandler invalidPushRegistrationHandler
    ) {
        this.pushDeliveryService = pushDeliveryService;
        this.pushRegistrationReader = pushRegistrationReader;
        this.pushMessageSender = pushMessageSender;
        this.invalidPushRegistrationHandler = invalidPushRegistrationHandler;
    }

    public int processBatch(
            int batchSize,
            Duration sendingTimeout,
            Duration retryDelay,
            int maxAttemptCount
    ) {
        List<PushDeliveryTask> tasks = pushDeliveryService.claimSendableDeliveries(
                batchSize,
                sendingTimeout
        );
        tasks.forEach(task -> process(task, retryDelay, maxAttemptCount));
        return tasks.size();
    }

    private void process(
            PushDeliveryTask task,
            Duration retryDelay,
            int maxAttemptCount
    ) {
        PushTarget target = pushRegistrationReader.findById(task.fcmRegistrationId())
                .orElse(null);
        if (target == null) {
            pushDeliveryService.markInvalidToken(task.deliveryId(), "등록 토큰이 존재하지 않습니다.");
            return;
        }

        PushSendResult result;
        try {
            result = pushMessageSender.send(target, task.message());
        } catch (RuntimeException exception) {
            recordUnexpectedFailure(task, retryDelay, maxAttemptCount, exception);
            return;
        }

        switch (result.type()) {
            case SUCCEEDED -> pushDeliveryService.markSucceeded(task.deliveryId());
            case INVALID_TOKEN -> invalidateRegistration(
                    task,
                    target,
                    result,
                    retryDelay,
                    maxAttemptCount
            );
            case PERMISSION_DENIED -> pushDeliveryService.markPermissionDenied(
                    task.deliveryId(),
                    result.errorMessage()
            );
            case RETRYABLE_FAILURE -> pushDeliveryService.recordRetryableFailure(
                    task.deliveryId(),
                    result.errorMessage(),
                    retryDelay,
                    maxAttemptCount
            );
            case PERMANENT_FAILURE -> pushDeliveryService.markFailed(
                    task.deliveryId(),
                    result.errorMessage()
            );
        }
    }

    private void invalidateRegistration(
            PushDeliveryTask task,
            PushTarget target,
            PushSendResult result,
            Duration retryDelay,
            int maxAttemptCount
    ) {
        try {
            invalidPushRegistrationHandler.invalidate(target.registrationId());
            pushDeliveryService.markInvalidToken(task.deliveryId(), result.errorMessage());
        } catch (RuntimeException exception) {
            recordUnexpectedFailure(task, retryDelay, maxAttemptCount, exception);
        }
    }

    private void recordUnexpectedFailure(
            PushDeliveryTask task,
            Duration retryDelay,
            int maxAttemptCount,
            RuntimeException exception
    ) {
        pushDeliveryService.recordRetryableFailure(
                task.deliveryId(),
                exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage(),
                retryDelay,
                maxAttemptCount
        );
    }
}
