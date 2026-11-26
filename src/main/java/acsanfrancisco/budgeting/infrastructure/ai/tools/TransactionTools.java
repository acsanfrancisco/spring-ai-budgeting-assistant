package acsanfrancisco.budgeting.infrastructure.ai.tools;

import acsanfrancisco.budgeting.application.input.CreateTransactionInput;
import acsanfrancisco.budgeting.application.output.MonthlyExpensesOutput;
import acsanfrancisco.budgeting.application.output.MonthlySummaryOutput;
import acsanfrancisco.budgeting.application.output.TransactionOutput;
import acsanfrancisco.budgeting.application.usecase.transaction.FindMonthlyExpensesByCategoryUseCase;
import acsanfrancisco.budgeting.application.usecase.transaction.FindTransactionsByCategoryAndPeriodUseCase;
import acsanfrancisco.budgeting.application.usecase.transaction.GetMonthlySummaryUseCase;
import acsanfrancisco.budgeting.application.usecase.transaction.PersistTransactionUseCase;
import acsanfrancisco.budgeting.domain.pagination.PageQuery;
import acsanfrancisco.budgeting.domain.pagination.PageResult;
import acsanfrancisco.budgeting.domain.transaction.Category;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Assistant tools for transactions. Each method adapts the model's arguments and the
 * ToolContext to a use case shared with the REST API; the names and descriptions are the
 * contract read by the model.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionTools {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final PersistTransactionUseCase persistTransaction;

    private final FindTransactionsByCategoryAndPeriodUseCase findTransactions;

    private final FindMonthlyExpensesByCategoryUseCase findMonthlyExpenses;

    private final GetMonthlySummaryUseCase getMonthlySummary;

    @Tool(name = "persist-transaction",
    description = """
        Registra uma nova receita ou despesa para o usuário autenticado.
        Use somente quando o usuário solicitar ou relatar uma movimentação
        com intenção clara de registrá-la; não use para consultas, exemplos ou hipóteses.
        Exige descrição, valor maior que zero e categoria.
        A categoria determina se a movimentação é receita ou despesa.
        Se o usuário ainda não indicou a data, pergunte se foi hoje e aguarde antes de registrar.
        Inclua na mesma mensagem a pergunta sobre a categoria ou outro dado que estiver faltando.
        Para despesas, ofereça adicionar uma descrição e aguarde a escolha antes de chamar.
        Mesmo com valor, data e categoria definidos, a categoria sozinha não expressa essa escolha.
        Se o usuário já ditou ou dispensou a descrição desse gasto, prossiga sem perguntar novamente.
        Se faltar um dado obrigatório ou houver ambiguidade, peça esclarecimento antes de chamar.
        Esta ferramenta cria um novo registro a cada execução.
        Confirme o registro somente após receber uma resposta de sucesso.
        """)
    public TransactionOutput persistTransaction(
            @ToolParam(description = """
                Preserve a descrição ditada pelo usuário. Se dispensar a descrição personalizada,
                use a finalidade informada ou o nome da categoria em português. Para receitas, extraia a descrição do relato.
                Não acrescente estabelecimentos, produtos ou outros detalhes não informados.
                """) String description,
            @ToolParam(description = """
                Valor da movimentação, maior que zero.
                Converta a quantia falada para número decimal: "vinte e cinco e cinquenta" corresponde a 25.50.
                Informe valor positivo tanto para receitas quanto para despesas.
                Não estime valores ausentes ou ambíguos.""") BigDecimal amount,
            @ToolParam(description = """
                Categoria que melhor corresponde à movimentação relatada.
                Despesas: GROCERIES (supermercado e compras de alimentos),
                PHARMA (farmácia e medicamentos), AUTO (gastos com veículo),
                HEALTH (serviços de saúde), TRANSPORT (transporte e deslocamentos),
                HOUSING (moradia), ENTERTAINMENT (lazer),
                PERSONAL_CARE (cuidados pessoais), EDUCATION (educação),
                OTHER_EXPENSES (demais despesas).
                Receitas: SALARY (salário), FREELANCE (trabalho autônomo),
                INVESTMENT (rendimentos de investimentos), OTHER_INCOMES (demais receitas).
                Se o relato indicar apenas o tipo (despesa ou receita), pergunte a finalidade do gasto
                ou a origem da receita e aguarde a resposta antes de chamar a ferramenta.
                Use OTHER_EXPENSES ou OTHER_INCOMES somente por escolha ou confirmação do usuário.
                Use somente um dos valores disponíveis.""") Category category,
            @ToolParam(description = """
                Data da movimentação no formato YYYY-MM-DD.
                Omita somente quando o usuário disser ou confirmar que foi hoje. O aplicativo aplicará a data atual.
                Para datas relativas, use somente uma data de referência fornecida pelo aplicativo.
                Se a data informada estiver incompleta ou ambígua, peça esclarecimento.""", required = false) String date, ToolContext context) {
        CreateTransactionInput input = new CreateTransactionInput(description, amount, category,
                date == null ? null : LocalDate.parse(date));
        TransactionOutput output = persistTransaction.execute(input, ToolContexts.userId(context),
                ToolContexts.transactionRequestKey(context));
        // Audit trail for records written by the AI; amounts and descriptions stay out of the log.
        log.info("Transaction registered by the assistant. id={}", output.id());
        return output;
    }

    @Tool(name = "list-transaction-by-category-and-period",
    description = """
    Lista lançamentos individuais de uma categoria para o usuário autenticado.
    As datas inicial e final são inclusivas e independentes. Sem datas, pesquisa todo o histórico da categoria.
    Se o usuário não indicou um período, pergunte se quer só os lançamentos de hoje ou todo o histórico.
    Se pedir todo o histórico ou indicar uma das datas, siga o filtro solicitado diretamente.
    Sem page e size, retorna a primeira página com até 10 lançamentos, do mais recente para o mais antigo,
    e o total de lançamentos encontrados em totalElements.
    Para mostrar outra quantidade ou outra página, chame novamente com os mesmos filtros, page e size de 1 a 100.
    Considere que não há lançamentos somente quando totalElements for zero.
    Use para pedidos de listagem, detalhes dos lançamentos ou uma página específica.
    Para consultar o total mensal gasto em uma categoria, prefira a ferramenta de total mensal.
    Não interprete o conteúdo de uma página como a lista completa nem sua soma como total geral.
    """)
    public PageResult<TransactionOutput> listTransactions(
            @ToolParam(description = """
                    Categoria dos lançamentos a consultar.
                    Use uma categoria disponível, identificada pelo pedido do usuário.""") Category category,
            @ToolParam(description = "Data inicial inclusiva (YYYY-MM-DD)", required = false) LocalDate from,
            @ToolParam(description = "Data final inclusiva (YYYY-MM-DD)", required = false) LocalDate to,
            @ToolParam(description = """
                    Índice da página, começando em zero: primeira página = 0, segunda = 1.
                    Ao buscar detalhes com size, a página padrão é 0.""", required = false) Integer page,
            @ToolParam(description = "Quantidade por página (1 a 100). Sem size, usa 10.", required = false) Integer size,
            ToolContext context) {
        PageQuery pageQuery = new PageQuery(page == null ? 0 : page, size == null ? DEFAULT_PAGE_SIZE : size);
        return findTransactions.execute(category, ToolContexts.userId(context), from, to, pageQuery);
    }

    @Tool(name = "list-monthly-expenses-by-category",
    description = """
            Consulta o valor total das despesas de uma categoria em um mês e ano, para o usuário autenticado.
            Use para perguntas como "quanto gastei com supermercado em dezembro de 2026?".
            Exige período mensal e uma categoria de despesa.
            Retorna um total agregado, não uma lista de lançamentos.
            Não use categorias de receita: SALARY, FREELANCE, INVESTMENT ou OTHER_INCOMES.
            """)
    public MonthlyExpensesOutput monthlyExpenses(
            @ToolParam(description = """
                    Mês e ano da consulta no formato YYYY-MM.
                    Exemplo: "dezembro de 2026" corresponde a "2026-12".
                    Não presuma o ano quando ele não puder ser determinado pelo contexto.
                    Resolva períodos relativos somente com uma data de referência fornecida pelo aplicativo.""") String yearMonth,
            @ToolParam(description = """
                    Categoria de despesa cujo total será consultado.
                    Valores permitidos: GROCERIES, PHARMA, AUTO, HEALTH, TRANSPORT,
                    HOUSING, ENTERTAINMENT, PERSONAL_CARE, EDUCATION ou OTHER_EXPENSES""") Category category,
            ToolContext context) {
        return findMonthlyExpenses.execute(YearMonth.parse(yearMonth), category, ToolContexts.userId(context));
    }

    @Tool(name = "get-monthly-summary",
    description = """
            Consulta o resumo financeiro de um mês e ano para o usuário autenticado.
            Retorna o total de receitas, o total de despesas e o saldo líquido do mês.
            Use para perguntas sobre o resultado geral do mês, quanto entrou, quanto saiu ou qual foi o saldo líquido.
            O saldo líquido representa receitas menos despesas daquele mês, não o saldo atual de uma conta bancária nem o saldo acumulado de todo o histórico.
            Exige mês e ano. Se o período não puder ser determinado, peça esclarecimento.
            """)
    public MonthlySummaryOutput monthlySummary(
            @ToolParam(description = """
                    Mês e ano do resumo no formato YYYY-MM.
                    Exemplo: "dezembro de 2026" corresponde a "2026-12".
                    Não presuma o ano quando ele não puder ser determinado pelo contexto.
                    Resolva períodos relativos somente com uma data de referência fornecida pelo aplicativo.""") String yearMonth,
            ToolContext context) {
        return getMonthlySummary.execute(YearMonth.parse(yearMonth), ToolContexts.userId(context));
    }
}
