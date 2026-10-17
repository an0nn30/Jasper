package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.presentation.Presentation;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockProfile;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import javax.swing.JRootPane;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Spec 16.1.4 against the mocks: the Enterprise menu row, rendered at 2× under the shipped kit, IBM Plex and the theme
 * ({@link EnterpriseLook}), compared with the Workstation (Light) and Dark Mode mocks by profile ("Mock measurements";
 * Rulings 12).
 */
class EnterpriseMenuRowMockTest {
    static final String LIGHT = "01 Workstation/Workstation - Connected (plugins in use).png";
    static final String DARK = "08 Dark Mode/Workstation Dark - Connected (plugins in use).png";
    /** Where the window's content starts in the 2× mocks: past the outer border, the native title bar and its rule. */
    static final int X = 2, Y = 60;
    /** The mocks' content area in logical pixels. */
    static final int WIDTH = 1440, HEIGHT = 871;

    @org.junit.jupiter.api.AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    /** The mocks' menus: a plugin's Session, Transfer and Tools entries, and the app's Help. */
    static Contributions workstation() {
        var model = new Contributions();
        for (String id : List.of("dev.x.connect", "dev.x.upload", "dev.x.tool", "dev.x.about"))
            model.addAction(id, id.substring(6), null, List.of(), Optional.empty(), invocation -> { });
        model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.SESSION)).set(List.of(new MenuEntry.Item("dev.x.connect")));
        model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TRANSFER)).set(List.of(new MenuEntry.Item("dev.x.upload")));
        model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TOOLS)).set(List.of(new MenuEntry.Item("dev.x.tool")));
        model.addMenuSection(MenuTarget.topLevel(MenuTarget.HELP, "Help")).set(List.of(new MenuEntry.Item("dev.x.about")));
        return model;
    }

    /** The Enterprise window as the mocks show its content, printed at 2× and written for inspection. EDT. */
    static BufferedImage render(boolean dark, java.util.function.Consumer<WindowContent> fixture) {
        var themes = EnterpriseLook.controller(dark);
        try {
            var projections = Projections.of(EnterpriseLook.chrome(), true, true, "dev.jasper.enterprise", new Projections.Host(true, false),
                EnumSet.allOf(Presentation.Landed.class));
            var look = WindowLook.of(projections);
            var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, look.chrome());
            owner.connectContributions(workstation());
            fixture.accept(owner);
            var root = new JRootPane();
            WindowContent.mount(root, owner, look, title -> { });
            var image = MockProfile.print(root, WIDTH, HEIGHT);
            MockProfile.write(image, "enterprise-" + (dark ? "dark" : "light") + "-window");
            return image;
        } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void theMenuRowMatchesTheMock(boolean dark) throws Exception {
        dev.jasper.app.testsupport.EnterpriseResources.assumePlexResolves();
        var mock = MockProfile.mock(dark ? DARK : LIGHT);
        BufferedImage[] render = new BufferedImage[1];
        edt(() -> render[0] = render(dark, owner -> { }));
        String what = dark ? "dark" : "light";
        var mockRuns = MockProfile.solid(MockProfile.column(mock, 2000, Y, Y + 52), 2);
        var renderRuns = MockProfile.solid(MockProfile.column(render[0], 2000 - X, 0, 52), 2);
        assertThat(mockRuns).as("the mock's row and rule runs").hasSizeGreaterThanOrEqualTo(2);
        assertThat(renderRuns).as(what + " row and rule runs").hasSizeGreaterThanOrEqualTo(2);
        // The 24 px row (48 at 2x) on surface.app, then its 1 px rule.
        assertThat(renderRuns.get(0).length()).as(what + " row").isCloseTo(mockRuns.get(0).length(), within(2));
        assertThat(MockProfile.deltaE(renderRuns.get(0).color(), mockRuns.get(0).color())).as(what + " row colour").isLessThanOrEqualTo(2.5);
        assertThat(renderRuns.get(1).length()).as(what + " rule").isCloseTo(mockRuns.get(1).length(), within(1));
        if (!dark) assertThat(MockProfile.deltaE(renderRuns.get(1).color(), mockRuns.get(1).color())).as("light rule colour").isLessThanOrEqualTo(2.5);
        // Plan 6a Rulings 9: Enterprise Dark's border.divider is darker than the mock's rule until plan 6b's override.
        var mockInks = MockProfile.inks(mock, new Rectangle(X + 2, Y + 2, 900, 44), 12, 25);
        var renderInks = MockProfile.inks(render[0], new Rectangle(2, 2, 900, 44), 12, 25);
        assertThat(mockInks).as("the mock's eight menu titles").hasSize(8);
        assertThat(renderInks).as(what + ": File, Edit, View, Session, Transfer, Tools, Window, Help").hasSize(8);
        // In IBM Plex, with the titles at their kerned fractional widths (ClassicMenuRow.titleWidth), every title starts within
        // 1 px of the mock's; Transfer's ink, drawn unkerned, is 3 px wider in Light.
        for (int i = 0; i < 8; i++) {
            var expected = mockInks.get(i); var actual = renderInks.get(i);
            assertThat(actual.start() + X).as(what + " title " + i + " starts").isCloseTo(expected.start(), within(1));
            assertThat(actual.width()).as(what + " title " + i + " width").isCloseTo(expected.width(), within(4));
            assertThat(actual.top() + Y).as(what + " title " + i + " top").isCloseTo(expected.top(), within(3));
        }
    }
}
