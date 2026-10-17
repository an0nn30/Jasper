package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.swing.FlatLafInstaller;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;

/** Today's header: the centred title field and the IntelliJ toolbar packed against the trailing edge. */
final class UnifiedHeader implements HeaderComposition {
    private static int metric(DesignMetrics.Role role) { return FlatLafInstaller.installedKit().metrics().value(role); }

    @Override public void build(WindowChrome chrome) { chrome.buildUnifiedToolbar(); }
    @Override public boolean titleBarZone() { return true; }
    @Override public Dimension minimumSize(WindowHeader header) { return header.unifiedMinimumSize(); }
    @Override public void layout(WindowHeader header) { header.unifiedLayout(); }
    @Override public Dimension searchPreferred() {
        return new Dimension(UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_WIDTH)), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT)));
    }
    @Override public Dimension searchMinimum() {
        return new Dimension(UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_MIN_WIDTH)), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT)));
    }
    @Override public Dimension searchMaximum() {
        return new Dimension(UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_MAX_WIDTH)), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT)));
    }
    @Override public void paintSearch(TitleSearchField field, Graphics graphics, boolean hover, boolean collapsed) {
        ChromePainter.installed().search(field, graphics, ThemeZone.theme(field), hover, collapsed,
            field.contextText(), field.shortcutText(), AppIcons.icon("search"), field.contextIcon());
    }
    @Override public String searchTooltip(String context, String contextTooltip, String shortcut) {
        return contextTooltip == null || contextTooltip.isBlank() ? context : contextTooltip;
    }
    @Override public String searchName(String context) { return "Search, " + context; }
    @Override public void toolbarMode(WindowHeader header, ToolbarMode mode, boolean normal) {
        header.toolbar().setVisible(normal && mode != ToolbarMode.HIDDEN);
        header.toolbar().setLabelsWanted(mode == ToolbarMode.ICONS_AND_LABELS);
    }
    @Override public void refreshTheme(WindowHeader header) { }
    @Override public JButton contributed(WindowChrome chrome, Action action, String label, Icon icon, boolean dropdown) {
        return chrome.referenceButton(action, label, icon, dropdown);
    }
}
