package acsanfrancisco.budgeting.infrastructure.http.documentation;

import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.infrastructure.http.exception.ErrorMessage;
import acsanfrancisco.budgeting.infrastructure.http.request.TransactionRequest;
import acsanfrancisco.budgeting.infrastructure.http.request.UpdateTransactionRequest;
import acsanfrancisco.budgeting.infrastructure.http.response.MonthlyExpensesResponse;
import acsanfrancisco.budgeting.infrastructure.http.response.MonthlySummaryResponse;
import acsanfrancisco.budgeting.infrastructure.http.response.PageResponse;
import acsanfrancisco.budgeting.infrastructure.http.response.TransactionResponse;
import acsanfrancisco.budgeting.infrastructure.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.YearMonth;
import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Transactions", description = "Manage transactions and financial reports for the authenticated user. "
        + "All endpoints require an Authorization: Bearer <token> header.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Authentication is missing or invalid", content = @Content)
public interface TransactionControllerDoc {

    @Operation(summary = "Create a transaction", description = """
            Register an income or expense for the authenticated user.
            Description must not be blank, amount must be positive, and category is required.
            Category determines the transaction type; both income and expense amounts are positive.
            Date is optional and defaults to the application's current date. Dates use dd/MM/yyyy.
            """, responses = {
            @ApiResponse(responseCode = "201", description = "Transaction created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body or transaction data",
                    content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "422", description = "Request field validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    ResponseEntity<TransactionResponse> createTransaction(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransactionRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "description" : "Weekly groceries",
                                        "amount" : 120.50,
                                        "category" : "GROCERIES",
                                        "date" : "07/11/2026"
                                    }
                                    """)))
            @RequestBody @Valid TransactionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(summary = "List transactions by category and optional dates", description = """
            Return one page of the authenticated user's transactions matching the required category. Optional from and to dates (dd/MM/yyyy) are independent and inclusive.
            Page numbering starts at zero. Default page is 0 and default size is 20.
            Results are ordered by transaction date descending, then by ID descending.
            Page must be non-negative and size must be between 1 and 100.
            The response contains content, page, size, and totalElements across all matching pages.
            Size is the requested capacity, not the number of returned items.
            No matches or a page beyond the result returns 200 with an empty content array.
            """, responses = {
            @ApiResponse(responseCode = "200", description = "Transaction page retrieved successfully",
                    useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                                "content":
                                    [
                                        {
                                            "id" : "34f5ae89-54eb-4ef7-9108-0fdc844898b4",
                                            "description" : "Weekly groceries",
                                            "amount" : 120.50,
                                            "category" : "GROCERIES",
                                            "date" : "07/11/2026"
                                            ,"type" : "EXPENSE"
                                        }
                                    ],
                                "page" : 0,
                                "size" : 20,
                                "totalElements" : 1
                            }
                            """))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid filters or pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    ResponseEntity<PageResponse<TransactionResponse>> findTransactionsByCategoryAndPeriodUseCase(
            @Parameter(description = "Category to filter", required = true, example = "GROCERIES")
            @RequestParam("category") Category category,
            @Parameter(description = "Start date, inclusive, in dd/MM/yyyy format", example = "01/11/2026",
                    schema = @Schema(type = "string"))
            @RequestParam(value = "from", required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate from,
            @Parameter(description = "End date, inclusive, in dd/MM/yyyy format", example = "30/11/2026",
                    schema = @Schema(type = "string"))
            @RequestParam(value = "to", required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate to,
            @Parameter(description = "Zero-based page index", example = "0", schema = @Schema(minimum = "0", defaultValue = "0"))
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Items per page", example = "20",
                    schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(summary = "Get monthly expenses by category", description = """
            Return the total spent in one expense category during the requested month.
            Income categories are not allowed. A month without matching expenses returns total zero.
            This is an aggregate over the month, not a paginated list of transactions.
            """, responses = {
            @ApiResponse(responseCode = "200", description = "Monthly expense total retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MonthlyExpensesResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid month or expense category",
                    content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    ResponseEntity<MonthlyExpensesResponse> findMonthlyExpensesByCategory(
            @Parameter(description = "Month in MM/yyyy format", required = true, example = "11/2026",
                    schema = @Schema(type = "string"))
            @RequestParam("yearMonth") @DateTimeFormat(pattern = "MM/yyyy") YearMonth yearMonth,
            @Parameter(description = "Expense category", required = true, example = "GROCERIES",
                    schema = @Schema(allowableValues = {"GROCERIES", "PHARMA", "AUTO", "HEALTH", "TRANSPORT", "HOUSING", "ENTERTAINMENT", "PERSONAL_CARE", "EDUCATION", "OTHER_EXPENSES"}))
            @RequestParam("category") Category category,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(summary = "Get monthly financial summary", description = """
            Return the month's total expenses, total income, and net amount for the authenticated user.
            Net is income minus expenses and may be negative; it is not an accumulated account balance.
            A month without transactions returns zero totals.
            """, responses = {
            @ApiResponse(responseCode = "200", description = "Monthly summary retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MonthlySummaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid month",
                    content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    ResponseEntity<MonthlySummaryResponse> getMonthlySummary(
            @Parameter(description = "Month in MM/yyyy format", required = true, example = "11/2026",
                    schema = @Schema(type = "string"))
            @RequestParam("yearMonth") @DateTimeFormat(pattern = "MM/yyyy") YearMonth yearMonth,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(summary = "Update a transaction", description = """
            Partially update a transaction owned by the authenticated user.
            At least one field must have a non-null value, an empty object or all-null fields return 400.
            Omitted or null fields retain their current values when another field is supplied.
            A supplied description must not be blank and a supplied amount must be positive.
            Category may change only within the original transaction type: expense to expense or income to income.
            Date uses dd/MM/yyyy. Transaction ID and owner remain unchanged.
            """, responses = {
            @ApiResponse(responseCode = "200", description = "Transaction updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ID or body, no non-null update fields, blank description, or category changes the transaction type",
                    content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "User or transaction not found for the authenticated user",
                    content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "422", description = "Request field validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    ResponseEntity<TransactionResponse> updateTransaction(
            @Parameter(description = "Transaction ID", required = true, example = "34f5ae89-54eb-4ef7-9108-0fdc844898b4")
            @PathVariable("transactionId") UUID transactionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateTransactionRequest.class),
                            examples = @ExampleObject(value = """
                                    {"description":"Corrected groceries purchase","amount":150.75}
                                    """)))
            @RequestBody @Valid UpdateTransactionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(summary = "Delete a transaction", description = """
            Delete a transaction owned by the authenticated user. No request body is required.
            Deleting an unknown or already deleted transaction returns 404.
            """, responses = {
            @ApiResponse(responseCode = "204", description = "Transaction deleted successfully", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid transaction ID format",
                    content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "Transaction not found for the authenticated user",
                    content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    ResponseEntity<Void> deleteTransaction(
            @Parameter(description = "Transaction ID", required = true, example = "34f5ae89-54eb-4ef7-9108-0fdc844898b4")
            @PathVariable("transactionId") UUID transactionId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

}
