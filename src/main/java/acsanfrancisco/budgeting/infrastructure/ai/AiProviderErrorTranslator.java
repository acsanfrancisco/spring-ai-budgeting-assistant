package acsanfrancisco.budgeting.infrastructure.ai;

import acsanfrancisco.budgeting.application.exception.AiProviderException;
import acsanfrancisco.budgeting.application.exception.AiProviderException.Reason;
import com.anthropic.errors.AnthropicServiceException;
import com.google.genai.errors.ApiException;
import com.openai.errors.OpenAIServiceException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public final class AiProviderErrorTranslator {
    private AiProviderErrorTranslator() {}

    /** Log-safe label for a translated failure: the AI_PROVIDER_* code when known, else the exception type. */
    public static String describe(RuntimeException translated) {
        return translated instanceof AiProviderException provider
                ? "AI_PROVIDER_" + provider.getReason().name()
                : translated.getClass().getSimpleName();
    }

    public static RuntimeException translate(RuntimeException failure, String operation) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            Reason reason;
            List<String> retry = List.of();
            if (cause instanceof OpenAIServiceException sdk) {
                reason = classify(sdk.statusCode(), sdk.code().orElse(""), "");
                retry = sdk.headers().values("retry-after");
            } else if (cause instanceof AnthropicServiceException sdk) {
                reason = classify(sdk.statusCode(), "", sdk.getMessage());
                retry = sdk.headers().values("retry-after");
            } else if (cause instanceof ApiException sdk) {
                reason = classify(sdk.code(), sdk.status(), sdk.message());
            } else if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException
                    || (cause instanceof InterruptedIOException && "timeout".equalsIgnoreCase(cause.getMessage()))) {
                reason = Reason.TIMEOUT;
            } else if (cause instanceof SocketException || cause instanceof UnknownHostException) {
                reason = Reason.UNAVAILABLE;
            } else {
                continue;
            }
            if (reason != null) {
                String retryAfter = reason == Reason.RATE_LIMITED || reason == Reason.UNAVAILABLE
                        ? validRetryAfter(retry) : null;
                return new AiProviderException(reason, operation, failure, retryAfter);
            }
        }
        return failure;
    }

    private static Reason classify(int status, String code, String detail) {
        String message = String.valueOf(detail).toLowerCase(Locale.ROOT);
        boolean invalidKey = "API_KEY_INVALID".equalsIgnoreCase(code) || "invalid_api_key".equals(code)
                || message.contains("api_key_invalid") || message.contains("api key not valid")
                || message.contains("api key expired");
        if (status == 401 || ((status == 400 || status == 403) && invalidKey)) {
            return Reason.INVALID_KEY;
        }
        if (status == 403) return Reason.ACCESS_DENIED;
        if ((status == 400 || status == 404) && "model_not_found".equals(code)) return Reason.MODEL_NOT_FOUND;
        if (status == 404 && (message.contains("model:") || (message.contains("models/") && message.contains("not found")))) {
            return Reason.MODEL_NOT_FOUND;
        }
        if (status == 402 && "insufficient_balance".equals(code)) return Reason.QUOTA_EXCEEDED;
        if (status == 429) {
            return "insufficient_quota".equals(code) || "billing_hard_limit_reached".equals(code)
                    ? Reason.QUOTA_EXCEEDED : Reason.RATE_LIMITED;
        }
        if (status == 408 || status == 504) return Reason.TIMEOUT;
        if (status >= 500 && status < 600) return Reason.UNAVAILABLE;
        return null;
    }

    private static String validRetryAfter(List<String> values) {
        if (values.isEmpty()) return null;
        String value = values.getFirst();
        if (value.matches("[0-9]{1,18}")) return value;
        try {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
            return value;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
