package com.namatdang.namatdang.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
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
class OwnerStoreApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void ownerCreatesStore() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        String storeName = "새로운 베이커리 " + uniqueValue();

        mockMvc.perform(post("/api/v1/owner/stores")
                        .requestAttr("userId", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "address": "대구광역시 중구 동성로 1",
                                  "addressDetail": "1층",
                                  "phoneNumber": "053-123-4567",
                                  "description": "매장 안내",
                                  "latitude": 35.8714354,
                                  "longitude": 128.6014450
                                }
                                """.formatted(storeName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(storeName))
                .andExpect(jsonPath("$.address").value("대구광역시 중구 동성로 1"))
                .andExpect(jsonPath("$.latitude").value(35.8714354));

        assertThat(storeRepository.findAllByOwnerIdOrderByIdAsc(owner.getId())).hasSize(1);
    }

    @Test
    void consumerCannotCreateStore() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);

        mockMvc.perform(post("/api/v1/owner/stores")
                        .requestAttr("userId", consumer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "권한 없는 매장",
                                  "address": "대구광역시 중구 동성로 2"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(storeRepository.findAllByOwnerIdOrderByIdAsc(consumer.getId())).isEmpty();
    }

    @Test
    void ownerCanCreateMultipleStores() throws Exception {
        User owner = saveUser(UserRole.OWNER);

        createStore(owner, "첫 번째 매장 " + uniqueValue());
        createStore(owner, "두 번째 매장 " + uniqueValue());

        assertThat(storeRepository.findAllByOwnerIdOrderByIdAsc(owner.getId())).hasSize(2);
    }

    @Test
    void ownerGetsOnlyOwnedStoresAndCanHaveMultipleStores() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User otherOwner = saveUser(UserRole.OWNER);
        Store firstStore = saveStore(owner, "내 매장 1 " + uniqueValue());
        Store secondStore = saveStore(owner, "내 매장 2 " + uniqueValue());
        saveStore(otherOwner, "다른 사장님 매장 " + uniqueValue());

        mockMvc.perform(get("/api/v1/owner/stores")
                        .requestAttr("userId", owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(firstStore.getId()))
                .andExpect(jsonPath("$[1].id").value(secondStore.getId()));
    }

    @Test
    void ownerWithoutStoresGetsEmptyList() throws Exception {
        User owner = saveUser(UserRole.OWNER);

        mockMvc.perform(get("/api/v1/owner/stores")
                        .requestAttr("userId", owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void ownerUpdatesOwnedStore() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner, "수정 전 매장 " + uniqueValue());
        String updatedName = "수정 후 매장 " + uniqueValue();

        mockMvc.perform(patch("/api/v1/owner/stores/{storeId}", store.getId())
                        .requestAttr("userId", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "phoneNumber": "053-999-9999"
                                }
                                """.formatted(updatedName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(updatedName))
                .andExpect(jsonPath("$.phoneNumber").value("053-999-9999"))
                .andExpect(jsonPath("$.address").value("대구광역시 중구 동성로 10"));
    }

    @Test
    void ownerCannotUpdateAnotherOwnersStore() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User otherOwner = saveUser(UserRole.OWNER);
        Store otherStore = saveStore(otherOwner, "타인 매장 " + uniqueValue());
        Long otherStoreId = otherStore.getId();
        String originalName = otherStore.getName();

        mockMvc.perform(patch("/api/v1/owner/stores/{storeId}", otherStore.getId())
                        .requestAttr("userId", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "탈취 시도"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));

        entityManager.flush();
        entityManager.clear();

        assertThat(storeRepository.findById(otherStoreId).orElseThrow().getName())
                .isEqualTo(originalName);
    }

    @Test
    void consumerCannotListOrUpdateStores() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner, "소비자 접근 거절 " + uniqueValue());

        mockMvc.perform(get("/api/v1/owner/stores")
                        .requestAttr("userId", consumer.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(patch("/api/v1/owner/stores/{storeId}", store.getId())
                        .requestAttr("userId", consumer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "수정 시도"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unknownStoreCannotBeUpdated() throws Exception {
        User owner = saveUser(UserRole.OWNER);

        mockMvc.perform(patch("/api/v1/owner/stores/{storeId}", Long.MAX_VALUE)
                        .requestAttr("userId", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "존재하지 않는 매장"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    void emptyUpdateRequestReturnsBadRequest() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner, "빈 수정 요청 " + uniqueValue());

        mockMvc.perform(patch("/api/v1/owner/stores/{storeId}", store.getId())
                        .requestAttr("userId", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void invalidStoreRequestReturnsBadRequest() throws Exception {
        User owner = saveUser(UserRole.OWNER);

        mockMvc.perform(post("/api/v1/owner/stores")
                        .requestAttr("userId", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "address": " ",
                                  "latitude": 91.0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unknownUserCannotManageStores() throws Exception {
        mockMvc.perform(get("/api/v1/owner/stores")
                        .requestAttr("userId", Long.MAX_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    private User saveUser(UserRole role) {
        User user = new User(uniqueValue() + "@example.com",
                             "encoded-password",
                             "테스트 회원",
                             "010-1234-5678",
                             role);
        return userRepository.saveAndFlush(user);
    }

    private Store saveStore(User owner, String name) {
        Store store = new Store(owner,
                                name,
                                "대구광역시 중구 동성로 10",
                                "2층",
                                "053-123-4567",
                                "매장 설명",
                                new BigDecimal("35.8714354"),
                                new BigDecimal("128.6014450"));
        return storeRepository.saveAndFlush(store);
    }

    private void createStore(User owner, String name) throws Exception {
        mockMvc.perform(post("/api/v1/owner/stores")
                        .requestAttr("userId", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "address": "대구광역시 중구 동성로 1"
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated());
    }

    private String uniqueValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
