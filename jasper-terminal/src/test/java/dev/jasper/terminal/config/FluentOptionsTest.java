package dev.jasper.terminal.config;

import dev.jasper.terminal.search.SearchQuery;
import dev.jasper.terminal.session.SessionLaunchOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FluentOptionsTest {
    @Test void buildersOwnTheirCollectionsAndDoNotModifyPriorValues() {
        var fonts = new ArrayList<>(List.of("Dialog"));
        var builder = TerminalOptions.builder().fontSize(22f).fallbackFonts(fonts)
            .bell(BellMode.NONE).copyOnSelect(true);
        fonts.clear();
        var first = builder.build();
        builder.fontSize(18f);
        assertThat(first.fontSize()).isEqualTo(22f);
        assertThat(first.fallbackFonts()).containsExactly("Dialog");
        assertThat(first.toBuilder().build()).isEqualTo(first);
        assertThat(TerminalOptions.defaults().fontSize()).isEqualTo(14f);
        assertThatThrownBy(() -> first.toBuilder().fontSize(Float.NaN).build())
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void launchValuesCopyInputsAndRoundTripAllFields() {
        var command = new ArrayList<>(List.of("shell","-l"));
        var environment = new HashMap<>(Map.of("LANG","C.UTF-8"));
        var builder = SessionLaunchOptions.builder().command(command).environment(environment)
            .workingDirectory(Path.of(".")).grid(new GridSize(100,30)).scrollback(200);
        command.clear(); environment.clear();
        var launch = builder.build();
        builder.command(List.of("other")).environment(Map.of()).scrollback(0);
        assertThat(launch.command()).containsExactly("shell","-l");
        assertThat(launch.environment()).containsEntry("LANG","C.UTF-8");
        assertThat(launch.toBuilder().build()).isEqualTo(launch);
        assertThat(launch.grid()).isEqualTo(new GridSize(100,30));
        assertThat(launch.scrollback()).isEqualTo(200);
    }
    @Test void launchRequiresExplicitInputsAndRejectsInvalidArguments() {
        assertThatThrownBy(() -> SessionLaunchOptions.builder().build()).isInstanceOf(NullPointerException.class);
        var valid = SessionLaunchOptions.builder().command(List.of("shell"))
            .environment(Map.of()).workingDirectory(Path.of(".")).build();
        for (var command : List.of(List.<String>of(),List.of(" "),List.of("shell","bad\0arg")))
            assertThatThrownBy(() -> valid.toBuilder().command(command).build()).isInstanceOf(IllegalArgumentException.class);
        for (var environment : List.of(Map.of("", "v"),Map.of("A=B","v"),Map.of("A","v\0")))
            assertThatThrownBy(() -> valid.toBuilder().environment(environment).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid.toBuilder().scrollback(-1).build()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void queryRejectsNullButLeavesRegexValidationToSearch() {
        assertThatThrownBy(() -> new SearchQuery(null,false,false)).isInstanceOf(NullPointerException.class);
        assertThat(new SearchQuery("[",true,true).text()).isEqualTo("[");
    }
}
