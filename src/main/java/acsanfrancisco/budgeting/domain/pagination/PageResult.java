package acsanfrancisco.budgeting.domain.pagination;

import java.util.List;

public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements) {
}
