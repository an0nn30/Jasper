// Adapted from JetBrains/jcef java_tests/tests (JBCefOsrComponent.java) at 9df0879, under JCEF's BSD-3-Clause licence.
package dev.jasper.browser.page;

import com.jetbrains.cef.JCefAppConfig;
import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.dnd.DropTarget;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.im.InputMethodRequests;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import javax.swing.JComponent;
import javax.swing.Timer;
import org.cef.CefClient;

/**
 * The canvas a page is drawn on. Chromium renders the page off screen and its {@link PageRenderer} hands each frame
 * here; this lightweight component paints the last one and, over it, the address of the link under the pointer. It hands
 * the page the mouse, the wheel, keys, input methods and drops. It is the Browser's one painting canvas (the
 * presentation check's {@code PAGE} exception), and the host sizes and colours it, as all custom content.
 *
 * <p><b>Keys.</b> Jasper's shortcuts are key bindings on the window's root that take a key only where it applies. Each
 * key event has Swing's pass first, its key listeners and then its key bindings, and reaches the page only when that
 * pass left it unconsumed. A press a binding takes keeps its typed and released events from the page too.
 *
 * <p><b>Mouse.</b> An event the window consumes before the view hears it, as the pane drag's press, does not reach the
 * page. Each press the page gets is its {@link #lastPress()}.
 *
 * <p><b>Showing.</b> While the view does not show (its tab is hidden, or it is out of its window) its page is told it is
 * hidden and draws one frame a second; showing again restores 60. Network work goes on.
 */
public final class PageView extends JComponent {
    static final int SHOWN_FRAME_RATE = 60, HIDDEN_FRAME_RATE = 1;
    private static final int RESIZE_DELAY_MILLIS = 100;
    private static final int HOVER_PADDING = 4, HOVER_MAX_CHARS = 512;
    private static final String ELLIPSIS = "…";

    private final Predicate<KeyEvent> taken;
    private final ImeAdapter ime = new ImeAdapter(this);
    private final Timer resize = new Timer(RESIZE_DELAY_MILLIS, event -> resized());
    private volatile Hooks hooks = Hooks.NONE;
    private volatile PageBrowser browser;
    private volatile LastPress lastPress = LastPress.NONE;
    private volatile String hoverUrl = "";
    private volatile double scale = 1;
    // The last frame, in device pixels, and the back buffer it is copied to; both written on Chromium's paint thread.
    private volatile BufferedImage frame;
    private volatile VolatileImage back;
    // UI thread: a press Swing's pass took, whose typed and released events are not the page's either.
    private boolean typedTaken;
    private int takenKey = KeyEvent.VK_UNDEFINED;

    /** Where the view's showing state and keys go: its browser's, or before it has one, nowhere. */
    private record Hooks(Consumer<Boolean> visibility, IntConsumer frameRate, Consumer<KeyEvent> keys) {
        static final Hooks NONE = new Hooks(shown -> { }, rate -> { }, key -> { });
    }

    /** A canvas without a page yet; {@link #setBrowser} gives it one. UI thread. Only {@link Page} makes one. */
    public PageView() {
        taken = KeyEvent::isConsumed;
        install();
    }

    /**
     * A canvas whose page is these hooks, for tests: a Chromium browser cannot be made without Chromium.
     *
     * @param taken whether Swing's pass took a key event, which then does not reach the page
     * @param visibility told whether the view shows
     * @param frameRate told the frames a second the page draws
     * @param keys handed each key event that reaches the page
     */
    PageView(Predicate<KeyEvent> taken, Consumer<Boolean> visibility, IntConsumer frameRate, Consumer<KeyEvent> keys) {
        this.taken = Objects.requireNonNull(taken, "taken");
        hooks = new Hooks(Objects.requireNonNull(visibility, "visibility"), Objects.requireNonNull(frameRate, "frameRate"),
            Objects.requireNonNull(keys, "keys"));
        install();
    }

    private void install() {
        resize.setRepeats(false);
        enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK
            | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.INPUT_METHOD_EVENT_MASK);
        enableInputMethods(true);
        addInputMethodListener(ime);
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
     * Gives the view its page. The page's native browser is made once the view is in a window, and the page is told
     * whether the view shows. UI thread; once.
     *
     * @param browser the page
     */
    public void setBrowser(PageBrowser browser) {
        this.browser = Objects.requireNonNull(browser, "browser");
        hooks = new Hooks(browser::setWindowVisibility, browser::setWindowlessFrameRate, browser::sendKeyEvent);
        ime.setBrowser(browser);
        if (!GraphicsEnvironment.isHeadless()) new DropTarget(this, browser.drops()); // the view's drop target from now on
        rescale();
        if (isDisplayable()) create(browser);
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
        if (target != null) create(target);
    }

    @Override public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        resize.restart(); // tells the page its size once the view has kept it for 100 ms
    }

    @Override public InputMethodRequests getInputMethodRequests() {
        return ime;
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
        boolean press = event.getID() == MouseEvent.MOUSE_PRESSED;
        if (press) lastPress = new LastPress(event.getButton(), event.getModifiersEx(), event.getWhen());
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
        PageBrowser target = browser;
        if (event.isConsumed() || target == null) return;
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
        if (pageGets) hooks.keys().accept(event);
        else event.consume();
    }

    /**
     * The page's native browser now exists. JCEF dropped what the page was told before, so it is told again whether the
     * view shows. UI thread.
     */
    void browserCreated() {
        showing(isShowing());
    }

    /** The view's input method side, which the renderer tells where the page's composition sits. */
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

    private static void create(PageBrowser target) {
        if (!CefClient.isNativeBrowserCreationStarted(target)) target.createImmediately();
    }

    private void rescale() {
        PageBrowser target = browser;
        if (target == null) return;
        scale = JCefAppConfig.getDeviceScaleFactor(this);
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
