package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AppearanceIconTest {
    @Test void oldImplementationsInheritModernFallback() {
        Icon original = new ImageIcon();
        Appearance legacy = new Appearance() {
            public Variant variant() { return Variant.LIGHT; }
            public Subscription onChanged(java.util.function.Consumer<Variant> handler) { return () -> {}; }
            public Icon icon(String path) {
                if (!"icon.svg".equals(path)) throw new IllegalArgumentException("missing");
                return original;
            }
        };
        assertThat(legacy.icon("icon.svg", OldGnomeIcon.LOCK)).isSameAs(original);
        assertThatNullPointerException().isThrownBy(() -> legacy.icon("icon.svg", null));
        assertThatIllegalArgumentException().isThrownBy(() -> legacy.icon(null, OldGnomeIcon.LOCK));
        assertThatIllegalArgumentException().isThrownBy(() -> legacy.icon("missing", OldGnomeIcon.LOCK));
    }
}
