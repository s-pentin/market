package org.market.payment.controller;

import org.market.payment.model.BalanceResponse;
import org.market.payment.model.PaymentRecordResponse;
import org.market.payment.model.PaymentRequest;
import org.market.payment.model.PaymentResponse;
import org.market.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
public class PaymentController implements BalanceApi, PaymentApi {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public Mono<ResponseEntity<BalanceResponse>> getBalance(Long userId, ServerWebExchange exchange) {
        return paymentService.getBalance(userId)
                .map(balance -> {
                    BalanceResponse response = new BalanceResponse()
                            .balance(balance.getAmount())
                            .currency(balance.getCurrency());
                    return ResponseEntity.ok(response);
                });
    }

    @Override
    public Mono<ResponseEntity<PaymentResponse>> processPayment(
            Mono<PaymentRequest> paymentRequest,
            ServerWebExchange exchange) {
        return paymentRequest
                .flatMap(request -> paymentService.processPayment(
                        request.getUserId(), request.getOrderId(), request.getIdempotencyKey(), request.getAmount()))
                .map(outcome -> {
                    PaymentResponse response = new PaymentResponse()
                            .success(true)
                            .newBalance(outcome.balance().getAmount())
                            .paymentId(outcome.paymentRecord().getId())
                            .message("OK");
                    return ResponseEntity.ok(response);
                });
    }

    @Override
    public Mono<ResponseEntity<PaymentRecordResponse>> getPaymentByIdempotencyKey(
            UUID idempotencyKey, ServerWebExchange exchange) {
        return paymentService.findByIdempotencyKey(idempotencyKey)
                .flatMap(record -> paymentService.getBalance(record.getUserId())
                        .map(balance -> {
                            PaymentRecordResponse response = new PaymentRecordResponse()
                                    .status(PaymentRecordResponse.StatusEnum.fromValue(record.getStatus().name()))
                                    .newBalance(balance.getAmount())
                                    .paymentId(record.getId());
                            return ResponseEntity.ok(response);
                        }))
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
    }
}