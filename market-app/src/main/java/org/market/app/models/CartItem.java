package org.market.app.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("cart_item")
public class CartItem {

    @Id
    private Long id;
    private Long userId;
    private Long productId;
    private Integer count;
}
