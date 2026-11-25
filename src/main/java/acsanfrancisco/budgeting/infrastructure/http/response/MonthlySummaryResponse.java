package acsanfrancisco.budgeting.infrastructure.http.response;

import acsanfrancisco.budgeting.application.output.MonthlySummaryOutput;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthlySummaryResponse(@JsonFormat(pattern = "MM/yyyy") YearMonth period, BigDecimal expenses, BigDecimal income, BigDecimal net) {
    public static MonthlySummaryResponse from(YearMonth period, MonthlySummaryOutput summaryOutput) {
        return new MonthlySummaryResponse(
                period,
                summaryOutput.expenses(),
                summaryOutput.income(),
                summaryOutput.net());
    }
}
