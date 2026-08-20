package com.namatdang.namatdang.favorite.repository;

import com.namatdang.namatdang.favorite.entity.Favorite;
import com.namatdang.namatdang.favorite.entity.FavoriteId;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {

    @Modifying
    @Query(value = """
            INSERT INTO favorites (user_id, store_id, created_at)
            VALUES (:userId, :storeId, CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE created_at = created_at
            """, nativeQuery = true)
    void addIfAbsent(@Param("userId") Long userId, @Param("storeId") Long storeId);

    @EntityGraph(attributePaths = "store")
    @Query("""
            SELECT favorite
            FROM Favorite favorite
            WHERE favorite.user.id = :userId
            ORDER BY favorite.createdAt ASC, favorite.store.id ASC
            """)
    List<Favorite> findAllByUserIdInRegistrationOrder(@Param("userId") Long userId);

    @Query("""
            SELECT favorite.user.id
            FROM Favorite favorite
            WHERE favorite.store.id = :storeId
            ORDER BY favorite.user.id ASC
            """)
    List<Long> findUserIdsByStoreId(@Param("storeId") Long storeId);

    long deleteByUserIdAndStoreId(Long userId, Long storeId);

    void deleteAllByUserId(Long userId);
}
