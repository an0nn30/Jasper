package dev.jasper.app.appearance;

import java.util.Locale;
import java.util.Optional;

/** The syntax roles a theme colours (a theme's {@code [syntax]}); the editor maps them onto its token types. */
public enum SyntaxRole {
    KEYWORD, TYPE, FUNCTION, VARIABLE, CONSTANT, NUMBER, STRING, COMMENT, DOC_COMMENT, ANNOTATION, OPERATOR,
    PUNCTUATION, TAG, ATTRIBUTE, REGEX, ERROR;

    /** The TOML key, for example {@code doc_comment}. */
    public String key() { return name().toLowerCase(Locale.ROOT); }

    /** The role with this TOML key. */
    public static Optional<SyntaxRole> of(String key) {
        for (SyntaxRole role : values()) if (role.key().equals(key)) return Optional.of(role);
        return Optional.empty();
    }
}
