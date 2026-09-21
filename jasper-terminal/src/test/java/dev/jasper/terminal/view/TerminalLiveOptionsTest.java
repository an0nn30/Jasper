package dev.jasper.terminal.view;

import dev.jasper.terminal.config.BellMode;
import dev.jasper.terminal.config.CursorStyle;
import dev.jasper.terminal.config.OptionAsMeta;
import dev.jasper.terminal.config.Palette;
import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.internal.emulation.EmulationFixture;
import dev.jasper.terminal.internal.emulation.FakeConnector;
import dev.jasper.terminal.internal.emulation.SessionInspection;
import dev.jasper.terminal.rendering.FontSet;
import dev.jasper.terminal.search.SearchQuery;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.testsupport.Await;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TerminalLiveOptionsTest {
    private final TerminalOptions defaults = TerminalOptions.defaults();
    private FakeConnector connector;
    private TerminalSession session;
    private TerminalView view;

    @BeforeEach
    void setUp() throws Exception {
        connector = new FakeConnector();
        session = EmulationFixture.unstarted(connector, 20, 6, 3);
        session.internalAccess().startReading();
        onEdt(() -> {
            view = new TerminalView(session, defaults);
            view.setSize(view.getPreferredSize());
        });
        onEdt(() -> { }); // deliver the initial component resize before testing live changes
    }

    @AfterEach
    void tearDown() throws Exception {
        onEdt(view::removeNotify);
        session.close();
    }

    @Test
    void typographyRefitsTheSameSessionAndManualSizeUsesLatestOptions() throws Exception {
        show("hello", 0, "hello");
        var next = options("Monospaced", 18f, List.of("Dialog"), false, 1.5f,
            CursorStyle.BLOCK, true, OptionAsMeta.LEFT, false, 900);
        onEdt(() -> {
            AtomicInteger minimumChanges = new AtomicInteger();
            view.addPropertyChangeListener("minimumSize", e -> minimumChanges.incrementAndGet());
            int width = view.getWidth();
            int height = view.getHeight();
            view.applyOptions(next);
            var fonts = new FontSet("Monospaced", 18f, List.of("Dialog"), false, 1.5f);
            assertThat(session.columns()).isEqualTo(Math.max(5, width / fonts.cellWidth()));
            assertThat(session.rows()).isEqualTo(Math.max(2, height / fonts.cellHeight()));
            assertThat(view.getMinimumSize()).isEqualTo(new Dimension(5 * fonts.cellWidth(), 2 * fonts.cellHeight()));
            assertThat(minimumChanges).hasValue(1);
            assertThat(session.internalAccess().snapshot().lineText(0)).startsWith("hello");
            view.handleKey(typed('x'));
            assertThat(connector.written()).isEqualTo("x");
            view.setFontSize(20f);
            var resized = new FontSet("Monospaced", 20f, List.of("Dialog"), false, 1.5f);
            assertThat(view.getMinimumSize()).isEqualTo(new Dimension(5 * resized.cellWidth(), 2 * resized.cellHeight()));
            assertThat(view.options().fontSize()).isEqualTo(20f);
            assertThat(view.options().fontFamily()).isEqualTo("Monospaced");
            assertThat(view.options().fallbackFonts()).containsExactly("Dialog");
            assertThat(view.options().ligatures()).isFalse();
            assertThat(view.options().lineHeight()).isEqualTo(1.5f);
            view.setPalette(Palette.jasperLight());
            assertThat(view.options().palette()).isEqualTo(Palette.jasperLight());
        });
    }

    @Test
    void behaviorUpdatesAndEqualOptionsPreserveSelectionFindAndPendingInput() throws Exception {
        show("hello", 0, "hello");
        onEdt(() -> {
            selectFirstWord();
            assertThat(view.find(new SearchQuery("hello", false, true)).count()).isOne();
            var next = options(defaults.fontFamily(), 14f, defaults.fallbackFonts(), true, 1f,
                CursorStyle.BEAM, false, OptionAsMeta.LEFT, true, 900);
            view.handleKey(typed((char) 0xD83D));
            view.applyOptions(next);
            view.applyOptions(new TerminalOptions(next.fontFamily(), next.fontSize(), next.fallbackFonts(),
                next.ligatures(), next.palette(), next.cursorStyle(), next.cursorBlink(), next.optionAsMeta(),
                next.scrollback(), next.copyOnSelect(), next.lineHeight(), next.bell()));
            assertThat(view.selectedText()).contains("hello");
            assertThat(view.findNext().count()).isOne();
            assertThat(view.options()).isEqualTo(next);
            view.handleKey(typed((char) 0xDE80));
            assertThat(connector.written()).isEqualTo(new String(Character.toChars(0x1F680)));
        });
        // The live options' 900-line default must not change this session's captured three-line capacity.
        connector.feed("\r\n1\r\n2\r\n3\r\n4\r\n5\r\n6\r\n7\r\n8\r\n9");
        Await.until(() -> session.internalAccess().snapshot().lineText(5).equals("9"), "output after live update");
        assertThat(session.internalAccess().snapshot().historyLines()).isEqualTo(3);
    }

    @Test
    void copyOnSelectChangesImmediately() throws Exception {
        show("hello", 0, "hello");
        onEdt(() -> {
            AtomicReference<String> copied = new AtomicReference<>();
            view.setClipboard(() -> null, copied::set);
            selectFirstWord();
            assertThat(copied).hasNullValue();
            view.applyOptions(options(defaults.fontFamily(), 14f, defaults.fallbackFonts(), true, 1f,
                CursorStyle.BLOCK, true, OptionAsMeta.LEFT, true, 100));
            selectFirstWord();
            assertThat(copied).hasValue("hello");
        });
    }

    @Test
    void optionAsMetaRefreshesEncodingWithoutReplacingAppShortcutRouting() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac"));
        onEdt(() -> {
            AtomicInteger shortcuts = new AtomicInteger();
            view.setShortcutHandler(e -> { shortcuts.incrementAndGet(); return e.getKeyCode() == KeyEvent.VK_D; });
            view.handleKey(new KeyEvent(view, KeyEvent.KEY_PRESSED, 0, InputEvent.ALT_DOWN_MASK,
                KeyEvent.VK_ALT, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT));
            view.handleKey(new KeyEvent(view, KeyEvent.KEY_PRESSED, 0, InputEvent.ALT_DOWN_MASK, KeyEvent.VK_A, 'a'));
            view.handleKey(typed('a'));
            view.applyOptions(options(defaults.fontFamily(), 14f, defaults.fallbackFonts(), true, 1f,
                CursorStyle.BLOCK, true, OptionAsMeta.NONE, false, 100));
            view.handleKey(new KeyEvent(view, KeyEvent.KEY_PRESSED, 0, InputEvent.ALT_DOWN_MASK, KeyEvent.VK_A, 'a'));
            view.handleKey(typed('å'));
            view.handleKey(new KeyEvent(view, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_D, 'd'));
            view.handleKey(typed('d'));
            assertThat(connector.written()).isEqualTo("\033aå");
            assertThat(shortcuts).hasValue(4);
        });
    }

    @Test
    void spacedMetricsDriveSelectionMouseReportsAndRenderedRows() throws Exception {
        var next = options(defaults.fontFamily(), 14f, defaults.fallbackFonts(), true, 1.5f,
            CursorStyle.BLOCK, true, OptionAsMeta.LEFT, false, 100);
        var fonts = new FontSet(next.fontFamily(), 14f, next.fallbackFonts(), true, 1.5f);
        onEdt(() -> view.applyOptions(next));
        show("\033[2;1Hsecond", 1, "second");
        onEdt(() -> {
            view.handleMouse(mouse(MouseEvent.MOUSE_PRESSED, 2, fonts.cellHeight() + 2, 2, MouseEvent.BUTTON1));
            assertThat(view.selectedText()).contains("second");
            BufferedImage image = paint();
            assertThat(image.getRGB(2, fonts.cellHeight() + 1) & 0xFFFFFF)
                .isEqualTo(defaults.palette().selection().getRGB() & 0xFFFFFF);
            assertThat(image.getRGB(2, fonts.cellHeight() - 2) & 0xFFFFFF)
                .isEqualTo(defaults.palette().background().getRGB() & 0xFFFFFF);
        });
        connector.feed("\033[?1000h\033[?1006h");
        Await.until(session.internalAccess()::mouseReporting, "mouse reporting enabled");
        onEdt(() -> view.handleMouse(mouse(MouseEvent.MOUSE_PRESSED, 2 * fonts.cellWidth() + 1,
            fonts.cellHeight() + 1, 1, MouseEvent.BUTTON3)));
        assertThat(connector.written()).isEqualTo("\033[<2;3;2M");
    }

    @Test
    void configuredCursorUpdatesRespectProgramOverridesAndRis() throws Exception {
        KeyboardFocusManager previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        var focus = new DefaultKeyboardFocusManager() {
            @Override public Component getFocusOwner() { return view; }
        };
        onEdt(() -> KeyboardFocusManager.setCurrentKeyboardFocusManager(focus));
        try {
            onEdt(() -> {
                view.applyOptions(options(defaults.fontFamily(), 14f, defaults.fallbackFonts(), true, 1f,
                    CursorStyle.BEAM, false, OptionAsMeta.LEFT, false, 100));
                assertCursor(CursorStyle.BEAM);
            });
            connector.feed("\033[4 q");
            Await.until(() -> new dev.jasper.terminal.internal.text.CursorRequest(dev.jasper.terminal.config.CursorStyle.UNDERLINE, false).equals(session.internalAccess().snapshot().cursorShape()), "program underline");
            onEdt(() -> {
                view.applyOptions(options(defaults.fontFamily(), 14f, defaults.fallbackFonts(), true, 1f,
                    CursorStyle.BLOCK, true, OptionAsMeta.LEFT, false, 100));
                assertCursor(CursorStyle.UNDERLINE);
            });
            connector.feed("\033c");
            Await.until(() -> session.internalAccess().snapshot().cursorShape() == null, "RIS clears program cursor override");
            onEdt(() -> assertCursor(CursorStyle.BLOCK));
        } finally {
            onEdt(() -> KeyboardFocusManager.setCurrentKeyboardFocusManager(previous));
        }
    }

    @Test
    void configuredBlinkChangesLiveWhileProgramBlinkWinsUntilCursorReset() throws Exception {
        KeyboardFocusManager previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        var focus = new DefaultKeyboardFocusManager() {
            @Override public Component getFocusOwner() { return view; }
        };
        onEdt(() -> KeyboardFocusManager.setCurrentKeyboardFocusManager(focus));
        try {
            onEdt(() -> {
                view.addNotify(); // blinking requires a showing attachment
                tickBlink(); // use the actual callback, leaving the focused cursor in its off phase
                assertCursorVisible(false);
                view.applyOptions(options(defaults.fontFamily(), 14f, defaults.fallbackFonts(), true, 1f,
                    CursorStyle.BLOCK, false, OptionAsMeta.LEFT, false, 100));
                assertCursorVisible(true);
            });
            connector.feed("\033[1 q");
            Await.until(() -> new dev.jasper.terminal.internal.text.CursorRequest(dev.jasper.terminal.config.CursorStyle.BLOCK, true).equals(session.internalAccess().snapshot().cursorShape()), "program blinking block");
            onEdt(() -> assertCursorVisible(false));
            connector.feed("\033[0 q");
            Await.until(() -> session.internalAccess().snapshot().cursorShape() == null, "DECSCUSR zero resets configured cursor");
            onEdt(() -> assertCursorVisible(true));
        } finally {
            onEdt(() -> KeyboardFocusManager.setCurrentKeyboardFocusManager(previous));
        }
    }

    private void tickBlink() {
        try {
            var timer = (javax.swing.Timer) SessionInspection.field(SessionInspection.field(view, "rendering"), "blinkTimer");
            for (var listener : timer.getActionListeners()) {
                listener.actionPerformed(new java.awt.event.ActionEvent(timer, 0, ""));
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private void assertCursorVisible(boolean visible) {
        var fonts = new FontSet(defaults.fontFamily(), 14f, defaults.fallbackFonts(), true);
        assertThat(paint().getRGB(fonts.cellWidth() / 2, fonts.cellHeight() / 2) & 0xFFFFFF)
            .isEqualTo((visible ? defaults.palette().cursor() : defaults.palette().background()).getRGB() & 0xFFFFFF);
    }

    private void assertCursor(CursorStyle style) {
        var fonts = new FontSet(defaults.fontFamily(), 14f, defaults.fallbackFonts(), true);
        BufferedImage image = paint();
        int cursor = defaults.palette().cursor().getRGB() & 0xFFFFFF;
        int background = defaults.palette().background().getRGB() & 0xFFFFFF;
        int middle = image.getRGB(fonts.cellWidth() / 2, fonts.cellHeight() / 2) & 0xFFFFFF;
        int left = image.getRGB(0, fonts.cellHeight() / 2) & 0xFFFFFF;
        int bottom = image.getRGB(fonts.cellWidth() / 2, fonts.cellHeight() - 1) & 0xFFFFFF;
        assertThat(middle).isEqualTo(style == CursorStyle.BLOCK ? cursor : background);
        assertThat(left).isEqualTo(style == CursorStyle.UNDERLINE ? background : cursor);
        assertThat(bottom).isEqualTo(style == CursorStyle.BEAM ? background : cursor);
    }

    private void selectFirstWord() {
        view.handleMouse(mouse(MouseEvent.MOUSE_PRESSED, 1, 1, 2, MouseEvent.BUTTON1));
        view.handleMouse(mouse(MouseEvent.MOUSE_RELEASED, 1, 1, 2, MouseEvent.BUTTON1));
    }

    private MouseEvent mouse(int id, int x, int y, int clicks, int button) {
        return new MouseEvent(view, id, 0, 0, x, y, x, y, clicks, false, button);
    }

    private KeyEvent typed(char c) {
        return new KeyEvent(view, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c);
    }

    private BufferedImage paint() {
        BufferedImage image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try { view.paint(g); } finally { g.dispose(); }
        return image;
    }

    private void show(String input, int row, String expected) throws Exception {
        connector.feed(input);
        Await.until(() -> session.internalAccess().snapshot().lineText(row).startsWith(expected), expected + " on screen");
    }

    private TerminalOptions options(String family, float size, List<String> fallbacks, boolean ligatures,
                                    float lineHeight, CursorStyle cursor, boolean blink, OptionAsMeta meta,
                                    boolean copy, int scrollback) {
        return new TerminalOptions(family, size, fallbacks, ligatures, defaults.palette(), cursor, blink, meta,
            scrollback, copy, lineHeight, BellMode.VISUAL);
    }

    private static void onEdt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
}
