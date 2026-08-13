package org.market.payment.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table("balance")
public class Balance {

    @Id
    private Long id;

    private BigDecimal amount;

    private String currency;
}