package dev.jasper.editor.ui;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import dev.jasper.editor.vim.VimTextArea;
import dev.jasper.editor.markdown.MarkdownMode;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.Appearance;
import dev.jasper.sdk.ui.EditorPalette;
import dev.jasper.sdk.ui.SyntaxPalette;
import dev.jasper.sdk.ui.TerminalTypography;
import java.awt.Font;
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
    private final PreviewPane preview;
    final JScrollPane previewScroll;
    final ScrollLink scrollLink;
    private final JSplitPane split;
    private MarkdownMode mode = MarkdownMode.SOURCE;
    private double dividerRatio = .5;
    private boolean restoringDivider;
    private boolean sourceFocused = true;
    /**
     * Where the source showed the text when Preview took its place: the viewport's position, the start of the first
     * line it showed, as a position that moves with the text, and how far below that line's top the viewport began.
     * The source comes back on that text, though other views of the file edited it meanwhile (pane flexibility spec 6).
     */
    private record SourcePlace(java.awt.Point shown, javax.swing.text.Position line, int below) { }
    private SourcePlace sourcePlace;
    private boolean restoreSourcePosition;
    /** @return where the source shows the text now */
    private SourcePlace sourceShown() {
        var shown = scroll.getViewport().getViewPosition();
        try {
            int line = area.getLineOfOffset(Math.max(0, area.viewToModel2D(new java.awt.Point(0, shown.y))));
            int top = area.yForLine(line);
            return new SourcePlace(shown, area.getDocument().createPosition(area.getLineStartOffset(line)), top < 0 ? 0 : shown.y - top);
        } catch (javax.swing.text.BadLocationException impossible) {
            return new SourcePlace(shown, null, 0);
        }
    }
    /** @return where the viewport shows the held place's line as it was shown; its old position when the line is folded away */
    private java.awt.Point sourcePosition() {
        if (sourcePlace.line() == null) return sourcePlace.shown();
        try {
            int top = area.yForLine(area.getLineOfOffset(sourcePlace.line().getOffset()));
            return top < 0 ? sourcePlace.shown() : new java.awt.Point(sourcePlace.shown().x, top + sourcePlace.below());
        } catch (javax.swing.text.BadLocationException impossible) {
            return sourcePlace.shown();
        }
    }
    void focusSource(boolean value) { sourceFocused = value; }
    javax.swing.text.JTextComponent focusedContent() {
        return mode == MarkdownMode.PREVIEW || mode == MarkdownMode.SPLIT && !sourceFocused ? preview.editor() : area;
    }
    private java.util.function.IntConsumer activateLink = id -> {};
    private Runnable geometryChanged = () -> {};
    Runnable controllerGeometry = () -> {};
    Runnable styleChanged = () -> {};
    record PreviewStyle(TerminalTypography typography, dev.jasper.sdk.ui.WorkspacePalette palette, SyntaxPalette syntax) { }
    private PreviewStyle previewStyle;
    PreviewStyle previewStyle() { return previewStyle; }
    void previewStyle(TerminalTypography type, dev.jasper.sdk.ui.WorkspacePalette palette, SyntaxPalette syntax) {
        var previous = previewStyle;
        previewStyle = new PreviewStyle(type, palette, syntax);
        if (getFont() != null) preview.restyle(type, palette, syntax, getFont(), opacity);
        if (previous == null || !previous.syntax().equals(syntax)) styleChanged.run();
    }

    PreviewPane preview() { return preview; }
    void previewCallbacks(java.util.function.IntConsumer links, Runnable geometry) { activateLink=links;geometryChanged=geometry; }
    double dividerRatio() { return dividerRatio; }
    void mode(MarkdownMode value) {
        if (closed || mode==value) return;
        if (value == MarkdownMode.PREVIEW) sourcePlace = sourceShown();
        else if (mode == MarkdownMode.PREVIEW) restoreSourcePosition = true;
        mode=value;removeAll();split.setLeftComponent(null);split.setRightComponent(null);
        if(value==MarkdownMode.SOURCE){preview.cancelInstall();add(scroll,BorderLayout.CENTER);}
        else if(value==MarkdownMode.PREVIEW)add(previewScroll,BorderLayout.CENTER);
        else {restoringDivider=true;try{split.setLeftComponent(scroll);split.setRightComponent(previewScroll);add(split,BorderLayout.CENTER);}finally{restoringDivider=false;}}
        scrollLink.split(value == MarkdownMode.SPLIT);
        revalidate();repaint();
    }
    @Override public void doLayout() {
        restoringDivider=true;
        try {super.doLayout();if(mode==MarkdownMode.SPLIT){split.setDividerLocation(dividerRatio);split.doLayout();}
            if (restoreSourcePosition && sourcePlace != null) {
                scroll.doLayout(); scroll.getViewport().doLayout();
                scroll.getViewport().setViewPosition(sourcePosition()); restoreSourcePosition = false;
            }
        }
        finally{restoringDivider=false;}
    }
    private final java.awt.event.HierarchyListener hierarchy = event -> {
        if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.PARENT_CHANGED) != 0) refreshAppearance();
    };
    private Appearance appearance;
    private Subscription changes;
    private EditorSettings settings;
    private TerminalTypography typography;
    private final EditorTheme theme = new EditorTheme();
    private boolean closed;
    private double opacity = 1;
    private Color background = Color.BLACK, gutter = Color.BLACK;

    /**
     * The text column of a view; with {@code shares}, of another view of the same document, whose text area this one's
     * shares before anything listens to it (pane flexibility spec 6).
     *
     * @param shares a view's text column, or null for the document's first
     */
    DocumentArea(DocumentArea shares) {
        if (shares != null) area.share(shares.area);
        setLayout(new BorderLayout());
        preview=new PreviewPane(id -> activateLink.accept(id), this::previewGeometry);
        previewScroll=new JScrollPane(preview.editor());previewScroll.setBorder(BorderFactory.createEmptyBorder());
        previewScroll.setOpaque(false);previewScroll.getViewport().setOpaque(false);
        split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);split.setOpaque(false);split.setBorder(BorderFactory.createEmptyBorder());split.setResizeWeight(.5);
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,event -> {
            int extent=split.getWidth()-split.getDividerSize();
            if(!restoringDivider && mode==MarkdownMode.SPLIT && extent>0)dividerRatio=Math.max(0,Math.min(1,split.getDividerLocation()/(double)extent));
        });
        addPropertyChangeListener("font",event -> {if(getFont()!=null){preview.bodyFont(getFont());}});
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
        scrollLink = new ScrollLink(this, preview);
        add(scroll, BorderLayout.CENTER);
        addHierarchyListener(hierarchy);
        area.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) { sourceFocused = true; }
        });
        preview.editor().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) { sourceFocused = false; }
        });
    }

    private void previewGeometry() {
        controllerGeometry.run();
        if (scrollLink != null) scrollLink.geometryChanged();
        geometryChanged.run();
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

    void typography(TerminalTypography value) {
        if (closed) return;
        typography = value;
        if (appearance != null && settings != null) refreshAppearance();
        else { area.typography(value); scroll.getGutter().setLineNumberFont(area.getFont()); }
    }

    private void applyFont(Font font) {
        area.setFont(font);
        scroll.getGutter().setLineNumberFont(font);
    }

    private void refreshAppearance() {
        if (!closed && appearance != null && settings != null)
            { apply(settings, theme, appearance.editorPalette(), appearance.syntaxPalette(), appearance.workspaceOpacity(this));
                if(typography!=null)previewStyle(typography,appearance.workspacePalette(),appearance.syntaxPalette()); }
    }

    void apply(EditorSettings settings, EditorTheme theme, EditorPalette editor, SyntaxPalette syntax, double opacity) {
        if (closed) return;
        if (typography == null) applyFont(settings.font());
        else { area.typography(typography); scroll.getGutter().setLineNumberFont(area.getFont()); }
        theme.apply(area, scroll, editor, syntax, opacity);
        area.setTabSize(settings.tabSize());
        glass(opacity, editor.background(), editor.gutter());
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        area.putClientProperty("jasper.editor.closed", Boolean.TRUE);
        scrollLink.close();
        preview.close();
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
        Rectangle sourceBounds = scroll.getParent()==null ? new Rectangle() : SwingUtilities.convertRectangle(scroll.getParent(),scroll.getBounds(),this);
        if(mode==MarkdownMode.PREVIEW) return;
        if (opacity >= 1) { graphics.setColor(background); graphics.fillRect(sourceBounds.x,sourceBounds.y,sourceBounds.width,sourceBounds.height); return; }
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setComposite(AlphaComposite.Src);
            g.setColor(translucent(background));
            g.fillRect(sourceBounds.x,sourceBounds.y,sourceBounds.width,sourceBounds.height);
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
