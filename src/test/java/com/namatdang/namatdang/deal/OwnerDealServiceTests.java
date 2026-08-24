package com.namatdang.namatdang.deal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.namatdang.namatdang.deal.dto.DealCreateRequestDto;
import com.namatdang.namatdang.deal.dto.DealDetailResponseDto;
import com.namatdang.namatdang.deal.dto.DealItemCreateRequestDto;
import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.repository.DealRepository;
import com.namatdang.namatdang.deal.service.OwnerDealService;
import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.media.ImageKind;
import com.namatdang.namatdang.media.ImageMediaService;
import com.namatdang.namatdang.media.ImageStorage;
import com.namatdang.namatdang.media.ImageValidator;
import com.namatdang.namatdang.media.ImageVariant;
import com.namatdang.namatdang.media.ImageVariantProcessor;
import com.namatdang.namatdang.media.ImageVariantSet;
import com.namatdang.namatdang.media.ProcessedImage;
import com.namatdang.namatdang.media.TestImages;
import com.namatdang.namatdang.notification.event.NotificationEventRecorder;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class OwnerDealServiceTests {

    private final DealRepository dealRepository = mock(DealRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationEventRecorder notificationEventRecorder = mock(NotificationEventRecorder.class);
    private final ImageMediaService imageMediaService = mock(ImageMediaService.class);
    private final TransactionOperations transactions = synchronousTransactions();
    private final OwnerDealService ownerDealService = new OwnerDealService(
            dealRepository,
            storeRepository,
            userRepository,
            notificationEventRecorder,
            imageMediaService,
            transactions
    );

    @Test
    void dealCreationWithoutImageLeavesTheImageKeyEmpty() {
        User owner = owner(1L);
        Store store = store(owner);
        stubDealCreation(owner, store);

        DealDetailResponseDto result = ownerDealService.createDeal(
                owner.getId(), store.getId(), dealRequest(), null);

        assertThat(result.getImageUrl()).isNull();
        verifyNoInteractions(imageMediaService);
        verify(notificationEventRecorder).recordDealCreated(31L, store.getId(), result.getCreatedAt());
    }

    @Test
    void dealCreationRejectsAnExplicitlyProvidedEmptyImage() {
        User owner = owner(1L);
        Store store = store(owner);
        stubDealCreation(owner, store);
        MockMultipartFile emptyImage = new MockMultipartFile(
                "image", "empty.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);
        OwnerDealService service = new OwnerDealService(
                dealRepository,
                storeRepository,
                userRepository,
                notificationEventRecorder,
                imageMediaService(new InMemoryImageStorage()),
                transactions
        );

        assertThatThrownBy(() -> service.createDeal(
                owner.getId(), store.getId(), dealRequest(), emptyImage))
                .isInstanceOfSatisfying(BusinessLogicException.class, exception ->
                        assertThat(exception.getExceptionCode()).isEqualTo(ExceptionCode.INVALID_IMAGE));
        verifyNoInteractions(notificationEventRecorder);
    }

    @Test
    void replacingDealImageKeepsTheOldObjectUntilCommitAndDeletesItAfterCommit() {
        User owner = owner(1L);
        String oldKey = "images/deals/31/old.jpg";
        Deal deal = deal(owner, 31L, oldKey);
        MockMultipartFile replacement = image();
        InMemoryImageStorage storage = new InMemoryImageStorage();
        storage.write(oldKey, MediaType.IMAGE_JPEG_VALUE, TestImages.jpeg());
        ImageMediaService realImageMediaService = imageMediaService(storage);
        OwnerDealService service = new OwnerDealService(
                dealRepository,
                storeRepository,
                userRepository,
                notificationEventRecorder,
                realImageMediaService,
                transactions
        );
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(dealRepository.findByIdForUpdate(deal.getId())).thenReturn(Optional.of(deal));

        TransactionSynchronizationManager.initSynchronization();
        try {
            DealDetailResponseDto result = service.updateImage(owner.getId(), deal.getId(), replacement);
            String replacementKey = deal.getImageKey();

            assertThat(replacementKey).isNotEqualTo(oldKey);
            assertThat(result.getImageUrl()).contains("/api/v1/deals/31/image?variant=detail&v=");
            assertThat(storage.content).containsKey(oldKey);
            assertThat(storage.content.keySet().stream()
                    .filter(key -> key.startsWith(replacementKey + "/"))).hasSize(3);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            assertThat(storage.content).doesNotContainKey(oldKey);
            assertThat(storage.content.keySet()).allMatch(key -> key.startsWith(replacementKey + "/"));
            assertThat(storage.content).hasSize(3);
            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void otherOwnerCannotReplaceDealImage() {
        User owner = owner(1L);
        User otherOwner = owner(2L);
        Deal deal = deal(owner, 31L, "images/deals/31/old.jpg");
        when(userRepository.findById(otherOwner.getId())).thenReturn(Optional.of(otherOwner));
        when(dealRepository.findByIdForUpdate(deal.getId())).thenReturn(Optional.of(deal));

        assertThatThrownBy(() -> ownerDealService.updateImage(otherOwner.getId(), deal.getId(), image()))
                .isInstanceOfSatisfying(BusinessLogicException.class, exception ->
                        assertThat(exception.getExceptionCode()).isEqualTo(ExceptionCode.FORBIDDEN));
        verifyNoInteractions(imageMediaService);
    }

    @Test
    void removesNewImageGroupWhenTheShortDatabaseTransactionFails() {
        User owner = owner(1L);
        Deal deal = deal(owner, 31L, "images/deals/31/old.jpg");
        ImageMediaService mediaService = mock(ImageMediaService.class);
        TransactionOperations failingTransactions = mock(TransactionOperations.class);
        when(failingTransactions.execute(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(mock(TransactionStatus.class));
                })
                .thenThrow(new IllegalStateException("simulated commit failure"));
        when(mediaService.store(eq(ImageKind.DEAL), any())).thenReturn("images/deals/new-group");
        OwnerDealService service = new OwnerDealService(
                dealRepository,
                storeRepository,
                userRepository,
                notificationEventRecorder,
                mediaService,
                failingTransactions
        );
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(dealRepository.findByIdForUpdate(deal.getId())).thenReturn(Optional.of(deal));

        assertThatThrownBy(() -> service.updateImage(owner.getId(), deal.getId(), image()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("commit failure");

        verify(mediaService).deleteImmediately("images/deals/new-group");
        assertThat(deal.getImageKey()).isEqualTo("images/deals/31/old.jpg");
    }

    private void stubDealCreation(User owner, Store store) {
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(storeRepository.findByIdAndOwnerId(store.getId(), owner.getId()))
                .thenReturn(Optional.of(store));
        when(dealRepository.saveAndFlush(any(Deal.class))).thenAnswer(invocation -> {
            Deal savedDeal = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedDeal, "id", 31L);
            ReflectionTestUtils.setField(savedDeal, "createdAt", LocalDateTime.now());
            return savedDeal;
        });
    }

    private DealCreateRequestDto dealRequest() {
        DealItemCreateRequestDto item = new DealItemCreateRequestDto();
        ReflectionTestUtils.setField(item, "name", "소금빵");
        ReflectionTestUtils.setField(item, "totalQuantity", 3);
        ReflectionTestUtils.setField(item, "originalPrice", 4000);
        ReflectionTestUtils.setField(item, "salePrice", 2000);

        DealCreateRequestDto request = new DealCreateRequestDto();
        ReflectionTestUtils.setField(request, "salesEndsAt", LocalDateTime.now().plusHours(2));
        ReflectionTestUtils.setField(request, "description", "딜 설명");
        ReflectionTestUtils.setField(request, "items", List.of(item));
        return request;
    }

    private User owner(Long id) {
        User owner = new User(
                "owner%d@example.com".formatted(id),
                "encoded-password",
                "테스트 사장님",
                "010-1234-5678",
                UserRole.OWNER
        );
        ReflectionTestUtils.setField(owner, "id", id);
        return owner;
    }

    private Deal deal(User owner, Long id, String imageKey) {
        Store store = store(owner);
        Deal deal = new Deal(store, LocalDateTime.now().plusHours(2), "딜 설명");
        deal.updateImageKey(imageKey);
        ReflectionTestUtils.setField(deal, "id", id);
        return deal;
    }

    private Store store(User owner) {
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
        ReflectionTestUtils.setField(store, "id", 11L);
        return store;
    }

    private MockMultipartFile image() {
        return new MockMultipartFile(
                "image", "replacement.jpg", MediaType.IMAGE_JPEG_VALUE, TestImages.jpeg());
    }

    private ImageMediaService imageMediaService(InMemoryImageStorage storage) {
        ImageVariantProcessor processor = mock(ImageVariantProcessor.class);
        EnumMap<ImageVariant, ProcessedImage> variants = new EnumMap<>(ImageVariant.class);
        variants.put(ImageVariant.THUMBNAIL, new ProcessedImage(new byte[]{1}, "image/webp", 320, 200));
        variants.put(ImageVariant.CARD, new ProcessedImage(new byte[]{2}, "image/webp", 768, 480));
        variants.put(ImageVariant.DETAIL, new ProcessedImage(new byte[]{3}, "image/webp", 1600, 1000));
        when(processor.process(any())).thenAnswer(invocation -> {
            new ImageValidator().validate(invocation.getArgument(0));
            return new ImageVariantSet(variants);
        });
        return new ImageMediaService(processor, storage);
    }

    private static TransactionOperations synchronousTransactions() {
        TransactionOperations transactions = mock(TransactionOperations.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        return transactions;
    }

    private static final class InMemoryImageStorage implements ImageStorage {

        private final Map<String, byte[]> content = new HashMap<>();

        @Override
        public void write(String key, String contentType, byte[] bytes) {
            content.put(key, bytes);
        }

        @Override
        public Optional<byte[]> read(String key) {
            return Optional.ofNullable(content.get(key));
        }

        @Override
        public void delete(String key) {
            content.remove(key);
        }
    }
}
