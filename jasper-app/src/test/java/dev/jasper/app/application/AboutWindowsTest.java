package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.platform.BuildInfo;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Rectangle;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(EdtTestExtension.class)
class AboutWindowsTest {
    private final List<String> shell = new ArrayList<>();
    private final List<URI> browsed = new ArrayList<>();
    private final Contributions contributions = new Contributions();
    private final AuxiliaryWindows windows = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(
        () -> shell.add("show " + surface.id()), () -> shell.add("front " + surface.id()), () -> shell.add("dispose " + surface.id()),
        title -> { }, () -> new Rectangle(0, 0, 760, 520), (title, initial) -> Optional.empty()));

    private AboutWindows about(java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit) {
        return new AboutWindows(contributions, windows, kit, new AboutWindows.Hooks(
            BuildInfo.of(Map.of("commit", "8ba67ed3" + "0".repeat(32), "branch", "main", "describe", "v1.2.0-14-g8ba67ed3")),
            new IncidentReport.Environment("ignored", "TestOS 1 (arm)", "TestJava 25 (Vendor)"), ZoneOffset.UTC,
            text -> true, browsed::add, (delay, task) -> () -> { }));
    }

    private AboutWindows about() { return about(KitTestSupport::context); }

    private void invoke() {
        contributions.action(AboutWindows.ABOUT).orElseThrow().invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
    }

    @Test void aboutJasperIsAnApplicationActionThatOpensOneWindow() {
        about();
        var action = contributions.action(AboutWindows.ABOUT).orElseThrow();
        assertThat(action.title()).isEqualTo("About Jasper");
        assertThat(action.keywords()).contains("version", "commit", "build", "license", "licenses", "credits");
        invoke();
        invoke();
        assertThat(windows.open()).singleElement().satisfies(surface -> {
            assertThat(surface.id()).isEqualTo(AboutWindows.ABOUT_ID);
            assertThat(surface.title()).isEqualTo("About Jasper");
            assertThat(surface.preferredSize()).isEqualTo(AboutWindows.ABOUT_SIZE);
        });
        assertThat(shell).containsExactly("show jasper.about", "front jasper.about", "show jasper.about", "front jasper.about");
    }

    @Test void licensesOpensItsOwnWindowAndOutlivesAbout() {
        var about = about();
        about.showAbout();
        AuxiliarySurface aboutSurface = about.aboutSurface().orElseThrow();
        KitTestSupport.require(aboutSurface.holder(), "licenses", AbstractButton.class).doClick();
        AuxiliarySurface licenses = about.licensesSurface().orElseThrow();
        assertThat(licenses.id()).isEqualTo(AboutWindows.LICENSES_ID);
        assertThat(licenses.title()).isEqualTo("Licenses");
        assertThat(licenses.preferredSize()).isEqualTo(AboutWindows.LICENSES_SIZE);
        assertThat(KitTestSupport.require(licenses.holder(), "detail.title", JLabel.class).getText()).isEqualTo("Jasper 1.2.0 + 14 commits");
        aboutSurface.close();
        assertThat(about.aboutSurface()).isEmpty();
        assertThat(about.licensesSurface()).contains(licenses);
        assertThat(windows.open()).containsExactly(licenses);
    }

    @Test void reopeningAboutAfterItClosedOpensAFreshWindow() {
        var about = about();
        about.showAbout();
        var first = about.aboutSurface().orElseThrow();
        first.close();
        about.showAbout();
        assertThat(about.aboutSurface()).get().isNotSameAs(first);
        assertThat(windows.open()).hasSize(1);
    }

    @Test void aViewThatFailsToBuildLeavesNoWindowBehind() {
        boolean[] fail = {true};
        var about = about(() -> {
            if (fail[0]) { fail[0] = false; throw new IllegalStateException("no kit"); }
            return KitTestSupport.context();
        });
        assertThatThrownBy(about::showAbout).isInstanceOf(IllegalStateException.class);
        assertThat(windows.open()).isEmpty();
        about.showAbout();
        assertThat(about.aboutSurface()).isPresent();
    }

    /** Spec 10.2: About is in Help on every platform once the menus are rows in the window, macOS included. */
    @Test void aboutIsInTheApplicationMenuOnlyWhileMacOsShowsTheScreenMenuBar() {
        assertThat(AboutWindows.aboutInApplicationMenu(true, true)).isTrue();
        assertThat(AboutWindows.aboutInApplicationMenu(true, false)).isFalse();
        assertThat(AboutWindows.aboutInApplicationMenu(false, true)).isFalse();
        assertThat(AboutWindows.helpEntries(AboutWindows.aboutInApplicationMenu(true, false))).contains(new MenuEntry.Item("app.about"));
    }

    @Test void helpListsTheVmOptionsActionsAfterKeyboardShortcutsAndAboutLastExceptOnMacOs() {
        assertThat(AboutWindows.helpEntries(false)).containsExactly(new MenuEntry.Item("app.shortcuts"), new MenuEntry.Separator(),
            new MenuEntry.Item("app.memory"), new MenuEntry.Item("app.vmoptions"), new MenuEntry.Separator(), new MenuEntry.Item("app.about"));
        assertThat(AboutWindows.helpEntries(true)).containsExactly(new MenuEntry.Item("app.shortcuts"), new MenuEntry.Separator(),
            new MenuEntry.Item("app.memory"), new MenuEntry.Item("app.vmoptions"));
    }
}
