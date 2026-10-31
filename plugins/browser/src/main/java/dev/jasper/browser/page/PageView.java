// Adapted from JetBrains/jcef java_tests/tests (JBCefOsrComponent.java) at 9df0879, under JCEF's BSD-3-Clause licence.
package dev.jasper.browser.page;

import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.font.TextHitInfo;
import java.awt.geom.AffineTransform;
import java.awt.im.InputMethodRequests;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * The canvas a page is drawn on. Chromium renders the page off screen and its {@link PageRenderer} hands each frame
 * here; this lightweight component paints the last one and, over it, the address of the link under the pointer. It hands
 * the page the mouse, the wheel, keys, input methods and drops. It is the Browser's one painting canvas (the
 * presentation check's {@code PAGE} exception), and the host sizes and colours it, as all custom content.
 *
 * <p><b>Keys.</b> Jasper's shortcuts are key bindings on the window's root that take a key only where it applies. Each
 * key event has Swing's pass first, its key listeners and then its key bindings, and reaches the page only when that
 * pass left it unconsumed. A press a binding takes keeps its typed and released events from the page too. Each press
 * of a key the page gets activates it ({@link #activation()}), unless the key is Escape or a modifier alone, as in
 * Chromium.
 *
 * <p><b>Mouse.</b> An event the window consumes before the view hears it, as the pane drag's press, does not reach the
 * page. Each press the page gets is its {@link #lastPress()} and activates it. The mouse's back and forward buttons, on
 * Windows and Linux, never reach the page: releasing one tells the view's {@link #onNavigationButton listener}. Nor,
 * there, does Ctrl+wheel, which touchpad pinch sends on Windows, so Chromium's own Ctrl+wheel zoom never applies: it
 * tells the view's {@link #onMagnify magnifier} a step, away from the user bigger, as Chromium counts the notches. They
 * add up, a turn gives a step once they round to a whole notch, at most one step a turn, and what is left over carries
 * to the next turn. With Shift down too, as Java reports a horizontal wheel or a tilt, the wheel is the page's, which
 * scrolls sideways. On macOS, where trackpad pinch is the gesture (spec section 7), Ctrl+wheel is the page's, as in
 * Chromium.
 *
 * <p><b>Showing.</b> While the view does not show (its tab is hidden, or it is out of its window) its page draws one
 * frame a second; showing again restores 60. The page itself is not told it is hidden: its scripts and network work go
 * on.
 */
public final class PageView extends JComponent {
    static final int SHOWN_FRAME_RATE = 60, HIDDEN_FRAME_RATE = 1;
    private static final int RESIZE_DELAY_MILLIS = 100;
    private static final int HOVER_PADDING = 4, HOVER_MAX_CHARS = 512;
    private static final String ELLIPSIS = "…";

    private final Predicate<KeyEvent> taken;
    // Which of AWT's buttons are the mouse's back and forward buttons here.
    private final IntFunction<Optional<NavigationButton>> navigationButtons;
    private volatile Consumer<NavigationButton> navigation = button -> { };
    private volatile IntConsumer magnifier = direction -> { };
    // Made with the page's browser: it names Chromium's types, which an item that has not shown yet does not load.
    private volatile ImeAdapter ime;
    // The view's input method client from the start, so a view focused before its page attaches is an active client.
    private final InputMethodRequests requests = new Requests();
    private final Timer resize = new Timer(RESIZE_DELAY_MILLIS, event -> resized());
    private volatile Hooks hooks = Hooks.NONE;
    // Set by the browser as it attaches; no method of the view names it, because the view's construction reflects on
    // its methods' types, and the browser's type loads Chromium's.
    volatile PageBrowser browser;
    private volatile LastPress lastPress = LastPress.NONE;
    private final Activation activation = new Activation();
    private volatile String hoverUrl = "";
    private volatile double scale = 1;
    // The last frame, in device pixels, and the back buffer it is copied to; both written on Chromium's paint thread.
    private volatile BufferedImage frame;
    private volatile VolatileImage back;
    // UI thread: a press Swing's pass took, whose typed and released events are not the page's either.
    private boolean typedTaken;
    private int takenKey = KeyEvent.VK_UNDEFINED;
    // UI thread: the notches Ctrl+wheel has turned that no step took yet, as Chromium keeps them; negative away from the user.
    private double magnifyTurn;

    /** Where the view's showing state and keys go: its browser's, or before it has one, nowhere. */
    record Hooks(Consumer<Boolean> visibility, IntConsumer frameRate, Consumer<KeyEvent> keys) {
        static final Hooks NONE = new Hooks(shown -> { }, rate -> { }, key -> { });
    }

    /** A canvas without a page yet; its {@link PageBrowser} attaches to it. UI thread. Only {@link Page} makes one. */
    public PageView() {
        taken = KeyEvent::isConsumed;
        navigationButtons = NavigationButton::of;
        install();
    }

    /**
     * A canvas whose page is these hooks, for tests: a Chromium browser cannot be made without Chromium.
     *
     * @param taken whether Swing's pass took a key event, which then does not reach the page
     * @param visibility told whether the view shows
     * @param frameRate told the frames a second the page draws
     * @param keys handed each key event that reaches the page
     * @param navigationButtons which navigation button, if any, each of AWT's mouse buttons is
     */
    PageView(Predicate<KeyEvent> taken, Consumer<Boolean> visibility, IntConsumer frameRate, Consumer<KeyEvent> keys,
             IntFunction<Optional<NavigationButton>> navigationButtons) {
        this.taken = Objects.requireNonNull(taken, "taken");
        hooks = new Hooks(Objects.requireNonNull(visibility, "visibility"), Objects.requireNonNull(frameRate, "frameRate"),
            Objects.requireNonNull(keys, "keys"));
        this.navigationButtons = Objects.requireNonNull(navigationButtons, "navigationButtons");
        install();
    }

    private void install() {
        resize.setRepeats(false);
        enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK
            | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.INPUT_METHOD_EVENT_MASK);
        enableInputMethods(true);
        setFocusable(true);
        setRequestFocusEnabled(true);
        setFocusTraversalKeysEnabled(false); // Tab and Shift+Tab are the page's
        addFocusListener(new FocusListener() {
            @Override public void focusGained(FocusEvent event) {
                PageBrowser target = browser;
                if (target != null) target.setFocus(true);
            }

            @Override public void focusLost(FocusEvent event) {
                typedTaken = false;
                takenKey = KeyEvent.VK_UNDEFINED;
                PageBrowser target = browser;
                if (target != null) target.setFocus(false);
            }
        });
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) showing(isShowing());
        });
        addPropertyChangeListener("graphicsConfiguration", event -> rescale());
    }

    /**
     * The view's page is the browser that set {@link #browser}: it takes the page's showing state, keys, input methods
     * and drops. The page's native browser is made once the view is in a window, and the page is told whether the view
     * shows. UI thread; once, from {@link PageBrowser#attach()}.
     */
    void attached(Hooks page, ImeAdapter adapter, DropTargetListener drops) {
        hooks = Objects.requireNonNull(page, "page");
        ime = Objects.requireNonNull(adapter, "adapter");
        addInputMethodListener(adapter);
        if (!GraphicsEnvironment.isHeadless()) new DropTarget(this, drops); // the view's drop target from now on
        rescale();
        PageBrowser target = browser;
        if (isDisplayable() && target != null) target.create();
        showing(isShowing());
    }

    /**
     * The last press the page got.
     *
     * @return the press, or {@link LastPress#NONE} before the first; any thread
     */
    public LastPress lastPress() {
        return lastPress;
    }

    /**
     * The page's user activation: each press the page gets and each press of a key it gets, but Escape and a modifier
     * alone, activate it, and each popup that opens consumes it. A key Jasper's shortcuts took is not the page's.
     *
     * @return the activation; any thread
     */
    public Activation activation() {
        return activation;
    }

    /**
     * Tells {@code listener} each time the mouse's back or forward button is released over the view, in place of the
     * page. UI thread.
     *
     * @param listener told which; it replaces the one before
     */
    void onNavigationButton(Consumer<NavigationButton> listener) {
        navigation = Objects.requireNonNull(listener, "listener");
    }

    // The platform whose Ctrl+wheel rule the view follows, as os.name names it; a test gives another.
    private volatile String osName = System.getProperty("os.name", "");

    /**
     * Has the view follow the Ctrl+wheel rule of the platform {@code os} names, as {@code os.name} does, for a test of
     * another platform than the one it runs on. UI thread.
     *
     * @param os an {@code os.name} value
     */
    void useOsName(String os) {
        osName = Objects.requireNonNull(os, "os");
    }

    /**
     * Tells {@code listener} each step Ctrl+wheel over the view magnifies the page by, in place of the page. UI thread.
     *
     * @param listener told 1 for a step bigger, -1 for a step smaller; it replaces the one before
     */
    void onMagnify(IntConsumer listener) {
        magnifier = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Shows {@code url} at the view's bottom left, as the address of the link under the pointer. Any thread.
     *
     * @param url the address, or empty or null to show none
     */
    public void setHoverUrl(String url) {
        String next = url == null ? "" : url;
        if (next.equals(hoverUrl)) return;
        hoverUrl = next;
        repaint();
    }

    @Override public void addNotify() {
        super.addNotify();
        PageBrowser target = browser;
        if (target != null) target.create();
    }

    @Override public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        resize.restart(); // tells the page its size once the view has kept it for 100 ms
    }

    /**
     * The view's input method requests, the same from its construction: an input method decides when the view gains
     * focus whether it takes composed text, so a view focused before its page attaches takes it too. Before the page
     * attaches, they answer as for empty text with the caret at the view's origin.
     */
    @Override public InputMethodRequests getInputMethodRequests() {
        return requests;
    }

    /** The page's input method side once it attaches; before, empty text with its caret at the view's origin. */
    private final class Requests implements InputMethodRequests {
        @Override public Rectangle getTextLocation(TextHitInfo offset) {
            ImeAdapter page = ime;
            if (page != null) return page.getTextLocation(offset);
            Point at = isShowing() ? getLocationOnScreen() : new Point();
            return new Rectangle(at.x, at.y, 0, 0);
        }

        @Override public TextHitInfo getLocationOffset(int x, int y) {
            ImeAdapter page = ime;
            return page == null ? null : page.getLocationOffset(x, y);
        }

        @Override public int getInsertPositionOffset() {
            ImeAdapter page = ime;
            return page == null ? 0 : page.getInsertPositionOffset();
        }

        @Override public AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex,
                                                                      AttributedCharacterIterator.Attribute[] attributes) {
            ImeAdapter page = ime;
            return page == null ? new AttributedString("").getIterator() : page.getCommittedText(beginIndex, endIndex, attributes);
        }

        @Override public int getCommittedTextLength() {
            ImeAdapter page = ime;
            return page == null ? 0 : page.getCommittedTextLength();
        }

        @Override public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
            ImeAdapter page = ime;
            return page == null ? null : page.cancelLatestCommittedText(attributes);
        }

        @Override public AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
            ImeAdapter page = ime;
            return page == null ? new AttributedString("").getIterator() : page.getSelectedText(attributes);
        }
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            Color background = getBackground();
            if (background != null) {
                g.setColor(background);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            VolatileImage buffer = back;
            BufferedImage last = frame;
            if (buffer != null && !buffer.contentsLost()) g.drawImage(buffer, 0, 0, null);
            else if (last != null) g.drawImage(last, 0, 0, logical(last.getWidth()), logical(last.getHeight()), null);
            paintHover(g);
        } finally {
            g.dispose();
        }
    }

    @Override protected void processMouseEvent(MouseEvent event) {
        super.processMouseEvent(event);
        if (event.isConsumed()) return; // the window's, as the pane drag's press and click are
        Optional<NavigationButton> navigationButton = navigationButtons.apply(event.getButton());
        if (navigationButton.isPresent()) { // its press, release and click are not the page's
            event.consume();
            if (event.getID() == MouseEvent.MOUSE_RELEASED) navigation.accept(navigationButton.get());
            return;
        }
        boolean press = event.getID() == MouseEvent.MOUSE_PRESSED;
        if (press) {
            lastPress = new LastPress(event.getButton(), event.getModifiersEx(), event.getWhen());
            activation.activate(event.getWhen());
        }
        PageBrowser target = browser;
        if (target != null) target.sendMouseEvent(event);
        if (press) requestFocusInWindow();
    }

    @Override protected void processMouseMotionEvent(MouseEvent event) {
        super.processMouseMotionEvent(event);
        PageBrowser target = browser;
        if (!event.isConsumed() && target != null) target.sendMouseEvent(event);
    }

    @Override protected void processMouseWheelEvent(MouseWheelEvent event) {
        super.processMouseWheelEvent(event);
        if (event.isConsumed()) return;
        if (magnifies(event.getModifiersEx(), osName)) { // Jasper's zoom, never the page's
            event.consume();
            magnify(event.getPreciseWheelRotation());
            return;
        }
        PageBrowser target = browser;
        if (target == null) return;
        double rotation = -event.getPreciseWheelRotation(); // Chromium's wheel turns the other way
        target.sendMouseWheelEvent(new MouseWheelEvent(event.getComponent(), event.getID(), event.getWhen(),
            event.getModifiersEx(), event.getX(), event.getY(), event.getXOnScreen(), event.getYOnScreen(),
            event.getClickCount(), event.isPopupTrigger(), event.getScrollType(), event.getScrollAmount(), (int) rotation,
            rotation));
    }

    @Override protected void processKeyEvent(KeyEvent event) {
        super.processKeyEvent(event); // key listeners, then key bindings: Jasper's shortcuts take their keys here
        boolean pageGets = switch (event.getID()) {
            case KeyEvent.KEY_PRESSED -> {
                boolean took = taken.test(event);
                typedTaken = took;
                if (took) takenKey = event.getKeyCode();
                else if (event.getKeyCode() == takenKey) takenKey = KeyEvent.VK_UNDEFINED;
                yield !took;
            }
            case KeyEvent.KEY_TYPED -> !typedTaken && !taken.test(event);
            case KeyEvent.KEY_RELEASED -> {
                if (takenKey != KeyEvent.VK_UNDEFINED && event.getKeyCode() == takenKey) {
                    takenKey = KeyEvent.VK_UNDEFINED;
                    yield false;
                }
                yield !taken.test(event);
            }
            default -> false;
        };
        if (!pageGets) {
            event.consume();
            return;
        }
        if (event.getID() == KeyEvent.KEY_PRESSED && activates(event.getKeyCode())) activation.activate(event.getWhen());
        hooks.keys().accept(event);
    }

    /**
     * Whether a wheel turned with {@code modifiers}, AWT's extended modifiers, magnifies the page on the platform
     * {@code osName} names: Ctrl without Shift, on Windows and Linux.
     */
    static boolean magnifies(int modifiers, String osName) {
        int ctrlAndShift = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
        return !osName.startsWith("Mac") && (modifiers & ctrlAndShift) == InputEvent.CTRL_DOWN_MASK;
    }

    /**
     * Ctrl+wheel turned {@code notches}, as Chromium counts them (its {@code zoom_scroll_remainder_}): they add to those
     * no step took yet, which round, half away from zero, to whole notches; any whole notch gives one step its way, and
     * the rest is kept.
     */
    private void magnify(double notches) {
        if (!Double.isFinite(notches)) return;
        magnifyTurn += notches;
        double whole = Math.signum(magnifyTurn) * Math.floor(Math.abs(magnifyTurn) + 0.5);
        magnifyTurn -= whole;
        if (whole != 0) magnifier.accept(whole < 0 ? 1 : -1);
    }

    /** Whether pressing {@code keyCode} activates the page: every key but Escape and the modifiers, as in Chromium. */
    private static boolean activates(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_ESCAPE, KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META,
                 KeyEvent.VK_ALT_GRAPH, KeyEvent.VK_CAPS_LOCK, KeyEvent.VK_WINDOWS -> false;
            default -> true;
        };
    }

    /**
     * The page's native browser now exists. JCEF dropped what the page was told before, so it is told again whether the
     * view shows. UI thread.
     */
    void browserCreated() {
        showing(isShowing());
    }

    /** The page's input method side, which the renderer tells where the page's composition sits; null before it has a page. */
    ImeAdapter ime() {
        return ime;
    }

    /** Device pixels per view pixel on the view's screen, as Chromium is told; 1 until the view has a page. */
    double scale() {
        return scale;
    }

    /**
     * Chromium painted {@code dirty} of {@code image}, both in device pixels: copies that part to the back buffer and
     * repaints it. Off a screen, only the image is kept, and painting draws it. Chromium's paint thread.
     */
    void painted(BufferedImage image, Rectangle dirty) {
        frame = image;
        Rectangle region = dirty;
        GraphicsConfiguration screen = getGraphicsConfiguration();
        if (screen == null) {
            back = null;
        } else {
            VolatileImage target = back;
            int width = logical(image.getWidth()), height = logical(image.getHeight());
            int state = target == null ? VolatileImage.IMAGE_INCOMPATIBLE : target.validate(screen);
            if (state == VolatileImage.IMAGE_INCOMPATIBLE || target.getWidth() != width || target.getHeight() != height) {
                target = screen.createCompatibleVolatileImage(width, height, Transparency.TRANSLUCENT);
                region = new Rectangle(0, 0, image.getWidth(), image.getHeight());
            } else if (state == VolatileImage.IMAGE_RESTORED) {
                region = new Rectangle(0, 0, image.getWidth(), image.getHeight());
            }
            Graphics2D g = target.createGraphics();
            try {
                AffineTransform device = g.getTransform();
                g.scale(1 / device.getScaleX(), 1 / device.getScaleY()); // one image pixel to one device pixel
                g.setComposite(AlphaComposite.Src);
                g.drawImage(image, region.x, region.y, region.x + region.width, region.y + region.height,
                    region.x, region.y, region.x + region.width, region.y + region.height, null);
            } finally {
                g.dispose();
            }
            back = target;
        }
        double ratio = scale;
        int x = (int) Math.floor(region.x / ratio), y = (int) Math.floor(region.y / ratio);
        repaint(x, y, (int) Math.ceil((region.x + region.width) / ratio) - x, (int) Math.ceil((region.y + region.height) / ratio) - y);
    }

    private void showing(boolean shown) {
        Hooks target = hooks;
        target.visibility().accept(shown);
        target.frameRate().accept(shown ? SHOWN_FRAME_RATE : HIDDEN_FRAME_RATE);
    }

    private void rescale() {
        PageBrowser target = browser;
        if (target == null) return;
        scale = target.deviceScale(this);
        target.notifyScreenInfoChanged();
    }

    private void resized() {
        PageBrowser target = browser;
        if (target != null) target.wasResized((int) Math.ceil(getWidth() * scale), (int) Math.ceil(getHeight() * scale));
    }

    private int logical(int devicePixels) {
        return (int) Math.ceil(devicePixels / scale);
    }

    /** The hover address in the host's font and colours, cut to the view's width. */
    private void paintHover(Graphics2D g) {
        String url = hoverUrl;
        if (url.isEmpty()) return;
        Font font = getFont();
        if (font != null) g.setFont(font);
        if (Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints") instanceof Map<?, ?> hints)
            g.addRenderingHints(hints);
        FontMetrics metrics = g.getFontMetrics();
        String shown = elide(url, metrics, getWidth() - 4 * HOVER_PADDING);
        int width = metrics.stringWidth(shown) + 2 * HOVER_PADDING, height = metrics.getHeight() + HOVER_PADDING;
        int top = getHeight() - height;
        Color background = getBackground(), foreground = getForeground();
        if (background != null) {
            g.setColor(new Color(background.getRGB())); // opaque, over the page
            g.fillRect(0, top, width, height);
        }
        if (foreground == null) return;
        g.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 64));
        g.drawRect(0, top, width - 1, height - 1);
        g.setColor(foreground);
        g.drawString(shown, HOVER_PADDING, top + HOVER_PADDING / 2 + metrics.getAscent());
    }

    /** {@code url} as it fits in {@code room}, cut at its end with an ellipsis when it does not. */
    private static String elide(String url, FontMetrics metrics, int room) {
        String text = url.length() > HOVER_MAX_CHARS ? url.substring(0, HOVER_MAX_CHARS) : url;
        if (text.length() == url.length() && metrics.stringWidth(text) <= room) return text;
        int low = 0, high = text.length(); // the longest start that fits with the ellipsis
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (metrics.stringWidth(text.substring(0, mid) + ELLIPSIS) <= room) low = mid;
            else high = mid - 1;
        }
        if (low > 0 && Character.isHighSurrogate(text.charAt(low - 1))) low--;
        return text.substring(0, low) + ELLIPSIS;
    }
}
