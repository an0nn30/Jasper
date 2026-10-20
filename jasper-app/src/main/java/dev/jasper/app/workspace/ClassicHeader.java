package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;

/**
 * The classic toolbar row (spec 10.3; the Workstation mocks): left-packed "New Session", "Split ▾" and "Maximize" in
 * separated groups, the plugin group, then "Search everywhere" with the palette's keycap and "Settings…" at the trailing
 * edge, over a 1 px {@code border.window} rule. The row is window chrome under a native title, not a title-bar zone.
 */
final class ClassicHeader implements HeaderComposition {
    private final WindowToolbar trailing = new WindowToolbar(() -> { });

    private static int metric(DesignMetrics.Role role) { return UIScale.scale(FlatLafInstaller.installedKit().metrics().value(role)); }

    /** The trailing group: "Settings…". */
    WindowToolbar trailing() { return trailing; }

    @Override public void build(WindowChrome chrome) {
        var spacing = WindowToolbar.Leading.classic(FlatLafInstaller.installedKit().metrics());
        var toolbar = chrome.windowToolbar();
        toolbar.packLeading(spacing);
        chrome.classicButton(toolbar, ActionId.NEW_TAB, "New Session", "square-plus", 90);
        toolbar.add(chrome.separator(true));
        chrome.classicSplit();
        chrome.classicButton(toolbar, ActionId.MAXIMIZE_PANE, "Maximize", "maximize", 10);
        chrome.addPluginGroup(true);
        toolbar.finish();
        trailing.setFloatable(false);
        trailing.setBorder(BorderFactory.createEmptyBorder());
        trailing.setOpaque(false);
        trailing.packLeading(new WindowToolbar.Leading(0, spacing.gap(), spacing.separatorGap(), spacing.separatorInset(), spacing.side()));
        chrome.classicButton(trailing, ActionId.OPEN_SETTINGS, "Settings…", "settings", 100);
        trailing.finish();
        chrome.header().add(trailing);
        refreshTheme(chrome.header());
    }

    @Override public boolean titleBarZone() { return false; }

    @Override public Dimension minimumSize(WindowHeader header) {
        Insets insets = header.getInsets();
        int toolbar = header.toolbar().isVisible() ? header.toolbar().minimumWidth() : 0;
        return new Dimension(insets.left + toolbar + metric(DesignMetrics.Role.CHROME_TRAILING_GAP) + trailing.fullWidth()
            + metric(DesignMetrics.Role.TOOLBAR_PADDING) + insets.right, header.toolbar().getPreferredSize().height + insets.top + insets.bottom);
    }

    @Override public void layout(WindowHeader header) {
        Insets insets = header.getInsets();
        int top = insets.top, height = Math.max(0, header.getHeight() - insets.top - insets.bottom);
        int gap = metric(DesignMetrics.Role.CHROME_TRAILING_GAP), right = Math.max(insets.left, header.getWidth() - insets.right - metric(DesignMetrics.Role.TOOLBAR_PADDING));
        int trailingWidth = Math.min(trailing.fullWidth(), right - insets.left);
        trailing.setBounds(right - trailingWidth, top, trailingWidth, height);
        var field = header.field();
        var toolbar = header.toolbar();
        Dimension preferred = searchPreferred(), minimum = searchMinimum();
        int fieldRight = trailing.getX() - gap;
        int toolbarMinimum = toolbar.isVisible() ? toolbar.minimumWidth() : 0;
        // The toolbar gives way first (labels, then the overflow menu, then its views); then the field shrinks and collapses.
        int fieldWidth = Math.min(preferred.width, Math.max(0, fieldRight - insets.left - toolbarMinimum - gap));
        boolean collapsed = fieldWidth < minimum.width;
        field.setCollapsed(collapsed);
        if (collapsed) fieldWidth = 0;
        int fieldX = fieldRight - fieldWidth;
        field.setBounds(fieldX, top + (height - preferred.height) / 2, fieldWidth, fieldWidth == 0 ? 0 : preferred.height);
        int toolbarRight = fieldWidth == 0 ? fieldRight : fieldX - gap;
        toolbar.setBounds(insets.left, top, toolbar.isVisible() ? Math.max(0, toolbarRight - insets.left) : 0, height);
    }

    @Override public Dimension searchPreferred() { return new Dimension(metric(DesignMetrics.Role.TOOLBAR_SEARCH_WIDTH), metric(DesignMetrics.Role.FIELD_HEIGHT)); }
    @Override public Dimension searchMinimum() { return new Dimension(metric(DesignMetrics.Role.TOOLBAR_SEARCH_WIDTH) / 2, metric(DesignMetrics.Role.FIELD_HEIGHT)); }
    @Override public Dimension searchMaximum() { return searchPreferred(); }

    /** The field reads "Search everywhere", so its tooltip and name say so, the tooltip with the palette's shortcut. */
    @Override public String searchTooltip(String context, String contextTooltip, String shortcut) {
        return shortcut == null || shortcut.isBlank() ? SEARCH : SEARCH + " (" + shortcut + ")";
    }
    @Override public String searchName(String context) { return SEARCH; }
    private static final String SEARCH = "Search everywhere";

