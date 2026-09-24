package dev.jasper.sdk.ui;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StatusProgressStateTest {
    @Test void validatesFractionsAndKeepsUnknownTotalsDistinctFromZero() {
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -.01, 1.01})
            assertThatIllegalArgumentException().isThrownBy(() -> state(OptionalDouble.of(bad)));
        assertThat(state(OptionalDouble.empty()).fraction()).isEmpty();
        assertThat(state(OptionalDouble.of(0)).fraction()).hasValue(0);
        assertThat(state(OptionalDouble.of(1)).fraction()).hasValue(1);
        assertThatNullPointerException().isThrownBy(() -> state(null));
    }
    private static StatusProgressState state(OptionalDouble fraction) {
        return new StatusProgressState("Transfer", "20 MiB left", "Half complete", fraction, null, null);
    }
}
