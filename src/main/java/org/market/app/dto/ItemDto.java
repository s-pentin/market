package org.market.app.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ItemDto {

    long id;
    String title;
    String description;
    String imgPath;
    long price;
    int count;
}
