package dev.jasper.editor.ui;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import dev.jasper.editor.vim.VimTextArea;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.Appearance;
import dev.jasper.sdk.ui.EditorPalette;
import dev.jasper.sdk.ui.SyntaxPalette;
import java.util.List;
import javax.swing.*;
import java.awt.event.ActionEvent;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * The editor's text column. In a translucent window it fills the editor background, and then the gutter's, once each
 * at the workspace opacity, replacing what lies beneath; the text area, viewports and gutter above it stay clear.
 * Java2D renders a translucent Src fill in software unless it is an unantialiased rectangle, so both are rectangles.
 */
final class DocumentArea extends JComponent implements AutoCloseable {
    final VimTextArea area = new VimTextArea();
    final RTextScrollPane scroll;
    private final java.awt.event.HierarchyListener hierarchy = event -> {
        if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.PARENT_CHANGED) != 0) refreshAppearance();
    };
    private Appearance appearance;
    private Subscription changes;
    private EditorSettings settings;
    private final EditorTheme theme = new EditorTheme();
    private boolean closed;
    private double opacity = 1;
    private Color background = Color.BLACK, gutter = Color.BLACK;

    DocumentArea() {
        setLayout(new BorderLayout());
        area.setCodeFoldingEnabled(true);
        area.setMarkOccurrences(true);
        area.setBracketMatchingEnabled(true);
        area.setAnimateBracketMatching(false);
        area.setHighlightCurrentLine(true);
        area.setAntiAliasingEnabled(true);
        scroll = new RTextScrollPane(area);
        scroll.setLineNumbersEnabled(true);
        scroll.setFoldIndicatorEnabled(true);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
        addHierarchyListener(hierarchy);
    }

    void appearance(Appearance value) {
        if (closed) return;
        if (changes != null) changes.close();
        appearance = value;
        changes = value.onWorkspaceChanged(variant -> refreshAppearance());
        refreshAppearance();
    }

    void settings(EditorSettings value) {
        if (closed) return;
        settings = value;
        refreshAppearance();
    }

    private void refreshAppearance() {
        if (!closed && appearance != null && settings != null)
            apply(settings, theme, appearance.editorPalette(), appearance.syntaxPalette(), appearance.workspaceOpacity(this));
    }

    void apply(EditorSettings settings, EditorTheme theme, EditorPalette editor, SyntaxPalette syntax, double opacity) {
        if (closed) return;
        area.setFont(settings.font());
        theme.apply(area, scroll, editor, syntax, opacity);
        scroll.getGutter().setLineNumberFont(settings.font());
        area.setTabSize(settings.tabSize());
        glass(opacity, editor.background(), editor.gutter());
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        removeHierarchyListener(hierarchy);
        if (changes != null) changes.close();
    }

    /** Below 1 this surface paints the glass; at 1 it paints an opaque document surface. */
    void glass(double opacity, Color background, Color gutter) {
        this.opacity = opacity;
        this.background = background;
        this.gutter = gutter;
        setOpaque(opacity >= 1);
        repaint();
    }

    double opacity() { return opacity; }

    @Override protected void paintComponent(Graphics graphics) {
        if (opacity >= 1) { graphics.setColor(background); graphics.fillRect(0, 0, getWidth(), getHeight()); return; }
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setComposite(AlphaComposite.Src);
            g.setColor(translucent(background));
            g.fillRect(0, 0, getWidth(), getHeight());
            var header = scroll.getRowHeader();
            if (header != null && header.isVisible() && header.getParent() != null) {
                Rectangle bounds = SwingUtilities.convertRectangle(header.getParent(), header.getBounds(), this);
                g.setColor(translucent(gutter));
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        } finally {
            g.dispose();
        }
    }

    /** @param commands bound in the text area's own map, which overrides RSyntaxTextArea's defaults for the same strokes */
    public void bind(List<Command> commands) {
        for (Command command : commands) {
            if (!command.bound() || command.stroke().isEmpty()) continue;
            area.getInputMap(JComponent.WHEN_FOCUSED).put(command.stroke().get(), command.id());
            area.getActionMap().put(command.id(), action(command));
            area.allowInNormalMode(command.id());
        }
    }

    /** @param commands added below RSyntaxTextArea's own right-click items */
    public void addToPopup(List<Command> commands) {
        JPopupMenu popup = area.getPopupMenu();
        popup.addSeparator();
        for (Command command : commands) popup.add(menuItem(command));
    }

    /**
     * @param command a command
     * @return a Swing action running it
     */
    public static Action action(Command command) {
        return new AbstractAction(command.title()) {
            @Override public void actionPerformed(ActionEvent event) { command.run().run(); }
        };
    }

    /**
     * @param command a command
     * @return a menu item titled with it, with its shortcut as the accelerator, running it
     */
    public static JMenuItem menuItem(Command command) {
        var item = new JMenuItem(command.title());
        command.stroke().ifPresent(item::setAccelerator);
        item.addActionListener(event -> command.run().run());
        return item;
    }

    /**
     * @param title the submenu's title, shown as plain text, since a split item's label comes from another plugin
     * @return a submenu for the vendor popup
     */
    static JMenu submenu(String title) {
        // The text goes in after html.disable: the UI reads the property only when the text, font or foreground changes.
        var menu = new JMenu();
        menu.putClientProperty("html.disable", true);
        menu.setText(title);
        return menu;
    }

    /**
     * @param title the item's title, shown as plain text, since a split item's label comes from another plugin
     * @param run what the item does, on the UI thread
     * @return a menu item for the vendor popup, running {@code run}
     */
    static JMenuItem plainItem(String title, Runnable run) {
        var item = new JMenuItem();
        item.putClientProperty("html.disable", true);
        item.setText(title);
        item.addActionListener(event -> run.run());
        return item;
    }

    private Color translucent(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) Math.round(255 * opacity));
    }
}
