package com.namatdang.namatdang.reservation.service;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealItem;
import com.namatdang.namatdang.deal.repository.DealItemRepository;
import com.namatdang.namatdang.deal.repository.DealRepository;
import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.idempotency.entity.IdempotencyOperation;
import com.namatdang.namatdang.idempotency.entity.IdempotencyRequest;
import com.namatdang.namatdang.idempotency.service.IdempotencyService;
import com.namatdang.namatdang.idempotency.service.IdempotentResponse;
import com.namatdang.namatdang.notification.event.NotificationEventRecorder;
import com.namatdang.namatdang.reservation.dto.ReservationCreateRequestDto;
import com.namatdang.namatdang.reservation.dto.ReservationDetailResponseDto;
import com.namatdang.namatdang.reservation.dto.ReservationItemCreateRequestDto;
import com.namatdang.namatdang.reservation.dto.ReservationPageResponseDto;
import com.namatdang.namatdang.reservation.entity.Reservation;
import com.namatdang.namatdang.reservation.entity.ReservationItem;
import com.namatdang.namatdang.reservation.entity.ReservationStatus;
import com.namatdang.namatdang.reservation.repository.ReservationRepository;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자의 예약 생성·조회·취소를 담당한다.
 * <p>
 * 쓰기 작업의 잠금 순서는 다음으로 고정한다. 순서를 바꾸면 다품목 동시 예약에서 데드락이 생긴다.
 * <ul>
 *   <li>생성: Customer → 멱등 확인 → Deal → DealItem 전체(id 오름차순)</li>
 *   <li>취소: Customer → 멱등 확인 → Reservation → Deal → DealItem 전체(id 오름차순)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReservationRepository reservationRepository;
    private final DealRepository dealRepository;
    private final DealItemRepository dealItemRepository;
    private final UserRepository userRepository;
    private final IdempotencyService idempotencyService;
    private final NotificationEventRecorder notificationEventRecorder;

    @Transactional
    public IdempotentResponse createReservation(Long userId,
                                                String idempotencyKey,
                                                ReservationCreateRequestDto requestDto) {
        User consumer = lockConsumer(userId);

        IdempotencyRequest idempotencyRequest = idempotencyService.acquire(
                consumer,
                IdempotencyOperation.RESERVATION_CREATE,
                idempotencyKey,
                idempotencyService.hash(requestDto.toCanonicalForm()));

        if (idempotencyRequest.isCompleted()) {
            return IdempotentResponse.replayed(idempotencyRequest.getResponseStatus(),
                                               idempotencyRequest.getResponseBody());
        }

        validateNoDuplicateItems(requestDto.getItems());

        // 잠금 순서를 지키려고 Deal을 먼저 잠근 뒤 품목 전체를 id 오름차순으로 잠근다.
        Deal deal = dealRepository.findByIdForUpdate(requestDto.getDealId())
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.DEAL_NOT_FOUND));
        List<DealItem> lockedItems = dealItemRepository.findByDealIdForUpdate(deal.getId());

        // 잠금 후에 다시 확인해야 다른 트랜잭션이 방금 바꾼 상태와 재고를 반영할 수 있다.
        if (!deal.isReservable(LocalDateTime.now())) {
            throw new BusinessLogicException(ExceptionCode.DEAL_NOT_RESERVABLE);
        }

        if (reservationRepository.existsByConsumerIdAndDealId(consumer.getId(), deal.getId())) {
            throw new BusinessLogicException(ExceptionCode.RESERVATION_ALREADY_EXISTS);
        }

        Map<Long, DealItem> itemsById = lockedItems.stream()
                .collect(Collectors.toMap(DealItem::getId, Function.identity()));

        // 한 품목이라도 부족하면 전체가 무산되어야 하므로(INV-07), 차감 전에 전 품목을 먼저 확인한다.
        for (ReservationItemCreateRequestDto itemRequest : requestDto.getItems()) {
            DealItem dealItem = itemsById.get(itemRequest.getDealItemId());

            if (dealItem == null) {
                throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
            }

            if (!dealItem.canDecrease(itemRequest.getQuantity())) {
                throw new BusinessLogicException(ExceptionCode.OUT_OF_STOCK);
            }
        }

        Reservation reservation = new Reservation(consumer, deal);
        for (ReservationItemCreateRequestDto itemRequest : requestDto.getItems()) {
            DealItem dealItem = itemsById.get(itemRequest.getDealItemId());
            dealItem.decrease(itemRequest.getQuantity());
            reservation.addItem(new ReservationItem(dealItem, itemRequest.getQuantity()));
        }

        if (isSoldOut(lockedItems)) {
            deal.markEnded();
        }

        Reservation savedReservation = reservationRepository.save(reservation);
        ReservationDetailResponseDto responseDto = ReservationDetailResponseDto.from(savedReservation);

        // 도메인 변경과 같은 트랜잭션에서 커밋되므로, 실패하면 예약·재고·멱등 기록이 함께 롤백된다.
        idempotencyService.complete(idempotencyRequest, 201, responseDto);
        notificationEventRecorder.recordReservationConfirmed(
                savedReservation.getId(),
                deal.getStore().getId(),
                savedReservation.getCreatedAt()
        );

        return IdempotentResponse.created(responseDto);
    }

    @Transactional
    public IdempotentResponse cancelReservation(Long userId, String idempotencyKey, Long reservationId) {
        User consumer = lockConsumer(userId);

        IdempotencyRequest idempotencyRequest = idempotencyService.acquire(
                consumer,
                IdempotencyOperation.RESERVATION_CANCEL,
                idempotencyKey,
                idempotencyService.hash(String.valueOf(reservationId)));

        if (idempotencyRequest.isCompleted()) {
            return IdempotentResponse.replayed(idempotencyRequest.getResponseStatus(),
                                               idempotencyRequest.getResponseBody());
        }

        // 수령 완료와 같은 예약 행을 잠근다. 먼저 잠금을 얻은 쪽만 상태를 확정한다(INV-06).
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.RESERVATION_NOT_FOUND));

        if (!reservation.isOwnedBy(consumer.getId())) {
            throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
        }

        Deal deal = dealRepository.findByIdForUpdate(reservation.getDeal().getId())
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.DEAL_NOT_FOUND));
        List<DealItem> lockedItems = dealItemRepository.findByDealIdForUpdate(deal.getId());

        // 이미 취소된 예약이면 false를 돌려주므로 수량을 두 번 복원하지 않는다(INV-03).
        boolean canceled = reservation.cancel(LocalDateTime.now());

        if (canceled) {
            restoreQuantities(reservation, lockedItems);
            resumeSellingIfPossible(deal, lockedItems);
        }

        ReservationDetailResponseDto responseDto = ReservationDetailResponseDto.from(reservation);
        idempotencyService.complete(idempotencyRequest, 200, responseDto);
        if (canceled) {
            notificationEventRecorder.recordReservationCanceled(
                    reservation.getId(),
                    deal.getStore().getId(),
                    reservation.getCanceledAt()
            );
        }

        return IdempotentResponse.ok(responseDto);
    }

    @Transactional(readOnly = true)
    public ReservationPageResponseDto getMyReservations(Long userId, ReservationStatus status, int page, int size) {
        validatePageRequest(page, size);

        User consumer = findConsumerById(userId);
        Pageable pageable = createPageable(page, size);

        Page<Reservation> reservations = status == null
                ? reservationRepository.findByConsumerId(consumer.getId(), pageable)
                : reservationRepository.findByConsumerIdAndStatus(consumer.getId(), status, pageable);

        return ReservationPageResponseDto.from(reservations);
    }

    @Transactional(readOnly = true)
    public ReservationDetailResponseDto getMyReservation(Long userId, Long reservationId) {
        User consumer = findConsumerById(userId);
        Reservation reservation = reservationRepository.findWithItemsById(reservationId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.RESERVATION_NOT_FOUND));

        if (!reservation.isOwnedBy(consumer.getId())) {
            throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
        }

        return ReservationDetailResponseDto.from(reservation);
    }

    /**
     * 예약 쓰기 요청의 가장 처음에 Customer 행을 잠근다. Customer 데이터를 바꾸려는 것이 아니라,
     * 같은 사용자의 생성·취소 요청을 직렬화해 같은 멱등키가 두 번 삽입되지 않게 하려는 잠금이다.
     */
    private User lockConsumer(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.USER_NOT_FOUND));

        if (user.getRole() != UserRole.CONSUMER) {
            throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
        }

        return user;
    }

    private User findConsumerById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.USER_NOT_FOUND));

        if (user.getRole() != UserRole.CONSUMER) {
            throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
        }

        return user;
    }

    private void restoreQuantities(Reservation reservation, List<DealItem> lockedItems) {
        Map<Long, DealItem> itemsById = lockedItems.stream()
                .collect(Collectors.toMap(DealItem::getId, Function.identity()));

        for (ReservationItem item : reservation.getItems()) {
            DealItem dealItem = itemsById.get(item.getDealItem().getId());
            dealItem.restore(item.getQuantity());
        }
    }

    /**
     * 판매 마감 전이고 판매 가능한 품목이 생겼다면 딜을 다시 판매중으로 되돌린다.
     * 마감 후 복원된 수량은 예약 가능 수량이 아니므로 마감 상태를 유지한다.
     */
    private void resumeSellingIfPossible(Deal deal, List<DealItem> lockedItems) {
        if (!deal.isEnded() || !deal.getSalesEndsAt().isAfter(LocalDateTime.now())) {
            return;
        }

        if (!isSoldOut(lockedItems)) {
            deal.markSelling();
        }
    }

    private boolean isSoldOut(List<DealItem> items) {
        return items.stream().allMatch(DealItem::isSoldOut);
    }

    private void validateNoDuplicateItems(List<ReservationItemCreateRequestDto> items) {
        Set<Long> dealItemIds = new HashSet<>();

        for (ReservationItemCreateRequestDto item : items) {
            if (!dealItemIds.add(item.getDealItemId())) {
                throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
            }
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
