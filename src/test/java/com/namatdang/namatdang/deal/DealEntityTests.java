package com.namatdang.namatdang.deal;

import static org.assertj.core.api.Assertions.assertThat;

import com.namatdang.namatdang.deal.entity.Deal;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

class DealEntityTests {

    @Test
    void recentStoreImageLookupHasACompositeIndexForItsOrder() {
        Table table = Deal.class.getAnnotation(Table.class);

        assertThat(table.indexes()).anySatisfy(index -> {
            assertThat(index.name()).isEqualTo("idx_deals_store_created_at_id");
            assertThat(index.columnList()).isEqualTo("store_id, created_at, id");
        });
    }
}
