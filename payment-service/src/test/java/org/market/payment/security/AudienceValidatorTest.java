package org.market.payment.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator();

    private Jwt jwtWithAudience(String audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none").subject("user");
        if (audience != null) {
            builder.audience(List.of(audience));
        }
        return builder.build();
    }

    @Test
    void audienceContainsPaymentService_succeeds() {
        assertThat(validator.validate(jwtWithAudience("payment-service")).hasErrors()).isFalse();
    }

    @Test
    void audienceIsDifferent_fails() {
        assertThat(validator.validate(jwtWithAudience("something-else")).hasErrors()).isTrue();
    }

    @Test
    void audienceMissing_fails() {
        assertThat(validator.validate(jwtWithAudience(null)).hasErrors()).isTrue();
    }
}