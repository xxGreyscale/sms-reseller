package com.opendesk.identity.config;

import com.opendesk.identity.user.IdentityUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stateless JWT security configuration for the identity-service.
 *
 * <p>Security decisions:
 * <ul>
 *   <li>CSRF: disabled — stateless API, no browser sessions (CLAUDE.md)</li>
 *   <li>Session management: STATELESS — JWT is the session (T-02-AC mitigated)</li>
 *   <li>Permit-all paths: admin login, user auth paths, actuator health (IDEN-01,04,07, ADMN-01)</li>
 *   <li>/api/v1/admin/** requires ROLE_ADMIN (T-05-05 mitigated)</li>
 *   <li>All other requests require authentication (anyRequest().authenticated())</li>
 *   <li>oauth2ResourceServer.jwt — reads roles claim via jwtAuthenticationConverter</li>
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
                                "/api/v1/auth/admin/login",  // Admin login — permitAll (ADMN-01)
                                "/auth/register",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/forgot",
                                "/auth/reset",
                                "/actuator/health/**",
                                "/error"
                        ).permitAll()
                        // Admin-only surface — ROLE_ADMIN required (T-05-05)
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Reads the {@code roles} claim from JWTs and maps each value to a {@link SimpleGrantedAuthority}.
     *
     * <p>This enables {@code hasRole("ADMIN")} to match tokens carrying {@code "ROLE_ADMIN"} in
     * the roles claim — Spring Security's hasRole() prepends "ROLE_" automatically, so storing
     * "ROLE_ADMIN" in the claim and converting it as-is gives the correct authority string.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object rolesClaim = jwt.getClaim("roles");
            if (rolesClaim instanceof Collection<?> roles) {
                return roles.stream()
                        .map(Object::toString)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
            }
            return List.of();
        });
        return converter;
    }

    /**
     * Delegating password encoder with BCrypt as the default (D-12).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Authentication manager backed by {@link IdentityUserDetailsService} + BCrypt encoder.
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
