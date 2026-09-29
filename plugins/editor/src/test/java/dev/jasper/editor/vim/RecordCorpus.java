package dev.jasper.editor.vim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Records the corpus's expectations from real Vim (spec 11.1): {@code ./gradlew :jasper-plugin-editor:recordVimCorpus}.
 * Each case runs in its own {@code vim -u NONE -i NONE -N}, which types the keys with feedkeys() and writes the text,
 * cursor, mode and registers as JSON. Divergent cases are skipped. Not part of {@code check}.
 *
 * <p>Arguments: the corpus folder, the Vim binary, and an optional case-name filter (a substring). A case with
 * {@code ext:} runs with its extensions' plugins, cloned at the commits {@code vim-oracle.properties} (in the Editor
 * plugin's folder) pins into {@code build/vim-oracle}, and tpope/vim-repeat, which lets {@code .} repeat them.
 */
public final class RecordCorpus {
    private RecordCorpus() { }

    /** The Editor plugin's folder: the nearest one above the corpus that holds {@code vim-oracle.properties}. */
    private static Path project;

    public static void main(String[] arguments) throws Exception {
        Path folder = Path.of(arguments[0]);
        project = folder.toAbsolutePath().normalize();
        while (project != null && !Files.exists(project.resolve("vim-oracle.properties"))) project = project.getParent();
        String vim = arguments.length > 1 && !arguments[1].isBlank() ? arguments[1] : "vim";
        String filter = arguments.length > 2 ? arguments[2] : "";
        String version = version(vim);
        List<Path> files;
        try (Stream<Path> listing = Files.list(folder)) { files = listing.filter(p -> p.toString().endsWith(".case")).sorted().toList(); }
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
        int recorded = 0;
        try {
            for (Path file : files) {
                List<CorpusCase> cases = CorpusFile.read(file);
                Map<String, Future<CorpusCase.Expected>> pending = new LinkedHashMap<>();
                for (CorpusCase c : cases) {
                    if (c.divergent() != null || !c.name().contains(filter)) continue;
                    pending.put(c.name(), pool.submit(() -> record(vim, version, c)));
                }
                Map<String, List<String>> lines = new LinkedHashMap<>();
                for (CorpusCase c : cases) {
                    Future<CorpusCase.Expected> result = pending.get(c.name());
                    if (result == null) continue;
                    lines.put(c.name(), CorpusFile.format(c, result.get()));
                    recorded++;
                }
                if (!lines.isEmpty()) CorpusFile.writeExpectations(file, lines);
            }
        } finally { pool.shutdownNow(); }
        System.out.println("Recorded " + recorded + " cases with " + version + ".");
    }

    /**
     * The plugin an extension's cases are recorded against, cloned the first time at the commit
     * {@code vim-oracle.properties} pins ({@code name = url commit}).
     *
     * @return its folder under {@code build/vim-oracle}
     */
    static synchronized Path oracle(String name) {
        try {
            return clone(name);
        } catch (IOException | InterruptedException failure) {
            throw new IllegalStateException("Could not clone the plugin for " + name + ": " + failure.getMessage(), failure);
        }
    }

    private static Path clone(String name) throws IOException, InterruptedException {
        if (project == null) throw new IOException("no vim-oracle.properties above the corpus folder");
        var pins = new java.util.Properties();
        try (var in = Files.newBufferedReader(project.resolve("vim-oracle.properties"))) { pins.load(in); }
        String pin = pins.getProperty(name);
        if (pin == null) throw new IllegalStateException("vim-oracle.properties pins no plugin for " + name);
        String[] parts = pin.trim().split("\\s+");
        Path folder = project.resolve("build/vim-oracle").resolve(name);
        if (!Files.isDirectory(folder.resolve(".git"))) {
            Files.createDirectories(folder.getParent());
            run(folder.getParent(), "git", "clone", "--quiet", parts[0], folder.getFileName().toString());
        }
        run(folder, "git", "checkout", "--quiet", parts[1]);
        return folder;
    }

    private static void run(Path in, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(in.toFile()).redirectErrorStream(true).start();
        String said = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException(String.join(" ", command) + " failed: " + said);
    }

