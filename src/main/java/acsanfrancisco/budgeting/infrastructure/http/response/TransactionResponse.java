package acsanfrancisco.budgeting.infrastructure.http.response;

import acsanfrancisco.budgeting.application.output.TransactionOutput;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(UUID id, String description, BigDecimal amount, String category,  @JsonFormat(pattern = "dd/MM/yyyy") LocalDate date, String type) {
    public static TransactionResponse from(TransactionOutput transaction) {
        return new TransactionResponse(
                transaction.id(),
                transaction.description(),
                transaction.amount(),
                transaction.category(),
                transaction.date(),
                transaction.type());
    }
}
