package com.namatdang.namatdang.media.service;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.repository.DealRepository;
import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.media.ImageVariant;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImageQueryService {

    private final StoreRepository storeRepository;
    private final DealRepository dealRepository;
    private final ImageMediaService imageMediaService;

    public ImageContent getStoreImage(Long storeId, ImageVariant variant, String version) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.STORE_NOT_FOUND));
        if (store.getImageKey() == null) {
            throw new BusinessLogicException(ExceptionCode.IMAGE_NOT_FOUND);
        }
        return imageMediaService.load(store.getImageKey(), variant, version);
    }

    public ImageContent getDealImage(Long dealId, ImageVariant variant, String version) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.DEAL_NOT_FOUND));
        if (deal.getImageKey() == null) {
            throw new BusinessLogicException(ExceptionCode.IMAGE_NOT_FOUND);
        }
        return imageMediaService.load(deal.getImageKey(), variant, version);
    }
}
