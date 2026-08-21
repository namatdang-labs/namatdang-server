package com.namatdang.namatdang.store.service;

import com.namatdang.namatdang.deal.entity.DealStatus;
import com.namatdang.namatdang.deal.repository.DealRepository;
import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.store.dto.StoreMapRequestDto;
import com.namatdang.namatdang.store.dto.StoreMapResponseDto;
import com.namatdang.namatdang.store.dto.StorePageResponseDto;
import com.namatdang.namatdang.store.dto.StoreResponseDto;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StoreService {

    private static final int MAX_PAGE_SIZE = 100;

    private final StoreRepository storeRepository;
    private final DealRepository dealRepository;

    @Transactional(readOnly = true)
    public StorePageResponseDto getStores(String keyword, int page, int size) {
        validatePageRequest(page, size);

        Pageable pageable = createPageable(page, size);
        Page<Store> stores = findStores(keyword, pageable);

        return StorePageResponseDto.from(stores);
    }

    @Transactional(readOnly = true)
    public StoreResponseDto getStore(Long storeId) {
        Store store = findStoreById(storeId);

        return StoreResponseDto.from(store);
    }

    @Transactional(readOnly = true)
    public List<StoreMapResponseDto> getStoresOnMap(StoreMapRequestDto requestDto) {
        requestDto.validateBounds();

        LocalDateTime now = LocalDateTime.now();
        List<Store> stores = findStoresInBounds(requestDto, now);

        if (stores.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> storeIds = stores.stream().map(Store::getId).toList();
        Map<Long, Long> activeDealCountMap = getActiveDealCounts(storeIds, now);

        return stores.stream()
                .map(store -> StoreMapResponseDto.of(store, activeDealCountMap.getOrDefault(store.getId(), 0L)))
                .toList();
    }

    private List<Store> findStoresInBounds(StoreMapRequestDto requestDto, LocalDateTime now) {
        boolean hasKeyword = StringUtils.hasText(requestDto.getKeyword());
        String normalizedKeyword = hasKeyword ? requestDto.getKeyword().strip() : null;
        boolean onlyDiscounting = Boolean.TRUE.equals(requestDto.getOnlyDiscounting());
        BigDecimal centerLat = midpoint(requestDto.getMinLat(), requestDto.getMaxLat());
        BigDecimal centerLng = midpoint(requestDto.getMinLng(), requestDto.getMaxLng());
        int limit = requestDto.getLimit();

        if (onlyDiscounting) {
            if (hasKeyword) {
                return storeRepository.findInBoundsWithActiveDealsAndKeyword(
                        requestDto.getMinLat(), requestDto.getMaxLat(),
                        requestDto.getMinLng(), requestDto.getMaxLng(),
                        normalizedKeyword, DealStatus.SELLING.name(), now,
                        centerLat, centerLng, limit);
            }
            return storeRepository.findInBoundsWithActiveDeals(
                    requestDto.getMinLat(), requestDto.getMaxLat(),
                    requestDto.getMinLng(), requestDto.getMaxLng(),
                    DealStatus.SELLING.name(), now, centerLat, centerLng, limit);
        }

        if (hasKeyword) {
            return storeRepository.findInBoundsWithKeyword(
                    requestDto.getMinLat(), requestDto.getMaxLat(),
                    requestDto.getMinLng(), requestDto.getMaxLng(),
                    normalizedKeyword, DealStatus.SELLING.name(), now,
                    centerLat, centerLng, limit);
        }

        return storeRepository.findInBounds(
                requestDto.getMinLat(), requestDto.getMaxLat(),
                requestDto.getMinLng(), requestDto.getMaxLng(),
                centerLat, centerLng, limit);
    }

    private BigDecimal midpoint(BigDecimal minimum, BigDecimal maximum) {
        return minimum.add(maximum).divide(BigDecimal.valueOf(2));
    }

    private Map<Long, Long> getActiveDealCounts(List<Long> storeIds, LocalDateTime now) {
        List<Object[]> results = dealRepository.countActiveDealsByStoreIds(storeIds, DealStatus.SELLING, now);
        Map<Long, Long> countMap = new HashMap<>();
        for (Object[] row : results) {
            Long storeId = (Long) row[0];
            Long count = ((Number) row[1]).longValue();
            countMap.put(storeId, count);
        }
        return countMap;
    }

    private Pageable createPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
    }

    private Page<Store> findStores(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return storeRepository.findAll(pageable);
        }

        String normalizedKeyword = keyword.strip();
        return storeRepository.findByNameContainingOrAddressContaining(normalizedKeyword,
                                                                       normalizedKeyword,
                                                                       pageable);
    }

    private Store findStoreById(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.STORE_NOT_FOUND));
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
        }
    }
}
