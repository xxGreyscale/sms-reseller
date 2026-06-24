package com.smsreseller.identity;

import com.smsreseller.identity.token.JwtIssuer;
import com.smsreseller.identity.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers: IDEN-05 — JWT issuance with RSA private key (NimbusJwtEncoder); verification_status claim (D-02).
 *
 * <p>Unit test for {@link JwtIssuer}. No Spring context — pure unit test using TestKeys.
 *
 * <p>RED phase: tests fail until JwtConfig + JwtIssuer are implemented (same plan 02-02).
 */
class JwtIssuerUnitTest {

    private JwtIssuer jwtIssuer;
    private NimbusJwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        RSAPrivateKey privateKey = TestKeys.loadPrivateKey();
        RSAPublicKey publicKey = TestKeys.loadPublicKey();

        // Build the JwtIssuer directly using the test keys — no Spring context needed
        jwtIssuer = JwtIssuer.withKeys(privateKey, publicKey);
        decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    @Test
    void issuesJwtWithVerificationStatusClaim() throws Exception {
        UUID userId = UUID.randomUUID();

        String token = jwtIssuer.issueAccessToken(userId, VerificationStatus.PENDING_VERIFICATION);

        var decoded = decoder.decode(token);
        assertThat((String) decoded.getClaim("verification_status"))
                .isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void issuesJwtWithVerifiedStatusClaim() throws Exception {
        UUID userId = UUID.randomUUID();

        String token = jwtIssuer.issueAccessToken(userId, VerificationStatus.VERIFIED);

        var decoded = decoder.decode(token);
        assertThat((String) decoded.getClaim("verification_status"))
                .isEqualTo("VERIFIED");
    }

    @Test
    void issuedTokenHasSubjectEqualToUserId() throws Exception {
        UUID userId = UUID.randomUUID();

        String token = jwtIssuer.issueAccessToken(userId, VerificationStatus.PENDING_VERIFICATION);

        var decoded = decoder.decode(token);
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
    }

    @Test
    void issuedTokenExpiresApproximatelyIn15Minutes() throws Exception {
        UUID userId = UUID.randomUUID();

        String token = jwtIssuer.issueAccessToken(userId, VerificationStatus.PENDING_VERIFICATION);

        var decoded = decoder.decode(token);
        Instant now = Instant.now();
        Instant exp = decoded.getExpiresAt();
        assertThat(exp).isNotNull();
        // Should be within 14 to 16 minutes of now
        assertThat(exp).isAfter(now.plusSeconds(13 * 60));
        assertThat(exp).isBefore(now.plusSeconds(17 * 60));
    }

    @Test
    void issuedTokenHasIssuerClaim() throws Exception {
        UUID userId = UUID.randomUUID();

        String token = jwtIssuer.issueAccessToken(userId, VerificationStatus.PENDING_VERIFICATION);

        var decoded = decoder.decode(token);
        assertThat(decoded.getIssuer()).isNotNull();
        assertThat(decoded.getIssuer().toString()).isEqualTo("https://identity.sms-reseller");
    }

    @Test
    void issuedTokenHasRolesClaim() throws Exception {
        UUID userId = UUID.randomUUID();

        String token = jwtIssuer.issueAccessToken(userId, VerificationStatus.PENDING_VERIFICATION);

        var decoded = decoder.decode(token);
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) decoded.getClaim("roles");
        assertThat(roles).isNotNull().contains("ROLE_USER");
    }
}
