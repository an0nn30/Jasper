package dev.jasper.terminal.view;

import dev.jasper.terminal.internal.TerminalAccess;
import dev.jasper.terminal.internal.emulation.EmulationFixture;
import dev.jasper.terminal.internal.emulation.FakeConnector;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.testsupport.Await;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The link gesture's platform rule through a {@link MouseController} built with {@code macOs} and {@code windows} set
 * explicitly, so every platform's case runs on every host; {@link TerminalViewInteractionTest} covers the view's own
 * platform.
 */
class MouseControllerLinkGestureTest {
    private static final int CELL_WIDTH = 10;
    private static final int CELL_HEIGHT = 20;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int CMD = InputEvent.META_DOWN_MASK;

    private final JPanel source = new JPanel();
    private final List<TerminalLink> opened = new ArrayList<>();
    private FakeConnector connector;
    private TerminalSession session;
    private TerminalAccess access;

    @BeforeEach
    void setUp() throws Exception {
        connector = new FakeConnector();
        session = EmulationFixture.unstarted(connector, 20, 4, 100);
        access = session.internalAccess();
        access.startReading();
        connector.feed("go https://m.dev x");
        Await.until(() -> access.snapshot().lineText(0).startsWith("go https://m.dev x"), "the link");
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    @Test
    void ctrlShiftClickIsASplitOnLinuxAndWindowsWithoutMouseReports() {
        for (boolean windows : new boolean[] {false, true}) {
            opened.clear();
            MouseController controller = controller(false, windows);

            click(controller, CTRL | SHIFT);

            assertThat(opened).as("windows=%s", windows).containsExactly(link(TerminalLink.Gesture.SPLIT));
        }
    }

    /** Shift is how a Ctrl-click stays local under reports, so there it cannot also ask for a split. */
    @Test
    void ctrlShiftClickStaysAnOpenOnLinuxAndWindowsWhileAProgramAsksForMouseReports() throws Exception {
        enableSgrMouse();
        for (boolean windows : new boolean[] {false, true}) {
            opened.clear();
            MouseController controller = controller(false, windows);

            click(controller, CTRL | SHIFT);

            assertThat(opened).as("windows=%s", windows).containsExactly(link(TerminalLink.Gesture.OPEN));
            assertThat(connector.written()).as("the program heard nothing").isEmpty();
        }
    }

    @Test
    void ctrlClickWithoutShiftIsAnOpenOnLinuxAndWindows() {
        MouseController controller = controller(false, false);

        click(controller, CTRL);

        assertThat(opened).containsExactly(link(TerminalLink.Gesture.OPEN));
    }

    @Test
    void cmdShiftClickIsASplitOnMacOsWithAndWithoutMouseReports() throws Exception {
        MouseController controller = controller(true, false);

        click(controller, CMD | SHIFT);
        enableSgrMouse();
        click(controller, CMD | SHIFT);

        assertThat(opened).containsExactly(link(TerminalLink.Gesture.SPLIT), link(TerminalLink.Gesture.SPLIT));
        assertThat(connector.written()).as("the program heard nothing").isEmpty();
    }

    @Test
    void cmdClickWithoutShiftIsAnOpenOnMacOsEvenWhileAProgramAsksForMouseReports() throws Exception {
        MouseController controller = controller(true, false);

        enableSgrMouse();
        click(controller, CMD);

        assertThat(opened).containsExactly(link(TerminalLink.Gesture.OPEN));
        assertThat(connector.written()).as("the program heard nothing").isEmpty();
    }

    private MouseController controller(boolean macOs, boolean windows) {
        return new MouseController(access, new SelectionController(access), new Viewport(), macOs, windows,
            () -> CELL_WIDTH, () -> CELL_HEIGHT, () -> false, () -> { }, () -> { }, opened::add, () -> null,
            event -> { }, () -> { });
    }

    private TerminalLink link(TerminalLink.Gesture gesture) {
        return new TerminalLink("https://m.dev", gesture, Optional.empty());
    }

    /** A left press and release on the link's {@code m} with {@code modifiers} held at the press. */
    private void click(MouseController controller, int modifiers) {
        int x = 8 * CELL_WIDTH + CELL_WIDTH / 2;
        int y = CELL_HEIGHT / 2;
        controller.handle(new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0, modifiers | InputEvent.BUTTON1_DOWN_MASK,
            x, y, 1, false, MouseEvent.BUTTON1));
        controller.handle(new MouseEvent(source, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    private void enableSgrMouse() throws Exception {
        connector.feed("\033[?1000h\033[?1006h");
        Await.until(access::mouseReporting, "mouse reporting on");
    }
}
