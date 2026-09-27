package dev.jasper.editor.syntax;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.Segment;
import org.fife.ui.rsyntaxtextarea.Token;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TomlTokenMakerTest {
    private static List<String> tokens(String line, int initial) {
        char[] chars = line.toCharArray();
        List<String> out = new ArrayList<>();
        for (Token token = new TomlTokenMaker().getTokenList(new Segment(chars, 0, chars.length), initial, 0);
             token != null && token.isPaintable(); token = token.getNextToken())
            if (token.getType() != Token.WHITESPACE && token.length() > 0) out.add(name(token.getType()) + ":" + token.getLexeme());
        return out;
    }

    private static List<String> tokens(String line) { return tokens(line, Token.NULL); }

    private static int endState(String line, int initial) {
        char[] chars = line.toCharArray();
        return new TomlTokenMaker().getLastTokenTypeOnLine(new Segment(chars, 0, chars.length), initial);
    }

    private static String name(int type) {
        return switch (type) {
            case Token.COMMENT_EOL -> "COMMENT";
            case Token.RESERVED_WORD -> "HEADER";
            case Token.VARIABLE -> "KEY";
            case Token.LITERAL_STRING_DOUBLE_QUOTE -> "STRING";
            case Token.LITERAL_CHAR -> "LITERAL";
            case Token.LITERAL_NUMBER_DECIMAL_INT -> "INT";
            case Token.LITERAL_NUMBER_HEXADECIMAL -> "RADIX";
            case Token.LITERAL_NUMBER_FLOAT -> "FLOAT";
            case Token.LITERAL_BOOLEAN -> "BOOL";
            case Token.DATA_TYPE -> "DATE";
            case Token.OPERATOR -> "OP";
            case Token.SEPARATOR -> "SEP";
            case Token.ERROR_IDENTIFIER -> "ERROR";
            default -> String.valueOf(type);
        };
    }

    @Test void commentsAndTableHeaders() {
        assertThat(tokens("# note")).containsExactly("COMMENT:# note");
        assertThat(tokens("[server.http]  # main")).containsExactly("HEADER:[server.http]", "COMMENT:# main");
        assertThat(tokens("  [[bin]]")).containsExactly("HEADER:[[bin]]");
    }

    @Test void bareQuotedAndDottedKeys() {
        assertThat(tokens("name = \"jasper\"")).containsExactly("KEY:name", "OP:=", "STRING:\"jasper\"");
        assertThat(tokens("site.\"google.com\".port = 8080"))
            .containsExactly("KEY:site", "OP:.", "KEY:\"google.com\"", "OP:.", "KEY:port", "OP:=", "INT:8080");
        assertThat(tokens("path = 'C:\\Users'")).containsExactly("KEY:path", "OP:=", "LITERAL:'C:\\Users'");
    }

    @Test void numbersBooleansAndDates() {
        assertThat(tokens("hex = 0xDEAD_BEEF")).containsExactly("KEY:hex", "OP:=", "RADIX:0xDEAD_BEEF");
        assertThat(tokens("mode = 0o755")).endsWith("RADIX:0o755");
        assertThat(tokens("mask = 0b1010")).endsWith("RADIX:0b1010");
        assertThat(tokens("big = 1_000")).endsWith("INT:1_000");
        assertThat(tokens("pi = 3.14e-2")).endsWith("FLOAT:3.14e-2");
        assertThat(tokens("low = -inf")).endsWith("FLOAT:-inf");
        assertThat(tokens("odd = nan")).endsWith("FLOAT:nan");
        assertThat(tokens("on = true")).endsWith("BOOL:true");
        assertThat(tokens("when = 1979-05-27T07:32:00Z")).endsWith("DATE:1979-05-27T07:32:00Z");
        assertThat(tokens("local = 1979-05-27 07:32:00")).endsWith("DATE:1979-05-27 07:32:00");
        assertThat(tokens("day = 1979-05-27")).endsWith("DATE:1979-05-27");
        assertThat(tokens("time = 07:32:00.999")).endsWith("DATE:07:32:00.999");
        assertThat(tokens("a = 1 junk")).endsWith("INT:1", "ERROR:junk");
    }

    @Test void arraysAndInlineTables() {
        assertThat(tokens("ports = [ 8080, 8081 ]"))
            .containsExactly("KEY:ports", "OP:=", "SEP:[", "INT:8080", "SEP:,", "INT:8081", "SEP:]");
        assertThat(tokens("point = { x = 1, y = \"two\" }")).containsExactly(
            "KEY:point", "OP:=", "SEP:{", "KEY:x", "OP:=", "INT:1", "SEP:,", "KEY:y", "OP:=", "STRING:\"two\"", "SEP:}");
    }

    @Test void multiLineBasicStringsCarryAcrossLines() {
        assertThat(tokens("text = \"\"\"first")).containsExactly("KEY:text", "OP:=", "STRING:\"\"\"first");
        assertThat(endState("text = \"\"\"first", Token.NULL)).isEqualTo(Token.LITERAL_STRING_DOUBLE_QUOTE);
        assertThat(tokens("still \"inside\"", Token.LITERAL_STRING_DOUBLE_QUOTE)).containsExactly("STRING:still \"inside\"");
        assertThat(endState("", Token.LITERAL_STRING_DOUBLE_QUOTE)).as("an empty line stays inside").isEqualTo(Token.LITERAL_STRING_DOUBLE_QUOTE);
        assertThat(tokens("end\"\"\" # done", Token.LITERAL_STRING_DOUBLE_QUOTE)).containsExactly("STRING:end\"\"\"", "COMMENT:# done");
        assertThat(endState("end\"\"\" # done", Token.LITERAL_STRING_DOUBLE_QUOTE)).isEqualTo(Token.NULL);
        assertThat(tokens("a = \"\"\"x \\\"\"\" y\"\"\"")).as("an escaped quote does not close").endsWith("STRING:\"\"\"x \\\"\"\" y\"\"\"");
        assertThat(tokens("q = \"\"\"a\"\"\"\"")).as("a quote before the delimiter").endsWith("STRING:\"\"\"a\"\"\"\"");
    }

    @Test void multiLineLiteralStringsCarryAcrossLines() {
        assertThat(endState("raw = '''one", Token.NULL)).isEqualTo(Token.LITERAL_CHAR);
        assertThat(tokens("two'''", Token.LITERAL_CHAR)).containsExactly("LITERAL:two'''");
        assertThat(endState("two'''", Token.LITERAL_CHAR)).isEqualTo(Token.NULL);
    }

    @Test void arraysOpenedOnAnEarlierLineKeepValueContext() {
        assertThat(endState("ports = [", Token.NULL)).isEqualTo(Token.SEPARATOR);
        assertThat(tokens("  8080,", Token.SEPARATOR)).containsExactly("INT:8080", "SEP:,");
        assertThat(endState("  8080,", Token.SEPARATOR)).isEqualTo(Token.SEPARATOR);
        assertThat(tokens("  [1, 2],", Token.SEPARATOR)).as("not a table header inside an array")
            .containsExactly("SEP:[", "INT:1", "SEP:,", "INT:2", "SEP:]", "SEP:,");
        assertThat(endState("]", Token.SEPARATOR)).isEqualTo(Token.NULL);
    }

    @Test void theCommentDelimiterMakesToggleCommentWork() {
        assertThat(new TomlTokenMaker().getLineCommentStartAndEnd(0)).containsExactly("#", null);
    }
}
