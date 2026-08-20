package com.namatdang.namatdang.reservation.service;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.reservation.dto.ReservationDetailResponseDto;
import com.namatdang.namatdang.reservation.dto.ReservationPageResponseDto;
import com.namatdang.namatdang.reservation.entity.Reservation;
import com.namatdang.namatdang.reservation.entity.ReservationStatus;
import com.namatdang.namatdang.reservation.repository.ReservationRepository;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사장님의 예약 조회와 수령 완료를 담당한다.
 * <p>
 * 수령 완료는 Owner를 일반 조회한 뒤 Reservation 행을 잠그고, 잠금을 얻은 뒤에 매장 소유권과
 * 상태를 다시 확인한다. 취소와 같은 행을 두고 경합하므로 소유권 판단도 잠금 이후여야
 * 확정된 상태를 보게 된다.
 */
@Service
@RequiredArgsConstructor
public class OwnerReservationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReservationRepository reservationRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    @Transactional
    public ReservationDetailResponseDto pickUpReservation(Long userId, Long reservationId) {
        User owner = findOwnerById(userId);

        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.RESERVATION_NOT_FOUND));

        validateStoreOwnership(owner.getId(), reservation);

        // 이미 수령 완료된 예약이면 false를 돌려주고 현재 결과를 그대로 반환한다.
        // 취소된 예약이면 RESERVATION_NOT_PICKUPABLE로 거절한다.
        reservation.pickUp(LocalDateTime.now());

        return ReservationDetailResponseDto.from(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationPageResponseDto getStoreReservations(Long userId,
                                                           Long storeId,
                                                           ReservationStatus status,
                                                           int page,
                                                           int size) {
        validatePageRequest(page, size);

        User owner = findOwnerById(userId);
        Store store = findStoreByIdAndOwnerId(storeId, owner.getId());

        Pageable pageable = createPageable(page, size);
        Page<Reservation> reservations = status == null
                ? reservationRepository.findByDealStoreId(store.getId(), pageable)
                : reservationRepository.findByDealStoreIdAndStatus(store.getId(), status, pageable);

        return ReservationPageResponseDto.from(reservations);
    }

    @Transactional(readOnly = true)
    public ReservationDetailResponseDto getStoreReservation(Long userId, Long reservationId) {
        User owner = findOwnerById(userId);

        Reservation reservation = reservationRepository.findWithItemsById(reservationId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.RESERVATION_NOT_FOUND));

        validateStoreOwnership(owner.getId(), reservation);

        return ReservationDetailResponseDto.from(reservation);
    }

    private User findOwnerById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.USER_NOT_FOUND));

        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
        }

        return user;
    }

    private Store findStoreByIdAndOwnerId(Long storeId, Long ownerId) {
        return storeRepository.findByIdAndOwnerId(storeId, ownerId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.STORE_NOT_FOUND));
    }

    private void validateStoreOwnership(Long ownerId, Reservation reservation) {
        if (!reservation.getDeal().getStore().getOwner().getId().equals(ownerId)) {
            throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
        }
    }

    private Pageable createPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
        }
    }
}
