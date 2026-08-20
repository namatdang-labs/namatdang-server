package com.namatdang.namatdang.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.repository.DealItemRepository;
import com.namatdang.namatdang.reservation.repository.ReservationRepository;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "security.cors.allowed-origins=http://localhost:3000")
class ReservationIdempotencyTests extends ReservationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealItemRepository dealItemRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void sameKeyAndSameRequestReplaysFirstResponseWithoutReservingTwice() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        Long itemId = deal.getItems().get(0).getId();
        String key = uniqueValue();
        String body = reservationBody(deal.getId(), itemId, 2);

        String first = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(reservationRepository.count()).isEqualTo(1);
        // 재시도로 수량이 두 번 차감되지 않아야 한다.
        assertThat(dealItemRepository.findById(itemId).orElseThrow().getRemainingQuantity()).isEqualTo(3);
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        Long itemId = deal.getItems().get(0).getId();
        String key = uniqueValue();

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), itemId, 2)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), itemId, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    /**
     * 품목 순서만 다른 요청은 업무적으로 같은 요청이므로, 해시가 같아 최초 응답을 재현해야 한다.
     */
    @Test
    void itemOrderDoesNotChangeIdempotencyHash() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        Long firstItemId = deal.getItems().get(0).getId();
        Long secondItemId = deal.getItems().get(1).getId();
        String key = uniqueValue();

        String first = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), firstItemId, 1, secondItemId, 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String reordered = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), secondItemId, 1, firstItemId, 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(reordered).isEqualTo(first);
    }

    @Test
    void sameKeyOnDifferentOperationsDoesNotCollide() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        String sharedKey = uniqueValue();

        String created = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // 유일성 기준이 사용자·작업·키 조합이므로 취소가 같은 키를 써도 충돌하지 않는다.
        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationIdOf(created))
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", sharedKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void cancelReplaysFirstResponseAndRestoresQuantityOnce() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        Long itemId = deal.getItems().get(0).getId();
        String cancelKey = uniqueValue();

        String created = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), itemId, 2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long reservationId = reservationIdOf(created);

        String firstCancel = mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", cancelKey))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String secondCancel = mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", cancelKey))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(secondCancel).isEqualTo(firstCancel);
        assertThat(dealItemRepository.findById(itemId).orElseThrow().getRemainingQuantity()).isEqualTo(5);
    }

    /**
     * 멱등키가 달라도 이미 취소된 예약은 수량을 다시 복원하지 않는다(INV-03).
     */
    @Test
    void cancelWithNewKeyDoesNotRestoreQuantityTwice() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        Long itemId = deal.getItems().get(0).getId();

        String created = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), itemId, 2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long reservationId = reservationIdOf(created);

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        assertThat(dealItemRepository.findById(itemId).orElseThrow().getRemainingQuantity()).isEqualTo(5);
    }

    @Test
    void preflightAllowsIdempotencyKeyHeader() throws Exception {
        mockMvc.perform(options("/api/v1/reservations")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers",
                                           org.hamcrest.Matchers.containsString("Idempotency-Key")));
    }

    private long reservationIdOf(String responseBody) {
        Number reservationId = JsonPath.parse(responseBody).read("$.reservationId", Number.class);
        return reservationId.longValue();
    }
}
