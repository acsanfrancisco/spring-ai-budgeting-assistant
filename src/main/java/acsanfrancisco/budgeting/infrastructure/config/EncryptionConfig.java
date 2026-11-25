package acsanfrancisco.budgeting.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;

@Configuration
public class EncryptionConfig {

    @Bean
    public BytesEncryptor apiKeyEncryptor(
            @Value("${ai.encryption.password}") String password,
            @Value("${ai.encryption.salt}") String salt) {
        return AesGcmBytesEncryptor.withPassword(password, salt).build();
    }
}
