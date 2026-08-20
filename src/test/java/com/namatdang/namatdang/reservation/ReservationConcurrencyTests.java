package com.namatdang.namatdang.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealItem;
import com.namatdang.namatdang.deal.repository.DealItemRepository;
import com.namatdang.namatdang.idempotency.repository.IdempotencyRequestRepository;
import com.namatdang.namatdang.reservation.entity.Reservation;
import com.namatdang.namatdang.reservation.entity.ReservationStatus;
import com.namatdang.namatdang.reservation.repository.ReservationRepository;
import com.namatdang.namatdang.reservation.service.OwnerReservationService;
import com.namatdang.namatdang.reservation.service.ReservationService;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

/**
 * 잠금이 실제로 동작하는지 확인하려면 트랜잭션이 실제로 커밋돼야 하므로 이 클래스는
 * {@code @Transactional}을 붙이지 않는다. 대신 만들어진 데이터를 직접 정리한다.
 * <p>
 * 다수 소비자의 부하·데드락 검증은 #9에서 별도로 수행하고, 여기서는 잠금 구조가 지키기로 한
 * 정합성만 확인한다.
 */
@SpringBootTest
class ReservationConcurrencyTests extends ReservationTestSupport {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private OwnerReservationService ownerReservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private DealItemRepository dealItemRepository;

