package acsanfrancisco.budgeting.application.output;

import acsanfrancisco.budgeting.domain.transaction.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionOutput(UUID id, String description, BigDecimal amount, String category, LocalDate date, String type) {

    public static TransactionOutput from(Transaction transaction) {
        return new TransactionOutput(
                transaction.getId().id(),
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getCategory().toString(),
                transaction.getDate(),
                transaction.getType().toString());
    }
}
