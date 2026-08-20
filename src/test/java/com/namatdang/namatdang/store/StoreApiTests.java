package com.namatdang.namatdang.store;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.namatdang.namatdang.security.JwtTokenProvider;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.support.IntegrationTestSupport;
import com.namatdang.namatdang.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoreApiTests extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private com.namatdang.namatdang.deal.repository.DealRepository dealRepository;

    @Test
    void getStoresOnMapWithinBounds() throws Exception {
        User owner = saveOwner();
        String keyword = uniqueKeyword();

        // 1. 영역 안 매장
        Store inBoundStore = saveStoreWithLocation(owner, "영역안 매장 " + keyword, "주소 1",
                new BigDecimal("37.5665"), new BigDecimal("126.9780"));

        // 2. 영역 밖 매장 (위도 벗어남)
        saveStoreWithLocation(owner, "영역밖 매장1 " + keyword, "주소 2",
                new BigDecimal("35.1796"), new BigDecimal("126.9780"));

        // 3. 영역 밖 매장 (경도 벗어남)
        saveStoreWithLocation(owner, "영역밖 매장2 " + keyword, "주소 3",
                new BigDecimal("37.5665"), new BigDecimal("129.0756"));

        mockMvc.perform(get("/api/v1/stores/map")
                        .header("Authorization", consumerToken())
                        .param("minLat", "37.5000")
                        .param("maxLat", "37.6000")
                        .param("minLng", "126.9000")
                        .param("maxLng", "127.0000")
                        .param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(inBoundStore.getId()))
                .andExpect(jsonPath("$[0].name").value("영역안 매장 " + keyword))
                .andExpect(jsonPath("$[0].hasActiveDeal").value(false))
                .andExpect(jsonPath("$[0].activeDealCount").value(0));
    }

    @Test
    void getStoresOnMapWithDiscountFilter() throws Exception {
        User owner = saveOwner();
        String keyword = uniqueKeyword();

        // 1. 할인 딜이 있는 매장
        Store discountingStore = saveStoreWithLocation(owner, "할인매장 " + keyword, "주소 1",
                new BigDecimal("37.5665"), new BigDecimal("126.9780"));
        com.namatdang.namatdang.deal.entity.Deal activeDeal = new com.namatdang.namatdang.deal.entity.Deal(
                discountingStore,
                java.time.LocalDateTime.now().plusHours(2),
                "마감 임박 빵 세일");
        dealRepository.saveAndFlush(activeDeal);

        // 2. 할인 딜이 없는 매장
        saveStoreWithLocation(owner, "일반매장 " + keyword, "주소 2",
                new BigDecimal("37.5665"), new BigDecimal("126.9790"));

        // 3. 마감된 딜만 있는 매장
        Store closedDealStore = saveStoreWithLocation(owner, "마감매장 " + keyword, "주소 3",
                new BigDecimal("37.5665"), new BigDecimal("126.9770"));
        com.namatdang.namatdang.deal.entity.Deal closedDeal = new com.namatdang.namatdang.deal.entity.Deal(
                closedDealStore,
                java.time.LocalDateTime.now().minusHours(1),
                "이미 종료된 세일");
        dealRepository.saveAndFlush(closedDeal);

        // onlyDiscounting=true 로 조회
        mockMvc.perform(get("/api/v1/stores/map")
                        .header("Authorization", consumerToken())
                        .param("minLat", "37.5000")
                        .param("maxLat", "37.6000")
                        .param("minLng", "126.9000")
                        .param("maxLng", "127.0000")
                        .param("onlyDiscounting", "true")
                        .param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(discountingStore.getId()))
                .andExpect(jsonPath("$[0].name").value("할인매장 " + keyword))
                .andExpect(jsonPath("$[0].hasActiveDeal").value(true))
                .andExpect(jsonPath("$[0].activeDealCount").value(1));
    }

    @Test
    void getStoresOnMapInvalidBoundsReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/stores/map")
                        .header("Authorization", consumerToken())
                        .param("minLat", "37.6000")
                        .param("maxLat", "37.5000") // minLat > maxLat
                        .param("minLng", "126.9000")
                        .param("maxLng", "127.0000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void getStoresWithoutKeyword() throws Exception {

        long existingStoreCount = storeRepository.count();
        User owner = saveOwner();
        saveStore(owner, "매장 목록 " + uniqueKeyword(), "대구광역시 북구 침산로 1");


        mockMvc.perform(get("/api/v1/stores")
                        .header("Authorization", consumerToken())
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(existingStoreCount + 1));
    }

    @Test
    void getStoresByPage() throws Exception {
        User owner = saveOwner();
        String keyword = uniqueKeyword();
        Store firstStore = saveStore(owner, keyword + " 1호점", "대구광역시 중구 1");
        Store secondStore = saveStore(owner, keyword + " 2호점", "대구광역시 중구 2");
        Store thirdStore = saveStore(owner, keyword + " 3호점", "대구광역시 중구 3");

        mockMvc.perform(get("/api/v1/stores")
                        .header("Authorization", consumerToken())
                        .param("keyword", keyword)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(firstStore.getId()))
                .andExpect(jsonPath("$.content[1].id").value(secondStore.getId()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/v1/stores")
                        .header("Authorization", consumerToken())
                        .param("keyword", keyword)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(thirdStore.getId()))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void searchStoresByName() throws Exception {
        User owner = saveOwner();
        String keyword = "달콤제과" + uniqueKeyword();
        String storeName = "오늘도 " + keyword + " 본점";
        Store matchedStore = saveStore(owner, storeName, "대구광역시 수성구 동대구로 1");
        saveStore(owner, "다른 매장 " + uniqueKeyword(), "대구광역시 달서구 달구벌대로 2");

        mockMvc.perform(get("/api/v1/stores")
                        .header("Authorization", consumerToken())
                        .param("keyword", keyword)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(matchedStore.getId()))
                .andExpect(jsonPath("$.content[0].name").value(storeName));
    }

    @Test
    void searchStoresByAddress() throws Exception {
        User owner = saveOwner();
        String keyword = "범어동" + uniqueKeyword();
        Store matchedStore = saveStore(owner, "범어 빵집", "대구광역시 수성구 " + keyword + " 10");
        saveStore(owner, "다른 빵집", "대구광역시 중구 동성로 20");

        mockMvc.perform(get("/api/v1/stores")
                        .header("Authorization", consumerToken())
                        .param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(matchedStore.getId()))
                .andExpect(jsonPath("$.content[0].address").value("대구광역시 수성구 " + keyword + " 10"));
    }

    @Test
    void getStoreDetail() throws Exception {
        User owner = saveOwner();
        Store store = saveStore(owner,
                                "남았당 베이커리 " + uniqueKeyword(),
                                "대구광역시 중구 국채보상로 1");

        mockMvc.perform(get("/api/v1/stores/{storeId}", store.getId())
                        .header("Authorization", consumerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(store.getId()))
                .andExpect(jsonPath("$.name").value(store.getName()))
                .andExpect(jsonPath("$.address").value(store.getAddress()))
                .andExpect(jsonPath("$.addressDetail").value("1층"))
                .andExpect(jsonPath("$.phoneNumber").value("053-123-4567"))
                .andExpect(jsonPath("$.description").value("매장 설명"))
                .andExpect(jsonPath("$.latitude").value(35.8714354))
                .andExpect(jsonPath("$.longitude").value(128.6014450));
    }

    @Test
    void unknownStoreReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/stores/{storeId}", Long.MAX_VALUE)
                        .header("Authorization", consumerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    void negativePageReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/stores")
                        .header("Authorization", consumerToken())
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void oversizedPageReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/stores")
                        .header("Authorization", consumerToken())
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void invalidParameterTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/stores")
                        .header("Authorization", consumerToken())
                        .param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/stores/{storeId}", "not-a-number")
                        .header("Authorization", consumerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private String consumerToken() {
        User consumer = new User(uniqueKeyword() + "@example.com",
                                 "encoded-password",
                                 "테스트 소비자",
                                 "010-1234-5678",
                                 UserRole.CONSUMER);
        userRepository.saveAndFlush(consumer);
        return "Bearer " + jwtTokenProvider.issue(consumer.getId(), consumer.getRole());
    }

    private User saveOwner() {
        User owner = new User(uniqueKeyword() + "@example.com",
                              "encoded-password",
                              "테스트 사장님",
                              "010-1234-5678",
                              UserRole.OWNER);
        return userRepository.saveAndFlush(owner);
    }

    private Store saveStore(User owner, String name, String address) {
        return saveStoreWithLocation(owner, name, address, new BigDecimal("35.8714354"), new BigDecimal("128.6014450"));
    }

    private Store saveStoreWithLocation(User owner, String name, String address, BigDecimal lat, BigDecimal lon) {
        Store store = new Store(owner,
                                name,
                                address,
                                "1층",
                                "053-123-4567",
                                "매장 설명",
                                lat,
                                lon);
        return storeRepository.saveAndFlush(store);
    }

    private String uniqueKeyword() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

