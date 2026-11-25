package acsanfrancisco.budgeting.infrastructure.http.controller;

import acsanfrancisco.budgeting.application.output.MonthlyExpensesOutput;
import acsanfrancisco.budgeting.application.output.MonthlySummaryOutput;
import acsanfrancisco.budgeting.application.output.TransactionOutput;
import acsanfrancisco.budgeting.application.usecase.transaction.*;
import acsanfrancisco.budgeting.domain.pagination.PageQuery;
import acsanfrancisco.budgeting.domain.pagination.PageResult;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.TransactionId;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.http.documentation.TransactionControllerDoc;
import acsanfrancisco.budgeting.infrastructure.http.request.TransactionRequest;
import acsanfrancisco.budgeting.infrastructure.http.request.UpdateTransactionRequest;
import acsanfrancisco.budgeting.infrastructure.http.response.MonthlyExpensesResponse;
import acsanfrancisco.budgeting.infrastructure.http.response.MonthlySummaryResponse;
import acsanfrancisco.budgeting.infrastructure.http.response.PageResponse;
import acsanfrancisco.budgeting.infrastructure.http.response.TransactionResponse;
import acsanfrancisco.budgeting.infrastructure.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.YearMonth;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController implements TransactionControllerDoc {

    private final PersistTransactionUseCase persistTransactionUseCase;
    private final FindTransactionsByCategoryAndPeriodUseCase findAllTransactionsByCategoryUseCase;
    private final FindMonthlyExpensesByCategoryUseCase  findMonthlyExpensesByCategoryUseCase;
    private final GetMonthlySummaryUseCase getMonthlySummary;
    private final UpdateTransactionUseCase updateTransactionUseCase;
    private final DeleteTransactionUseCase deleteTransactionUseCase;

    public TransactionController(PersistTransactionUseCase persistTransactionUseCase,
                                 FindTransactionsByCategoryAndPeriodUseCase findAllTransactionsByCategoryUseCase,
                                 FindMonthlyExpensesByCategoryUseCase  findMonthlyExpensesByCategoryUseCase,
                                 GetMonthlySummaryUseCase getMonthlySummary,
                                 UpdateTransactionUseCase updateTransactionUseCase,
                                 DeleteTransactionUseCase deleteTransactionUseCase) {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.findAllTransactionsByCategoryUseCase = findAllTransactionsByCategoryUseCase;
        this.findMonthlyExpensesByCategoryUseCase = findMonthlyExpensesByCategoryUseCase;
        this.updateTransactionUseCase = updateTransactionUseCase;
        this.deleteTransactionUseCase = deleteTransactionUseCase;
        this.getMonthlySummary = getMonthlySummary;

    }

    @Override
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TransactionResponse> createTransaction(
            @RequestBody @Valid TransactionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserId userId = userDetails.getId();
        TransactionOutput transaction = persistTransactionUseCase.execute(request.toInput(), userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(TransactionResponse.from(transaction));
    }

    @Override
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<TransactionResponse>> findTransactionsByCategoryAndPeriodUseCase(
            @RequestParam("category") Category category,
            @RequestParam(value = "from", required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserId userId = userDetails.getId();
        PageResult<TransactionOutput> result = findAllTransactionsByCategoryUseCase.execute(category, userId, from, to, new PageQuery(page, size));
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(PageResponse.from(result, TransactionResponse::from));
    }

    @Override
    @GetMapping("/monthly/expenses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MonthlyExpensesResponse> findMonthlyExpensesByCategory(
            @RequestParam("yearMonth") @DateTimeFormat(pattern = "MM/yyyy") YearMonth yearMonth,
            @RequestParam("category") Category category,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserId userId = userDetails.getId();
        MonthlyExpensesOutput expensesOutput = findMonthlyExpensesByCategoryUseCase.execute(yearMonth, category, userId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(MonthlyExpensesResponse.from(expensesOutput));

    }

    @Override
    @GetMapping("/monthly/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MonthlySummaryResponse> getMonthlySummary(
            @RequestParam("yearMonth") @DateTimeFormat(pattern = "MM/yyyy") YearMonth yearMonth,
            @AuthenticationPrincipal CustomUserDetails userDetails){
        UserId userId = userDetails.getId();
        MonthlySummaryOutput monthlySummaryOutput = getMonthlySummary.execute(yearMonth, userId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(MonthlySummaryResponse.from(yearMonth, monthlySummaryOutput));
    }

    @Override
    @PatchMapping("/{transactionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable("transactionId") UUID transactionId,
            @RequestBody @Valid UpdateTransactionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails){
        UserId userId = userDetails.getId();
        TransactionOutput transaction = updateTransactionUseCase.execute(request.toInput(), new TransactionId(transactionId), userId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(TransactionResponse.from(transaction));
    }

    @Override
    @DeleteMapping("/{transactionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable("transactionId") UUID transactionId,
            @AuthenticationPrincipal CustomUserDetails userDetails){
        UserId userId = userDetails.getId();
        deleteTransactionUseCase.execute(new TransactionId(transactionId), userId);
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

}
