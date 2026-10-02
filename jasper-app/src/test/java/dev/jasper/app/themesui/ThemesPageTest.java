package dev.jasper.app.themesui;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.settingsui.*;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ThemesPageTest {
    private final SettingsDraft draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
    private final PageContext context = new PageContext(draft, id -> {}, Runnable::run, (title, initial) -> Optional.empty(), () -> {});
    private final AtomicReference<ThemeCatalog.Snapshot> catalog = new AtomicReference<>(new ThemeCatalog.Snapshot(List.of(Theme.DARK, Theme.JASPER_LIGHT), List.of()));
    private ThemesPage page() {
        return new ThemesPage(context, catalog::get, () -> ConfigSnapshot.defaults().viewOptions(12, Theme.DARK.palette()), ThemePageFixture.actions(context));
    }
    private static void select(ThemePicker picker, String name) {
        for (int i = 0; i < picker.list.getModel().getSize(); i++) {
            if (picker.list.getModel().getElementAt(i).value().equals(name)) { picker.list.setSelectedIndex(i); return; }
        }
        throw new AssertionError("Missing row: " + name);
    }
    @Test void listChoicesStageIndependentlyAndSharedDraftOwnsApply() {
        var page = page(); page.component();
        try {
            select(page.ui, "Jasper Light"); select(page.workspace, "Jasper Dark");
            assertThat(draft.value(CoreSettings.UI_THEME_NAME)).isEqualTo("Jasper Light");
            assertThat(draft.value(CoreSettings.UI_THEME_WORKSPACE)).isEqualTo("Jasper Dark");
            assertThat(draft.edit().changes()).hasSize(2); assertThat(page.modified()).isFalse();
            assertThat(page.apply()).isCompleted(); assertThat(draft.modified()).isTrue();
            draft.stage(CoreSettings.TERMINAL_FONT_SIZE, Optional.of(18f));
            page.reset();
            assertThat(draft.modified(CoreSettings.UI_THEME_NAME)).isFalse();
            assertThat(draft.modified(CoreSettings.UI_THEME_WORKSPACE)).isFalse();
            assertThat(draft.value(CoreSettings.TERMINAL_FONT_SIZE)).isEqualTo(18f);
        } finally { page.dispose(); }
    }
    @Test void filtersNeverStageAndMatchStaysReachableWithNoResults() {
        var page = page(); page.component();
        try {
            page.workspace.query.setText("not a theme");
            page.workspace.tone.setSelectedItem(ThemeChoices.Tone.LIGHT);
            page.workspace.source.setSelectedItem(ThemeChoices.Source.MINE);
            assertThat(page.workspace.list.getModel().getSize()).isEqualTo(1);
            assertThat(page.workspace.list.getModel().getElementAt(0).label()).isEqualTo("Same as UI theme");
            assertThat(page.workspace.empty.getText()).contains("No themes");
            assertThat(draft.modified()).isFalse();
            page.ui.query.setText("jasper"); page.ui.query.setCaretPosition(2);
            page.refresh(); assertThat(page.ui.query.getCaretPosition()).isEqualTo(2);
            assertThat(page.ui.query.getText()).isEqualTo("jasper");
        } finally { page.dispose(); }
    }
    @Test void missingDirtySelectionAndInvalidFileSurviveRefreshThenRecover() {
        var page = page(); page.component();
        try {
            draft.stage(CoreSettings.UI_THEME_WORKSPACE, Optional.of("Mine"));
            catalog.set(new ThemeCatalog.Snapshot(List.of(Theme.DARK), List.of(new ThemeCatalog.Problem(Path.of("broken.toml"), 7, "Unknown color"))));
            page.refresh();
            assertThat(page.workspace.requested()).isEqualTo("Mine");
            assertThat(page.workspace.selection.getText()).contains("Mine", "unavailable");
            assertThat(page.diagnostics.getText()).contains("broken.toml:7", "Unknown color");
            assertThat(draft.value(CoreSettings.UI_THEME_WORKSPACE)).isEqualTo("Mine");
            assertThat(page.valid()).isTrue();
            catalog.set(new ThemeCatalog.Snapshot(List.of(Theme.DARK, ThemeChoicesTest.user("Mine")), List.of())); page.refresh();
            assertThat(page.workspace.list.getSelectedValue().value()).isEqualTo("Mine");
            assertThat(page.diagnostics.getText()).isEmpty();
        } finally { page.dispose(); }
    }
    @Test void labelsAreLiteralAndKeyboardSelectionStagesTheValue() {
        Theme hostile = ThemeChoicesTest.user("<html>Mine"); catalog.set(new ThemeCatalog.Snapshot(List.of(hostile, Theme.DARK), List.of()));
        var page = page(); page.component();
        try {
            select(page.ui, hostile.name());
            var row = page.ui.list.getSelectedValue();
            var rendered = (JLabel) page.ui.list.getCellRenderer().getListCellRendererComponent(page.ui.list, row, 0, true, false);
            assertThat(rendered.getText()).isEqualTo("<html>Mine");
            assertThat(rendered.getClientProperty("html")).isNull();
            page.ui.list.getActionMap().get("selectNextRow").actionPerformed(new java.awt.event.ActionEvent(page.ui.list, 0, ""));
            assertThat(draft.value(CoreSettings.UI_THEME_NAME)).isEqualTo("Jasper Dark");
        } finally { page.dispose(); }
    }
    @Test void swatchesShowSixEffectiveColorsAndRefreshKeepsScrollPosition() {
        var many = new java.util.ArrayList<Theme>(catalog.get().themes());
        for (int i = 0; i < 30; i++) many.add(ThemeChoicesTest.user("User " + i));
        catalog.set(new ThemeCatalog.Snapshot(many, List.of()));
        var page = page(); page.component();
        try {
            select(page.ui, "Jasper Dark");
            var row = page.ui.list.getSelectedValue();
            var rendered = (JLabel) page.ui.list.getCellRenderer().getListCellRendererComponent(page.ui.list, row, 0, false, false);
            var icon = rendered.getIcon(); var image = new java.awt.image.BufferedImage(icon.getIconWidth(), icon.getIconHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics(); icon.paintIcon(rendered, graphics, 0, 0); graphics.dispose();
            int step = com.formdev.flatlaf.util.UIScale.scale(15);
            assertThat(new java.awt.Color(image.getRGB(1, 1), true)).isEqualTo(new java.awt.Color(0x191c20));
            int[] ansi = {0xff5555, 0x50fa7b, 0xbd93f9, 0xf1fa8c};
            for (int i = 0; i < ansi.length; i++) assertThat(new java.awt.Color(image.getRGB((i + 2) * step + 1, 1), true)).isEqualTo(new java.awt.Color(ansi[i]));
            var viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, page.ui.list);
            viewport.setExtentSize(new java.awt.Dimension(200, 20)); page.ui.list.setSize(300, 200);
            viewport.setViewPosition(new java.awt.Point(0, 30));
            page.refresh(); assertThat(viewport.getViewPosition()).isEqualTo(new java.awt.Point(0, 30));
        } finally { page.dispose(); }
    }

    @Test void disposalReleasesDraftListenerEvenBeforeLazyConstruction() {
        var reads = new AtomicInteger();
        var page = new ThemesPage(context, () -> { reads.incrementAndGet(); return catalog.get(); }, () -> ConfigSnapshot.defaults().viewOptions(12, Theme.DARK.palette()), ThemePageFixture.actions(context));
        page.dispose(); page.dispose(); int before = reads.get();
        draft.stage(CoreSettings.UI_THEME_NAME, Optional.of("Jasper Light"));
        assertThat(reads.get()).isEqualTo(before);
    }
}
