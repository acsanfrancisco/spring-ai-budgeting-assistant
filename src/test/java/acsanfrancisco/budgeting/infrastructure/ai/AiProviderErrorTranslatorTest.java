package acsanfrancisco.budgeting.infrastructure.ai;

import acsanfrancisco.budgeting.application.exception.AiProviderException;
import acsanfrancisco.budgeting.application.exception.AiProviderException.Reason;
import com.google.genai.errors.ClientException;
import com.openai.core.http.Headers;
import com.openai.errors.RateLimitException;
import com.openai.models.ErrorObject;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class AiProviderErrorTranslatorTest {
    @Test
    void describesKnownFailuresByCodeAndUnknownOnesByType() {
        var known = AiProviderErrorTranslator.translate(new RuntimeException(new ClientException(401, "ERROR", "denied")), "chat");
        assertThat(AiProviderErrorTranslator.describe(known)).isEqualTo("AI_PROVIDER_INVALID_KEY");
        assertThat(AiProviderErrorTranslator.describe(new IllegalStateException("boom"))).isEqualTo("IllegalStateException");
    }

    @ParameterizedTest
    @CsvSource({
            "401, denied, INVALID_KEY", "403, denied, ACCESS_DENIED",
            "400, API key not valid, INVALID_KEY", "404, models/example not found, MODEL_NOT_FOUND",
            "429, too many requests, RATE_LIMITED", "503, unavailable, UNAVAILABLE",
            "504, upstream timeout, TIMEOUT"
    })
    void translatesWrappedSdkFailure(int status, String detail, Reason reason) {
        var failure = new RuntimeException(new ClientException(status, "ERROR", detail));
        var translated = (AiProviderException) AiProviderErrorTranslator.translate(failure, "chat");
        assertThat(translated.getReason()).isEqualTo(reason);
        assertThat(translated.getCause()).isSameAs(failure);
    }

    @Test
    void forwardsRetryAfterForTemporaryLimitAndRecognizesExplicitQuotaCode() {
        var builder = RateLimitException.builder().headers(Headers.builder().put("Retry-After", "30").build());
        var sdk = builder.build();
        var limited = (AiProviderException) AiProviderErrorTranslator.translate(sdk, "transcription");
        assertThat(limited.getReason()).isEqualTo(Reason.RATE_LIMITED);
        assertThat(limited.getRetryAfter()).isEqualTo("30");
        sdk = builder.error(ErrorObject.builder().code("insufficient_quota").param((String) null)
                .type("insufficient_quota").message("quota exhausted").build()).build();
        var quota = (AiProviderException) AiProviderErrorTranslator.translate(sdk, "transcription");
        assertThat(quota.getReason()).isEqualTo(Reason.QUOTA_EXCEEDED);
        assertThat(quota.getRetryAfter()).isNull();
    }

    @Test
    void distinguishesTransportFailuresAndPreservesUnspecifiedNotFound() {
        var timeout = new RuntimeException(new HttpTimeoutException("timeout"));
        var connection = new RuntimeException(new UnknownHostException());
        assertThat(((AiProviderException) AiProviderErrorTranslator.translate(timeout, "transcription")).getReason())
                .isEqualTo(Reason.TIMEOUT);
        assertThat(((AiProviderException) AiProviderErrorTranslator.translate(connection, "chat")).getReason())
                .isEqualTo(Reason.UNAVAILABLE);
        var unknown = new ClientException(404, "NOT_FOUND", "endpoint missing");
        assertThat(AiProviderErrorTranslator.translate(unknown, "chat")).isSameAs(unknown);
    }
}
