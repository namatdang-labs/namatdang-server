package com.namatdang.namatdang.push.repository;

import com.namatdang.namatdang.push.entity.FcmRegistration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FcmRegistrationRepository extends JpaRepository<FcmRegistration, Long> {

    Optional<FcmRegistration> findByRegistrationToken(String registrationToken);

    Optional<FcmRegistration> findByIdAndUser_Id(Long id, Long userId);

    List<FcmRegistration> findAllByUser_IdOrderByIdAsc(Long userId);
}
