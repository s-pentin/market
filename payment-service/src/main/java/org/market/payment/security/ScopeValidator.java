package org.market.payment.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Проверяет, что токен выдан с требуемым scope — дополнительно к issuer/audience.
 */
public class ScopeValidator implements OAuth2TokenValidator<Jwt> {

    private static final String REQUIRED_SCOPE = "payment-service-audience";
    private static final OAuth2Error ERROR = new OAuth2Error(
            "insufficient_scope", "The required scope '" + REQUIRED_SCOPE + "' is missing", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> scopes = jwt.getClaimAsStringList("scope");
        if (scopes != null && scopes.contains(REQUIRED_SCOPE)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(ERROR);
    }
}
