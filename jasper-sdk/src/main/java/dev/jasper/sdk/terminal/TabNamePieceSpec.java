package dev.jasper.sdk.terminal;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A piece a plugin adds to Terminal tab names. Users write it as {@code {name}} in Settings › Terminal › Tab name
 * ({@code terminal.tab_name}), and each terminal tab shows the value the plugin set for it through the
 * {@link TabNamePiece} that {@link Terminals#addTabNamePiece} returns; a tab without a value shows nothing for it.
 *
 * @param name what users type between the braces: 1 to 32 lowercase letters, digits and {@code _}, starting with a
 *     letter, and none of {@link #BUILT_IN}
 * @param description one line Settings lists beside the piece, such as "the SSH host this tab is connected to"
 * @since 0.22.0
 */
public record TabNamePieceSpec(String name, String description) {
    /** Jasper's own pieces, {@code dir}, {@code full}, {@code process} and {@code title}, which no plugin can add. */
    public static final Set<String> BUILT_IN = Set.of("dir", "full", "process", "title");
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    /**
     * Checks the piece.
     *
     * @throws IllegalArgumentException for a name that is not a piece name or is one of Jasper's own, or a description
     *     that is blank or more than one line
     */
    public TabNamePieceSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        if (!NAME.matcher(name).matches())
            throw new IllegalArgumentException("A tab name piece is 1 to 32 lowercase letters, digits and _, starting with a letter: " + name);
        if (BUILT_IN.contains(name)) throw new IllegalArgumentException("{" + name + "} is one of Jasper's own tab name pieces");
        if (description.isBlank() || description.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("A tab name piece's description is one nonblank line");
    }
}
