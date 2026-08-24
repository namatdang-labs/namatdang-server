package com.namatdang.namatdang.store.dto;

import com.namatdang.namatdang.store.entity.Store;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@AllArgsConstructor
public class StorePageResponseDto {

    private List<StoreResponseDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static StorePageResponseDto from(Page<Store> stores) {
        List<StoreResponseDto> content = stores.getContent().stream()
                .map(StoreResponseDto::from)
                .toList();

        return new StorePageResponseDto(content,
                                        stores.getNumber(),
                                        stores.getSize(),
                                        stores.getTotalElements(),
                                        stores.getTotalPages(),
                                        stores.isFirst(),
                                        stores.isLast());
    }
}
