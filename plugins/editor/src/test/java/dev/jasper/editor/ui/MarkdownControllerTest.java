package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.MarkdownLimits;
import dev.jasper.editor.markdown.MarkdownMode;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.editor.testsupport.MarkdownFixture;
import dev.jasper.sdk.testing.FakeKit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("try")
class MarkdownControllerTest {
    private final MarkdownFixture.ManualExecutor worker = new MarkdownFixture.ManualExecutor();
    private final MarkdownFixture.ManualExecutor sessionWorker = new MarkdownFixture.ManualExecutor();
    private final MarkdownFixture.ManualExecutor ui = new MarkdownFixture.ManualExecutor();
    private EditorTab tab;
    private MarkdownController controller;
    private int changes;

    @BeforeEach void setup() throws Exception {
        edt(() -> {
            var environment = new SessionEnvironment(sessionWorker, ui, Question::dismissed, () -> 8 << 20);
            tab = new EditorTab(new FakeKit("dev.jasper.editor"), DocumentSession.untitled("Untitled", environment), t -> {}, false);
            tab.session().setStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
            tab.surface().previewStyle(PreviewPaneTest.TYPE, PreviewPaneTest.palette(false), EditorThemeTest.syntax(1));
            controller = new MarkdownController(tab, worker, ui, () -> {changes++;tab.render();});
            controller.active(true);
        });
    }
    @AfterEach void cleanup() throws Exception { edt(() -> tab.dispose()); }
    static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
    void complete() throws Exception { worker.runNext(); edt(ui::drain); for(int i=0;i<12;i++) edt(() -> {}); }
    void preview(String text) throws Exception { edt(() -> { tab.area().setText(text); controller.mode(MarkdownMode.PREVIEW); }); }
    String visible() { return tab.surface().preview().editor().getText(); }

