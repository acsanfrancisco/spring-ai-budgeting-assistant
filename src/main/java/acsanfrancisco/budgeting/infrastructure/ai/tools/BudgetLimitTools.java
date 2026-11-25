package acsanfrancisco.budgeting.infrastructure.ai.tools;

import acsanfrancisco.budgeting.application.input.CreateBudgetLimitInput;
import acsanfrancisco.budgeting.application.output.BudgetLimitOutput;
import acsanfrancisco.budgeting.application.output.MonthlyBudgetLimitStatusOutput;
import acsanfrancisco.budgeting.application.usecase.budget.GetBudgetLimitStatusUseCase;
import acsanfrancisco.budgeting.application.usecase.budget.PersistBudgetLimitUseCase;
import acsanfrancisco.budgeting.domain.transaction.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Assistant tools for monthly budget limits. Each method adapts the model's arguments and the
 * ToolContext to a use case shared with the REST API; the names and descriptions are the
 * contract read by the model.
 */
@Component
@RequiredArgsConstructor
public class BudgetLimitTools {

    private final PersistBudgetLimitUseCase persistBudgetLimit;

    private final GetBudgetLimitStatusUseCase getBudgetLimitStatus;

    @Tool(name = "persist-budget-limit",
    description = """
            Cria um limite mensal de gastos para uma categoria de despesa.
            Requer categoria, período no formato YYYY-MM e valor maior que zero.
            Use somente quando houver intenção clara de definir um novo limite.
            Não registra transações nem altera limites existentes.
            Só pode existir um limite por usuário para a mesma categoria e período.
            """)
    public BudgetLimitOutput persistBudgetLimit(
            @ToolParam(description = """
            Categoria de despesa à qual o limite será aplicado.
            Use somente uma categoria disponível do tipo EXPENSE, conforme o relato do usuário.
            """) Category category,
            @ToolParam(description = """
            Mês e ano de aplicação do limite, no formato YYYY-MM.
            Se o período não puder ser determinado pelo contexto, solicite o mês e o ano antes de executar.
            """) String period,
            @ToolParam(description = """
            Valor máximo de gastos para a categoria no período.
            Deve ser maior que zero e representa o limite total, não uma despesa nem um acréscimo ao limite existente.
            """) BigDecimal limitAmount, ToolContext context) {
        CreateBudgetLimitInput input = new CreateBudgetLimitInput(category, YearMonth.parse(period), limitAmount);
        return persistBudgetLimit.execute(input, ToolContexts.userId(context));
    }

    @Tool(name = "get-budget-limit-status",
    description = """
            Consulta a situação do limite de gastos de uma categoria de despesa
            em um mês. Retorna o limite configurado, total gasto, valor restante,
            percentual utilizado e situação do limite.
            Use para saber quanto ainda pode ser gasto ou se o limite está próximo, foi atingido ou ultrapassado.
            Se não houver limite configurado, a consulta informa essa situação.
            Não cria nem altera limites ou transações.
            """)
    public MonthlyBudgetLimitStatusOutput budgetLimitStatus(
            @ToolParam(description = """
                    Mês e ano da consulta, no formato YYYY-MM.
                    Se o período não puder ser determinado pelo contexto, solicite o mês e o ano antes de executar.
                    """) String period,
            @ToolParam(description = """
                    Categoria de despesa cujo limite será consultado.
                    Use somente uma categoria disponível do tipo EXPENSE.
                    Se a categoria estiver ambígua, peça esclarecimento.
                    """) Category category, ToolContext context){
        return getBudgetLimitStatus.execute(YearMonth.parse(period), ToolContexts.userId(context), category);
    }
}
