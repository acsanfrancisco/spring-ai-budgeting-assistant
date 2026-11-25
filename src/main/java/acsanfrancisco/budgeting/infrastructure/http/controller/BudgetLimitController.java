package acsanfrancisco.budgeting.infrastructure.http.controller;

import acsanfrancisco.budgeting.application.output.BudgetLimitOutput;
import acsanfrancisco.budgeting.application.output.MonthlyBudgetLimitStatusOutput;
import acsanfrancisco.budgeting.application.usecase.budget.DeleteBudgetLimitUseCase;
import acsanfrancisco.budgeting.application.usecase.budget.GetBudgetLimitStatusUseCase;
import acsanfrancisco.budgeting.application.usecase.budget.PersistBudgetLimitUseCase;
import acsanfrancisco.budgeting.application.usecase.budget.UpdateBudgetLimitUseCase;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitId;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.http.documentation.BudgetLimitControllerDoc;
import acsanfrancisco.budgeting.infrastructure.http.request.BudgetLimitRequest;
import acsanfrancisco.budgeting.infrastructure.http.response.BudgetLimitResponse;
import acsanfrancisco.budgeting.infrastructure.http.response.BudgetLimitStatusResponse;
import acsanfrancisco.budgeting.infrastructure.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetLimitController implements BudgetLimitControllerDoc {

    private final PersistBudgetLimitUseCase persistBudgetLimitUseCase;
    private final GetBudgetLimitStatusUseCase getBudgetLimitStatusUseCase;
    private final UpdateBudgetLimitUseCase updateBudgetLimitUseCase;
    private final DeleteBudgetLimitUseCase deleteBudgetLimitUseCase;

    public BudgetLimitController(PersistBudgetLimitUseCase persistBudgetLimitUseCase,  GetBudgetLimitStatusUseCase getBudgetLimitStatusUseCase,
                                 UpdateBudgetLimitUseCase updateBudgetLimitUseCase, DeleteBudgetLimitUseCase deleteBudgetLimitUseCase) {
        this.persistBudgetLimitUseCase = persistBudgetLimitUseCase;
        this.getBudgetLimitStatusUseCase = getBudgetLimitStatusUseCase;
        this.updateBudgetLimitUseCase = updateBudgetLimitUseCase;
        this.deleteBudgetLimitUseCase = deleteBudgetLimitUseCase;
    }

    @Override
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BudgetLimitResponse> createBudgetLimit(
            @RequestBody @Valid BudgetLimitRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserId userId = userDetails.getId();
        BudgetLimitOutput budgetOutput = persistBudgetLimitUseCase.execute(request.toInput(), userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BudgetLimitResponse.from(budgetOutput));
    }

    @Override
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BudgetLimitStatusResponse> getBudgetLimitStatus(
            @RequestParam("period") @DateTimeFormat(pattern = "MM/yyyy") YearMonth period,
            @RequestParam("category") Category category,
            @AuthenticationPrincipal CustomUserDetails userDetails){
        UserId userId = userDetails.getId();
        MonthlyBudgetLimitStatusOutput budgetLimitStatusOutput = getBudgetLimitStatusUseCase.execute(period, userId, category);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(BudgetLimitStatusResponse.from(period, budgetLimitStatusOutput));
    }

    @Override
    @PatchMapping("/{budgetLimitId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BudgetLimitResponse> updateBudgetLimitAmount(
            @PathVariable("budgetLimitId") UUID budgetLimitId,
            @RequestParam("limitAmount") BigDecimal limitAmount,
            @AuthenticationPrincipal CustomUserDetails userDetails){
        UserId userId = userDetails.getId();
        BudgetLimitOutput budgetLimitOutput = updateBudgetLimitUseCase.execute(limitAmount, new BudgetLimitId(budgetLimitId), userId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(BudgetLimitResponse.from(budgetLimitOutput));
    }

    @Override
    @DeleteMapping("/{budgetLimitId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteBudgetLimit(
            @PathVariable("budgetLimitId") UUID budgetLimitId,
            @AuthenticationPrincipal CustomUserDetails userDetails){
        UserId userId = userDetails.getId();
        deleteBudgetLimitUseCase.execute(new BudgetLimitId(budgetLimitId), userId);
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

}
