package dev.jasper.editor.vim.ui;

import dev.jasper.editor.vim.EditorDefaults;
import dev.jasper.editor.vim.KeyInput;
import dev.jasper.editor.vim.Vim;
import dev.jasper.editor.vim.VimEditor;
import dev.jasper.editor.vim.VimHost;
import dev.jasper.editor.vim.VimTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VimBarTest {
    @Test void feedbackIsDescribedByKitTextRatherThanRawSwing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea(); area.setText("abc\n");
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            try {
                var bar = new VimBar(() -> { }, id -> { }); bar.attach(editor);
                assertThat(status(bar).role()).isEqualTo(dev.jasper.sdk.ui.Nodes.TextRole.MONO);
                assertThat(status(bar).severity()).isEmpty();
                assertThat(bar.node(false).state().visible()).isFalse();
                type(editor, "\"qp");
                assertThat(status(bar).severity()).contains(dev.jasper.sdk.ui.Nodes.Severity.INFO);
                bar.detach();
                assertThat(status(bar).severity()).isEmpty();
                assertThat(bar.node(true).state().visible()).isFalse();
            } finally { editor.close(); }
        });
    }

    @Test void aBellWithNoMessageStillHasVisibleFeedback() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area=new VimTextArea();area.setText("abc\n");area.setCaretPosition(0);
            var editor=new VimEditor(new Vim(VimHost.detached()),area,EditorDefaults.VIM);
            var bar=new VimBar(() -> {},id -> {});
            try {
                bar.attach(editor);type(editor,"h");
                assertThat(editor.bells()).isPositive();
                assertThat(status(bar).severity()).contains(dev.jasper.sdk.ui.Nodes.Severity.INFO);
                assertThat(status(bar).text()).as("tinting a blank normal-mode status would hide the only bell feedback").isNotBlank();
            }finally {bar.detach();editor.close();}
        });
    }

    @Test void bellExpiryShowsTheCurrentModeRatherThanOverwritingNewerFeedback() throws Exception {
        var bar=new VimBar[1];var editor=new VimEditor[1];
        var expired=new java.util.concurrent.CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            var area=new VimTextArea();area.setText("abc\n");area.setCaretPosition(0);
            editor[0]=new VimEditor(new Vim(VimHost.detached()),area,EditorDefaults.VIM);
            bar[0]=new VimBar(() -> {
                if(bar[0]!=null && status(bar[0]).text().equals("-- INSERT --") && status(bar[0]).severity().isEmpty())expired.countDown();
            },id -> {});
            bar[0].attach(editor[0]);type(editor[0],"h");
            assertThat(status(bar[0]).text()).isEqualTo("Bell");
            type(editor[0],"i");
            assertThat(status(bar[0]).text()).isEqualTo("-- INSERT --");
            assertThat(status(bar[0]).severity()).contains(dev.jasper.sdk.ui.Nodes.Severity.INFO);
        });
        assertThat(expired.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(status(bar[0]).text()).isEqualTo("-- INSERT --");
            bar[0].detach();editor[0].close();
        });
    }

    @Test void bellExpiresBackToErrorAndDetachStopsTheTimerAndListener() throws Exception {
        var bar = new VimBar[1]; var editor = new VimEditor[1]; var calls = new java.util.concurrent.atomic.AtomicInteger();
        var expired = new java.util.concurrent.CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea(); area.setText("abc\n");
            editor[0] = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            bar[0] = new VimBar(() -> {
                calls.incrementAndGet();
                if (bar[0] != null && status(bar[0]).severity().equals(java.util.Optional.of(dev.jasper.sdk.ui.Nodes.Severity.DANGER))) expired.countDown();
            }, id -> { });
            bar[0].attach(editor[0]); type(editor[0], "\"qp");
            assertThat(status(bar[0]).severity()).contains(dev.jasper.sdk.ui.Nodes.Severity.INFO);
        });
        assertThat(expired.await(3, java.util.concurrent.TimeUnit.SECONDS)).as("the 150 ms bell ends and restores the error tint").isTrue();
        var afterDetach = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            assertThat(status(bar[0]).severity()).contains(dev.jasper.sdk.ui.Nodes.Severity.DANGER);
            type(editor[0], "\"qp"); bar[0].detach(); afterDetach[0] = calls.get();
            type(editor[0], "i"); assertThat(status(bar[0]).text().strip()).isEmpty();
        });
        new java.util.concurrent.CountDownLatch(1).await(250, java.util.concurrent.TimeUnit.MILLISECONDS);
        SwingUtilities.invokeAndWait(() -> { assertThat(calls.get()).isEqualTo(afterDetach[0]); editor[0].close(); });
    }

    @Test void closingTheHostedDocumentRevokesTheBarListenerAndRunningTimer() throws Exception {
        dev.jasper.editor.testsupport.Kits.edt(() -> {
            var kit=new dev.jasper.sdk.testing.FakeKit("dev.jasper.editor");
            var environment=new dev.jasper.editor.session.SessionEnvironment(Runnable::run,Runnable::run,dev.jasper.editor.session.Question::dismissed,() -> 1<<20);
            var tab=new dev.jasper.editor.ui.EditorTab(kit,dev.jasper.editor.session.DocumentSession.untitled("Untitled",environment),closed -> {},false);
            tab.area().setText("abc\n");tab.area().setCaretPosition(0);
            tab.vim(java.util.Optional.of(new Vim(VimHost.detached())));var editor=tab.vimEditor().orElseThrow();
            var bar=(VimBar)field(tab,"vimBar"); var timer=(javax.swing.Timer)field(bar,"flash");
            var listener=(Runnable)field(bar,"listener");
            @SuppressWarnings("unchecked") var listeners=(java.util.List<Runnable>)field(editor,"listeners");
            type(editor,"h");assertThat(timer.isRunning()).isTrue();assertThat(listeners).contains(listener);
            tab.view().close();
            assertThat(timer.isRunning()).isFalse();assertThat(listeners).doesNotContain(listener);
            assertThat(tab.area().vim()).isEmpty();tab.dispose();kit.close();
        });
    }
    private static Object field(Object target,String name) throws ReflectiveOperationException {
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }

    @Test void outputRowsAreCompactPassiveCappedAtTwelveAndRevealOnlyNewOutput() throws Exception {
        dev.jasper.editor.testsupport.Kits.edt(() -> {
            var kit = new dev.jasper.sdk.testing.FakeKit("dev.jasper.editor");
            var area = new VimTextArea(); area.setText(java.util.stream.IntStream.range(0, 20).mapToObj(i -> "line " + i + "\n").collect(java.util.stream.Collectors.joining()));
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var view = new dev.jasper.sdk.ui.UiView[1];
            var reveals = new java.util.ArrayList<String>();
            var barNode = new VimBar[1];
            var bar = new VimBar(() -> { if (view[0] != null) view[0].update(new dev.jasper.sdk.ui.ViewSpec(barNode[0].node(true))); }, id -> { view[0].reveal(id); reveals.add(id); });
            barNode[0] = bar;
            view[0] = kit.create(new dev.jasper.sdk.ui.ViewSpec(bar.node(true)), event -> { });
            try {
                bar.attach(editor); type(editor, ":%p<CR>");
                var output = dev.jasper.editor.testsupport.Kits.node(view[0], "vim.output", dev.jasper.sdk.ui.Nodes.ItemList.class);
                assertThat(output.items()).hasSize(20); assertThat(output.visibleRows()).hasValue(12);
                assertThat(output.kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.ListKind.COMPACT);
                assertThat(output.selectionMode()).isEqualTo(dev.jasper.sdk.ui.Nodes.SelectionMode.NONE);
                assertThat(output.selectedIds()).isEmpty(); assertThat(reveals).containsExactly("vim.output.text.19");
                view[0].update(new dev.jasper.sdk.ui.ViewSpec(bar.node(true)));
                assertThat(reveals).as("theme or layout refresh preserves the user's output scroll").hasSize(1);
                type(editor, "<CR>"); assertThat(output(bar)).isEmpty();
                // Explicit backend output fixture exercises the error flag even though ordinary failed Ex commands clear output.
                var field = VimEditor.class.getDeclaredField("output"); field.setAccessible(true);
                field.set(editor, java.util.List.of(new VimEditor.OutputLine("plain", false), new VimEditor.OutputLine("bad", true)));
                bar.attach(editor);
                assertThat(text(bar, "vim.output.text.0").severity()).isEmpty();
                assertThat(text(bar, "vim.output.text.1").severity()).contains(dev.jasper.sdk.ui.Nodes.Severity.DANGER);
                assertThat(dev.jasper.editor.testsupport.Kits.node(view[0], "vim.output", dev.jasper.sdk.ui.Nodes.ItemList.class).visibleRows()).hasValue(2);
            } finally { bar.detach(); editor.close(); view[0].close(); kit.close(); barNode[0] = null; }
        });
    }

    private static dev.jasper.sdk.ui.Nodes.Text text(VimBar bar, String id) {
        return (dev.jasper.sdk.ui.Nodes.Text) dev.jasper.editor.testsupport.Kits.find(bar.node(true), id).orElseThrow();
    }
    private static dev.jasper.sdk.ui.Nodes.Text status(VimBar bar) { return text(bar, "vim.status"); }
    private static java.util.List<String> output(VimBar bar) {
        return ((dev.jasper.sdk.ui.Nodes.ItemList) dev.jasper.editor.testsupport.Kits.find(bar.node(true), "vim.output").orElseThrow())
            .items().stream().map(item -> ((dev.jasper.sdk.ui.Nodes.Text) item.title().getFirst()).text()).toList();
    }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    @Test void showsTheModeThePartlyTypedCommandAndMessages() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("abc\n");
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar(() -> { }, id -> { });
            bar.attach(editor);
            assertThat(status(bar).text().strip()).isEmpty();
            type(editor, "i");
            assertThat(status(bar).text().strip()).isEqualTo("-- INSERT --");
            type(editor, "<Esc>2d");
            assertThat(status(bar).text().strip()).isEmpty();
            assertThat(text(bar, "vim.command").text().strip()).isEqualTo("2d");
            type(editor, "<Esc>\"qp");
            assertThat(status(bar).text().strip()).isEqualTo("E353: Nothing in register q");
            assertThat(status(bar).severity()).contains(dev.jasper.sdk.ui.Nodes.Severity.INFO);
            type(editor, "R");
            assertThat(status(bar).text().strip()).isEqualTo("-- REPLACE --");
            bar.detach();
            type(editor, "<Esc>i");
            assertThat(status(bar).text().strip()).as("a detached bar no longer follows the editor").isEmpty();
        });
    }

    @Test void showsRecordingAfterTheMode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("abc\n");
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar(() -> { }, id -> { });
            bar.attach(editor);
            type(editor, "qq");
            assertThat(status(bar).text().strip()).isEqualTo("recording @q");
            type(editor, "i");
            assertThat(status(bar).text().strip()).isEqualTo("-- INSERT --recording @q");
            type(editor, "<Esc>q");
            assertThat(status(bar).text().strip()).isEmpty();
        });
    }

    @Test void showsTheCommandLineWithItsCursor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("one two\n");
            area.setCaretPosition(0);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar(() -> { }, id -> { });
            bar.attach(editor);
            type(editor, "/tw");
            assertThat(status(bar).text().strip()).isEqualTo("/tw");
            assertThat(status(bar).caret().orElse(-1)).isEqualTo(3);
            type(editor, "<Left>");
            assertThat(status(bar).caret().orElse(-1)).isEqualTo(2);
            type(editor, "<C-v><C-a>");
            assertThat(status(bar).text().strip()).as("a control character shows as ^A").isEqualTo("/t^Aw");
            assertThat(status(bar).caret()).hasValue(4);
            type(editor, "<Esc>");
            assertThat(status(bar).caret().orElse(-1)).isEqualTo(-1);
            type(editor, "/two<CR>");
            assertThat(status(bar).text().strip()).as("a search echoes its pattern").isEqualTo("/two");
            type(editor, "n");
            assertThat(status(bar).text().strip()).isEqualTo("search hit BOTTOM, continuing at TOP");
        });
    }

    @Test void showsTheVisualAreasSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("ab\u00e9d\nefgh\nijkl\n");
            area.setCaretPosition(0);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar(() -> { }, id -> { });
            bar.attach(editor);
            type(editor, "vl");
            assertThat(status(bar).text().strip()).isEqualTo("-- VISUAL --");
            assertThat(text(bar, "vim.command").text().strip()).isEqualTo("2");
            type(editor, "l");
            assertThat(text(bar, "vim.command").text().strip()).as("characters, then UTF-8 bytes when they differ").isEqualTo("3-4");
            type(editor, "j");
            assertThat(text(bar, "vim.command").text().strip()).isEqualTo("2");
            type(editor, "<C-v>");
            assertThat(text(bar, "vim.command").text().strip()).isEqualTo("2x3");
            type(editor, "V");
            assertThat(text(bar, "vim.command").text().strip()).isEqualTo("2");
            type(editor, "\"");
            assertThat(text(bar, "vim.command").text().strip()).as("a pending key replaces the size").isEqualTo("\"");
        });
    }

    @Test void showsTheOutputPanelAndQuestions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("one\ntwo\n");
            area.setCaretPosition(0);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar(() -> { }, id -> { });
            bar.attach(editor);
            type(editor, ":%p");
            assertThat(status(bar).text().strip()).isEqualTo(":%p");
            type(editor, "<CR>");
            assertThat(output(bar)).containsExactly("one", "two");
            assertThat(status(bar).text().strip()).isEqualTo("Press ENTER or type command to continue");
            type(editor, "<CR>");
            assertThat(output(bar)).isEmpty();
            type(editor, ":1s/one/1/c<CR>");
            assertThat(status(bar).text().strip()).isEqualTo("replace with 1 (y/n/a/q/l/^E/^Y)?");
            type(editor, "y");
            assertThat(status(bar).text().strip()).isEmpty();
            type(editor, ":foo<CR>");
            assertThat(status(bar).text().strip()).isEqualTo("E492: Not an editor command: foo");
        });
    }
}
