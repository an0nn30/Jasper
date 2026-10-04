package dev.jasper.ui.themeimport;

import dev.jasper.ui.theme.ThemeDocument;
import dev.jasper.ui.themeio.ThemeToml;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Bundled native parent inputs, independent of the application's runtime catalog. */
final class NativeThemeParents {
    private NativeThemeParents() {}
    static List<ThemeDocument> named(String name) {
        return Inputs.DOCUMENTS.stream().filter(document -> document.name().strip().equalsIgnoreCase(name.strip())).toList();
    }
    private static final class Inputs {
        static final List<ThemeDocument> DOCUMENTS = read("index.txt").lines()
            .map(file -> ThemeToml.parse(read(file), file)).toList();
    }
    private static String read(String file) {
        try (var input = NativeThemeParents.class.getResourceAsStream("native/" + file)) {
            if (input == null) throw new IllegalStateException("Missing native import parent " + file);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
