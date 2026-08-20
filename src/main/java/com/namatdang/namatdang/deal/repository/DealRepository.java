package com.namatdang.namatdang.deal.repository;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealRepository extends JpaRepository<Deal, Long> {

    // 목록 조회는 store만 fetch join 한다. items까지 넣으면 컬렉션 조인 때문에
    // Hibernate가 SQL LIMIT을 버리고 인메모리 페이징으로 떨어진다.
    // items의 N+1은 Deal.items의 @BatchSize로 완화한다.

    @EntityGraph(attributePaths = "store")
    Page<Deal> findByStatusAndSalesEndsAtAfter(DealStatus status,
                                               LocalDateTime salesEndsAt,
                                               Pageable pageable);

    @EntityGraph(attributePaths = "store")
    Page<Deal> findByStoreIdAndStatusAndSalesEndsAtAfter(Long storeId,
                                                         DealStatus status,
                                                         LocalDateTime salesEndsAt,
                                                         Pageable pageable);

    @EntityGraph(attributePaths = "store")
    Page<Deal> findByStoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = "store")
    Page<Deal> findByStoreIdAndStatus(Long storeId, DealStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"store", "items"})
    Optional<Deal> findWithItemsById(Long dealId);

    /**
     * 예약 생성·취소 트랜잭션에서 Deal 행을 잠근다. 품목 잠금보다 먼저 호출해 잠금 순서를
     * 고정하고, 마감 여부와 판매 상태를 잠금 후 다시 확인한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select deal from Deal deal where deal.id = :dealId")
    Optional<Deal> findByIdForUpdate(@Param("dealId") Long dealId);
}
