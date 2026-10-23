package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.vim.Mode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.editor.testsupport.EdtTests.class)
class StatusLineTest {
    private final SessionEnvironment environment = new SessionEnvironment(Runnable::run, Runnable::run, Question::dismissed, () -> 1024);

    @Test void describesTheCaretLanguageEncodingEndingsIndentationAndWrap() {
        Languages.register();
        var session = DocumentSession.untitled("Untitled", environment);
        var tab = new EditorTab(new dev.jasper.sdk.testing.FakeKit("dev.jasper.editor"), session, closing -> { }, false);
        tab.area().setText("abc\ndef");
        tab.area().setCaretPosition(6);
        session.setStyle(Languages.TOML);
        session.setWrap(true);
        int[] clicked = {0, 0, 0};
        var status = new StatusLine(() -> clicked[0]++, () -> clicked[1]++, () -> clicked[2]++, () -> { }, () -> { }, () -> { });
        status.display(Optional.of(tab), 4, true, Mode.NORMAL);
        assertThat(status.positionText()).isEqualTo("Ln 2, Col 3");
        assertThat(status.languageText()).isEqualTo("TOML");
        assertThat(status.encodingText()).isEqualTo("UTF-8");
        assertThat(status.separatorText()).isEqualTo("LF");
        assertThat(status.indentText()).isEqualTo("Spaces: 4");
        assertThat(status.wrapSelected()).isTrue();
        assertThat(status.noticeText()).isEmpty();
        assertThat(status.vimSelected()).isTrue();
        status.items().getLast().menu().getFirst().run().run();
        assertThat(clicked[2]).isEqualTo(1);
        status.display(Optional.empty(), 4, false, Mode.NORMAL);
        assertThat(status.noticeText()).isEmpty();
    }

    /**
     * Plan 3 Task 8: compact status. The host status bar styles and spaces the items (caption text, its own gaps); the
     * Editor contributes short single-line descriptions in a stable order, only the three switchable ones actionable,
     * each item's tooltip its own text.
     */
    @Test void contributesCompactSingleLineItemsInAStableOrder() {
        Languages.register();
        var session = DocumentSession.untitled("Untitled", environment);
        var tab = new EditorTab(new dev.jasper.sdk.testing.FakeKit("dev.jasper.editor"), session, closing -> { }, false);
        tab.area().setText("abc\ndef");
        session.setStyle(Languages.TOML);
        var status = new StatusLine(() -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        status.display(Optional.of(tab), 4, false, Mode.NORMAL);
        var items = status.items();
        assertThat(items).extracting(dev.jasper.sdk.ui.ContentStatus::text)
            .containsExactly("Ln 2, Col 4", "TOML", "UTF-8", "LF", "Spaces: 4", "Wrap: Off", "");
        assertThat(items.subList(0, items.size() - 1)).allSatisfy(item -> {
            assertThat(item.text()).doesNotContain("\n").hasSizeLessThanOrEqualTo(16);
            assertThat(item.tooltip()).isEqualTo(item.text());
        });
        assertThat(items).filteredOn(item -> item.run() != null).extracting(dev.jasper.sdk.ui.ContentStatus::text)
            .containsExactly("TOML", "Wrap: Off");
        tab.dispose();
    }

    private EditorTab readyTab() {
        Languages.register();
        var session = DocumentSession.untitled("Untitled", environment);
        return new EditorTab(new dev.jasper.sdk.testing.FakeKit("dev.jasper.editor"), session, closing -> { }, false);
    }

    /** Vim IdeaVim keys spec 4: the widget shows the mode word IdeaVim's widget shows. */
    @Test void vimWidgetShowsTheMode() {
        var tab = readyTab();
        var status = new StatusLine(() -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        var words = new java.util.EnumMap<Mode, String>(Mode.class);
        words.put(Mode.NORMAL, "NORMAL"); words.put(Mode.OPERATOR_PENDING, "NORMAL"); words.put(Mode.INSERT, "INSERT");
        words.put(Mode.REPLACE, "REPLACE"); words.put(Mode.INSERT_NORMAL, "(INSERT)"); words.put(Mode.VISUAL, "VISUAL");
        words.put(Mode.VISUAL_LINE, "V-LINE"); words.put(Mode.VISUAL_BLOCK, "V-BLOCK"); words.put(Mode.COMMAND_LINE, "COMMAND");
        assertThat(words.keySet()).containsExactlyInAnyOrder(Mode.values());
        words.forEach((mode, word) -> {
            status.display(Optional.of(tab), 4, true, mode);
            var widget = status.items().getLast();
            assertThat(widget.text()).as(mode.name()).isEqualTo(word);
            assertThat(widget.tooltip()).isEqualTo("Vim mode");
            assertThat(widget.icon()).isEqualTo("dev.jasper.editor:vim");
            assertThat(widget.dimmed()).isFalse();
        });
        tab.dispose();
    }

    @Test void vimOffIsADimmedIcon() {
        var tab = readyTab();
        var status = new StatusLine(() -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        status.display(Optional.of(tab), 4, false, Mode.INSERT);
        var widget = status.items().getLast();
        assertThat(widget.text()).isEmpty();
        assertThat(widget.icon()).isEqualTo("dev.jasper.editor:vim");
        assertThat(widget.dimmed()).isTrue();
        assertThat(widget.menu().getFirst().checkable()).isTrue();
        assertThat(widget.menu().getFirst().checked()).isFalse();
        status.display(Optional.of(tab), 4, true, Mode.NORMAL);
        assertThat(status.items().getLast().menu().getFirst().checked()).isTrue();
        tab.dispose();
    }

    @Test void menuRunsItsCommands() {
        var tab = readyTab();
        var ran = new java.util.ArrayList<String>();
        var status = new StatusLine(() -> ran.add("language"), () -> ran.add("wrap"), () -> ran.add("vim"), () -> ran.add("open"),
            () -> ran.add("reload"), () -> ran.add("settings"));
        status.display(Optional.of(tab), 4, true, Mode.NORMAL);
        var menu = status.items().getLast().menu();
        assertThat(menu).extracting(dev.jasper.sdk.ui.ContentStatus.MenuEntry::label)
            .containsExactly("Enabled", "Open vimrc", "Reload vimrc", "", "Settings…");
        assertThat(menu.get(3).separator()).isTrue();
        menu.stream().filter(entry -> !entry.separator()).forEach(entry -> entry.run().run());
        assertThat(ran).containsExactly("vim", "open", "reload", "settings");
        tab.dispose();
    }
}
