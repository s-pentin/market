package org.market.app.config;

import io.netty.channel.ChannelOption;
import org.market.app.payment.ApiClient;
import org.market.app.payment.api.BalanceApi;
import org.market.app.payment.api.PaymentApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class PaymentClientConfig {

    @Bean
    public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            ReactiveOAuth2AuthorizedClientService authorizedClientService) {

        var provider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(clientCredentials -> clientCredentials
                        .accessTokenResponseClient(new WebClientReactiveClientCredentialsTokenResponseClient()))
                .build();

        var manager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    @Bean
    public ApiClient paymentApiClient(
            @Value("${app.payment.service.url:http://localhost:8082}") String paymentServiceUrl,
            @Value("${app.payment.service.timeout-ms:3000}") int timeoutMs,
            ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {

        var oauth2Filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Filter.setDefaultClientRegistrationId("payment-service");

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs)
                .responseTimeout(Duration.ofMillis(timeoutMs));

        WebClient webClient = WebClient.builder()
                .baseUrl(paymentServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(oauth2Filter)
                .build();

        ApiClient apiClient = new ApiClient(webClient);
        apiClient.setBasePath(paymentServiceUrl);
        return apiClient;
    }

    @Bean
    public BalanceApi balanceApi(ApiClient paymentApiClient) {
        return new BalanceApi(paymentApiClient);
    }

    @Bean
    public PaymentApi paymentApi(ApiClient paymentApiClient) {
        return new PaymentApi(paymentApiClient);
    }
}