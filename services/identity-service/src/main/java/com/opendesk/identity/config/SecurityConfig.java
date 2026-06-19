package com.opendesk.identity.config;

import com.opendesk.identity.user.IdentityUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT security configuration for the identity-service.
 *
 * <p>Security decisions:
 * <ul>
 *   <li>CSRF: disabled — stateless API, no browser sessions (CLAUDE.md)</li>
 *   <li>Session management: STATELESS — JWT is the session (T-02-AC mitigated)</li>
 *   <li>Permit-all paths: registration/login/refresh/password-reset/actuator health (IDEN-01,04,07)</li>
 *   <li>All other requests require authentication (anyRequest().authenticated())</li>
 *   <li>oauth2ResourceServer.jwt — identity validates its own issued tokens (defense-in-depth)</li>
 *   <li>Password encoder: DelegatingPasswordEncoder with BCrypt default (D-12; T-02-01 mitigated)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final IdentityUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/register",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/forgot",
                                "/auth/reset",
                                "/actuator/health/**",
                                "/error"            // Spring Boot error endpoint — must be permitAll so
                                                    // errors thrown by permitAll endpoints don't get 401
                        ).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * Delegating password encoder with BCrypt as the default (D-12).
     *
     * <p>Using DelegatingPasswordEncoder instead of raw BCryptPasswordEncoder enables
     * future algorithm upgrades (e.g. to Argon2) without a schema migration — Spring
     * encodes the hash with a {@code {bcrypt}} prefix that this encoder reads at runtime.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Authentication manager backed by {@link IdentityUserDetailsService} + BCrypt encoder.
     *
     * <p>Used by login endpoints (IDEN-04 prep). The DaoAuthenticationProvider wires
     * the UserDetailsService to loadUserByUsername and validates the password with BCrypt.
     *
     * @param authenticationConfiguration Spring's auto-configured authentication configuration
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Explicit DaoAuthenticationProvider ensures the correct UserDetailsService and
     * PasswordEncoder are used by the AuthenticationManager.
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
}
