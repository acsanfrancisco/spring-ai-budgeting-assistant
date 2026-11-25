package acsanfrancisco.budgeting.application.usecase.budget;

import acsanfrancisco.budgeting.application.exception.ResourceAlreadyExistsException;
import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.input.CreateBudgetLimitInput;
import acsanfrancisco.budgeting.application.output.BudgetLimitOutput;
import acsanfrancisco.budgeting.domain.budget.BudgetLimit;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitRepository;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistBudgetLimitUseCase {

    private final BudgetLimitRepository budgetLimitRepository;
    private final UserRepository userRepository;

    public PersistBudgetLimitUseCase(BudgetLimitRepository budgetLimitRepository,  UserRepository userRepository) {
        this.budgetLimitRepository = budgetLimitRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BudgetLimitOutput execute(CreateBudgetLimitInput input, UserId userId) {
        if(input == null){
            throw new IllegalArgumentException("BudgetLimit data is mandatory.");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        BudgetLimit budgetLimit = new BudgetLimit(user, input.category(), input.period(), input.limitAmount());
        if (budgetLimitRepository.existsByUserIdAndCategoryAndPeriod(userId, input.category(), input.period())) {
            throw new ResourceAlreadyExistsException("Budget limit already exists for this category and period");
        }

        return BudgetLimitOutput.from(budgetLimitRepository.save(budgetLimit));
    }
}
