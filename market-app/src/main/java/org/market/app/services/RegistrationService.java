package org.market.app.services;

import org.market.app.dto.RegistrationRequest;
import org.market.app.exceptions.PasswordMismatchException;
import org.market.app.exceptions.UsernameAlreadyExistsException;
import org.market.app.models.Role;
import org.market.app.models.User;
import org.market.app.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Mono<User> register(RegistrationRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            return Mono.error(new PasswordMismatchException("Пароли не совпадают"));
        }

        return userRepository.existsByUsername(request.getUsername())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new UsernameAlreadyExistsException(
                                "Пользователь с логином '" + request.getUsername() + "' уже существует")
                        );
                    }
                    User newUser = new User(null, request.getUsername(),
                            passwordEncoder.encode(request.getPassword()), Role.CUSTOMER, true);
                    return userRepository.save(newUser);
                });
    }
}