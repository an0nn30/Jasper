package dev.jasper.editor.syntax;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE;

class LanguagesTest {
    @Test void everyFactoryStyleIsListedPlainTextFirstWithTomlAdded() {
        var all = Languages.all();
        assertThat(all.getFirst()).isEqualTo(new Languages.Language(SYNTAX_STYLE_NONE, "Plain Text"));
        assertThat(all).extracting(Languages.Language::name).contains("TOML", "JSON", "Python", "Shell (Bash)", "YAML");
        assertThat(Languages.name("text/unheard-of")).isEqualTo("unheard-of");
    }

    @Test void theTomlLexerHighlightsInARealTextArea() {
        Languages.register();
        var area = new RSyntaxTextArea();
        area.setSyntaxEditingStyle(Languages.TOML);
        area.setText("port = 8080\n");
        Token first = area.getTokenListForLine(0);
        assertThat(first.getType()).isEqualTo(Token.VARIABLE);
        assertThat(first.getLexeme()).isEqualTo("port");
    }

    @Test void nestedMultiLineArraysDoNotMisreadAsHeaders() {
        Languages.register();
        var area = new RSyntaxTextArea();
        area.setSyntaxEditingStyle(Languages.TOML);
        area.setText("matrix = [\n  [\n    1, 2\n  ],\n  [3, 4],\n]\n");
        Token line5 = area.getTokenListForLine(4);
        while (line5.getType() == Token.WHITESPACE) line5 = line5.getNextToken();
        assertThat(line5.getType()).as("not a table header").isEqualTo(Token.SEPARATOR);
    }
}
