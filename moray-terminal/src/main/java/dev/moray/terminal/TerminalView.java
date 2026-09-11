package dev.moray.terminal;

import com.jediterm.core.input.MouseEvent.Type;
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes;
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags;

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
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
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
    private static final int FRAME_MILLIS = 8;
    private static final int BLINK_MILLIS = 530;
    private static final int WHEEL_LINES = 3;
    private static final long SEARCH_IDLE_MILLIS = 1_000;
    private static final float DEFAULT_FONT_SIZE = 14f;
    private static final float MIN_FONT_SIZE = 6f;
    private static final float MAX_FONT_SIZE = 72f;

    private final TerminalSession session;
    private final TerminalOptions options;
    private FontSet fonts;
    private TerminalPainter painter;
    private final KeyEncoder keys;
    private final boolean macOs;
    private final Viewport viewport = new Viewport();
    private final Color matchColor;
    private final Color currentMatchColor;
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private final Timer frameTimer;
    private final Timer blinkTimer;
    private final TerminalSession.Listener listener = new TerminalSession.Listener() {
        @Override
        public void screenChanged() {
            dirty.set(true);
        }

        @Override
        public void scrollbackReset() {
            SwingUtilities.invokeLater(TerminalView.this::reconcileAbsoluteRows);
        }

        @Override
        public void alternateBufferChanged(boolean alternate) {
            SwingUtilities.invokeLater(TerminalView.this::reconcileAbsoluteRows);
        }
    };
    private boolean blinkOn = true;
    private boolean suppressNextTyped;
    private boolean leftAltHeld;
    private boolean rightAltHeld;
    private Selection selection;
    /** Where a click-drag started; becomes the selection on the first drag. */
    private Selection pendingAnchor;
    /**
     * Whether the mouse button currently held down started a local selection (as opposed to being reported to the
     * program). A gesture that starts locally stays local for its whole drag/release, even if a later event in the
     * same gesture no longer carries the Shift modifier that opted it out of reporting in the first place — AWT does
     * not guarantee a MOUSE_RELEASED carries the same modifiers as the MOUSE_PRESSED that started the drag.
     */
    private boolean capturingSelection;
    /** Fractional wheel rotation carried over between events until it adds up to a whole notch. */
    private double wheelRemainder;
    /** A ⌘-press on macOS opened a link; its drag and release are swallowed rather than reported to the program. */
    private boolean openingLink;
    /** A right-click press that stayed local, retained because popup-trigger modifiers may differ on release. */
    private boolean localPopupGesture;
    private boolean popupShown;
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
        this.observedAbsoluteRowEpoch = session.absoluteRowEpoch();
        this.options = options;
        this.fontSize = options.fontSize();
        this.fonts = new FontSet(options.fontFamily(), fontSize, options.fallbackFonts(), options.ligatures());
        this.painter = new TerminalPainter(fonts, options.palette());
        this.macOs = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
        this.keys = new KeyEncoder(options.optionAsMeta(), macOs);
        Color yellow = options.palette().ansi().get(3);
        this.matchColor = CellStyle.blend(yellow, options.palette().background(), 0.7f);
        this.currentMatchColor = CellStyle.blend(yellow, options.palette().background(), 0.35f);
        this.frameTimer = new Timer(FRAME_MILLIS, e -> {
            if (dirty.getAndSet(false)) {
                repaint();
            }
        });
        this.blinkTimer = new Timer(BLINK_MILLIS, e -> {
            blinkOn = !blinkOn;
            repaint();
        });

        setOpaque(true);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setBackground(options.palette().background());
        enableEvents(AWTEvent.KEY_EVENT_MASK);
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
                openingLink = false;
                localPopupGesture = false;
                popupShown = false;
                repaint();
            }
        });
        session.exitFuture().thenAccept(code -> {
            exited = true;
            dirty.set(true);
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        session.addListener(listener);
        reconcileAbsoluteRows();
        frameTimer.start();
        blinkTimer.start();
    }

    @Override
    public void removeNotify() {
        frameTimer.stop();
        blinkTimer.stop();
        session.removeListener(listener);
        invalidatePendingSearch();
        super.removeNotify();
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
        session.paste(text);
        repaint();
    }

    public Optional<String> selectedText() {
        return selection == null ? Optional.empty() : Optional.of(session.text(selection));
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

    /** The current terminal font size in points. */
    public float fontSize() {
        return fontSize;
    }

    /** Changes only this view's font size, clamped to 6–72 points, and refits its existing session. */
    public void setFontSize(float size) {
        float bounded = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size));
        if (!Float.isFinite(bounded) || bounded == fontSize) {
            return;
        }
        fontSize = bounded;
        fonts = new FontSet(options.fontFamily(), fontSize, options.fallbackFonts(), options.ligatures());
        painter = new TerminalPainter(fonts, options.palette());
        revalidate();
        if (getWidth() > 0 && getHeight() > 0) {
            resizeSessionToFit();
        }
        repaint();
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
            matches = session.search(query, regex, caseSensitive);
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
            found = session.search(query, regex, caseSensitive);
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
                    Thread thread = new Thread(runnable, "moray-terminal-search");
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
        ScreenSnapshot snapshot = session.snapshot(viewport.topRow());
        List<Long> prompts = session.promptRows();
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
        ScreenSnapshot snapshot = session.snapshot(viewport.topRow());
        for (long prompt : session.promptRows()) {
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
        ScreenSnapshot snapshot = session.snapshot(viewport.topRow());
        CursorStyle style = CursorStyle.effective(snapshot.cursorShape(), options.cursorStyle());
        boolean blinks = CursorStyle.effectiveBlink(snapshot.cursorShape(), options.cursorBlink());
        boolean focused = isFocusOwner();
        boolean on = !blinks || !focused || blinkOn;
        painter.paint((Graphics2D) g, snapshot, new TerminalPainter.CursorLook(style, on, focused),
            highlights(snapshot), getWidth(), getHeight());
        if (inactiveDim > 0f) {
            Color background = options.palette().background();
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
            selection = null;
            restartBlink();
            e.consume();
        }
    }

    void handleMouse(MouseEvent e) {
        Type type = typeOf(e);
        if (type == null || (type == Type.MOVED && !session.mouseReporting())) {
            return;
        }
        if (type == Type.PRESSED) {
            openingLink = false;
            localPopupGesture = false;
            popupShown = false;
            requestFocusInWindow();
        }
        if (macOs && type == Type.WHEEL && e.isShiftDown()) {
            return; // macOS sends horizontal trackpad scrolling (and Shift+wheel) this way; the terminal has no use for it
        }
        if (macOs && type == Type.PRESSED && SwingUtilities.isLeftMouseButton(e) && e.isMetaDown() && openLinkAt(e)) {
            openingLink = true;
            return;
        }
        if (openingLink && (type == Type.DRAGGED || type == Type.RELEASED)) {
            openingLink = type != Type.RELEASED; // the rest of a ⌘-click that opened a link is not reported
            return;
        }
        int notches = 0;
        if (type == Type.WHEEL) {
            notches = notches((MouseWheelEvent) e);
            if (notches == 0) {
                return;
            }
        }
        ScreenSnapshot snapshot = session.snapshot(viewport.topRow());
        int column = Math.max(0, Math.min(snapshot.width() - 1, e.getX() / fonts.cellWidth()));
        int row = Math.max(0, Math.min(snapshot.height() - 1, e.getY() / fonts.cellHeight()));
        long absoluteRow = snapshot.firstRow() + row;
        boolean linkModifier = macOs ? e.isMetaDown() : e.isControlDown();
        MouseRouting.Action action = MouseRouting.decide(type, buttonOf(e), e.getClickCount(), e.isShiftDown(),
            linkModifier, session.mouseReporting(), snapshot.alternateBuffer());
        if (type == Type.PRESSED) {
            localPopupGesture = SwingUtilities.isRightMouseButton(e) && action != MouseRouting.Action.REPORT;
            capturingSelection = action == MouseRouting.Action.START_SELECTION
                || action == MouseRouting.Action.SELECT_WORD
                || action == MouseRouting.Action.SELECT_LINE;
        } else if (type == Type.RELEASED && localPopupGesture && SwingUtilities.isRightMouseButton(e)) {
            action = MouseRouting.Action.NONE;
        } else if (capturingSelection && type == Type.DRAGGED) {
            action = MouseRouting.Action.EXTEND_SELECTION;
        } else if (capturingSelection && type == Type.RELEASED) {
            action = MouseRouting.Action.END_SELECTION;
            capturingSelection = false;
        }
        switch (action) {
            case REPORT -> {
                int screenRow = row - snapshot.scrollOffset();
                if (screenRow >= 0) {
                    session.reportMouse(column, screenRow, jediEvent(e, type, notches));
                }
            }
            case START_SELECTION -> {
                selection = null;
                pendingAnchor = Selection.at(absoluteRow, column, e.isAltDown());
            }
            case SELECT_WORD -> {
                selection = session.wordSelection(absoluteRow, column);
                pendingAnchor = null;
            }
            case SELECT_LINE -> {
                selection = session.lineSelection(absoluteRow);
                pendingAnchor = null;
            }
            case EXTEND_SELECTION -> {
                if (selection == null && pendingAnchor != null) {
                    selection = pendingAnchor;
                }
                if (selection != null) {
                    selection = selection.withFocus(absoluteRow, column);
                }
            }
            case END_SELECTION -> {
                pendingAnchor = null;
                if (options.copyOnSelect() && selection != null) {
                    copySelection();
                }
            }
            case OPEN_LINK -> session.linkAt(absoluteRow, column).ifPresent(linkOpener);
            case SCROLL_VIEW -> scrollBy(notches * WHEEL_LINES);
            case SEND_ARROWS -> sendArrows(notches);
            case NONE -> {
                // nothing to do locally
            }
        }
        showContextMenuIfTriggered(e);
        if (type == Type.RELEASED) {
            localPopupGesture = false;
            popupShown = false;
        }
        repaint();
    }

    void resizeSessionToFit() {
        GridSize grid = GridSize.fit(getWidth(), getHeight(), fonts.cellWidth(), fonts.cellHeight());
        boolean widthChanged = grid.columns() != session.columns();
        session.resize(grid.columns(), grid.rows());
        if (widthChanged) {
            forgetAbsoluteRows(); // JediTerm reflows soft-wrapped lines, moving them to other absolute rows
        }
        dirty.set(true);
    }

    /**
     * Drops everything that names lines by absolute row (selection, drag anchor, find matches, a scrolled-back view),
     * for when those rows stop naming the same lines. Runs on the Event Dispatch Thread.
     */
    private void forgetAbsoluteRows() {
        observedAbsoluteRowEpoch = session.absoluteRowEpoch();
        invalidatePendingSearch();
        selection = null;
        pendingAnchor = null;
        matches = List.of();
        currentMatch = -1;
        viewport.follow();
        repaint();
        notifyFindResultListener(new FindResult(0, 0, null));
    }

    /** Reconciles row-state changes that may have happened while this view had no session listener. */
    private void reconcileAbsoluteRows() {
        if (observedAbsoluteRowEpoch != session.absoluteRowEpoch()) {
            forgetAbsoluteRows();
        }
    }

    /** Opens the link at a mouse event's cell, if there is one; true when it did. */
    private boolean openLinkAt(MouseEvent e) {
        ScreenSnapshot snapshot = session.snapshot(viewport.topRow());
        int column = Math.max(0, Math.min(snapshot.width() - 1, e.getX() / fonts.cellWidth()));
        int row = Math.max(0, Math.min(snapshot.height() - 1, e.getY() / fonts.cellHeight()));
        Optional<String> link = session.linkAt(snapshot.firstRow() + row, column);
        link.ifPresent(linkOpener);
        return link.isPresent();
    }

    private void showContextMenuIfTriggered(MouseEvent event) {
        if (localPopupGesture && event.isPopupTrigger() && !popupShown) {
            popupShown = true;
            contextMenuHandler.accept(event);
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
        for (int i = 0; i < matches.size(); i++) {
            TerminalSearch.Match match = matches.get(i);
            if (match.row() >= first && match.row() <= last) {
                highlights.add(new TerminalPainter.Highlight((int) (match.row() - first), match.startColumn(),
                    match.endColumn(), i == currentMatch ? currentMatchColor : matchColor));
            }
        }
        if (selection != null) {
            for (int row = 0; row < snapshot.height(); row++) {
                int[] columns = selection.columnsOn(first + row, snapshot.width());
                if (columns != null) {
                    highlights.add(new TerminalPainter.Highlight(row, columns[0], columns[1],
                        options.palette().selection()));
                }
            }
        }
        return highlights;
    }

    private void scrollBy(int lines) {
        viewport.scrollBy(lines, session.snapshot(viewport.topRow()));
        repaint();
    }

    private void sendArrows(int rotation) {
        byte[] arrow = session.codeForKey(rotation < 0 ? KeyEvent.VK_UP : KeyEvent.VK_DOWN, 0);
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
            viewport.reveal(matches.get(currentMatch).row(), session.snapshot(viewport.topRow()));
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
        blinkTimer.restart();
        repaint();
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

    private static MouseRouting.Button buttonOf(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            return MouseRouting.Button.LEFT;
        }
        if (SwingUtilities.isMiddleMouseButton(e)) {
            return MouseRouting.Button.MIDDLE;
        }
        if (SwingUtilities.isRightMouseButton(e)) {
            return MouseRouting.Button.RIGHT;
        }
        return MouseRouting.Button.NONE;
    }

    private static com.jediterm.core.input.MouseEvent jediEvent(MouseEvent e, Type type, int notches) {
        int modifiers = (e.isShiftDown() ? MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG : 0)
            | (e.isAltDown() ? MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG : 0)
            | (e.isControlDown() ? MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG : 0);
        if (e instanceof MouseWheelEvent wheel) {
            int button = notches < 0 ? MouseButtonCodes.SCROLLUP : MouseButtonCodes.SCROLLDOWN;
            return new com.jediterm.core.input.MouseWheelEvent(button, modifiers, wheel.getUnitsToScroll());
        }
        int button = SwingUtilities.isLeftMouseButton(e) ? MouseButtonCodes.LEFT
            : SwingUtilities.isMiddleMouseButton(e) ? MouseButtonCodes.MIDDLE
            : SwingUtilities.isRightMouseButton(e) ? MouseButtonCodes.RIGHT
            : MouseButtonCodes.RELEASE; // motion with no button held
        return new com.jediterm.core.input.MouseEvent(type, button, modifiers);
    }

    private static String readSystemClipboard() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (UnsupportedFlavorException | IOException | IllegalStateException | HeadlessException e) {
            return null;
        }
    }

    private static void writeSystemClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (IllegalStateException | HeadlessException e) {
            // The clipboard is busy or unavailable; plan 4 logs this.
        }
    }

    private static void openInBrowser(String uri) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(uri));
            }
        } catch (IOException | URISyntaxException | RuntimeException e) {
            // RuntimeException covers UnsupportedOperationException, IllegalArgumentException and SecurityException.
            // Nothing can open this link; plan 4 logs this.
        }
    }
}
