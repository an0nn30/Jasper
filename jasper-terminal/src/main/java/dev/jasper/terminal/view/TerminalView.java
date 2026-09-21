package dev.jasper.terminal.view;

import dev.jasper.terminal.config.CursorStyle;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.config.Palette;
import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.internal.TerminalAccess;
import dev.jasper.terminal.internal.desktop.DesktopServices;
import dev.jasper.terminal.internal.rendering.CellStyle;
import dev.jasper.terminal.internal.rendering.ScreenSnapshot;
import dev.jasper.terminal.internal.rendering.TerminalPainter;
import dev.jasper.terminal.internal.text.CursorRequest;
import dev.jasper.terminal.internal.text.Selection;
import dev.jasper.terminal.internal.text.TerminalSearch;
import dev.jasper.terminal.rendering.FontSet;
import dev.jasper.terminal.search.FindResult;
import dev.jasper.terminal.search.SearchQuery;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The Swing component that shows a {@link TerminalSession}: painting, keyboard, mouse, selection, scrollback, search
 * and prompt jumps. Construct and access it on the EDT. Removing it cancels presentation
 * work but leaves its session open; the embedding application owns session shutdown.
 */
public final class TerminalView extends JComponent {
    private static final System.Logger LOG = System.getLogger(TerminalView.class.getName());
    private static final float DEFAULT_FONT_SIZE = 14f;
    private static final float MIN_FONT_SIZE = 6f;
    private static final float MAX_FONT_SIZE = 72f;

    private final TerminalSession session;
    private final TerminalAccess access;
    private final SearchController search;
    private final RenderScheduler rendering;
    private final BellController bells;
    private TerminalOptions options;
    private FontSet fonts;
    private Palette palette;
    private TerminalPainter painter;
    private final boolean macOs;
    private final Viewport viewport = new Viewport();
    private Color matchColor;
    private Color currentMatchColor;
    private TerminalSessionListener listener;
    private final SelectionController selection;
    private final KeyboardController keyboard;
    private final MouseController mouseInput;
    private long observedAbsoluteRowEpoch;
    private volatile boolean exited;
    private float fontSize;
    private float inactiveDim;
    private Runnable onCloseRequest = () -> { };
    private Predicate<KeyEvent> shortcutHandler;
    private Consumer<MouseEvent> contextMenuHandler = event -> { };
    private Consumer<FindResult> findResultListener = result -> { };
    private Supplier<String> clipboardReader = DesktopServices::readClipboard;
    private Consumer<String> clipboardWriter = DesktopServices::writeClipboard;
    private Consumer<String> linkOpener = DesktopServices::openBrowser;

