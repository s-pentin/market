package org.market.app.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.payment.ApiClient;
import org.market.app.payment.api.BalanceApi;
import org.market.app.services.PurchaseService;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentClientConfigTest {

    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void purchaseService_attachesBearerTokenFromTokenEndpoint() {
        wireMock.stubFor(post(urlEqualTo("/realms/market/protocol/openid-connect/token"))
                .willReturn(okJson("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
        wireMock.stubFor(get(urlEqualTo("/api/v1/balance/1"))
                .willReturn(okJson("{\"balance\":5000,\"currency\":\"RUB\"}")));

        String baseUrl = "http://localhost:" + wireMock.port();

        ClientRegistration registration = ClientRegistration.withRegistrationId("payment-service")
                .tokenUri(baseUrl + "/realms/market/protocol/openid-connect/token")
                .clientId("market-app-client")
                .clientSecret("secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();

        InMemoryReactiveClientRegistrationRepository registrationRepository =
                new InMemoryReactiveClientRegistrationRepository(registration);
        InMemoryReactiveOAuth2AuthorizedClientService clientService =
                new InMemoryReactiveOAuth2AuthorizedClientService(registrationRepository);

        var provider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(cc -> cc.accessTokenResponseClient(
                        new WebClientReactiveClientCredentialsTokenResponseClient()))
                .build();

        var manager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                registrationRepository, clientService);
        manager.setAuthorizedClientProvider(provider);

        var oauth2Filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(manager);
        oauth2Filter.setDefaultClientRegistrationId("payment-service");

        WebClient webClient = WebClient.builder().baseUrl(baseUrl).filter(oauth2Filter).build();
        ApiClient apiClient = new ApiClient(webClient);
        apiClient.setBasePath(baseUrl);
        BalanceApi balanceApi = new BalanceApi(apiClient);
        PurchaseService purchaseService = new PurchaseService(balanceApi, null);

        StepVerifier.create(purchaseService.getBalance(1L))
                .assertNext(balance -> assertThat(balance).isEqualByComparingTo("5000"))
                .verifyComplete();

        wireMock.verify(getRequestedFor(urlEqualTo("/api/v1/balance/1"))
                .withHeader("Authorization", equalTo("Bearer test-token")));
    }
}
