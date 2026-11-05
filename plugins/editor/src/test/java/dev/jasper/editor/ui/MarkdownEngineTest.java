package dev.jasper.editor.ui;

import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.editor.markdown.MarkdownMode;
import dev.jasper.editor.markdown.PreviewEngine;
import dev.jasper.editor.markdown.resources.ImageBudget;
import dev.jasper.editor.markdown.resources.ImageLoader;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.editor.testsupport.FakeMarkdownPreviews;
import dev.jasper.editor.testsupport.Kits;
import dev.jasper.editor.testsupport.MarkdownFixture;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.testing.FakeKit;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_MARKDOWN;

/**
 * Which surface a view's Markdown preview draws on: the Markdown preview setting, the Browser's previews and whether
 * Chromium can draw. Each view swaps surfaces in place, keeping its view, divider and Synchronize scrolling.
 */
class MarkdownEngineTest {
    private final MarkdownFixture.ManualExecutor worker = new MarkdownFixture.ManualExecutor();
    private final MarkdownFixture.ManualExecutor sessionWorker = new MarkdownFixture.ManualExecutor();
    private final MarkdownFixture.ManualExecutor ui = new MarkdownFixture.ManualExecutor();
    private final FakeMarkdownPreviews previews = new FakeMarkdownPreviews();
    private final List<EditorTab> tabs = new ArrayList<>();
    private EditorTab tab;

