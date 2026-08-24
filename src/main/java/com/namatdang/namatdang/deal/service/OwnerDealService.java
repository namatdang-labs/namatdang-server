package com.namatdang.namatdang.deal.service;

import com.namatdang.namatdang.deal.dto.DealCreateRequestDto;
import com.namatdang.namatdang.deal.dto.DealDetailResponseDto;
import com.namatdang.namatdang.deal.dto.DealItemCreateRequestDto;
import com.namatdang.namatdang.deal.dto.DealPageResponseDto;
import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealStatus;
import com.namatdang.namatdang.deal.repository.DealRepository;
import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.media.ImageKind;
import com.namatdang.namatdang.media.ImageMediaService;
import com.namatdang.namatdang.notification.event.NotificationEventRecorder;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class OwnerDealService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Duration MIN_SALES_END_GAP = Duration.ofMinutes(10);
    private static final Duration MAX_SALES_END_GAP = Duration.ofHours(24);

    private final DealRepository dealRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final NotificationEventRecorder notificationEventRecorder;
    private final ImageMediaService imageMediaService;
    private final TransactionOperations transactions;

    public DealDetailResponseDto createDeal(Long userId, Long storeId, DealCreateRequestDto requestDto,
                                            MultipartFile image) {
        if (image == null) {
            return inTransaction(() -> createDealInTransaction(userId, storeId, requestDto, null));
        }

        inTransaction(() -> {
            User owner = findOwnerById(userId);
            findStoreByIdAndOwnerId(storeId, owner.getId());
            validateSalesEndsAt(requestDto.getSalesEndsAt());
            return Boolean.TRUE;
        });

        String newImageKey = imageMediaService.store(ImageKind.DEAL, image);
        try {
            return inTransaction(() -> createDealInTransaction(userId, storeId, requestDto, newImageKey));
        } catch (RuntimeException exception) {
            imageMediaService.deleteImmediately(newImageKey);
            throw exception;
        }
    }

    private DealDetailResponseDto createDealInTransaction(Long userId, Long storeId,
                                                          DealCreateRequestDto requestDto,
                                                          String imageKey) {
        User owner = findOwnerById(userId);
        Store store = findStoreByIdAndOwnerId(storeId, owner.getId());

        validateSalesEndsAt(requestDto.getSalesEndsAt());

        Deal deal = new Deal(store, requestDto.getSalesEndsAt(), requestDto.getDescription());
        for (DealItemCreateRequestDto itemRequestDto : requestDto.getItems()) {
            deal.addItem(itemRequestDto.toEntity());
        }

        deal.updateImageKey(imageKey);
        Deal savedDeal = dealRepository.saveAndFlush(deal);
        notificationEventRecorder.recordDealCreated(
                savedDeal.getId(),
                store.getId(),
                savedDeal.getCreatedAt()
        );

        return DealDetailResponseDto.from(savedDeal);
    }

    @Transactional(readOnly = true)
    public DealPageResponseDto getMyStoreDeals(Long userId, Long storeId, DealStatus status, int page, int size) {
        validatePageRequest(page, size);

        User owner = findOwnerById(userId);
        Store store = findStoreByIdAndOwnerId(storeId, owner.getId());

        Pageable pageable = createPageable(page, size);
        Page<Deal> deals = status == null
                ? dealRepository.findByStoreId(store.getId(), pageable)
                : dealRepository.findByStoreIdAndStatus(store.getId(), status, pageable);

        return DealPageResponseDto.from(deals);
    }

    @Transactional(readOnly = true)
    public DealDetailResponseDto getMyDeal(Long userId, Long dealId) {
        User owner = findOwnerById(userId);
        Deal deal = findDealById(dealId);
        validateOwnership(owner.getId(), deal);

        return DealDetailResponseDto.from(deal);
    }

    public DealDetailResponseDto updateImage(Long userId, Long dealId, MultipartFile image) {
        inTransaction(() -> {
            User owner = findOwnerById(userId);
            Deal deal = findDealByIdForUpdate(dealId);
            validateOwnership(owner.getId(), deal);
            return Boolean.TRUE;
        });

        String newImageKey = imageMediaService.store(ImageKind.DEAL, image);
        try {
            return inTransaction(() -> {
                User owner = findOwnerById(userId);
                Deal deal = findDealByIdForUpdate(dealId);
                validateOwnership(owner.getId(), deal);

                String previousKey = deal.getImageKey();
                deal.updateImageKey(newImageKey);
                imageMediaService.deleteAfterCommit(previousKey);
                return DealDetailResponseDto.from(deal);
            });
        } catch (RuntimeException exception) {
            imageMediaService.deleteImmediately(newImageKey);
            throw exception;
        }
    }

    private Pageable createPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }

    private User findOwnerById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.USER_NOT_FOUND));

        if (!user.hasRole(UserRole.OWNER)) {
            throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
        }

        return user;
    }

    private Store findStoreByIdAndOwnerId(Long storeId, Long ownerId) {
        return storeRepository.findByIdAndOwnerId(storeId, ownerId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.STORE_NOT_FOUND));
    }

    private Deal findDealById(Long dealId) {
        return dealRepository.findWithItemsById(dealId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.DEAL_NOT_FOUND));
    }

    private Deal findDealByIdForUpdate(Long dealId) {
        return dealRepository.findByIdForUpdate(dealId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.DEAL_NOT_FOUND));
    }

    private void validateOwnership(Long ownerId, Deal deal) {
        if (!deal.getStore().getOwner().getId().equals(ownerId)) {
            throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
        }
    }

    /**
     * DR-05: 판매 마감시각은 현재로부터 10분 이후 ~ 24시간 이내여야 한다.
     */
    private void validateSalesEndsAt(LocalDateTime salesEndsAt) {
        LocalDateTime now = LocalDateTime.now();

        if (salesEndsAt.isBefore(now.plus(MIN_SALES_END_GAP))
                || salesEndsAt.isAfter(now.plus(MAX_SALES_END_GAP))) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
        }
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
        }
    }

    private <T> T inTransaction(Supplier<T> action) {
        return Objects.requireNonNull(transactions.execute(status -> action.get()));
    }
}
