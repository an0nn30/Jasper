package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.testsupport.MockProfile;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Spec 10.3, 10.5 and 10.6 against the mocks: the Enterprise toolbar row, tab strip and status cells, rendered at 2×
 * under the shipped kit, Plex and the Enterprise theme, compared with the Workstation (Light) and Dark Mode mocks by
 * profile ("Mock measurements"; plan 6a Rulings 12, plan 6b Rulings 7, 8 and 15). Render coordinates are the mock's
 * minus (X, Y), except below the tab strip, which is 1 px taller than the mock's (Rulings 7).
 */
class EnterpriseWorkstationMockTest {
    static final int X = EnterpriseMenuRowMockTest.X, Y = EnterpriseMenuRowMockTest.Y;
    /** The Run Dialog mock, the one that shows the status bar's run item. */
    static final String RUN = "06 Run Dialog/Run Dialog - Just opened (recent + most used).png";
    // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
    /** The toolbar row's height in px. */
    static final int TOOLBAR = 28;
    // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
    /** The device pixels by which the mocks' 34 px toolbar row is taller than the 28 px one. */
    static final int SHORTER = 2 * (34 - 28);
    /**
     * The device pixels the window's ⋮ (a 22 px icon button) and its 8 px gap take after Settings…, which the mocks predate
     * (workspace plan 7b Rulings 20).
     */
    static final int WINDOW_MENU = 2 * (22 + 8);

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    /** The mocks' window: the 28 px toolbar row, "Local: ~" and the selected "Mac: ~", a configuration warning. EDT. */
    static void workstation(WindowContent owner) {
        // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
        owner.setToolbarHeight(TOOLBAR);
        owner.currentTerminal().rename("Local: ~");
        owner.newTab(DesktopTestSupport.HOME);
        owner.currentTerminal().rename("Mac: ~");
        owner.setConfigurationState(RuledCellsTest.state(ConfigDiagnostic.Severity.WARNING));
        owner.update();
    }

    static EnterpriseMenuRowMockTest.Mounted window(boolean dark) throws Exception {
        EnterpriseMenuRowMockTest.Mounted[] mounted = new EnterpriseMenuRowMockTest.Mounted[1];
        edt(() -> mounted[0] = EnterpriseMenuRowMockTest.mounted(dark, "workstation", EnterpriseWorkstationMockTest::workstation));
        return mounted[0];
    }

    static BufferedImage mock(boolean dark) { return MockProfile.mock(dark ? EnterpriseMenuRowMockTest.DARK : EnterpriseMenuRowMockTest.LIGHT); }

    /** The run is the mock's length (within {@code tolerance}) in the colour the shipped theme paints there, {@code colour}. */
    static void same(String what, MockProfile.Run actual, MockProfile.Run expected, int tolerance, Color colour) {
        assertThat(actual.length()).as(what + " length").isCloseTo(expected.length(), within(tolerance));
        assertThat(MockProfile.deltaE(actual.color(), colour)).as(what + " colour").isLessThanOrEqualTo(2.5);
    }

