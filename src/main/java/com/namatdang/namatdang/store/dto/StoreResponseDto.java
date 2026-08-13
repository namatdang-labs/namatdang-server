package com.namatdang.namatdang.store.dto;

import com.namatdang.namatdang.store.entity.Store;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StoreResponseDto {

    private Long id;
    private String name;
    private String address;
    private String addressDetail;
    private String phoneNumber;
    private String description;
    private BigDecimal latitude;
    private BigDecimal longitude;

    public static StoreResponseDto from(Store store) {
        return new StoreResponseDto(
                store.getId(),
                store.getName(),
                store.getAddress(),
                store.getAddressDetail(),
                store.getPhoneNumber(),
                store.getDescription(),
                store.getLatitude(),
                store.getLongitude()
        );
    }
}
