package com.namatdang.namatdang.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealItem;
import com.namatdang.namatdang.deal.entity.DealStatus;
import com.namatdang.namatdang.deal.repository.DealItemRepository;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import java.util.List;
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
class ReservationApiTests extends ReservationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealItemRepository dealItemRepository;

    @Test
    void consumerReservesMultipleItemsAndServerComputesTotal() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        List<DealItem> items = deal.getItems();

        // 소금빵 2000원 × 2 + 크루아상 3000원 × 1 = 7000원
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(),
                                                 items.get(0).getId(), 2,
                                                 items.get(1).getId(), 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").isNumber())
                .andExpect(jsonPath("$.dealId").value(deal.getId()))
                .andExpect(jsonPath("$.storeId").value(store.getId()))
                .andExpect(jsonPath("$.storeName").value(store.getName()))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.totalAmount").value(7000))
                .andExpect(jsonPath("$.canceledAt").isEmpty())
                .andExpect(jsonPath("$.pickedUpAt").isEmpty())
                .andExpect(jsonPath("$.items.length()").value(2));

        assertThat(dealItemRepository.findById(items.get(0).getId()).orElseThrow().getRemainingQuantity())
                .isEqualTo(3);
        assertThat(dealItemRepository.findById(items.get(1).getId()).orElseThrow().getRemainingQuantity())
                .isEqualTo(2);
    }

    @Test
    void reservationKeepsItemSnapshotWhenDealItemChangesLater() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        DealItem item = deal.getItems().get(0);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), item.getId(), 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].name").value("소금빵"))
                .andExpect(jsonPath("$.items[0].salePrice").value(2000))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(4000));
    }

    @Test
    void reservationRollsBackEveryItemWhenOneIsOutOfStock() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 1);
        List<DealItem> items = deal.getItems();

        // 크루아상 재고가 1개뿐이라 2개 요청은 실패한다. 소금빵도 함께 무산돼야 한다.
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(),
                                                 items.get(0).getId(), 2,
                                                 items.get(1).getId(), 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));

        assertThat(dealItemRepository.findById(items.get(0).getId()).orElseThrow().getRemainingQuantity())
                .isEqualTo(5);
        assertThat(dealItemRepository.findById(items.get(1).getId()).orElseThrow().getRemainingQuantity())
                .isEqualTo(1);
    }

    @Test
    void reservingAllRemainingQuantityEndsDeal() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 2, 1);
        List<DealItem> items = deal.getItems();

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(),
                                                 items.get(0).getId(), 2,
                                                 items.get(1).getId(), 1)))
                .andExpect(status().isCreated());

        assertThat(dealRepository.findById(deal.getId()).orElseThrow().getStatus())
                .isEqualTo(DealStatus.ENDED);
    }

    @Test
    void reservingUnknownDealItemReturnsBadRequest() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), Long.MAX_VALUE, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void reservingSameDealItemTwiceInOneRequestReturnsBadRequest() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        Long itemId = deal.getItems().get(0).getId();

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), itemId, 1, itemId, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void reservingOversizedQuantityReturnsBadRequest() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 50, 3);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), deal.getItems().get(0).getId(), 11)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void reservingZeroQuantityReturnsBadRequest() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), deal.getItems().get(0).getId(), 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void reservingDealPastSalesEndsAtIsRejected() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, hoursLater(-1), 5, 3);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAL_NOT_RESERVABLE"));
    }

    @Test
    void reservingEndedDealIsRejected() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        deal.markEnded();
        dealRepository.saveAndFlush(deal);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAL_NOT_RESERVABLE"));
    }

    @Test
    void reservingSameDealTwiceIsRejected() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        Long itemId = deal.getItems().get(0).getId();

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), itemId, 1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), itemId, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_ALREADY_EXISTS"));
    }

    /**
     * (consumer_id, deal_id) 유일 제약은 취소 상태에도 적용되므로 재예약할 수 없다.
     */
    @Test
    void reservingAgainAfterCancelIsRejected() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        Long itemId = deal.getItems().get(0).getId();

        String created = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), itemId, 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long reservationId = reservationIdOf(created);

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), itemId, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_ALREADY_EXISTS"));
    }

    @Test
    void ownerCannotCreateReservation() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(owner))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void creatingReservationWithoutIdempotencyKeyReturnsBadRequest() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void creatingReservationWithBlankIdempotencyKeyReturnsBadRequest() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void consumerGetsOwnReservationsOnly() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        User otherConsumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        Long itemId = deal.getItems().get(0).getId();

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), itemId, 1)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/reservations")
                        .header("Authorization", bearerToken(otherConsumer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dealId").value(deal.getId()));
    }

    @Test
    void consumerCannotGetOtherConsumersReservation() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        User otherConsumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);

        String created = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/v1/reservations/{reservationId}", reservationIdOf(created))
                        .header("Authorization", bearerToken(otherConsumer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void reservationListFiltersByStatus() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);

        String created = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .param("status", "RESERVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .param("status", "CANCELED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationIdOf(created))
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .param("status", "CANCELED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void oversizedPageSizeReturnsBadRequest() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);

        mockMvc.perform(get("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void negativePageReturnsBadRequest() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);

        mockMvc.perform(get("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unknownReservationReturnsNotFound() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);

        mockMvc.perform(get("/api/v1/reservations/{reservationId}", Long.MAX_VALUE)
                        .header("Authorization", bearerToken(consumer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    private long reservationIdOf(String responseBody) {
        Number reservationId = JsonPath.parse(responseBody).read("$.reservationId", Number.class);
        return reservationId.longValue();
    }
}
