package acsanfrancisco.budgeting.application.usecase.transaction;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.output.MonthlySummaryOutput;
import acsanfrancisco.budgeting.domain.report.MonthlySummary;
import acsanfrancisco.budgeting.domain.transaction.TransactionRepository;
import acsanfrancisco.budgeting.domain.transaction.TransactionType;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.YearMonth;

@Service
public class GetMonthlySummaryUseCase {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public GetMonthlySummaryUseCase(TransactionRepository transactionRepository, UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public MonthlySummaryOutput execute(YearMonth yearMonth, UserId userId) {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        if(yearMonth == null) {
            throw new IllegalArgumentException("Must inform period");
        }

        MonthlySummary summary = transactionRepository.getMonthlySummary(yearMonth, userId, TransactionType.INCOME, TransactionType.EXPENSE);
        return MonthlySummaryOutput.from(summary);
    }
}
