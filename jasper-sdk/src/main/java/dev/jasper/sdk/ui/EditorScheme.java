package dev.jasper.sdk.ui;

import dev.jasper.sdk.Variant;
import java.util.Objects;
import java.util.regex.Pattern;

/** One installed theme's editor scheme for its Dark or Light variant ({@link Appearance#editorSchemes()}).
 * @param id {@code <theme id>/dark} or {@code <theme id>/light}, such as {@code jasper-meadow/dark}
 * @param name the theme's name as Settings lists it, without the variant
 * @param variant the variant the scheme belongs to, which the id's suffix names
 * @param editor the scheme's surfaces
 * @param syntax the scheme's syntax styles
 * @since 0.21.0
 */
public record EditorScheme(String id, String name, Variant variant, EditorPalette editor, SyntaxPalette syntax) {
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*/(dark|light)");

    /** Validates the id against the variant and requires every value. */
    public EditorScheme {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(name, "name"); Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(editor, "editor"); Objects.requireNonNull(syntax, "syntax");
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Not an editor scheme id: " + id);
        if (!id.endsWith(variant == Variant.DARK ? "/dark" : "/light"))
            throw new IllegalArgumentException(id + " is not a " + variant + " scheme");
        if (name.isBlank()) throw new IllegalArgumentException("An editor scheme needs a name");
    }
}
