package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.Composition;
import dev.jasper.app.testsupport.KitTestSupport;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractButton;
import org.junit.jupiter.api.Test;

/** Spec 12.1: the collapsible navigation's sections, its search and its navigation, for one opening at a time. */
class SettingsNavigationTest {
    static SettingsDialog dialog(dev.jasper.app.presentation.SettingsChrome chrome) {
        return new SettingsDialog(SettingsChromeTest.windows(), context -> CorePages.pages(AppearanceFixtures.CHOICES,
            new CorePages.SystemActions(() -> Path.of("/fixture/config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> false, Optional.empty()),
            page -> new GroupPage("keymap", "Keymap", Optional.empty(), false, List::of, page),
            page -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, page)).apply(context),
            SettingsChromeTest.hooks(chrome, Composition.CLASSIC));
    }

    /** Sizes and lays out the headless dialog's view as its window would: the navigation mounts the rows it shows. */
    static SettingsView shown(SettingsDialog dialog) {
        var view = dialog.view();
        view.setSize(1000, 700);
        for (int i = 0; i < 3; i++) KitTestSupport.layout(view);
        return view;
    }

    static void collapse(SettingsDialog dialog, String section) {
        KitTestSupport.require(shown(dialog), "@tree/pages/" + section + "/expand", AbstractButton.class).doClick(0);
    }

    @Test void aSectionsTriangleClosesItAndATitleClickSelectsItsPage() throws Exception {
        var dialog = dialog(SettingsChromeTest.CLASSIC);
        try {
            edt(() -> {
                dialog.open(null, "keymap");
                assertThat(dialog.view().tree.visibleIds()).contains("appearance", "window");
                collapse(dialog, "appearance-behavior");
                assertThat(dialog.view().tree.isCollapsed("appearance-behavior")).isTrue();
                assertThat(dialog.view().tree.node().expandedIds()).doesNotContain("appearance-behavior").contains("terminal");
                assertThat(dialog.currentPage()).as("toggling selects nothing").isEqualTo("keymap");
                dialog.select("appearance-behavior");
                assertThat(dialog.currentPage()).isEqualTo("appearance-behavior");
            });
        } finally { edt(dialog::cancel); }
    }

    @Test void navigatingIntoACollapsedSectionOpensIt() throws Exception {
        var dialog = dialog(SettingsChromeTest.CLASSIC);
        try {
            edt(() -> {
                dialog.open(null, "window");
                dialog.select("keymap");
                collapse(dialog, "appearance-behavior");
                dialog.back();
                assertThat(dialog.currentPage()).isEqualTo("window");
                assertThat(dialog.view().tree.isCollapsed("appearance-behavior")).isFalse();
                assertThat(dialog.view().tree.node().expandedIds()).contains("appearance-behavior");
            });
        } finally { edt(dialog::cancel); }
    }

    @Test void aSearchOpensEverySectionUntilItIsCleared() throws Exception {
        var dialog = dialog(SettingsChromeTest.CLASSIC);
        try {
            edt(() -> {
                // Appearance matches "font" itself, so the search selects no page inside the collapsed section.
                dialog.open(null, "appearance");
                collapse(dialog, "terminal");
                dialog.search("font");
                assertThat(dialog.view().tree.node().expandedIds()).contains("terminal");
                dialog.search("");
                assertThat(dialog.view().tree.node().expandedIds()).doesNotContain("terminal");
            });
        } finally { edt(dialog::cancel); }
    }

    @Test void theStateIsNotSaved() throws Exception {
        var dialog = dialog(SettingsChromeTest.CLASSIC);
        edt(() -> { dialog.open(null, "keymap"); collapse(dialog, "terminal"); });
        edt(dialog::cancel);
        try {
            edt(() -> {
                dialog.open(null, "keymap");
                assertThat(dialog.view().tree.isCollapsed("terminal")).isFalse();
                assertThat(dialog.view().tree.node().expandedIds()).contains("terminal");
            });
        } finally { edt(dialog::cancel); }
    }

    @Test void theDefaultLooksNavigationHasNoTriangles() throws Exception {
        var dialog = dialog(LookRowsTest.LINUX_DEFAULT);
        try {
            edt(() -> {
                dialog.open(null, "keymap");
                assertThat(KitTestSupport.find(shown(dialog), "@tree/pages/terminal/expand", AbstractButton.class)).isNull();
                assertThat(KitTestSupport.find(dialog.view(), "@tree/pages/terminal/title", java.awt.Component.class)).as("the row is mounted").isNotNull();
            });
        } finally { edt(dialog::cancel); }
    }
}
