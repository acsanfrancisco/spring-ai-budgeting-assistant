package acsanfrancisco.budgeting.infrastructure.http.response;

import acsanfrancisco.budgeting.domain.pagination.PageResult;
import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <Input, Output> PageResponse<Output> from(PageResult<Input> result, Function<? super Input, ? extends Output> mapper) {
        List<Output> content = result.content().stream().<Output>map(mapper).toList();
        return new PageResponse<>(
                content,
                result.page(),
                result.size(),
                result.totalElements());
    }
}
