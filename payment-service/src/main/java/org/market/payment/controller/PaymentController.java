package org.market.payment.controller;

import org.market.payment.model.BalanceResponse;
import org.market.payment.model.PaymentRequest;
import org.market.payment.model.PaymentResponse;
import org.market.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class PaymentController implements BalanceApi, PaymentApi {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public Mono<ResponseEntity<BalanceResponse>> getBalance(ServerWebExchange exchange) {
        return paymentService.getBalance()
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
                .map(PaymentRequest::getAmount)
                .flatMap(paymentService::processPayment)
                .map(balance -> {
                    PaymentResponse response = new PaymentResponse()
                            .success(true)
                            .newBalance(balance.getAmount())
                            .message("OK");
                    return ResponseEntity.ok(response);
                });
    }
}