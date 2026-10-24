package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionsTest {
    @Test void defaultsAreVims() {
        var options = new Options();
        assertThat(options.number("tabstop")).isEqualTo(8);
        assertThat(options.flag("joinspaces")).isTrue();
        assertThat(options.string("whichwrap")).isEqualTo("b,s");
        assertThat(options.shiftwidth()).isEqualTo(8);
    }

    @Test void appliesSetArguments() {
        var options = new Options().local();
        options.apply("sw=4 et noai ts:2 isk+=- nf-=octal");
        assertThat(options.number("shiftwidth")).isEqualTo(4);
        assertThat(options.flag("expandtab")).isTrue();
        assertThat(options.flag("autoindent")).isFalse();
        assertThat(options.number("tabstop")).isEqualTo(2);
        assertThat(options.string("iskeyword")).endsWith(",-");
        assertThat(options.string("nrformats")).isEqualTo("bin,hex");
        options.apply("invet sw& js!");
        assertThat(options.flag("expandtab")).isFalse();
        assertThat(options.number("shiftwidth")).isEqualTo(8);
        assertThat(options.flag("joinspaces")).isFalse();
        options.apply("sw+=2 sw^=3");
        assertThat(options.number("shiftwidth")).isEqualTo(30);
        assertThat(options.shiftwidth()).isEqualTo(30);
        options.apply("sw=0");
        assertThat(options.shiftwidth()).isEqualTo(2);
    }

    @Test void reportsVimsErrors() {
        var options = new Options();
        assertThatThrownBy(() -> options.apply("nosuch")).hasMessage("E518: Unknown option: nosuch");
        assertThatThrownBy(() -> options.apply("sw=x")).hasMessage("E521: Number required after =: sw=x");
        assertThatThrownBy(() -> options.apply("et=1")).hasMessage("E474: Invalid argument: et=1");
        assertThatThrownBy(() -> options.apply("sw!")).hasMessage("E488: Trailing characters: sw!");
        assertThatThrownBy(() -> options.apply("ts=0")).hasMessageStartingWith("E487");
        assertThatThrownBy(() -> options.apply("isk=999")).hasMessageStartingWith("E474");
    }

    @Test void localValuesStayWithTheirEditorWhileSetAlsoSetsTheGlobalValue() {
        var global = new Options();
        var first = global.local();
        var second = global.local();
        first.set("shiftwidth", 2);
        assertThat(global.number("shiftwidth")).isEqualTo(2);
        assertThat(global.local().number("shiftwidth")).as("a new editor starts from the global value").isEqualTo(2);
        second.setLocal("shiftwidth", 6);
        assertThat(first.number("shiftwidth")).isEqualTo(2);
        assertThat(second.number("shiftwidth")).isEqualTo(6);
        first.set("joinspaces", false);
        assertThat(second.flag("joinspaces")).as("a global option is shared").isFalse();
    }

    @Test void tellsListenersAboutChanges() {
        var options = new Options().local();
        List<String> heard = new ArrayList<>();
        options.onChanged(() -> heard.add("changed"));
        options.set("tabstop", 4);
        options.set("tabstop", 4);
        assertThat(heard).hasSize(1);
        assertThat(options.has("whichwrap", "s")).isTrue();
    }

    @Test void setShowsValuesAndWorksOnEachScope() {
        var global = new Options();
        var local = global.local();
        List<String> shown = new ArrayList<>();
        local.set("sw? et ts", Options.Scope.BOTH, 80, shown);
        assertThat(shown).containsExactly("  shiftwidth=8", "  tabstop=8");
        assertThat(local.flag("expandtab")).isTrue();
        local.set("sw=4", Options.Scope.BOTH, 80, shown);
        local.set("sw=3", Options.Scope.LOCAL, 80, shown);
        shown.clear();
        local.set("sw?", Options.Scope.GLOBAL, 80, shown);
        local.set("sw?", Options.Scope.LOCAL, 80, shown);
        assertThat(shown).containsExactly("  shiftwidth=4", "  shiftwidth=3");
        local.set("sw=6", Options.Scope.GLOBAL, 80, shown);
        assertThat(local.number("shiftwidth")).as(":setglobal leaves the local value").isEqualTo(3);
        assertThat(global.local().number("shiftwidth")).isEqualTo(6);
        shown.clear();
        local.set("", Options.Scope.BOTH, 80, shown);
        assertThat(shown.getFirst()).isEqualTo("--- Options ---");
        assertThat(String.join("\n", shown)).contains("expandtab").contains("shiftwidth=3");
        assertThatThrownBy(() -> local.set("sw=2 foo sw=5", Options.Scope.BOTH, 80, new ArrayList<>())).hasMessage("E518: Unknown option: foo ");
        assertThat(local.number("shiftwidth")).as("the arguments before the error took effect").isEqualTo(2);
        local.set("fo+=ct nf-=octal ww+=h,l", Options.Scope.BOTH, 80, shown);
        assertThat(local.string("formatoptions")).isEqualTo("qct");
        assertThat(local.string("nrformats")).isEqualTo("bin,hex");
        assertThat(local.string("whichwrap")).isEqualTo("b,s,h,l");
        assertThatThrownBy(() -> local.apply("fo=tcqz")).hasMessage("E539: Illegal character <z>: fo=tcqz");
    }

    /**
     * Another window on a buffer (Vim's buffer and window options): the buffer-local values are the buffer's, which a
     * change in either window changes for both and tells both; the window-local values start as the window's it came
     * from and are each window's own after that.
     */
    @Test void windowsOfOneBufferShareItsBufferLocalValues() {
        var global = new Options();
        var first = global.local();
        first.setLocal("relativenumber", true);
        var second = first.window();
        List<String> heard = new ArrayList<>();
        second.onChanged(() -> heard.add("second"));
        first.setLocal("shiftwidth", 2);
        assertThat(second.number("shiftwidth")).isEqualTo(2);
        assertThat(heard).as("a buffer-local change tells every window").containsExactly("second");
        assertThat(second.flag("relativenumber")).as("a window starts with its source's window-local values").isTrue();
        second.setLocal("relativenumber", false);
        assertThat(first.flag("relativenumber")).isTrue();
        second.close();
        first.setLocal("tabstop", 4);
        assertThat(heard).as("a closed window hears nothing").hasSize(2);
        assertThat(global.local().number("shiftwidth")).as("another buffer has its own").isEqualTo(8);
    }
}
