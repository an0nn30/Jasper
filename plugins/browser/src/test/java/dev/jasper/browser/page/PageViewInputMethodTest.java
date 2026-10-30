package dev.jasper.browser.page;

import java.text.CharacterIterator;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A page's canvas is an input method client from its construction, before its page attaches: an input method decides
 * when the canvas gains focus whether it takes composed text, and does not ask again while the focus stays. Its page's
 * input method side is made here without a browser, because a Chromium browser cannot be made without Chromium.
 */
class PageViewInputMethodTest {
    @Test void theCanvasAnswersInputMethodRequestsBeforeItsPageAttachesAndTheSameRequestsAnswerForThePageAfter() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = new PageView();
            var requests = view.getInputMethodRequests();
            assertThat(requests).as("an active client from the start").isNotNull();
            assertThat(requests.getCommittedTextLength()).isZero();
            assertThat(requests.getInsertPositionOffset()).isZero();
            assertThat(requests.getLocationOffset(1, 1)).isNull();
            assertThat(requests.cancelLatestCommittedText(null)).isNull();
            assertThat(requests.getSelectedText(null).first()).isEqualTo(CharacterIterator.DONE);
            assertThat(requests.getCommittedText(0, 0, null).first()).isEqualTo(CharacterIterator.DONE);
            assertThat(requests.getTextLocation(null)).as("the caret at the origin of a view not on screen").isEqualTo(new java.awt.Rectangle());
            var adapter = new ImeAdapter(view, null);
            view.attached(PageView.Hooks.NONE, adapter, null);
            assertThat(view.getInputMethodRequests()).as("the same client once the page attaches").isSameAs(requests);
            adapter.selectionChanged("chosen");
            var selected = requests.getSelectedText(null);
            var text = new StringBuilder();
            for (char c = selected.first(); c != CharacterIterator.DONE; c = selected.next()) text.append(c);
            assertThat(text.toString()).as("the page's selection, through the same client").isEqualTo("chosen");
            assertThat(view.ime()).isSameAs(adapter);
        });
    }
}