    static String version(String vim) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(vim, "--version").redirectErrorStream(true).start();
        String first = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().findFirst().orElse("");
        process.waitFor();
        // "VIM - Vi IMproved 9.1 (2024 Jan 02, compiled ...)" -> "vim 9.1"
        String[] words = first.split("\\s+");
        return words.length > 4 ? "vim " + words[4] : "vim";
    }

    static CorpusCase.Expected record(String vim, String version, CorpusCase c) throws IOException, InterruptedException {
        Path folder = Files.createTempDirectory("jasper-vim-case");
        try {
            Path out = folder.resolve("out.json"), script = folder.resolve("case.vim");
            Files.writeString(script, script(c, out, folder.resolve("text.txt")), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(vim, "-u", "NONE", "-i", "NONE", "-N", "-n", "--not-a-term", "-S", script.toString())
                .redirectInput(ProcessBuilder.Redirect.from(Path.of(System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null").toFile()))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException(c + ": Vim did not finish within 20 s (keys left it waiting for input?)");
            }
            if (!Files.exists(out)) throw new IllegalStateException(c + ": Vim wrote no result; check the keys");
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) Json.parse(Files.readString(out, StandardCharsets.UTF_8).trim());
            if (!result.containsKey("text")) throw new IllegalStateException(c + ": the keys left Vim waiting for more (a prompt?)");
            @SuppressWarnings("unchecked")
            List<String> text = (List<String>) result.get("text");
            Map<Character, CorpusCase.Register> registers = new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, List<Object>> regs = (Map<String, List<Object>>) result.get("regs");
            for (var entry : regs.entrySet()) {
                String type = (String) entry.getValue().get(0);
                @SuppressWarnings("unchecked")
                List<String> lines = (List<String>) entry.getValue().get(1);
                if (type.isEmpty() || lines.isEmpty()) continue;
                String kind = type.equals("V") ? "l" : type.equals("v") ? "c" : "b" + type.substring(1);
                String joined = String.join("\n", lines) + (kind.equals("l") ? "\n" : "");
                registers.put(entry.getKey().charAt(0), new CorpusCase.Register(kind, joined));
            }
            @SuppressWarnings("unchecked")
            List<Long> visual = (List<Long>) result.get("visual");
            @SuppressWarnings("unchecked")
            List<Long> view = (List<Long>) result.get("view");
            @SuppressWarnings("unchecked")
            List<String> closed = (List<String>) result.get("folds");
            List<String> output = null;
            if (c.output()) {
                output = new ArrayList<>();
                @SuppressWarnings("unchecked")
                List<String> said = (List<String>) result.get("out");
                // The recorder's Vim runs the keys while sourcing its script, so each error message comes with a
                // header naming the script and line; a typed command line has none.
                for (String line : said) {
                    if (line.isEmpty() || line.startsWith("Error detected while processing ") || line.matches("line +\\d+:")) continue;
                    output.add(line);
                }
            }
            return new CorpusCase.Expected(version, text, ((Long) result.get("line")).intValue(), ((Long) result.get("col")).intValue(),
                mode((String) result.get("mode")), visual.isEmpty() ? null : visual.get(0) + "," + visual.get(1), registers,
                c.window() == null ? null : view.get(0) + "," + view.get(1),
                c.folds() == null ? null : closed.isEmpty() ? "none" : String.join(" ", closed), output);
        } finally {
            try (Stream<Path> files = Files.list(folder)) { for (Path p : files.toList()) Files.deleteIfExists(p); }
            Files.deleteIfExists(folder);
        }
    }

    static String mode(String vim) {
        if (vim.equals("n")) return "normal";
        if (vim.equals("i")) return "insert";
        if (vim.equals("R")) return "replace";
        if (vim.equals("niI") || vim.equals("niR")) return "insert-normal";
        if (vim.startsWith("no")) return "operator-pending";
        if (vim.equals("c")) return "command-line";
        if (vim.equals("v")) return "visual";
        if (vim.equals("V")) return "visual-line";
        if (vim.equals("\u0016")) return "visual-block";
        return vim;
    }

    /** @return a Vim script that sets the case up, types its keys and writes the result to {@code out} */
    static String script(CorpusCase c, Path out, Path file) {
        List<String> lines = new ArrayList<>();
        lines.add("set encoding=utf-8");
        lines.add("scriptencoding utf-8");
        lines.add("set nocompatible");
        // Vim on macOS maps <D-v> and Visual <BS> even with -u NONE; Jasper starts with no mappings.
        lines.add("mapclear | mapclear!");
        lines.add("function! JasperRecord()");
        lines.add("  let g:jasper = {'text': getline(1, '$'), 'line': line('.'), 'col': charcol('.'), 'mode': mode(1), 'regs': {}, 'visual': []}");
        lines.add("  let g:jasper.view = [winsaveview().topline, winsaveview().leftcol]");
        lines.add("  let g:jasper.folds = []");
        lines.add("  let l = 1");
        lines.add("  while l <= line('$')");
        lines.add("    if foldclosed(l) == l");
        lines.add("      call add(g:jasper.folds, l . '-' . foldclosedend(l))");
        lines.add("      let l = foldclosedend(l)");
        lines.add("    endif");
        lines.add("    let l += 1");
        lines.add("  endwhile");
        lines.add("  if mode() =~# '^[vV\\x16]'");
        lines.add("    let g:jasper.visual = [line('v'), charcol('v')]");
        lines.add("  endif");
        lines.add("  for r in " + vimList(c.registers().stream().map(String::valueOf).toList()));
        lines.add("    let g:jasper.regs[r] = [getregtype(r), getreg(r, 1, 1)]");
        lines.add("  endfor");
        lines.add("endfunction");
        lines.add("for r in split('\"0123456789abcdefghijklmnopqrstuvwxyz-', '\\zs')");
        lines.add("  call setreg(r, [], 'c')");
        lines.add("endfor");
        if (c.window() != null) {
            int[] size = c.windowSize();
            lines.add("set columns=" + size[0] + " lines=" + (size[1] + 1));
        }
        if (!c.set().isBlank()) lines.add("set " + c.set());
        if (!c.extensions().isEmpty()) {
            // The plugins load as Vim's startup would load them, before the file is edited.
            List<Path> plugins = new ArrayList<>();
            plugins.add(oracle("repeat"));
            for (String extension : c.extensions()) plugins.add(oracle(extension.toLowerCase(java.util.Locale.ROOT)));
            for (Path plugin : plugins) lines.add("set rtp^=" + plugin.toString().replace(" ", "\\ "));
            for (Path plugin : plugins)
                lines.add("for s:f in glob(" + Json.quote(plugin + "/plugin/*.vim") + ", 0, 1) | execute 'source' fnameescape(s:f) | endfor");
        }
        for (var entry : c.given().entrySet()) {
            CorpusCase.Register register = entry.getValue();
            String text = register.type().equals("l") && register.text().endsWith("\n") ? register.text().substring(0, register.text().length() - 1) : register.text();
            lines.add("call setreg(" + Json.quote(String.valueOf(entry.getKey())) + ", " + vimList(List.of(text.split("\n", -1))) + ", " + Json.quote(register.type()) + ")");
        }
        // Editing a file, rather than setline(), leaves no undo history for u to reach.
        lines.add("call writefile(" + vimList(c.text()) + ", " + Json.quote(file.toString()) + ")");
        lines.add("silent execute 'edit!' fnameescape(" + Json.quote(file.toString()) + ")");
        if (c.folds() != null) lines.addAll(foldCommands(c));
        lines.add("call setcursorcharpos(" + c.line() + ", " + c.col() + ")");
        lines.add("let g:jasper = {}");
        // What the keys say is captured; 'nomore' keeps a long listing from waiting for a key.
        lines.add("set nomore");
        lines.add("let g:jasper_out = ''");
        lines.add("redir => g:jasper_out");
        lines.add("call feedkeys(\"" + vimKeys(c.keys()) + "\\<Cmd>call JasperRecord()\\<CR>\", 'xt')");
        lines.add("redir END");
        lines.add("let g:jasper.out = split(g:jasper_out, \"\\n\", 1)");
        lines.add("call writefile([json_encode(g:jasper)], " + Json.quote(out.toString()) + ")");
        lines.add("qa!");
        return String.join("\n", lines) + "\n";
    }

    /**
     * The case's code folds as Vim manual folds: RSyntaxTextArea's Java fold parser finds them in the text, inner
     * folds are made first (a fold made over a closed one would take it whole), all are opened, and then the ones the
     * case lists are closed, innermost (last-starting) first.
     */
    static List<String> foldCommands(CorpusCase c) {
        List<String> out = new ArrayList<>();
        var area = new VimTextArea();
        area.setText(String.join("\n", c.text()) + "\n");
        CorpusRunner.javaFolds(area, List.of());
        List<org.fife.ui.rsyntaxtextarea.folding.Fold> folds = new ArrayList<>(CorpusRunner.folds(area.getFoldManager()));
        java.util.Collections.reverse(folds);
        for (var fold : folds) out.add((fold.getStartLine() + 1) + "," + (fold.getEndLine() + 1) + "fold");
        out.add("normal! zR");
        // Innermost first: closing a line's fold when an outer one is already closed would close nothing.
        List<Integer> closed = new ArrayList<>(c.closedAtStart());
        closed.sort(java.util.Comparator.reverseOrder());
        for (int line : closed) out.add(line + "foldclose");
        return out;
    }

    private static String vimList(List<String> items) {
        List<String> quoted = items.stream().map(Json::quote).toList();
        return "[" + String.join(", ", quoted) + "]";
    }

    /** @return the keys as the inside of a Vim double-quoted string: {@code <Esc>} becomes {@code \<Esc>} */
    static String vimKeys(String notation) {
        StringBuilder out = new StringBuilder();
        for (KeyInput key : KeyInput.parse(notation)) {
            if (key.isChar() && key.modifiers() == 0) {
                char c = key.key().charAt(0);
                if (c == '\\') out.append("\\\\");
                else if (c == '"') out.append("\\\"");
                else if (key.key().length() == 1 && c < 0x20) out.append(String.format("\\x%02x", (int) c));
                else out.append(key.key());
            } else out.append('\\').append(key.notation());
        }
        return out.toString();
    }
}
