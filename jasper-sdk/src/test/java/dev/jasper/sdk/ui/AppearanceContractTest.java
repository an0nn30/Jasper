package dev.jasper.sdk.ui;

import dev.jasper.sdk.JasperSdk;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AppearanceContractTest {
    @Test void appearanceOffersOneIconFamilyInSdkZeroEight() {
        assertThat(JasperSdk.VERSION).isEqualTo("0.8.0");
        assertThat(Arrays.stream(Appearance.class.getMethods()).filter(method -> method.getName().equals("icon"))
            .map(method -> method.getParameterCount())).containsOnly(1);
    }
}
