package org.market.app.security;

import org.market.app.repositories.UserRepository;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AppReactiveUserDetailsService implements ReactiveUserDetailsService {

    private final UserRepository userRepository;

    public AppReactiveUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(AppUserDetails::new)
                .switchIfEmpty(Mono.error(
                        new UsernameNotFoundException("User not found: " + username)))
                .cast(UserDetails.class);
    }
}