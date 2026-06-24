package com.smsreseller.identity.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads a {@link User} by email address for Spring Security's authentication pipeline.
 *
 * <p>Used by the {@code DaoAuthenticationProvider} in {@code SecurityConfig} to validate
 * credentials during login (IDEN-04). The returned {@link UserDetails} carries the stored
 * BCrypt hash so Spring Security can verify the provided plaintext password.
 *
 * <p>Security: the stored hash is returned to Spring Security ONLY — it must never be
 * forwarded to any external caller or included in any API response DTO.
 */
@Service
@RequiredArgsConstructor
public class IdentityUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
