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
@Table(name = "orders")
public class Orders {

    @Id
    private Long id;
    private Long userId;
    private BigDecimal totalSum;

//    @OneToMany(mappedBy = "orders", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
//    private List<OrderItems> items;
}



