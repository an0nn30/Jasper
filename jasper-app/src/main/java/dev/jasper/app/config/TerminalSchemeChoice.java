package dev.jasper.app.config;

import dev.jasper.ui.theme.SchemeId;
import java.util.Objects;
import java.util.Optional;

/** {@code terminal.scheme}: {@code match}, the shown theme's own scheme, or one terminal scheme by its id. */
public record TerminalSchemeChoice(Optional<SchemeId> scheme) {
    /** The default: the shown theme's matching scheme. */
    public static final TerminalSchemeChoice MATCH = new TerminalSchemeChoice(Optional.empty());

    public TerminalSchemeChoice { Objects.requireNonNull(scheme, "scheme"); }

    /** One scheme, whichever theme is shown. */
    public static TerminalSchemeChoice of(SchemeId id) { return new TerminalSchemeChoice(Optional.of(id)); }

    /** {@code match} or a scheme id such as {@code slate-cobalt/high-contrast}; empty for anything else. */
    public static Optional<TerminalSchemeChoice> parse(String text) {
        if ("match".equals(text)) return Optional.of(MATCH);
        return SchemeId.parse(text).map(TerminalSchemeChoice::of);
    }

    /** The value in config.toml: {@code match} or the scheme id. */
    public String key() { return scheme.map(SchemeId::toString).orElse("match"); }
}
