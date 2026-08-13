package com.namatdang.namatdang.store.service;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.store.dto.StoreCreateRequestDto;
import com.namatdang.namatdang.store.dto.StoreResponseDto;
import com.namatdang.namatdang.store.dto.StoreUpdateRequestDto;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnerStoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    @Transactional
    public StoreResponseDto createStore(Long userId, StoreCreateRequestDto request) {
        User owner = findOwner(userId);
        Store store = request.toEntity(owner);
        Store savedStore = storeRepository.save(store);

        return StoreResponseDto.from(savedStore);
    }

    @Transactional(readOnly = true)
    public List<StoreResponseDto> getMyStores(Long userId) {
        findOwner(userId);

        return storeRepository.findAllByOwnerIdOrderByIdAsc(userId).stream()
                .map(StoreResponseDto::from)
                .toList();
    }

    @Transactional
    public StoreResponseDto updateStore(Long userId, Long storeId, StoreUpdateRequestDto request) {
        findOwner(userId);
        validateUpdateRequest(request);

        Store store = storeRepository.findByIdAndOwnerId(storeId, userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.STORE_NOT_FOUND));

        store.update(
                normalize(request.getName()),
                normalize(request.getAddress()),
                normalize(request.getAddressDetail()),
                normalize(request.getPhoneNumber()),
                normalize(request.getDescription()),
                request.getLatitude(),
                request.getLongitude()
        );

        return StoreResponseDto.from(store);
    }

    private User findOwner(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.USER_NOT_FOUND));

        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
        }

        return user;
    }

    private void validateUpdateRequest(StoreUpdateRequestDto request) {
        if (request.hasNoValues()) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
