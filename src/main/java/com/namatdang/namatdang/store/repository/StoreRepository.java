package com.namatdang.namatdang.store.repository;

import com.namatdang.namatdang.store.entity.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    boolean existsByOwnerId(Long ownerId);

    List<Store> findAllByOwnerIdOrderByIdAsc(Long ownerId);

    Optional<Store> findByIdAndOwnerId(Long storeId, Long ownerId);

    Page<Store> findByNameContainingOrAddressContaining(String nameKeyword,
                                                        String addressKeyword,
                                                        Pageable pageable);
}
