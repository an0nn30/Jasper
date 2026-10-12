package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.accessibility.AccessibleRole;
import javax.swing.UIManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class TitleSearchFieldTest {
    @BeforeAll static void theme() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> ThemeTestSupport.install(Theme.DARK));
    }

    @Test void showsTheContextWithAFallbackAndATooltipThatIsNeverHtml() {
        var field = new TitleSearchField(() -> { }, () -> "⌘K");
        assertThat(field.contextText()).isEqualTo("Search");
        field.setContext("dustin@~/projects : vim", "dustin@~/projects : vim (full)", null);
        assertThat(field.contextText()).isEqualTo("dustin@~/projects : vim");
        assertThat(field.getToolTipText()).isEqualTo("dustin@~/projects : vim (full)");
        field.setContext(" ", null, null);
        assertThat(field.contextText()).isEqualTo("Search");
        assertThat(field.getToolTipText()).isEqualTo("Search");
        assertThat(field.createToolTip().getClientProperty("html.disable")).isEqualTo(true);
        assertThat(field.shortcutText()).isEqualTo("⌘K");
        assertThat(field.isFocusable()).isFalse();
    }

    @Test void isAnAccessibleButtonNamedAfterTheContext() {
        var field = new TitleSearchField(() -> { }, () -> "");
        field.setContext("build-host:/srv/app", null, null);
        assertThat(field.getAccessibleContext().getAccessibleRole()).isEqualTo(AccessibleRole.PUSH_BUTTON);
        assertThat(field.getAccessibleContext().getAccessibleName()).isEqualTo("Search, build-host:/srv/app");
    }

    @Test void aLeftClickInsideOpensThePaletteAndADragOutsideDoesNot() {
        var opened = new AtomicInteger();
        var field = new TitleSearchField(opened::incrementAndGet, () -> "");
        field.setSize(400, 22);
        field.dispatchEvent(mouse(field, MouseEvent.MOUSE_PRESSED, 10));
        field.dispatchEvent(mouse(field, MouseEvent.MOUSE_RELEASED, 10));
        assertThat(opened).hasValue(1);
        field.dispatchEvent(mouse(field, MouseEvent.MOUSE_PRESSED, 10));
        field.dispatchEvent(mouse(field, MouseEvent.MOUSE_RELEASED, 900));
        assertThat(opened).hasValue(1);
        field.click();
        assertThat(opened).hasValue(2);
    }

    @Test void clipKeepsWholeCodePointsAndEndsWithAnEllipsis() {
        var metrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).getGraphics().getFontMetrics(UIManager.getFont("Label.font"));
        assertThat(TitleSearchField.clip("short", metrics, 1000)).isEqualTo("short");
        String clipped = TitleSearchField.clip("a very long working directory name", metrics, metrics.stringWidth("a very…"));
        assertThat(clipped).endsWith("…").startsWith("a very");
        assertThat(metrics.stringWidth(clipped)).isLessThanOrEqualTo(metrics.stringWidth("a very…"));
        String emoji = "ab" + new String(Character.toChars(0x1F600)) + "cdefghij";
        String cut = TitleSearchField.clip(emoji, metrics, metrics.stringWidth("ab…") + 1);
        assertThat(cut.chars().filter(c -> Character.isSurrogate((char) c)).count() % 2).as("no half emoji").isZero();
        assertThat(TitleSearchField.clip("anything", metrics, 0)).isEmpty();
    }

    /** theme.md: the title bar's command field is a raised field; hover changes its edge, never its fill. */
    @Test void paintsTheRaisedFieldFillAndKeepsItOnHover() {
        var field = new TitleSearchField(() -> { }, () -> "⌘K");
        field.setSize(400, 22);
        var raised = dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_FIELD_RAISED);
        assertThat(pixel(field, 200, 11)).isEqualTo(raised);
        field.dispatchEvent(mouse(field, MouseEvent.MOUSE_ENTERED, 10));
        assertThat(pixel(field, 390, 3)).isEqualTo(raised);
        field.setCollapsed(true);
        assertThat(field.collapsed()).isTrue();
    }

    private static MouseEvent mouse(TitleSearchField field, int id, int x) {
        return new MouseEvent(field, id, System.currentTimeMillis(), 0, x, 5, 1, false, MouseEvent.BUTTON1);
    }

    private static Color pixel(TitleSearchField field, int x, int y) {
        var image = new BufferedImage(field.getWidth(), field.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { field.paint(g); } finally { g.dispose(); }
        return new Color(image.getRGB(x, y));
    }
}
