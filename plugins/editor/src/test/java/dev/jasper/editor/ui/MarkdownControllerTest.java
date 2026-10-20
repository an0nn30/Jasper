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

    @Test void onlyAttachedCurrentResultEnablesPassageScrollingAndEditsPauseIt() throws Exception {
        String text="# Start\n\n"+"paragraph\n\n".repeat(25)+"# Target\n\n"+"tail\n\n".repeat(25);
        edt(() -> {tab.area().setText(text);controller.mode(MarkdownMode.SPLIT);});complete();
        edt(() -> {dev.jasper.editor.testsupport.PreviewFixture.layout(tab.surface().scroll,250,140);
            dev.jasper.editor.testsupport.PreviewFixture.layout(tab.surface().previewScroll,350,140);tab.surface().scrollLink.geometryChanged();});
        for (int i=0;i<8;i++) edt(() -> {});
        edt(() -> {
            try {
                int source=(int)tab.area().modelToView2D(text.indexOf("# Target")).getY();
                var result=controller.currentResult().orElseThrow();
                var block=result.blocks().stream().filter(b -> b.sourceStart()==text.indexOf("# Target")).findFirst().orElseThrow();
                int target=(int)tab.surface().preview().editor().modelToView2D(tab.surface().preview().anchor(block.id()).orElseThrow()).getY();
                tab.surface().scroll.getViewport().setViewPosition(new java.awt.Point(0,source));
                assertThat(tab.surface().previewScroll.getViewport().getViewPosition().y).isCloseTo(target,within(2));
                dev.jasper.editor.testsupport.Kits.click(tab.view(),"markdown.scroll-sync");
                tab.surface().scroll.getViewport().setViewPosition(new java.awt.Point(0,0));
                assertThat(tab.surface().previewScroll.getViewport().getViewPosition().y).isCloseTo(target,within(2));
                dev.jasper.editor.testsupport.Kits.click(tab.view(),"markdown.scroll-sync");
                tab.area().append("edit");assertThat(controller.currentResult()).isEmpty();
                int before=tab.surface().previewScroll.getViewport().getViewPosition().y;
                tab.surface().scroll.getViewport().setViewPosition(new java.awt.Point(0,source));
                assertThat(tab.surface().previewScroll.getViewport().getViewPosition().y).isEqualTo(before);
            } catch(javax.swing.text.BadLocationException e) {throw new AssertionError(e);}
        });
    }

    @org.junit.jupiter.api.io.TempDir java.nio.file.Path home;
    @Test void ownerFontAndWorkspaceChangesKeepInstalledDocumentReadingAnchorAndImages() throws Exception {
        try(var host=new dev.jasper.sdk.testing.FakePluginHost(home)) {
            dev.jasper.sdk.ui.Appearance[] appearance={null};
            edt(() -> {
                appearance[0]=host.start(new dev.jasper.sdk.PluginInfo("dev.jasper.editor","Editor","0.1.0",java.util.Set.of()),java.util.Set.of(),java.util.Set.of(),c -> {}).appearance();
                tab.appearance(appearance[0]);
                tab.settings(new EditorSettings("Monospaced",16,java.util.List.of(),false,1.3,false,4,false,10,false));
                tab.typography(PreviewPaneTest.TYPE);
                tab.area().setText("![image](relative.png)\n\n# Target\n\n"+"tail paragraph\n\n".repeat(60));
                controller.mode(MarkdownMode.SPLIT);
            });complete();
            Object[] installed={null},document={null};int[] marker={0};
            edt(() -> {
                var result=controller.currentResult().orElseThrow();installed[0]=result;document[0]=tab.surface().preview().editor().getDocument();
                var block=result.blocks().stream().filter(b -> b.sourceStart()==tab.area().getText().indexOf("# Target")).findFirst().orElseThrow();
                marker[0]=tab.surface().preview().anchor(block.id()).orElseThrow();
                dev.jasper.editor.testsupport.PreviewFixture.layout(tab.surface().scroll,250,140);
                dev.jasper.editor.testsupport.PreviewFixture.layout(tab.surface().previewScroll,350,140);
                tab.surface().preview().image(0,new java.awt.image.BufferedImage(80,500,java.awt.image.BufferedImage.TYPE_INT_ARGB));
                dev.jasper.editor.testsupport.Kits.click(tab.view(),"markdown.scroll-sync");
            });for(int i=0;i<8;i++)edt(() -> {});
            edt(() -> {
                tab.surface().previewScroll.getViewport().setViewPosition(new java.awt.Point(0,previewY(marker[0])));
                var settings=new EditorSettings("Serif",22,java.util.List.of(),false,1.8,false,4,false,10,false);
                tab.settings(settings);tab.typography(EditorTypography.effective(settings,appearance[0].terminalTypography()));
                assertThat(worker.size()).isZero();assertThat(controller.currentResult().orElseThrow()).isSameAs(installed[0]);
                assertThat(tab.surface().preview().editor().getDocument()).isSameAs(document[0]);
            });for(int i=0;i<8;i++)edt(() -> {});
            edt(() -> {
                assertThat(tab.surface().previewScroll.getViewport().getViewPosition().y).isCloseTo(previewY(marker[0]),within(2));
                assertThat(previewY(marker[0])).isGreaterThan(400);
                tab.surface().setFont(new java.awt.Font("Dialog",java.awt.Font.PLAIN,18));
                host.setWorkspace(dev.jasper.sdk.Variant.DARK,PreviewPaneTest.palette(true),appearance[0].editorPalette(),appearance[0].syntaxPalette());host.flush();
                assertThat(worker.size()).isZero();assertThat(controller.currentResult().orElseThrow()).isSameAs(installed[0]);
                assertThat(tab.surface().preview().editor().getDocument()).isSameAs(document[0]);
            });for(int i=0;i<8;i++)edt(() -> {});
            edt(() -> {assertThat(tab.surface().previewScroll.getViewport().getViewPosition().y).isCloseTo(previewY(marker[0]),within(2));
                assertThat(previewY(marker[0])).isGreaterThan(400);
                host.setWorkspace(dev.jasper.sdk.Variant.DARK,PreviewPaneTest.palette(true),appearance[0].editorPalette(),EditorThemeTest.syntax(0x882200));host.flush();
                assertThat(worker.size()).isEqualTo(1);assertThat(controller.currentResult()).isEmpty();});
            complete();
            edt(() -> assertThat(controller.currentResult().orElseThrow()).isNotSameAs(installed[0]));
        }
    }
    private int previewY(int offset) {
        try{return (int)tab.surface().preview().editor().modelToView2D(offset).getY();}
        catch(javax.swing.text.BadLocationException e){throw new AssertionError(e);}
    }
    @Test void typographyChangeWhileWorkerRunsUsesLatestGeometryButSyntaxChangeRendersAgain() throws Exception {
        var larger=new dev.jasper.sdk.ui.TerminalTypography("Serif",24,java.util.List.of(),false,1.8);
        preview("```java\nint value = 1;\n```\n");
        edt(() -> {tab.surface().previewStyle(larger,PreviewPaneTest.palette(true),EditorThemeTest.syntax(1));assertThat(worker.size()).isEqualTo(1);});
        complete();
        Object[] initial={null};
        edt(() -> {assertThat(worker.size()).isZero();assertThat(tab.surface().preview().editor().getFont().getSize2D()).isEqualTo(24f);
            initial[0]=controller.currentResult().orElseThrow();
            tab.surface().previewStyle(larger,PreviewPaneTest.palette(true),EditorThemeTest.syntax(0x882200));
            assertThat(worker.size()).isEqualTo(1);assertThat(controller.currentResult()).isEmpty();});
        complete();
        edt(() -> {assertThat(controller.currentResult().orElseThrow()).isNotSameAs(initial[0]);assertThat(visible()).contains("#8822");});
    }

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
    @Test void manyUnconsentedImagesDoNotRefreshDescriptorsPerImage() throws Exception {
        try (var loader = new dev.jasper.editor.markdown.resources.ImageLoader(new dev.jasper.editor.markdown.resources.ImageBudget(1024))) {
            edt(() -> { controller.close(); controller = new MarkdownController(tab, worker, ui, ()->{changes++;tab.render();}, loader); controller.active(true); });
            preview("![alt](https://example.invalid/a.png) ".repeat(2048));int before=changes;complete();
            for(int i=0;i<50;i++)edt(()->{});
            edt(()->{assertThat(controller.currentResult()).isPresent();assertThat(controller.needsImageConsent()).isTrue();assertThat(changes-before).isLessThanOrEqualTo(3);});
            int beforeHide=changes;edt(()->controller.active(false));int after=changes;
            assertThat(after-beforeHide).isLessThanOrEqualTo(1);
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
        }); broken.set(true);controller.retry();});
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
