package acsanfrancisco.budgeting.infrastructure.http.exception;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Should log unexpected errors with request and stack trace while returning a generic message")
    void shouldLogUnexpectedErrorWithStackTrace(CapturedOutput output) {
        var request = new MockHttpServletRequest("POST", "/api/v1/assistant/voice");

        var response = handler.runTimeException(new IllegalStateException("database connection lost"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("Failed to process request");
        assertThat(output)
                .contains("ERROR")
                .contains("Unexpected error on POST /api/v1/assistant/voice")
                .contains("java.lang.IllegalStateException: database connection lost")
                .contains("at acsanfrancisco.budgeting.infrastructure.http.exception.GlobalExceptionHandlerTest");
    }

    @Test
    @DisplayName("Should return 413 for oversized uploads without logging an error")
    void shouldReturnContentTooLargeForOversizedUpload(CapturedOutput output) {
        var request = new MockHttpServletRequest("POST", "/api/v1/assistant/voice");

        var response = handler.uploadTooLarge(new MaxUploadSizeExceededException(25L * 1024 * 1024), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody().getMessage()).isEqualTo("Uploaded file exceeds the 25 MB limit");
        assertThat(output).doesNotContain("ERROR");
    }

    @Test
    @DisplayName("Should not log client errors as errors")
    void shouldNotLogClientErrors(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/api/v1/budgets/unknown");

        var response = handler.resourceNotFoundException(new ResourceNotFoundException("Budget limit not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(output).doesNotContain("ERROR").doesNotContain("Budget limit not found");
    }
}
