package acsanfrancisco.budgeting.domain.report;

import java.math.BigDecimal;

public record MonthlySummary(BigDecimal expenses, BigDecimal income) {
    public BigDecimal net(){
        return income.subtract(expenses);
    }
}
