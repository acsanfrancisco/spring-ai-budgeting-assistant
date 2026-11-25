package acsanfrancisco.budgeting.application.input;

import acsanfrancisco.budgeting.domain.transaction.Category;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTransactionInput(String description, BigDecimal amount, Category category, LocalDate date) {
}
