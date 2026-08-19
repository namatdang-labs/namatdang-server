package com.namatdang.namatdang.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.repository.DealRepository;
import com.namatdang.namatdang.favorite.entity.Favorite;
import com.namatdang.namatdang.favorite.repository.FavoriteRepository;
import com.namatdang.namatdang.notification.event.NotificationEventType;
import com.namatdang.namatdang.notification.outbox.entity.NotificationEvent;
import com.namatdang.namatdang.notification.outbox.entity.NotificationEventStatus;
import com.namatdang.namatdang.notification.outbox.repository.NotificationEventRepository;
import com.namatdang.namatdang.notification.recipient.FavoriteRecipientReader;
import com.namatdang.namatdang.security.JwtTokenProvider;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.support.IntegrationTestSupport;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationDomainIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private FavoriteRecipientReader favoriteRecipientReader;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void dealCreationRecordsDealCreatedEvent() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);

        MvcResult result = mockMvc.perform(post("/api/v1/owner/stores/{storeId}/deals", store.getId())
                                                  .header("Authorization", "Bearer "
                                                          + jwtTokenProvider.issue(owner.getId(), owner.getRole()))
                                                  .contentType(MediaType.APPLICATION_JSON)
                                                  .content(dealRequestBody()))
                .andExpect(status().isCreated())
                .andReturn();

        Long dealId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("dealId")
                .asLong();

        entityManager.flush();
        entityManager.clear();

        Deal savedDeal = dealRepository.findById(dealId).orElseThrow();
        NotificationEvent savedEvent = notificationEventRepository.findAll().stream()
                .filter(event -> dealId.equals(event.getDealId()))
                .findFirst()
                .orElseThrow();

        assertThat(savedEvent.getEventType()).isEqualTo(NotificationEventType.DEAL_CREATED);
        assertThat(savedEvent.getDealId()).isEqualTo(savedDeal.getId());
        assertThat(savedEvent.getStoreId()).isEqualTo(store.getId());
        assertThat(savedEvent.getOccurredAt()).isEqualTo(savedDeal.getCreatedAt());
        assertThat(savedEvent.getSourceRequestKey()).isEqualTo("DEAL:%d:CREATED".formatted(savedDeal.getId()));
        assertThat(savedEvent.getStatus()).isEqualTo(NotificationEventStatus.PENDING);
    }

    @Test
    void favoriteRecipientReaderFindsOnlyUsersWhoFavoritedStoreInIdOrder() {
        User owner = saveUser(UserRole.OWNER);
        User firstConsumer = saveUser(UserRole.CONSUMER);
        User secondConsumer = saveUser(UserRole.CONSUMER);
        Store targetStore = saveStore(owner);
        Store otherStore = saveStore(owner);

        favoriteRepository.save(new Favorite(secondConsumer, targetStore));
        favoriteRepository.save(new Favorite(firstConsumer, targetStore));
        favoriteRepository.save(new Favorite(firstConsumer, otherStore));
        favoriteRepository.flush();

        assertThat(favoriteRecipientReader.findUserIdsByStoreId(targetStore.getId()))
                .containsExactly(firstConsumer.getId(), secondConsumer.getId());
        assertThat(favoriteRecipientReader.findUserIdsByStoreId(Long.MAX_VALUE))
                .isEmpty();
    }

    private User saveUser(UserRole role) {
        User user = new User(
                uniqueValue() + "@example.com",
                "encoded-password",
                role == UserRole.OWNER ? "테스트 사장님" : "테스트 소비자",
                "010-1234-5678",
                role
        );
        return userRepository.saveAndFlush(user);
    }

    private Store saveStore(User owner) {
        Store store = new Store(
                owner,
                "알림 테스트 매장 " + uniqueValue(),
                "대구광역시 중구 국채보상로 1",
                "1층",
                "053-123-4567",
                "매장 설명",
                new BigDecimal("35.8714354"),
                new BigDecimal("128.6014450")
        );
        return storeRepository.saveAndFlush(store);
    }

    private String dealRequestBody() {
        String pickupDeadline = LocalDateTime.now()
                .plusHours(3)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return """
                {
                  "pickupDeadline":"%s",
                  "description":"마감 임박 상품입니다.",
                  "items":[
                    {"name":"소금빵","totalQuantity":5,"originalPrice":4000,"salePrice":2000}
                  ]
                }
                """.formatted(pickupDeadline);
    }

    private String uniqueValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
