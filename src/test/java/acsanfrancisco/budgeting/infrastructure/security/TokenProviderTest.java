package acsanfrancisco.budgeting.infrastructure.security;

import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.domain.user.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Set;

public class TokenProviderTest {

    private static final String TEST_KEY = "test-only-signing-key-0123456789-abcdefghijklmnopqrstuvwxyz-ABCDEFG";
    private static final long EXPIRATION_MILLIS = 300_000L;

    private TokenProvider tokenProvider;
    private SecretKey signingKey;
    private CustomUserDetails principal;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        tokenProvider = new TokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "key", TEST_KEY);
        ReflectionTestUtils.setField(tokenProvider, "expiration", EXPIRATION_MILLIS);
        signingKey = Keys.hmacShaKeyFor(TEST_KEY.getBytes(StandardCharsets.UTF_8));
        principal = new CustomUserDetails(new UserId(), "first@example.com", "encoded-password", Set.of(Role.ROLE_USER));
        authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("Should generate signed JWT containing current subject representation and username without password")
    void shouldGenerateSignedTokenWithUserClaims() {
        String token = tokenProvider.generateToken(authentication);

        Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        Assertions.assertEquals(principal.getId().toString(), claims.getSubject());
        Assertions.assertEquals(principal.getUsername(), claims.get("username", String.class));
        Assertions.assertFalse(claims.containsKey("password"));
        Assertions.assertEquals(3, token.split("\\.").length);
    }

    @Test
    @DisplayName("Should set issued-at time and expiration using configured duration")
    void shouldSetConfiguredExpiration() {
        long before = System.currentTimeMillis();
        String token = tokenProvider.generateToken(authentication);
        long after = System.currentTimeMillis();

        Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        Assertions.assertTrue(claims.getIssuedAt().getTime() >= before / 1000 * 1000);
        Assertions.assertTrue(claims.getIssuedAt().getTime() <= after / 1000 * 1000);
        Assertions.assertEquals(EXPIRATION_MILLIS,
                claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
    }

    @Test
    @DisplayName("Should accept token generated with configured signing key")
    void shouldAcceptGeneratedToken() {
        Assertions.assertTrue(tokenProvider.isTokenValid(tokenProvider.generateToken(authentication)));
    }

    @Test
    @DisplayName("Should extract username from valid signed token")
    void shouldExtractUsername() {
        String token = tokenProvider.generateToken(authentication);

        Assertions.assertEquals(principal.getUsername(), tokenProvider.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("Should reject expired token without waiting for time to pass")
    void shouldRejectExpiredToken() {
        String token = Jwts.builder().subject(principal.getId().toString())
                .claim("username", principal.getUsername())
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(signingKey).compact();

        Assertions.assertFalse(tokenProvider.isTokenValid(token));
        Assertions.assertThrows(ExpiredJwtException.class, () -> tokenProvider.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("Should reject token signed with another key")
    void shouldRejectTokenSignedWithAnotherKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-test-only-key-abcdefghijklmnopqrstuvwxyz-0123456789-ABCDEFG".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().claim("username", principal.getUsername())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MILLIS))
                .signWith(otherKey).compact();

        Assertions.assertFalse(tokenProvider.isTokenValid(token));
        Assertions.assertThrows(SignatureException.class, () -> tokenProvider.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("Should reject token whose username payload was modified without resigning")
    void shouldRejectTamperedPayload() {
        String token = tokenProvider.generateToken(authentication);
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String changedPayload = payload.replace(principal.getUsername(), "attacker@example.com");
        Assertions.assertNotEquals(payload, changedPayload);
        String tamperedToken = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(changedPayload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        Assertions.assertFalse(tokenProvider.isTokenValid(tamperedToken));
        Assertions.assertThrows(SignatureException.class, () -> tokenProvider.getUsernameFromToken(tamperedToken));
    }

    @Test
    @DisplayName("Should reject unsecured token without a signature")
    void shouldRejectUnsignedToken() {
        String token = Jwts.builder().claim("username", principal.getUsername())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MILLIS)).compact();

        Assertions.assertFalse(tokenProvider.isTokenValid(token));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "not-a-token", "abc.def.ghi"})
    @DisplayName("Should return false for null, blank or malformed token")
    void shouldRejectMalformedToken(String token) {
        Assertions.assertFalse(tokenProvider.isTokenValid(token));
    }
}
