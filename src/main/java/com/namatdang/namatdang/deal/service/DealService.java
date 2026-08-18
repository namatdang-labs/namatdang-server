package com.namatdang.namatdang.deal.service;

import com.namatdang.namatdang.deal.dto.DealDetailResponseDto;
import com.namatdang.namatdang.deal.dto.DealPageResponseDto;
import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealStatus;
import com.namatdang.namatdang.deal.repository.DealRepository;
import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.store.repository.StoreRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DealService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DealRepository dealRepository;
    private final StoreRepository storeRepository;

    /**
     * 자동 마감 배치가 없으므로 조회 시점에 마감시각으로 한 번 더 걸러 마감된 딜이 노출되지 않게 한다.
     * <p>
     * TODO: #27 - 자동 마감 배치가 들어오면 pickupDeadline 조건을 빼고 status = SELLING
     * 만으로 조회한다.
     */
    @Transactional(readOnly = true)
    public DealPageResponseDto getSellingDeals(int page, int size) {
        validatePageRequest(page, size);

        Pageable pageable = createPageable(page, size);
        Page<Deal> deals = dealRepository.findByStatusAndPickupDeadlineAfter(DealStatus.SELLING,
                                                                             LocalDateTime.now(),
                                                                             pageable);

        return DealPageResponseDto.from(deals);
    }

    @Transactional(readOnly = true)
    public DealPageResponseDto getStoreDeals(Long storeId, int page, int size) {
        validatePageRequest(page, size);
        validateStoreExists(storeId);

        Pageable pageable = createPageable(page, size);
        Page<Deal> deals = dealRepository.findByStoreIdAndStatusAndPickupDeadlineAfter(storeId,
                                                                                       DealStatus.SELLING,
                                                                                       LocalDateTime.now(),
                                                                                       pageable);

        return DealPageResponseDto.from(deals);
    }

    @Transactional(readOnly = true)
    public DealDetailResponseDto getDeal(Long dealId) {
        Deal deal = dealRepository.findWithItemsById(dealId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.DEAL_NOT_FOUND));

        return DealDetailResponseDto.from(deal);
    }

    private Pageable createPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }

    private void validateStoreExists(Long storeId) {
        if (!storeRepository.existsById(storeId)) {
            throw new BusinessLogicException(ExceptionCode.STORE_NOT_FOUND);
        }
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
        }
    }
}
