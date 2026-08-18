package com.namatdang.namatdang.push.repository;

import com.namatdang.namatdang.push.entity.FcmRegistration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FcmRegistrationRepository extends JpaRepository<FcmRegistration, Long> {

    Optional<FcmRegistration> findByRegistrationToken(String registrationToken);

    Optional<FcmRegistration> findByIdAndUser_Id(Long id, Long userId);

    List<FcmRegistration> findAllByUser_IdOrderByIdAsc(Long userId);

    List<FcmRegistration> findAllByUser_IdInOrderByIdAsc(Collection<Long> userIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FcmRegistration registration WHERE registration.id = :registrationId")
    int deleteInvalidRegistration(@Param("registrationId") Long registrationId);
}
