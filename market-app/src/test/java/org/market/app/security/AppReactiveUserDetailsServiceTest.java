package org.market.app.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.models.Role;
import org.market.app.models.User;
import org.market.app.repositories.UserRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppReactiveUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppReactiveUserDetailsService service;

    @Test
    void findByUsername_existingUser_returnsAppUserDetailsWithIdAndRole() {
        User user = new User(1L, "customer1", "hash", Role.CUSTOMER, true);
        when(userRepository.findByUsername("customer1")).thenReturn(Mono.just(user));

        StepVerifier.create(service.findByUsername("customer1"))
                .assertNext(details -> {
                    assertThat(details).isInstanceOf(AppUserDetails.class);
                    AppUserDetails appDetails = (AppUserDetails) details;
                    assertThat(appDetails.getId()).isEqualTo(1L);
                    assertThat(appDetails.getUsername()).isEqualTo("customer1");
                    assertThat(appDetails.getAuthorities())
                            .extracting(a -> a.getAuthority())
                            .containsExactly("ROLE_CUSTOMER");
                })
                .verifyComplete();
    }

    @Test
    void findByUsername_notFound_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Mono.empty());

        StepVerifier.create(service.findByUsername("ghost"))
                .expectError(UsernameNotFoundException.class)
                .verify();
    }
}