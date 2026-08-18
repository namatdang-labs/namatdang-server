package com.namatdang.namatdang.deal.dto;

import com.namatdang.namatdang.deal.entity.Deal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@AllArgsConstructor
public class DealPageResponseDto {

    private List<DealResponseDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static DealPageResponseDto from(Page<Deal> deals) {
        List<DealResponseDto> content = deals.getContent().stream()
                .map(DealResponseDto::from)
                .toList();

        return new DealPageResponseDto(content,
                                       deals.getNumber(),
                                       deals.getSize(),
                                       deals.getTotalElements(),
                                       deals.getTotalPages(),
                                       deals.isFirst(),
                                       deals.isLast());
    }
}
