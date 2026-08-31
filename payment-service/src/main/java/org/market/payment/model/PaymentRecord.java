package org.market.payment.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table("payment")
public class PaymentRecord {

    @Id
    private Long id;

    private Long orderId;
    private Long userId;
    private UUID idempotencyKey;
    private BigDecimal amount;
    private PaymentRecordStatus status;
    private LocalDateTime createdAt;
}