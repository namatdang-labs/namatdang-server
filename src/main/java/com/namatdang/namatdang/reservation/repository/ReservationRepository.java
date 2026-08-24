package com.namatdang.namatdang.reservation.repository;

import com.namatdang.namatdang.reservation.entity.Reservation;
import com.namatdang.namatdang.reservation.entity.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // 목록 조회는 deal과 store만 fetch join 한다. items까지 넣으면 컬렉션 조인 때문에
    // 인메모리 페이징으로 떨어지므로, items의 N+1은 Reservation.items의 @BatchSize로 완화한다.

    boolean existsByConsumerIdAndDealId(Long consumerId, Long dealId);

    @EntityGraph(attributePaths = {"deal", "deal.store"})
    Page<Reservation> findByConsumerId(Long consumerId, Pageable pageable);

    @EntityGraph(attributePaths = {"deal", "deal.store"})
    Page<Reservation> findByConsumerIdAndStatus(Long consumerId, ReservationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"deal", "deal.store"})
    Page<Reservation> findByDealStoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"deal", "deal.store"})
    Page<Reservation> findByDealStoreIdAndStatus(Long storeId, ReservationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"deal", "deal.store", "items"})
    Optional<Reservation> findWithItemsById(Long reservationId);

    @EntityGraph(attributePaths = {"consumer", "deal", "deal.store", "deal.store.owner"})
    @Query("select reservation from Reservation reservation where reservation.id = :reservationId")
    Optional<Reservation> findNotificationTargetById(@Param("reservationId") Long reservationId);

    /**
     * 취소와 수령 완료가 공유하는 잠금이다. 두 처리가 같은 예약 행을 두고 경합하면 먼저 잠금을
     * 얻은 트랜잭션만 상태를 확정하고, 나중 요청은 확정된 상태를 다시 읽어 반대 전이를 거절한다(INV-06).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from Reservation reservation where reservation.id = :reservationId")
    Optional<Reservation> findByIdForUpdate(@Param("reservationId") Long reservationId);
}
