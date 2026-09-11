package dev.moray.terminal;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** The Swing component that shows a {@link TerminalSession} and sends it keyboard input. */
public final class TerminalView extends JComponent {
    private static final int FRAME_MILLIS = 8;
    private static final int BLINK_MILLIS = 530;

    private final TerminalSession session;
    private final TerminalOptions options;
    private final FontSet fonts;
    private final TerminalPainter painter;
    private final KeyEncoder keys;
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

    public TerminalView(TerminalSession session, TerminalOptions options) {
        this.session = session;
        this.options = options;
        this.fonts = new FontSet(options.fontFamily(), options.fontSize(), options.fallbackFonts(), options.ligatures());
        this.painter = new TerminalPainter(fonts, options.palette());
        this.keys = new KeyEncoder(options.optionAsMeta(),
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac"));
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
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }
        });
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

    @Override
    protected void paintComponent(Graphics g) {
        ScreenSnapshot snapshot = session.snapshot();
        CursorStyle style = CursorStyle.effective(snapshot.cursorShape(), options.cursorStyle());
        boolean blinks = CursorStyle.effectiveBlink(snapshot.cursorShape(), options.cursorBlink());
        boolean focused = isFocusOwner();
        boolean on = !blinks || !focused || blinkOn;
        painter.paint((Graphics2D) g, snapshot, new TerminalPainter.CursorLook(style, on, focused), List.of(),
            getWidth(), getHeight());
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
            restartBlink();
            e.consume();
        }
    }

    void resizeSessionToFit() {
        GridSize grid = GridSize.fit(getWidth(), getHeight(), fonts.cellWidth(), fonts.cellHeight());
        session.resize(grid.columns(), grid.rows());
        dirty.set(true);
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
}
