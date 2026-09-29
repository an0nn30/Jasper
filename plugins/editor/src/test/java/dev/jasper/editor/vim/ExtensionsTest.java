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

    @Test void theTextObjectsAndParagraphMotionSwitchAsIdeaVimNamesThem() throws Exception {
        onEdt(() -> {
            var editor = editor("x\n");
            type(editor, ":set textobj-entire textobj-indent argtextobj vim-paragraph-motion highlightedyank<CR>");
            for (String option : new String[] { "textobj-entire", "textobj-indent", "argtextobj", "vim-paragraph-motion", "highlightedyank" })
                assertThat(editor.options().flag(option)).as(option).isTrue();
            assertThat(mapped(editor, Mappings.OP_PENDING, "ae")).isTrue();
            assertThat(mapped(editor, Mappings.VISUAL, "ii")).isTrue();
            assertThat(mapped(editor, Mappings.OP_PENDING, "aa")).isTrue();
            type(editor, ":set notextobj-entire<CR>");
            assertThat(mapped(editor, Mappings.OP_PENDING, "ae")).isFalse();
            type(editor, ":set sw-=2<CR>");
            assertThat(editor.options().number("shiftwidth")).as("a hyphen before = is still -=").isEqualTo(6);
            var plugged = editor("x\n");
            for (String repository : new String[] { "kana/vim-textobj-entire", "michaeljsmith/vim-indent-object", "vim-scripts/argtextobj.vim",
                "dbakker/vim-paragraph-motion", "machakann/vim-highlightedyank" })
                type(plugged, ":Plug '" + repository + "'<CR>");
            for (String option : new String[] { "textobj-entire", "textobj-indent", "argtextobj", "vim-paragraph-motion", "highlightedyank" })
                assertThat(plugged.options().flag(option)).as("Plug switches on " + option).isTrue();
        });
    }

    @Test void uniqueMappingsLeaveTheUsersKeysAlone() throws Exception {
        onEdt(() -> {
            var editor = editor("a\n\nb\n");
            type(editor, ":nnoremap } j<CR>:omap ae iw<CR>:set vim-paragraph-motion textobj-entire<CR>");
            assertThat(editor.vim().mappings().starting(Mappings.NORMAL, KeyInput.parse("}").getFirst()).getFirst().rhs)
                .as("<unique>: the user's } stays").isEqualTo(KeyInput.parse("j"));
            assertThat(mapped(editor, Mappings.OP_PENDING, "}")).as("in other modes the extension maps }").isTrue();
            assertThat(editor.vim().mappings().starting(Mappings.OP_PENDING, KeyInput.parse("a").getFirst()).stream()
                .filter(m -> m.lhs.equals(KeyInput.parse("ae"))).map(m -> m.rhs).toList()).containsExactly(KeyInput.parse("iw"));
            type(editor, ":set novim-paragraph-motion<CR>");
            assertThat(editor.vim().mappings().starting(Mappings.NORMAL, KeyInput.parse("}").getFirst()))
                .as("switching off keeps the user's own").hasSize(1);
        });
    }

    @Test void matchitSwitchesOnWithPackaddAsWell() throws Exception {
        onEdt(() -> {
            var editor = editor("(a)\n");
            type(editor, ":packadd matchit<CR>");
            assertThat(editor.options().flag("matchit")).isTrue();
            assertThat(mapped(editor, Mappings.NORMAL, "%")).isTrue();
            assertThat(mapped(editor, Mappings.VISUAL, "a%")).isTrue();
            type(editor, ":packadd nothing<CR>");
            assertThat(editor.message()).isEqualTo("E919: Directory not found in 'packpath': \"pack/*/opt/nothing\"");
            var plugged = editor("x\n");
            type(plugged, ":Plug 'chrisbra/matchit'<CR>");
            assertThat(plugged.options().flag("matchit")).isTrue();
        });
    }

    @Test void matchpairsFollowTheLanguageAsItsFiletypePluginAddsThem() throws Exception {
        onEdt(() -> {
            var editor = editor("<a>\n");
            var area = (org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) editor.area();
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_HTML);
            assertThat(editor.options().string("matchpairs")).as("html adds <:>").isEqualTo("(:),{:},[:],<:>");
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
            assertThat(editor.options().string("matchpairs")).isEqualTo("(:),{:},[:]");
            type(editor, ":set mps+=\u00ab:\u00bb<CR>");
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PHP);
            assertThat(editor.options().string("matchpairs")).as("added to the global value").isEqualTo("(:),{:},[:],\u00ab:\u00bb,<:>");
            assertThat(MatchitLanguages.byFiletype("sh")).map(MatchitLanguages.Language::style).contains(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
            assertThat(MatchitLanguages.of(SyntaxConstants.SYNTAX_STYLE_HTML).words()).contains("<\\@<=/\\1>");
        });
    }

    @Test void sneakRepeatsWithDotAndDrawsTheMatchesAhead() throws Exception {
        onEdt(() -> {
            var editor = editor("one two three two four two\n");
            type(editor, ":Plug 'justinmk/vim-sneak'<CR>");
            assertThat(editor.options().flag("sneak")).isTrue();
            type(editor, "dztw");
            assertThat(editor.area().getText()).isEqualTo("two three two four two\n");
            type(editor, "w.");
            assertThat(editor.area().getText()).as(". deletes to the next tw again").isEqualTo("two two four two\n");
            type(editor, "0stw");
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 4));
            assertThat(editor.highlights().stream().filter(r -> r[0] == 3).map(r -> r[1] + "-" + r[2]).toList())
                .as("the matches ahead and the one reached").containsExactly("13-15", "4-6");
            type(editor, "l");
            assertThat(editor.highlights().stream().filter(r -> r[0] == 3).toList()).as("moving on ends it").isEmpty();
        });
    }

    @Test void sneakHighlightEndsWhenTheTextChangesUnderTheCursor() throws Exception {
        onEdt(() -> {
            var editor = editor("one two three two\n");
            type(editor, ":set sneak<CR>stw");
            assertThat(editor.highlights().stream().filter(r -> r[0] == 3).toList()).isNotEmpty();
            type(editor, "x");
            assertThat(editor.highlights().stream().filter(r -> r[0] == 3).toList()).as("x changed the text").isEmpty();
        });
    }

    @Test void scriptedFindsInSurroundDoNotTakeSemicolonFromSneak() throws Exception {
        onEdt(() -> {
            var editor = editor("*b* one two three two\n");
            type(editor, ":set surround<CR>:set sneak<CR>stw");
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 8));
            type(editor, "0ds*");
            assertThat(editor.area().getText()).isEqualTo("b one two three two\n");
            type(editor, ";");
            assertThat(editor.cursor()).as("; still sneaks, it was not given back to F").isEqualTo(new Pos(0, 6));
        });
    }

    @Test void sneakLeavesTheUsersKeysAlone() throws Exception {
        onEdt(() -> {
            var editor = editor("x\n");
            type(editor, ":nnoremap sa x<CR>:set sneak<CR>");
            assertThat(mapped(editor, Mappings.NORMAL, "s")).as("mapcheck(): sa starts with s").isFalse();
            assertThat(mapped(editor, Mappings.NORMAL, "S")).isTrue();
            assertThat(mapped(editor, Mappings.OP_PENDING, "z")).isTrue();
            assertThat(mapped(editor, Mappings.NORMAL, "<Plug>SneakForward")).as("the old names, always").isTrue();
        });
    }
}
