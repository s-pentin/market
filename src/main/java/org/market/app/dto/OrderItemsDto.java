package org.market.app.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderItemsDto {
    private Long id;
    private String title;
    private long price;
    private int count;
}
