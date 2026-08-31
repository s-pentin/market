package org.market.payment.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeValidatorTest {

    private final ScopeValidator validator = new ScopeValidator();

    private Jwt jwtWithScope(String scope) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none").subject("user");
        if (scope != null) {
            builder.claim("scope", List.of(scope));
        }
        return builder.build();
    }

    @Test
    void scopeContainsRequiredScope_succeeds() {
        assertThat(validator.validate(jwtWithScope("payment-service-audience")).hasErrors()).isFalse();
    }

    @Test
    void scopeIsDifferent_fails() {
        assertThat(validator.validate(jwtWithScope("something-else")).hasErrors()).isTrue();
    }

    @Test
    void scopeMissing_fails() {
        assertThat(validator.validate(jwtWithScope(null)).hasErrors()).isTrue();
    }
}
