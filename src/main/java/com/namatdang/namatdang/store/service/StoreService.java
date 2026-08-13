package com.namatdang.namatdang.store.service;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.store.dto.StorePageResponseDto;
import com.namatdang.namatdang.store.dto.StoreResponseDto;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
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
