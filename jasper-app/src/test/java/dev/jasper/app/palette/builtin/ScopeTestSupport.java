package dev.jasper.app.palette.builtin;

import dev.jasper.app.commands.Command;
import dev.jasper.app.palette.PaletteRow;
import java.util.List;

/** Package-local test access, excluded from production artifacts. */
public final class ScopeTestSupport {
    public static List<PaletteRow> rows(CommandsScope scope, List<Command> commands) { return scope.rows(commands); }
    public static String rowId(String name) { return SnippetsScope.rowId(name); }
}
