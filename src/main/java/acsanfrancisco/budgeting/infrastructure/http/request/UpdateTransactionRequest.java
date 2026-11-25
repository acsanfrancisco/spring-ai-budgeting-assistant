package acsanfrancisco.budgeting.infrastructure.http.request;

import acsanfrancisco.budgeting.application.input.UpdateTransactionInput;
import acsanfrancisco.budgeting.domain.transaction.Category;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateTransactionRequest(
        String description,
        @Positive(message = "Amount must be positive")
        BigDecimal amount,
        Category category,
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate date) {
    public UpdateTransactionInput toInput() {
        return new UpdateTransactionInput(description, amount, category, date);
    }
}
