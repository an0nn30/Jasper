package dev.jasper.browser.links;

import dev.jasper.browser.links.LinkGesture.Where;
import dev.jasper.browser.page.LastPress;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.jasper.browser.links.LinkGesture.Where.NEXT_TAB;
import static dev.jasper.browser.links.LinkGesture.Where.SAME_ITEM;
import static dev.jasper.browser.links.LinkGesture.Where.SPLIT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where a clicked link goes (spec section 8): Cmd-click on macOS and Ctrl-click elsewhere opens a new tab next to this
 * one, with Shift a split, and a middle click a new tab; every other click loads the link in the same item. Every
 * combination of Shift, Ctrl, Alt and Meta is checked on both platforms.
 */
class LinkGestureTest {
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK, CTRL = InputEvent.CTRL_DOWN_MASK,
        ALT = InputEvent.ALT_DOWN_MASK, META = InputEvent.META_DOWN_MASK;

    /** One combination of held keys, and where a left click with them goes on macOS and on Windows and Linux. */
    private record Row(int keys, Where macOs, Where other) { }

    // Meta is Cmd on macOS, and the Windows or Super key elsewhere, which decides nothing there.
    private static final List<Row> LEFT_CLICKS = List.of(
        new Row(0, SAME_ITEM, SAME_ITEM),
        new Row(SHIFT, SAME_ITEM, SAME_ITEM),
        new Row(CTRL, SAME_ITEM, NEXT_TAB),
        new Row(ALT, SAME_ITEM, SAME_ITEM),
        new Row(META, NEXT_TAB, SAME_ITEM),
        new Row(SHIFT | CTRL, SAME_ITEM, SPLIT),
        new Row(SHIFT | ALT, SAME_ITEM, SAME_ITEM),
        new Row(SHIFT | META, SPLIT, SAME_ITEM),
        new Row(CTRL | ALT, SAME_ITEM, NEXT_TAB),
        new Row(CTRL | META, NEXT_TAB, NEXT_TAB),
        new Row(ALT | META, NEXT_TAB, SAME_ITEM),
        new Row(SHIFT | CTRL | ALT, SAME_ITEM, SPLIT),
        new Row(SHIFT | CTRL | META, SPLIT, SPLIT),
        new Row(SHIFT | ALT | META, SPLIT, SAME_ITEM),
        new Row(CTRL | ALT | META, NEXT_TAB, NEXT_TAB),
        new Row(SHIFT | CTRL | ALT | META, SPLIT, SPLIT));

    @Test void theTableCoversEveryCombinationOfTheFourKeysOnce() {
        assertThat(LEFT_CLICKS.stream().map(Row::keys).distinct()).hasSize(16)
            .allSatisfy(keys -> assertThat(keys & ~(SHIFT | CTRL | ALT | META)).isZero());
    }

    @Test void onMacOsCmdClickOpensANextTabAndCmdShiftClickASplit() {
        for (Row row : LEFT_CLICKS)
            assertThat(LinkGesture.of(press(MouseEvent.BUTTON1, row.keys()), true)).as(describe(row.keys())).isEqualTo(row.macOs());
    }

    @Test void onWindowsAndLinuxCtrlClickOpensANextTabAndCtrlShiftClickASplit() {
        for (Row row : LEFT_CLICKS)
            assertThat(LinkGesture.of(press(MouseEvent.BUTTON1, row.keys()), false)).as(describe(row.keys())).isEqualTo(row.other());
    }

    @Test void aMiddleClickOpensANextTabWhateverKeysAreHeldOnEitherPlatform() {
        for (Row row : LEFT_CLICKS)
            for (boolean macOs : new boolean[] {true, false})
                assertThat(LinkGesture.of(press(MouseEvent.BUTTON2, row.keys()), macOs)).as(describe(row.keys()) + (macOs ? " on macOS" : ""))
                    .isEqualTo(NEXT_TAB);
    }

    @Test void theRightButtonAndNoPressLoadInTheSameItem() {
        for (boolean macOs : new boolean[] {true, false}) {
            assertThat(LinkGesture.of(press(MouseEvent.BUTTON3, META | CTRL | SHIFT), macOs)).isEqualTo(SAME_ITEM);
            assertThat(LinkGesture.of(LastPress.NONE, macOs)).isEqualTo(SAME_ITEM);
        }
    }

    /** A press of {@code button} with {@code keys} held, as AWT reports it: the pressed button's own mask among them. */
    private static LastPress press(int button, int keys) {
        int buttonMask = switch (button) {
            case MouseEvent.BUTTON1 -> InputEvent.BUTTON1_DOWN_MASK;
            case MouseEvent.BUTTON2 -> InputEvent.BUTTON2_DOWN_MASK;
            default -> InputEvent.BUTTON3_DOWN_MASK;
        };
        return new LastPress(button, keys | buttonMask, 1_000, 0, 0);
    }

    private static String describe(int keys) {
        var names = new java.util.ArrayList<String>();
        if ((keys & SHIFT) != 0) names.add("Shift");
        if ((keys & CTRL) != 0) names.add("Ctrl");
        if ((keys & ALT) != 0) names.add("Alt");
        if ((keys & META) != 0) names.add("Meta");
        return names.isEmpty() ? "no keys" : String.join("+", names);
    }
}
