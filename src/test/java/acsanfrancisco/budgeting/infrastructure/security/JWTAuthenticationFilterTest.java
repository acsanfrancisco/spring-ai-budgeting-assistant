package acsanfrancisco.budgeting.infrastructure.security;

import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
public class JWTAuthenticationFilterTest {

    private static final String TEST_KEY = "filter-test-only-key-0123456789-abcdefghijklmnopqrstuvwxyz-ABCDEFG";

    @Mock
    private UserRepository userRepository;

    private TokenProvider tokenProvider;
    private JWTAuthenticationFilter filter;
    private User user;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        tokenProvider = new TokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "key", TEST_KEY);
        ReflectionTestUtils.setField(tokenProvider, "expiration", 300_000L);
        filter = new JWTAuthenticationFilter(tokenProvider, new CustomUserDetailsService(userRepository));
        user = new User(new UserId(), "First Test User", "first@example.com", "encoded-password");
        user.setRoles(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN));
        request = new MockHttpServletRequest("GET", "/api/v1/transactions");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should authenticate valid signed bearer token using user loaded from repository")
    void shouldAuthenticateWithValidToken() throws Exception {
        CustomUserDetails principal = new CustomUserDetails(user.getId(), user.getEmail(), user.getPassword(), user.getRoles());
        String token = tokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        request.addHeader("Authorization", "Bearer " + token);
        Mockito.when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Assertions.assertNotNull(authentication);
        Assertions.assertTrue(authentication.isAuthenticated());
        Assertions.assertNull(authentication.getCredentials());
        CustomUserDetails authenticatedUser = Assertions.assertInstanceOf(CustomUserDetails.class, authentication.getPrincipal());
        Assertions.assertEquals(user.getId(), authenticatedUser.getId());
        Assertions.assertEquals(user.getEmail(), authenticatedUser.getUsername());
        Assertions.assertEquals(Set.of("ROLE_USER", "ROLE_ADMIN"),
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
        Assertions.assertSame(request, chain.getRequest());
        Assertions.assertSame(response, chain.getResponse());
        Mockito.verify(userRepository).findByEmail(user.getEmail());
        Mockito.verifyNoMoreInteractions(userRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "Basic credentials", "bearer token", "Bearer"})
    @DisplayName("Should continue without authentication when bearer authorization header is absent or not recognized")
    void shouldContinueWithoutRecognizedBearerHeader(String header) throws Exception {
        if (header != null) {
            request.addHeader("Authorization", header);
        }

        filter.doFilter(request, response, chain);

        Assertions.assertNull(SecurityContextHolder.getContext().getAuthentication());
        Assertions.assertSame(request, chain.getRequest());
        Assertions.assertSame(response, chain.getResponse());
        Mockito.verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "not-a-token", "abc.def.ghi"})
    @DisplayName("Should continue without authentication or user lookup when bearer token is empty or malformed")
    void shouldContinueForInvalidBearerToken(String token) throws Exception {
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, response, chain);

        Assertions.assertNull(SecurityContextHolder.getContext().getAuthentication());
        Assertions.assertSame(request, chain.getRequest());
        Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Should continue without authentication or user lookup for expired signed token")
    void shouldContinueForExpiredToken() throws Exception {
        String token = Jwts.builder().claim("username", user.getEmail())
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(TEST_KEY.getBytes(StandardCharsets.UTF_8))).compact();
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, response, chain);

        Assertions.assertNull(SecurityContextHolder.getContext().getAuthentication());
        Assertions.assertSame(request, chain.getRequest());
        Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Should continue without authentication or user lookup for token signed with another key")
    void shouldContinueForTokenSignedWithAnotherKey() throws Exception {
        String token = Jwts.builder().claim("username", user.getEmail())
                .expiration(new Date(System.currentTimeMillis() + 300_000))
                .signWith(Keys.hmacShaKeyFor(
                        "other-filter-test-key-abcdefghijklmnopqrstuvwxyz-0123456789-ABCDEFG".getBytes(StandardCharsets.UTF_8)))
                .compact();
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, response, chain);

        Assertions.assertNull(SecurityContextHolder.getContext().getAuthentication());
        Assertions.assertSame(request, chain.getRequest());
        Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Should expose authenticated principal to downstream filter chain")
    void shouldPopulateContextBeforeContinuingChain() throws Exception {
        CustomUserDetails principal = new CustomUserDetails(user.getId(), user.getEmail(), user.getPassword(), user.getRoles());
        String token = tokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        request.addHeader("Authorization", "Bearer " + token);
        Mockito.when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Assertions.assertNotNull(authentication);
            Assertions.assertEquals(user.getEmail(), authentication.getName());
        });

        Mockito.verify(userRepository).findByEmail(user.getEmail());
    }

    @Test
    @DisplayName("Should propagate current user-not-found exception without continuing chain")
    void shouldPropagateUserNotFoundForValidToken() {
        CustomUserDetails principal = new CustomUserDetails(user.getId(), user.getEmail(), user.getPassword(), user.getRoles());
        String token = tokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        request.addHeader("Authorization", "Bearer " + token);
        Mockito.when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.empty());

        UsernameNotFoundException thrown = Assertions.assertThrows(UsernameNotFoundException.class,
                () -> filter.doFilter(request, response, chain));

        Assertions.assertEquals("Email not found", thrown.getMessage());
        Assertions.assertNull(SecurityContextHolder.getContext().getAuthentication());
        Assertions.assertNull(chain.getRequest());
        Mockito.verify(userRepository).findByEmail(user.getEmail());
    }
}
