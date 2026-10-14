package dev.jasper.editor.external;

import dev.jasper.editor.external.KnownEditor.Kind;
import dev.jasper.editor.external.KnownEditor.LineForm;
import java.util.List;
import java.util.Optional;

/** The editors Jasper finds and runs, in the order Settings lists them; checked against each one's documentation on 2026-10-14. */
public final class KnownEditors {
    /** Every known editor. */
    public static final List<KnownEditor> ALL = List.of(
        terminal("nvim", "Neovim", List.of("nvim"), List.of(), LineForm.PLUS_LINE_BEFORE),
        terminal("vim", "Vim", List.of("vim"), List.of(), LineForm.PLUS_LINE_BEFORE),
        terminal("vi", "vi", List.of("vi"), List.of(), LineForm.PLUS_LINE_BEFORE),
        terminal("nano", "nano", List.of("nano"), List.of(), LineForm.PLUS_LINE_BEFORE),
        terminal("micro", "micro", List.of("micro"), List.of(), LineForm.PLUS_LINE_BEFORE),
        terminal("helix", "Helix", List.of("hx", "helix"), List.of(), LineForm.FILE_COLON_LINE),
        terminal("emacs", "Emacs", List.of("emacs"), List.of("-nw"), LineForm.PLUS_LINE_BEFORE),
        app("code", "Visual Studio Code", "code", List.of("Visual Studio Code.app/Contents/Resources/app/bin/code"), List.of("--wait"),
            LineForm.DASH_G_FILE_COLON_LINE),
        app("cursor", "Cursor", "cursor", List.of("Cursor.app/Contents/Resources/app/bin/cursor"), List.of("--wait"),
            LineForm.DASH_G_FILE_COLON_LINE),
        app("zed", "Zed", "zed", List.of("Zed.app/Contents/MacOS/cli"), List.of("--wait"), LineForm.FILE_COLON_LINE),
        app("sublime", "Sublime Text", "subl", List.of("Sublime Text.app/Contents/SharedSupport/bin/subl"), List.of("-w"),
            LineForm.FILE_COLON_LINE),
        app("bbedit", "BBEdit", "bbedit", List.of(), List.of("--wait"), LineForm.PLUS_LINE_BEFORE),
        app("textmate", "TextMate", "mate", List.of("TextMate.app/Contents/Resources/mate"), List.of("-w"), LineForm.DASH_L_LINE_BEFORE),
        app("idea", "IntelliJ IDEA", "idea", List.of(), List.of("--wait"), LineForm.LINE_OPTION_BEFORE),
        app("notepadpp", "Notepad++", "notepad++", List.of(), List.of("-multiInst", "-nosession"), LineForm.DASH_N_LINE_BEFORE),
        app("notepad", "Notepad", "notepad", List.of(), List.of(), LineForm.NONE));

    private KnownEditors() { }

    /**
     * @param id a {@code default_editor} value
     * @return the known editor with exactly this id
     */
    public static Optional<KnownEditor> byId(String id) { return ALL.stream().filter(editor -> editor.id().equals(id)).findFirst(); }

    private static KnownEditor terminal(String id, String name, List<String> executables, List<String> extraArgs, LineForm line) {
        return new KnownEditor(id, name, Kind.TERMINAL, executables, List.of(), extraArgs, List.of(), line);
    }

    private static KnownEditor app(String id, String name, String executable, List<String> macApps, List<String> waitArgs, LineForm line) {
        return new KnownEditor(id, name, Kind.APP, List.of(executable), macApps, List.of(), waitArgs, line);
    }
}
