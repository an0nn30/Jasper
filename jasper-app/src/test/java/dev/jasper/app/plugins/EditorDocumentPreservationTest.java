package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static dev.jasper.app.plugins.StagedEditorHost.call;
import static dev.jasper.app.plugins.WorkspaceContentRouteTest.named;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual staged Editor domain state and vendor styling through the production kit and content-tab host. */
class EditorDocumentPreservationTest {
    @TempDir Path root;

    /**
     * Quitting while {@code git commit} waits on {@code jasper editor --wait}: the Editor ends the call itself as it
     * stops, with 0, before the host ends the calls a plugin leaves running with 1, which would abort the commit.
     */
    @Test @Timeout(value=60,unit=TimeUnit.SECONDS)
    void aWaitingEditorCallEndsWithZeroWhenTheEditorStops() throws Exception {
        Path file = java.nio.file.Files.writeString(root.resolve("COMMIT_EDITMSG"), "message\n");
        var err = new StringBuffer();
        CompletableFuture<Integer> exit;
        try (var editor = new StagedEditorHost(root.resolve("data"), true)) {
            var invocation = new dev.jasper.app.commandline.Invocation(List.of("editor", "--wait", file.toString()), root,
                Optional.empty(), text -> { }, err::append);
            exit = editor.host.commands.dispatch(invocation).toCompletableFuture();
            StagedVaultHost.await(() -> onEdtValue(() -> editor.contributions.tabs().hosted().size() == 1), "the file's tab");
            StagedVaultHost.await(() -> onEdtValue(() -> editor.documentText().getText().equals("message\n")), "the file's text");
            assertThat(exit).as("waiting for the tab to close").isNotDone();
        }
        assertThat(exit.get(5, TimeUnit.SECONDS)).isZero();
        assertThat(err.toString()).isEmpty();
    }

