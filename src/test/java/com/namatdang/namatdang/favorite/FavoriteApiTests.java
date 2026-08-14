package com.namatdang.namatdang.favorite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.namatdang.namatdang.favorite.entity.Favorite;
import com.namatdang.namatdang.favorite.repository.FavoriteRepository;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.store.repository.StoreRepository;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
class FavoriteApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void consumerAddsFavoriteIdempotently() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore();

        addFavorite(consumer, store);
        Favorite savedFavorite = favoriteRepository.findAllByUserIdOrderByIdAsc(consumer.getId()).getFirst();
        Long favoriteId = savedFavorite.getId();
        LocalDateTime createdAt = savedFavorite.getCreatedAt();

        addFavorite(consumer, store);
        entityManager.flush();
        entityManager.clear();

        assertThat(favoriteRepository.findAllByUserIdOrderByIdAsc(consumer.getId()))
                .singleElement()
                .satisfies(favorite -> {
                    assertThat(favorite.getId()).isEqualTo(favoriteId);
                    assertThat(favorite.getStore().getId()).isEqualTo(store.getId());
                    assertThat(favorite.getCreatedAt()).isEqualTo(createdAt);
                });
    }

    @Test
    void consumerGetsOnlyOwnFavoritesInRegistrationOrder() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);
        User otherConsumer = saveUser(UserRole.CONSUMER);
        Store firstStore = saveStore();
        Store secondStore = saveStore();
        favoriteRepository.saveAndFlush(new Favorite(consumer, firstStore));
        favoriteRepository.saveAndFlush(new Favorite(consumer, secondStore));
        favoriteRepository.saveAndFlush(new Favorite(otherConsumer, firstStore));

        mockMvc.perform(get("/api/v1/favorites")
                        .requestAttr("userId", consumer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(firstStore.getId()))
                .andExpect(jsonPath("$[0].name").value(firstStore.getName()))
                .andExpect(jsonPath("$[1].id").value(secondStore.getId()));
    }

    @Test
    void consumerWithoutFavoritesGetsEmptyList() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);

        mockMvc.perform(get("/api/v1/favorites")
                        .requestAttr("userId", consumer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void consumerDeletesFavoriteIdempotently() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);
        User otherConsumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore();
        Store otherStore = saveStore();
        favoriteRepository.saveAndFlush(new Favorite(consumer, store));
        favoriteRepository.saveAndFlush(new Favorite(consumer, otherStore));
        favoriteRepository.saveAndFlush(new Favorite(otherConsumer, store));

        deleteFavorite(consumer, store);
        deleteFavorite(consumer, store);

        assertThat(favoriteRepository.findAllByUserIdOrderByIdAsc(consumer.getId()))
                .singleElement()
                .extracting(favorite -> favorite.getStore().getId())
                .isEqualTo(otherStore.getId());
        assertThat(favoriteRepository.findAllByUserIdOrderByIdAsc(otherConsumer.getId()))
                .hasSize(1);
    }

    @Test
    void ownerCannotUseFavorites() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore();

        mockMvc.perform(get("/api/v1/favorites")
                        .requestAttr("userId", owner.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(put("/api/v1/favorites/{storeId}", store.getId())
                        .requestAttr("userId", owner.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/v1/favorites/{storeId}", store.getId())
                        .requestAttr("userId", owner.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(favoriteRepository.findAllByUserIdOrderByIdAsc(owner.getId()))
                .isEmpty();
    }

    @Test
    void unknownStoreCannotBeFavoritedOrUnfavorited() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);

        mockMvc.perform(put("/api/v1/favorites/{storeId}", Long.MAX_VALUE)
                        .requestAttr("userId", consumer.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/favorites/{storeId}", Long.MAX_VALUE)
                        .requestAttr("userId", consumer.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    void unknownUserCannotUseFavorites() throws Exception {
        Store store = saveStore();

        mockMvc.perform(get("/api/v1/favorites")
                        .requestAttr("userId", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/favorites/{storeId}", store.getId())
                        .requestAttr("userId", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/favorites/{storeId}", store.getId())
                        .requestAttr("userId", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void invalidStoreIdReturnsBadRequest() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);

        mockMvc.perform(put("/api/v1/favorites/{storeId}", "not-a-number")
                        .requestAttr("userId", consumer.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(delete("/api/v1/favorites/{storeId}", "not-a-number")
                        .requestAttr("userId", consumer.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void deletingConsumerRemovesFavoritesAndKeepsStore() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore();
        favoriteRepository.saveAndFlush(new Favorite(consumer, store));

        mockMvc.perform(delete("/api/v1/users/me")
                        .requestAttr("userId", consumer.getId()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(favoriteRepository.findAllByUserIdOrderByIdAsc(consumer.getId()))
                .isEmpty();
        assertThat(userRepository.findById(consumer.getId())).isEmpty();
        assertThat(storeRepository.findById(store.getId())).isPresent();
    }

    private void addFavorite(User consumer, Store store) throws Exception {
        mockMvc.perform(put("/api/v1/favorites/{storeId}", store.getId())
                        .requestAttr("userId", consumer.getId()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    private void deleteFavorite(User consumer, Store store) throws Exception {
        mockMvc.perform(delete("/api/v1/favorites/{storeId}", store.getId())
                        .requestAttr("userId", consumer.getId()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    private User saveUser(UserRole role) {
        User user = new User(uniqueValue() + "@example.com",
                             "encoded-password",
                             "테스트 회원",
                             "010-1234-5678",
                             role);
        return userRepository.saveAndFlush(user);
    }

    private Store saveStore() {
        User owner = saveUser(UserRole.OWNER);
        Store store = new Store(owner,
                                "즐겨찾기 매장 " + uniqueValue(),
                                "대구광역시 중구 동성로 10",
                                "1층",
                                "053-123-4567",
                                "매장 설명",
                                new BigDecimal("35.8714354"),
                                new BigDecimal("128.6014450"));
        return storeRepository.saveAndFlush(store);
    }

    private String uniqueValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
