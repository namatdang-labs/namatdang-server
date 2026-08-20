package com.namatdang.namatdang.deal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealItem;
import com.namatdang.namatdang.deal.repository.DealRepository;
import com.namatdang.namatdang.security.JwtTokenProvider;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.support.IntegrationTestSupport;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnerDealApiTests extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void ownerCreatesDealWithItems() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);
        LocalDateTime salesEndsAt = hoursLater(3);

        mockMvc.perform(post("/api/v1/owner/stores/{storeId}/deals", store.getId())
                        .header("Authorization", bearerToken(owner))
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dealRequestBody(salesEndsAt, 5, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dealId").isNumber())
                .andExpect(jsonPath("$.storeId").value(store.getId()))
                .andExpect(jsonPath("$.salesEndsAt").value(format(salesEndsAt)))
                .andExpect(jsonPath("$.status").value("SELLING"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].totalQuantity").value(5))
                .andExpect(jsonPath("$.items[0].remainingQuantity").value(5))
                .andExpect(jsonPath("$.items[0].status").value("SELLING"))
                .andExpect(jsonPath("$.items[0].discountRate").value(50))
                .andExpect(jsonPath("$.items[1].totalQuantity").value(3));
    }

    @Test
    void creatingDealOnOtherOwnersStoreReturnsNotFound() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User otherOwner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);

        mockMvc.perform(post("/api/v1/owner/stores/{storeId}/deals", store.getId())
                        .header("Authorization", bearerToken(otherOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dealRequestBody(hoursLater(3), 5, 3)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    void consumerCannotCreateDeal() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);

        mockMvc.perform(post("/api/v1/owner/stores/{storeId}/deals", store.getId())
                        .header("Authorization", bearerToken(consumer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dealRequestBody(hoursLater(3), 5, 3)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void creatingDealWithElevenItemsReturnsBadRequest() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);

        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            if (i > 0) {
                items.append(",");
            }
            items.append("""
                    {"name":"품목%d","totalQuantity":1,"originalPrice":2000,"salePrice":1000}"""
                                 .formatted(i));
        }
        String body = """
                {"salesEndsAt":"%s","description":"안내","items":[%s]}"""
                .formatted(format(hoursLater(3)), items);

        mockMvc.perform(post("/api/v1/owner/stores/{storeId}/deals", store.getId())
                        .header("Authorization", bearerToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void creatingDealWithTooEarlySalesEndsAtReturnsBadRequest() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);

        mockMvc.perform(post("/api/v1/owner/stores/{storeId}/deals", store.getId())
                        .header("Authorization", bearerToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dealRequestBody(LocalDateTime.now().plusMinutes(5), 5, 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void creatingDealBeyondTwentyFourHoursReturnsBadRequest() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);

        mockMvc.perform(post("/api/v1/owner/stores/{storeId}/deals", store.getId())
                        .header("Authorization", bearerToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dealRequestBody(LocalDateTime.now().plusHours(25), 5, 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void creatingDealWithOversizedQuantityReturnsBadRequest() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);

        String body = """
                {"salesEndsAt":"%s","description":"안내",
                 "items":[{"name":"소금빵","totalQuantity":100,"originalPrice":4000,"salePrice":2000}]}"""
                .formatted(format(hoursLater(3)));

        mockMvc.perform(post("/api/v1/owner/stores/{storeId}/deals", store.getId())
                        .header("Authorization", bearerToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void ownerGetsMyStoreDeals() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);
        LocalDateTime salesEndsAt = hoursLater(3);
        Deal deal = saveDeal(store, salesEndsAt, 5);

        mockMvc.perform(get("/api/v1/owner/stores/{storeId}/deals", store.getId())
                        .header("Authorization", bearerToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dealId").value(deal.getId()))
                .andExpect(jsonPath("$.content[0].salesEndsAt").value(format(salesEndsAt)))
                .andExpect(jsonPath("$.content[0].itemCount").value(1));
    }

    @Test
    void ownerGetsMyStoreDealsIncludingClosedOnes() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);
        saveDeal(store, hoursLater(3), 5);
        saveDeal(store, LocalDateTime.now().minusHours(1), 5);

        mockMvc.perform(get("/api/v1/owner/stores/{storeId}/deals", store.getId())
                        .header("Authorization", bearerToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void ownerGetsMyDealDetail() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);
        LocalDateTime salesEndsAt = hoursLater(3);
        Deal deal = saveDeal(store, salesEndsAt, 5);

        mockMvc.perform(get("/api/v1/owner/deals/{dealId}", deal.getId())
                        .header("Authorization", bearerToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealId").value(deal.getId()))
                .andExpect(jsonPath("$.salesEndsAt").value(format(salesEndsAt)))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void ownerCannotGetOtherOwnersDeal() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User otherOwner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, hoursLater(3), 5);

        mockMvc.perform(get("/api/v1/owner/deals/{dealId}", deal.getId())
                        .header("Authorization", bearerToken(otherOwner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private String dealRequestBody(LocalDateTime salesEndsAt, int firstQuantity, int secondQuantity) {
        return """
                {"salesEndsAt":"%s",
                 "description":"마감 임박 상품입니다.",
                 "items":[{"name":"소금빵","totalQuantity":%d,"originalPrice":4000,"salePrice":2000},
                          {"name":"크루아상","totalQuantity":%d,"originalPrice":5000,"salePrice":3000}]}"""
                .formatted(format(salesEndsAt), firstQuantity, secondQuantity);
    }

    private String format(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    // 초 단위로 잘라 DB 왕복이나 JSON 직렬화의 소수점 자리 차이로 값 비교가 흔들리지 않게 한다.
    private LocalDateTime hoursLater(int hours) {
        return LocalDateTime.now().plusHours(hours).truncatedTo(ChronoUnit.SECONDS);
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.issue(user.getId());
    }

    private User saveUser(UserRole role) {
        User user = new User(uniqueValue() + "@example.com",
                             "encoded-password",
                             role == UserRole.OWNER ? "테스트 사장님" : "테스트 소비자",
                             "010-1234-5678",
                             role);
        return userRepository.saveAndFlush(user);
    }

    private Store saveStore(User owner) {
        Store store = new Store(owner,
                                "남았당 베이커리 " + uniqueValue(),
                                "대구광역시 중구 국채보상로 1",
                                "1층",
                                "053-123-4567",
                                "매장 설명",
                                new BigDecimal("35.8714354"),
                                new BigDecimal("128.6014450"));
        return storeRepository.saveAndFlush(store);
    }

    private Deal saveDeal(Store store, LocalDateTime salesEndsAt, int quantity) {
        Deal deal = new Deal(store, salesEndsAt, "마감 임박 상품입니다.");
        deal.addItem(new DealItem("소금빵", quantity, 4000, 2000));
        return dealRepository.saveAndFlush(deal);
    }

    private String uniqueValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
