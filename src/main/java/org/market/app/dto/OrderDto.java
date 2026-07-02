package org.market.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OrderDto {

    private Long id;
    private Long totalSum;
    private List<OrderItemsDto> items;
}
