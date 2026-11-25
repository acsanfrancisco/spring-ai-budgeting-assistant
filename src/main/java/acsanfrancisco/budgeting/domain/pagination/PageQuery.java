package acsanfrancisco.budgeting.domain.pagination;

public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 100;

    public PageQuery{
        if (page < 0){
            throw new IllegalArgumentException("Page number must not be negative");
        }
        if (size < 1){
            throw new IllegalArgumentException("Size must be positive");
        }
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("Size must not exceed " + MAX_SIZE);
        }
    }
}
