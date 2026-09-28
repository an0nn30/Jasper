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
        assertThatThrownBy(() -> options.apply("sw!")).hasMessage("E474: Invalid argument: sw!");
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
}
