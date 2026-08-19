package com.namatdang.namatdang.notification.recipient;

import java.util.List;

public interface FavoriteRecipientReader {

    List<Long> findUserIdsByStoreId(Long storeId);
}
