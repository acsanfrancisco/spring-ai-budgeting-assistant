package acsanfrancisco.budgeting.domain.transaction;

import lombok.Getter;

@Getter
public enum Category {

    GROCERIES(TransactionType.EXPENSE),
    PHARMA(TransactionType.EXPENSE),
    AUTO(TransactionType.EXPENSE),
    HEALTH(TransactionType.EXPENSE),
    TRANSPORT(TransactionType.EXPENSE),
    HOUSING(TransactionType.EXPENSE),
    ENTERTAINMENT(TransactionType.EXPENSE),
    PERSONAL_CARE(TransactionType.EXPENSE),
    EDUCATION(TransactionType.EXPENSE),
    OTHER_EXPENSES(TransactionType.EXPENSE),

    SALARY(TransactionType.INCOME),
    FREELANCE(TransactionType.INCOME),
    INVESTMENT(TransactionType.INCOME),
    OTHER_INCOMES(TransactionType.INCOME);

    private final TransactionType type;

    Category(TransactionType type) {
        this.type = type;
    }

}
