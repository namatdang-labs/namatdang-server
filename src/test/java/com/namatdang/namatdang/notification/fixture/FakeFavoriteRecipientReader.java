package com.namatdang.namatdang.notification.fixture;

import com.namatdang.namatdang.notification.recipient.FavoriteRecipientReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakeFavoriteRecipientReader implements FavoriteRecipientReader {

    private final Map<Long, List<Long>> recipientsByStoreId = new HashMap<>();

    @Override
    public List<Long> findUserIdsByStoreId(Long storeId) {
        return recipientsByStoreId.getOrDefault(storeId, List.of());
    }

    public void setRecipients(Long storeId, List<Long> userIds) {
        recipientsByStoreId.put(storeId, List.copyOf(userIds));
    }

    public void reset() {
        recipientsByStoreId.clear();
    }
}
