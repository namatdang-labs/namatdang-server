package com.namatdang.namatdang.reservation;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealItem;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 예약 테스트가 공유하는 픽스처. 딜 테스트의 헬퍼와 같은 구조를 유지한다.
 */
public abstract class ReservationTestSupport extends IntegrationTestSupport {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected StoreRepository storeRepository;

    @Autowired
    protected DealRepository dealRepository;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    protected String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.issue(user.getId(), user.getRole());
    }

    protected String uniqueValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    protected LocalDateTime hoursLater(int hours) {
        return LocalDateTime.now().plusHours(hours).truncatedTo(ChronoUnit.SECONDS);
    }

    protected User saveUser(UserRole role) {
        User user = new User(uniqueValue() + "@example.com",
                             "encoded-password",
                             role == UserRole.OWNER ? "테스트 사장님" : "테스트 소비자",
                             "010-1234-5678",
                             role);
        return userRepository.saveAndFlush(user);
    }

    protected Store saveStore(User owner) {
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

    /**
     * 소금빵(2000원)과 크루아상(3000원) 두 품목을 가진 판매중 딜을 만든다.
     */
    protected Deal saveDeal(Store store, int firstQuantity, int secondQuantity) {
        return saveDeal(store, hoursLater(3), firstQuantity, secondQuantity);
    }

    protected Deal saveDeal(Store store, LocalDateTime salesEndsAt, int firstQuantity, int secondQuantity) {
        Deal deal = new Deal(store, salesEndsAt, "마감 임박 상품입니다.");
        deal.addItem(new DealItem("소금빵", firstQuantity, 4000, 2000));
        deal.addItem(new DealItem("크루아상", secondQuantity, 5000, 3000));
        return dealRepository.saveAndFlush(deal);
    }

    protected String reservationBody(Long dealId, Long firstItemId, int firstQuantity) {
        return """
                {"dealId":%d,"items":[{"dealItemId":%d,"quantity":%d}]}"""
                .formatted(dealId, firstItemId, firstQuantity);
    }

    protected String reservationBody(Long dealId,
                                     Long firstItemId, int firstQuantity,
                                     Long secondItemId, int secondQuantity) {
        return """
                {"dealId":%d,"items":[{"dealItemId":%d,"quantity":%d},
                                      {"dealItemId":%d,"quantity":%d}]}"""
                .formatted(dealId, firstItemId, firstQuantity, secondItemId, secondQuantity);
    }
}
