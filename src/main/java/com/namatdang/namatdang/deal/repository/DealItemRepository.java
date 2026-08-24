package com.namatdang.namatdang.deal.repository;

import com.namatdang.namatdang.deal.entity.DealItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealItemRepository extends JpaRepository<DealItem, Long> {

    List<DealItem> findByDealIdOrderByIdAsc(Long dealId);

    /**
     * 딜에 속한 품목 전체를 id 오름차순으로 잠근다. 잠금 순서를 고정해 다품목 동시 예약에서
     * 데드락을 피하고, 예약 원자성(INV-07)을 확보한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from DealItem i where i.deal.id = :dealId order by i.id asc")
    List<DealItem> findByDealIdForUpdate(@Param("dealId") Long dealId);
}
