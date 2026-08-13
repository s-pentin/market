package org.market.app.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.cache")
@Validated
public record CacheProperties(@NotNull Duration productTtl, @NotNull Duration productListTtl) {
}