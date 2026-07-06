package org.market.app.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderItemsDto {
    private Long id;
    private String title;
    private BigDecimal price;
    private int count;
}
