package acsanfrancisco.budgeting.infrastructure.http.documentation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Enter the JWT returned by login, without the Bearer prefix.")))
                .info(new Info()
                        .title("Budgeting API")
                        .description("""
                                Budgeting is a REST API for personal finance management.
                                Users can register income and expenses, browse transactions with pagination and category
                                and period filters, update and delete transactions, and consult monthly financial summaries.
                                The API also supports monthly spending limits by category, including creation, limit updates,
                                deletion, and budget status queries.
                                Voice input allows users to register transactions and query financial information through
                                audio transcription and AI-assisted tool calling.
                                Authenticated users can configure chat providers (OpenAI, DeepSeek, Gemini, or Anthropic)
                                and transcription (OpenAI).
                                AI configuration must be saved before using voice requests; API keys are never returned.
                                Protected endpoints require authentication using a Bearer JWT.
                                User registration and login are public.
                                """)
                        .version("v1")
                        .contact(new Contact()
                                .name("João Francisco Silva Mendes Santiago")
                                .email("joaosannetof@gmail.com")
                                .url("https://github.com/acsanfrancisco")));
    }

}
