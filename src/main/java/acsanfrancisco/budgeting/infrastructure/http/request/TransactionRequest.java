package acsanfrancisco.budgeting.infrastructure.http.request;

import acsanfrancisco.budgeting.application.input.CreateTransactionInput;
import acsanfrancisco.budgeting.domain.transaction.Category;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @NotBlank(message = "Description must be informed")
        String description,
        @NotNull(message = "Amount must be informed")
        @Positive(message = "Amount must be positive")
        BigDecimal amount,
        @NotNull(message = "Must inform the category")
        Category category,
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate date) {

    public CreateTransactionInput toInput() {
        return new CreateTransactionInput(description, amount, category, date);
    }
}