    /**
     * A field on {@code surface.input} in a {@code border.control} edge ({@code border.hover} while hovered), the
     * placeholder "Search everywhere" and, at its trailing edge, the palette's shortcut in a light keycap (Rulings 6).
     * The placeholder and its hint keep {@code text.placeholder}, as field placeholders do.
     */
    @Override public void paintSearch(TitleSearchField field, Graphics graphics, boolean hover, boolean collapsed) {
        int width = field.getWidth(), height = field.getHeight();
        if (width <= 0 || height <= 0) return;
        var g = (Graphics2D) graphics.create();
        try {
            FlatUIUtils.setRenderingHints(g);
            var theme = ThemeZone.theme(field);
            var palette = theme.semantic();
            int arc = 2 * metric(DesignMetrics.Role.FIELD_RADIUS);
            g.setColor(palette.color(SemanticColor.SURFACE_INPUT));
            g.fillRoundRect(0, 0, width, height, arc, arc);
            g.setColor(SurfaceContrast.painted(theme, hover ? WindowPairs.SEARCH_HOVER_EDGE : WindowPairs.SEARCH_EDGE));
            g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            if (collapsed) return;
            int pad = metric(DesignMetrics.Role.TOOLBAR_SEARCH_PADDING), hint = 0;
            String shortcut = field.shortcutText();
            if (!shortcut.isEmpty()) {
                // IBM Plex Mono has no modifier symbols: the keycap then takes the platform face (plan 5b, ruling 26).
                g.setFont(ThemeZone.font(Typography.Role.MONO_SMALL, shortcut));
                FontMetrics mono = g.getFontMetrics();
                int keyPad = metric(DesignMetrics.Role.HINT_KEYCAP_PADDING), keyHeight = metric(DesignMetrics.Role.HINT_KEYCAP_HEIGHT);
                hint = mono.stringWidth(shortcut) + 2 * keyPad;
                int x = width - pad - hint, y = (height - keyHeight) / 2, keyArc = 2 * metric(DesignMetrics.Role.KEYCAP_RADIUS);
                g.setColor(DerivedColor.SURFACE_TABLE_HEADER.color(theme));
                g.fillRoundRect(x, y, hint, keyHeight, keyArc, keyArc);
                g.setColor(DerivedColor.BORDER_DIVIDER.color(theme));
                g.drawRoundRect(x, y, hint - 1, keyHeight - 1, keyArc, keyArc);
                g.setColor(SurfaceContrast.painted(theme, WindowPairs.KEYCAP_TEXT));
                FlatUIUtils.drawString(field, g, shortcut, x + keyPad, y + (keyHeight - mono.getHeight()) / 2 + mono.getAscent());
                hint += pad;
            }
            g.setFont(ThemeZone.font(Typography.Role.BODY));
            FontMetrics body = g.getFontMetrics();
            g.setColor(SurfaceContrast.painted(theme, WindowPairs.PLACEHOLDER));
            String text = TitleSearchField.clip(SEARCH, body, Math.max(0, width - 2 * pad - hint));
            FlatUIUtils.drawString(field, g, text, pad, (height - body.getHeight()) / 2 + body.getAscent());
        } finally { g.dispose(); }
    }

    @Override public void toolbarMode(WindowHeader header, ToolbarMode mode, boolean normal) {
        // Spec 10.3: hidden gives no row, so the Session view and Search everywhere go with it.
        header.setVisible(normal && mode != ToolbarMode.HIDDEN);
        header.toolbar().setVisible(true);
        boolean labels = mode == ToolbarMode.ICONS_AND_LABELS;
        header.toolbar().setLabelsWanted(labels);
        trailing.setLabelsWanted(labels);
    }

    @Override public void refreshTheme(WindowHeader header) {
        var theme = FlatLafInstaller.installedTheme();
        header.setBorder(BorderFactory.createMatteBorder(0, 0, metric(DesignMetrics.Role.HAIRLINE), 0,
            SurfaceContrast.painted(theme, WindowPairs.RULE)));
        for (var bar : new WindowToolbar[] {header.toolbar(), trailing})
            for (Component child : bar.getComponents())
                if (child instanceof ClassicToolbarButton button) button.setFont(ThemeZone.font(Typography.Role.TOOLBAR_LABEL));
        header.revalidate(); header.repaint();
    }

    /** Spec 10.3: a contributed button is a classic icon-and-label button; a dropdown ends in the caret, as "Split ▾" does. */
    @Override public JButton contributed(WindowChrome chrome, Action action, String label, Icon icon, boolean dropdown) {
        var button = new ClassicToolbarButton(action, label, dropdown, icon != null ? icon : AppIcons.icon("command"));
        if (dropdown) button.putClientProperty("keepLabel", true);
        if (action != null) WindowToolbar.entry(button, 30, () -> WindowToolbar.menuItem(action));
        return button;
    }
}
