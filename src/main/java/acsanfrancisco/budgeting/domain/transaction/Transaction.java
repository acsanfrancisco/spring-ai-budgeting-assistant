package acsanfrancisco.budgeting.domain.transaction;

import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.MonetaryAmount;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
public class Transaction {

    private final TransactionId id;

    private String description;

    private BigDecimal amount;

    private Category category;

    private LocalDate date;

    private final TransactionType type;

    private final User user;

    public Transaction(String description, BigDecimal amount, Category category, User user, LocalDate date) {
        this(new TransactionId(), description, amount, category,
                date == null ? LocalDate.now() : date, typeFrom(category), user);
    }

    public Transaction(TransactionId id, String description, BigDecimal amount, Category category,
                       LocalDate date, TransactionType type, User user) {
        if(id == null){
            throw new IllegalArgumentException("Id must be informed");
        }
        if(user == null){
            throw new IllegalArgumentException("Must inform user id");
        }
        if(type == null){
            throw new IllegalArgumentException("Must inform transaction type");
        }
        validateDescription(description);
        validateCategory(category, type);
        validateDate(date);

        this.id = id;
        this.description = description;
        this.amount = MonetaryAmount.normalize(amount);
        this.category = category;
        this.user = user;
        this.date = date;
        this.type = type;
    }

    public void setDescription(String description) {
        validateDescription(description);
        this.description = description;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = MonetaryAmount.normalize(amount);
    }

    public void setCategory(Category category) {
        validateCategory(category, this.type);
        this.category = category;
    }

    public void setDate(LocalDate date) {
        validateDate(date);
        this.date = date;
    }

    private static TransactionType typeFrom(Category category) {
        if(category == null){
            throw new IllegalArgumentException("Must inform category");
        }
        return category.getType();
    }


    private static void validateDescription(String description) {
        if(description == null || description.isBlank()){
            throw new IllegalArgumentException("Must inform description");
        }

    }

    private static void validateCategory(Category category, TransactionType type) {
        if(typeFrom(category) != type){
            throw new IllegalArgumentException("Category type must match the transaction type");
        }

    }

    private static void validateDate(LocalDate date) {
        if(date == null){
            throw new IllegalArgumentException("Must inform date");
        }
    }

}
