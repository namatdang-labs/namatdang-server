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
        String keyword = "목록조회" + uniqueValue();
        Deal deal = saveDeal(store,
                             salesEndsAt,
                             keyword,
                             new DealItem("소금빵", 5, 4000, 2000));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", bearerToken(consumer))
                        .param("keyword", keyword)
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dealId").value(deal.getId()))
                .andExpect(jsonPath("$.content[0].salesEndsAt").value(format(salesEndsAt)))
                .andExpect(jsonPath("$.content[0].storeName").value(store.getName()))
                .andExpect(jsonPath("$.content[0].lowestSalePrice").value(2000))
                .andExpect(jsonPath("$.content[0].distanceMeters").doesNotExist())
                .andExpect(jsonPath("$.content[0].headlineItemName").value("소금빵"))
                .andExpect(jsonPath("$.content[0].totalRemainingQuantity").value(5))
                .andExpect(jsonPath("$.content[0].maxDiscountRate").value(50));
    }

    @Test
    void keywordSearchesDescriptionStoreAndItemsWithoutDuplicatePagination() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        String keyword = "키워드" + uniqueValue();

        Deal descriptionDeal = saveDeal(
                saveStore(owner, "설명 검색 가게", "대구광역시 중구 1", BigDecimal.ZERO, BigDecimal.ZERO),
                hoursLater(3),
                keyword + " 설명",
                new DealItem("크루아상", 2, 4000, 3000));
        Deal storeNameDeal = saveDeal(
                saveStore(owner, keyword + " 가게", "대구광역시 중구 2", BigDecimal.ZERO, BigDecimal.ZERO),
                hoursLater(3),
                "평범한 설명",
                new DealItem("식빵", 2, 4000, 3000));
        Deal storeAddressDeal = saveDeal(
                saveStore(owner, "주소 검색 가게", "대구광역시 " + keyword, BigDecimal.ZERO, BigDecimal.ZERO),
                hoursLater(3),
                "평범한 설명",
                new DealItem("바게트", 2, 4000, 3000));
        Deal itemNameDeal = saveDeal(
                saveStore(owner, "품목 검색 가게", "대구광역시 중구 4", BigDecimal.ZERO, BigDecimal.ZERO),
                hoursLater(3),
                "평범한 설명",
                new DealItem(keyword + " 소금빵", 3, 4000, 2000),
                new DealItem(keyword + " 크로플", 4, 5000, 2500));
        saveDeal(
                saveStore(owner, "검색에 안 나오는 가게", "대구광역시 중구 5", BigDecimal.ZERO, BigDecimal.ZERO),
                hoursLater(3),
                "다른 설명",
                new DealItem("파이", 1, 4000, 3000));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", bearerToken(consumer))
                        .param("keyword", keyword)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].dealId").value(itemNameDeal.getId()))
                .andExpect(jsonPath("$.content[0].headlineItemName").value(keyword + " 소금빵"))
                .andExpect(jsonPath("$.content[0].totalRemainingQuantity").value(7))
                .andExpect(jsonPath("$.content[0].maxDiscountRate").value(50))
                .andExpect(jsonPath("$.content[1].dealId").value(storeAddressDeal.getId()));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", bearerToken(consumer))
                        .param("keyword", keyword)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].dealId").value(storeNameDeal.getId()))
                .andExpect(jsonPath("$.content[1].dealId").value(descriptionDeal.getId()));
    }

    @Test
    void locationSearchUsesDefaultFiveKilometerRadiusAndDistanceOrder() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);

        Deal nearestDeal = saveDeal(
                saveStore(owner, "0km 가게", "적도 근처 1", BigDecimal.ZERO, BigDecimal.ZERO),
                hoursLater(3),
                "가까운 딜",
                new DealItem("소금빵", 5, 4000, 2000));
        Deal secondDeal = saveDeal(
                saveStore(owner, "1km 가게", "적도 근처 2", new BigDecimal("0.0100000"), BigDecimal.ZERO),
                hoursLater(3),
                "두 번째 딜",
                new DealItem("식빵", 3, 4000, 3000));
        Deal outsideDeal = saveDeal(
                saveStore(owner, "10km 가게", "적도 근처 3", new BigDecimal("0.1000000"), BigDecimal.ZERO),
                hoursLater(3),
                "반경 밖 딜",
                new DealItem("크루아상", 2, 4000, 3000));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", bearerToken(consumer))
                        .param("centerLat", "0")
                        .param("centerLng", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].dealId").value(nearestDeal.getId()))
                .andExpect(jsonPath("$.content[0].distanceMeters").value(0))
                .andExpect(jsonPath("$.content[1].dealId").value(secondDeal.getId()))
                .andExpect(jsonPath("$.content[1].distanceMeters").isNumber())
                .andExpect(jsonPath("$.content[?(@.dealId == " + outsideDeal.getId() + ")]").isEmpty());
    }

    @Test
    void locationAndKeywordFiltersAreAppliedTogether() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        String keyword = "반경검색" + uniqueValue();

        Deal matchingDeal = saveDeal(
                saveStore(owner, "가까운 매장", "반경 검색 1", new BigDecimal("20.0010000"), new BigDecimal("20.0010000")),
                hoursLater(3),
                "평범한 설명",
                new DealItem(keyword + " 스콘", 2, 5000, 2500));
        saveDeal(
                saveStore(owner, "더 가까운 다른 매장", "반경 검색 2", new BigDecimal("20.0001000"), new BigDecimal("20.0001000")),
                hoursLater(3),
                "평범한 설명",
                new DealItem("타르트", 2, 5000, 3000));
        saveDeal(
                saveStore(owner, "먼 매장", "반경 검색 3", new BigDecimal("20.1000000"), new BigDecimal("20.1000000")),
                hoursLater(3),
                keyword + " 설명",
                new DealItem("빵", 2, 5000, 3000));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", bearerToken(consumer))
                        .param("centerLat", "20")
                        .param("centerLng", "20")
                        .param("radiusMeters", "2000")
                        .param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dealId").value(matchingDeal.getId()))
                .andExpect(jsonPath("$.content[0].distanceMeters").isNumber());
    }

    @Test
    void publicSellingListsExcludeSoldOutDealsAndSummarizeAvailableItems() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        String keyword = "재고계약" + uniqueValue();
        String soldOutItemKeyword = "품절품목검색" + uniqueValue();
        Store store = saveStore(owner,
                                "재고 테스트 가게",
                                "적도 근처",
                                BigDecimal.ZERO,
                                BigDecimal.ZERO);

        DealItem soldOutSummaryItem = new DealItem(soldOutItemKeyword, 4, 10000, 500);
        Deal availableDeal = saveDeal(store,
                                      hoursLater(3),
                                      keyword + " 일부 판매 중",
                                      soldOutSummaryItem,
                                      new DealItem("판매 가능 소금빵", 3, 5000, 3000));
        soldOutSummaryItem.decrease(4);

        DealItem fullySoldOutItem = new DealItem("모두 품절된 빵", 2, 4000, 1000);
        Deal fullySoldOutDeal = saveDeal(store,
                                         hoursLater(3),
                                         keyword + " 모두 품절",
                                         fullySoldOutItem);
        fullySoldOutItem.decrease(2);
        dealRepository.flush();

        String token = bearerToken(consumer);

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", token)
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.dealId == " + availableDeal.getId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.dealId == " + fullySoldOutDeal.getId() + ")]").isEmpty());

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", token)
                        .param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dealId").value(availableDeal.getId()))
                .andExpect(jsonPath("$.content[0].itemCount").value(1))
                .andExpect(jsonPath("$.content[0].headlineItemName").value("판매 가능 소금빵"))
                .andExpect(jsonPath("$.content[0].lowestSalePrice").value(3000))
                .andExpect(jsonPath("$.content[0].totalRemainingQuantity").value(3))
                .andExpect(jsonPath("$.content[0].maxDiscountRate").value(40));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", token)
                        .param("centerLat", "0")
                        .param("centerLng", "0")
                        .param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dealId").value(availableDeal.getId()))
                .andExpect(jsonPath("$.content[0].itemCount").value(1))
                .andExpect(jsonPath("$.content[0].headlineItemName").value("판매 가능 소금빵"))
                .andExpect(jsonPath("$.content[0].lowestSalePrice").value(3000))
                .andExpect(jsonPath("$.content[0].totalRemainingQuantity").value(3))
                .andExpect(jsonPath("$.content[0].maxDiscountRate").value(40));

        mockMvc.perform(get("/api/v1/stores/{storeId}/deals", store.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dealId").value(availableDeal.getId()))
                .andExpect(jsonPath("$.content[0].itemCount").value(1))
                .andExpect(jsonPath("$.content[0].headlineItemName").value("판매 가능 소금빵"))
                .andExpect(jsonPath("$.content[0].lowestSalePrice").value(3000))
                .andExpect(jsonPath("$.content[0].totalRemainingQuantity").value(3))
                .andExpect(jsonPath("$.content[0].maxDiscountRate").value(40));

        mockMvc.perform(get("/api/v1/deals/{dealId}", availableDeal.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value(soldOutItemKeyword))
                .andExpect(jsonPath("$.items[0].remainingQuantity").value(0))
                .andExpect(jsonPath("$.items[0].status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.items[1].name").value("판매 가능 소금빵"));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", token)
                        .param("keyword", soldOutItemKeyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", token)
                        .param("centerLat", "0")
                        .param("centerLng", "0")
                        .param("keyword", soldOutItemKeyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/owner/stores/{storeId}/deals", store.getId())
                        .header("Authorization", bearerToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[1].dealId").value(availableDeal.getId()))
                .andExpect(jsonPath("$.content[1].itemCount").value(2));
    }

    @Test
    void locationSearchRejectsIncompleteCoordinatesAndInvalidRange() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);
        String token = bearerToken(consumer);

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", token)
                        .param("centerLat", "35.8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", token)
                        .param("centerLat", "35.8")
                        .param("centerLng", "128.6")
                        .param("radiusMeters", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", token)
                        .param("centerLat", "91")
                        .param("centerLng", "128.6"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", token)
                        .param("centerLat", "35.8")
                        .param("centerLng", "128.6")
                        .param("radiusMeters", "50001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", token)
                        .param("centerLat", "not-a-number")
                        .param("centerLng", "128.6"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void dealPastSalesEndsAtIsNotListed() throws Exception {
        long existingDealCount = currentSellingDealCount();
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        String keyword = "마감조회" + uniqueValue();
        saveDeal(store,
                 LocalDateTime.now().minusMinutes(1),
                 keyword,
                 new DealItem("소금빵", 5, 4000, 2000));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", bearerToken(consumer))
                        .param("keyword", keyword)
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
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
    void guestGetsSellingDealsWithoutToken() throws Exception {
        long existingDealCount = currentSellingDealCount();
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, hoursLater(3), 5);

        mockMvc.perform(get("/api/v1/deals")
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(existingDealCount + 1))
                .andExpect(jsonPath("$.content[0].dealId").value(deal.getId()));
    }

    @Test
    void guestGetsDealDetailWithoutToken() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, hoursLater(3), 5);

        mockMvc.perform(get("/api/v1/deals/{dealId}", deal.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealId").value(deal.getId()))
                .andExpect(jsonPath("$.items[0].salePrice").value(2000));
    }

    @Test
    void dealsWithInvalidTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
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
        return saveStore(owner,
                         "남았당 베이커리 " + uniqueValue(),
                         "대구광역시 중구 국채보상로 1",
                         new BigDecimal("35.8714354"),
                         new BigDecimal("128.6014450"));
    }

    private Store saveStore(User owner, String name, String address,
                            BigDecimal latitude, BigDecimal longitude) {
        Store store = new Store(owner,
                                name,
                                address,
                                "1층",
                                "053-123-4567",
                                "매장 설명",
                                latitude,
                                longitude);
        return storeRepository.saveAndFlush(store);
    }

    private Deal saveDeal(Store store, LocalDateTime salesEndsAt, int quantity) {
        return saveDeal(store,
                        salesEndsAt,
                        "마감 임박 상품입니다.",
                        new DealItem("소금빵", quantity, 4000, 2000));
    }

    private Deal saveDeal(Store store, LocalDateTime salesEndsAt, String description,
                          DealItem... items) {
        Deal deal = new Deal(store, salesEndsAt, description);
        for (DealItem item : items) {
            deal.addItem(item);
        }
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
