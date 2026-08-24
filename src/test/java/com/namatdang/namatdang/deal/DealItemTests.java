package com.namatdang.namatdang.deal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.namatdang.namatdang.deal.entity.DealItem;
import com.namatdang.namatdang.deal.entity.DealItemStatus;
import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import org.junit.jupiter.api.Test;

class DealItemTests {

    @Test
    void decreaseMarksSoldOutWhenRemainingReachesZero() {
        DealItem item = new DealItem("소금빵", 3, 4000, 2000);

        item.decrease(3);

        assertThat(item.getRemainingQuantity()).isZero();
        assertThat(item.getStatus()).isEqualTo(DealItemStatus.SOLD_OUT);
    }

    @Test
    void decreaseBeyondRemainingIsRejected() {
        DealItem item = new DealItem("소금빵", 3, 4000, 2000);

        assertThatThrownBy(() -> item.decrease(4))
                .isInstanceOf(BusinessLogicException.class)
                .hasFieldOrPropertyWithValue("exceptionCode", ExceptionCode.OUT_OF_STOCK);

        assertThat(item.getRemainingQuantity()).isEqualTo(3);
        assertThat(item.getStatus()).isEqualTo(DealItemStatus.SELLING);
    }

    @Test
    void restoreBringsSoldOutItemBackToSelling() {
        DealItem item = new DealItem("소금빵", 3, 4000, 2000);
        item.decrease(3);

        item.restore(1);

        assertThat(item.getRemainingQuantity()).isEqualTo(1);
        assertThat(item.getStatus()).isEqualTo(DealItemStatus.SELLING);
    }

    @Test
    void restoreBeyondTotalQuantityIsRejected() {
        DealItem item = new DealItem("소금빵", 3, 4000, 2000);
        item.decrease(1);

        assertThatThrownBy(() -> item.restore(2))
                .isInstanceOf(BusinessLogicException.class);

        assertThat(item.getRemainingQuantity()).isEqualTo(2);
    }

    @Test
    void discountRateIsZeroWhenOriginalPriceIsNotHigher() {
        DealItem item = new DealItem("소금빵", 3, 2000, 2000);

        assertThat(item.discountRate()).isZero();
    }
}
