package com.namatdang.namatdang.store.service;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.media.ImageKind;
import com.namatdang.namatdang.media.service.ImageMediaService;
import com.namatdang.namatdang.store.dto.StoreCreateRequestDto;
import com.namatdang.namatdang.store.dto.StoreResponseDto;
import com.namatdang.namatdang.store.dto.StoreUpdateRequestDto;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class OwnerStoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final ImageMediaService imageMediaService;
    private final TransactionOperations transactions;

    public StoreResponseDto createStore(Long userId, StoreCreateRequestDto requestDto, MultipartFile image) {
        if (image == null) {
            return inTransaction(() -> createStoreInTransaction(userId, requestDto, null));
        }

        inTransaction(() -> {
            findConsumerByIdForUpdate(userId);
            return Boolean.TRUE;
        });

        String newImageKey = imageMediaService.store(ImageKind.STORE, image);
        try {
            return inTransaction(() -> createStoreInTransaction(userId, requestDto, newImageKey));
        } catch (RuntimeException exception) {
            imageMediaService.deleteImmediately(newImageKey);
            throw exception;
        }
    }

    private StoreResponseDto createStoreInTransaction(Long userId, StoreCreateRequestDto requestDto,
                                                       String imageKey) {
        User owner = findConsumerByIdForUpdate(userId);
        owner.grantOwnerRole();

        Store store = requestDto.toEntity(owner);
        store.updateImageKey(imageKey);
        Store savedStore = storeRepository.saveAndFlush(store);

        return StoreResponseDto.from(savedStore);
    }

    @Transactional(readOnly = true)
    public List<StoreResponseDto> getMyStores(Long userId) {
        User owner = findOwnerById(userId);

        List<Store> stores = storeRepository.findAllByOwnerIdOrderByIdAsc(owner.getId());

        return stores.stream()
                .map(StoreResponseDto::from)
                .toList();
    }

    @Transactional
    public StoreResponseDto updateStore(Long userId, Long storeId, StoreUpdateRequestDto requestDto) {
        User owner = findOwnerById(userId);
        validateHasUpdates(requestDto);

        Store store = findStoreByIdAndOwnerIdForUpdate(storeId, owner.getId());
        store.updateInfo(requestDto.getName(),
                         requestDto.getAddress(),
                         requestDto.getAddressDetail(),
                         requestDto.getPhoneNumber(),
                         requestDto.getDescription());
        store.updateLocation(requestDto.getLatitude(), requestDto.getLongitude());

        return StoreResponseDto.from(store);
    }

    public StoreResponseDto updateImage(Long userId, Long storeId, MultipartFile image) {
        inTransaction(() -> {
            User owner = findOwnerById(userId);
            findStoreByIdAndOwnerIdForUpdate(storeId, owner.getId());
            return Boolean.TRUE;
        });

        String newImageKey = imageMediaService.store(ImageKind.STORE, image);
        try {
            return inTransaction(() -> {
                User owner = findOwnerById(userId);
                Store store = findStoreByIdAndOwnerIdForUpdate(storeId, owner.getId());

                String previousKey = store.getImageKey();
                store.updateImageKey(newImageKey);
                imageMediaService.deleteAfterCommit(previousKey);
                return StoreResponseDto.from(store);
            });
        } catch (RuntimeException exception) {
            imageMediaService.deleteImmediately(newImageKey);
            throw exception;
        }
    }

    @Transactional
    public void deleteImage(Long userId, Long storeId) {
        User owner = findOwnerById(userId);
        Store store = findStoreByIdAndOwnerIdForUpdate(storeId, owner.getId());

        String previousKey = store.getImageKey();
        store.updateImageKey(null);
        imageMediaService.deleteAfterCommit(previousKey);
    }

    private User findOwnerById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.USER_NOT_FOUND));

        if (!user.hasRole(UserRole.OWNER)) {
            throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
        }

        return user;
    }

    private User findConsumerByIdForUpdate(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.USER_NOT_FOUND));

        if (!user.hasRole(UserRole.CONSUMER)) {
            throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
        }

        return user;
    }

    private Store findStoreByIdAndOwnerIdForUpdate(Long storeId, Long ownerId) {
        return storeRepository.findByIdAndOwnerIdForUpdate(storeId, ownerId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.STORE_NOT_FOUND));
    }

    private void validateHasUpdates(StoreUpdateRequestDto requestDto) {
        if (!requestDto.hasUpdates()) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
        }
    }

    private <T> T inTransaction(Supplier<T> action) {
        return Objects.requireNonNull(transactions.execute(status -> action.get()));
    }
}
