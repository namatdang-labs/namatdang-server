package com.namatdang.namatdang.deal.repository;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealRepository extends JpaRepository<Deal, Long> {

    interface DealDistanceRow {

        Long getDealId();

        Double getDistanceMeters();
    }

    // 목록 조회는 store만 fetch join 한다. items까지 넣으면 컬렉션 조인 때문에
    // Hibernate가 SQL LIMIT을 버리고 인메모리 페이징으로 떨어진다.
    // items의 N+1은 Deal.items의 @BatchSize로 완화한다.

    @EntityGraph(attributePaths = "store")
    @Query(value = """
            SELECT deal
            FROM Deal deal
            WHERE deal.status = :status
              AND deal.salesEndsAt > :salesEndsAt
              AND EXISTS (
                    SELECT availableItem.id
                    FROM DealItem availableItem
                    WHERE availableItem.deal = deal
                      AND availableItem.remainingQuantity > 0
              )
            """,
            countQuery = """
            SELECT COUNT(deal)
            FROM Deal deal
            WHERE deal.status = :status
              AND deal.salesEndsAt > :salesEndsAt
              AND EXISTS (
                    SELECT availableItem.id
                    FROM DealItem availableItem
                    WHERE availableItem.deal = deal
                      AND availableItem.remainingQuantity > 0
              )
            """)
    Page<Deal> findByStatusAndSalesEndsAtAfter(@Param("status") DealStatus status,
                                               @Param("salesEndsAt") LocalDateTime salesEndsAt,
                                               Pageable pageable);

    @EntityGraph(attributePaths = "store")
    @Query(value = """
            SELECT deal
            FROM Deal deal
            WHERE deal.status = :status
              AND deal.salesEndsAt > :now
              AND EXISTS (
                    SELECT availableItem.id
                    FROM DealItem availableItem
                    WHERE availableItem.deal = deal
                      AND availableItem.remainingQuantity > 0
              )
              AND (
                    :keyword IS NULL
                    OR LOWER(CAST(deal.description AS String)) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(deal.store.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(deal.store.address) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR EXISTS (
                        SELECT item.id
                        FROM DealItem item
                        WHERE item.deal = deal
                          AND item.remainingQuantity > 0
                          AND LOWER(item.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    )
              )
            """,
            countQuery = """
            SELECT COUNT(deal)
            FROM Deal deal
            WHERE deal.status = :status
              AND deal.salesEndsAt > :now
              AND EXISTS (
                    SELECT availableItem.id
                    FROM DealItem availableItem
                    WHERE availableItem.deal = deal
                      AND availableItem.remainingQuantity > 0
              )
              AND (
                    :keyword IS NULL
                    OR LOWER(CAST(deal.description AS String)) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(deal.store.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(deal.store.address) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR EXISTS (
                        SELECT item.id
                        FROM DealItem item
                        WHERE item.deal = deal
                          AND item.remainingQuantity > 0
                          AND LOWER(item.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    )
              )
            """)
    Page<Deal> findSellingDealsByKeyword(@Param("status") DealStatus status,
                                         @Param("now") LocalDateTime now,
                                         @Param("keyword") String keyword,
                                         Pageable pageable);

    @Query(value = """
            SELECT d.id AS dealId,
                   ST_Distance_Sphere(
                       POINT(s.longitude, s.latitude),
                       POINT(:centerLng, :centerLat)
                   ) AS distanceMeters
            FROM deals d
            JOIN stores s ON s.id = d.store_id
            WHERE d.status = :status
              AND d.sales_ends_at > :now
              AND EXISTS (
                    SELECT 1
                    FROM deal_items available_item
                    WHERE available_item.deal_id = d.id
                      AND available_item.remaining_quantity > 0
              )
              AND s.latitude IS NOT NULL
              AND s.longitude IS NOT NULL
              AND ST_Distance_Sphere(
                      POINT(s.longitude, s.latitude),
                      POINT(:centerLng, :centerLat)
                  ) <= :radiusMeters
              AND (
                    :keyword IS NULL
                    OR LOWER(d.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(s.address) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR EXISTS (
                        SELECT 1
                        FROM deal_items search_item
                        WHERE search_item.deal_id = d.id
                          AND search_item.remaining_quantity > 0
                          AND LOWER(search_item.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    )
              )
            ORDER BY distanceMeters ASC, d.id DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM deals d
            JOIN stores s ON s.id = d.store_id
            WHERE d.status = :status
              AND d.sales_ends_at > :now
              AND EXISTS (
                    SELECT 1
                    FROM deal_items available_item
                    WHERE available_item.deal_id = d.id
                      AND available_item.remaining_quantity > 0
              )
              AND s.latitude IS NOT NULL
              AND s.longitude IS NOT NULL
              AND ST_Distance_Sphere(
                      POINT(s.longitude, s.latitude),
                      POINT(:centerLng, :centerLat)
                  ) <= :radiusMeters
              AND (
                    :keyword IS NULL
                    OR LOWER(d.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(s.address) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR EXISTS (
                        SELECT 1
                        FROM deal_items search_item
                        WHERE search_item.deal_id = d.id
                          AND search_item.remaining_quantity > 0
                          AND LOWER(search_item.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    )
              )
            """,
            nativeQuery = true)
    Page<DealDistanceRow> findSellingDealIdsWithinRadius(@Param("status") String status,
                                                         @Param("now") LocalDateTime now,
                                                         @Param("centerLat") BigDecimal centerLat,
                                                         @Param("centerLng") BigDecimal centerLng,
                                                         @Param("radiusMeters") int radiusMeters,
                                                         @Param("keyword") String keyword,
                                                         Pageable pageable);

    @EntityGraph(attributePaths = {"store", "items"})
    @Query("SELECT DISTINCT deal FROM Deal deal WHERE deal.id IN :dealIds")
    List<Deal> findAllWithStoreAndItemsByIdIn(@Param("dealIds") Collection<Long> dealIds);

    @EntityGraph(attributePaths = "store")
    @Query(value = """
            SELECT deal
            FROM Deal deal
            WHERE deal.store.id = :storeId
              AND deal.status = :status
              AND deal.salesEndsAt > :salesEndsAt
              AND EXISTS (
                    SELECT availableItem.id
                    FROM DealItem availableItem
                    WHERE availableItem.deal = deal
                      AND availableItem.remainingQuantity > 0
              )
            """,
            countQuery = """
            SELECT COUNT(deal)
            FROM Deal deal
            WHERE deal.store.id = :storeId
              AND deal.status = :status
              AND deal.salesEndsAt > :salesEndsAt
              AND EXISTS (
                    SELECT availableItem.id
                    FROM DealItem availableItem
                    WHERE availableItem.deal = deal
                      AND availableItem.remainingQuantity > 0
              )
            """)
    Page<Deal> findByStoreIdAndStatusAndSalesEndsAtAfter(@Param("storeId") Long storeId,
                                                         @Param("status") DealStatus status,
                                                         @Param("salesEndsAt") LocalDateTime salesEndsAt,
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

    @Query("SELECT d.store.id, COUNT(d) FROM Deal d " +
           "WHERE d.store.id IN :storeIds AND d.status = :status AND d.salesEndsAt > :now " +
           "AND EXISTS (SELECT availableItem.id FROM DealItem availableItem " +
           "WHERE availableItem.deal = d AND availableItem.remainingQuantity > 0) " +
           "GROUP BY d.store.id")
    List<Object[]> countActiveDealsByStoreIds(
            @Param("storeIds") java.util.Collection<Long> storeIds,
            @Param("status") DealStatus status,
            @Param("now") LocalDateTime now
    );
}
