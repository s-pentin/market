package org.market.app.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.dto.RegistrationRequest;
import org.market.app.exceptions.PasswordMismatchException;
import org.market.app.exceptions.UsernameAlreadyExistsException;
import org.market.app.models.Role;
import org.market.app.models.User;
import org.market.app.repositories.UserRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    private RegistrationService registrationService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(userRepository, passwordEncoder);
    }

    private RegistrationRequest request(String username, String password, String confirm) {
        RegistrationRequest req = new RegistrationRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setConfirmPassword(confirm);
        return req;
    }

    @Test
    void register_success_savesCustomerWithEncodedPassword() {
        when(userRepository.existsByUsername("newuser")).thenReturn(Mono.just(false));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return Mono.just(new User(1L, u.getUsername(), u.getPasswordHash(), u.getRole(), u.isEnabled()));
        });

        StepVerifier.create(registrationService.register(request("newuser", "password123", "password123")))
                .assertNext(user -> {
                    assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
                    assertThat(user.isEnabled()).isTrue();
                    assertThat(user.getPasswordHash()).isNotEqualTo("password123");
                    assertThat(passwordEncoder.matches("password123", user.getPasswordHash())).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void register_existingUsername_throwsUsernameAlreadyExists() {
        when(userRepository.existsByUsername("existing")).thenReturn(Mono.just(true));

        StepVerifier.create(registrationService.register(request("existing", "password123", "password123")))
                .expectError(UsernameAlreadyExistsException.class)
                .verify();
    }

    @Test
    void register_passwordMismatch_throwsPasswordMismatch() {
        StepVerifier.create(registrationService.register(request("newuser", "password123", "different")))
                .expectError(PasswordMismatchException.class)
                .verify();
    }
}