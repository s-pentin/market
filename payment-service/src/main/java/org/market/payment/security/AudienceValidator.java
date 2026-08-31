package org.market.payment.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final String REQUIRED_AUDIENCE = "payment-service";
    private static final OAuth2Error ERROR = new OAuth2Error(
            "invalid_token", "The required audience '" + REQUIRED_AUDIENCE + "' is missing", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getAudience() != null && jwt.getAudience().contains(REQUIRED_AUDIENCE)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(ERROR);
    }
}