    static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }

    /** A Markdown view whose preview may draw with {@link #previews}; with {@code loader}, it loads the document's images. */
    private EditorTab view(ImageLoader loader) {
        var sessions = new SessionEnvironment(sessionWorker, ui, Question::dismissed, () -> 8 << 20);
        return view(loader, DocumentSession.untitled("Untitled", sessions), null);
    }

    /** As {@link #view(ImageLoader)}, of {@code session}; with {@code sharing}, another view of its file. */
    private EditorTab view(ImageLoader loader, DocumentSession session, EditorTab sharing) {
        var made = new EditorTab(new FakeKit("dev.jasper.editor"), session, t -> {}, false, sharing);
        made.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        made.surface().previewStyle(Variant.LIGHT, PreviewPaneTest.TYPE, PreviewPaneTest.palette(false), EditorThemeTest.syntax(1));
        if (loader == null) made.previews(worker, ui); else made.previews(worker, ui, loader);
        made.markdown().active(true);
        made.chromium(Optional.of(new ChromiumPreviews(previews)));
        tabs.add(made);
        return made;
    }

    @BeforeEach void setup() throws Exception { edt(() -> tab = view(null)); }
    @AfterEach void cleanup() throws Exception { edt(() -> tabs.forEach(EditorTab::dispose)); }

    /** Runs the next render and lets the Basic preview's install finish. */
    private void complete() throws Exception { worker.runNext(); edt(ui::drain); for (int i = 0; i < 12; i++) edt(() -> {}); }
    private String visible() { return tab.surface().preview().editor().getText(); }

    private static void layout(Component component) {
        if (component instanceof Container container) {
            for (int i = 0; i < 2; i++) { container.doLayout(); for (var child : container.getComponents()) layout(child); }
        }
    }

    @Test void switchingEnginesKeepsModeDividerAndSync() throws Exception {
        double[] ratio = {0};
        edt(() -> {
            tab.previewEngine(PreviewEngine.BASIC);
            tab.area().setText("# Title\n\n" + "a paragraph\n\n".repeat(40));
            var surface = tab.surface();
            surface.setSize(800, 400);
            tab.markdownMode(MarkdownMode.SPLIT, false);
            layout(surface);
            ((JSplitPane) surface.getComponent(0)).setDividerLocation(250);
            ratio[0] = surface.dividerRatio();
            Kits.press(tab.view(), "markdown.scroll-sync");
            assertThat(tab.linkedScrolling()).isFalse();
        });
        complete();
        edt(() -> {
            assertThat(visible()).contains("Title");
            tab.previewEngine(PreviewEngine.CHROMIUM);
            var surface = tab.surface();
            assertThat(surface.surface()).isInstanceOf(ChromiumPreview.class);
            assertThat(tab.markdown().mode()).isEqualTo(MarkdownMode.SPLIT);
            layout(surface);
            var split = (JSplitPane) surface.getComponent(0);
            assertThat(split.getLeftComponent()).isSameAs(tab.scroll());
            assertThat(split.getRightComponent()).isSameAs(previews.last().component());
            assertThat(surface.dividerRatio()).isCloseTo(ratio[0], within(.001));
            assertThat(split.getDividerLocation()).isEqualTo(250);
            assertThat(tab.linkedScrolling()).isFalse();
            assertThat(surface.scrollLink.linked()).isFalse();
            assertThat(tab.markdown().currentResult()).as("the Chromium preview renders afresh").isEmpty();
        });
        complete();
        edt(() -> {
            var page = previews.last();
            assertThat(page.calls()).containsExactly("theme", "show 1");
            assertThat(page.document(1)).contains("Title", "data-b");
            page.fire(new PreviewEvent.Shown(1));
            assertThat(tab.markdown().currentResult()).isPresent();
            tab.previewEngine(PreviewEngine.BASIC);
            var surface = tab.surface();
            assertThat(surface.surface()).isSameAs(surface.basic());
            assertThat(page.closed()).as("leaving Chromium closes its preview").isTrue();
            assertThat(tab.markdown().mode()).isEqualTo(MarkdownMode.SPLIT);
            layout(surface);
            var split = (JSplitPane) surface.getComponent(0);
            assertThat(split.getRightComponent()).isSameAs(surface.previewScroll);
            assertThat(surface.dividerRatio()).isCloseTo(ratio[0], within(.001));
            assertThat(split.getDividerLocation()).isEqualTo(250);
            assertThat(tab.linkedScrolling()).isFalse();
            assertThat(surface.scrollLink.linked()).isFalse();
        });
        complete();
        edt(() -> {
            assertThat(visible()).contains("Title");
            assertThat(tab.markdown().currentResult()).isPresent();
        });
    }

    /** Task 3's contract: the swap goes through the controller's invalidation, so the render for the old surface is dropped. */
    @Test void aSwapWhileARenderRunsRendersForTheNewSurface() throws Exception {
        edt(() -> {
            tab.previewEngine(PreviewEngine.BASIC);
            tab.area().setText("# Old enough");
            tab.markdownMode(MarkdownMode.PREVIEW, false);
        });
        assertThat(worker.size()).as("Basic's render waits for the worker").isEqualTo(1);
        edt(() -> tab.previewEngine(PreviewEngine.CHROMIUM));
        complete();
        edt(() -> {
            assertThat(tab.surface().basic().installedResult()).as("the old surface's render is dropped").isEmpty();
            assertThat(previews.last().calls()).isEmpty();
            assertThat(worker.size()).as("the new surface's render").isEqualTo(1);
        });
        complete();
        edt(() -> {
            var page = previews.last();
            assertThat(page.calls()).containsExactly("theme", "show 1");
            assertThat(page.document(1)).contains("Old enough", "data-b");
            page.fire(new PreviewEvent.Shown(1));
            assertThat(tab.markdown().currentResult()).isPresent();
            assertThat(tab.markdown().failure()).isEmpty();
        });
    }

    @Test void switchingClosesFindOnTheOldPreview() throws Exception {
        edt(() -> {
            tab.previewEngine(PreviewEngine.BASIC);
            tab.area().setText("find me");
            tab.markdownMode(MarkdownMode.PREVIEW, false);
            var bar = new FindBar(tab::findTarget, () -> {}, id -> {});
            tab.find(bar);
            bar.open(false);
            assertThat(bar.isVisible()).isTrue();
            tab.previewEngine(PreviewEngine.CHROMIUM);
            assertThat(bar.isVisible()).as("Find closes on the Basic preview").isFalse();
            bar.open(false);
            assertThat(bar.isVisible()).isTrue();
            tab.previewEngine(PreviewEngine.BASIC);
            assertThat(bar.isVisible()).as("and on the Chromium preview").isFalse();
        });
    }

    @Test void unavailableFallsBackToBasicKeepingTheView() throws Exception {
        String text = "# Title\n\nbody text";
        edt(() -> {
            tab.previewEngine(PreviewEngine.CHROMIUM);
            tab.area().setText(text);
            tab.markdownMode(MarkdownMode.SPLIT, false);
        });
        complete();
        edt(() -> {
            var page = previews.last();
            page.fire(new PreviewEvent.Shown(1));
            assertThat(tab.markdown().currentResult()).isPresent();
            page.fire(new PreviewEvent.Unavailable("Chromium stopped"));
            assertThat(tab.surface().surface()).isSameAs(tab.surface().basic());
            assertThat(page.closed()).as("closed, so the Browser forgets its page and origin").isTrue();
            assertThat(tab.markdown().mode()).isEqualTo(MarkdownMode.SPLIT);
            assertThat(tab.area().getText()).isEqualTo(text);
        });
        complete();
        edt(() -> {
            assertThat(visible()).contains("Title", "body text");
            assertThat(tab.markdown().currentResult()).isPresent();
            assertThat(tab.area().getText()).isEqualTo(text);
            tab.previewEngine(PreviewEngine.CHROMIUM);
            assertThat(tab.surface().surface()).as("Basic for the rest of the run").isSameAs(tab.surface().basic());
            assertThat(previews.made()).hasSize(1);
        });
    }

    @Test void withoutTheBrowserOrWhileChromiumCannotDrawThePreviewIsBasicWhateverTheSetting() throws Exception {
        edt(() -> {
            var alone = view(null);
            alone.chromium(Optional.empty());
            alone.previewEngine(PreviewEngine.CHROMIUM);
            assertThat(alone.surface().surface()).as("no Browser").isSameAs(alone.surface().basic());
            previews.available(false);
            tab.previewEngine(PreviewEngine.CHROMIUM);
            assertThat(tab.surface().surface()).as("Chromium cannot draw").isSameAs(tab.surface().basic());
            assertThat(previews.made()).isEmpty();
            previews.available(true);
            tab.previewEngine(PreviewEngine.BASIC);
            assertThat(tab.surface().surface()).as("Basic chosen").isSameAs(tab.surface().basic());
            assertThat(previews.made()).isEmpty();
            tab.previewEngine(PreviewEngine.CHROMIUM);
            assertThat(tab.surface().surface()).isInstanceOf(ChromiumPreview.class);
            previews.available(false);
            tab.previewEngine(PreviewEngine.CHROMIUM);
            assertThat(tab.surface().surface()).as("Chromium stopped since").isSameAs(tab.surface().basic());
            assertThat(previews.last().closed()).isTrue();
        });
    }

    @Test void aViewMakesItsChromiumPreviewTheFirstTimeItShowsMarkdown() throws Exception {
        edt(() -> {
            var text = view(null);
            text.session().setStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA);
            text.previewEngine(PreviewEngine.CHROMIUM);
            assertThat(previews.made()).as("not Markdown").isEmpty();
            assertThat(text.surface().surface()).isSameAs(text.surface().basic());
            text.session().setStyle(SYNTAX_STYLE_MARKDOWN);
            assertThat(previews.made()).hasSize(1);
            assertThat(text.surface().surface()).isInstanceOf(ChromiumPreview.class);
            var other = view(null, text.session(), text);
            other.previewEngine(PreviewEngine.CHROMIUM);
            assertThat(previews.made()).hasSize(2);
            assertThat(other.surface().surface()).as("each view of the file has its own preview").isNotSameAs(text.surface().surface());
        });
    }

    /** Images reach whichever surface the view draws on (pre-flight F16). */
    @Test void imagesReachTheChromiumPreviewAfterTheSwap() throws Exception {
        try (var loader = new ImageLoader(new ImageBudget(1024))) {
            edt(() -> {
                tab = view(loader);
                tab.previewEngine(PreviewEngine.BASIC);
                tab.area().setText("![alt](https://example.invalid/a.png)");
                tab.markdownMode(MarkdownMode.PREVIEW, false);
            });
            complete();
            edt(() -> tab.previewEngine(PreviewEngine.CHROMIUM));
            complete();
            edt(() -> previews.last().fire(new PreviewEvent.Shown(1)));
            for (int i = 0; i < 12; i++) edt(() -> {});
            edt(() -> {
                assertThat(previews.last().calls()).contains("imageReason 0 Load external images to display");
                assertThat(tab.markdown().needsImageConsent()).isTrue();
            });
        }
    }
}
