package com.namatdang.namatdang.favorite.service;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.favorite.entity.Favorite;
import com.namatdang.namatdang.favorite.repository.FavoriteRepository;
import com.namatdang.namatdang.store.dto.StoreResponseDto;
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
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;

    @Transactional
    public void addFavorite(Long userId, Long storeId) {
        User consumer = findConsumerByIdForUpdate(userId);
        Store store = findStoreById(storeId);

        favoriteRepository.addIfAbsent(consumer.getId(), store.getId());
    }

    @Transactional(readOnly = true)
    public List<StoreResponseDto> getFavorites(Long userId) {
        User consumer = findConsumerById(userId);

        return favoriteRepository.findAllByUserIdInRegistrationOrder(consumer.getId()).stream()
                .map(Favorite::getStore)
                .map(StoreResponseDto::from)
                .toList();
    }

    @Transactional
    public void deleteFavorite(Long userId, Long storeId) {
        User consumer = findConsumerByIdForUpdate(userId);
        Store store = findStoreById(storeId);

        favoriteRepository.deleteByUserIdAndStoreId(consumer.getId(), store.getId());
    }

    private User findConsumerById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.USER_NOT_FOUND));

        validateConsumerRole(user);
        return user;
    }

    private User findConsumerByIdForUpdate(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.USER_NOT_FOUND));

        validateConsumerRole(user);
        return user;
    }

    private void validateConsumerRole(User user) {
        if (!user.hasRole(UserRole.CONSUMER)) {
            throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
        }
    }

    private Store findStoreById(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.STORE_NOT_FOUND));
    }
}
