package acsanfrancisco.budgeting.infrastructure.persistence.entity;

import acsanfrancisco.budgeting.domain.aiconversation.AiConversation;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationId;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitId;
import acsanfrancisco.budgeting.domain.budget.BudgetLimit;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.converter.YearMonthConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

@Entity
@Table(name = "tb_budget_limit",
        uniqueConstraints = @UniqueConstraint(
        name = "uk_budget_limit_user_category_period",
        columnNames = {"user_id", "category", "period"}))
@Getter
@Setter
@NoArgsConstructor
public class BudgetLimitEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Category category;

    @Column(nullable = false)
    @Convert(converter = YearMonthConverter.class)
    private YearMonth period;

    @Column(nullable = false, precision = 19, scale = 2, name = "limit_amount")
    private BigDecimal limitAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    public BudgetLimitEntity(UUID id, Category category, YearMonth period, BigDecimal limitAmount) {
        this.id = id;
        this.category = category;
        this.period = period;
        this.limitAmount = limitAmount;
    }

    public BudgetLimit toDomain(){
        return new BudgetLimit(
                new BudgetLimitId(this.id),
                this.user.toDomain(),
                this.category,
                this.period,
                this.limitAmount);
    }

    public static BudgetLimitEntity from(BudgetLimit budget) {
        return new BudgetLimitEntity(
                budget.getId().id(),
                budget.getCategory(),
                budget.getPeriod(),
                budget.getLimitAmount());
    }
}
