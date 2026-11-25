package acsanfrancisco.budgeting.domain.budget;

import acsanfrancisco.budgeting.domain.report.MonthlyBudgetLimitStatus;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.user.UserId;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

public interface BudgetLimitRepository {

    BudgetLimit save(BudgetLimit budgetLimit);

    boolean existsByUserIdAndCategoryAndPeriod(UserId userId, Category category, YearMonth period);

    MonthlyBudgetLimitStatus getBudgetLimitStatus(YearMonth period, UserId userId, Category category);

    BudgetLimit updateLimitAmount(BigDecimal limitAmount, BudgetLimitId budgetLimitId, UserId userId);

    Optional<BudgetLimit> findBudgetLimitByIdAndUserId(BudgetLimitId budgetLimitId, UserId userId);

    void delete(BudgetLimitId budgetLimitId, UserId userId);

}
