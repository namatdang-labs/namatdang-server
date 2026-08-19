package com.namatdang.namatdang.favorite.adapter;

import com.namatdang.namatdang.favorite.repository.FavoriteRepository;
import com.namatdang.namatdang.notification.recipient.FavoriteRecipientReader;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FavoriteNotificationRecipientAdapter implements FavoriteRecipientReader {

    private final FavoriteRepository favoriteRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Long> findUserIdsByStoreId(Long storeId) {
        return favoriteRepository.findUserIdsByStoreId(storeId);
    }
}
