package com.namatdang.namatdang.favorite.repository;

import com.namatdang.namatdang.favorite.entity.Favorite;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO favorites (user_id, store_id, created_at)
            VALUES (:userId, :storeId, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void addIfAbsent(@Param("userId") Long userId, @Param("storeId") Long storeId);

    @EntityGraph(attributePaths = "store")
    List<Favorite> findAllByUserIdOrderByIdAsc(Long userId);

    long deleteByUserIdAndStoreId(Long userId, Long storeId);

    void deleteAllByUserId(Long userId);
}
