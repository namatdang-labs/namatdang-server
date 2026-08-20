package com.namatdang.namatdang.deal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealItem;
import com.namatdang.namatdang.deal.entity.DealStatus;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DealApiTests extends IntegrationTestSupport {

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
    void consumerGetsSellingDeals() throws Exception {
        long existingDealCount = currentSellingDealCount();
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        LocalDateTime salesEndsAt = hoursLater(3);
        Deal deal = saveDeal(store, salesEndsAt, 5);

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", bearerToken(consumer))
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(existingDealCount + 1))
                .andExpect(jsonPath("$.content[0].dealId").value(deal.getId()))
                .andExpect(jsonPath("$.content[0].salesEndsAt").value(format(salesEndsAt)))
                .andExpect(jsonPath("$.content[0].storeName").value(store.getName()))
                .andExpect(jsonPath("$.content[0].lowestSalePrice").value(2000));
    }

    @Test
    void dealPastSalesEndsAtIsNotListed() throws Exception {
        long existingDealCount = currentSellingDealCount();
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        saveDeal(store, LocalDateTime.now().minusMinutes(1), 5);

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", bearerToken(consumer))
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(existingDealCount));
    }

    @Test
    void consumerGetsDealDetailWithDiscountRate() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        LocalDateTime salesEndsAt = hoursLater(3);
        Deal deal = saveDeal(store, salesEndsAt, 5);

        mockMvc.perform(get("/api/v1/deals/{dealId}", deal.getId())
                        .header("Authorization", bearerToken(consumer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealId").value(deal.getId()))
                .andExpect(jsonPath("$.storeId").value(store.getId()))
                .andExpect(jsonPath("$.salesEndsAt").value(format(salesEndsAt)))
                .andExpect(jsonPath("$.status").value("SELLING"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("소금빵"))
                .andExpect(jsonPath("$.items[0].remainingQuantity").value(5))
                .andExpect(jsonPath("$.items[0].originalPrice").value(4000))
                .andExpect(jsonPath("$.items[0].salePrice").value(2000))
                .andExpect(jsonPath("$.items[0].discountRate").value(50));
    }

    @Test
    void dealPastSalesEndsAtIsShownAsClosedInDetail() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, LocalDateTime.now().minusMinutes(1), 5);

        mockMvc.perform(get("/api/v1/deals/{dealId}", deal.getId())
                        .header("Authorization", bearerToken(consumer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealId").value(deal.getId()))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void listPaginationLimitsRowsWhenDealsHaveManyItems() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        saveDealWithItems(store, hoursLater(3), 3);
        saveDealWithItems(store, hoursLater(3), 3);
        saveDealWithItems(store, hoursLater(3), 3);

        mockMvc.perform(get("/api/v1/stores/{storeId}/deals", store.getId())
                        .header("Authorization", bearerToken(consumer))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].itemCount").value(3));
    }

    @Test
    void consumerGetsStoreDeals() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Store otherStore = saveStore(owner);
        Deal deal = saveDeal(store, hoursLater(3), 5);
        saveDeal(otherStore, hoursLater(3), 5);

        mockMvc.perform(get("/api/v1/stores/{storeId}/deals", store.getId())
                        .header("Authorization", bearerToken(consumer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dealId").value(deal.getId()));
    }

    @Test
    void storeDealsOfUnknownStoreReturnsNotFound() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);

        mockMvc.perform(get("/api/v1/stores/{storeId}/deals", Long.MAX_VALUE)
                        .header("Authorization", bearerToken(consumer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    void unknownDealReturnsNotFound() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);

        mockMvc.perform(get("/api/v1/deals/{dealId}", Long.MAX_VALUE)
                        .header("Authorization", bearerToken(consumer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEAL_NOT_FOUND"));
    }

    @Test
    void oversizedPageReturnsBadRequest() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", bearerToken(consumer))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void dealsWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/deals"))
                .andExpect(status().isUnauthorized());
    }

    // 초 단위로 잘라 DB 왕복이나 JSON 직렬화의 소수점 자리 차이로 값 비교가 흔들리지 않게 한다.
    private LocalDateTime hoursLater(int hours) {
        return LocalDateTime.now().plusHours(hours).truncatedTo(ChronoUnit.SECONDS);
    }

    private String format(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.issue(user.getId());
    }

    private long currentSellingDealCount() {
        return dealRepository.findByStatusAndSalesEndsAtAfter(
                DealStatus.SELLING,
                LocalDateTime.now(),
                Pageable.unpaged()
        ).getTotalElements();
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

    private Deal saveDealWithItems(Store store, LocalDateTime salesEndsAt, int itemCount) {
        Deal deal = new Deal(store, salesEndsAt, "마감 임박 상품입니다.");
        for (int i = 0; i < itemCount; i++) {
            deal.addItem(new DealItem("품목" + i, 5, 4000, 2000));
        }
        return dealRepository.saveAndFlush(deal);
    }

    private String uniqueValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
