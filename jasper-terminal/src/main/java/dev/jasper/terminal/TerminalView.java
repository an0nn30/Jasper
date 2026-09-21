package dev.jasper.terminal;

import dev.jasper.terminal.MouseInput.Type;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.PatternSyntaxException;

/**
 * The Swing component that shows a {@link TerminalSession}: painting, keyboard, mouse, selection, scrollback, search
 * and prompt jumps.
 */
public final class TerminalView extends JComponent {
    private static final System.Logger LOG = System.getLogger(TerminalView.class.getName());
    private static final int FRAME_MILLIS = 8;
    private static final int BLINK_MILLIS = 530;
    private static final int BELL_MILLIS = 150;
    private static final int WHEEL_LINES = 3;
    private static final long SEARCH_IDLE_MILLIS = 1_000;
    private static final float DEFAULT_FONT_SIZE = 14f;
    private static final float MIN_FONT_SIZE = 6f;
    private static final float MAX_FONT_SIZE = 72f;

    private final TerminalSession session;
    private final TerminalAccess access;
    private TerminalOptions options;
    private FontSet fonts;
    private Palette palette;
    private TerminalPainter painter;
    private KeyEncoder keys;
    private final boolean macOs;
    private final Viewport viewport = new Viewport();
    private Color matchColor;
    private Color currentMatchColor;
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private volatile AtomicBoolean pendingFrame = new AtomicBoolean();
    private volatile boolean renderingActive;
    private final Timer frameTimer;
    private final Timer blinkTimer;
    private final Timer bellTimer;
    private TerminalSessionListener listener;
    private volatile long attachmentGeneration;
    /** A fresh coalescing token on attachment or mode change also invalidates queued deliveries. */
    private volatile AtomicBoolean pendingBell;
    private boolean visualBell;
    private Runnable bellSound = Toolkit.getDefaultToolkit()::beep;
    private boolean blinkOn = true;
    private boolean suppressNextTyped;
    private boolean leftAltHeld;
    private boolean rightAltHeld;
    private Selection selection;
    /** Where a click-drag started; becomes the selection on the first drag. */
    private Selection pendingAnchor;
    private Selection wordAnchor;
    private List<SelectedCells> selectedLiveCells = List.of();
    /** Press ownership and report modifiers survive until that button's matching release. */
    private final java.util.EnumMap<MouseInput.Button, Gesture> gestures =
        new java.util.EnumMap<>(MouseInput.Button.class);
    private long gestureSequence;
    private double wheelRemainder;

    private static final class Gesture {
        final MouseRouting.Action action;
        final boolean shift, alt, control;
        final long sequence;
        boolean popupShown;

        Gesture(MouseRouting.Action action, boolean shift, boolean alt, boolean control, long sequence) {
            this.action = action;
            this.shift = shift; this.alt = alt; this.control = control;
            this.sequence = sequence;
        }
    }
    private List<TerminalSearch.Match> matches = List.of();
    private int currentMatch = -1;
    private final Object searchLock = new Object();
    private long searchGeneration;
    private long observedAbsoluteRowEpoch;
    private ThreadPoolExecutor searchExecutor;
    private Future<?> pendingSearch;
    private volatile boolean exited;
    private float fontSize;
    private float inactiveDim;
    private Runnable onCloseRequest = () -> { };
    private Predicate<KeyEvent> shortcutHandler;
    private Consumer<MouseEvent> contextMenuHandler = event -> { };
    private Consumer<FindResult> findResultListener = result -> { };
    private Supplier<String> clipboardReader = TerminalView::readSystemClipboard;
    private Consumer<String> clipboardWriter = TerminalView::writeSystemClipboard;
    private Consumer<String> linkOpener = TerminalView::openInBrowser;

