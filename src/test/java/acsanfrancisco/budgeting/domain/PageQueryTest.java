package acsanfrancisco.budgeting.domain;

import acsanfrancisco.budgeting.domain.pagination.PageQuery;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageQueryTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void acceptsBoundarySizes(int size) {
        assertEquals(size, new PageQuery(0, size).size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void rejectsSizesOutsideRange(int size) {
        assertThrows(IllegalArgumentException.class, () -> new PageQuery(0, size));
    }
}
