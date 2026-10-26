package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tab names spec 3: free text with {name} pieces, shared by tab names and a look's window title. */
class TitleTemplateTest {
    private static String fill(String template, Map<String, String> values) {
        return TitleTemplate.parse(template).fill(values::get);
    }

    @Test void piecesAndTheTextAroundThemAreSplitInOrder() {
        var template = TitleTemplate.parse("[{dir}] {process}|{title}");
        assertThat(template.names()).containsExactly("dir", "process", "title");
        assertThat(template.literals()).containsExactly("[", "] ", "|", "");
    }

    @Test void doubledBracesWriteABrace() {
        assertThat(TitleTemplate.parse("{{x}} {dir}").literals()).containsExactly("{x} ", "");
        assertThat(fill("{{{dir}}}", Map.of("dir", "moray"))).isEqualTo("{moray}");
        assertThat(fill("no pieces {{here}}", Map.of())).isEqualTo("no pieces {here}");
    }

    @Test void theTextBetweenTwoPiecesShowsOnlyWhenBothHaveAValue() {
        var values = Map.of("dir", "moray", "process", "zsh", "title", "");
        assertThat(fill("{dir} {process} {title}", values)).isEqualTo("moray zsh");
        assertThat(fill("{dir} | {process} | {title}", values)).isEqualTo("moray | zsh");
        assertThat(fill("{title} - {dir}", values)).isEqualTo("moray");
        assertThat(fill("{dir} | {title} - {process}", values)).as("an empty piece goes with the text before it")
            .isEqualTo("moray - zsh");
        assertThat(fill("{dir} {host}", values)).as("a piece nobody fills is empty").isEqualTo("moray");
    }

    @Test void textAroundTheEndsStays() {
        assertThat(fill("{dir} ({host})", Map.of("dir", "moray"))).isEqualTo("moray)");
        assertThat(fill("[{title}]", Map.of())).isEqualTo("[]");
        assertThat(fill("Tab {title}", Map.of())).isEqualTo("Tab");
        assertThat(fill("{title} at work", Map.of())).isEqualTo("at work");
    }

    @Test void valuesAreUsedAsGiven() {
        assertThat(fill("{a}|{b}", Map.of("a", " x ", "b", "y"))).isEqualTo(" x |y");
        assertThat(fill("{a}|{b}", Map.of("a", "  ", "b", "y"))).as("blank is empty").isEqualTo("y");
    }

    @Test void malformedTemplatesSayWhatIsWrong() {
        assertThat(TitleTemplate.problem("{dir} {process}")).isEmpty();
        assertThat(TitleTemplate.problem("{dir")).hasValueSatisfying(message -> assertThat(message).contains("has no }"));
        assertThat(TitleTemplate.problem("dir}")).hasValueSatisfying(message -> assertThat(message).contains("has no {"));
        for (String bad : List.of("{}", "{Dir}", "{1st}", "{a-b}", "{a b}", "{" + "a".repeat(33) + "}"))
            assertThat(TitleTemplate.problem(bad)).as(bad).hasValueSatisfying(message -> assertThat(message).contains("not a piece name"));
        assertThat(TitleTemplate.problem("{" + "a".repeat(32) + "}")).isEmpty();
        assertThatThrownBy(() -> TitleTemplate.parse("{dir")).isInstanceOf(IllegalArgumentException.class);
    }
}