    public TerminalView(TerminalSession session, TerminalOptions options) {
        this.session = session;
        this.access = session.internalAccess();
        this.observedAbsoluteRowEpoch = access.absoluteRowEpoch();
        this.options = options;
        this.fontSize = options.fontSize();
        this.fonts = new FontSet(options.fontFamily(), fontSize, options.fallbackFonts(), options.ligatures(),
            options.lineHeight());
        this.palette = options.palette();
        this.painter = new TerminalPainter(fonts, palette);
        this.macOs = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
        this.keys = new KeyEncoder(options.optionAsMeta(), macOs);
        Color yellow = palette.ansi().get(3);
        this.matchColor = CellStyle.blend(yellow, palette.background(), 0.7f);
        this.currentMatchColor = CellStyle.blend(yellow, palette.background(), 0.35f);
        this.frameTimer = new Timer(FRAME_MILLIS, e -> frameTimerFinished());
        this.frameTimer.setRepeats(false);
        this.blinkTimer = new Timer(BLINK_MILLIS, e -> {
            reconcileBlink();
            if (((Timer) e.getSource()).isRunning()) {
                blinkOn = !blinkOn;
                repaint();
            }
        });

        this.bellTimer = new Timer(BELL_MILLIS, e -> clearVisualBell());
        this.bellTimer.setRepeats(false);

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
                leftAltHeld = false;
                rightAltHeld = false;
                // A lost command-click release must not swallow a later gesture. Other buttons keep ownership.
                gestures.entrySet().removeIf(entry -> entry.getValue().action == MouseRouting.Action.OPEN_LINK);
                reconcileBlink();
                repaint();
            }
        });
        session.exitFuture().thenAccept(code -> {
            exited = true;
            markDirty(attachmentGeneration);
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        pendingBell = new AtomicBoolean();
        listener = listenerFor(++attachmentGeneration);
        session.addListener(listener);
        reconcileAbsoluteRows();
        refreshRendering();
    }

    @Override
    public void removeNotify() {
        ++attachmentGeneration;
        renderingActive = false;
        pendingFrame = new AtomicBoolean();
        pendingBell = null;
        clearVisualBell();
        frameTimer.stop();
        blinkTimer.stop();
        if (listener != null) {
            session.removeListener(listener);
            listener = null;
        }
        invalidatePendingSearch();
        super.removeNotify();
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(GridSize.MIN_COLUMNS * fonts.cellWidth(), GridSize.MIN_ROWS * fonts.cellHeight());
    }

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
    public boolean hasSelection() { return selection != null; }

    public Optional<String> selectedText() {
        reconcileAbsoluteRows();
        if (selection == null) return Optional.empty();
        Optional<String> text = access.selectedText(selection, selectedLiveCells);
        if (text.isEmpty()) setSelection(null);
        return text;
    }

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
            keys = new KeyEncoder(next.optionAsMeta(), macOs);
        }
        if (options.bell() != next.bell()) {
            pendingBell = listener == null ? null : new AtomicBoolean();
            clearVisualBell();
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

    /** Finds matches in the scrollback and screen and shows the newest one. */
    public FindResult find(String query, boolean regex, boolean caseSensitive) {
        invalidatePendingSearch();
        try {
            matches = access.search(new SearchQuery(query, regex, caseSensitive));
        } catch (PatternSyntaxException invalid) {
            matches = List.of();
            currentMatch = -1;
            repaint();
            return new FindResult(0, 0, invalid.getDescription());
        }
        currentMatch = matches.size() - 1;
        revealCurrentMatch();
        return findResult();
    }

    /**
     * Searches away from the Event Dispatch Thread. At most one running and one queued request are retained; only the
     * latest generation may change highlights or invoke its callback, and that callback always runs on the EDT.
     */
    public void findAsync(String query, boolean regex, boolean caseSensitive, Consumer<FindResult> callback) {
        Consumer<FindResult> completion = callback == null ? result -> { } : callback;
        long generation;
        ThreadPoolExecutor executor;
        synchronized (searchLock) {
            generation = ++searchGeneration;
            if (pendingSearch != null) {
                pendingSearch.cancel(true);
            }
            executor = searchExecutor();
            executor.getQueue().clear();
            pendingSearch = executor.submit(() -> calculateFind(generation, query, regex, caseSensitive, completion));
        }
    }

    /** Moves to the next newer match, wrapping around. */
    public FindResult findNext() {
        return stepMatch(1);
    }

    /** Moves to the next older match, wrapping around. */
    public FindResult findPrevious() {
        return stepMatch(-1);
    }

    public void clearFind() {
        invalidatePendingSearch();
        matches = List.of();
        currentMatch = -1;
        repaint();
    }

    private void calculateFind(long generation, String query, boolean regex, boolean caseSensitive,
                               Consumer<FindResult> callback) {
        List<TerminalSearch.Match> found;
        FindResult result;
        try {
            found = access.search(new SearchQuery(query, regex, caseSensitive));
            result = new FindResult(found.size(), found.size(), null);
        } catch (PatternSyntaxException invalid) {
            found = List.of();
            result = new FindResult(0, 0, invalid.getDescription());
        }
        List<TerminalSearch.Match> completedMatches = found;
        FindResult completedResult = result;
        SwingUtilities.invokeLater(() -> applyFind(generation, completedMatches, completedResult, callback));
    }

    private void applyFind(long generation, List<TerminalSearch.Match> found, FindResult result,
                           Consumer<FindResult> callback) {
        synchronized (searchLock) {
            if (generation != searchGeneration) {
                return;
            }
            pendingSearch = null;
        }
        matches = found;
        currentMatch = found.size() - 1;
        revealCurrentMatch();
        callback.accept(result);
    }

    private ThreadPoolExecutor searchExecutor() {
        if (searchExecutor == null || searchExecutor.isShutdown()) {
            searchExecutor = new ThreadPoolExecutor(1, 1, SEARCH_IDLE_MILLIS, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), runnable -> {
                    Thread thread = new Thread(runnable, "jasper-terminal-search");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.DiscardOldestPolicy());
            searchExecutor.allowCoreThreadTimeOut(true);
        }
        return searchExecutor;
    }

    private void invalidatePendingSearch() {
        synchronized (searchLock) {
            searchGeneration++;
            if (pendingSearch != null) {
                pendingSearch.cancel(true);
                pendingSearch = null;
            }
            if (searchExecutor != null) {
                searchExecutor.getQueue().clear();
            }
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

    @Override
    protected void paintComponent(Graphics g) {
        reconcileAbsoluteRows();
        if (selection != null && !access.selectionUnchanged(selectedLiveCells)) setSelection(null);
        ScreenSnapshot snapshot = access.snapshot(viewport.topRow());
        CursorStyle style = CursorRequest.effective(snapshot.cursorShape(), options.cursorStyle());
        boolean blinks = CursorRequest.effectiveBlink(snapshot.cursorShape(), options.cursorBlink());
        boolean focused = isFocusOwner();
        reconcileBlink();
        boolean on = exited || !blinks || !focused || blinkOn;
        painter.paint((Graphics2D) g, snapshot, new TerminalPainter.CursorLook(style, on, focused),
            highlights(snapshot), getWidth(), getHeight());
        if (visualBell) {
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

    @Override
    protected void processKeyEvent(KeyEvent e) {
        handleKey(e);
        if (!e.isConsumed()) {
            super.processKeyEvent(e);
        }
    }

    void handleKey(KeyEvent e) {
        trackAltKeys(e);
        if (e.getID() == KeyEvent.KEY_PRESSED && handleShortcut(e)) {
            suppressNextTyped = true;
            e.consume();
            return;
        }
        if (exited) {
            if (e.getID() == KeyEvent.KEY_PRESSED && !isModifierOnly(e.getKeyCode())) {
                onCloseRequest.run();
            }
            e.consume();
            return;
        }
        KeyInput input = new KeyInput(e.getKeyCode(), e.getKeyChar(), e.getModifiersEx(), leftAltHeld, rightAltHeld);
        byte[] bytes = switch (e.getID()) {
            case KeyEvent.KEY_PRESSED -> {
                byte[] pressed = keys.pressed(input, session::codeForKey);
                suppressNextTyped = pressed != null; // re-decided on every press
                yield pressed;
            }
            case KeyEvent.KEY_TYPED -> {
                if (suppressNextTyped) {
                    suppressNextTyped = false;
                    e.consume();
                    yield null;
                }
                yield keys.typed(input);
            }
            default -> null;
        };
        if (bytes != null) {
            session.write(bytes);
            viewport.follow();
            setSelection(null);
            restartBlink();
            e.consume();
        }
    }

    void handleMouse(MouseEvent e) {
        Type type = typeOf(e);
        if (type == null || (type == Type.MOVED && !access.mouseReporting())) return;
        if (macOs && type == Type.WHEEL && e.isShiftDown()) return;
        int notches = type == Type.WHEEL ? notches((MouseWheelEvent) e) : 0;
        if (type == Type.WHEEL && notches == 0) return;

        MouseGeometry grid = access.mouseGeometry(viewport.topRow());
        int column = Math.max(0, Math.min(grid.width() - 1, e.getX() / fonts.cellWidth()));
        int row = Math.max(0, Math.min(grid.height() - 1, e.getY() / fonts.cellHeight()));
        long absoluteRow = grid.firstRow() + row;
        MouseInput.Button button = gestureButton(e, type);
        Gesture gesture = gestures.get(button);
        MouseRouting.Action action;
        if (type == Type.PRESSED) {
            requestFocusInWindow();
            boolean linkModifier = macOs ? e.isMetaDown() : e.isControlDown();
            action = MouseRouting.decide(type, button, e.getClickCount(), e.isShiftDown(),
                linkModifier, access.mouseReporting(), grid.alternateBuffer());
            // Command-click is deliberately local on macOS, even if the program requests reports.
            if (button == MouseInput.Button.LEFT && ((macOs && linkModifier)
                || action == MouseRouting.Action.OPEN_LINK)) {
                Optional<String> link = access.linkAt(absoluteRow, column);
                if (link.isPresent()) {
                    linkOpener.accept(link.get());
                    action = MouseRouting.Action.OPEN_LINK;
                } else {
                    action = MouseRouting.Action.START_SELECTION;
                }
            }
            gesture = new Gesture(action, e.isShiftDown(), e.isAltDown(), e.isControlDown(), ++gestureSequence);
            if (button != MouseInput.Button.NONE) gestures.put(button, gesture);
        } else if (type == Type.DRAGGED || type == Type.RELEASED) {
            action = gesture == null
                ? (access.mouseReporting() && !e.isShiftDown() ? MouseRouting.Action.REPORT : MouseRouting.Action.NONE)
                : switch (gesture.action) {
                case REPORT -> MouseRouting.Action.REPORT;
                case START_SELECTION, SELECT_WORD, SELECT_LINE -> type == Type.DRAGGED
                    ? MouseRouting.Action.EXTEND_SELECTION : MouseRouting.Action.END_SELECTION;
                default -> MouseRouting.Action.NONE;
            };
        } else {
            action = MouseRouting.decide(type, button, e.getClickCount(), e.isShiftDown(), false,
                access.mouseReporting(), grid.alternateBuffer());
        }

        if (action == MouseRouting.Action.REPORT) {
            int screenRow = row - grid.scrollOffset();
            if (screenRow >= 0) {
                boolean owned = gesture != null && type != Type.WHEEL && type != Type.MOVED;
                MouseInput input = new MouseInput(type, button,
                    owned ? gesture.shift : e.isShiftDown(), owned ? gesture.alt : e.isAltDown(),
                    owned ? gesture.control : e.isControlDown(), notches);
                int count = type == Type.WHEEL ? Math.abs(notches) : 1;
                for (int i = 0; i < count; i++) {
                    access.reportMouse(column, screenRow, input);
                }
            }
            if (type == Type.RELEASED) gestures.remove(button);
            return; // Reports need no copied screen content and no repaint.
        }
        switch (action) {
            case START_SELECTION -> {
                setSelection(null);
                wordAnchor = null;
                pendingAnchor = Selection.at(absoluteRow, column, e.isAltDown());
            }
            case SELECT_WORD -> {
                wordAnchor = access.wordSelection(absoluteRow, column);
                setSelection(wordAnchor);
                pendingAnchor = null;
            }
            case SELECT_LINE -> {
                wordAnchor = null;
                setSelection(access.lineSelection(absoluteRow));
                pendingAnchor = null;
            }
            case EXTEND_SELECTION -> {
                Selection next = selection == null ? pendingAnchor : selection;
                if (wordAnchor != null) {
                    Selection word = access.wordSelection(absoluteRow, column);
                    boolean before = word.startRow() < wordAnchor.startRow()
                        || (word.startRow() == wordAnchor.startRow() && word.startColumn() < wordAnchor.startColumn());
                    next = before
                        ? new Selection(wordAnchor.endRow(), wordAnchor.endColumn(), word.startRow(), word.startColumn(), false)
                        : new Selection(wordAnchor.startRow(), wordAnchor.startColumn(), word.endRow(), word.endColumn(), false);
                } else if (next != null) {
                    next = next.withFocus(absoluteRow, column);
                }
                setSelection(next);
            }
            case END_SELECTION -> {
                pendingAnchor = null;
                wordAnchor = null;
                if (options.copyOnSelect() && selection != null) copySelection();
            }
            case SCROLL_VIEW -> scrollBy(notches * WHEEL_LINES);
            case SEND_ARROWS -> sendArrows(notches);
            default -> { }
        }
        if (button == MouseInput.Button.RIGHT && gesture != null && e.isPopupTrigger() && !gesture.popupShown) {
            gesture.popupShown = true;
            contextMenuHandler.accept(e);
        }
        if (type == Type.RELEASED) gestures.remove(button);
        if (action != MouseRouting.Action.NONE && action != MouseRouting.Action.OPEN_LINK) repaint();
    }

    private void setSelection(Selection next) {
        selection = next;
        selectedLiveCells = next == null ? List.of() : access.selectedLiveCells(next);
    }

    /** AWT drag events usually have NOBUTTON; prefer the latest owned button still held. */
    private MouseInput.Button gestureButton(MouseEvent event, Type type) {
        if (type != Type.DRAGGED || event.getButton() != MouseEvent.NOBUTTON) return buttonOf(event);
        MouseInput.Button result = MouseInput.Button.NONE;
        long newest = -1;
        int held = event.getModifiersEx() & (InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK
            | InputEvent.BUTTON3_DOWN_MASK);
        for (var entry : gestures.entrySet()) {
            int mask = switch (entry.getKey()) {
                case LEFT -> InputEvent.BUTTON1_DOWN_MASK;
                case MIDDLE -> InputEvent.BUTTON2_DOWN_MASK;
                case RIGHT -> InputEvent.BUTTON3_DOWN_MASK;
                case NONE -> 0;
            };
            if ((held == 0 || (held & mask) != 0) && entry.getValue().sequence > newest) {
                result = entry.getKey();
                newest = entry.getValue().sequence;
            }
        }
        return result;
    }

    void resizeSessionToFit() {
        GridSize grid = GridSize.fit(getWidth(), getHeight(), fonts.cellWidth(), fonts.cellHeight());
        boolean widthChanged = grid.columns() != session.columns();
        session.resize(grid.columns(), grid.rows());
        if (widthChanged) {
            forgetAbsoluteRows(); // JediTerm reflows soft-wrapped lines, moving them to other absolute rows
        }
        markDirty(attachmentGeneration);
    }

    /**
     * Drops everything that names lines by absolute row (selection, drag anchor, find matches, a scrolled-back view),
     * for when those rows stop naming the same lines. Runs on the Event Dispatch Thread.
     */
    private void forgetAbsoluteRows() {
        observedAbsoluteRowEpoch = access.absoluteRowEpoch();
        invalidatePendingSearch();
        setSelection(null);
        pendingAnchor = null;
        wordAnchor = null;
        matches = List.of();
        currentMatch = -1;
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
        bellSound = Objects.requireNonNull(sound, "sound");
    }

    private TerminalSessionListener listenerFor(long generation) {
        return new TerminalSessionListener() {
            @Override
            public void screenChanged() {
                markDirty(generation);
            }

            @Override
            public void scrollbackReset() {
                SwingUtilities.invokeLater(() -> {
                    if (generation == attachmentGeneration) reconcileAbsoluteRows();
                });
            }

            @Override
            public void alternateBufferChanged(boolean alternate) {
                SwingUtilities.invokeLater(() -> {
                    if (generation == attachmentGeneration) reconcileAbsoluteRows();
                });
            }

            @Override
            public void bell() {
                AtomicBoolean pending = pendingBell;
                if (generation != attachmentGeneration || pending == null || !pending.compareAndSet(false, true)) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    pending.set(false);
                    if (generation == attachmentGeneration && pending == pendingBell) {
                        ringBell();
                    }
                });
            }
        };
    }

    private void ringBell() {
        switch (options.bell()) {
            case VISUAL -> {
                visualBell = true;
                bellTimer.restart();
                repaint();
            }
            case SOUND -> bellSound.run();
            case NONE -> { }
        }
    }

    private void clearVisualBell() {
        bellTimer.stop();
        if (visualBell) {
            visualBell = false;
            repaint();
        }
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
        int high = matches.size();
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (matches.get(middle).row() < first) low = middle + 1; else high = middle;
        }
        for (int i = low; i < matches.size(); i++) {
            TerminalSearch.Match match = matches.get(i);
            if (match.row() > last) break;
            highlights.add(new TerminalPainter.Highlight((int) (match.row() - first), match.startColumn(),
                match.endColumn(), i == currentMatch ? currentMatchColor : matchColor));
        }
        if (selection != null) {
            for (int row = 0; row < snapshot.height(); row++) {
                int[] columns = selection.columnsOn(first + row, snapshot.width());
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

    private void sendArrows(int rotation) {
        byte[] arrow = access.codeForKey(rotation < 0 ? KeyEvent.VK_UP : KeyEvent.VK_DOWN, 0);
        if (arrow == null) {
            return;
        }
        for (int i = 0; i < Math.abs(rotation); i++) {
            session.write(arrow);
        }
    }

    private FindResult stepMatch(int direction) {
        if (matches.isEmpty()) {
            return findResult();
        }
        currentMatch = Math.floorMod(currentMatch + direction, matches.size());
        revealCurrentMatch();
        return findResult();
    }

    private void revealCurrentMatch() {
        if (currentMatch >= 0) {
            viewport.reveal(matches.get(currentMatch).row(), access.snapshot(viewport.topRow()));
        }
        repaint();
    }

    private FindResult findResult() {
        return new FindResult(matches.size(), currentMatch + 1, null);
    }

    private void trackAltKeys(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.VK_ALT) {
            return;
        }
        boolean down = e.getID() == KeyEvent.KEY_PRESSED;
        if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT) {
            rightAltHeld = down;
        } else {
            leftAltHeld = down;
        }
    }

    private void restartBlink() {
        blinkOn = true;
        reconcileBlink();
        if (blinkTimer.isRunning()) blinkTimer.restart();
        repaint();
    }

    /** Reader-thread calls retain one dirty bit and at most one queued EDT delivery per attachment. */
    private void markDirty(long generation) {
        // Capture before validation: an old callback must never claim a newer attachment's token.
        AtomicBoolean pending = pendingFrame;
        if (generation != attachmentGeneration) return;
        publishDirty(generation, pending);
    }

    /** An already validated reader request may resume here after its attachment has been replaced. */
    private void publishDirty(long generation, AtomicBoolean pending) {
        dirty.set(true);
        if (!renderingActive || !pending.compareAndSet(false, true)) return;
        SwingUtilities.invokeLater(() -> {
            if (generation == attachmentGeneration && pending == pendingFrame && renderingActive) {
                frameTimer.start();
            }
        });
    }

    private void frameTimerFinished() {
        frameTimer.stop();
        pendingFrame.set(false);
        if (!renderingActive) return;
        if (dirty.getAndSet(false)) {
            reconcileAbsoluteRows();
            reconcileBlink();
            repaint();
        }
    }

    /** Hidden tabs keep session/application metadata flowing, but schedule no view frames or cursor ticks. */
    private void refreshRendering() {
        renderingActive = listener != null && isShowing();
        if (renderingActive) {
            markDirty(attachmentGeneration);
        } else {
            invalidatePendingSearch();
            pendingFrame = new AtomicBoolean();
            frameTimer.stop();
        }
        reconcileBlink();
    }

    private void reconcileBlink() {
        boolean eligible = renderingActive && isFocusOwner() && !exited
            && access.blinkingCursorInView(viewport.topRow(), options.cursorBlink());
        if (eligible) blinkTimer.start(); else blinkTimer.stop();
    }

    /**
     * Accumulates a wheel event's precise rotation and returns the whole notches it now adds up to (rounded toward
     * zero), keeping the fractional remainder for the next event. Trackpads deliver many events whose precise
     * rotation is well under one notch, and some events report an integer rotation of 0 despite a nonzero precise
     * value, so acting on {@code getWheelRotation()} directly would under- or over-react.
     */
    private int notches(MouseWheelEvent e) {
        wheelRemainder += e.getPreciseWheelRotation();
        int whole = (int) wheelRemainder;
        wheelRemainder -= whole;
        return whole;
    }

    private static boolean isModifierOnly(int keyCode) {
        return keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_CONTROL || keyCode == KeyEvent.VK_ALT
            || keyCode == KeyEvent.VK_ALT_GRAPH || keyCode == KeyEvent.VK_META;
    }

    private static Type typeOf(MouseEvent e) {
        return switch (e.getID()) {
            case MouseEvent.MOUSE_PRESSED -> Type.PRESSED;
            case MouseEvent.MOUSE_RELEASED -> Type.RELEASED;
            case MouseEvent.MOUSE_DRAGGED -> Type.DRAGGED;
            case MouseEvent.MOUSE_MOVED -> Type.MOVED;
            case MouseEvent.MOUSE_WHEEL -> Type.WHEEL;
            default -> null;
        };
    }

    private static MouseInput.Button buttonOf(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            return MouseInput.Button.LEFT;
        }
        if (SwingUtilities.isMiddleMouseButton(e)) {
            return MouseInput.Button.MIDDLE;
        }
        if (SwingUtilities.isRightMouseButton(e)) {
            return MouseInput.Button.RIGHT;
        }
        return MouseInput.Button.NONE;
    }

    private static String readSystemClipboard() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (UnsupportedFlavorException | IOException | IllegalStateException | HeadlessException e) {
            LOG.log(System.Logger.Level.WARNING, "Clipboard read failed", e);
            return null;
        }
    }

    private static void writeSystemClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (IllegalStateException | HeadlessException e) {
            LOG.log(System.Logger.Level.WARNING, "Clipboard write failed", e);
        }
    }

    private static void openInBrowser(String uri) {
        dispatchBrowserAction(() -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(new URI(uri));
                } else {
                    LOG.log(System.Logger.Level.WARNING, "Browser opening is unsupported");
                }
            } catch (IOException | URISyntaxException failure) {
                // Desktop exceptions can contain the URI. Keep diagnostics fixed and free of terminal content.
                LOG.log(System.Logger.Level.WARNING, "Browser open failed");
            }
        });
    }

    private static void dispatchBrowserAction(Runnable action) {
        try {
            BrowserWorker.EXECUTOR.execute(() -> {
                try {
                    action.run();
                } catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.WARNING, "Browser open failed");
                }
            });
        } catch (RejectedExecutionException busy) {
            LOG.log(System.Logger.Level.WARNING, "Browser request dropped: pending request limit reached");
        }
    }

    /** Shared across views, lazily created, and never falls back to running Desktop calls on the EDT. */
    private static final class BrowserWorker {
        static final ThreadPoolExecutor EXECUTOR = createExecutor();

        private static ThreadPoolExecutor createExecutor() {
            var executor = new ThreadPoolExecutor(1, 1, 1_000, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(8), action -> {
                    var thread = new Thread(action, "jasper-terminal-browser");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
            executor.allowCoreThreadTimeOut(true);
            return executor;
        }
    }
}
