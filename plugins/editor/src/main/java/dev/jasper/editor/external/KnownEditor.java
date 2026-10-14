package dev.jasper.editor.external;

import java.util.List;
import java.util.Objects;

/**
 * An editor Jasper knows how to find and run.
 *
 * @param id the {@code default_editor} value that names it
 * @param name what Settings lists
 * @param kind whether it runs in a terminal or as an app of its own
 * @param executables the program names looked for in each folder, in order
 * @param macApps on macOS, its command-line tool inside an app, relative to an Applications folder
 * @param extraArgs arguments it always gets before the file ({@code -nw} for Emacs)
 * @param waitArgs what makes an app editor return only once the file is closed, for {@code EDITOR}
 * @param lineForm how it is told the line to open at
 */
public record KnownEditor(String id, String name, Kind kind, List<String> executables, List<String> macApps,
                          List<String> extraArgs, List<String> waitArgs, LineForm lineForm) {
    /** Where an editor runs. */
    public enum Kind {
        /** In a terminal: Jasper runs it in a tab. */
        TERMINAL,
        /** In a window of its own: Jasper starts it on its own. */
        APP
    }

    /** How an editor is told the line to open at; without a line every form is just {@code FILE}. */
    public enum LineForm {
        /** {@code +LINE FILE} */
        PLUS_LINE_BEFORE,
        /** {@code FILE:LINE} */
        FILE_COLON_LINE,
        /** {@code -g FILE:LINE} */
        DASH_G_FILE_COLON_LINE,
        /** {@code -l LINE FILE} */
        DASH_L_LINE_BEFORE,
        /** {@code --line LINE FILE} */
        LINE_OPTION_BEFORE,
        /** {@code -nLINE FILE} */
        DASH_N_LINE_BEFORE,
        /** {@code FILE}: the editor takes no line */
        NONE
    }

    /** Copies the lists; rejects nulls and an editor without a program name. */
    public KnownEditor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        executables = List.copyOf(executables);
        macApps = List.copyOf(macApps);
        extraArgs = List.copyOf(extraArgs);
        waitArgs = List.copyOf(waitArgs);
        Objects.requireNonNull(lineForm, "lineForm");
        if (executables.isEmpty()) throw new IllegalArgumentException("An editor needs a program name: " + id);
    }
}
