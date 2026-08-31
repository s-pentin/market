package org.market.app.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.infra.TestContainers;
import org.market.app.models.Role;
import org.market.app.models.User;
import org.market.app.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
@ImportTestcontainers(TestContainers.class)
class SecurityIntegrationTest {

    private static long counter = 0;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private UserRepository userRepository;

    private String username;

    @BeforeEach
    void setUp() {
        username = "loginuser" + (counter++);
        userRepository.save(new User(null, username,
                new BCryptPasswordEncoder().encode("password123"), Role.CUSTOMER, true)).block();
    }

    @Test
    void loginWithValidCredentials_thenLogout_clearsSession() {
        var loginResponse = webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post().uri("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("username=" + username + "&password=password123")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/items.*")
                .returnResult(Void.class);

        String sessionCookie = loginResponse.getResponseCookies().getFirst("SESSION").getValue();

        webTestClient.get().uri("/cart/items")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().isOk();

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post().uri("/logout")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().is3xxRedirection();

        webTestClient.get().uri("/cart/items")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/login.*");
    }
}
