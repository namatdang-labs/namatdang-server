package com.namatdang.namatdang.reservation.dto;

import com.namatdang.namatdang.reservation.entity.Reservation;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@AllArgsConstructor
public class ReservationPageResponseDto {

    private List<ReservationResponseDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static ReservationPageResponseDto from(Page<Reservation> reservations) {
        List<ReservationResponseDto> content = reservations.getContent().stream()
                .map(ReservationResponseDto::from)
                .toList();

        return new ReservationPageResponseDto(content,
                                              reservations.getNumber(),
                                              reservations.getSize(),
                                              reservations.getTotalElements(),
                                              reservations.getTotalPages(),
                                              reservations.isFirst(),
                                              reservations.isLast());
    }
}