    @Test void resourceControlsAppearOnlyAfterMatchingPreviewInstall() throws Exception {
        try (var loader = new dev.jasper.editor.markdown.resources.ImageLoader(new dev.jasper.editor.markdown.resources.ImageBudget(1024))) {
            edt(() -> { controller.close(); controller = new MarkdownController(tab, worker, ui, tab::render, loader); controller.active(true); });
            preview("![alt](https://example.invalid/a.png)");
            edt(() -> assertThat(controller.needsImageConsent()).isFalse());
            complete();
            edt(() -> {assertThat(controller.needsImageConsent()).isTrue(); controller.active(false);});
            edt(() -> assertThat(controller.currentResult()).isEmpty());
        }
    }
    @Test void imageFailuresLeavePreviewTextSourceEditingAndSourceActionUsable() throws Exception {
        try (var loader = new dev.jasper.editor.markdown.resources.ImageLoader(new dev.jasper.editor.markdown.resources.ImageBudget(1024))) {
            edt(() -> { controller.close(); controller = new MarkdownController(tab, worker, ui, tab::render, loader); controller.active(true); });
            preview("Before\n\n![alt](file:/unsupported.png)\n\nAfter"); complete();
            edt(() -> {assertThat(controller.failure()).isEmpty();assertThat(controller.imageFailures()).isTrue();
                assertThat(visible()).contains("Before", "After");dev.jasper.editor.testsupport.Kits.click(tab.view(), "markdown.source");
                assertThat(controller.mode()).isEqualTo(MarkdownMode.SOURCE);tab.area().append(" Edited");assertThat(tab.session().dirty()).isTrue();});
        }
    }
    @Test void initialPreviewRunsImmediatelyAndHundredEditsKeepOnlyLatestPending() throws Exception {
        preview("First"); assertThat(worker.size()).isEqualTo(1);
        edt(() -> { for(int i=0;i<100;i++){tab.area().setText("Latest " + i);controller.flushDebounce();} });
        assertThat(worker.size()).isEqualTo(1);
        complete(); assertThat(worker.size()).isEqualTo(1);
        edt(() -> assertThat(controller.currentResult()).isEmpty());
        complete(); edt(() -> {assertThat(visible()).contains("Latest 99");assertThat(controller.currentResult()).isPresent();});
    }
    @Test void staleTextAndThemeCompletionsCannotPublish() throws Exception {
        preview("Old"); worker.runNext();
        edt(() -> {tab.area().setText("New");controller.styleChanged();ui.drain();});
        edt(() -> assertThat(controller.currentResult()).isEmpty());
        complete(); edt(() -> assertThat(visible()).contains("New").doesNotContain("Old"));
    }
    @Test void hiddenSourceAndCloseInvalidateEveryQueuedCompletion() throws Exception {
        preview("Old");worker.runNext();edt(() -> controller.active(false));edt(ui::drain);
        edt(() -> {assertThat(controller.currentResult()).isEmpty();tab.area().setText("Fresh");controller.active(true);});
        worker.runNext();edt(() -> controller.mode(MarkdownMode.SOURCE));edt(ui::drain);
        edt(() -> {assertThat(controller.currentResult()).isEmpty();controller.mode(MarkdownMode.PREVIEW);});
        worker.runNext();edt(() -> controller.close());int before=changes;edt(ui::drain);
        edt(() -> {controller.sourceChanged();controller.retry();controller.active(true);assertThat(changes).isEqualTo(before);assertThat(controller.currentResult()).isEmpty();});
    }
    @Test void activationRendersDirtyTextAndSwitchesKeepSourceUndoAndSession() throws Exception {
        var session=tab.session();var document=tab.area().getDocument();
        edt(() -> {controller.mode(MarkdownMode.SPLIT);controller.active(false);tab.area().setText("Hidden edit");controller.flushDebounce();});
        complete();assertThat(worker.size()).isZero();
        edt(() -> controller.active(true));complete();
        edt(() -> {assertThat(visible()).contains("Hidden edit");controller.mode(MarkdownMode.SOURCE);controller.mode(MarkdownMode.SPLIT);assertThat(tab.area().getDocument()).isSameAs(document);assertThat(tab.session()).isSameAs(session);assertThat(tab.area().canUndo()).isTrue();});
    }
    @Test void rejectedWorkerShowsFailureAndRetryLeavesSourceAndSaveWorking() throws Exception {
        worker.reject=true;preview("Save me");
        edt(() -> {assertThat(controller.failure()).isPresent();assertThat(tab.session().dirty()).isTrue();controller.mode(MarkdownMode.SOURCE);tab.area().append(" edited");});
        worker.reject=false;edt(() -> {controller.mode(MarkdownMode.PREVIEW);controller.retry();});complete();complete();
        edt(() -> {assertThat(visible()).contains("Save me edited");assertThat(controller.failure()).isEmpty();});
    }
    @Test void sourceAndSaveActionsStayUsableAfterPreviewRejection() throws Exception {
        worker.reject=true;preview("Unsaved Markdown");
        var saved = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Boolean>>();
        edt(() -> {
            assertThat(controller.failure()).isPresent();
            dev.jasper.editor.testsupport.Kits.click(tab.view(), "document.preview.source");
            assertThat(controller.mode()).isEqualTo(MarkdownMode.SOURCE);
            saved.set(tab.session().saveAs(new dev.jasper.editor.testsupport.StubDocument("saved", "saved.md", "saved.md", null)));
        });
        sessionWorker.runNext();
        edt(() -> {
            ui.drain();
            assertThat(saved.get()).isCompletedWithValue(true);
            assertThat(tab.session().dirty()).isFalse();
            assertThat(tab.area().getText()).isEqualTo("Unsaved Markdown");
        });
    }
    @Test void rejectedUiCompletionReleasesRunningSlotAndCanRetry() throws Exception {
        preview("Retry");ui.reject=true;worker.runNext();edt(() -> {});
        edt(() -> assertThat(controller.failure()).isPresent());
        ui.reject=false;edt(controller::retry);complete();edt(() -> assertThat(visible()).contains("Retry"));
    }
    @Test void boundIsCheckedBeforeSourceSnapshotAndRetryWorksAfterShrinking() throws Exception {
        preview("x".repeat(MarkdownLimits.SOURCE_UNITS+1));
        assertThat(worker.size()).isZero();
        edt(() -> {assertThat(controller.failure()).contains("This document is too large to preview");tab.area().setText("Small again");controller.retry();});
        complete();edt(() -> {assertThat(visible()).contains("Small again");assertThat(controller.failure()).isEmpty();});
    }
    @Test void cancelledLexerThatIgnoresInterruptRetainsOwnershipButCannotInstall() throws Exception {
        var probe = new MarkdownFixture.Probe(true);
        try (var ignored = MarkdownFixture.lexer(probe)) {
            preview("```" + MarkdownFixture.LANGUAGE + "\nint value;\n```\n");
            var thread = new Thread(worker::runNext);
            thread.start();
            try {
                assertThat(probe.firstToken.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                edt(() -> {tab.area().setText("Latest after cancel");controller.flushDebounce();});
                assertThat(worker.size()).isZero();
                assertThat(thread.isAlive()).isTrue();
            } finally { probe.release.countDown(); thread.join(10000); }
            assertThat(thread.isAlive()).isFalse();
            edt(ui::drain);
            edt(() -> assertThat(controller.currentResult()).isEmpty());
            assertThat(worker.size()).isEqualTo(1);
        }
        complete();edt(() -> assertThat(visible()).contains("Latest after cancel"));
    }
    @Test void installFailureRetainsOldContentAndRetryCanRecover() throws Exception {
        preview("Old content");complete();
        var broken = new java.util.concurrent.atomic.AtomicBoolean();
        edt(() -> {tab.surface().setFont(new java.awt.Font("Serif",0,12) {
            @Override public java.awt.Font deriveFont(float size) { if (broken.get()) throw new IllegalStateException("font failure"); return super.deriveFont(size); }
        }); broken.set(true);});
        complete();
        edt(() -> {assertThat(controller.failure()).contains("font failure");assertThat(visible()).contains("Old content");
            tab.surface().setFont(new java.awt.Font("Serif",0,12));controller.retry();});
        complete();edt(() -> assertThat(controller.failure()).isEmpty());
    }
    @Test void blockMapLimitShowsFailureAndRetryRecovers() throws Exception {
        preview("x\n\n".repeat(50001));complete();
        edt(() -> {assertThat(controller.failure()).contains("This document is too large to preview");tab.area().setText("Fits");controller.retry();});
        complete();edt(() -> assertThat(visible()).contains("Fits"));
    }
    @Test void hiddenWindowAndDetachedMoveSuspendUntilShownAgain() throws Exception {
        preview("Visible");worker.runNext();
        edt(() -> {tab.surface().addNotify();tab.surface().setVisible(false);ui.drain();});
        edt(() -> assertThat(controller.currentResult()).isEmpty());
        assertThat(worker.size()).isZero();
        edt(() -> {tab.surface().setVisible(true);tab.surface().removeNotify();});
        // A job queued by showing keeps ownership until it returns, but detaching cancels it.
        complete();assertThat(worker.size()).isZero();
        edt(() -> {tab.area().setText("Moved text");controller.flushDebounce();});
        assertThat(worker.size()).isZero();
        edt(() -> tab.surface().addNotify());complete();
        edt(() -> {assertThat(visible()).contains("Moved text");tab.surface().removeNotify();});
    }
    @Test void geometryNotificationsDoNotPublishUnfinishedInstallation() throws Exception {
        preview("paragraph\n\n".repeat(500));worker.runNext();
        edt(() -> {ui.drain();tab.surface().preview().editor().setSize(320,200);assertThat(controller.currentResult()).isEmpty();controller.sourceChanged();});
        for(int i=0;i<12;i++)edt(() -> {});
        edt(() -> assertThat(controller.currentResult()).isEmpty());
    }
}
