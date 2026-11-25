package acsanfrancisco.budgeting.infrastructure.http.documentation;

import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.infrastructure.http.exception.ErrorMessage;
import acsanfrancisco.budgeting.infrastructure.http.request.BudgetLimitRequest;
import acsanfrancisco.budgeting.infrastructure.http.response.BudgetLimitResponse;
import acsanfrancisco.budgeting.infrastructure.http.response.BudgetLimitStatusResponse;
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
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

@Tag(
        name = "Budget limits",
        description = """
                Manage monthly spending limits and consult budget status for the authenticated user.
                All endpoints require an Authorization: Bearer <token> header."""
)
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Authentication is missing or invalid", content = @Content)
public interface BudgetLimitControllerDoc {

    @Operation(
            summary = "Create a monthly budget limit",
            description = """
                    Create a spending limit for an expense category and month for the authenticated user.
                    All fields are required and limitAmount must be greater than zero.
                    Only one budget is allowed per user, category, and period.
                    Reaching or exceeding the spending limit does not block transaction registration.
                    The period uses MM/yyyy in both the request and the response.
                    Income categories are not allowed.
                    """,
            responses = {
                    @ApiResponse(responseCode = "201", description = "Budget limit created successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BudgetLimitResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request body or category is not an expense category",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "404", description = "User not found",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "409", description = "Budget limit already exists for this category and period",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "422", description = "Request field validation failed",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            }
    )
    ResponseEntity<BudgetLimitResponse> createBudgetLimit(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Expense category, month, and positive spending limit.", required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BudgetLimitRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "category" : "EDUCATION",
                                        "period" : "11/2026",
                                        "limitAmount" : 300.00
                                    }
                                    """)))
            @RequestBody @Valid BudgetLimitRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(
            summary = "Get monthly budget status",
            description = """
                    Consult the spending limit, expenses, remaining amount, and percentage used for an expense category and month.
                    Status is WITHIN_LIMIT below 80%, NEAR_LIMIT from 80% to below 100%, AT_LIMIT at 100%,
                    and EXCEEDED above 100%. Remaining amount can be negative and percentage can exceed 100.
                    If no budget is configured, return 200 with status NOT_CONFIGURED, null limit,
                    remaining and percentage. The current implementation returns spent as zero in that case.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Budget status retrieved successfully, including when no limit is configured",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BudgetLimitStatusResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Required parameter is missing or invalid, or category is not an expense category",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "404", description = "User not found",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            }
    )
    ResponseEntity<BudgetLimitStatusResponse> getBudgetLimitStatus(
            @Parameter(description = "Month to consult in MM/yyyy format", required = true, example = "11/2026",
                    schema = @Schema(type = "string"))
            @RequestParam("period") @DateTimeFormat(pattern = "MM/yyyy") YearMonth period,
            @Parameter(description = "Expense category to consult", required = true, example = "EDUCATION",
                    schema = @Schema(allowableValues = {"GROCERIES", "PHARMA", "AUTO", "HEALTH", "TRANSPORT", "HOUSING",
                            "ENTERTAINMENT", "PERSONAL_CARE", "EDUCATION", "OTHER_EXPENSES"}))
            @RequestParam("category") Category category,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(
            summary = "Update a budget limit amount",
            description = """
                    Update only the spending limit of a budget owned by the authenticated user.
                    Supply limitAmount as a query parameter, not a JSON body. It must be greater than zero.
                    The budget ID, category, period, and owner remain unchanged.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Budget limit updated successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BudgetLimitResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid budget ID, missing or invalid amount, or amount is not positive",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "404", description = "Budget limit not found for the authenticated user",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            }
    )
    ResponseEntity<BudgetLimitResponse> updateBudgetLimitAmount(
            @Parameter(description = "ID of the budget limit to update", required = true, example = "6d849ef4-9746-4067-b31e-1737693ab1c6")
            @PathVariable("budgetLimitId") UUID budgetLimitId,
            @Parameter(description = "New spending limit, greater than zero", required = true, example = "500.00")
            @RequestParam("limitAmount") BigDecimal limitAmount,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(
            summary = "Delete a budget limit",
            description = """
                    Delete a budget limit owned by the authenticated user.
                    Existing transactions are preserved. No request body is required.
                    Deleting an already deleted or unknown budget returns 404.
                    """,
            responses = {
                    @ApiResponse(responseCode = "204", description = "Budget limit deleted successfully", content = @Content),
                    @ApiResponse(responseCode = "400", description = "Invalid budget ID format",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "404", description = "Budget limit not found for the authenticated user",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            }
    )
    ResponseEntity<Void> deleteBudgetLimit(
            @Parameter(description = "ID of the budget limit to delete", required = true, example = "6d849ef4-9746-4067-b31e-1737693ab1c6")
            @PathVariable("budgetLimitId") UUID budgetLimitId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

}
