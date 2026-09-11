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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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

    private final TerminalSession session;
    private final TerminalOptions options;
    private final FontSet fonts;
    private final TerminalPainter painter;
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
    private List<TerminalSearch.Match> matches = List.of();
    private int currentMatch = -1;
    private volatile boolean exited;
    private Runnable onCloseRequest = () -> { };
    private Supplier<String> clipboardReader = TerminalView::readSystemClipboard;
    private Consumer<String> clipboardWriter = TerminalView::writeSystemClipboard;
    private Consumer<String> linkOpener = TerminalView::openInBrowser;

    public TerminalView(TerminalSession session, TerminalOptions options) {
        this.session = session;
        this.options = options;
        this.fonts = new FontSet(options.fontFamily(), options.fontSize(), options.fallbackFonts(), options.ligatures());
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
        frameTimer.start();
        blinkTimer.start();
    }

    @Override
    public void removeNotify() {
        frameTimer.stop();
        blinkTimer.stop();
        session.removeListener(listener);
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

    /** Finds matches in the scrollback and screen and shows the newest one. */
    public FindResult find(String query, boolean regex, boolean caseSensitive) {
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

    /** Moves to the next newer match, wrapping around. */
    public FindResult findNext() {
        return stepMatch(1);
    }

    /** Moves to the next older match, wrapping around. */
    public FindResult findPrevious() {
        return stepMatch(-1);
    }

    public void clearFind() {
        matches = List.of();
        currentMatch = -1;
        repaint();
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
        if (e.getID() == KeyEvent.KEY_PRESSED && handleViewShortcut(e)) {
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
        ScreenSnapshot snapshot = session.snapshot(viewport.topRow());
        int column = Math.max(0, Math.min(snapshot.width() - 1, e.getX() / fonts.cellWidth()));
        int row = Math.max(0, Math.min(snapshot.height() - 1, e.getY() / fonts.cellHeight()));
        long absoluteRow = snapshot.firstRow() + row;
        if (type == Type.PRESSED) {
            requestFocusInWindow();
        }
        boolean linkModifier = macOs ? e.isMetaDown() : e.isControlDown();
        MouseRouting.Action action = MouseRouting.decide(type, buttonOf(e), e.getClickCount(), e.isShiftDown(),
            linkModifier, session.mouseReporting(), snapshot.alternateBuffer());
        if (type == Type.PRESSED) {
            capturingSelection = action == MouseRouting.Action.START_SELECTION
                || action == MouseRouting.Action.SELECT_WORD
                || action == MouseRouting.Action.SELECT_LINE;
        } else if (capturingSelection && type == Type.DRAGGED) {
            action = MouseRouting.Action.EXTEND_SELECTION;
        } else if (capturingSelection && type == Type.RELEASED) {
            action = MouseRouting.Action.END_SELECTION;
            capturingSelection = false;
        }
        switch (action) {
            case REPORT -> session.reportMouse(column, row - snapshot.scrollOffset(), jediEvent(e, type));
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
            case SCROLL_VIEW -> scrollBy(((MouseWheelEvent) e).getWheelRotation() * WHEEL_LINES);
            case SEND_ARROWS -> sendArrows(((MouseWheelEvent) e).getWheelRotation());
            case NONE -> {
                // nothing to do locally
            }
        }
        repaint();
    }

    void resizeSessionToFit() {
        GridSize grid = GridSize.fit(getWidth(), getHeight(), fonts.cellWidth(), fonts.cellHeight());
        session.resize(grid.columns(), grid.rows());
        dirty.set(true);
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

    /** Copy, paste, prompt jumps and page scrolling; plan 3 moves these into the configurable keymap. */
    private boolean handleViewShortcut(KeyEvent e) {
        int code = e.getKeyCode();
        boolean primary = macOs ? e.isMetaDown() : e.isControlDown() && e.isShiftDown();
        if (primary && code == KeyEvent.VK_C) {
            copySelection();
            return true;
        }
        if (primary && code == KeyEvent.VK_V) {
            paste(clipboardReader.get());
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
        if (!primary && e.isShiftDown() && code == KeyEvent.VK_PAGE_UP) {
            scrollBy(-(session.rows() - 1));
            return true;
        }
        if (!primary && e.isShiftDown() && code == KeyEvent.VK_PAGE_DOWN) {
            scrollBy(session.rows() - 1);
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

    private static com.jediterm.core.input.MouseEvent jediEvent(MouseEvent e, Type type) {
        int modifiers = (e.isShiftDown() ? MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG : 0)
            | (e.isAltDown() ? MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG : 0)
            | (e.isControlDown() ? MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG : 0);
        if (e instanceof MouseWheelEvent wheel) {
            int button = wheel.getWheelRotation() < 0 ? MouseButtonCodes.SCROLLUP : MouseButtonCodes.SCROLLDOWN;
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
        } catch (IOException | URISyntaxException | UnsupportedOperationException e) {
            // Nothing can open this link; plan 4 logs this.
        }
    }
}
