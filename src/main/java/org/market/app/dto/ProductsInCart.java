package org.market.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProductsInCart {
    List<ItemDto> items;
    long totalCost;
}
