package acsanfrancisco.budgeting.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MonetaryAmount {
    private MonetaryAmount() {
    }

    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount value must be informed an greater than zero");
        }
        long integerDigits = (long) amount.precision() - amount.scale();
        if (integerDigits > 17) {
            throw new IllegalArgumentException("Amount exceeds 17 integer digits");
        }
        if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amount must be exact to the cent");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
