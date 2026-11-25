package acsanfrancisco.budgeting.infrastructure.http.controller;

import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.security.CustomUserDetails;
import acsanfrancisco.budgeting.infrastructure.security.TokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.mockito.Mockito;
import java.util.Set;
import java.util.UUID;

@SpringBootTest(properties = {
        "JWT_KEY=authorization-integration-test-key-0123456789-abcdefghijklmnopqrstuvwxyz",
        "JWT_EXPIRATION=300000",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Sql("/sql/seeding-budget-limits.sql")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(AuthorizationIntegrationTestSupport.AiConfiguration.class)
abstract class AuthorizationIntegrationTestSupport {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    static final String USER_A = "00000000-0000-0000-0000-000000000001";
    static final String USER_B = "00000000-0000-0000-0000-000000000002";
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired TokenProvider tokenProvider;
    @MockitoBean TranscriptionModel transcriptionModel;
    @MockitoBean TextToSpeechModel textToSpeechModel;
    @MockitoBean ChatClient chatClient;
    String tokenA;
    String tokenB;

    @BeforeEach
    void authenticateUsers() {
        jdbc.update("INSERT INTO tb_user_role (user_id, role) VALUES (?, 'ROLE_USER'), (?, 'ROLE_USER')",
                UUID.fromString(USER_A), UUID.fromString(USER_B));
        tokenA = token(USER_A, "first@example.com");
        tokenB = token(USER_B, "second@example.com");
    }

    private String token(String id, String email) {
        var principal = new CustomUserDetails(new UserId(UUID.fromString(id)), email,
                "encoded-password", Set.of(Role.ROLE_USER));
        return "Bearer " + tokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AiConfiguration {
        @Bean ChatClient.Builder chatClientBuilder(ChatClient chatClient) {
            var builder = Mockito.mock(ChatClient.Builder.class, Mockito.RETURNS_SELF);
            Mockito.when(builder.build()).thenReturn(chatClient);
            return builder;
        }
    }
}

