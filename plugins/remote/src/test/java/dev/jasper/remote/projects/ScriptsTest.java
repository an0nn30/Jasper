package dev.jasper.remote.projects;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ScriptsTest {
    @TempDir Path dir;

    private static boolean windows() { return System.getProperty("os.name").startsWith("Windows"); }

    private static String shell() {
        if (!windows()) return "sh";
        Path bash = Path.of(System.getenv("ProgramFiles"), "Git", "usr", "bin", "bash.exe");
        assertThat(bash).as("Git for Windows supplies the POSIX test shell").isRegularFile();
        return bash.toString();
    }

    private static String shellPath(Path path) {
        String text = path.toAbsolutePath().toString().replace('\\', '/');
        return windows() ? "/" + Character.toLowerCase(text.charAt(0)) + text.substring(2) : text;
    }

    record Run(int status, List<String> fields) { }

    Run run(byte[] script, Path home, String marker) throws Exception {
        var builder = new ProcessBuilder(shell(), "-s").redirectError(ProcessBuilder.Redirect.DISCARD);
        if (home == null) builder.environment().remove("HOME"); else builder.environment().put("HOME", shellPath(home));
        if (windows()) {
            String unixTools = Path.of(shell()).getParent().toString();
            builder.environment().put("PATH", unixTools + java.io.File.pathSeparator + builder.environment().get("PATH"));
        }
        Process process = builder.start();
        try (var stdin = process.getOutputStream()) { stdin.write(script); }
        var reading = CompletableFuture.supplyAsync(() -> {
            try { return process.getInputStream().readAllBytes(); }
            catch (IOException closed) { return new byte[0]; }
        });
        byte[] output;
        try {
            output = reading.get(10, TimeUnit.SECONDS);
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).as("script finished").isTrue();
        } finally {
            process.destroyForcibly();
        }
        var fields = new ArrayList<String>();
        byte[] start = (marker + "\0").getBytes(StandardCharsets.UTF_8);
        int at = indexOf(output, start);
        if (at >= 0) {
            var field = new ByteArrayOutputStream();
            for (int i = at + start.length; i < output.length; i++) {
                if (output[i] != 0) { field.write(output[i]); continue; }
                fields.add(field.toString(StandardCharsets.UTF_8));
                field.reset();
            }
        }
        return new Run(process.exitValue(), fields);
    }

    static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) if (haystack[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    @Test void existsReportsLiteralAndGlobMarkersBelowHomeOnly() throws Exception {
        Path home = Files.createDirectories(dir.resolve("home")).toRealPath();
        Files.createDirectories(home.resolve(".git"));
        Path app = Files.createDirectories(home.resolve("src/it's $(touch pwned)"));
        Files.createDirectories(app.resolve(".git"));
        Files.writeString(app.resolve("Shop.sln"), "");
        Path lib = Files.createDirectories(app.resolve("lib"));
        Files.writeString(lib.resolve("Cargo.toml"), "");
        List<String> folders = List.of(shellPath(lib), shellPath(app), shellPath(home.resolve("src")), shellPath(home), shellPath(home) + "/",
            shellPath(home.getParent()), shellPath(home.getParent()) + "/", "/");
        var run = run(Scripts.exists(folders, Set.of(".git", "Cargo.toml", "*.sln", "usr", "us*")), home, Scripts.EXISTS);
        assertThat(run.status()).isZero();
        Map<String, Set<String>> found = Scripts.pairs(run.fields());
        assertThat(found).containsOnlyKeys(shellPath(lib), shellPath(app));
        assertThat(found).as("home, its ancestors and / report nothing").doesNotContainKeys("/", shellPath(home), shellPath(home) + "/", shellPath(home.getParent()));
        assertThat(found.get(shellPath(lib))).containsExactly("Cargo.toml");
        assertThat(found.get(shellPath(app))).containsExactlyInAnyOrder(".git", "Shop.sln");
        assertThat(dir.resolve("pwned")).as("names are data, never code").doesNotExist();
        assertThat(home.resolve("src/pwned")).doesNotExist();
    }

    @Test void findListsFilesSkippingExcludedFoldersAndAMissingRootExitsThree() throws Exception {
        Path root = Files.createDirectories(dir.resolve("app"));
        Files.createDirectories(root.resolve("node_modules/x"));
        Files.writeString(root.resolve("node_modules/x/index.js"), "");
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/main.rs"), "");
        String oddName = windows() ? "odd 'name" : "odd\nname";
        Files.writeString(root.resolve(oddName), "");
        var run = run(Scripts.find(shellPath(root), List.of("node_modules")), dir, Scripts.LIST);
        assertThat(run.status()).isZero();
        assertThat(run.fields()).containsExactlyInAnyOrder("./src/main.rs", "./" + oddName);
        assertThat(run(Scripts.find(shellPath(dir.resolve("gone")), List.of()), dir, Scripts.LIST).status()).isEqualTo(3);
    }

    @Test void gitListsAWorkTreeAndDeclinesOneWhoseTopIsHome() throws Exception {
        assumeTrue(gitAvailable());
        Path home = Files.createDirectories(dir.resolve("home")).toRealPath();
        Path root = Files.createDirectories(home.resolve("app"));
        git(root, "init", "-q");
        Files.writeString(root.resolve(".gitignore"), "ignored\n");
        Files.writeString(root.resolve("ignored"), "");
        Files.writeString(root.resolve("kept.txt"), "");
        var run = run(Scripts.git(shellPath(root)), home, Scripts.LIST);
        assertThat(run.status()).isZero();
        assertThat(run.fields()).containsExactlyInAnyOrder(".gitignore", "kept.txt");
        assertThat(run(Scripts.git(shellPath(root)), null, Scripts.LIST).fields()).as("no HOME is no home folder").contains("kept.txt");
        git(home, "init", "-q");
        Path plain = Files.createDirectories(home.resolve("plain"));
        assertThat(run(Scripts.git(shellPath(plain)), home, Scripts.LIST).status()).as("home's own work tree").isEqualTo(4);
        Path outside = Files.createDirectories(dir.resolve("outside"));
        assertThat(run(Scripts.git(shellPath(outside)), home, Scripts.LIST).status()).as("not a work tree").isEqualTo(4);
    }

    @Test void gitComparesTheCanonicalWorkTreeToHome() throws Exception {
        Path home = Files.createDirectories(dir.resolve("home")).toRealPath();
        Path nested = Files.createDirectories(home.resolve("nested"));
        String git = "git() { if [ \"$3\" = rev-parse ]; then printf '%s/./\\n' \"$HOME\"; else return 99; fi; }\n";
        byte[] script = (git + new String(Scripts.git(shellPath(nested)), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        assertThat(run(script, home, Scripts.LIST).status()).isEqualTo(4);
    }

    static boolean gitAvailable() {
        try { Process process = new ProcessBuilder("git", "--version").start(); process.getInputStream().readAllBytes(); return process.waitFor() == 0; }
        catch (IOException | InterruptedException missing) { return false; }
    }

    static void git(Path directory, String... args) throws Exception {
        var command = new ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assertThat(process.waitFor()).isZero();
    }
}