    @Test @Timeout(value=60,unit=TimeUnit.SECONDS)
    void sameDarkChangeAndRelocationKeepTheDocumentFindOptionsUndoPinAndCloseGuard() throws Exception {
        var appearance=onEdtValue(WorkspaceAppearanceFixture::new);
        var chrome=onEdtValue(() -> ThemeTestSupport.generated(false));
        var first=onEdtValue(() -> ThemeTestSupport.generated(true));
        var next=sameDarkness(first);
        onEdt(() -> appearance.setPair(ThemeTestSupport.mixed(chrome,first)));
        try(var editor=new StagedEditorHost(root,true,appearance)) {
            var state=onEdtValue(() -> {
                call(editor.editorWindow(),"newUntitled");
                JTextArea area=editor.documentText();
                area.setText("int foo = 42;\nfoo += 1;\n");
                call(call(editor.selectedTab(),"session"),"setStyle",new Class<?>[]{String.class},"text/java");
                area.append("// edit\n"); area.select(4,7);
                var tab=editor.contentTab(); var entry=editor.contributions.tabs().hosted().getFirst();
                assertThat(entry.commands()).extracting(dev.jasper.app.contributions.ContentCommand::title)
                    .contains("Save", "Save As…", "Save All", "Open…", "New File", "Find…", "Replace…", "Go to Line…",
                        "Toggle Comment", "Revert to Saved", "Language…", "Toggle Soft Wrap", "Toggle Vim Mode")
                    .as("the host's tab menu has its own Close Pane and Close Tab").doesNotContain("Close Tab", "Close Pane");
                var view=(KitView)entry.component();
                assertThat(view.route()).isEqualTo(ThemeRoute.WORKSPACE);
                assertThat(view.isOpaque()).isTrue();
                var command=entry.commands().stream().filter(c -> c.id().equals("replace")).findFirst().orElseThrow();
                command.run().run();
                var query=named(view,"find.query.input",JTextComponent.class);
                var replace=named(view,"find.replacement.input",JTextComponent.class);
                assertThat(query.getSelectedText()).as("opening Find selects its query").isEqualTo("foo");
                replace.setText("bar");
                named(view,"find.case",AbstractButton.class).doClick();
                named(view,"find.word",AbstractButton.class).doClick();
                named(view,"find.regex",AbstractButton.class).doClick();
                assertThat(named(view,"find.status",JLabel.class).getText()).isEqualTo("2 matches");
                area.select(1,3);
                Object document=area.getDocument();
                Color keyword=keyword(editor,area);
                var syntax=editor.context.appearance().syntaxPalette();
                assertThat(keyword).isEqualTo(syntax.style(dev.jasper.sdk.ui.SyntaxRole.KEYWORD).color());
                assertThat(opacity(editor)).isEqualTo(.8);
                var pin=entry.pin();tab.moveToNewWindow();assertThat(tab.window().isMain()).isTrue();pin.close();tab.moveToNewWindow();
                assertThat(tab.window().isMain()).isFalse();assertThat(entry.component()).isSameAs(view);
                assertThat(opacity(editor)).as("destination opacity resampled").isEqualTo(.45);
                assertThat(area.getBackground().getAlpha()).isZero();
                assertThat(editor.context.appearance().variant()).isEqualTo(dev.jasper.sdk.Variant.LIGHT);
                assertThat(editor.context.appearance().workspaceVariant()).isEqualTo(dev.jasper.sdk.Variant.DARK);
                return new Object[]{area,document,tab,view,keyword};
            });
            onEdt(() -> appearance.setPair(ThemeTestSupport.mixed(chrome,next)));
            onEdt(() -> {
                var area=(JTextArea)state[0]; var tab=(dev.jasper.sdk.ui.ContentTab)state[2]; var view=(KitView)state[3];
                assertThat(area.getDocument()).isSameAs(state[1]);
                assertThat(area.getText()).isEqualTo("int foo = 42;\nfoo += 1;\n// edit\n");
                assertThat(area.getSelectionStart()).isEqualTo(1);assertThat(area.getSelectionEnd()).isEqualTo(3);
                assertThat(area.getCaretPosition()).isEqualTo(3);
                assertThat(call(area,"getSyntaxEditingStyle")).isEqualTo("text/java");
                assertThat(call(area,"canUndo")).isEqualTo(true);
                assertThat(call(call(editor.selectedTab(),"session"),"dirty")).isEqualTo(true);
                assertThat(keyword(editor,area)).isEqualTo(editor.context.appearance().syntaxPalette().style(dev.jasper.sdk.ui.SyntaxRole.KEYWORD).color());
                assertThat(keyword(editor,area)).isNotEqualTo(state[4]);
                assertThat(opacity(editor)).isEqualTo(.45);
                assertThat(named(view,"find.query.input",JTextComponent.class).getText()).isEqualTo("foo");
                assertThat(named(view,"find.replacement.input",JTextComponent.class).getText()).isEqualTo("bar");
                for(var id:List.of("find.case","find.word","find.regex"))assertThat(named(view,id,AbstractButton.class).isSelected()).isTrue();
                assertThat(named(view,"find.bar",javax.swing.JComponent.class).isVisible()).isTrue();
                assertThat(named(view,"find.status",JLabel.class).getForeground()).as("a caption").isEqualTo(next.semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_TERTIARY));
                tab.moveToMainWindow();assertThat(opacity(editor)).isEqualTo(.8);
                call(area,"undoLastAction");assertThat(area.getText()).isEqualTo("int foo = 42;\nfoo += 1;\n");
                call(area,"redoLastAction");
                measure(area);
                dev.jasper.sdk.contracts.ThemeMigrationCapture.save("editor-hosted","dark","document-find-after-move",view,1100,520);
                // Enter/Shift+Enter and Escape use Swing's key binding path in the hosted descriptor controls.
                var query=named(view,"find.query.input",JTextComponent.class);area.setCaretPosition(0);
                StagedEditorHost.enter(query);assertThat(area.getSelectionStart()).isEqualTo(4);
                StagedEditorHost.enter(query);assertThat(area.getSelectionStart()).isEqualTo(14);
                StagedEditorHost.press(query,KeyEvent.VK_ENTER,InputEvent.SHIFT_DOWN_MASK,'\n');assertThat(area.getSelectionStart()).isEqualTo(4);
                StagedEditorHost.escape(named(view,"find.next",AbstractButton.class));
                assertThat(named(view,"find.bar",javax.swing.JComponent.class).isVisible()).isFalse();
            });
            var decision=new CompletableFuture<Boolean>();
            javax.swing.SwingUtilities.invokeLater(() -> editor.contentTab().requestClose().whenComplete((value,failure) -> {
                if(failure==null)decision.complete(value);else decision.completeExceptionally(failure);
            }));
            var question=editor.awaitSurface("Unsaved changes");
            StagedRemoteHost.awaitNamed(question,"question.choice.2");
            onEdt(() -> StagedEditorHost.escape(EditorEnterBaselineTest.require(question,"question.choice.2")));
            assertThat(decision.get(10,TimeUnit.SECONDS)).as("dirty close guard survives relocation").isFalse();
            onEdt(() -> {assertThat(editor.contentTab().isClosed()).isFalse();editor.contentTab().close();});
            assertThat(editor.problems).isEmpty();
        }
    }

    @Test void closingTheHostedViewStopsDocumentPaletteAndSessionRefreshes() throws Exception {
        var appearance=onEdtValue(WorkspaceAppearanceFixture::new);
        try(var editor=new StagedEditorHost(root,true,appearance)) {
            onEdt(() -> {
                call(editor.editorWindow(),"newUntitled");
                var tab=editor.selectedTab();var area=editor.documentText();var view=editor.documentView();
                var before=area.getCaretColor();var style=call(area,"getSyntaxEditingStyle");
                view.close();
                appearance.setPair(ThemeTestSupport.resolved(false));
                call(call(tab,"session"),"setStyle",new Class<?>[]{String.class},"text/java");
                assertThat(area.getCaretColor()).isEqualTo(before);
                assertThat(call(area,"getSyntaxEditingStyle")).isEqualTo(style);
                assertThat(call(tab,"vimEditor")).isEqualTo(Optional.empty());
                editor.contentTab().close();
            });
            assertThat(editor.problems).isEmpty();
        }
    }

    @Test void cancellingTheActualEditorsSettingsPageDiscardsInputAndKeepsDocumentConfiguration() throws Exception {
        try(var editor=new StagedEditorHost(root,true)) {
            onEdt(() -> {
                call(editor.editorWindow(),"newUntitled");
                var area=editor.documentText();int size=area.getFont().getSize();
                var pageContext=new dev.jasper.app.settingsui.PageContext(null,id -> { },Runnable::run,(title,path) -> Optional.empty(),
                    () -> { },() -> null,dev.jasper.app.testsupport.KitTestSupport::context);
                var page=(PluginPanelPage)editor.host.settingsPages(pageContext).getFirst();
                var component=page.component();
                named(component,"settings:font_size.input",JTextComponent.class).setText("27");
                assertThat(page.modified()).isTrue();
                page.dispose(); // Settings Cancel closes pages without Apply.
                assertThat(area.getFont().getSize()).isEqualTo(size);
                assertThat(java.nio.file.Files.exists(editor.host.environment.settingsFiles().file("dev.jasper.editor"))).isFalse();
                var reopened=(PluginPanelPage)editor.host.settingsPages(pageContext).getFirst();
                assertThat(Double.parseDouble(named(reopened.component(),"settings:font_size.input",JTextComponent.class).getText())).isEqualTo((double)size);
                assertThat(reopened.modified()).isFalse();reopened.dispose();
                editor.contentTab().close();
            });
            assertThat(editor.problems).isEmpty();
        }
    }

    @Test void theActualDocumentCornerKeepsWorkspaceGlassAndNativeTracksSurviveReparentingUnderLightChrome() throws Exception {
        var controller=onEdtValue(() -> ThemeTestSupport.controller(true));
        onEdt(() -> controller.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH));
        var appearance=onEdtValue(WorkspaceAppearanceFixture::new);
        onEdt(() -> appearance.setPair(ThemeTestSupport.mixed(ThemeTestSupport.generated(false),ThemeTestSupport.generated(true))));
        try(var editor=new StagedEditorHost(root,true,appearance)) {
            onEdt(() -> {
                call(editor.editorWindow(),"newUntitled");var area=editor.documentText();
                area.setText(("Long document row "+"wide ".repeat(120)+"\n").repeat(100));measure(area);
                var scroll=(javax.swing.JScrollPane)call(editor.selectedTab(),"scroll");
                scroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
                scroll.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
                var surface=(javax.swing.JComponent)call(editor.selectedTab(),"surface");
                Integer nativeTrack = null;
                for(var detached:List.of(false,true)) {
                    if (detached) editor.contentTab().moveToNewWindow(); else editor.contentTab().moveToMainWindow();
                    String state=detached ? "document-scroll-moved" : "document-scroll-main";
                    dev.jasper.sdk.contracts.ThemeMigrationCapture.save("editor-hosted","mixed",state,surface,600,300);
                    var image=new java.awt.image.BufferedImage(600,300,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var graphics=image.createGraphics();try {surface.paint(graphics);}finally {graphics.dispose();}
                    var corner=javax.swing.SwingUtilities.convertPoint(scroll,scroll.getWidth()-2,scroll.getHeight()-2,surface);
                    int background=editor.context.appearance().editorPalette().background().getRGB() & 0xffffff;
                    assertThat(image.getRGB(corner.x,corner.y) & 0xffffff).as("scroll corner").isEqualTo(background);
                    assertThat(image.getRGB(corner.x,corner.y) >>> 24).as("one Src alpha in corner").isEqualTo(detached ? 115 : 204);
                    var bar=scroll.getVerticalScrollBar();
                    var track=javax.swing.SwingUtilities.convertPoint(bar,2,bar.getHeight()/2,surface);
                    int trackColor=image.getRGB(track.x,track.y);
                    // Accepted G8 visual-only limitation: native FlatLaf track paints its own color.
                    assertThat(trackColor >>> 24).as("native track remains opaque").isEqualTo(255);
                    assertThat(trackColor & 0xffffff).as("native track differs from document surface").isNotEqualTo(background);
                    if(nativeTrack == null) nativeTrack=trackColor;
                    else assertThat(trackColor).as("native track preserved after move").isEqualTo(nativeTrack);
                }
                editor.contentTab().close();
            });
            assertThat(editor.problems).isEmpty();
        } finally {onEdt(() -> {controller.close();ThemeTestSupport.controller(true);});}
    }

    @Test @Timeout(value=60,unit=TimeUnit.SECONDS)
    void vimFeedbackUsesTheHostedKitAndKeepsCommandAndOutputThroughThemeAndMove() throws Exception {
        var appearance=onEdtValue(WorkspaceAppearanceFixture::new);
        var chrome=onEdtValue(() -> ThemeTestSupport.generated(false));
        var first=onEdtValue(() -> ThemeTestSupport.generated(true));
        var next=sameDarkness(first);
        onEdt(() -> appearance.setPair(ThemeTestSupport.mixed(chrome,first)));
        try(var editor=new StagedEditorHost(root,true,appearance)) {
            Object[] state=onEdtValue(() -> {
                call(editor.editorWindow(),"newUntitled");
                var area=editor.documentText();
                area.setText(java.util.stream.IntStream.range(0,30).mapToObj(i -> "line " + i + "\n").collect(java.util.stream.Collectors.joining()));
                area.setCaretPosition(0); measure(area);
                call(editor.editorWindow(),"toggleVim");
                var vim=((Optional<?>)call(editor.selectedTab(),"vimEditor")).orElseThrow();
                var view=(KitView)editor.contributions.tabs().hosted().getFirst().component();
                feed(editor,vim,"/line<Left>");
                var status=named(view,"vim.status",JLabel.class);
                assertThat(status.getText()).isEqualTo("/line");
                assertThat(status.getFont()).isNotEqualTo(area.getFont());
                var sdk=editor.documentView();
                var descriptor=(dev.jasper.sdk.ui.Nodes.Text)findNode(sdk.spec().root(),"vim.status");
                assertThat(descriptor.caret()).hasValue(4);
                assertThat(descriptor.role()).isEqualTo(dev.jasper.sdk.ui.Nodes.TextRole.MONO);
                Object document=area.getDocument();
                feed(editor,vim,"<Esc>:%p<CR>");
                dev.jasper.sdk.contracts.ThemeMigrationCapture.save("editor-hosted","dark","document-vim-output",view,1100,700);
                var output=named(view,"vim.output",javax.swing.JScrollPane.class);
                var canvas=output.getViewport().getView();
                assertThat(canvas.isFocusable()).isFalse();
                assertThat(output.getViewport().getViewPosition().y).as("last output line is revealed; output=%s preferred=%s extent=%s canvas=%s bar=%s",output.getBounds(),output.getPreferredSize(),output.getViewport().getExtentSize(),canvas.getSize(),named(view,"vim.bar",javax.swing.JComponent.class).getBounds()).isGreaterThan(0);
                var last=named(view,"vim.output.text.29",JLabel.class);
                var rect=javax.swing.SwingUtilities.convertRectangle(last.getParent(),last.getBounds(),canvas);
                assertThat(output.getViewport().getViewRect().intersects(rect)).as("last line is actually visible").isTrue();
                output.getViewport().setViewPosition(new java.awt.Point(0,0));
                return new Object[]{vim,view,document,output};
            });
            onEdt(() -> appearance.setPair(ThemeTestSupport.mixed(chrome,next)));
            onEdt(() -> {
                var view=(KitView)state[1];var output=(javax.swing.JScrollPane)state[3];
                assertThat(editor.documentText().getDocument()).isSameAs(state[2]);
                assertThat(named(view,"vim.status",JLabel.class).getForeground()).isEqualTo(next.semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY));
                assertThat(output.getViewport().getViewPosition().y).isZero();
                editor.contentTab().moveToNewWindow();
                assertThat(editor.documentText().getDocument()).isSameAs(state[2]);
                assertThat(output.getViewport().getViewPosition().y).as("moving preserves output scroll").isZero();
                feed(editor,state[0],"<CR>\"qp");
                assertThat(named(view,"vim.status",JLabel.class).getForeground()).isEqualTo(next.semantic().color(dev.jasper.ui.theme.SemanticColor.ACCENT));
                dev.jasper.sdk.contracts.ThemeMigrationCapture.save("editor-hosted","dark","document-vim-bell",view,1100,520);
            });
            TimeUnit.MILLISECONDS.sleep(250);
            onEdt(() -> {
                var view=(KitView)state[1];
                assertThat(named(view,"vim.status",JLabel.class).getForeground()).isEqualTo(next.semantic().color(dev.jasper.ui.theme.SemanticColor.STATUS_DANGER));
                dev.jasper.sdk.contracts.ThemeMigrationCapture.save("editor-hosted","dark","document-vim-error",view,1100,520);
                feed(editor,state[0],"/line<Left>");
                var command=(dev.jasper.sdk.ui.Nodes.Text)findNode(editor.documentView().spec().root(),"vim.status");
                assertThat(command.caret()).hasValue(4);
                appearance.setPair(ThemeTestSupport.mixed(chrome,first));
                assertThat(findNode(editor.documentView().spec().root(),"vim.status")).isEqualTo(command);
                assertThat(named(view,"vim.status",JLabel.class).getForeground()).isEqualTo(first.semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY));
                dev.jasper.sdk.contracts.ThemeMigrationCapture.save("editor-hosted","dark","document-vim-command-after-theme",view,1100,520);
                feed(editor,state[0],"<Esc>2d");
                assertThat(named(view,"vim.command",JLabel.class).getText()).isEqualTo("2d");
                dev.jasper.sdk.contracts.ThemeMigrationCapture.save("editor-hosted","dark","document-vim-pending-command",view,1100,520);
                var before=editor.documentView().spec();
                call(editor.editorWindow(),"toggleVim");
                assertThat(named(view,"vim.bar",javax.swing.JComponent.class).isVisible()).isFalse();
                feed(editor,state[0],"i");
                assertThat(named(view,"vim.bar",javax.swing.JComponent.class).isVisible()).isFalse();
                assertThat(editor.documentView().spec()).isNotEqualTo(before);
                assertThat(editor.documentText().getDocument()).isSameAs(state[2]);
            });
        }
    }
    private static dev.jasper.sdk.ui.Nodes.Node findNode(dev.jasper.sdk.ui.Nodes.Node node,String id) {
        if(node.id().equals(id))return node;
        if(node instanceof dev.jasper.sdk.ui.Nodes.Split split)for(var child:split.children()){var found=findNode(child,id);if(found!=null)return found;}
        if(node instanceof dev.jasper.sdk.ui.Nodes.Stack stack)for(var child:stack.children()){var found=findNode(child,id);if(found!=null)return found;}
        return null;
    }
    private static void feed(StagedEditorHost editor,Object vim,String keys) {
        try {
            Class<?> key=editor.loader.loadClass("dev.jasper.editor.vim.KeyInput");
            for(Object value:(List<?>)key.getMethod("parse",String.class).invoke(null,keys))call(vim,"feed",new Class<?>[]{key},value);
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }

    private static double opacity(StagedEditorHost editor) { return (double)call(call(editor.selectedTab(),"surface"),"opacity"); }
    private static Color keyword(StagedEditorHost editor,JTextArea area) {
        try {
            int token=editor.loader.loadClass("org.fife.ui.rsyntaxtextarea.TokenTypes").getField("RESERVED_WORD").getInt(null);
            Object scheme=call(area,"getSyntaxScheme");Object style=call(scheme,"getStyle",new Class<?>[]{int.class},token);
            return (Color)style.getClass().getField("foreground").get(style);
        } catch(ReflectiveOperationException failure) {throw new AssertionError(failure);}
    }
    /** Same pinned vendor headless font-metric initialization as EditorTabTest; no native peer. */
    private static void measure(JTextArea area) {
        var graphics=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            var type=area.getClass().getSuperclass();var refresh=type.getDeclaredMethod("refreshFontMetrics",java.awt.Graphics2D.class);
            refresh.setAccessible(true);refresh.invoke(area,graphics);
            var flag=type.getDeclaredField("metricsNeverRefreshed");flag.setAccessible(true);flag.setBoolean(area,false);
        } catch(ReflectiveOperationException failure) {throw new AssertionError(failure);}finally {graphics.dispose();}
    }

    /** Another palette of the same darkness, delivered through the hosting appearance (Decision 5): plum accent, panel and keywords. */
    private static dev.jasper.ui.theme.GeneratedTheme sameDarkness(dev.jasper.ui.theme.GeneratedTheme first) {
        var plum = new java.awt.Color(0x7a3fa0);
        var keyword = first.syntax().get(dev.jasper.ui.theme.SyntaxRole.KEYWORD);
        return ThemeTestSupport.withSyntax(ThemeTestSupport.recolored(first, java.util.Map.of(dev.jasper.ui.theme.SemanticColor.ACCENT, plum,
                dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL, dev.jasper.ui.theme.ColorMath.mix(plum,
                    first.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL), .2))),
            java.util.Map.of(dev.jasper.ui.theme.SyntaxRole.KEYWORD, new dev.jasper.ui.theme.SyntaxStyle(plum.brighter(), keyword.bold(), keyword.italic())));
    }
}