    /** Constructs an EDT-owned view without taking ownership of session shutdown; options and session must be nonnull. */
    public TerminalView(TerminalSession session, TerminalOptions options) {
        this.session = session;
        this.access = session.internalAccess();
        this.selection = new SelectionController(access);
        this.search = new SearchController(access::search, access::absoluteRowEpoch,
            row -> viewport.reveal(row, access.snapshot(viewport.topRow())), this::repaint);
        this.observedAbsoluteRowEpoch = access.absoluteRowEpoch();
        this.options = options;
        this.fontSize = options.fontSize();
        this.fonts = new FontSet(options.fontFamily(), fontSize, options.fallbackFonts(), options.ligatures(),
            options.lineHeight());
        this.palette = options.palette();
        this.painter = new TerminalPainter(fonts, palette);
        this.macOs = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
        this.keyboard = new KeyboardController(access, new KeyEncoder(options.optionAsMeta(), macOs),
            this::handleShortcut, () -> exited, () -> onCloseRequest.run(), () -> {
                viewport.follow(); selection.set(null); restartBlink();
            });
        this.mouseInput = new MouseController(access, selection, viewport, macOs,
            () -> fonts.cellWidth(), () -> fonts.cellHeight(), () -> this.options.copyOnSelect(),
            this::requestFocusInWindow, this::copySelection, link -> linkOpener.accept(link),
            event -> contextMenuHandler.accept(event), this::repaint);
        Color yellow = palette.ansi().get(3);
        this.matchColor = CellStyle.blend(yellow, palette.background(), 0.7f);
        this.currentMatchColor = CellStyle.blend(yellow, palette.background(), 0.35f);
        this.rendering = new RenderScheduler(this::reconcileAbsoluteRows, this::repaint, search::cancelPending,
            () -> isFocusOwner() && !exited && access.blinkingCursorInView(viewport.topRow(), this.options.cursorBlink()));
        this.bells = new BellController(() -> this.options.bell(), this::repaint, Toolkit.getDefaultToolkit()::beep);

        setOpaque(true);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setBackground(palette.background());
        enableEvents(AWTEvent.KEY_EVENT_MASK);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) refreshRendering();
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeSessionToFit();
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMouse(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleMouse(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouse(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                handleMouse(e);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                handleMouse(e);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                restartBlink();
            }

            @Override
            public void focusLost(FocusEvent e) {
                keyboard.focusLost();
                // A lost command-click release must not swallow a later gesture. Other buttons keep ownership.
                mouseInput.focusLost();
                reconcileBlink();
                repaint();
            }
        });
        session.exitFuture().thenAccept(code -> {
            exited = true;
            rendering.markDirty(rendering.generation());
        });
    }

    /** Attaches fresh listener/timer generations when Swing realizes this component; EDT only. */
    @Override
    public void addNotify() {
        super.addNotify();
        long generation = rendering.attach();
        bells.attach(generation);
        listener = listenerFor(generation);
        session.addListener(listener);
        reconcileAbsoluteRows();
        rendering.showing(isShowing());
    }

    /** Invalidates presentation work and unregisters its listener; leaves the session open. EDT only. */
    @Override
    public void removeNotify() {
        rendering.detach();
        bells.detach();
        if (listener != null) {
            session.removeListener(listener);
            listener = null;
        }
        search.cancelPending();
        super.removeNotify();
    }

    /** Returns the emulator minimum grid in current font metrics. */
    @Override
    public Dimension getMinimumSize() {
        return new Dimension(GridSize.MIN_COLUMNS * fonts.cellWidth(), GridSize.MIN_ROWS * fonts.cellHeight());
    }

    /** Returns the current session grid in current font metrics. */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(session.columns() * fonts.cellWidth(), session.rows() * fonts.cellHeight());
    }

    /** Pastes text into the program (bracketed when it asked) and returns the view to the live screen. */
    public void paste(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        viewport.follow();
        access.paste(text);
        repaint();
    }

    /** Whether this EDT-owned view has a selected cell range; does not read or lock terminal text. */
    public boolean hasSelection() { return selection.hasSelection(); }

    /** Validates selected live cells and extracts text atomically; returns empty if output overwrote the selection. EDT only. */
    public Optional<String> selectedText() {
        reconcileAbsoluteRows();
        return selection.selectedText();
    }

    /** Copies a still-valid nonempty selection to the clipboard. EDT only. */
    public void copySelection() {
        selectedText().filter(text -> !text.isEmpty()).ifPresent(clipboardWriter);
    }

    /** Pastes the current clipboard contents, if text is available. Call on the Event Dispatch Thread. */
    public void pasteClipboard() {
        paste(clipboardReader.get());
    }

    /** Clears saved history without sending input to the child or changing the live screen. */
    public void clearScrollback() {
        session.clearScrollback();
    }

    /** Installs application key routing; {@code null} restores the standalone shortcuts. */
    public void setShortcutHandler(Predicate<KeyEvent> handler) {
        shortcutHandler = handler;
    }

    /** Installs the popup callback for right-click gestures owned locally by the view. */
    public void setContextMenuHandler(Consumer<MouseEvent> handler) {
        contextMenuHandler = handler == null ? event -> { } : handler;
    }

    /** Receives row-state invalidations on the Event Dispatch Thread. */
    public void setFindResultListener(Consumer<FindResult> listener) {
        findResultListener = listener == null ? result -> { } : listener;
    }

    /** Current appearance and behavior, owned by the Event Dispatch Thread. */
    public TerminalOptions options() { return options; }

    /** Applies live settings on the EDT. Scrollback remains a default for future sessions. */
    public void applyOptions(TerminalOptions next) {
        Objects.requireNonNull(next, "options");
        if (options.equals(next)) {
            return;
        }
        boolean typographyChanged = !options.fontFamily().equals(next.fontFamily())
            || options.fontSize() != next.fontSize()
            || !options.fallbackFonts().equals(next.fallbackFonts())
            || options.ligatures() != next.ligatures()
            || options.lineHeight() != next.lineHeight();
        Dimension previousMinimum = getMinimumSize();
        int previousWidth = fonts.cellWidth();
        int previousHeight = fonts.cellHeight();
        if (typographyChanged) {
            fonts = new FontSet(next.fontFamily(), next.fontSize(), next.fallbackFonts(), next.ligatures(),
                next.lineHeight());
        }
        if (options.optionAsMeta() != next.optionAsMeta()) {
            keyboard.setEncoder(new KeyEncoder(next.optionAsMeta(), macOs));
        }
        if (options.bell() != next.bell()) {
            bells.modeChanged();
        }
        options = next;
        fontSize = next.fontSize();
        if (!palette.equals(next.palette())) {
            updatePalette(next.palette());
        } else if (typographyChanged) {
            painter = new TerminalPainter(fonts, palette);
        }
        if (typographyChanged) {
            firePropertyChange("minimumSize", previousMinimum, getMinimumSize());
            revalidate();
            if ((previousWidth != fonts.cellWidth() || previousHeight != fonts.cellHeight())
                && getWidth() > 0 && getHeight() > 0) {
                resizeSessionToFit();
            }
        }
        reconcileBlink();
        repaint();
    }

    /** The current terminal font size in points. */
    public float fontSize() {
        return fontSize;
    }

    /** The palette currently used to render this view. Event Dispatch Thread owned. */
    public Palette palette() {
        return palette;
    }

    /** Recolors this view without changing its session, fonts, scrollback, selection, search or viewport. */
    public void setPalette(Palette value) {
        Palette next = Objects.requireNonNull(value, "palette");
        if (next.equals(palette)) {
            return;
        }
        applyOptions(new TerminalOptions(options.fontFamily(), fontSize, options.fallbackFonts(), options.ligatures(),
            next, options.cursorStyle(), options.cursorBlink(), options.optionAsMeta(), options.scrollback(),
            options.copyOnSelect(), options.lineHeight(), options.bell()));
    }

    private void updatePalette(Palette next) {
        palette = next;
        painter = new TerminalPainter(fonts, next);
        setBackground(next.background());
        Color yellow = next.ansi().get(3);
        matchColor = CellStyle.blend(yellow, next.background(), 0.7f);
        currentMatchColor = CellStyle.blend(yellow, next.background(), 0.35f);
        repaint();
    }

    /** Changes only this view's font size, clamped to 6–72 points, and refits its existing session. */
    public void setFontSize(float size) {
        float bounded = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size));
        if (!Float.isFinite(bounded) || bounded == fontSize) {
            return;
        }
        applyOptions(new TerminalOptions(options.fontFamily(), bounded, options.fallbackFonts(), options.ligatures(),
            palette, options.cursorStyle(), options.cursorBlink(), options.optionAsMeta(), options.scrollback(),
            options.copyOnSelect(), options.lineHeight(), options.bell()));
    }

    /** Restores the Phase 1 default of 14 points. */
    public void resetFontSize() {
        setFontSize(DEFAULT_FONT_SIZE);
    }

    /** Sets a background overlay for inactive panes: 0 is clear and 1 is fully dimmed. */
    public void setInactiveDim(float amount) {
        float bounded = Float.isFinite(amount) ? Math.max(0f, Math.min(1f, amount)) : 0f;
        if (bounded != inactiveDim) {
            inactiveDim = bounded;
            repaint();
        }
    }

    /** Scrolls so the nearest prompt above the view is at the top. */
    public void scrollToPreviousPrompt() {
        ScreenSnapshot snapshot = access.snapshot(viewport.topRow());
        List<Long> prompts = access.promptRows();
        for (int i = prompts.size() - 1; i >= 0; i--) {
            if (prompts.get(i) < snapshot.firstRow()) {
                viewport.showAtTop(prompts.get(i), snapshot);
                repaint();
                return;
            }
        }
    }

    /** Scrolls so the next prompt below the top of the view is at the top, or back to the live screen. */
    public void scrollToNextPrompt() {
        ScreenSnapshot snapshot = access.snapshot(viewport.topRow());
        for (long prompt : access.promptRows()) {
            if (prompt > snapshot.firstRow()) {
                viewport.showAtTop(prompt, snapshot);
                repaint();
                return;
            }
        }
        viewport.follow();
        repaint();
    }

    /** Called when a key is pressed after the program has exited; the owner closes the view. */
    public void setOnCloseRequest(Runnable action) {
        this.onCloseRequest = action;
    }

    /** Captures rows under the buffer lock, then paints detached runs and overlays on EDT. */
    @Override
    protected void paintComponent(Graphics g) {
        reconcileAbsoluteRows();
        selection.validate();
        ScreenSnapshot snapshot = access.snapshot(viewport.topRow());
        CursorStyle style = CursorRequest.effective(snapshot.cursorShape(), options.cursorStyle());
        boolean blinks = CursorRequest.effectiveBlink(snapshot.cursorShape(), options.cursorBlink());
        boolean focused = isFocusOwner();
        reconcileBlink();
        boolean on = exited || !blinks || !focused || rendering.blinkOn();
        painter.paint((Graphics2D) g, snapshot, new TerminalPainter.CursorLook(style, on, focused),
            highlights(snapshot), getWidth(), getHeight());
        if (bells.visual()) {
            Color foreground = palette.foreground();
            g.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(),
                Math.round(255 * .15f)));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        if (inactiveDim > 0f) {
            Color background = palette.background();
            int alpha = Math.round(255 * inactiveDim);
            g.setColor(new Color(background.getRed(), background.getGreen(), background.getBlue(), alpha));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    /** Routes AWT keys through shortcut precedence and terminal encoding on EDT. */
    @Override
    protected void processKeyEvent(KeyEvent e) {
        handleKey(e);
        if (!e.isConsumed()) {
            super.processKeyEvent(e);
        }
    }

    void resizeSessionToFit() {
        GridSize grid = GridSize.fit(getWidth(), getHeight(), fonts.cellWidth(), fonts.cellHeight());
        boolean widthChanged = grid.columns() != session.columns();
        session.resize(grid.columns(), grid.rows());
        if (widthChanged) {
            forgetAbsoluteRows(); // JediTerm reflows soft-wrapped lines, moving them to other absolute rows
        }
        rendering.markDirty(rendering.generation());
    }

    /**
     * Drops everything that names lines by absolute row (selection, drag anchor, find matches, a scrolled-back view),
     * for when those rows stop naming the same lines. Runs on the Event Dispatch Thread.
     */
    private void forgetAbsoluteRows() {
        observedAbsoluteRowEpoch = access.absoluteRowEpoch();
        selection.clear();
        search.clear();
        viewport.follow();
        repaint();
        notifyFindResultListener(new FindResult(0, 0, null));
    }

    /** Reconciles row-state changes that may have happened while this view had no session listener. */
    private void reconcileAbsoluteRows() {
        if (observedAbsoluteRowEpoch != access.absoluteRowEpoch()) {
            forgetAbsoluteRows();
        }
    }

    private void notifyFindResultListener(FindResult result) {
        if (SwingUtilities.isEventDispatchThread()) {
            findResultListener.accept(result);
        } else {
            SwingUtilities.invokeLater(() -> findResultListener.accept(result));
        }
    }

    void setClipboard(Supplier<String> reader, Consumer<String> writer) {
        this.clipboardReader = reader;
        this.clipboardWriter = writer;
    }

    void setBellSound(Runnable sound) {
        bells.setSound(sound);
    }

    private TerminalSessionListener listenerFor(long generation) {
        return new TerminalSessionListener() {
            @Override public void screenChanged() { rendering.markDirty(generation); }
            @Override public void scrollbackReset() { reconcileRowsLater(generation); }
            @Override public void alternateBufferChanged(boolean alternate) { reconcileRowsLater(generation); }
            @Override public void bell() { bells.signal(generation); }
        };
    }

    void setLinkOpener(Consumer<String> opener) {
        this.linkOpener = opener;
    }

    long topRow() {
        return viewport.topRow();
    }

    boolean exited() {
        return exited;
    }

    /** Application or standalone shortcuts plus page scrolling that always remains owned by the terminal view. */
    private boolean handleShortcut(KeyEvent e) {
        int code = e.getKeyCode();
        if (e.isShiftDown() && code == KeyEvent.VK_PAGE_UP) {
            scrollBy(-(session.rows() - 1));
            return true;
        }
        if (e.isShiftDown() && code == KeyEvent.VK_PAGE_DOWN) {
            scrollBy(session.rows() - 1);
            return true;
        }
        return shortcutHandler != null ? shortcutHandler.test(e) : handleViewShortcut(e);
    }

    /** Copy, paste and prompt jumps for a TerminalView with no application owner. */
    private boolean handleViewShortcut(KeyEvent e) {
        int code = e.getKeyCode();
        boolean primary = macOs ? e.isMetaDown() : e.isControlDown() && e.isShiftDown();
        if (primary && code == KeyEvent.VK_C) {
            copySelection();
            return true;
        }
        if (primary && code == KeyEvent.VK_V) {
            pasteClipboard();
            return true;
        }
        if (primary && code == KeyEvent.VK_UP) {
            scrollToPreviousPrompt();
            return true;
        }
        if (primary && code == KeyEvent.VK_DOWN) {
            scrollToNextPrompt();
            return true;
        }
        return false;
    }

    private List<TerminalPainter.Highlight> highlights(ScreenSnapshot snapshot) {
        List<TerminalPainter.Highlight> highlights = new ArrayList<>();
        long first = snapshot.firstRow();
        long last = first + snapshot.height() - 1;
        // Search results are sorted by row and column; skip the offscreen prefix in logarithmic time.
        int low = 0;
        int high = search.matches().size();
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (search.matches().get(middle).row() < first) low = middle + 1; else high = middle;
        }
        for (int i = low; i < search.matches().size(); i++) {
            TerminalSearch.Match match = search.matches().get(i);
            if (match.row() > last) break;
            highlights.add(new TerminalPainter.Highlight((int) (match.row() - first), match.startColumn(),
                match.endColumn(), i == search.currentIndex() ? currentMatchColor : matchColor));
        }
        Selection range = selection.range();
        if (range != null) {
            for (int row = 0; row < snapshot.height(); row++) {
                int[] columns = range.columnsOn(first + row, snapshot.width());
                if (columns != null) {
                    highlights.add(new TerminalPainter.Highlight(row, columns[0], columns[1],
                        palette.selection()));
                }
            }
        }
        return highlights;
    }

    private void scrollBy(int lines) {
        viewport.scrollBy(lines, access.snapshot(viewport.topRow()));
        repaint();
    }

    /** Finds matches synchronously on EDT; prefer findAsync for long histories. Matches do not span soft wraps. */
    public FindResult find(SearchQuery query) { return search.find(query); }
    /**
     * Starts a search on EDT, matches captured rows on the bounded worker, and publishes on EDT.
     * Only the latest query in the same row epoch may publish; clearing, hiding or detaching
     * cancels pending callbacks. A null callback is allowed. Matches do not span soft wraps.
     */
    public void findAsync(SearchQuery query, Consumer<FindResult> callback) { search.findAsync(query, callback); }

    /** Selects the next newer match, wrapping around; call on EDT. */
    public FindResult findNext() { return search.next(); }
    /** Selects the next older match, wrapping around; call on EDT. */
    public FindResult findPrevious() { return search.previous(); }
    /** Cancels pending publication and clears current highlights on EDT. */
    public void clearFind() { search.clear(); }
    void handleKey(KeyEvent event) { keyboard.handle(event); }
    void handleMouse(MouseEvent event) { mouseInput.handle(event); }
    /** Executes a reusable command synchronously. Requires the Event Dispatch Thread. */
    public void execute(TerminalAction action) {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Terminal actions require the EDT");
        switch (java.util.Objects.requireNonNull(action,"action")) {
            case COPY_SELECTION -> copySelection();
            case PASTE_CLIPBOARD -> pasteClipboard();
            case CLEAR_SCROLLBACK -> clearScrollback();
            case FIND_NEXT -> findNext();
            case FIND_PREVIOUS -> findPrevious();
            case PREVIOUS_PROMPT -> scrollToPreviousPrompt();
            case NEXT_PROMPT -> scrollToNextPrompt();
        }
    }

    private void reconcileRowsLater(long generation) {
        SwingUtilities.invokeLater(() -> {
            if (generation == rendering.generation()) reconcileAbsoluteRows();
        });
    }
    private void restartBlink() { rendering.restartBlink(); }
    private void reconcileBlink() { rendering.reconcileBlink(); }
    private void refreshRendering() { rendering.showing(isShowing()); }
}