    /** The full-height 1 px rules of {@code divider}'s colour along row {@code y}: 2 px wide, and 12 px above and below too. */
    static List<Integer> dividers(BufferedImage image, int y, int from, int to, Color divider) {
        var found = new ArrayList<Integer>();
        for (var run : MockProfile.row(image, y, from, to))
            if (run.length() == 2 && MockProfile.deltaE(run.color(), divider) <= 2.5
                    && MockProfile.deltaE(MockProfile.at(image, run.start(), y - 12), divider) <= 2.5
                    && MockProfile.deltaE(MockProfile.at(image, run.start(), y + 12), divider) <= 2.5) found.add(run.start());
        return found;
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void theRowsDownTheWindowAreTheMocks(boolean dark) throws Exception {
        String what = dark ? "dark " : "light ";
        // Column 2000 crosses no control: the menu row and its rule, the toolbar row and its rule, the tab strip and its rule.
        // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
        var expected = new ArrayList<>(MockProfile.solid(MockProfile.column(mock(dark), 2000, Y, Y + 180), 2));
        // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34. The render's column stops
        // SHORTER sooner, so it ends at the same row of the tab strip as the mock's (the Dark strip, rule and terminal are
        // one colour and run to the column's end).
        var actual = MockProfile.solid(MockProfile.column(window(dark).image(), 2000 - X, 0, 180 - SHORTER), 2);
        String[] names = {"menu row", "menu rule", "toolbar row", "toolbar rule", "tab strip", "tab rule"};
        Color[] colours = {MockProfile.role(dark, "surface.app"), MockProfile.role(dark, "border.divider"), MockProfile.role(dark, "surface.app"),
            MockProfile.painted(dark, WindowPairs.RULE), MockProfile.role(dark, "surface.tabstrip"), MockProfile.role(dark, "border.window")};
        // The user's 2026-10-18 direction: the New UI Dark port paints the tab strip and its rule alike (#1E1F22), so the
        // render reads them as one run, measured against the mock's strip and rule together.
        if (MockProfile.deltaE(colours[4], colours[5]) <= 2.5) {
            expected = new ArrayList<>(expected.subList(0, 6));
            expected.set(4, new MockProfile.Run(expected.get(4).start(), expected.get(4).length() + expected.get(5).length(), expected.get(4).color()));
            expected.remove(5);
            names = java.util.Arrays.copyOf(names, 5);
            names[4] = "tab strip and its rule";
        }
        // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
        expected.set(2, new MockProfile.Run(expected.get(2).start(), expected.get(2).length() - SHORTER, expected.get(2).color()));
        for (int i = 0; i < names.length; i++) same(what + names[i], actual.get(i), expected.get(i), 2, colours[i]);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void theToolbarRowIsTheMocks(boolean dark) throws Exception {
        String what = dark ? "dark " : "light ";
        var mock = mock(dark);
        var mounted = window(dark);
        var render = mounted.image();
        // "+" and "New Session": toolbar padding 8, button padding 8, the glyph 6 before the label (mock ink 38-51, 66-199).
        var expected = MockProfile.inks(mock, new Rectangle(X + 4, 112, 400, 64), 12, 25);
        // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
        var actual = MockProfile.inks(render, new Rectangle(4, 112 - Y, 400, 64 - SHORTER), 12, 25);
        // The user's 2026-10-18 direction: classic icon then label. The add icon's ink begins 3 px into it and is 10 wide;
        // the label follows the 16 px icon and its 6 px gap where it followed the 7 px "+" and its 6 px gap.
        int[] shift = {2 * 3, 2 * ((16 + 6) - (7 + 6))};
        for (int i = 0; i < 2; i++) {
            assertThat(actual.get(i).start() + X).as(what + "ink " + i + " starts").isCloseTo(expected.get(i).start() + shift[i], within(4));
            // The user's 2026-10-18 direction: classic icon then label.
            if (i == 0) assertThat(actual.get(i).width()).as(what + "the add icon's width").isCloseTo(2 * 10, within(4));
            else MockProfile.fonts(what + "New Session's ink width", actual.get(i).width(), expected.get(i).width(), 4);
            // The user's 2026-10-18 direction: classic icon then label. The add icon's 10 px ink, 3 px into the icon centred in
            // the 24 px button, tops out 2 px above the 7 px "+" centred there.
            int rise = i == 0 ? 2 * 2 : 0;
            // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
            assertThat(actual.get(i).top() + Y).as(what + "ink " + i + " top").isCloseTo(expected.get(i).top() - rise - SHORTER / 2, within(3));
        }
        // The user's 2026-10-19 direction: IntelliJ Classic's Search Everywhere icon in place of the mocks' field (x 2116-2715).
        assertThat(mounted.field().width).as(what + "no search field").isZero();
        var search = mounted.search();
        var settingsButton = mounted.settings();
        // The user's 2026-10-19 direction: IntelliJ Classic's Search Everywhere icon in place of the mocks' field (x 2116-2715).
        // The icon square ends 8 px (16 mock px) before Settings…, the trailing group's button gap (CHROME_BUTTON_GAP, as
        // between the toolbar's buttons).
        assertThat(2 * (settingsButton.x - (search.x + search.width))).as(what + "the search icon's gap before Settings…").isEqualTo(2 * 8);
        // The user's 2026-10-19 direction: IntelliJ Classic's Search Everywhere icon in place of the mocks' field (x 2116-2715).
        // It is a 22 px icon square, centred in the 28 px row where the field's 22 px stood (mock y 122-165).
        assertThat(2 * search.width).as(what + "search icon width").isEqualTo(44);
        assertThat(2 * search.height).as(what + "search icon height").isEqualTo(44);
        assertThat(2 * search.y + Y).as(what + "search icon top").isCloseTo(122 - SHORTER / 2, within(2));
        // "Settings…" at the trailing edge: ink 2742-2846 in the mock.
        var settings = MockProfile.inks(mock, new Rectangle(2722, 112, 160, 64), 12, 25);
        // The user's 2026-10-18 direction: classic icon then label. The crop widens left by the icon and its gap. Settings…
        // is disabled here (no settings opener), so its grey icon, faded at 0.42, lies about ΔE 19 from the row: the
        // render's crop reads ink from ΔE 12.
        // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
        var rendered = MockProfile.inks(render, new Rectangle(2722 - 44 - WINDOW_MENU - X, 112 - Y, 160 + 44, 64 - SHORTER), 12, 12);
        assertThat(settings).as("the mock's Settings…").hasSize(1);
        // The user's 2026-10-18 direction: classic icon then label.
        assertThat(rendered).as(what + "Settings…'s icon, then its label").hasSize(2);
        MockProfile.fonts(what + "Settings… starts", rendered.getLast().start() + X + WINDOW_MENU, settings.getFirst().start(), 4);
        assertThat(rendered.getLast().end() + X + WINDOW_MENU).as(what + "Settings… ends").isCloseTo(settings.getFirst().end(), within(4));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void theTabsAreTheMocks(boolean dark) throws Exception {
        String what = dark ? "dark " : "light ";
        var mock = mock(dark);
        var mounted = window(dark);
        var render = mounted.image();
        assertThat(mounted.firstTab().isEmpty()).as(what + "classic tabs").isFalse();
        int left = 2 * mounted.firstTab().x;
        // The window's tab strip sits right under the toolbar, the one row of tabs the mocks draw (tabs and panes spec 5
        // and 13): its top is the toolbar's rule's bottom.
        // The underline row: mock y 233 (the indicator 232-235 above the rule at 236); the render's strip is 2 px taller.
        // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
        int strip = 25 + TOOLBAR + 1;
        assertThat(mounted.firstTab().y).as(what + "the window's strip under the toolbar").isEqualTo(strip);
        int underline = 2 * (strip + 29) - 3;
        var expected = MockProfile.solid(MockProfile.row(mock, 233, 650, 1100), 4);
        var actual = MockProfile.solid(MockProfile.row(render, underline, left, left + 450), 4);
        same(what + "the first tab and its divider on the strip", actual.get(0), expected.get(0), 6, MockProfile.role(dark, "surface.tabstrip"));
        same(what + "the active tab's tab.indicator underline", actual.get(1), expected.get(1), 6, MockProfile.painted(dark, WindowPairs.TAB_INDICATOR));
        // The active tab on surface.card, inside its 12 px leading padding (mock x 850, mid-strip y 208).
        // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
        assertThat(MockProfile.deltaE(MockProfile.at(render, left + 200, 2 * (strip + 14)), MockProfile.role(dark, "surface.card")))
            .as(what + "the active tab's ground").isLessThanOrEqualTo(2.5);
        // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
        MockProfile.write(render.getSubimage(left, 2 * strip, 700, 58), "enterprise-" + (dark ? "dark" : "light") + "-tabs");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void theStatusCellsAreTheMocks(boolean dark) throws Exception {
        String what = dark ? "dark " : "light ";
        var mock = mock(dark);
        var render = window(dark).image();
        int height = 2 * EnterpriseMenuRowMockTest.HEIGHT;
        // From the bottom of column 1000: surface.app under its border.window rule (24 tall; the mock's 22 a residual).
        var expected = MockProfile.solid(MockProfile.column(mock, 1000, 1700, 1802), 2);
        var actual = MockProfile.solid(MockProfile.column(render, 1000 - X, height - 100, height), 2);
        same(what + "status bar", actual.getLast(), expected.getLast(), 4, MockProfile.role(dark, "surface.app"));
        same(what + "status rule", actual.get(actual.size() - 2), expected.get(expected.size() - 2), 1, MockProfile.painted(dark, WindowPairs.RULE));
        // The border.divider rules: the mock's last two (before UTF-8 and the warning) at 2470 and 2574, from the right edge.
        Color divider = MockProfile.at(mock, 2470, 1782);
        int row = height - 22;
        var mockRules = dividers(mock, 1782, X, 2882, divider);
        var renderRules = dividers(render, row, 0, 2880, MockProfile.role(dark, "border.divider"));
        assertThat(mockRules).as("the mock's seven rules").containsExactly(108, 164, 1966, 2140, 2322, 2470, 2574);
        assertThat(renderRules.size()).as(what + "a rule after the run item, session and folder and before each right cell").isEqualTo(3 + 5);
        MockProfile.fonts(what + "the rule before UTF-8", renderRules.get(renderRules.size() - 2) + X, 2470, 6);
        MockProfile.fonts(what + "the rule before the warning", renderRules.getLast() + X, 2574, 6);
        assertThat(MockProfile.deltaE(MockProfile.at(render, 2860, row), MockProfile.role(dark, "warning.ground")))
            .as(what + "the warning cell's ground").isLessThanOrEqualTo(2.5);
        // Rulings 15: the run item, 60 wide in the Run Dialog mock (its rule at 122).
        var runMock = MockProfile.mock(RUN);
        assertThat(dividers(runMock, 1782, X, 400, MockProfile.at(runMock, 122, 1782))).as("the Run Dialog mock's first rule").startsWith(122);
        MockProfile.fonts(what + "the run item's width", renderRules.getFirst() + X, 122, 4);
        MockProfile.write(render.getSubimage(1898, height - 46, 982, 46), "enterprise-" + (dark ? "dark" : "light") + "-status");
    }
}
