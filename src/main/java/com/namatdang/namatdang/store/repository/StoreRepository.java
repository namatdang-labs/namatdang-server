package com.namatdang.namatdang.store.repository;

import com.namatdang.namatdang.deal.entity.DealStatus;
import com.namatdang.namatdang.store.entity.Store;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

    boolean existsByOwnerId(Long ownerId);

    List<Store> findAllByOwnerIdOrderByIdAsc(Long ownerId);

    Optional<Store> findByIdAndOwnerId(Long storeId, Long ownerId);

    Page<Store> findByNameContainingOrAddressContaining(String nameKeyword,
                                                        String addressKeyword,
                                                        Pageable pageable);

    @Query("SELECT s FROM Store s " +
           "WHERE s.latitude BETWEEN :minLat AND :maxLat " +
           "AND s.longitude BETWEEN :minLng AND :maxLng")
    List<Store> findInBounds(
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng
    );

    @Query("SELECT s FROM Store s " +
           "WHERE s.latitude BETWEEN :minLat AND :maxLat " +
           "AND s.longitude BETWEEN :minLng AND :maxLng " +
           "AND (s.name LIKE %:keyword% OR s.address LIKE %:keyword%)")
    List<Store> findInBoundsWithKeyword(
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng,
            @Param("keyword") String keyword
    );

    @Query("SELECT DISTINCT s FROM Store s " +
           "JOIN Deal d ON d.store = s " +
           "WHERE s.latitude BETWEEN :minLat AND :maxLat " +
           "AND s.longitude BETWEEN :minLng AND :maxLng " +
           "AND d.status = :dealStatus AND d.salesEndsAt > :now")
    List<Store> findInBoundsWithActiveDeals(
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng,
            @Param("dealStatus") DealStatus dealStatus,
            @Param("now") LocalDateTime now
    );

    @Query("SELECT DISTINCT s FROM Store s " +
           "JOIN Deal d ON d.store = s " +
           "WHERE s.latitude BETWEEN :minLat AND :maxLat " +
           "AND s.longitude BETWEEN :minLng AND :maxLng " +
           "AND (s.name LIKE %:keyword% OR s.address LIKE %:keyword%) " +
           "AND d.status = :dealStatus AND d.salesEndsAt > :now")
    List<Store> findInBoundsWithActiveDealsAndKeyword(
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng,
            @Param("keyword") String keyword,
            @Param("dealStatus") DealStatus dealStatus,
            @Param("now") LocalDateTime now
    );
}

