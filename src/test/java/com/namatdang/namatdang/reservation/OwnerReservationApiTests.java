package com.namatdang.namatdang.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.repository.DealItemRepository;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
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
class OwnerReservationApiTests extends ReservationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealItemRepository dealItemRepository;

    @Test
    void ownerPicksUpReservation() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        Long itemId = deal.getItems().get(0).getId();
        long reservationId = createReservation(consumer, reservationBody(deal.getId(), itemId, 2));

        mockMvc.perform(post("/api/v1/owner/reservations/{reservationId}/pickup", reservationId)
                        .header("Authorization", bearerToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.pickedUpAt").isNotEmpty());

        // 수령 완료는 재고 수량을 바꾸지 않는다.
        assertThat(dealItemRepository.findById(itemId).orElseThrow().getRemainingQuantity()).isEqualTo(3);
    }

    @Test
    void pickingUpTwiceReturnsCurrentResult() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        long reservationId = createReservation(consumer,
                                               reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1));

        mockMvc.perform(post("/api/v1/owner/reservations/{reservationId}/pickup", reservationId)
                        .header("Authorization", bearerToken(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/owner/reservations/{reservationId}/pickup", reservationId)
                        .header("Authorization", bearerToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));
    }

    @Test
    void cancelAfterPickUpIsRejected() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        Long itemId = deal.getItems().get(0).getId();
        long reservationId = createReservation(consumer, reservationBody(deal.getId(), itemId, 2));

        mockMvc.perform(post("/api/v1/owner/reservations/{reservationId}/pickup", reservationId)
                        .header("Authorization", bearerToken(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_CANCELABLE"));

        // 거절된 취소는 수량을 복원하지 않는다.
        assertThat(dealItemRepository.findById(itemId).orElseThrow().getRemainingQuantity()).isEqualTo(3);
    }

    @Test
    void pickUpAfterCancelIsRejected() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        long reservationId = createReservation(consumer,
                                               reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1));

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/owner/reservations/{reservationId}/pickup", reservationId)
                        .header("Authorization", bearerToken(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_PICKUPABLE"));
    }

    @Test
    void otherOwnerCannotPickUpReservation() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User otherOwner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        long reservationId = createReservation(consumer,
                                               reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1));

        mockMvc.perform(post("/api/v1/owner/reservations/{reservationId}/pickup", reservationId)
                        .header("Authorization", bearerToken(otherOwner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void consumerCannotPickUpReservation() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        long reservationId = createReservation(consumer,
                                               reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1));

        mockMvc.perform(post("/api/v1/owner/reservations/{reservationId}/pickup", reservationId)
                        .header("Authorization", bearerToken(consumer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void ownerGetsStoreReservationsOnly() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User otherOwner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Store otherStore = saveStore(otherOwner);
        Deal deal = saveDeal(store, 5, 3);
        createReservation(consumer, reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1));

        mockMvc.perform(get("/api/v1/owner/stores/{storeId}/reservations", store.getId())
                        .header("Authorization", bearerToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dealId").value(deal.getId()));

        mockMvc.perform(get("/api/v1/owner/stores/{storeId}/reservations", otherStore.getId())
                        .header("Authorization", bearerToken(otherOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void ownerCannotGetOtherOwnersStoreReservations() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User otherOwner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);

        mockMvc.perform(get("/api/v1/owner/stores/{storeId}/reservations", store.getId())
                        .header("Authorization", bearerToken(otherOwner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    void ownerGetsReservationDetailWithItemSnapshot() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        long reservationId = createReservation(consumer,
                                               reservationBody(deal.getId(), deal.getItems().get(0).getId(), 2));

        mockMvc.perform(get("/api/v1/owner/reservations/{reservationId}", reservationId)
                        .header("Authorization", bearerToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.totalAmount").value(4000))
                .andExpect(jsonPath("$.items[0].name").value("소금빵"))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void ownerCannotGetOtherOwnersReservationDetail() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User otherOwner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        long reservationId = createReservation(consumer,
                                               reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1));

        mockMvc.perform(get("/api/v1/owner/reservations/{reservationId}", reservationId)
                        .header("Authorization", bearerToken(otherOwner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void storeReservationListFiltersByStatus() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        long reservationId = createReservation(consumer,
                                               reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1));

        mockMvc.perform(post("/api/v1/owner/reservations/{reservationId}/pickup", reservationId)
                        .header("Authorization", bearerToken(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/owner/stores/{storeId}/reservations", store.getId())
                        .header("Authorization", bearerToken(owner))
                        .param("status", "PICKED_UP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/owner/stores/{storeId}/reservations", store.getId())
                        .header("Authorization", bearerToken(owner))
                        .param("status", "RESERVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pickUpDoesNotRequireIdempotencyKey() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        long reservationId = createReservation(consumer,
                                               reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1));

        mockMvc.perform(post("/api/v1/owner/reservations/{reservationId}/pickup", reservationId)
                        .header("Authorization", bearerToken(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void pickingUpUnknownReservationReturnsNotFound() throws Exception {
        User owner = saveUser(UserRole.OWNER);

        mockMvc.perform(post("/api/v1/owner/reservations/{reservationId}/pickup", Long.MAX_VALUE)
                        .header("Authorization", bearerToken(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    private long createReservation(User consumer, String body) throws Exception {
        String created = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.parse(created).read("$.reservationId", Number.class).longValue();
    }
}
