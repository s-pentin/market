package org.market.app.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("order_items")
public class OrderItems {

    @Id
    private Long id;
    private Long orderId;
    private Long productId;
    private String title;
    private BigDecimal price;
    private int count;
}
