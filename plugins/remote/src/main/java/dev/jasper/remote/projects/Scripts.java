package dev.jasper.remote.projects;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The fixed scripts, built with every path and pattern quoted. Each prints its marker and a NUL, then NUL-separated
 * fields; it exits 3 when the root cannot be entered and 4 when its tool is unavailable.
 */
final class Scripts {
    static final String EXISTS = "jasper-exists";
    static final String LIST = "jasper-list";

    private Scripts() { }

    /**
     * Which names exist in which folders, as folder and name field pairs. Names with {@code *} are globs. Folders that
     * are the login's home or above it report nothing, so detection stops below home.
     */
    static byte[] exists(List<String> folders, Set<String> names) {
        var literals = names.stream().filter(name -> name.indexOf('*') < 0 && usable(name)).sorted().toList();
        var globs = names.stream().filter(name -> name.indexOf('*') >= 0 && usable(name)).sorted().toList();
        var out = new StringBuilder("printf '" + EXISTS + "\\000'\n");
        var quoted = folders.stream().filter(Scripts::usable).map(ShellText::quote).toList();
        if (quoted.isEmpty()) return bytes(out);
        out.append("for d in ").append(String.join(" ", quoted)).append("\ndo\n");
        out.append("  case \"$HOME/\" in \"${d%/}\"/*) continue ;; esac\n");
        if (!literals.isEmpty()) {
            out.append("  for n in ").append(String.join(" ", literals.stream().map(ShellText::quote).toList())).append("\n  do\n");
            out.append("    if [ -e \"$d/$n\" ] || [ -L \"$d/$n\" ]; then printf '%s\\000%s\\000' \"$d\" \"$n\"; fi\n  done\n");
        }
        if (!globs.isEmpty()) {
            out.append("  for e in ").append(String.join(" ", globs.stream().map(glob -> "\"$d\"/" + ShellText.glob(glob)).toList()))
                .append("\n  do\n");
            out.append("    if [ -e \"$e\" ] || [ -L \"$e\" ]; then printf '%s\\000%s\\000' \"$d\" \"${e##*/}\"; fi\n  done\n");
        }
        out.append("done\n");
        return bytes(out);
    }

    /** Git's view of the work tree at {@code root}; exits 4 when Git is missing, the root is not in a work tree, or the tree's top is home. */
    static byte[] git(String root) {
        return bytes(new StringBuilder()
            .append("cd -- ").append(ShellText.quote(root)).append(" 2>/dev/null || exit 3\n")
            .append("command -v git >/dev/null 2>&1 || exit 4\n")
            .append("top=$(git -c core.fsmonitor=false rev-parse --show-toplevel 2>/dev/null) || exit 4\n")
            .append("top=$(cd -- \"$top\" 2>/dev/null && pwd -P) || exit 4\n")
            .append("home=$(cd -- \"${HOME:-/nonexistent}\" 2>/dev/null && pwd -P)\n")
            .append("[ \"$top\" = \"$home\" ] && exit 4\n")
            .append("printf '").append(LIST).append("\\000'\n")
            .append("GIT_OPTIONAL_LOCKS=0 exec git -c core.fsmonitor=false ls-files -z --cached --others --exclude-standard 2>/dev/null\n"));
    }

    /** Every file under {@code root}, as {@code ./}-prefixed paths, skipping folders with excluded names; exits 4 without {@code find}. */
    static byte[] find(String root, List<String> excludes) {
        var usable = excludes.stream().filter(Scripts::usable).toList();
        var out = new StringBuilder()
            .append("cd -- ").append(ShellText.quote(root)).append(" 2>/dev/null || exit 3\n")
            .append("command -v find >/dev/null 2>&1 || exit 4\n")
            .append("printf '").append(LIST).append("\\000'\n")
            .append("exec find .");
        if (!usable.isEmpty()) {
            out.append(" \\( -type d \\(");
            for (int i = 0; i < usable.size(); i++) out.append(i == 0 ? " -name " : " -o -name ").append(ShellText.quote(usable.get(i)));
            out.append(" \\) -prune \\) -o");
        }
        out.append(" -type f -print0 2>/dev/null\n");
        return bytes(out);
    }

    /** @return the {@link #exists} fields as folder to names */
    static Map<String, Set<String>> pairs(List<String> fields) {
        var result = new LinkedHashMap<String, Set<String>>();
        for (int i = 0; i + 1 < fields.size(); i += 2) result.computeIfAbsent(fields.get(i), folder -> new TreeSet<>()).add(fields.get(i + 1));
        return result;
    }

    private static boolean usable(String text) { return !text.isEmpty() && text.indexOf('\0') < 0; }

    private static byte[] bytes(StringBuilder script) { return script.toString().getBytes(StandardCharsets.UTF_8); }
}
