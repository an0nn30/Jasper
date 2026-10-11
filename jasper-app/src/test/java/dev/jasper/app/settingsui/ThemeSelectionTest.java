package dev.jasper.app.settingsui;

import static dev.jasper.app.config.CoreSettings.*;
import static org.assertj.core.api.Assertions.*;
import dev.jasper.app.config.*;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.model.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class ThemeSelectionTest {
    private static final Option.Palette COLORS=new Option.Palette(1,2,3,4,5);
    private final List<ThemeChoices.Source> sources=new ArrayList<>(List.of(
        new ThemeChoices.Source("Jasper Dark",ThemeSource.CATALOG,false,COLORS,70,false),
        new ThemeChoices.Source("Custom Night",ThemeSource.CATALOG,true,COLORS,85,true),
        new ThemeChoices.Source("Jasper Dark",ThemeSource.BUNDLED,true,COLORS,90,true),
        new ThemeChoices.Source("Jasper Light",ThemeSource.BUNDLED,false,COLORS,100,false)));
    private final ThemeChoices themes=new ThemeChoices(()->sources.stream().map(ThemeChoices.Source::name).distinct().toList(),
        ignored->new ThemeChoices.Import(()->{},()->{}),()->List.copyOf(sources),()->Optional.of(false),ignored->new Subscription(()->{}));
    private SettingsDraft draft(String config){return new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"),config,true).values());}
    private ThemeSelection editor(SettingsDraft draft){return new ThemeSelection("Theme",themes,PageKit.context(draft,id->{},()->{}));}
    private void choose(ThemeSelection editor,String owner,String id){editor.event("ui.theme.name",new UiEvent.Selected(owner,Set.of(id)));}
    @Test void darkTileCannotResolveTheOppositePolarityInstalledNameCollision() {
        var draft=draft("");try(var editor=new Owned(editor(draft))){choose(editor.value,"ui.theme.name:policy","dark");
            assertThat(draft.value(UI_THEME_SOURCE)).isEqualTo(ThemeSource.BUNDLED);
            assertThat(draft.value(UI_THEME_NAME)).isEqualTo("Jasper Dark");assertThat(draft.value(UI_THEME_FOLLOW_SYSTEM)).isFalse();}
    }
    @Test void systemRemembersManualIdentityAndMatchingTileKeepsItExactly() {
        var draft=draft("[ui.theme]\nname=' Custom Night '\nsource='catalog'");try(var editor=new Owned(editor(draft))){
            choose(editor.value,"ui.theme.name:policy","system");assertThat(draft.value(UI_THEME_FOLLOW_SYSTEM)).isTrue();
            assertThat(draft.value(UI_THEME_NAME)).isEqualTo("Custom Night");
            choose(editor.value,"ui.theme.name:policy","dark");assertThat(draft.value(UI_THEME_NAME)).isEqualTo("Custom Night");
            assertThat(draft.value(UI_THEME_SOURCE)).isEqualTo(ThemeSource.CATALOG);assertThat(draft.value(UI_THEME_FOLLOW_SYSTEM)).isFalse();}
    }
    @Test void chooserStagesOriginAndPolicyTogetherAndCatalogRefreshDoesNotRewriteIntent() {
        var draft=draft("[ui.theme]\nfollow_system=true");var observed=new ArrayList<ThemeSource>();draft.onChanged(()->{assertThat(draft.value(UI_THEME_FOLLOW_SYSTEM)).isFalse();observed.add(draft.value(UI_THEME_SOURCE));});
        try(var editor=new Owned(editor(draft))){assertThat(editor.value.labels()).contains("Jasper Dark (installed)","Jasper Dark (bundled)");
            choose(editor.value,"ui.theme.name","theme-2");assertThat(observed).containsExactly(ThemeSource.BUNDLED);
            sources.remove(2);editor.value.refresh();editor.value.node("ui.theme.name",ControlState.READY);
            assertThat(draft.value(UI_THEME_SOURCE)).isEqualTo(ThemeSource.BUNDLED);assertThat(draft.value(UI_THEME_NAME)).isEqualTo("Jasper Dark");}
    }
    @Test void namedImportedSourceExitsSystemAndCoupledResetRemovesAllThreeKeys() {
        var draft=draft("[ui.theme]\nname='Jasper Light'\nsource='bundled'\nfollow_system=true");
        var system=new CorePages.SystemActions(()->Path.of("config.toml"),()->{},()->{},()->{},()->{},()->false);
        var page=CorePages.specs(themes,system).stream().filter(spec->spec.id().equals("appearance")).findFirst().orElseThrow().build(PageKit.context(draft,id->{},()->{}));
        try {
            var box=PageKit.kit(page,"ui.theme.name.input",javax.swing.JComboBox.class);var document=((javax.swing.text.JTextComponent)box.getEditor().getEditorComponent()).getDocument();
            box.setSelectedIndex(1);assertThat(draft.value(UI_THEME_NAME)).isEqualTo("Custom Night");assertThat(draft.value(UI_THEME_SOURCE)).isEqualTo(ThemeSource.CATALOG);assertThat(draft.value(UI_THEME_FOLLOW_SYSTEM)).isFalse();
            var menu=PageKit.kit(page,"ui.theme.name:actions",javax.swing.JComponent.class).getComponentPopupMenu();
            java.util.Arrays.stream(menu.getComponents()).filter(javax.swing.JMenuItem.class::isInstance).map(javax.swing.JMenuItem.class::cast).filter(item->item.getText().equals("Reset to defaults")).findFirst().orElseThrow().doClick(0);
            assertThat(draft.value(UI_THEME_NAME)).isEqualTo(UI_THEME_NAME.defaultValue());assertThat(draft.value(UI_THEME_SOURCE)).isEqualTo(ThemeSource.CATALOG);assertThat(draft.value(UI_THEME_FOLLOW_SYSTEM)).isFalse();
            assertThat(draft.edit().changes()).containsEntry(UI_THEME_NAME,Optional.empty()).containsEntry(UI_THEME_SOURCE,Optional.empty()).containsEntry(UI_THEME_FOLLOW_SYSTEM,Optional.empty());
            assertThat(((javax.swing.text.JTextComponent)box.getEditor().getEditorComponent()).getDocument()).isSameAs(document);
            draft.reset(List.of(UI_THEME_NAME,UI_THEME_SOURCE,UI_THEME_FOLLOW_SYSTEM));draft.rebase(ConfigLoader.parse(Path.of("config.toml"),"[ui.theme]\nname='Custom Night'\nsource='catalog'\nfollow_system=false",true).values());
            assertThat(draft.modified()).isFalse();assertThat(draft.value(UI_THEME_NAME)).isEqualTo("Custom Night");
        }finally{page.dispose();}
    }
    @Test void pendingSelectionRetainsEveryCompanionAcrossExternalRebase() {
        for (String pick : List.of("theme-1", "dark", "light")) {
            var draft = draft("[ui.theme]\nname='Nord'\nsource='catalog'\nfollow_system=false");
            try (var editor = new Owned(editor(draft))) {
                choose(editor.value, pick.startsWith("theme") ? "ui.theme.name" : "ui.theme.name:policy", pick);
                String selectedName = draft.value(UI_THEME_NAME);
                ThemeSource selectedSource = draft.value(UI_THEME_SOURCE);
                draft.rebase(ConfigLoader.parse(Path.of("config.toml"), "[ui.theme]\nname='Jasper Dark'\nsource='bundled'\nfollow_system=true", true).values());
                assertThat(draft.value(UI_THEME_NAME)).isEqualTo(selectedName);
                assertThat(draft.value(UI_THEME_SOURCE)).as(pick + " retains chosen origin").isEqualTo(selectedSource);
                assertThat(draft.value(UI_THEME_FOLLOW_SYSTEM)).as(pick + " remains manual").isFalse();
                assertThat(draft.edit().changes()).containsKeys(UI_THEME_NAME, UI_THEME_SOURCE, UI_THEME_FOLLOW_SYSTEM);
                assertThat(draft.edit().expected()).containsEntry(UI_THEME_SOURCE, Optional.of(ThemeSource.BUNDLED)).containsEntry(UI_THEME_FOLLOW_SYSTEM, Optional.of(true));
                // A partial external match must not drop a protected companion.
                draft.rebase(ConfigLoader.parse(Path.of("config.toml"), "[ui.theme]\nname='" + selectedName + "'\nsource='catalog'\nfollow_system=true", true).values());
                assertThat(draft.edit().changes()).containsKeys(UI_THEME_NAME, UI_THEME_SOURCE, UI_THEME_FOLLOW_SYSTEM);
                assertThat(draft.value(UI_THEME_SOURCE)).isEqualTo(selectedSource);
            }
        }
    }

    @Test void coupledResetRetainsAbsentCompanionsAndAnUnchangedChoiceStaysClean() {
        var draft = draft("[ui.theme]\nname='Custom Night'\nsource='catalog'\nfollow_system=false");
        try (var editor = new Owned(editor(draft))) {
            choose(editor.value, "ui.theme.name", "theme-1");
            assertThat(draft.modified()).isFalse();
        }
        draft.rebase(ConfigLoader.parse(Path.of("config.toml"), "[ui.theme]\nname='Custom Night'", true).values());
        var system = new CorePages.SystemActions(() -> Path.of("config.toml"), () -> {}, () -> {}, () -> {}, () -> {}, () -> false);
        var page = CorePages.specs(themes, system).stream().filter(spec -> spec.id().equals("appearance")).findFirst().orElseThrow().build(PageKit.context(draft, id -> {}, () -> {}));
        try {
            var menu = PageKit.kit(page, "ui.theme.name:actions", javax.swing.JComponent.class).getComponentPopupMenu();
            java.util.Arrays.stream(menu.getComponents()).filter(javax.swing.JMenuItem.class::isInstance).map(javax.swing.JMenuItem.class::cast).filter(item -> item.getText().equals("Reset to defaults")).findFirst().orElseThrow().doClick(0);
            draft.rebase(ConfigLoader.parse(Path.of("config.toml"), "[ui.theme]\nname='Nord'\nsource='bundled'\nfollow_system=true", true).values());
            assertThat(draft.edit().changes()).containsEntry(UI_THEME_NAME, Optional.empty()).containsEntry(UI_THEME_SOURCE, Optional.empty()).containsEntry(UI_THEME_FOLLOW_SYSTEM, Optional.empty());
            assertThat(draft.value(UI_THEME_SOURCE)).isEqualTo(ThemeSource.CATALOG);
            assertThat(draft.value(UI_THEME_FOLLOW_SYSTEM)).isFalse();
            draft.rebase(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
            assertThat(draft.modified()).as("entire tuple is now accepted").isFalse();
        } finally { page.dispose(); }
    }

    record Owned(ThemeSelection value) implements AutoCloseable {public void close(){value.dispose();}}
}
