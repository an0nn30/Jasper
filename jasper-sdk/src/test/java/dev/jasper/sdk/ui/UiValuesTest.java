package dev.jasper.sdk.ui;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UiValuesTest {
    /** The PNG signature followed by {@code length - 8} zero bytes: a PNG to the SDK, which reads only the signature. */
    static byte[] png(int length) {
        byte[] data = new byte[length];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, 0, data, 0, 8);
        return data;
    }

    /** Plan 4b Task 1 (SDK 0.27.0): a pixel image is a PNG of at most 65,536 bytes, copied in and out. */
    @Test void aPixelImageIsAPngOfAtMost65536BytesCopiedInAndOut() {
        byte[] data = png(64);
        data[20] = 7;
        var image = new PixelImage(data);
        data[20] = 9;
        assertThat(image.png()[20]).as("copied in").isEqualTo((byte) 7);
        image.png()[20] = 9;
        assertThat(image.png()[20]).as("copied out").isEqualTo((byte) 7);
        assertThat(image).isEqualTo(new PixelImage(image.png())).hasSameHashCodeAs(new PixelImage(image.png()));
        assertThat(image).isNotEqualTo(new PixelImage(png(64)));
        assertThat(image).hasToString("PixelImage[64 bytes]");
        assertThat(new PixelImage(png(65_536)).png()).hasSize(65_536);
        assertThatIllegalArgumentException().isThrownBy(() -> new PixelImage(png(65_537)))
            .withMessage("A pixel image holds at most 65536 bytes, not 65537");
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0};
        for (byte[] refused : List.of(gif, new byte[0], java.util.Arrays.copyOf(png(8), 7)))
            assertThatIllegalArgumentException().isThrownBy(() -> new PixelImage(refused)).withMessage("A pixel image must be a PNG");
        assertThatNullPointerException().isThrownBy(() -> new PixelImage(null));
    }

    /** Plan 4b Task 1 (SDK 0.27.0): a content tab shows its presentation's image, and the icon name stays the fallback. */
    @Test void aContentPresentationsImageKeepsItsIconAsTheFallback() {
        var plain = new ContentPresentation("Example", "example.com", "file", true, "https://example.com/", "example.com");
        assertThat(plain.image()).isEmpty();
        var image = new PixelImage(png(32));
        var shown = plain.withImage(image);
        assertThat(shown.image()).contains(image);
        assertThat(shown).isEqualTo(new ContentPresentation("Example", "example.com", "file", true, "https://example.com/", "example.com",
            Optional.of(image)));
        assertThat(shown.icon()).isEqualTo("file");
        assertThat(new ContentPresentation("Example", "", null, false, "", "").withImage(image).icon()).isNull();
        assertThatNullPointerException().isThrownBy(() -> plain.withImage(null));
        assertThatNullPointerException().isThrownBy(() -> new ContentPresentation("Example", "", "file", false, "", "", null));
    }

    /** Vim IdeaVim keys spec 4: a status item may carry an icon, a dimmed state and a menu. */
    @Test void contentStatusCarriesAnIconADimmedStateAndACopiedMenu() {
        Runnable r = () -> {};
        var plain = new ContentStatus("Ln 1", "Caret", null);
        assertThat(plain.icon()).isEmpty();
        assertThat(plain.dimmed()).isFalse();
        assertThat(plain.menu()).isEmpty();
        var check = ContentStatus.MenuEntry.check("Enabled", true, r);
        assertThat(check.checkable()).isTrue();
        assertThat(check.checked()).isTrue();
        assertThat(check.run()).isSameAs(r);
        assertThat(ContentStatus.MenuEntry.item("Open", r).checkable()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> ContentStatus.MenuEntry.item(" ", r));
        assertThatNullPointerException().isThrownBy(() -> ContentStatus.MenuEntry.item("Open", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ContentStatus.MenuEntry("Open", r, false, true, false));
        var separator = ContentStatus.MenuEntry.separatorLine();
        assertThat(separator.separator()).isTrue();
        assertThat(separator.run()).isNull();
        var entries = new ArrayList<ContentStatus.MenuEntry>(List.of(check, separator));
        var status = new ContentStatus("NORMAL", "Vim mode", null, "dev.jasper.test:v", true, entries);
        entries.clear();
        assertThat(status.menu()).containsExactly(check, separator);
        assertThat(status.icon()).isEqualTo("dev.jasper.test:v");
        assertThat(status.dimmed()).isTrue();
        assertThatNullPointerException().isThrownBy(() -> new ContentStatus("A", "A", null, null, false, List.of()));
    }

    /** The Editor's Color scheme setting: an editor scheme's id is its theme's id and its variant. */
    @Test void editorSchemeChecksItsId() {
        var c = java.awt.Color.GRAY;
        var editor = new EditorPalette(c, c, c, c, c, c, c);
        var styles = new java.util.EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        for (var role : SyntaxRole.values()) styles.put(role, new SyntaxStyle(c, false, false));
        var syntax = new SyntaxPalette(styles);
        var scheme = new EditorScheme("jasper-meadow/dark", "Jasper Meadow", dev.jasper.sdk.Variant.DARK, editor, syntax);
        assertThat(scheme.id()).isEqualTo("jasper-meadow/dark");
        assertThat(new EditorScheme("catppuccin-latte/light", "Latte", dev.jasper.sdk.Variant.LIGHT, editor, syntax).variant())
            .isEqualTo(dev.jasper.sdk.Variant.LIGHT);
        for (String id : new String[]{"jasper-meadow/light", "Meadow/dark", "jasper-meadow", "/dark", "a/dark/x"})
            assertThatIllegalArgumentException().as(id).isThrownBy(() -> new EditorScheme(id, "Meadow", dev.jasper.sdk.Variant.DARK, editor, syntax));
        assertThatIllegalArgumentException().isThrownBy(() -> new EditorScheme("a/dark", " ", dev.jasper.sdk.Variant.DARK, editor, syntax));
        assertThatNullPointerException().isThrownBy(() -> new EditorScheme("a/dark", "A", dev.jasper.sdk.Variant.DARK, null, syntax));
    }

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

    /** Pane flexibility spec 4: a tab choice is numbered from 1 and worded "N title", or "Tab N" without a title. */
    @Test void aTabChoiceIsNumberedFromOneAndWordedNumberThenTitle() {
        assertThat(new TabChoice(3, "notes.txt").label()).isEqualTo("3 notes.txt");
        assertThat(new TabChoice(2, "").label()).as("a title the plugin may not read").isEqualTo("Tab 2");
        assertThatIllegalArgumentException().isThrownBy(() -> new TabChoice(0, "zsh"));
        assertThatNullPointerException().isThrownBy(() -> new TabChoice(1, null));
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

    /** Browser plan 3: the browser hook is a default an older host ignores, and an address comes from a click or from outside Jasper. */
    @Test void theBrowserHookIsADefaultThatAnOlderHostIgnores() throws Exception {
        assertThat(Platform.class.getMethod("browser", WebBrowser.class).isDefault()).isTrue();
        Platform older = (Platform) java.lang.reflect.Proxy.newProxyInstance(Platform.class.getClassLoader(), new Class<?>[]{Platform.class},
            (proxy, method, args) -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args) : null);
        var asked = new java.util.concurrent.atomic.AtomicInteger();
        older.browser(link -> {
            asked.incrementAndGet();
            return true;
        }).close();
        assertThat(asked).as("an older host never asks").hasValue(0);
        assertThatNullPointerException().isThrownBy(() -> older.browser(null));
        assertThat(LinkOrigin.values()).containsExactly(LinkOrigin.CLICK, LinkOrigin.EXTERNAL);
        assertThat(LinkPlacement.values()).containsExactly(LinkPlacement.TAB, LinkPlacement.SPLIT_RIGHT, LinkPlacement.SPLIT_DOWN);
    }

    /**
     * Browser plan 4a: opening a file, content menus and the modal query are defaults that an older host refuses, and a
     * next tab is a placement of its own.
     */
    @Test void theBrowserCoreMembersAreDefaultsThatAnOlderHostRefuses() throws Exception {
        assertThat(Platform.class.getMethod("openFile", Path.class).isDefault()).isTrue();
        assertThat(Menus.class.getMethod("createForContent", String.class, String.class).isDefault()).isTrue();
        assertThat(Windows.class.getMethod("hasModal", dev.jasper.sdk.WindowOwner.class).isDefault()).isTrue();
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> older(Platform.class).openFile(Path.of("/tmp/index.html")));
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> older(Menus.class).createForContent("dev.x.web", "Web"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> older(Windows.class).hasModal(new dev.jasper.sdk.WindowOwner() { }));
        assertThat(TabPlacement.values()).containsExactly(TabPlacement.NEW_TAB, TabPlacement.NEXT_TAB, TabPlacement.SPLIT_RIGHT,
            TabPlacement.SPLIT_DOWN, TabPlacement.NEW_WINDOW);
    }

    /** A host that implements only the abstract members of {@code type}, as one built for an older SDK does. */
    private static <T> T older(Class<T> type) {
        return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args) : null));
    }

    /** Browser plan 3b: a web link is an http or https address with a host, as the user clicked or an outside program gave it. */
    @Test void aWebLinkTakesHttpAndHttpsAddressesWithAHost() {
        var link = new WebLink(URI.create("https://example.com/a"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty());
        assertThat(link.address()).hasToString("https://example.com/a");
        assertThat(new WebLink(URI.create("HTTP://example.com/"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty()).address())
            .as("the scheme is case-insensitive").hasToString("HTTP://example.com/");
        assertThatIllegalArgumentException().isThrownBy(() -> new WebLink(URI.create("mailto:a@b.c"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new WebLink(URI.create("https:///x"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty()));
        assertThatIllegalArgumentException().as("a relative address").isThrownBy(() -> new WebLink(URI.create("/x"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty()));
        assertThatIllegalArgumentException().as("an authority that is no host").isThrownBy(() -> new WebLink(URI.create("http://my_host/"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new WebLink(null, LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new WebLink(URI.create("https://e.com"), null, LinkPlacement.TAB, Optional.empty(), Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new WebLink(URI.create("https://e.com"), LinkOrigin.CLICK, null, Optional.empty(), Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new WebLink(URI.create("https://e.com"), LinkOrigin.CLICK, LinkPlacement.TAB, null, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new WebLink(URI.create("https://e.com"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), null));
    }

    @Test void aWebLinkTakesALocalFileOnlyAsAClickInATab() {
        URI file = Path.of("/tmp/site/index.html").toUri();
        assertThatCode(() -> new WebLink(file, LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty())).doesNotThrowAnyException();
        assertThatIllegalArgumentException().isThrownBy(() -> new WebLink(file, LinkOrigin.EXTERNAL, LinkPlacement.TAB, Optional.empty(), Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new WebLink(file, LinkOrigin.CLICK, LinkPlacement.SPLIT_RIGHT, Optional.empty(), Optional.empty()));
        assertThatIllegalArgumentException().as("a relative file address").isThrownBy(() -> new WebLink(URI.create("file:site/index.html"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty()));
    }

    @Test void aTargetIsOneToTwoHundredFiftySixCharactersWithoutControls() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WebLink(URI.create("https://e.com"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.of(""), Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new WebLink(URI.create("https://e.com"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.of("a\nb"), Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new WebLink(URI.create("https://e.com"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.of("x".repeat(257)), Optional.empty()));
        assertThat(new WebLink(URI.create("https://e.com"), LinkOrigin.CLICK, LinkPlacement.TAB, Optional.of("x".repeat(256)), Optional.empty()).target()).hasValueSatisfying(target -> assertThat(target).hasSize(256));
        assertThat(new WebLink(URI.create("https://e.com"), LinkOrigin.CLICK, LinkPlacement.SPLIT_DOWN, Optional.of("docs"), Optional.empty()).target().orElseThrow()).isEqualTo("docs");
    }

    @Test void anExternalLinkHasNoTargetAndOpensInATab() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WebLink(URI.create("https://e.com"), LinkOrigin.EXTERNAL, LinkPlacement.SPLIT_DOWN, Optional.empty(), Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new WebLink(URI.create("https://e.com"), LinkOrigin.EXTERNAL, LinkPlacement.TAB, Optional.of("t"), Optional.empty()));
        assertThat(new WebLink(URI.create("https://e.com"), LinkOrigin.EXTERNAL, LinkPlacement.TAB, Optional.empty(), Optional.empty()).origin()).isEqualTo(LinkOrigin.EXTERNAL);
    }
}
