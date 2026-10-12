package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.editor.syntax.Languages;
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
        var status = new StatusLine(() -> clicked[0]++, () -> clicked[1]++, () -> clicked[2]++);
        status.display(Optional.of(tab), 4, true);
        assertThat(status.positionText()).isEqualTo("Ln 2, Col 3");
        assertThat(status.languageText()).isEqualTo("TOML");
        assertThat(status.encodingText()).isEqualTo("UTF-8");
        assertThat(status.separatorText()).isEqualTo("LF");
        assertThat(status.indentText()).isEqualTo("Spaces: 4");
        assertThat(status.wrapSelected()).isTrue();
        assertThat(status.noticeText()).isEmpty();
        assertThat(status.vimSelected()).isTrue();
        var vim = status.items().stream().filter(item -> item.text().equals("Vim: On")).findFirst().orElseThrow();
        vim.run().run();
        assertThat(clicked[2]).isEqualTo(1);
        status.display(Optional.empty(), 4, false);
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
        var status = new StatusLine(() -> { }, () -> { }, () -> { });
        status.display(Optional.of(tab), 4, false);
        var items = status.items();
        assertThat(items).extracting(dev.jasper.sdk.ui.ContentStatus::text)
            .containsExactly("Ln 2, Col 4", "TOML", "UTF-8", "LF", "Spaces: 4", "Wrap: Off", "Vim: Off");
        assertThat(items).allSatisfy(item -> {
            assertThat(item.text()).doesNotContain("\n").hasSizeLessThanOrEqualTo(16);
            assertThat(item.tooltip()).isEqualTo(item.text());
        });
        assertThat(items).filteredOn(item -> item.run() != null).extracting(dev.jasper.sdk.ui.ContentStatus::text)
            .containsExactly("TOML", "Wrap: Off", "Vim: Off");
        tab.dispose();
    }
}
