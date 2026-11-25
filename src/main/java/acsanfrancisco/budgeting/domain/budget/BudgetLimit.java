package acsanfrancisco.budgeting.domain.budget;

import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.TransactionType;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.MonetaryAmount;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.YearMonth;

@Getter
public class BudgetLimit {

    private final BudgetLimitId id;

    private final User user;

    private final Category category;

    private final YearMonth period;

    private BigDecimal limitAmount;

    public BudgetLimit(User user, Category category, YearMonth period, BigDecimal limitAmount){
        this(new BudgetLimitId(), user, category, period, limitAmount);
    }

    public BudgetLimit(BudgetLimitId id, User user, Category category, YearMonth period, BigDecimal limitAmount){
        if(id == null){
            throw new IllegalArgumentException("Id must be informed");
        }
        if(user == null){
            throw new IllegalArgumentException("User must be informed");
        }
        if(category == null){
            throw new IllegalArgumentException("Category must be informed");
        }
        if(category.getType() != TransactionType.EXPENSE){
            throw new IllegalArgumentException("Category type must be EXPENSE");
        }
        if(period == null){
            throw new IllegalArgumentException("Period must be informed");
        }

        this.id = id;
        this.user = user;
        this.category = category;
        this.limitAmount = MonetaryAmount.normalize(limitAmount);
        this.period = period;
    }

    public void setLimitAmount(BigDecimal limitAmount){
        this.limitAmount = MonetaryAmount.normalize(limitAmount);
    }

}
