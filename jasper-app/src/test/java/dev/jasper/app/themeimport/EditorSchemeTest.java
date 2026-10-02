package dev.jasper.app.themeimport;

import dev.jasper.app.appearance.ThemeFixtures;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EditorSchemeTest {
    private static EditorScheme.Result read(String xml, boolean dark) {
        return EditorScheme.read(xml.getBytes(StandardCharsets.UTF_8), "fixture.xml", dark);
    }

    @Test void intellijsOwnSchemesAreTheParents() {
        var darcula = EditorScheme.named("Darcula", "t.theme.json", true);
        assertThat(darcula.warnings()).isEmpty();
        Palette palette = darcula.palette();
        assertThat(palette.background()).isEqualTo(new Color(0x2b2b2b));
        assertThat(palette.foreground()).isEqualTo(new Color(0xbbbbbb));
        assertThat(palette.cursor()).isEqualTo(new Color(0xbbbbbb));
        assertThat(palette.selection()).isEqualTo(new Color(0x214283));
        assertThat(palette.ansi().get(1)).isEqualTo(new Color(0xf0524f));
        assertThat(palette.ansi().get(8)).isEqualTo(new Color(0x595959));
        assertThat(palette.ansi().get(15)).isEqualTo(new Color(0xffffff));
        var light = EditorScheme.named("Default", "t.theme.json", false).palette();
        assertThat(light.background()).isEqualTo(Color.WHITE);
        assertThat(light.cursor()).as("\"0\" is black").isEqualTo(Color.BLACK);
        assertThat(light.ansi().get(1)).isEqualTo(new Color(0xce0505));
    }

    @Test void theSchemesTheBuiltInIntellijThemesNameHaveNoWarnings() {
        // BuiltInThemes passes the darkness of the theme file it converts.
        assertThat(EditorScheme.named("IntelliJ Light", "IntelliJ Light",
            ThemeParents.load("Light.theme.json").dark()).warnings()).isEmpty();
        assertThat(EditorScheme.named("Darcula", "Darcula",
            ThemeParents.load("darcula.theme.json").dark()).warnings()).isEmpty();
    }

    @Test void everyConsoleKeyMapsToItsPaletteField() {
        String[] ansi = {"BLACK", "RED", "GREEN", "YELLOW", "BLUE", "MAGENTA", "CYAN", "GRAY",
            "DARKGRAY", "RED_BRIGHT", "GREEN_BRIGHT", "YELLOW_BRIGHT", "BLUE_BRIGHT", "MAGENTA_BRIGHT", "CYAN_BRIGHT", "WHITE"};
        var attributes = new StringBuilder(
            "<option name=\"CONSOLE_NORMAL_OUTPUT\"><value><option name=\"FOREGROUND\" value=\"131415\"/></value></option>");
        for (int i = 0; i < ansi.length; i++)
            attributes.append("<option name=\"CONSOLE_").append(ansi[i]).append("_OUTPUT\"><value><option name=\"FOREGROUND\" value=\"")
                .append(String.format("%06x", 0x200000 + i)).append("\"/></value></option>");
        var result = read("<scheme name=\"All\" parent_scheme=\"Darcula\"><colors>"
            + "<option name=\"CONSOLE_BACKGROUND_KEY\" value=\"0a0b0c\"/><option name=\"CARET_COLOR\" value=\"0d0e0f\"/>"
            + "<option name=\"SELECTION_BACKGROUND\" value=\"101112\"/></colors><attributes>" + attributes
            + "</attributes></scheme>", true);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.palette().background()).isEqualTo(new Color(0x0a0b0c));
        assertThat(result.palette().cursor()).isEqualTo(new Color(0x0d0e0f));
        assertThat(result.palette().selection()).isEqualTo(new Color(0x101112));
        assertThat(result.palette().foreground()).isEqualTo(new Color(0x131415));
        for (int i = 0; i < 16; i++) assertThat(result.palette().ansi().get(i)).as(ansi[i]).isEqualTo(new Color(0x200000 + i));
    }

    @Test void keysASchemeLeavesOutComeFromItsParent() {
        var result = read(ThemeFixtures.scheme("Partial", "Darcula", "101010", "abcdef"), true);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.palette().background()).isEqualTo(new Color(0x101010));
        assertThat(result.palette().ansi().get(1)).isEqualTo(new Color(0xabcdef));
        assertThat(result.palette().ansi().get(2)).as("Darcula's green").isEqualTo(new Color(0x5c962c));
        assertThat(result.palette().foreground()).as("Darcula's console text").isEqualTo(new Color(0xbbbbbb));
        var orphan = read("<scheme name=\"Orphan\"><colors><option name=\"CONSOLE_BACKGROUND_KEY\" value=\"202020\"/></colors></scheme>", true);
        assertThat(orphan.warnings()).isEmpty();
        assertThat(orphan.palette().ansi().get(1)).as("no parent_scheme means Default").isEqualTo(new Color(0xce0505));
    }

    @Test void baseAttributesPointAtAnotherKey() {
        var result = read("""
            <scheme name="Based" parent_scheme="Darcula"><attributes>
              <option name="MY_RED"><value><option name="FOREGROUND" value="123456"/></value></option>
              <option name="CONSOLE_RED_OUTPUT" baseAttributes="MY_RED"/>
            </attributes></scheme>""", true);
        assertThat(result.palette().ansi().get(1)).isEqualTo(new Color(0x123456));
    }

    @Test void withoutAKnownParentTheEditorTextAndThenJaspersPaletteFillIn() {
        var result = read("""
            <scheme name="Lonely" parent_scheme="Nobody"><attributes>
              <option name="TEXT"><value><option name="FOREGROUND" value="111111"/><option name="BACKGROUND" value="222222"/></value></option>
            </attributes></scheme>""", true);
        assertThat(result.palette().foreground()).isEqualTo(new Color(0x111111));
        assertThat(result.palette().background()).isEqualTo(new Color(0x222222));
        assertThat(result.palette().ansi()).isEqualTo(Palette.jasperDark().ansi());
        assertThat(result.palette().cursor()).isEqualTo(Palette.jasperDark().cursor());
        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning).contains("fixture.xml", "Nobody"));
        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning).contains("missing console", "Jasper Dark"));
    }

    @Test void coloursAreHexNumbersThatMayDropLeadingZeros() {
        var result = read("""
            <scheme name="Hex" parent_scheme="Default"><colors>
              <option name="CARET_COLOR" value="0"/><option name="SELECTION_BACKGROUND" value="ff"/>
              <option name="CONSOLE_BACKGROUND_KEY" value="#00ff00"/>
            </colors></scheme>""", false);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.palette().cursor()).isEqualTo(Color.BLACK);
        assertThat(result.palette().selection()).isEqualTo(new Color(0x0000ff));
        assertThat(result.palette().background()).isEqualTo(new Color(0x00ff00));
    }

    @Test void badColoursAndDocumentsFallBackWithWarnings() {
        var bad = read("<scheme name=\"Bad\" parent_scheme=\"Default\"><colors><option name=\"CARET_COLOR\" value=\"zz\"/></colors></scheme>", false);
        assertThat(bad.palette().cursor()).as("the parent Default's valid caret").isEqualTo(Color.BLACK);
        assertThat(bad.warnings()).singleElement().asString().contains("CARET_COLOR").contains("zz");
        var broken = read("<scheme", true);
        assertThat(broken.palette()).isEqualTo(Palette.jasperDark());
        assertThat(broken.warnings()).singleElement().asString().contains("fixture.xml").contains("not a readable XML document");
        var xxe = read("<!DOCTYPE scheme [<!ENTITY x SYSTEM \"file:///etc/hosts\">]><scheme name=\"&x;\"/>", true);
        assertThat(xxe.palette()).isEqualTo(Palette.jasperDark());
        assertThat(xxe.warnings()).singleElement().asString().contains("DOCTYPE");
        var two = read("<component><scheme name=\"A\"/><scheme name=\"B\"/></component>", false);
        assertThat(two.palette()).isEqualTo(Palette.jasperLight());
        assertThat(two.warnings()).singleElement().asString().contains("found 2");
    }

    @Test void aSchemeNameJasperDoesNotHaveFallsBack() {
        var result = EditorScheme.named("Nobody's Scheme", "t.theme.json", false);
        assertThat(result.palette()).isEqualTo(Palette.jasperLight());
        assertThat(result.warnings()).singleElement().asString().contains("Nobody's Scheme");
    }

    @Test void theNewUiAndIslandsSchemesAreNamedSchemesAndParents() {
        var islands = EditorScheme.named("Islands Dark", "t.theme.json", true);
        assertThat(islands.warnings()).isEmpty();
        assertThat(islands.palette().background()).isEqualTo(new Color(0x191a1c));
        assertThat(islands.palette().foreground()).isEqualTo(new Color(0xbcbec4));
        var child = read("<scheme name=\"Child\" parent_scheme=\"Dark\"><colors/></scheme>", true);
        assertThat(child.warnings()).isEmpty();
        assertThat(child.palette().background()).as("the New UI Dark scheme's console").isEqualTo(new Color(0x1e1f22));
        var light = EditorScheme.named("Light", "t.theme.json", false);
        assertThat(light.warnings()).isEmpty();
        assertThat(light.palette().selection()).as("Light's own, over Default's 5974ab").isEqualTo(new Color(0xa6d2ff));
        assertThat(light.palette().foreground()).as("console output inherited from Default").isEqualTo(Color.BLACK);
    }
}
