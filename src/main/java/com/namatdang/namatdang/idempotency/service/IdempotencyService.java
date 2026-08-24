package com.namatdang.namatdang.idempotency.service;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.idempotency.entity.IdempotencyOperation;
import com.namatdang.namatdang.idempotency.entity.IdempotencyRequest;
import com.namatdang.namatdang.idempotency.repository.IdempotencyRequestRepository;
import com.namatdang.namatdang.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 멱등 요청을 선점하고 최초 성공 응답을 재현한다.
 * <p>
 * 호출부는 이 서비스를 쓰기 전에 사용자 행을 비관적 잠금해야 한다. 같은 사용자의 쓰기 요청이
 * 직렬화되어야 같은 키의 동시 삽입 경합이 생기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final int MAX_KEY_LENGTH = 100;

    private final IdempotencyRequestRepository idempotencyRequestRepository;
    private final ObjectMapper objectMapper;

    /**
     * 멱등키를 선점하거나 이미 완료된 최초 요청을 돌려준다.
     *
     * @throws BusinessLogicException 같은 키에 다른 요청이면 IDEMPOTENCY_KEY_REUSED,
     *                                아직 처리 중인 같은 요청이면 CONCURRENT_REQUEST_CONFLICT
     */
    public IdempotencyRequest acquire(User user,
                                      IdempotencyOperation operation,
                                      String idempotencyKey,
                                      String requestHash) {
        validateKey(idempotencyKey);

        Optional<IdempotencyRequest> existing = idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(user.getId(), operation, idempotencyKey);

        if (existing.isEmpty()) {
            return idempotencyRequestRepository.save(
                    new IdempotencyRequest(user, operation, idempotencyKey, requestHash));
        }

        IdempotencyRequest request = existing.get();

        if (!request.matches(requestHash)) {
            throw new BusinessLogicException(ExceptionCode.IDEMPOTENCY_KEY_REUSED);
        }

        // 사용자 행 잠금 덕분에 같은 사용자의 요청은 직렬화된다. 그래도 PROCESSING이 보이면
        // 앞선 트랜잭션이 아직 커밋되지 않은 것이므로 재시도를 요구한다.
        if (!request.isCompleted()) {
            throw new BusinessLogicException(ExceptionCode.CONCURRENT_REQUEST_CONFLICT);
        }

        return request;
    }

    /**
     * 최초 성공 응답을 기록한다. 도메인 작업과 같은 트랜잭션에서 호출해야 함께 커밋·롤백된다.
     */
    public void complete(IdempotencyRequest request, int responseStatus, Object responseBody) {
        request.complete(responseStatus, write(responseBody));
    }

    /**
     * 업무 의미가 없는 표현 차이로 키 충돌이 나지 않도록, 호출부가 정규화한 문자열을 받아 해시한다.
     */
    public String hash(String normalizedRequest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(normalizedRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String write(Object responseBody) {
        return objectMapper.writeValueAsString(responseBody);
    }

    private void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
        }
    }
}
