package com.namatdang.namatdang.deal.service;

import com.namatdang.namatdang.deal.dto.DealDetailResponseDto;
import com.namatdang.namatdang.deal.dto.DealPageResponseDto;
import com.namatdang.namatdang.deal.dto.DealResponseDto;
import com.namatdang.namatdang.deal.dto.DealSearchRequestDto;
import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealStatus;
import com.namatdang.namatdang.deal.repository.DealRepository;
import com.namatdang.namatdang.deal.repository.DealRepository.DealDistanceRow;
import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.store.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    private static final int MAX_PAGE_SIZE = DealSearchRequestDto.MAX_PAGE_SIZE;

    private final DealRepository dealRepository;
    private final StoreRepository storeRepository;

    /**
     * 자동 마감 배치가 없으므로 조회 시점에 마감시각으로 한 번 더 걸러 마감된 딜이 노출되지 않게 한다.
     * <p>
     * TODO: #27 - 자동 마감 배치가 들어오면 salesEndsAt 조건을 빼고 status = SELLING
     * 만으로 조회한다.
     */
    @Transactional(readOnly = true)
    public DealPageResponseDto getSellingDeals(DealSearchRequestDto requestDto) {
        requestDto.validate();

        LocalDateTime now = LocalDateTime.now();
        String keyword = requestDto.normalizedKeyword();

        if (requestDto.hasLocation()) {
            return getSellingDealsWithinRadius(requestDto, keyword, now);
        }

        Pageable pageable = createPageable(requestDto.getPage(), requestDto.getSize());
        Page<Deal> deals = keyword == null
                ? dealRepository.findByStatusAndSalesEndsAtAfter(DealStatus.SELLING, now, pageable)
                : dealRepository.findSellingDealsByKeyword(DealStatus.SELLING, now, keyword, pageable);

        return DealPageResponseDto.fromSelling(deals);
    }

    @Transactional(readOnly = true)
    public DealPageResponseDto getSellingDeals(int page, int size) {
        DealSearchRequestDto requestDto = new DealSearchRequestDto();
        requestDto.setPage(page);
        requestDto.setSize(size);
        return getSellingDeals(requestDto);
    }

    @Transactional(readOnly = true)
    public DealPageResponseDto getStoreDeals(Long storeId, int page, int size) {
        validatePageRequest(page, size);
        validateStoreExists(storeId);

        Pageable pageable = createPageable(page, size);
        Page<Deal> deals = dealRepository.findByStoreIdAndStatusAndSalesEndsAtAfter(storeId,
                                                                                   DealStatus.SELLING,
                                                                                   LocalDateTime.now(),
                                                                                   pageable);

        return DealPageResponseDto.fromSelling(deals);
    }

    @Transactional(readOnly = true)
    public DealDetailResponseDto getDeal(Long dealId) {
        Deal deal = dealRepository.findWithItemsById(dealId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.DEAL_NOT_FOUND));

        return DealDetailResponseDto.from(deal);
    }

    private DealPageResponseDto getSellingDealsWithinRadius(DealSearchRequestDto requestDto,
                                                             String keyword,
                                                             LocalDateTime now) {
        Pageable pageable = PageRequest.of(requestDto.getPage(), requestDto.getSize());
        Page<DealDistanceRow> distancePage = dealRepository.findSellingDealIdsWithinRadius(
                DealStatus.SELLING.name(),
                now,
                requestDto.getCenterLat(),
                requestDto.getCenterLng(),
                requestDto.getRadiusMeters(),
                keyword,
                pageable);

        if (distancePage.isEmpty()) {
            return DealPageResponseDto.from(distancePage, List.of());
        }

        List<Long> dealIds = distancePage.getContent().stream()
                .map(DealDistanceRow::getDealId)
                .toList();
        Map<Long, Deal> dealsById = dealRepository.findAllWithStoreAndItemsByIdIn(dealIds).stream()
                .collect(Collectors.toMap(Deal::getId, Function.identity()));

        List<DealResponseDto> content = distancePage.getContent().stream()
                .map(row -> DealResponseDto.fromSelling(dealsById.get(row.getDealId()),
                                                        Math.round(row.getDistanceMeters())))
                .toList();

        return DealPageResponseDto.from(distancePage, content);
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
