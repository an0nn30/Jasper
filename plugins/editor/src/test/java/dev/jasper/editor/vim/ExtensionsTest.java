package dev.jasper.editor.vim;

import java.util.Optional;
import javax.swing.SwingUtilities;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** What the corpus cannot record about the extensions (spec 8): switching them, their mappings and their prompts. */
class ExtensionsTest {
    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private static VimEditor editor(String text) {
        var area = new VimTextArea();
        area.setText(text);
        area.setCaretPosition(0);
        return new VimEditor(new Vim(new VimHost(r -> Optional.empty(), (r, t) -> { })), area, EditorDefaults.VIM);
    }

    private static boolean mapped(VimEditor editor, int mode, String lhs) {
        return !editor.vim().mappings().starting(mode, KeyInput.parse(lhs).getFirst()).stream()
            .filter(m -> m.lhs.equals(KeyInput.parse(lhs))).toList().isEmpty();
    }

    @Test void aSwitchAddsTheDefaultMappingsAndTakesThemAway() throws Exception {
        onEdt(() -> {
            var editor = editor("x\n");
            assertThat(mapped(editor, Mappings.NORMAL, "ys")).isFalse();
            type(editor, ":set surround<CR>");
            assertThat(mapped(editor, Mappings.NORMAL, "ys")).isTrue();
            assertThat(mapped(editor, Mappings.VISUAL, "S")).isTrue();
            type(editor, ":set nosurround<CR>");
            assertThat(mapped(editor, Mappings.NORMAL, "ys")).isFalse();
            int bells = editor.bells();
            type(editor, "ds");
            assertThat(editor.bells()).as("ds is Vim's d, then s, which is no motion").isEqualTo(bells + 1);
        });
    }

    @Test void plugLinesSwitchOnWhatJasperHasAndIgnoreTheRest() throws Exception {
        onEdt(() -> {
            var editor = editor("x\n");
            type(editor, ":Plug 'tpope/vim-commentary'<CR>");
            assertThat(editor.options().flag("commentary")).isTrue();
            type(editor, ":Plug \"https://github.com/tommcdo/vim-exchange.git\"<CR>");
            assertThat(editor.options().flag("exchange")).isTrue();
            type(editor, ":Plug 'preservim/nerdtree', { 'on': 'NERDTreeToggle' }<CR>");
            assertThat(editor.message()).as("another plugin is ignored").isEmpty();
            type(editor, ":Plug<CR>");
            assertThat(editor.message()).isEqualTo("E471: Argument required");
            assertThat(Extensions.byRepository("vim-scripts/ReplaceWithRegister")).isPresent();
            assertThat(Extensions.byRepository("nobody/nothing")).isEmpty();
        });
    }

    @Test void noMappingsAndHasmaptoKeepTheDefaultsOut() throws Exception {
        onEdt(() -> {
            var editor = editor("x\n");
            type(editor, ":let g:surround_no_mappings = 1<CR>:set surround<CR>");
            assertThat(mapped(editor, Mappings.NORMAL, "ys")).isFalse();
            type(editor, ":nmap gx <lt>Plug>(Exchange)<CR>:set exchange<CR>");
            assertThat(mapped(editor, Mappings.NORMAL, "cx")).as("hasmapto(): the user's gx").isFalse();
            assertThat(mapped(editor, Mappings.NORMAL, "cxc")).isTrue();
            type(editor, ":nmap gc <lt>Plug>Commentary<CR>:set commentary<CR>");
            assertThat(mapped(editor, Mappings.NORMAL, "gcc")).as("commentary maps all or nothing").isFalse();
        });
    }

    @Test void anExtensionThatIsOffCannotRun() throws Exception {
        onEdt(() -> {
            var editor = editor("(a)\n");
            type(editor, ":nmap Q <lt>Plug>Dsurround<CR>");
            int bells = editor.bells();
            type(editor, "lQ(");
            assertThat(editor.bells()).isEqualTo(bells + 1);
            assertThat(editor.buffer().line(0)).isEqualTo("(a)");
            type(editor, ":set surround<CR>Q(");
            assertThat(editor.buffer().line(0)).isEqualTo("a");
        });
    }

    @Test void reloadingSwitchesTheExtensionsOff() throws Exception {
        onEdt(() -> {
            var editor = editor("x\n");
            type(editor, ":set surround commentary<CR>");
            editor.vim().reset();
            assertThat(editor.options().flag("surround")).isFalse();
            assertThat(editor.vim().mappings().isEmpty()).isTrue();
            type(editor, ":set surround<CR>");
            assertThat(mapped(editor, Mappings.NORMAL, "ds")).as("and on again").isTrue();
        });
    }

    @Test void commentstringFollowsTheTabsLanguage() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            area.setText("x\n");
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            assertThat(editor.options().string("commentstring")).isEqualTo("//%s");
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
            assertThat(editor.options().string("commentstring")).isEqualTo("#%s");
            type(editor, ":set commentary<CR>gcc");
            assertThat(editor.buffer().line(0)).isEqualTo("# x");
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            assertThat(editor.options().string("commentstring")).as("no line comment: kept").isEqualTo("#%s");
        });
    }

    @Test void theTextToExchangeIsHighlightedUntilTheSecond() throws Exception {
        onEdt(() -> {
            var editor = editor("one two\n");
            type(editor, ":set exchange<CR>cxiw");
            assertThat(editor.highlights()).anySatisfy(range -> assertThat(range).containsExactly(1, 0, 3));
            type(editor, "cxc");
            assertThat(editor.highlights()).isEmpty();
            type(editor, "cxiwwcxiw");
            assertThat(editor.buffer().line(0)).isEqualTo("two one");
            assertThat(editor.highlights()).isEmpty();
        });
    }

    @Test void surroundAsksForATagOnTheCommandLine() throws Exception {
        onEdt(() -> {
            var editor = editor("one two\n");
            type(editor, ":set surround<CR>ysiwt");
            assertThat(editor.commandLineText()).contains("<");
            type(editor, "em");
            assertThat(editor.commandLineText()).contains("<em");
            type(editor, ">");
            assertThat(editor.commandLineText()).as("> ends the tag").isEmpty();
            assertThat(editor.buffer().line(0)).isEqualTo("<em>one</em> two");
            type(editor, "wdst");
            assertThat(editor.buffer().line(0)).isEqualTo("one two");
            type(editor, "ysiwT");
            assertThat(editor.commandLineText()).as("T offers the last tag removed").contains("<em");
            type(editor, "<Esc>");
            assertThat(editor.buffer().line(0)).as("no tag: nothing around it").isEqualTo("one two");
        });
    }
}
