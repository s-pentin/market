package org.market.app.services;

import org.market.app.dto.ItemDto;
import org.market.app.models.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ItemDto toItemDto(Product product, int cartCount) {
        return ItemDto.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .imgPath(product.getImgPath())
                .price(product.getPrice())
                .count(cartCount)
                .build();
    }
}
