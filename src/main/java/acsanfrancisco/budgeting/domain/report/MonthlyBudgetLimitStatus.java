package acsanfrancisco.budgeting.domain.report;

import acsanfrancisco.budgeting.domain.budget.BudgetStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record MonthlyBudgetLimitStatus(BigDecimal limitAmount, BigDecimal spent) {

    public BigDecimal remaining(){
        return limitAmount == null ? null : limitAmount.subtract(spent);
    }

    public BigDecimal percentage(){
        return limitAmount == null ? null : spent.multiply(BigDecimal.valueOf(100)).divide(limitAmount, 2, RoundingMode.HALF_UP);
    }

    public BudgetStatus status(){
       if(limitAmount == null){
           return BudgetStatus.NOT_CONFIGURED;
       }
       int comparison = spent.compareTo(limitAmount);

       if(comparison > 0){
           return BudgetStatus.EXCEEDED;
       }
       if(comparison == 0){
           return BudgetStatus.AT_LIMIT;
       }
       BigDecimal limit = limitAmount.multiply(new BigDecimal("0.80"));

       if(spent.compareTo(limit) >= 0){
           return BudgetStatus.NEAR_LIMIT;
       }
       return BudgetStatus.WITHIN_LIMIT;
    }
}
