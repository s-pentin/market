package org.market.app.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ItemDto {

    long id;
    String title;
    String description;
    String imgPath;
    BigDecimal price;
    int count;
}
