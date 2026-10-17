package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.FontFile;
import dev.jasper.app.presentation.KitParser;
import dev.jasper.ui.metrics.FaceSet;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.metrics.Typography;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 5.6 and 5.9: a kit's font files register under their declared family, and a role draws in its face. */
class LookFontsTest {
    static byte[] bundled(String style) throws IOException {
        String path = "/dev/jasper/app/fonts/cousine/CousineNerdFontMono-" + style + ".ttf";
        try (var in = LookFontsTest.class.getResourceAsStream(path)) { return in.readAllBytes(); }
    }

    /** The bundled Cousine face as it ships; a machine that has Cousine installed may hide it. */
    static FontFile cousine(String style) throws IOException { return FontFile.read("fonts/cousine-" + style + ".ttf", bundled(style)); }

    /** Cousine with its family renamed in the name table (same length), so it registers on every machine. */
    static FontFile lookFace(String style) throws IOException {
        byte[] bytes = bundled(style);
        for (var charset : List.of(StandardCharsets.UTF_16BE, StandardCharsets.ISO_8859_1)) {
            byte[] from = "Cousine Nerd Font Mono".getBytes(charset), to = "Jasper Look Face Tests".getBytes(charset);
            for (int i = 0; i + from.length <= bytes.length; i++)
                if (Arrays.equals(bytes, i, i + from.length, from, 0, from.length)) System.arraycopy(to, 0, bytes, i, to.length);
        }
        return FontFile.read("fonts/look-" + style + ".ttf", bytes);
    }

    @Test void filesOfTheDeclaredFamilyRegisterAndTheirRolesDrawInThem() throws Exception {
        FontFile regular = lookFace("Regular"), bold = lookFace("Bold");
        assertThat(regular.named()).isTrue();
        assertThat(regular.family()).isEqualTo("Jasper Look Face Tests");
        var faces = new KitParser.Faces(Optional.of(new KitParser.UiFaces(regular.family(), regular, regular, bold)),
            Optional.of(new KitParser.DataFaces(regular.family(), regular, bold)));
        assertThat(LookFonts.register(faces)).isEmpty();
        assertThat(LookFonts.register(faces)).as("registering again is harmless").isEmpty();
        var typography = Typography.of(TypeScale.DEFAULT, FaceSet.of(regular.fullName(), regular.fullName(), bold.fullName(),
            regular.fullName(), bold.fullName()), Typography.UserFont.NONE);
        assertThat(typography.font(Typography.Role.BODY).getFontName(Locale.ROOT)).isEqualTo(regular.fullName());
        assertThat(typography.font(Typography.Role.SECTION_LABEL).getFontName(Locale.ROOT)).as("weight 600").isEqualTo(bold.fullName());
        assertThat(typography.font(Typography.Role.MONO).getFontName(Locale.ROOT)).isEqualTo(regular.fullName());
        assertThat(typography.font(Typography.Role.BODY).getSize2D()).isEqualTo(13f);
    }

    @Test void aFileOfAnotherFamilyOrWithoutNamesIsNamedAndNotRegistered() throws Exception {
        var regular = cousine("Regular");
        var unnamed = FontFile.read("fonts/broken.ttf", new byte[] {1, 2, 3});
        var faces = new KitParser.Faces(Optional.of(new KitParser.UiFaces("Another Family", regular, unnamed, regular)), Optional.empty());
        assertThat(LookFonts.register(faces)).containsExactly(
            "fonts/cousine-Regular.ttf: its family is " + regular.family() + ", not Another Family; that text uses the platform font",
            "fonts/broken.ttf: its names cannot be read; that text uses the platform font",
            "fonts/cousine-Regular.ttf: its family is " + regular.family() + ", not Another Family; that text uses the platform font");
    }

    /** A file whose names read but that Java cannot load, and one whose name table disagrees with Java, are named. */
    @Test void aFileJavaCannotReadOrNamesOtherwiseIsNamedAndNotRegistered() throws Exception {
        var garbage = new FontFile("fonts/garbage.ttf", new byte[] {0, 1, 0, 0, 9, 9, 9, 9}, "Jasper Garbage Face", "Jasper Garbage");
        var regular = lookFace("Regular");
        var misnamed = new FontFile("fonts/misnamed.ttf", regular.bytes(), "Jasper Other Name", regular.family());
        assertThat(LookFonts.register(new KitParser.Faces(Optional.empty(), Optional.of(new KitParser.DataFaces("Jasper Garbage", garbage, garbage)))))
            .containsExactly("fonts/garbage.ttf: it is not a font Java can read; that text uses the platform font",
                "fonts/garbage.ttf: it is not a font Java can read; that text uses the platform font");
        assertThat(LookFonts.register(new KitParser.Faces(Optional.empty(), Optional.of(new KitParser.DataFaces(regular.family(), misnamed, misnamed)))))
            .containsExactly("fonts/misnamed.ttf: Java names it " + regular.fullName() + ", not Jasper Other Name; that text uses the platform font",
                "fonts/misnamed.ttf: Java names it " + regular.fullName() + ", not Jasper Other Name; that text uses the platform font");
        assertThat(new Font("Jasper Other Name", Font.PLAIN, 1).getFontName(Locale.ROOT)).as("not registered under that name").isNotEqualTo("Jasper Other Name");
    }

    /** Spec 5.9: a file whose full name does not resolve once registered (an installed font of its family hides it) is named. */
    @Test void aFileAnInstalledFontHidesIsNamedWhereItIsHidden() throws Exception {
        var regular = cousine("Regular");
        var problems = LookFonts.register(new KitParser.Faces(Optional.empty(), Optional.of(new KitParser.DataFaces(regular.family(), regular, regular))));
        boolean hidden = !new Font(regular.fullName(), Font.PLAIN, 1).getFontName(Locale.ROOT).equals(regular.fullName());
        String line = "fonts/cousine-Regular.ttf: an installed font named " + regular.family() + " hides it; that text uses the platform font";
        assertThat(problems).isEqualTo(hidden ? List.of(line, line) : List.of());
    }
}
