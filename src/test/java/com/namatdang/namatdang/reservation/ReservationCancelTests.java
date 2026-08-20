package com.namatdang.namatdang.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealStatus;
import com.namatdang.namatdang.deal.repository.DealItemRepository;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import jakarta.persistence.EntityManager;
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
class ReservationCancelTests extends ReservationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealItemRepository dealItemRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void cancelRestoresEveryReservedQuantity() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        List<Long> itemIds = deal.getItems().stream().map(item -> item.getId()).toList();

        long reservationId = createReservation(consumer, deal.getId(),
                                               reservationBody(deal.getId(),
                                                               itemIds.get(0), 2,
                                                               itemIds.get(1), 1));

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.canceledAt").isNotEmpty());

        assertThat(dealItemRepository.findById(itemIds.get(0)).orElseThrow().getRemainingQuantity()).isEqualTo(5);
        assertThat(dealItemRepository.findById(itemIds.get(1)).orElseThrow().getRemainingQuantity()).isEqualTo(3);
    }

    /**
     * 판매 마감 전이라면 취소로 생긴 수량은 다시 예약 가능하므로 딜을 판매중으로 되돌린다.
     */
    @Test
    void cancelBeforeSalesEndResumesSellingOnEndedDeal() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 1, 1);

        long reservationId = createReservation(consumer, deal.getId(),
                                               reservationBody(deal.getId(),
                                                               deal.getItems().get(0).getId(), 1,
                                                               deal.getItems().get(1).getId(), 1));

        assertThat(dealRepository.findById(deal.getId()).orElseThrow().getStatus()).isEqualTo(DealStatus.ENDED);

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue()))
                .andExpect(status().isOk());

        assertThat(dealRepository.findById(deal.getId()).orElseThrow().getStatus()).isEqualTo(DealStatus.SELLING);
    }

    /**
     * 판매 마감 후 복원된 수량은 예약 가능 수량이 아니므로 딜을 다시 열지 않는다.
     */
    @Test
    void cancelAfterSalesEndRestoresQuantityButKeepsDealEnded() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 1, 1);
        Long itemId = deal.getItems().get(0).getId();

        long reservationId = createReservation(consumer, deal.getId(),
                                               reservationBody(deal.getId(),
                                                               itemId, 1,
                                                               deal.getItems().get(1).getId(), 1));

        // 예약을 받은 뒤 판매 마감시각을 과거로 옮겨 마감 후 취소 상황을 만든다.
        Deal endedDeal = dealRepository.findById(deal.getId()).orElseThrow();
        assertThat(endedDeal.getStatus()).isEqualTo(DealStatus.ENDED);
        moveSalesEndToPast(endedDeal);

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        assertThat(dealItemRepository.findById(itemId).orElseThrow().getRemainingQuantity()).isEqualTo(1);
        assertThat(dealRepository.findById(deal.getId()).orElseThrow().getStatus()).isEqualTo(DealStatus.ENDED);
    }

    /**
     * 판매 마감시각은 취소 가능 여부를 제한하지 않는다.
     */
    @Test
    void cancelIsAllowedAfterSalesEnd() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);

        long reservationId = createReservation(consumer, deal.getId(),
                                               reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1));

        moveSalesEndToPast(dealRepository.findById(deal.getId()).orElseThrow());

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void consumerCannotCancelOtherConsumersReservation() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        User otherConsumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);

        long reservationId = createReservation(consumer, deal.getId(),
                                               reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1));

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(otherConsumer))
                        .header("Idempotency-Key", uniqueValue()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void cancelingUnknownReservationReturnsNotFound() throws Exception {
        User consumer = saveUser(UserRole.CONSUMER);

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", Long.MAX_VALUE)
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", uniqueValue()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void cancelingWithoutIdempotencyKeyReturnsBadRequest() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);

        long reservationId = createReservation(consumer, deal.getId(),
                                               reservationBody(deal.getId(), deal.getItems().get(0).getId(), 1));

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(consumer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /**
     * 딜 수정 API는 아직 #7 범위라 마감시각을 바꿀 도메인 메서드가 없다. 마감 후 상황을 만들기
     * 위해 테스트에서만 직접 갱신한다.
     */
    private void moveSalesEndToPast(Deal deal) {
        // 벌크 갱신 뒤 clear()가 아직 반영되지 않은 재고 차감을 버리지 않도록 먼저 flush 한다.
        entityManager.flush();
        entityManager.createQuery(
                        "update Deal target set target.salesEndsAt = :salesEndsAt where target.id = :dealId")
                .setParameter("salesEndsAt", hoursLater(-1))
                .setParameter("dealId", deal.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private long createReservation(User consumer, Long dealId, String body) throws Exception {
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
