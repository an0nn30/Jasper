package dev.jasper.editor.vim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the Vim documentation true (spec 11.1 and 12): every divergent corpus case is listed in
 * docs/vim-divergences.md, and docs/editor-vim-reference.md is generated from {@link VimCommands}. Regenerate the
 * reference with {@code ./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true}.
 */
class VimDocsTest {
    private static final Path DOCS = Path.of(System.getProperty("jasper.repoRoot", "../..")).toAbsolutePath().normalize().resolve("docs");

    @Test void everyDivergentCaseIsListedWithItsReason() throws IOException {
        String doc = Files.readString(DOCS.resolve("vim-divergences.md"), StandardCharsets.UTF_8);
        List<CorpusCase> divergent = VimCorpusTest.cases().stream().filter(c -> c.divergent() != null).toList();
        for (CorpusCase c : divergent)
            assertThat(doc).as("docs/vim-divergences.md lists %s", c).contains("| " + c.file() + " | " + c.name() + " | " + c.divergent() + " |");
        long rows = doc.lines().filter(line -> line.startsWith("| ") && line.contains(".case | ")).count();
        assertThat(rows).as("rows in docs/vim-divergences.md's corpus table").isEqualTo(divergent.size());
    }

    @Test void theReferenceIsGeneratedFromTheCommandTable() throws IOException {
        Path file = DOCS.resolve("editor-vim-reference.md");
        String expected = reference(VimCommands.standard());
        if (Boolean.parseBoolean(System.getProperty("jasper.updateDocs", "false"))) Files.writeString(file, expected, StandardCharsets.UTF_8);
        assertThat(Files.readString(file, StandardCharsets.UTF_8))
            .as("docs/editor-vim-reference.md is stale: run ./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true")
            .isEqualTo(expected);
    }

    static String reference(VimCommands commands) {
        StringBuilder out = new StringBuilder();
        out.append("# Editor Vim mode: supported keys\n\n");
        out.append("Generated from `VimCommands` by `VimDocsTest`; do not edit by hand. Regenerate with\n");
        out.append("`./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true`. How Vim mode works: [Editor](editor.md#vim-mode);\n");
        out.append("where it differs from Vim: [divergences](vim-divergences.md).\n");
        section(out, commands, VimCommands.Kind.MOTION, "Motions",
            "In Normal and Visual mode and after an operator. A count repeats or scales them. `/` and `?` read a pattern and an offset in the Vim bar, ended by `<CR>`.");
        section(out, commands, VimCommands.Kind.OPERATOR, "Operators",
            "Followed by a motion or text object, or on the Visual area; doubled (`dd`, `gUU`, `gUgU`) for whole lines.");
        section(out, commands, VimCommands.Kind.OBJECT, "Text objects", "After an operator, and in Visual mode, where they extend the area.");
        section(out, commands, VimCommands.Kind.ACTION, "Normal-mode commands", "");
        section(out, commands, VimCommands.Kind.VISUAL, "Visual-mode commands",
            "In Visual mode, besides the motions, operators and text objects above. A change repeats with `.` on an area of the same size.");
        section(out, commands, VimCommands.Kind.INSERT, "Insert-mode keys", "Printable keys type text; these keys do more.");
        exCommands(out);
        return out.toString();
    }

    /** The Ex commands, with Vim's shortest abbreviation outside the brackets ({@code :s[ubstitute]}). */
    private static void exCommands(StringBuilder out) {
        out.append("\n## Ex commands\n\n");
        out.append("Typed after `:` and ended by `<CR>`. A range goes before the name: `3`, `.`, `$`, `%`, `'x`, `/pattern/`, `?pattern?`,\n");
        out.append("`\\/`, `\\?` and `\\&`, each with `+N` or `-N` offsets, separated by `,` or `;`; `'<,'>` and `*` are the last Visual lines.\n");
        out.append("`|` separates commands. When a command line says more than one line, the output stays above the Vim bar until a\n");
        out.append("key is typed. A command Vim has and Jasper does not fails with E319.\n\n");
        out.append("| Command | What it does |\n|---|---|\n");
        for (Ex.Def def : Ex.implemented()) {
            String abbreviation = def.abbreviation();
            String name = abbreviation.equals(def.name()) ? ":" + def.name() : ":" + abbreviation + "[" + def.name().substring(abbreviation.length()) + "]";
            out.append("| `").append(name.replace("|", "\\|")).append("` | ").append(def.description().replace("|", "\\|")).append(" |\n");
        }
    }

    private static void section(StringBuilder out, VimCommands commands, VimCommands.Kind kind, String title, String note) {
        out.append("\n## ").append(title).append("\n\n");
        if (!note.isEmpty()) out.append(note).append("\n\n");
        out.append("| Keys | What it does |\n|---|---|\n");
        for (VimCommands.Entry entry : commands.entries()) {
            if (entry.kind() != kind) continue;
            String keys = entry.keys().equals(" ") ? "<Space>" : entry.keys();
            if (entry.needsChar()) keys += "{char}";
            String code = keys.contains("`") ? "`` " + keys + " ``" : "`" + keys + "`";
            out.append("| ").append(code.replace("|", "\\|")).append(" | ").append(entry.description().replace("|", "\\|")).append(" |\n");
        }
    }
}
