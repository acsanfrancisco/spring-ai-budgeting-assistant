package acsanfrancisco.budgeting.domain.budget;

import java.util.UUID;

public record BudgetLimitId(UUID id) {
    public BudgetLimitId {
        if (id == null) {
            throw new IllegalArgumentException("Id must not be null");
        }
    }
    public BudgetLimitId(){
        this(UUID.randomUUID());
    }
}
