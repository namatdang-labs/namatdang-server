package com.namatdang.namatdang.deal.repository;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
