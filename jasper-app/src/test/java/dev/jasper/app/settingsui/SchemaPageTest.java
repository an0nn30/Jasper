package dev.jasper.app.settingsui;

import static dev.jasper.app.settingsui.PageKit.kit;
import static dev.jasper.app.settingsui.PageKit.node;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class SchemaPageTest {
    private final SettingsDraft draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "[window]\ntab_height = 44\n", true).values());
    private final List<String> navigated = new ArrayList<>();
    private final int[] changes = new int[1];
    private final PageContext context = PageKit.context(draft, navigated::add, () -> changes[0]++);

    private SchemaPage page() {
        return SchemaPage.page("window", "Window").parent("appearance-behavior").anchor("window-anchor")
            .section("Bars")
            .row(CoreSettings.WINDOW_STATUS_BAR)
            .row(CoreSettings.WINDOW_TAB_HEIGHT, Editors.integer("px"))
            .row(CoreSettings.TERMINAL_FONT_FAMILY, Editors.text()).note("Missing fonts fall back.")
            .row(CoreSettings.WINDOW_COLUMNS)
            .build(context);
    }

    private static boolean checked(SchemaPage page, String id) { return ((Nodes.Toggle) node(page, id)).checked(); }

    @Test void cleanupClosesViewBeforeEditorsAndContinuesAfterFailureExactlyOnce() {
        var calls=new ArrayList<String>();var mounted=new dev.jasper.ui.swing.KitView[1];
        var factory=new Editors.Factory<String>((setting,ctx)->new ValueEditor<>() {
            public Nodes.Node node(String id,dev.jasper.ui.model.ControlState state){return new Nodes.Text(id,state,"Value",Nodes.TextRole.BODY);}
            public String value(){return "system";}public void show(String value){}public boolean event(String id,dev.jasper.ui.model.UiEvent event){return false;}
            public void dispose(){assertThat(mounted[0].isClosed()).isTrue();calls.add(setting.name());throw new IllegalStateException(setting.name());}
        },List.of());
        var page=SchemaPage.page("cleanup","Cleanup").row(CoreSettings.TERMINAL_FONT_FAMILY,factory).row(CoreSettings.UI_FONT_FAMILY,factory).build(context);
        page.component();mounted[0]=page.view();
        assertThatThrownBy(page::dispose).isInstanceOf(IllegalStateException.class).satisfies(failure->assertThat(failure.getSuppressed()).hasSize(1));
        page.dispose();assertThat(calls).containsExactly(CoreSettings.TERMINAL_FONT_FAMILY.name(),CoreSettings.UI_FONT_FAMILY.name());
    }
    @Test void aPartIsItsOwnSectionWithItsEventsRefreshAndDisposal() {
        var calls = new ArrayList<String>();
        var shown = new String[] {"none"};
        Runnable[] render = new Runnable[1];
        var part = new SchemaPage.Part("extras", "Extras", (pageContext, again) -> {
            assertThat(pageContext).isSameAs(context);
            render[0] = again;
            return new SchemaPage.Part.Section(
                id -> new Nodes.Stack(id, dev.jasper.ui.model.ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
                    new Nodes.Text(id + ":text", dev.jasper.ui.model.ControlState.READY, "Shown: " + shown[0], Nodes.TextRole.BODY),
                    new Nodes.Button(id + ":go", dev.jasper.ui.model.ControlState.READY, "Go", "", Nodes.ButtonKind.SECONDARY, id + ":go", ""))),
                event -> event instanceof dev.jasper.ui.model.UiEvent.Action action && action.id().equals("extras:go") && calls.add("go"),
                () -> calls.add("refresh"), () -> calls.add("dispose"));
        }, List.of("extra words"));
        var page = SchemaPage.page("parts", "Parts").section("Bars").row(CoreSettings.WINDOW_STATUS_BAR).part(part)
            .row(CoreSettings.WINDOW_TAB_HEIGHT, Editors.integer("px")).build(context);
        assertThat(page.searchTerms()).contains("Extras", "extra words");
        page.component();
        var stack = (Nodes.Stack) page.viewSpec().root();
        assertThat(stack.children()).as("Bars, then the part, then an untitled card of the rows after it").hasSize(3);
        assertThat(((Nodes.Section) stack.children().get(0)).title()).isEqualTo("Bars");
        assertThat(stack.children().get(1)).isInstanceOfSatisfying(Nodes.Section.class, section -> {
            assertThat(section.id()).isEqualTo("section-extras");
            assertThat(section.title()).isEqualTo("Extras");
        });
        assertThat(stack.children().get(2)).isInstanceOf(Nodes.Card.class);
        kit(page, "extras:go", JButton.class).doClick();
        assertThat(calls).containsExactly("go");
        shown[0] = "later";
        render[0].run();
        assertThat(((Nodes.Text) node(page, "extras:text")).text()).isEqualTo("Shown: later");
        page.refresh();
        assertThat(calls).containsExactly("go", "refresh");
        page.dispose();
        assertThat(calls).containsExactly("go", "refresh", "dispose");
    }

    @Test void rowsShowTheDraftAndStageEdits() {
        var page = page();
        page.component();
        assertThat(page.rows()).hasSize(4);
        assertThat(node(page, "row:window.status_bar")).isInstanceOf(Nodes.SettingRow.class);
        assertThat(checked(page, "window.status_bar")).isTrue();
        kit(page, "window.status_bar", JButton.class).doClick();
        assertThat(draft.value(CoreSettings.WINDOW_STATUS_BAR)).isFalse();
        assertThat(checked(page, "window.status_bar")).isFalse();
        assertThat(page.modified()).isTrue();
        page.reset();
        assertThat(page.modified()).isFalse();
        assertThat(checked(page, "window.status_bar")).as("reset shows the saved value again").isTrue();
        assertThat(kit(page, "window.tab_height.input", JTextField.class).getText()).isEqualTo("44");
    }

    @Test void invalidValuesShowTheLoadersMessageAndAreNotStaged() {
        var page = page();
        var font = kit(page, "terminal.font.family.input", JTextField.class);
        font.setText("   ");
        var error = kit(page, "terminal.font.family.error", JLabel.class);
        assertThat(error.getText()).isEqualTo("Use a nonblank font name without NUL; using the default.");
        assertThat(error.isVisible()).isTrue();
        assertThat(page.rows().get(2).error()).isEqualTo(error.getText());
        assertThat(page.valid()).isFalse();
        assertThat(draft.modified(CoreSettings.TERMINAL_FONT_FAMILY)).isFalse();
        assertThat(changes[0]).isPositive();
        assertThat(font.getText()).as("the typed text stays for correcting").isEqualTo("   ");
        font.setText("Iosevka");
        assertThat(page.valid()).isTrue();
        assertThat(error.isVisible()).isFalse();
        assertThat(draft.value(CoreSettings.TERMINAL_FONT_FAMILY)).isEqualTo("Iosevka");
    }

    @Test void numbersRejectWhatIsNoNumberOrOutOfRangeWithoutStaging() {
        var page = page();
        var height = kit(page, "window.tab_height.input", JTextField.class);
        assertThat(((Nodes.Field) node(page, "window.tab_height")).numeric()).contains(new Nodes.Numeric(20, 72, 1, 0));
        height.setText("abc");
        assertThat(height.getText()).as("letters never enter a number field").isEqualTo("44");
        height.setText("");
        assertThat(page.rows().get(1).error()).isEqualTo("Not a valid value.");
        assertThat(page.valid()).isFalse();
        height.setText("99");
        assertThat(page.rows().get(1).error()).as("the schema keeps range authority").isNotBlank().isNotEqualTo("Not a valid value.");
        assertThat(draft.value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(44);
        height.setText("50");
        assertThat(page.valid()).isTrue();
        assertThat(draft.value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(50);
    }

    @Test void resetLinksAppearForNonDefaultValuesAndRemoveTheKey() {
        var page = page();
        JButton reset = kit(page, "window.tab_height:reset", JButton.class);
        assertThat(reset.isVisible()).as("44 is not the default 30").isTrue();
        assertThat(reset.getAccessibleContext().getAccessibleName()).isEqualTo("Reset Tab height to default");
        assertThat(kit(page, "window.status_bar:reset", JButton.class).isVisible()).isFalse();
        reset.doClick();
        assertThat(draft.value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(30);
        assertThat(draft.edit().changes()).containsEntry(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty());
        assertThat(reset.isVisible()).isFalse();
        assertThat(kit(page, "window.tab_height.input", JTextField.class).getText()).isEqualTo("30");
    }

    @Test void commentsSayWhenAChangeAppliesAndIncludeNotes() {
        var page = page();
        page.component();
        assertThat(page.rows().get(3).comment()).isEqualTo("Applies to new windows");
        assertThat(page.rows().get(2).comment()).isEqualTo("Missing fonts fall back.");
        assertThat(((Nodes.SettingRow) node(page, "row:window.columns")).description()).isEqualTo("Applies to new windows");
        assertThat(kit(page, "row:window.columns.description", JComponent.class).getAccessibleContext().getAccessibleName())
            .isEqualTo("Applies to new windows");
        assertThat(kit(page, "row:window.status_bar.description", JComponent.class).isVisible()).isFalse();
    }

    @Test void searchTermsAndHighlightCoverLabelsDescriptionsAndKeywords() {
        var page = page();
        assertThat(page.searchTerms()).contains("Window", "Show status bar", "Tab height");
        assertThat(String.join(" ", page.searchTerms())).contains("footer");
        page.component();
        page.highlight("footer");
        assertThat(((Nodes.SettingRow) node(page, "row:window.status_bar")).highlighted()).isTrue();
        assertThat(((Nodes.SettingRow) node(page, "row:window.tab_height")).highlighted()).isFalse();
        assertThat(kit(page, "row:window.status_bar", JComponent.class).getAccessibleContext().getAccessibleDescription()).isEqualTo("Matches search");
        page.highlight("");
        assertThat(((Nodes.SettingRow) node(page, "row:window.status_bar")).highlighted()).isFalse();
        assertThat(page.helpAnchor()).isEqualTo("window-anchor");
        assertThat(page.parent()).hasValue("appearance-behavior");
    }

    @Test void highlightingScrollsTheFirstMatchIntoViewInTheDialogsScroller() {
        var spec = SchemaPage.page("long", "Long");
        for (var setting : List.of(CoreSettings.WINDOW_TAB_HEIGHT, CoreSettings.WINDOW_RAIL_WIDTH, CoreSettings.WINDOW_COLUMNS,
                CoreSettings.WINDOW_LINES, CoreSettings.TERMINAL_SCROLLBACK, CoreSettings.TERMINAL_COPY_ON_SELECT,
                CoreSettings.PALETTE_VISIBLE_ROWS, CoreSettings.BUDDY_ENABLED, CoreSettings.TERMINAL_CURSOR_BLINK, CoreSettings.WINDOW_STATUS_BAR))
            spec.row(setting);
        var page = spec.build(context);
        var scroller = new javax.swing.JScrollPane(page.component());
        scroller.setSize(600, 160);
        for (int i = 0; i < 3; i++) KitTestSupport.layout(scroller);
        assertThat(scroller.getViewport().getViewPosition().y).isZero();
        page.highlight("footer");
        for (int i = 0; i < 2; i++) KitTestSupport.layout(scroller);
        var row = kit(page, "row:window.status_bar", JComponent.class);
        var bounds = javax.swing.SwingUtilities.convertRectangle(row.getParent(), row.getBounds(), scroller.getViewport().getView());
        assertThat(scroller.getViewport().getViewRect().intersects(bounds)).as("the matching row is visible").isTrue();
        assertThat(scroller.getViewport().getViewPosition().y).isPositive();
    }

    @Test void anActionRowIsASettingsRowWithItsButtonsAtTheEnd() {
        boolean[] enabled = {false};
        int[] runs = {0};
        var page = SchemaPage.page("system", "System Settings")
            .action("Migrate old settings", () -> "Rewrite old names.", new SchemaPage.Button("Migrate\u2026", () -> runs[0]++, () -> enabled[0], "Nothing to migrate"))
            .build(context);
        page.component();
        var row = (Nodes.SettingRow) ((Nodes.Card) ((Nodes.Stack) page.viewSpec().root()).children().getFirst()).children().getFirst();
        assertThat(row.title()).isEqualTo("Migrate old settings");
        assertThat(row.description()).isEqualTo("Rewrite old names.");
        JButton migrate = kit(page, page.actionButtons().getFirst(), JButton.class);
        assertThat(migrate.getText()).isEqualTo("Migrate\u2026");
        assertThat(migrate.isEnabled()).isFalse();
        assertThat(migrate.getToolTipText()).isEqualTo("Nothing to migrate");
        enabled[0] = true;
        page.refresh();
        assertThat(migrate.isEnabled()).isTrue();
        assertThat(migrate.getToolTipText()).isNull();
        migrate.doClick();
        assertThat(runs[0]).isEqualTo(1);
    }

    /** A long action description wraps beside or above its buttons and never under them. */
    @Test void aLongActionDescriptionWrapsBesideOrAboveItsButtonsInsideThePage() {
        String text = "Rewrite every option that an older Jasper wrote under a former name, keeping the file's comments and order. ".repeat(3).strip();
        var page = SchemaPage.page("system", "System Settings")
            .action("Migrate old settings", () -> text, new SchemaPage.Button("Migrate\u2026", () -> { }, () -> true))
            .build(context);
        var body = page.component();
        body.setSize(420, 600);
        for (int pass = 0; pass < 3; pass++) KitTestSupport.layout(body);
        // The row's description is its wrapped text, a settings row's description (kit id <row>.description).
        var label = kit(page, "action-0.description", javax.swing.JComponent.class);
        assertThat(label.getHeight()).as("the description wraps over several lines").isGreaterThan(3 * label.getFontMetrics(label.getFont()).getHeight() - 1);
        JButton migrate = kit(page, page.actionButtons().getFirst(), JButton.class);
        var labelBounds = javax.swing.SwingUtilities.convertRectangle(label.getParent(), label.getBounds(), body);
        var buttonBounds = javax.swing.SwingUtilities.convertRectangle(migrate.getParent(), migrate.getBounds(), body);
        assertThat(labelBounds.intersects(buttonBounds)).as("the button never covers the text").isFalse();
        assertThat(buttonBounds.x + buttonBounds.width).as("the button stays inside the page").isLessThanOrEqualTo(body.getWidth());
    }

    private static javax.swing.JLabel labelWithText(java.awt.Component root, String text) {
        if (root instanceof javax.swing.JLabel label && text.equals(label.getText())) return label;
        if (root instanceof java.awt.Container container) for (var child : container.getComponents()) {
            var found = labelWithText(child, text);
            if (found != null) return found;
        }
        return null;
    }

    @Test void groupPagesLinkToTheirChildren() {
        var child = page();
        var group = new GroupPage("appearance-behavior", "Appearance & Behavior", Optional.empty(), false, () -> List.of(child), context);
        var link = KitTestSupport.require(group.component(), "link:window", JButton.class);
        assertThat(link.getText()).isEqualTo("Window");
        assertThat(((Nodes.Button) ((Nodes.Stack) group.view().spec().root()).children().get(1)).kind()).isEqualTo(Nodes.ButtonKind.LINK);
        assertThat(KitTestSupport.texts(group.component())).contains("Appearance & Behavior");
        link.doClick();
        assertThat(navigated).containsExactly("window");
        assertThat(group.modified()).isFalse();
        var tools = new GroupPage("tools", "Tools", Optional.empty(), true, List::of, context);
        assertThat(tools.hiddenWhenChildless()).isTrue();
    }

    @Test void aConditionEnablesARowOnlyWhileItHolds() {
        var page = SchemaPage.page("bars", "Bars")
            .row(CoreSettings.WINDOW_STATUS_BAR)
            .row(CoreSettings.WINDOW_STATUS_BAR_HEIGHT, Editors.integer("px")).enabledWhen(d -> d.value(CoreSettings.WINDOW_STATUS_BAR))
            .build(context);
        var height = kit(page, "window.status_bar_height.input", JTextField.class);
        assertThat(height.isEnabled()).isTrue();
        kit(page, "window.status_bar", JButton.class).doClick();
        assertThat(node(page, "window.status_bar_height").state().enabled()).isFalse();
        assertThat(height.isEnabled()).as("the input itself").isFalse();
        page.reset();
        assertThat(height.isEnabled()).isTrue();
    }

    @Test void aRowShownOnlyElsewhereKeepsItsSettingButBuildsNoControl() {
        var spec = SchemaPage.page("window", "Window")
            .row(CoreSettings.WINDOW_STATUS_BAR)
            .row(CoreSettings.WINDOW_NATIVE_DECORATIONS).shownWhen(false);
        assertThat(spec.settings()).containsExactly(CoreSettings.WINDOW_STATUS_BAR, CoreSettings.WINDOW_NATIVE_DECORATIONS);
        var page = spec.build(context);
        page.component();
        assertThat(page.rows()).hasSize(1);
        assertThat(KitTestSupport.find(page.component(), "row:window.native_decorations", JComponent.class)).isNull();
        assertThat(page.searchTerms()).doesNotContain(CoreSettings.WINDOW_NATIVE_DECORATIONS.label());
    }

    @Test void aSectionWhoseRowsAreAllHiddenShowsNoHeader() {
        var spec = SchemaPage.page("window", "Window")
            .section("Bars")
            .row(CoreSettings.WINDOW_STATUS_BAR)
            .section("Elsewhere")
            .row(CoreSettings.WINDOW_NATIVE_DECORATIONS).shownWhen(false)
            .section("Empty")
            .section("Size")
            .row(CoreSettings.WINDOW_COLUMNS);
        var page = spec.build(context);
        assertThat(PageKit.sectionTitles(page)).containsExactly("Bars", "Empty", "Size");
        assertThat(page.searchTerms()).contains("Bars", "Empty", "Size").doesNotContain("Elsewhere");
    }

    @Test void aNarrowPageScrollsSidewaysRatherThanSquashingItsLists() {
        draft.rebase(ConfigLoader.parse(Path.of("config.toml"), "[terminal.shell]\nargs = ['-l', '-i']\n[terminal.env]\nA = '1'\n", true).values());
        var page = SchemaPage.page("shell", "Shell")
            .row(CoreSettings.TERMINAL_SHELL_ARGS, Editors.textList())
            .row(CoreSettings.TERMINAL_ENV, Editors.table())
            .build(context);
        var viewport = new javax.swing.JScrollPane(page.component());
        for (int width : new int[]{300, 1400}) {
            viewport.setSize(width, 900);
            for (int i = 0; i < 3; i++) KitTestSupport.layout(viewport);
            for (String editor : List.of("terminal.shell.args", "terminal.env"))
                assertThat(kit(page, editor, JComponent.class).getSize()).as(editor + " at " + width + " px")
                    .isEqualTo(kit(page, editor, JComponent.class).getPreferredSize());
        }
        viewport.setSize(300, 900);
        KitTestSupport.layout(viewport);
        assertThat(((javax.swing.Scrollable) page.component()).getScrollableTracksViewportWidth()).as("scrolls sideways").isFalse();
        viewport.setSize(1400, 900);
        KitTestSupport.layout(viewport);
        assertThat(((javax.swing.Scrollable) page.component()).getScrollableTracksViewportWidth()).isTrue();
    }

    /** Runs the input's own Enter binding as Swing does: only an enabled action consumes the key before the dialog's OK. */
    static boolean enter(JComponent input) {
        var name = input.getInputMap(JComponent.WHEN_FOCUSED).get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0));
        var action = name == null ? null : input.getActionMap().get(name);
        if (action == null || !action.isEnabled()) return false;
        action.actionPerformed(new java.awt.event.ActionEvent(input, 0, ""));
        return true;
    }

    @Test void enterInAListsEntryAddsItInsteadOfPressingOk() {
        var page = SchemaPage.page("shell", "Shell").row(CoreSettings.TERMINAL_SHELL_ARGS, Editors.textList()).build(context);
        var entry = kit(page, "terminal.shell.args:new.input", JTextField.class);
        assertThat(enter(entry)).as("an empty entry leaves Enter to the dialog").isFalse();
        entry.setText("-l");
        assertThat(enter(entry)).as("Enter is the entry's own").isTrue();
        assertThat(draft.value(CoreSettings.TERMINAL_SHELL_ARGS)).containsExactly("-l");
        assertThat(kit(page, "terminal.shell.args:new.input", JTextField.class).getText()).as("ready for the next").isEmpty();
    }

    /** Before Plan 2 the list was a JList, which binds no Enter key, so Enter on it pressed the dialog's OK. */
    @Test void enterOnAnOrderedListPassesToTheDialogAsTheOldListDid() {
        var page = SchemaPage.page("shell", "Shell").row(CoreSettings.TERMINAL_SHELL_ARGS, Editors.textList()).build(context);
        var list = KitTestSupport.require(page.component(), "terminal.shell.args", javax.swing.JScrollPane.class);
        var canvas = (javax.swing.JComponent) list.getViewport().getView();
        var enter = new java.awt.event.KeyEvent(canvas, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0, java.awt.event.KeyEvent.VK_ENTER, '\n');
        assertThat(javax.swing.SwingUtilities.processKeyBindings(enter)).as("the list does not take Enter; OK does").isFalse();
    }

    @Test void enterInAFontListsEntryAddsTheHighlightedFamily() {
        var page = SchemaPage.page("font", "Font").row(CoreSettings.TERMINAL_FONT_FALLBACK, Editors.fontList(false)).build(context);
        var before = draft.value(CoreSettings.TERMINAL_FONT_FALLBACK);
        var combo = kit(page, "terminal.font.fallback:new.input", javax.swing.JComboBox.class);
        var field = (JTextField) combo.getEditor().getEditorComponent();
        field.setText("monospaced");
        var enter = new java.awt.event.KeyEvent(field, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0, java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.CHAR_UNDEFINED);
        for (var listener : field.getKeyListeners()) listener.keyPressed(enter);
        assertThat(enter.isConsumed()).as("Enter never reaches OK").isTrue();
        var expected = new ArrayList<>(before); expected.add("Monospaced");
        assertThat(draft.value(CoreSettings.TERMINAL_FONT_FALLBACK)).containsExactlyElementsOf(expected);
    }

    @Test void aLongDescriptionWrapsInsteadOfWideningThePage() {
        int plain = SchemaPage.page("a", "A").row(CoreSettings.TERMINAL_FONT_FAMILY, Editors.text()).note("Short.")
            .build(context).component().getPreferredSize().width;
        var page = SchemaPage.page("b", "B").row(CoreSettings.TERMINAL_FONT_FAMILY, Editors.text())
            .note("A long description that goes on and on, ".repeat(12)).build(context);
        assertThat(page.component().getPreferredSize().width).isEqualTo(plain);
        var viewport = new javax.swing.JScrollPane(page.component());
        viewport.setSize(500, 900);
        for (int i = 0; i < 3; i++) KitTestSupport.layout(viewport);
        var comment = kit(page, "row:terminal.font.family.description", JComponent.class);
        assertThat(comment.getWidth()).isLessThanOrEqualTo(500);
        assertThat(comment.getHeight()).as("several lines").isGreaterThan(3 * comment.getFontMetrics(comment.getFont()).getHeight());
    }

    @Test void disposingClosesThePagesKitView() {
        var page = page();
        page.component();
        page.dispose();
        assertThat(page.view().isClosed()).isTrue();
    }
}
