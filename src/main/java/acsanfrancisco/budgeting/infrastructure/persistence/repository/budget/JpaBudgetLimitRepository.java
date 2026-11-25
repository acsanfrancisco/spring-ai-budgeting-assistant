package acsanfrancisco.budgeting.infrastructure.persistence.repository.budget;

import acsanfrancisco.budgeting.domain.MonetaryAmount;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.domain.budget.BudgetLimit;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitId;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitRepository;
import acsanfrancisco.budgeting.domain.report.MonthlyBudgetLimitStatus;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.BudgetLimitEntity;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.UserEntity;
import acsanfrancisco.budgeting.infrastructure.persistence.projection.MonthlyBudgetLimitStatusProjection;
import acsanfrancisco.budgeting.infrastructure.persistence.repository.user.UserEntityRepository;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

@Repository
public class JpaBudgetLimitRepository implements BudgetLimitRepository {

    private final BudgetLimitEntityRepository budgetLimitEntityRepository;
    private final UserEntityRepository userEntityRepository;

    public JpaBudgetLimitRepository(BudgetLimitEntityRepository budgetLimitEntityRepository,  UserEntityRepository userEntityRepository) {
        this.budgetLimitEntityRepository = budgetLimitEntityRepository;
        this.userEntityRepository = userEntityRepository;
    }

    @Override
    public BudgetLimit save(BudgetLimit budgetLimit) {
        UserEntity user = userEntityRepository.findById(budgetLimit.getUser().getId().id())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        BudgetLimitEntity entity = BudgetLimitEntity.from(budgetLimit);
        entity.setUser(user);
        return budgetLimitEntityRepository.save(entity).toDomain();
    }

    @Override
    public boolean existsByUserIdAndCategoryAndPeriod(UserId userId, Category category, YearMonth period) {
        return budgetLimitEntityRepository.existsByUserIdAndCategoryAndPeriod(userId.id(), category, period);
    }

    @Override
    public MonthlyBudgetLimitStatus getBudgetLimitStatus(YearMonth period, UserId userId, Category category) {
        userEntityRepository.findById(userId.id())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        LocalDate startDate = period.atDay(1);
        LocalDate endDate = period.plusMonths(1).atDay(1);

        MonthlyBudgetLimitStatusProjection projection = budgetLimitEntityRepository
                .getBudgetLimitStatus(period, startDate, endDate, userId.id(), category);
        if(projection == null){
            return new MonthlyBudgetLimitStatus(null,  BigDecimal.ZERO);
        }
        return new MonthlyBudgetLimitStatus(
                projection.getLimit(),
                projection.getSpent() == null ? BigDecimal.ZERO : projection.getSpent());
    }

    @Override
    public BudgetLimit updateLimitAmount(BigDecimal limitAmount, BudgetLimitId budgetLimitId, UserId userId) {
        BudgetLimitEntity budgetLimitToUpdate = budgetLimitEntityRepository.findBudgetLimitByIdAndUserId(budgetLimitId.id(), userId.id())
                .orElseThrow(() -> new ResourceNotFoundException("Budget limit not found"));
        budgetLimitToUpdate.setLimitAmount(MonetaryAmount.normalize(limitAmount));
        BudgetLimitEntity updatedBudgetLimit = budgetLimitEntityRepository.save(budgetLimitToUpdate);
        return updatedBudgetLimit.toDomain();
    }

    @Override
    public Optional<BudgetLimit> findBudgetLimitByIdAndUserId(BudgetLimitId budgetLimitId, UserId userId) {
        return budgetLimitEntityRepository.findBudgetLimitByIdAndUserId(budgetLimitId.id(), userId.id())
                .map(BudgetLimitEntity::toDomain);
    }

    @Override
    public void delete(BudgetLimitId budgetLimitId, UserId userId) {
       budgetLimitEntityRepository.findBudgetLimitByIdAndUserId(budgetLimitId.id(), userId.id())
                .orElseThrow(() -> new ResourceNotFoundException("Budget limit not found"));
        budgetLimitEntityRepository.deleteById(budgetLimitId.id());
    }
}
