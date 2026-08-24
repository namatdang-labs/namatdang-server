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

        return from(deals, content);
    }

    public static DealPageResponseDto fromSelling(Page<Deal> deals) {
        List<DealResponseDto> content = deals.getContent().stream()
                .map(DealResponseDto::fromSelling)
                .toList();

        return from(deals, content);
    }

    public static DealPageResponseDto from(Page<?> page, List<DealResponseDto> content) {
        return new DealPageResponseDto(content,
                                       page.getNumber(),
                                       page.getSize(),
                                       page.getTotalElements(),
                                       page.getTotalPages(),
                                       page.isFirst(),
                                       page.isLast());
    }
}
