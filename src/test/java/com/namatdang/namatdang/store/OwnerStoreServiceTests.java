package com.namatdang.namatdang.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.namatdang.namatdang.media.ImageKind;
import com.namatdang.namatdang.media.ImageMediaService;
import com.namatdang.namatdang.media.TestImages;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.store.service.OwnerStoreService;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class OwnerStoreServiceTests {

    @Test
    void removesNewImageGroupWhenTheShortDatabaseTransactionFails() {
        StoreRepository storeRepository = mock(StoreRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ImageMediaService imageMediaService = mock(ImageMediaService.class);
        TransactionOperations transactions = mock(TransactionOperations.class);
        User owner = owner(1L);
        Store store = store(owner, 11L);
        store.updateImageKey("images/stores/11/old.jpg");
        MockMultipartFile image = new MockMultipartFile(
                "image", "replacement.jpg", MediaType.IMAGE_JPEG_VALUE, TestImages.jpeg());

        when(transactions.execute(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(mock(TransactionStatus.class));
                })
                .thenThrow(new IllegalStateException("simulated commit failure"));
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(storeRepository.findByIdAndOwnerIdForUpdate(store.getId(), owner.getId()))
                .thenReturn(Optional.of(store));
        when(imageMediaService.store(eq(ImageKind.STORE), any()))
                .thenReturn("images/stores/new-group");
        OwnerStoreService service = new OwnerStoreService(
                storeRepository, userRepository, imageMediaService, transactions);

        assertThatThrownBy(() -> service.updateImage(owner.getId(), store.getId(), image))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("commit failure");

        verify(imageMediaService).deleteImmediately("images/stores/new-group");
        assertThat(store.getImageKey()).isEqualTo("images/stores/11/old.jpg");
    }

    private User owner(Long id) {
        User owner = new User(
                "owner@example.com",
                "encoded-password",
                "테스트 사장님",
                "010-1234-5678",
                UserRole.OWNER
        );
        ReflectionTestUtils.setField(owner, "id", id);
        return owner;
    }

    private Store store(User owner, Long id) {
        Store store = new Store(
                owner,
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
}
