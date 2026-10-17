package dev.jasper.app.application;

import dev.jasper.app.presentation.FontFile;
import dev.jasper.app.presentation.KitParser;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Registers a look's font files in this JVM (spec 4.9 and 5.6), right after the bundled fonts and before any font list
 * is cached. A file whose names cannot be read, that does not decode, or whose family is not the declared one is not
 * registered, and one whose full name does not resolve once registered (an installed font of its family hides it: macOS
 * finds installed fonts by family or PostScript name, never by full name) cannot be drawn: its face then draws in the
 * platform face, and it is named for the look's notice. EDT or bootstrap.
 */
final class LookFonts {
    private LookFonts() { }

    /** Registers every file {@code faces} names; the problems, one line each, in declared order. */
    static List<String> register(KitParser.Faces faces) {
        var problems = new ArrayList<String>();
        faces.ui().ifPresent(ui -> {
            for (FontFile file : List.of(ui.regular(), ui.medium(), ui.semibold())) register(file, ui.family(), problems);
        });
        faces.data().ifPresent(data -> {
            for (FontFile file : List.of(data.regular(), data.medium())) register(file, data.family(), problems);
        });
        return List.copyOf(problems);
    }

    private static void register(FontFile file, String family, List<String> problems) {
        String fallback = "; that text uses the platform font";
        if (!file.named()) { problems.add(file.path() + ": its names cannot be read" + fallback); return; }
        if (!file.family().equals(family)) { problems.add(file.path() + ": its family is " + file.family() + ", not " + family + fallback); return; }
        Font font;
        try { font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(file.bytes())); }
        catch (FontFormatException | IOException | RuntimeException undecodable) {
            problems.add(file.path() + ": it is not a font Java can read" + fallback);
            return;
        }
        if (!font.getFontName(Locale.ROOT).equals(file.fullName())) {
            problems.add(file.path() + ": Java names it " + font.getFontName(Locale.ROOT) + ", not " + file.fullName() + fallback);
            return;
        }
        // False when a font of this family or full name is already registered or installed: a copy this JVM registered
        // still resolves by its full name, an installed one (macOS) does not, and that face would draw in Dialog.
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
        if (!new Font(file.fullName(), Font.PLAIN, 1).getFontName(Locale.ROOT).equals(file.fullName()))
            problems.add(file.path() + ": an installed font named " + file.family() + " hides it" + fallback);
    }
}