    @Autowired
    private IdempotencyRequestRepository idempotencyRequestRepository;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdStoreIds = new ArrayList<>();
    private final List<Long> createdDealIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // deleteAllInBatch는 cascade를 건너뛰어 reservation_items가 남고 FK 제약에 걸린다.
        reservationRepository.deleteAll();
        idempotencyRequestRepository.deleteAll();
        createdDealIds.forEach(dealRepository::deleteById);
        createdStoreIds.forEach(storeRepository::deleteById);
        createdUserIds.forEach(userRepository::deleteById);
        createdDealIds.clear();
        createdStoreIds.clear();
        createdUserIds.clear();
    }

    /**
     * 같은 소비자가 같은 멱등키로 동시에 요청해도 Customer 행 잠금이 두 요청을 직렬화하므로
     * 예약은 한 건만 생기고 재고도 한 번만 차감돼야 한다.
     */
    @Test
    void sameConsumerConcurrentRequestsWithSameKeyCreateOneReservation() throws Exception {
        Fixture fixture = createFixture(5, 3);
        String sharedKey = uniqueValue();
        String body = requestFor(fixture, 2);

        List<Outcome> outcomes = runConcurrently(
                () -> reservationService.createReservation(fixture.consumerId, sharedKey, parse(body)),
                () -> reservationService.createReservation(fixture.consumerId, sharedKey, parse(body)));

        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(remainingOf(fixture.firstItemId)).isEqualTo(3);

        // 직렬화되므로 둘 다 성공하거나, 뒤 요청이 처리 중 충돌로 거절된다. 어느 쪽이든 예약은 1건이다.
        long failures = outcomes.stream().filter(Outcome::failed).count();
        assertThat(failures).isLessThanOrEqualTo(1);
    }

    /**
     * 같은 소비자가 서로 다른 키로 같은 딜을 동시에 예약해도 예약 이력 유일 제약 때문에
     * 한 건만 성립해야 한다.
     */
    @Test
    void sameConsumerConcurrentRequestsWithDifferentKeysCreateOneReservation() throws Exception {
        Fixture fixture = createFixture(5, 3);
        String body = requestFor(fixture, 1);

        runConcurrently(
                () -> reservationService.createReservation(fixture.consumerId, uniqueValue(), parse(body)),
                () -> reservationService.createReservation(fixture.consumerId, uniqueValue(), parse(body)));

        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(remainingOf(fixture.firstItemId)).isEqualTo(4);
    }

    /**
     * 취소와 수령 완료가 같은 예약 행을 두고 경합하면 먼저 잠금을 얻은 쪽만 성립하고,
     * 나머지는 확정된 상태를 다시 읽어 반대 전이를 거절해야 한다(INV-06).
     */
    @Test
    void cancelAndPickUpContentionLeavesOneFinalState() throws Exception {
        Fixture fixture = createFixture(5, 3);
        Reservation reservation = reserve(fixture, 2);

        List<Outcome> outcomes = runConcurrently(
                () -> reservationService.cancelReservation(fixture.consumerId, uniqueValue(), reservation.getId()),
                () -> ownerReservationService.pickUpReservation(fixture.ownerId, reservation.getId()));

        ReservationStatus finalStatus = reservationRepository.findById(reservation.getId())
                .orElseThrow()
                .getStatus();

        // 하나만 성공하므로 최종 상태는 둘 중 하나로 확정된다.
        assertThat(finalStatus).isIn(ReservationStatus.CANCELED, ReservationStatus.PICKED_UP);
        assertThat(outcomes.stream().filter(Outcome::failed).count()).isEqualTo(1);

        // 재고는 최종 상태와 일치해야 한다. 취소가 이겼으면 복원되고, 수령이 이겼으면 그대로다.
        int expectedRemaining = finalStatus == ReservationStatus.CANCELED ? 5 : 3;
        assertThat(remainingOf(fixture.firstItemId)).isEqualTo(expectedRemaining);
    }

    /**
     * 서로 다른 소비자가 남은 재고보다 많이 요청하면 재고만큼만 성공해야 한다(INV-01).
     */
    @Test
    void concurrentConsumersCannotOversell() throws Exception {
        Fixture fixture = createFixture(1, 3);
        Long secondConsumerId = saveTrackedUser(UserRole.CONSUMER).getId();
        String body = requestFor(fixture, 1);

        List<Outcome> outcomes = runConcurrently(
                () -> reservationService.createReservation(fixture.consumerId, uniqueValue(), parse(body)),
                () -> reservationService.createReservation(secondConsumerId, uniqueValue(), parse(body)));

        assertThat(outcomes.stream().filter(outcome -> !outcome.failed()).count()).isEqualTo(1);
        assertThat(remainingOf(fixture.firstItemId)).isZero();
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    private Reservation reserve(Fixture fixture, int quantity) {
        reservationService.createReservation(fixture.consumerId, uniqueValue(), parse(requestFor(fixture, quantity)));

        return reservationRepository.findByConsumerId(fixture.consumerId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private int remainingOf(Long dealItemId) {
        return dealItemRepository.findById(dealItemId).orElseThrow().getRemainingQuantity();
    }

    private String requestFor(Fixture fixture, int quantity) {
        return reservationBody(fixture.dealId, fixture.firstItemId, quantity);
    }

    private com.namatdang.namatdang.reservation.dto.ReservationCreateRequestDto parse(String body) {
        return new tools.jackson.databind.ObjectMapper()
                .readValue(body, com.namatdang.namatdang.reservation.dto.ReservationCreateRequestDto.class);
    }

    /**
     * 두 작업을 같은 순간에 출발시켜 실제로 경합하게 만든다.
     */
    private List<Outcome> runConcurrently(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLine = new CountDownLatch(1);

        try {
            List<Future<Outcome>> futures = List.of(
                    executor.submit(() -> await(startLine, first)),
                    executor.submit(() -> await(startLine, second)));

            startLine.countDown();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(20, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private Outcome await(CountDownLatch startLine, Callable<?> action) throws InterruptedException {
        startLine.await();

        try {
            action.call();
            return new Outcome(null);
        } catch (Exception exception) {
            return new Outcome(exception);
        }
    }

    private Fixture createFixture(int firstQuantity, int secondQuantity) {
        User owner = saveTrackedUser(UserRole.OWNER);
        User consumer = saveTrackedUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        createdStoreIds.add(store.getId());
        Deal deal = saveDeal(store, firstQuantity, secondQuantity);
        createdDealIds.add(deal.getId());

        List<DealItem> items = deal.getItems();
        return new Fixture(owner.getId(), consumer.getId(), deal.getId(), items.get(0).getId());
    }

    private User saveTrackedUser(UserRole role) {
        User user = saveUser(role);
        createdUserIds.add(user.getId());
        return user;
    }

    private record Fixture(Long ownerId, Long consumerId, Long dealId, Long firstItemId) {
    }

    private record Outcome(Exception exception) {

        boolean failed() {
            return exception != null;
        }
    }
}
