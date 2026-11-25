package acsanfrancisco.budgeting.application.output;

import acsanfrancisco.budgeting.domain.report.MonthlySummary;

import java.math.BigDecimal;

public record MonthlySummaryOutput(BigDecimal expenses, BigDecimal income, BigDecimal net) {
    public static MonthlySummaryOutput from(MonthlySummary monthlySummary) {
        return new MonthlySummaryOutput(
                monthlySummary.expenses(),
                monthlySummary.income(),
                monthlySummary.net());
    }
}
