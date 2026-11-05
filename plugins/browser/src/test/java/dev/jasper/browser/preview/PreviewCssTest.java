package dev.jasper.browser.preview;

import dev.jasper.browser.api.PreviewTheme;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.SyntaxPalette;
import dev.jasper.sdk.ui.SyntaxRole;
import dev.jasper.sdk.ui.SyntaxStyle;
import dev.jasper.sdk.ui.TerminalTypography;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The theme as the preview's page reads it: CSS custom properties, and Mermaid's theme variables as JSON. */
class PreviewCssTest {
    static final WorkspacePalette PALETTE = new WorkspacePalette(new Color(0x101820), new Color(0x202830), new Color(0x080c10),
        new Color(0xE0E0E0), new Color(0x909090), new Color(0x404850), new Color(0x2050A0), new Color(0xFFFFFF), new Color(0x303840),
        new Color(0x4080FF), new Color(0x40C060), new Color(0xE0A020), new Color(0xE04040));
    static final TerminalTypography TYPOGRAPHY = new TerminalTypography("Fira \"Code\"", 13, List.of("Menlo", "Back\\slash"), true, 1.2);

    @Test void theColoursAreHexOfThePalette() {
        Map<String, String> variables = PreviewCss.variables(theme(Variant.DARK));
        assertThat(variables).containsEntry("--bg", "#101820").containsEntry("--fg", "#e0e0e0").containsEntry("--muted", "#909090")
            .containsEntry("--border", "#404850").containsEntry("--raised", "#202830").containsEntry("--sunken", "#080c10")
            .containsEntry("--accent", "#4080ff").containsEntry("--selection", "#2050a0").containsEntry("--selection-fg", "#ffffff")
            .containsEntry("--success", "#40c060").containsEntry("--warning", "#e0a020").containsEntry("--error", "#e04040");
    }

    @Test void theFontSizeIsPixelsWithNoExponentWhateverTheLocale() {
        Locale before = Locale.getDefault();
        try {
            for (Locale locale : List.of(Locale.GERMANY, Locale.forLanguageTag("ar-EG"), Locale.ROOT)) {
                Locale.setDefault(locale);
                assertThat(PreviewCss.variables(theme(Variant.DARK))).as(locale.toString()).containsEntry("--font-size", "13.0px")
                    .containsEntry("--line-height", "1.2");
                var small = new TerminalTypography("Mono", 6.25, List.of(), false, 0.5);
                assertThat(PreviewCss.variables(new PreviewTheme(Variant.LIGHT, PALETTE, syntax(), "Inter", small)))
                    .containsEntry("--font-size", "6.25px").containsEntry("--line-height", "0.5").containsEntry("--code-ligatures", "none");
            }
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test void fontFamiliesAreCssStringsWithQuotesAndBackslashesEscaped() {
        Map<String, String> variables = PreviewCss.variables(new PreviewTheme(Variant.DARK, PALETTE, syntax(), "Inter \"Display\"", TYPOGRAPHY));
        assertThat(variables.get("--text-font")).isEqualTo("\"Inter \\\"Display\\\"\", sans-serif");
        assertThat(variables.get("--code-font")).as("the family, its fallbacks, then monospace")
            .isEqualTo("\"Fira \\\"Code\\\"\", \"Menlo\", \"Back\\\\slash\", monospace");
        assertThat(variables).containsEntry("--code-ligatures", "normal");
        var newline = new PreviewTheme(Variant.DARK, PALETTE, syntax(), "A\nB", TYPOGRAPHY);
        assertThat(PreviewCss.variables(newline).get("--text-font")).as("a control character as a CSS escape").isEqualTo("\"A\\a B\", sans-serif");
    }

    @Test void everyRoleHasItsThreeVariables() {
        Map<String, String> variables = PreviewCss.variables(theme(Variant.DARK));
        for (SyntaxRole role : SyntaxRole.values()) {
            String name = "--tok-" + role.name().toLowerCase(Locale.ROOT).replace('_', '-');
            assertThat(variables).as(role.name()).containsKeys(name, name + "-weight", name + "-style");
        }
        assertThat(variables).containsEntry("--tok-keyword", "#c080ff").containsEntry("--tok-keyword-weight", "700")
            .containsEntry("--tok-keyword-style", "normal").containsEntry("--tok-comment-weight", "400")
            .containsEntry("--tok-comment-style", "italic").containsEntry("--tok-doc-comment", "#707070");
    }

    @Test void theAlertsTakeTheirColours() {
        Map<String, String> variables = PreviewCss.variables(theme(Variant.LIGHT));
        assertThat(variables).containsEntry("--alert-note", "#4080ff").containsEntry("--alert-tip", "#40c060")
            .as("the workspace palette has no purple").containsEntry("--alert-important", "#c080ff")
            .containsEntry("--alert-warning", "#e0a020").containsEntry("--alert-caution", "#e04040");
    }

    @Test void mermaidsThemeVariablesAreJson() {
        Map<String, Object> dark = StrictJson.object(PreviewCss.mermaid(theme(Variant.DARK)), 1).orElseThrow();
        assertThat(dark).containsEntry("darkMode", true).containsEntry("background", "#101820").containsEntry("primaryColor", "#202830")
            .containsEntry("primaryTextColor", "#e0e0e0").containsEntry("primaryBorderColor", "#404850")
            .containsEntry("lineColor", "#909090").containsEntry("secondaryColor", "#080c10").containsEntry("tertiaryColor", "#101820")
            .containsEntry("noteBkgColor", "#080c10").containsEntry("noteTextColor", "#e0e0e0")
            .containsEntry("fontFamily", "\"Inter\", sans-serif").containsEntry("fontSize", "13.0px");
        assertThat(StrictJson.object(PreviewCss.mermaid(theme(Variant.LIGHT)), 1).orElseThrow()).containsEntry("darkMode", false);
    }

    /** Every class the Editor's WEB dialect writes, and the shell's, has a rule in the stylesheet. */
    @Test void theStylesheetStylesTheWebVocabulary() throws IOException {
        String css;
        try (InputStream in = PreviewCss.class.getResourceAsStream("preview.css")) {
            assertThat(in).as("preview.css").isNotNull();
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var classes = new ArrayList<>(List.of("markdown-body", "markdown-alert", "markdown-alert-title", "markdown-alert-note",
            "markdown-alert-tip", "markdown-alert-important", "markdown-alert-warning", "markdown-alert-caution", "task-list-item",
            "mermaid", "footnotes", "footnote-ref", "footnote-backref", "align-left", "align-center", "align-right", "image-reason",
            "mermaid-diagram", "mermaid-error"));
        for (SyntaxRole role : SyntaxRole.values()) classes.add("tok-" + role.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        for (String name : classes) assertThat(css).as(name).containsPattern("\\." + name + "(?![\\w-])");
        assertThat(css).contains("::selection").contains("::highlight(jasper-find)").contains("var(--text-font)")
            .contains("var(--code-font)").contains("var(--font-size)").doesNotContain("@import").doesNotContain("url(");
    }

    static PreviewTheme theme(Variant variant) {
        return new PreviewTheme(variant, PALETTE, syntax(), "Inter", TYPOGRAPHY);
    }

    static SyntaxPalette syntax() {
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        for (SyntaxRole role : SyntaxRole.values()) styles.put(role, new SyntaxStyle(new Color(0x707070), false, false));
        styles.put(SyntaxRole.KEYWORD, new SyntaxStyle(new Color(0xC080FF), true, false));
        styles.put(SyntaxRole.COMMENT, new SyntaxStyle(new Color(0x808080), false, true));
        return new SyntaxPalette(styles);
    }
}
