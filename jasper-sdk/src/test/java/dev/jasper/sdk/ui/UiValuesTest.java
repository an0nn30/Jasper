package dev.jasper.sdk.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UiValuesTest {
    /** Workspace tabs spec 14: the Tab menu holds what the Pane menu held. */
    @Test void theStandardMenusAreFileEditViewTabAndThePluginOnes() {
        assertThat(StandardMenu.values()).containsExactly(StandardMenu.FILE, StandardMenu.EDIT, StandardMenu.VIEW, StandardMenu.TAB,
            StandardMenu.TOOLS, StandardMenu.SESSION, StandardMenu.TRANSFER);
    }

    /** Tabs and panes spec 10 and 11 (workspace plan 8b Rulings 3): a split item's id names its two actions. */
    @Test void aSplitItemsIdNamesItsRightAndDownActions() {
        var editor = SplitItemSpec.of("dev.jasper.editor.editor", "Editor").withIcon("file");
        assertThat(editor.rightAction()).isEqualTo("dev.jasper.editor.split_right_editor");
        assertThat(editor.downAction()).isEqualTo("dev.jasper.editor.split_down_editor");
        assertThat(editor.icon()).contains("file");
        var longest = SplitItemSpec.of("dev.x." + "a".repeat(110), "Long");
        assertThat(ActionSpec.of(longest.rightAction(), "Split Right with Long").id()).as("still an action id").hasSize(128);
        for (String id : new String[]{"", "nodot", "Upper.case", "dev.x." + "a".repeat(111)})
            assertThatIllegalArgumentException().as(id).isThrownBy(() -> SplitItemSpec.of(id, "Label"));
        assertThatIllegalArgumentException().isThrownBy(() -> SplitItemSpec.of("dev.x.notes", " "));
        assertThatNullPointerException().isThrownBy(() -> new SplitItemSpec("dev.x.notes", "Notes", null, false));
        assertThat(SplitChoice.TERMINAL).isEqualTo(new SplitChoice("Terminal", "split_right", "split_down", false));
    }

    /**
     * Pane flexibility spec 3 and workspace plan 9a Rulings 1: an item whose actions ask something first says so, its
     * copies keep it, and its entries end with an ellipsis; one that opens at once, the terminal's included, has none.
     */
    @Test void anAskingSplitItemsEntriesEndWithAnEllipsis() {
        var openFile = SplitItemSpec.of("dev.jasper.editor.open-file", "Open File").asking();
        assertThat(openFile.asks()).isTrue();
        assertThat(openFile.withIcon("file").asks()).as("a copy with an icon keeps it").isTrue();
        assertThat(openFile.withIcon("file").icon()).contains("file");
        assertThat(SplitItemSpec.of("dev.jasper.editor.editor", "Editor").withIcon("file").asks()).as("an item that opens at once")
            .isFalse();
        assertThat(openFile.rightAction()).isEqualTo("dev.jasper.editor.split_right_open-file");
        assertThat(ActionSpec.of(openFile.downAction(), "Split Down with Open File…").id()).as("an action id")
            .isEqualTo("dev.jasper.editor.split_down_open-file");
        var choice = new SplitChoice("Open File", openFile.rightAction(), openFile.downAction(), true);
        assertThat(choice.rightLabel()).isEqualTo("Open File Right…");
        assertThat(choice.downLabel()).isEqualTo("Open File Down…");
        assertThat(SplitChoice.TERMINAL.rightLabel()).isEqualTo("Terminal Right");
        assertThat(SplitChoice.TERMINAL.downLabel()).isEqualTo("Terminal Down");
    }

    @Test void actionSpecValidatesAndCopies() {
        var keywords = new ArrayList<>(List.of("connect"));
        ActionSpec spec = ActionSpec.of("dev.x.tool.run", "Run Tool").withKeywords(keywords).withDefaultBinding("cmd+alt+j");
        keywords.add("later");
        assertThat(spec.keywords()).containsExactly("connect");
        assertThat(spec.defaultBinding()).hasValue("cmd+alt+j");
        assertThat(spec.icon()).isEmpty();
        assertThat(ActionSpec.of("dev.x.tool.run", "Run").withDefaultBinding(null).defaultBinding()).isEmpty();
        for (String id : new String[]{"", "Upper.case", "nodot", "sp ace.x"})
            assertThatIllegalArgumentException().as(id).isThrownBy(() -> ActionSpec.of(id, "Title"));
        assertThatIllegalArgumentException().isThrownBy(() -> ActionSpec.of("dev.x.run", " "));
        assertThatNullPointerException().isThrownBy(() ->
            new ActionSpec("dev.x.run", "Run", null, List.of(), Optional.empty()));
    }

    @Test void actionSpecCarriesPlatformDefaultsAndAShortcutContext() {
        var spec = ActionSpec.of("dev.x.tool.run", "Run");
        assertThat(spec.otherDefaultBinding()).isEmpty();
        assertThat(spec.context()).isEqualTo(ShortcutContext.global());
        var paired = spec.withDefaultBindings("cmd+alt+1", "alt+1").withContext(ShortcutContext.panel("dev.x.tool.hosts"))
            .withKeywords(List.of("hosts")).withIcon("file");
        assertThat(paired.defaultBinding()).hasValue("cmd+alt+1");
        assertThat(paired.otherDefaultBinding()).hasValue("alt+1");
        assertThat(paired.context()).isEqualTo(ShortcutContext.panel("dev.x.tool.hosts"));
        assertThat(paired.keywords()).containsExactly("hosts");
        assertThat(spec.withDefaultBindings("cmd+m", null).otherDefaultBinding()).as("no default off macOS").hasValue("none");
        assertThat(paired.withDefaultBinding("cmd+j").otherDefaultBinding()).as("one default again").isEmpty();
        assertThat(new ActionSpec("dev.x.run", "Run", Optional.empty(), List.of(), Optional.of("cmd+j")).context())
            .isEqualTo(ShortcutContext.global());
        assertThatNullPointerException().isThrownBy(() -> spec.withContext(null));
    }

    /** SDK 0.18: a toolbar view is a factory the host asks once per window. */
    @Test void aToolbarViewNeedsAFactory() {
        ToolbarViewFactory factory = window -> { throw new AssertionError("not called"); };
        assertThat(ToolbarItem.view(factory)).isEqualTo(new ToolbarItem.View(factory));
        assertThat(((ToolbarItem.View) ToolbarItem.view(factory)).factory()).isSameAs(factory);
        assertThatNullPointerException().isThrownBy(() -> new ToolbarItem.View(null));
    }

    @Test void toolbarItemsAndStatusSpecsValidate() {
        assertThat(ToolbarItem.action("dev.x.run")).isEqualTo(new ToolbarItem.Button("dev.x.run"));
        var ids = new ArrayList<>(List.of("dev.x.a", "dev.x.b"));
        var dropdown = (ToolbarItem.Dropdown) ToolbarItem.menu("file", "Hosts", ids);
        ids.clear();
        assertThat(dropdown.actionIds()).containsExactly("dev.x.a", "dev.x.b");
        assertThatIllegalArgumentException().isThrownBy(() -> ToolbarItem.menu("file", " ", List.of("dev.x.a")));
        assertThatIllegalArgumentException().isThrownBy(() -> ToolbarItem.menu("file", "Hosts", List.of()));
        assertThatNullPointerException().isThrownBy(() -> ToolbarItem.menu(null, "Hosts", List.of("dev.x.a")));
        assertThatNullPointerException().isThrownBy(() -> ToolbarItem.action(null));

        assertThat(new StatusItemSpec("dev.x.lock", Side.RIGHT, 10).side()).isEqualTo(Side.RIGHT);
        assertThatIllegalArgumentException().isThrownBy(() -> new StatusItemSpec("nodot", Side.LEFT, 0));
        assertThatNullPointerException().isThrownBy(() -> new StatusItemSpec("dev.x.lock", null, 0));
    }

    @Test void theEditorHookIsADefaultThatAnOlderHostIgnores() throws Exception {
        assertThat(Platform.class.getMethod("editor", FileEditor.class).isDefault()).isTrue();
        Platform older = (Platform) java.lang.reflect.Proxy.newProxyInstance(Platform.class.getClassLoader(), new Class<?>[]{Platform.class},
            (proxy, method, args) -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args) : null);
        var asked = new java.util.concurrent.atomic.AtomicInteger();
        older.editor((file, line, window) -> {
            asked.incrementAndGet();
            return true;
        }).close();
        assertThat(asked).as("an older host never asks").hasValue(0);
        assertThatNullPointerException().isThrownBy(() -> older.editor(null));
    }
}
