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
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JPanel;
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
    /** Runs every render waiting, those a dropped one hands on among them. */
    private void completeAll() throws Exception { while (worker.size() > 0) complete(); }
    private String visible() { return tab.surface().preview().editor().getText(); }

    /** Puts {@code surface} in a holder made displayable, so it shows, as a window would. */
    private static JPanel mount(DocumentArea surface) {
        var holder = new JPanel(new BorderLayout());
        holder.add(surface);
        holder.setSize(800, 600);
        holder.addNotify();
        holder.doLayout();
        assertThat(surface.isShowing()).isTrue();
        return holder;
    }

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
        String text = "# Title\n\n" + "body text\n\n".repeat(30);
        double[] ratio = {0};
        edt(() -> {
            tab.previewEngine(PreviewEngine.CHROMIUM);
            tab.area().setText(text);
            var surface = tab.surface();
            surface.setSize(800, 400);
            tab.markdownMode(MarkdownMode.SPLIT, false);
            layout(surface);
            ((JSplitPane) surface.getComponent(0)).setDividerLocation(250);
            ratio[0] = surface.dividerRatio();
        });
        complete();
        edt(() -> {
            var page = previews.last();
            page.fire(new PreviewEvent.Shown(1));
            assertThat(tab.markdown().currentResult()).isPresent();
            page.fire(new PreviewEvent.Unavailable("Chromium stopped"));
        });
        edt(() -> {
            var surface = tab.surface();
            assertThat(surface.surface()).as("a turn later").isSameAs(surface.basic());
            assertThat(previews.last().closed()).as("closed, so the Browser forgets its page and origin").isTrue();
            assertThat(tab.markdown().mode()).isEqualTo(MarkdownMode.SPLIT);
            layout(surface);
            var split = (JSplitPane) surface.getComponent(0);
            assertThat(split.getRightComponent()).isSameAs(surface.previewScroll);
            assertThat(surface.dividerRatio()).isCloseTo(ratio[0], within(.001));
            assertThat(split.getDividerLocation()).isEqualTo(250);
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

    /**
     * Fix round 1, E1: a page whose preview client already failed reports Unavailable from inside the add that first
     * shows its canvas. The view falls back once that layout is done, so Basic takes the preview's place and nothing is
     * asked of the closed preview.
     */
    @Test void unavailableAsTheCanvasFirstShowsFallsBackOnceTheSwapIsDone() throws Exception {
        edt(() -> {
            tab.previewEngine(PreviewEngine.BASIC);
            tab.area().setText("# Title\n\nbody");
            tab.markdownMode(MarkdownMode.PREVIEW, false);
            mount(tab.surface());
            previews.unavailableWhenShown("The preview could not start");
            tab.previewEngine(PreviewEngine.CHROMIUM);
            assertThat(previews.made()).hasSize(1);
            tab.focusContent();
        });
        edt(() -> {
            var surface = tab.surface();
            assertThat(surface.surface()).isSameAs(surface.basic());
            assertThat(surface.getComponents()).containsExactly(surface.previewScroll);
            assertThat(((BorderLayout) surface.getLayout()).getLayoutComponent(BorderLayout.CENTER)).isSameAs(surface.previewScroll);
            assertThat(previews.last().closed()).isTrue();
            assertThat(previews.last().calls()).as("nothing after its close").endsWith("close").containsOnlyOnce("close");
            assertThat(tab.markdown().mode()).isEqualTo(MarkdownMode.PREVIEW);
            assertThat(surface.previewFocused()).as("the view's commands go to Basic").isTrue();
        });
        completeAll();
        edt(() -> assertThat(visible()).contains("Title", "body"));
    }

    /** As above, when the first Preview of a view in Source lays out the canvas (ruling N9). */
    @Test void unavailableAsTheCanvasShowsForTheFirstPreviewFallsBackOnceTheLayoutIsDone() throws Exception {
        edt(() -> {
            tab.previewEngine(PreviewEngine.CHROMIUM);
            tab.area().setText("# Title\n\nbody");
            mount(tab.surface());
            previews.unavailableWhenShown("The preview could not start");
            tab.markdownMode(MarkdownMode.PREVIEW, false);
            assertThat(previews.made()).hasSize(1);
        });
        edt(() -> {
            var surface = tab.surface();
            assertThat(surface.surface()).isSameAs(surface.basic());
            assertThat(((BorderLayout) surface.getLayout()).getLayoutComponent(BorderLayout.CENTER)).isSameAs(surface.previewScroll);
            assertThat(surface.getComponents()).containsExactly(surface.previewScroll);
            assertThat(previews.last().closed()).isTrue();
            assertThat(tab.markdown().mode()).isEqualTo(MarkdownMode.PREVIEW);
        });
        completeAll();
        edt(() -> assertThat(visible()).contains("Title", "body"));
    }

    /** Fix round 1, E4: the setting turns Basic while the page has the document but has not said it shows it. */
    @Test void chromiumToBasicWhileAChromiumInstallWaits() throws Exception {
        edt(() -> {
            tab.previewEngine(PreviewEngine.CHROMIUM);
            tab.area().setText("# Waiting");
            tab.markdownMode(MarkdownMode.PREVIEW, false);
        });
        complete();
        edt(() -> {
            var page = previews.last();
            assertThat(page.calls()).as("the Preview took the focus").containsExactly("focus", "theme", "show 1");
            assertThat(tab.surface().surface().installing()).isTrue();
            tab.previewEngine(PreviewEngine.BASIC);
            assertThat(page.closed()).isTrue();
            assertThat(worker.size()).as("Basic renders").isEqualTo(1);
            page.fire(new PreviewEvent.Shown(1));
        });
        complete();
        edt(() -> {
            assertThat(visible()).contains("Waiting");
            assertThat(tab.markdown().currentResult()).containsSame(tab.surface().basic().installedResult().orElseThrow());
            assertThat(tab.markdown().failure()).isEmpty();
        });
    }

    /** Fix round 1, E4: the setting turns Basic while the worker renders for Chromium. */
    @Test void chromiumToBasicWhileAChromiumRenderRuns() throws Exception {
        edt(() -> {
            tab.previewEngine(PreviewEngine.CHROMIUM);
            tab.area().setText("# Rendering");
            tab.markdownMode(MarkdownMode.PREVIEW, false);
        });
        assertThat(worker.size()).as("Chromium's render waits for the worker").isEqualTo(1);
        edt(() -> tab.previewEngine(PreviewEngine.BASIC));
        complete();
        edt(() -> {
            assertThat(previews.last().calls()).as("Chromium's render is dropped").doesNotContain("show 1");
            assertThat(previews.last().closed()).isTrue();
            assertThat(worker.size()).as("Basic's render").isEqualTo(1);
        });
        complete();
        edt(() -> {
            assertThat(visible()).contains("Rendering");
            assertThat(tab.markdown().currentResult()).isPresent();
            assertThat(tab.markdown().failure()).isEmpty();
        });
    }

    @Test void withoutTheBrowserOrWhileChromiumCannotDrawThePreviewIsBasicWhateverTheSetting() throws Exception {
        edt(() -> {
            var alone = view(null);
            alone.chromium(Optional.empty());
            alone.previewEngine(PreviewEngine.CHROMIUM);
            alone.markdownMode(MarkdownMode.SPLIT, false);
            assertThat(alone.surface().surface()).as("no Browser").isSameAs(alone.surface().basic());
            tab.markdownMode(MarkdownMode.SPLIT, false);
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

    /**
     * Ruling N9 (fix round 1, E2): a view makes its Chromium preview the first time Split or Preview shows, so a view that
     * shows only its source, as a restored workspace's Markdown tabs in Source do, makes no page (spec section 19).
     */
    @Test void aViewMakesItsChromiumPreviewTheFirstTimeSplitOrPreviewShows() throws Exception {
        edt(() -> {
            tab.previewEngine(PreviewEngine.CHROMIUM);
            tab.previewEngine(PreviewEngine.BASIC);
            tab.previewEngine(PreviewEngine.CHROMIUM);
            assertThat(previews.made()).as("Source only").isEmpty();
            assertThat(tab.surface().surface()).isSameAs(tab.surface().basic());
            tab.markdownMode(MarkdownMode.SPLIT, false);
            assertThat(previews.made()).hasSize(1);
            assertThat(tab.surface().surface()).isInstanceOf(ChromiumPreview.class);
            assertThat(((JSplitPane) tab.surface().getComponent(0)).getRightComponent()).isSameAs(previews.last().component());
            tab.markdownMode(MarkdownMode.SOURCE, false);
            tab.markdownMode(MarkdownMode.PREVIEW, false);
            assertThat(previews.made()).as("once").hasSize(1);
            var java = view(null);
            java.session().setStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA);
            java.previewEngine(PreviewEngine.CHROMIUM);
            java.markdownMode(MarkdownMode.PREVIEW, false);
            assertThat(previews.made()).as("not Markdown").hasSize(1);
            var other = view(null, tab.session(), tab);
            other.previewEngine(PreviewEngine.CHROMIUM);
            assertThat(previews.made()).as("the file's other view shows its source").hasSize(1);
            other.markdownMode(MarkdownMode.PREVIEW, false);
            assertThat(previews.made()).hasSize(2);
            assertThat(other.surface().surface()).as("each view of the file has its own preview").isNotSameAs(tab.surface().surface());
        });
    }

    /** A view's own markdown default, as the window gives one, lays out the chosen surface first (fix round 1, E3). */
    @Test void aNewViewsFirstPreviewIsTheChosenOne() throws Exception {
        var added = new ArrayList<Component>();
        edt(() -> {
            var sessions = new SessionEnvironment(sessionWorker, ui, Question::dismissed, () -> 8 << 20);
            var made = new EditorTab(new FakeKit("dev.jasper.editor"), DocumentSession.untitled("Untitled", sessions), t -> {}, false);
            tabs.add(made);
            made.surface().addContainerListener(new java.awt.event.ContainerAdapter() {
                @Override public void componentAdded(java.awt.event.ContainerEvent event) { added.add(event.getChild()); }
            });
            made.session().setStyle(SYNTAX_STYLE_MARKDOWN);
            made.previews(worker, ui);
            // As EditorWindow.addView: the Browser's previews and the setting, then the saved default view.
            made.chromium(Optional.of(new ChromiumPreviews(previews)));
            made.previewEngine(PreviewEngine.CHROMIUM);
            made.markdownPreferences(new MarkdownPreferences(() -> MarkdownMode.PREVIEW, mode -> java.util.concurrent.CompletableFuture.completedFuture(null)));
            assertThat(made.markdown().mode()).isEqualTo(MarkdownMode.PREVIEW);
            assertThat(added).as("the Chromium canvas, never Basic's").contains(previews.last().component())
                .doesNotContain(made.surface().previewScroll);
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
