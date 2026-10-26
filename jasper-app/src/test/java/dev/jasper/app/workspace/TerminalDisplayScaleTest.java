package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.FontConfig;
import dev.jasper.terminal.rendering.FontSet;
import java.awt.Dimension;
import java.util.ArrayDeque;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class TerminalDisplayScaleTest {
    @Test void panesAndInitialGeometryFollowSharedScaleWhileReloadAndZoomStayLogicalAndCloseUnbinds() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        var owner = new WindowContent[1];
        String previous = System.getProperty("flatlaf.uiScale");
        try {
            edt(() -> {
                System.setProperty("flatlaf.uiScale", "1.5");
                owner[0] = content(idleLauncher(pending));
                owner[0].applyConfiguration(ConfigSnapshot.builder().font(FontConfig.defaults().withSize(14)).build(), false);
                assertThat(UIScale.getUserScaleFactor()).isEqualTo(1.5f);
                dev.jasper.app.platform.BundledFonts.register();
                var f = FontConfig.defaults();
                var fonts = new FontSet(f.family(), 21, f.fallback(), f.ligatures(), f.lineHeight());
                assertThat(InitialWindowSize.terminalArea(ConfigSnapshot.builder().font(FontConfig.defaults().withSize(14)).build()))
                    .isEqualTo(new Dimension(100 * fonts.cellWidth() + 12, 35 * fonts.cellHeight() + 12));
            });
            pending.remove().run(); edt(() -> {});
            edt(() -> {
                var pane = owner[0].currentPane();
                var view = pane.view();
                assertThat(view).isNotNull();
                assertMinimum(view, 21);
                assertThat(pane.getInsets().left).isEqualTo(6);
                owner[0].invoke(ActionId.FONT_BIGGER);
                assertThat(view.fontSize()).isEqualTo(15);
                assertMinimum(view, 22.5f);
                owner[0].applyConfiguration(ConfigSnapshot.builder().font(FontConfig.defaults().withSize(14)).build(), false);
                assertThat(view.fontSize()).isEqualTo(15);
                changeScale("2");
                assertThat(view.fontSize()).isEqualTo(15);
                assertMinimum(view, 30);
                assertThat(pane.getInsets().left).isEqualTo(8);
                owner[0].invoke(ActionId.FONT_RESET);
                assertThat(view.fontSize()).isEqualTo(14);
                assertMinimum(view, 28);
                owner[0].applyConfiguration(ConfigSnapshot.builder().font(FontConfig.defaults().withSize(18)).build(), false);
                assertMinimum(view, 36);
                var minimum = view.getMinimumSize();
                int listeners = UIManager.getDefaults().getPropertyChangeListeners().length;
                pane.close();
                assertThat(UIManager.getDefaults().getPropertyChangeListeners().length).isLessThan(listeners);
                changeScale("1.25");
                assertThat(view.getMinimumSize()).isEqualTo(minimum);
            });
        } finally {
            closeOwners();
            edt(() -> {
                if (previous == null) System.clearProperty("flatlaf.uiScale"); else System.setProperty("flatlaf.uiScale", previous);
                dev.jasper.app.appearance.ThemeTestSupport.controller(true).close();
            });
        }
    }

    private static void changeScale(String value) {
        System.setProperty("flatlaf.uiScale", value);
        // Desktop font updates are FlatLaf's supported scale invalidation path.
        UIManager.getLookAndFeelDefaults().put("defaultFont", UIManager.getFont("defaultFont").deriveFont(UIManager.getFont("defaultFont").getSize2D() + 1));
        assertThat(UIScale.getUserScaleFactor()).isEqualTo(Float.parseFloat(value));
    }

    private static void assertMinimum(dev.jasper.terminal.view.TerminalView view, float size) {
        var o = view.options();
        var fonts = new FontSet(o.fontFamily(), size, o.fallbackFonts(), o.ligatures(), o.lineHeight());
        assertThat(view.getMinimumSize()).isEqualTo(new Dimension(5 * fonts.cellWidth(), 2 * fonts.cellHeight()));
    }
}
