package dev.jasper.editor.vim.ui;

import dev.jasper.editor.testsupport.ZoneColors;
import dev.jasper.sdk.ui.WorkspacePalette;
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
    @Test void feedbackColorsFollowMountedPaletteOnFirstPaintWithoutAnotherCommand() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var colors = new ZoneColors()) {
                var area = new VimTextArea(); area.setText("abc\n");
                var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
                var bar = new VimBar(); bar.attach(editor);
                var status = (javax.swing.JLabel) ((java.awt.BorderLayout) bar.getLayout()).getLayoutComponent(java.awt.BorderLayout.CENTER);
                var scroll = (javax.swing.JScrollPane) ((java.awt.BorderLayout) bar.getLayout()).getLayoutComponent(java.awt.BorderLayout.NORTH);
                @SuppressWarnings("unchecked") var output = (javax.swing.JList<VimEditor.OutputLine>) scroll.getViewport().getView();
                colors.workspace.add(bar); ZoneColors.paint(bar);
                assertThat(status.getForeground()).isEqualTo(WorkspacePalette.current(bar).text());
                type(editor, "\"qp");
                assertThat(bar.flashing()).isTrue();
                for (var parent : java.util.List.of(colors.workspace, colors.chrome, colors.workspace)) {
                    parent.add(bar); ZoneColors.paint(bar);
                    var p = WorkspacePalette.current(bar);
                    assertThat(status.getForeground()).isEqualTo(p.error());
                    assertThat(bar.getBackground()).isEqualTo(p.accent());
                    assertThat(((java.util.Map<?, ?>) status.getClientProperty("FlatLaf.style")).get("foreground")).isEqualTo(p.error());
                    assertThat(((java.util.Map<?, ?>) bar.getClientProperty("FlatLaf.style")).get("background")).isEqualTo(p.accent());
                    for (boolean error : java.util.List.of(false, true)) {
                        var rendered = output.getCellRenderer().getListCellRendererComponent(output, new VimEditor.OutputLine("output", error), 0, false, false);
                        assertThat(rendered.getForeground()).isEqualTo(error ? p.error() : p.text());
                    }
                    colors.put("Jasper.workspace.error", new java.awt.Color(0x123456));
                    colors.put("Jasper.workspace.accent", new java.awt.Color(0x234567));
                    ZoneColors.paint(bar);
                    assertThat(status.getForeground()).isEqualTo(WorkspacePalette.current(bar).error());
                    assertThat(bar.getBackground()).isEqualTo(WorkspacePalette.current(bar).accent());
                }
                bar.detach(); ZoneColors.paint(bar);
                assertThat(status.getForeground()).isEqualTo(WorkspacePalette.current(bar).text());
                assertThat(bar.getBackground()).isEqualTo(WorkspacePalette.current(bar).background());
            }
        });
    }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    @Test void showsTheModeThePartlyTypedCommandAndMessages() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("abc\n");
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            assertThat(bar.statusText()).isEmpty();
            type(editor, "i");
            assertThat(bar.statusText()).isEqualTo("-- INSERT --");
            type(editor, "<Esc>2d");
            assertThat(bar.statusText()).isEmpty();
            assertThat(bar.commandText()).isEqualTo("2d");
            type(editor, "<Esc>\"qp");
            assertThat(bar.statusText()).isEqualTo("E353: Nothing in register q");
            assertThat(bar.flashing()).isTrue();
            type(editor, "R");
            assertThat(bar.statusText()).isEqualTo("-- REPLACE --");
            bar.detach();
            type(editor, "<Esc>i");
            assertThat(bar.statusText()).as("a detached bar no longer follows the editor").isEmpty();
        });
    }

    @Test void showsRecordingAfterTheMode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("abc\n");
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            type(editor, "qq");
            assertThat(bar.statusText()).isEqualTo("recording @q");
            type(editor, "i");
            assertThat(bar.statusText()).isEqualTo("-- INSERT --recording @q");
            type(editor, "<Esc>q");
            assertThat(bar.statusText()).isEmpty();
        });
    }

    @Test void showsTheCommandLineWithItsCursor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("one two\n");
            area.setCaretPosition(0);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            type(editor, "/tw");
            assertThat(bar.statusText()).isEqualTo("/tw");
            assertThat(bar.commandLineCursor()).isEqualTo(3);
            type(editor, "<Left>");
            assertThat(bar.commandLineCursor()).isEqualTo(2);
            type(editor, "<C-v><C-a>");
            assertThat(bar.statusText()).as("a control character shows as ^A").isEqualTo("/t^Aw");
            type(editor, "<Esc>");
            assertThat(bar.commandLineCursor()).isEqualTo(-1);
            type(editor, "/two<CR>");
            assertThat(bar.statusText()).as("a search echoes its pattern").isEqualTo("/two");
            type(editor, "n");
            assertThat(bar.statusText()).isEqualTo("search hit BOTTOM, continuing at TOP");
        });
    }

    @Test void showsTheVisualAreasSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("ab\u00e9d\nefgh\nijkl\n");
            area.setCaretPosition(0);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            type(editor, "vl");
            assertThat(bar.statusText()).isEqualTo("-- VISUAL --");
            assertThat(bar.commandText()).isEqualTo("2");
            type(editor, "l");
            assertThat(bar.commandText()).as("characters, then UTF-8 bytes when they differ").isEqualTo("3-4");
            type(editor, "j");
            assertThat(bar.commandText()).isEqualTo("2");
            type(editor, "<C-v>");
            assertThat(bar.commandText()).isEqualTo("2x3");
            type(editor, "V");
            assertThat(bar.commandText()).isEqualTo("2");
            type(editor, "\"");
            assertThat(bar.commandText()).as("a pending key replaces the size").isEqualTo("\"");
        });
    }

    @Test void showsTheOutputPanelAndQuestions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("one\ntwo\n");
            area.setCaretPosition(0);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            type(editor, ":%p");
            assertThat(bar.statusText()).isEqualTo(":%p");
            type(editor, "<CR>");
            assertThat(bar.outputLines()).containsExactly("one", "two");
            assertThat(bar.statusText()).isEqualTo("Press ENTER or type command to continue");
            type(editor, "<CR>");
            assertThat(bar.outputLines()).isEmpty();
            type(editor, ":1s/one/1/c<CR>");
            assertThat(bar.statusText()).isEqualTo("replace with 1 (y/n/a/q/l/^E/^Y)?");
            type(editor, "y");
            assertThat(bar.statusText()).isEmpty();
            type(editor, ":foo<CR>");
            assertThat(bar.statusText()).isEqualTo("E492: Not an editor command: foo");
        });
    }
}
