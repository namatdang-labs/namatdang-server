package com.namatdang.namatdang.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealStatus;
import com.namatdang.namatdang.deal.repository.DealRepository;
import com.namatdang.namatdang.store.dto.StoreDetailResponseDto;
import com.namatdang.namatdang.store.dto.StoreMapRequestDto;
import com.namatdang.namatdang.store.dto.StoreMapResponseDto;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.store.service.StoreService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class StoreServiceTests {

    @Test
    void mapUsesOneBulkLatestDealImageLookupAndFallsBackToStoreImage() {
        StoreRepository storeRepository = mock(StoreRepository.class);
        DealRepository dealRepository = mock(DealRepository.class);
        StoreService storeService = new StoreService(storeRepository, dealRepository);
        Store dealImageStore = store(11L);
        Store fallbackStore = store(12L);
        fallbackStore.updateImageKey("images/stores/12/legacy.jpg");
        Deal latestDeal = imageDeal(dealImageStore, 31L, LocalDateTime.of(2026, 8, 24, 15, 30));
        StoreMapRequestDto request = new StoreMapRequestDto(
                new BigDecimal("35.0"), new BigDecimal("36.0"),
                new BigDecimal("128.0"), new BigDecimal("129.0"),
                false, null);

        when(storeRepository.findInBounds(
                request.getMinLat(), request.getMaxLat(), request.getMinLng(), request.getMaxLng(),
                new BigDecimal("35.5"), new BigDecimal("128.5"), 50))
                .thenReturn(List.of(dealImageStore, fallbackStore));
        when(dealRepository.countActiveDealsByStoreIds(
                eq(List.of(11L, 12L)), eq(DealStatus.SELLING), any(LocalDateTime.class)))
                .thenReturn(java.util.Collections.singletonList(new Object[]{11L, 1L}));
        when(dealRepository.findLatestImageDealsByStoreIds(List.of(11L, 12L), DealStatus.CANCELED))
                .thenReturn(List.of(latestDeal));

        List<StoreMapResponseDto> result = storeService.getStoresOnMap(request);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getImageUrl())
                .startsWith("/api/v1/deals/31/image?variant=thumbnail&v=");
        assertThat(result.get(0).getCardImageUrl())
                .startsWith("/api/v1/deals/31/image?variant=card&v=");
        assertThat(result.get(1).getImageUrl())
                .startsWith("/api/v1/stores/12/image?variant=thumbnail&v=");
        assertThat(result.get(1).getCardImageUrl())
                .startsWith("/api/v1/stores/12/image?variant=card&v=");
        verify(dealRepository).findLatestImageDealsByStoreIds(List.of(11L, 12L), DealStatus.CANCELED);
    }

    @Test
    void storeDetailUsesOneStoreLookupAndOneThreeItemDealImageLookup() {
        StoreRepository storeRepository = mock(StoreRepository.class);
        DealRepository dealRepository = mock(DealRepository.class);
        StoreService storeService = new StoreService(storeRepository, dealRepository);
        Store store = store(11L);
        Deal recentDeal = imageDeal(store, 31L, LocalDateTime.of(2026, 8, 24, 15, 30));
        Pageable expectedPageable = Pageable.ofSize(3);

        when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));
        when(dealRepository.findRecentImageDealsByStoreId(
                store.getId(), DealStatus.CANCELED, expectedPageable))
                .thenReturn(List.of(recentDeal));

        StoreDetailResponseDto result = storeService.getStore(store.getId());

        assertThat(result.getRecentDealImages()).hasSize(1);
        assertThat(result.getRecentDealImages().getFirst().getDealId()).isEqualTo(recentDeal.getId());
        verify(storeRepository).findById(store.getId());
        verify(dealRepository).findRecentImageDealsByStoreId(
                store.getId(), DealStatus.CANCELED, expectedPageable);
        verifyNoMoreInteractions(storeRepository, dealRepository);
    }

    private Store store(Long id) {
        Store store = new Store(
                null,
                "남았당 베이커리",
                "대구광역시 중구 국채보상로 1",
                "1층",
                "053-123-4567",
                "매장 설명",
                new BigDecimal("35.8714354"),
                new BigDecimal("128.6014450")
        );
        ReflectionTestUtils.setField(store, "id", id);
        return store;
    }

    private Deal imageDeal(Store store, Long id, LocalDateTime createdAt) {
        Deal deal = new Deal(store, createdAt.plusHours(2), "딜 설명");
        deal.updateImageKey("images/deals/%d/photo.jpg".formatted(id));
        ReflectionTestUtils.setField(deal, "id", id);
        ReflectionTestUtils.setField(deal, "createdAt", createdAt);
        return deal;
    }
}
