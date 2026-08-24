package com.namatdang.namatdang.store.repository;

import com.namatdang.namatdang.store.entity.Store;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface StoreRepository extends JpaRepository<Store, Long> {

    String ORDER_AND_LIMIT_BY_DISTANCE_FROM_CENTER = """
             ORDER BY ST_Distance_Sphere(
                 POINT(s.longitude, s.latitude),
                 POINT(:centerLng, :centerLat)
             ) ASC, s.id ASC
             LIMIT :limit
            """;

    boolean existsByOwnerId(Long ownerId);

    List<Store> findAllByOwnerIdOrderByIdAsc(Long ownerId);

    Optional<Store> findByIdAndOwnerId(Long storeId, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select store from Store store where store.id = :storeId and store.owner.id = :ownerId")
    Optional<Store> findByIdAndOwnerIdForUpdate(@Param("storeId") Long storeId,
                                                @Param("ownerId") Long ownerId);

    Page<Store> findByNameContainingOrAddressContaining(String nameKeyword,
                                                        String addressKeyword,
                                                        Pageable pageable);

    @Query(value = """
            SELECT s.*
            FROM stores s
            WHERE s.latitude BETWEEN :minLat AND :maxLat
              AND s.longitude BETWEEN :minLng AND :maxLng
            """ + ORDER_AND_LIMIT_BY_DISTANCE_FROM_CENTER, nativeQuery = true)
    List<Store> findInBounds(
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng,
            @Param("centerLat") BigDecimal centerLat,
            @Param("centerLng") BigDecimal centerLng,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT s.*
            FROM stores s
            WHERE s.latitude BETWEEN :minLat AND :maxLat
              AND s.longitude BETWEEN :minLng AND :maxLng
              AND (
                    LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(s.address) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR EXISTS (
                        SELECT 1
                        FROM deals keyword_deal
                        WHERE keyword_deal.store_id = s.id
                          AND keyword_deal.status = :dealStatus
                          AND keyword_deal.sales_ends_at > :now
                          AND EXISTS (
                              SELECT 1
                              FROM deal_items available_item
                              WHERE available_item.deal_id = keyword_deal.id
                                AND available_item.remaining_quantity > 0
                          )
                          AND (
                                LOWER(keyword_deal.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                OR EXISTS (
                                    SELECT 1
                                    FROM deal_items keyword_item
                                    WHERE keyword_item.deal_id = keyword_deal.id
                                      AND keyword_item.remaining_quantity > 0
                                      AND LOWER(keyword_item.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                )
                          )
                    )
              )
            """ + ORDER_AND_LIMIT_BY_DISTANCE_FROM_CENTER, nativeQuery = true)
    List<Store> findInBoundsWithKeyword(
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng,
            @Param("keyword") String keyword,
            @Param("dealStatus") String dealStatus,
            @Param("now") LocalDateTime now,
            @Param("centerLat") BigDecimal centerLat,
            @Param("centerLng") BigDecimal centerLng,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT s.*
            FROM stores s
            WHERE s.latitude BETWEEN :minLat AND :maxLat
              AND s.longitude BETWEEN :minLng AND :maxLng
              AND EXISTS (
                  SELECT 1
                  FROM deals d
                  WHERE d.store_id = s.id
                    AND d.status = :dealStatus
                    AND d.sales_ends_at > :now
                    AND EXISTS (
                        SELECT 1
                        FROM deal_items available_item
                        WHERE available_item.deal_id = d.id
                          AND available_item.remaining_quantity > 0
                    )
              )
            """ + ORDER_AND_LIMIT_BY_DISTANCE_FROM_CENTER, nativeQuery = true)
    List<Store> findInBoundsWithActiveDeals(
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng,
            @Param("dealStatus") String dealStatus,
            @Param("now") LocalDateTime now,
            @Param("centerLat") BigDecimal centerLat,
            @Param("centerLng") BigDecimal centerLng,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT s.*
            FROM stores s
            WHERE s.latitude BETWEEN :minLat AND :maxLat
              AND s.longitude BETWEEN :minLng AND :maxLng
              AND EXISTS (
                  SELECT 1
                  FROM deals d
                  WHERE d.store_id = s.id
                    AND d.status = :dealStatus
                    AND d.sales_ends_at > :now
                    AND EXISTS (
                        SELECT 1
                        FROM deal_items available_item
                        WHERE available_item.deal_id = d.id
                          AND available_item.remaining_quantity > 0
                    )
                    AND (
                          LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(s.address) LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(d.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR EXISTS (
                              SELECT 1
                              FROM deal_items keyword_item
                              WHERE keyword_item.deal_id = d.id
                                AND keyword_item.remaining_quantity > 0
                                AND LOWER(keyword_item.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                          )
                    )
              )
            """ + ORDER_AND_LIMIT_BY_DISTANCE_FROM_CENTER, nativeQuery = true)
    List<Store> findInBoundsWithActiveDealsAndKeyword(
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng,
            @Param("keyword") String keyword,
            @Param("dealStatus") String dealStatus,
            @Param("now") LocalDateTime now,
            @Param("centerLat") BigDecimal centerLat,
            @Param("centerLng") BigDecimal centerLng,
            @Param("limit") int limit
    );
}
