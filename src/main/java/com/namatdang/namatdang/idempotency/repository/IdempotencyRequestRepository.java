package com.namatdang.namatdang.idempotency.repository;

import com.namatdang.namatdang.idempotency.entity.IdempotencyOperation;
import com.namatdang.namatdang.idempotency.entity.IdempotencyRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequest, Long> {

    Optional<IdempotencyRequest> findByUserIdAndOperationAndIdempotencyKey(Long userId,
                                                                          IdempotencyOperation operation,
                                                                          String idempotencyKey);
}
