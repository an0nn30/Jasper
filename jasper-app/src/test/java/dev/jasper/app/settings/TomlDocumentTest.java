package dev.jasper.app.settings;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TomlDocumentTest {
    private static String set(String text, List<String> path, String literal) { return TomlDocument.parse(text).set(path, literal).text(); }
    private static String remove(String text, List<String> path) { return TomlDocument.parse(text).remove(path).text(); }

    @Test void replacingAValueKeepsItsCommentAndEverythingElse() {
        assertThat(set("# head\n[window]\ntab_height = 30 # px\n", List.of("window", "tab_height"), "44"))
            .isEqualTo("# head\n[window]\ntab_height = 44 # px\n");
        assertThat(set("k = [\n  1,\n  2,\n] # list\n", List.of("k"), "[3]")).isEqualTo("k = [3] # list\n");
    }

    @Test void aNewKeyGoesAfterTheLastAssignmentOfItsOwnTable() {
        assertThat(set("[window]\na = 1\n\n[terminal]\nb = 2\n", List.of("window", "c"), "3"))
            .isEqualTo("[window]\na = 1\nc = 3\n\n[terminal]\nb = 2\n");
        assertThat(set("[window]\n# nothing yet\n", List.of("window", "a"), "1")).isEqualTo("[window]\na = 1\n# nothing yet\n");
        assertThat(set("['keybindings']\ncopy = 'x'\n", List.of("keybindings", "app.shortcuts"), "\"ctrl+k\""))
            .isEqualTo("['keybindings']\ncopy = 'x'\n\"app.shortcuts\" = \"ctrl+k\"\n");
    }

    @Test void withoutAHeaderANewKeyJoinsDottedSiblingsOrStartsASection() {
        assertThat(set("terminal.font.family = 'x'\n[window]\na = 1\n", List.of("terminal", "font", "size"), "16"))
            .isEqualTo("terminal.font.family = 'x'\nterminal.font.size = 16\n[window]\na = 1\n");
        assertThat(set("[window]\na = 1\n", List.of("terminal", "font", "size"), "16")).isEqualTo("[window]\na = 1\n\n[terminal.font]\nsize = 16\n");
        assertThat(set("[window]\na = 1\n\n", List.of("t", "b"), "2")).isEqualTo("[window]\na = 1\n\n[t]\nb = 2\n");
        assertThat(set("", List.of("t", "b"), "2")).isEqualTo("[t]\nb = 2\n");
        assertThat(set("[a]\nb = 1", List.of("a", "c"), "2")).isEqualTo("[a]\nb = 1\nc = 2\n");
    }

    @Test void rootKeysGoAfterRootAssignmentsOrLeadingComments() {
        assertThat(set("# Jasper\n# settings\n\n[window]\na = 1\n", List.of("version"), "2"))
            .isEqualTo("# Jasper\n# settings\nversion = 2\n\n[window]\na = 1\n");
        assertThat(set("a = 1\n[t]\nb = 2\n", List.of("c"), "3")).isEqualTo("a = 1\nc = 3\n[t]\nb = 2\n");
        assertThat(set("[t]\nb = 2\n", List.of("version"), "2")).isEqualTo("version = 2\n[t]\nb = 2\n");
    }

    @Test void inlineTablesAreRewrittenInPlace() {
        String text = "keybindings = { copy = 'ctrl+c' } # keep\n";
        assertThat(set(text, List.of("keybindings", "paste"), "\"v\"")).isEqualTo("keybindings = { copy = \"ctrl+c\", paste = \"v\" } # keep\n");
        assertThat(remove(text, List.of("keybindings", "copy"))).isEqualTo("keybindings = {} # keep\n");
    }

    @Test void lineEndingsFollowTheFile() {
        assertThat(set("[a]\r\nb = 1\r\n", List.of("a", "c"), "2")).isEqualTo("[a]\r\nb = 1\r\nc = 2\r\n");
        assertThat(remove("[a]\r\nb = 1 # why\r\nc = 2\r\n", List.of("a", "c"))).isEqualTo("[a]\r\nb = 1 # why\r\n");
    }

    @Test void removingKeepsASameLineCommentAsItsOwnLine() {
        assertThat(remove("[a]\nb = 1 # why\nc = 2\n", List.of("a", "b"))).isEqualTo("[a]\n# why\nc = 2\n");
        assertThat(remove("[a]\nb = 1\nc = 2\n", List.of("a", "c"))).isEqualTo("[a]\nb = 1\n");
        assertThat(remove("[k]\ncopy = \"\"\"\nctrl+c\"\"\" # keep\n", List.of("k", "copy"))).isEqualTo("[k]\n# keep\n");
        var unchanged = TomlDocument.parse("[a]\nb = 1\n");
        assertThat(unchanged.remove(List.of("a", "zzz"))).isSameAs(unchanged);
        assertThat(remove("t = { x = 1, y = 2 }\n", List.of("t"))).isEqualTo("");
    }

    @Test void editsThatCannotLandExactlyAreRefusedWithoutChangingAnything() {
        var arrays = TomlDocument.parse("[[hosts]]\nname = 'a'\n");
        assertThatIllegalArgumentException().isThrownBy(() -> arrays.set(List.of("hosts", "name"), "'b'"))
            .withMessage("Can't safely edit hosts.name here; edit the file by hand.");
        assertThatIllegalArgumentException().isThrownBy(() -> arrays.set(List.of("hosts", "port"), "22"));
        var scalar = TomlDocument.parse("a = 1\n");
        assertThatIllegalArgumentException().isThrownBy(() -> scalar.set(List.of("a", "b"), "2"));
        assertThat(scalar.text()).isEqualTo("a = 1\n");
        assertThatIllegalArgumentException().isThrownBy(() -> scalar.set(List.of("t"), "{ x = 1 }"));
        assertThatIllegalArgumentException().isThrownBy(() -> TomlDocument.parse("[broken"));
        assertThatIllegalArgumentException().isThrownBy(() -> scalar.set(List.of("b"), "not toml"));
    }

    @Test void keysUnderListsAssignmentsBelowAPrefixInFileOrder() {
        var document = TomlDocument.parse("[terminal.env]\nA = '1'\nB = '2'\n[terminal]\nbell = 'none'\n");
        assertThat(document.keysUnder(List.of("terminal", "env"))).containsExactly(List.of("terminal", "env", "A"), List.of("terminal", "env", "B"));
        assertThat(document.keysUnder(List.of("terminal"))).hasSize(3);
        assertThat(TomlDocument.leaves("a = [1, 2]\n[t]\nx = 'y'\n")).containsOnlyKeys(List.of("a"), List.of("t", "x"));
    }
}
