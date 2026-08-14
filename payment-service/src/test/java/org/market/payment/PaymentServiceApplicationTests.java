package org.market.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@SpringBootTest
class PaymentServiceApplicationTests {

    // Без реального Keycloak ReactiveJwtDecoders.fromIssuerLocation() при создании бина
    // тянет OIDC discovery-документ и падает с ConnectException — подменяем декодер моком.
    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    @Test
    void contextLoads() {
    }

}
