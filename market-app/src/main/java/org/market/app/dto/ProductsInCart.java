package org.market.app.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProductsInCart {
    List<ItemDto> items;
    BigDecimal totalCost;
}
