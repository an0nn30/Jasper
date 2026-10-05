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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ScriptsTest {
    @TempDir Path dir;

    record Run(int status, List<String> fields) { }

    Run run(byte[] script, Path home, String marker) throws Exception {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        var builder = new ProcessBuilder("sh", "-s").redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("HOME", home.toString());
        Process process = builder.start();
        try (var stdin = process.getOutputStream()) { stdin.write(script); }
        byte[] output = process.getInputStream().readAllBytes();
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
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
        List<String> folders = List.of(lib.toString(), app.toString(), home.resolve("src").toString(), home.toString(), home.getParent().toString());
        var run = run(Scripts.exists(folders, Set.of(".git", "Cargo.toml", "*.sln")), home, Scripts.EXISTS);
        assertThat(run.status()).isZero();
        Map<String, Set<String>> found = Scripts.pairs(run.fields());
        assertThat(found).containsOnlyKeys(lib.toString(), app.toString());
        assertThat(found.get(lib.toString())).containsExactly("Cargo.toml");
        assertThat(found.get(app.toString())).containsExactlyInAnyOrder(".git", "Shop.sln");
        assertThat(dir.resolve("pwned")).as("names are data, never code").doesNotExist();
        assertThat(home.resolve("src/pwned")).doesNotExist();
    }

    @Test void findListsFilesSkippingExcludedFoldersAndAMissingRootExitsThree() throws Exception {
        Path root = Files.createDirectories(dir.resolve("app"));
        Files.createDirectories(root.resolve("node_modules/x"));
        Files.writeString(root.resolve("node_modules/x/index.js"), "");
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/main.rs"), "");
        Files.writeString(root.resolve("odd\nname"), "");
        var run = run(Scripts.find(root.toString(), List.of("node_modules")), dir, Scripts.LIST);
        assertThat(run.status()).isZero();
        assertThat(run.fields()).containsExactlyInAnyOrder("./src/main.rs", "./odd\nname");
        assertThat(run(Scripts.find(dir.resolve("gone").toString(), List.of()), dir, Scripts.LIST).status()).isEqualTo(3);
    }

    @Test void gitListsAWorkTreeAndDeclinesOneWhoseTopIsHome() throws Exception {
        assumeTrue(gitAvailable());
        Path home = Files.createDirectories(dir.resolve("home")).toRealPath();
        Path root = Files.createDirectories(home.resolve("app"));
        git(root, "init", "-q");
        Files.writeString(root.resolve(".gitignore"), "ignored\n");
        Files.writeString(root.resolve("ignored"), "");
        Files.writeString(root.resolve("kept.txt"), "");
        var run = run(Scripts.git(root.toString()), home, Scripts.LIST);
        assertThat(run.status()).isZero();
        assertThat(run.fields()).containsExactlyInAnyOrder(".gitignore", "kept.txt");
        git(home, "init", "-q");
        Path plain = Files.createDirectories(home.resolve("plain"));
        assertThat(run(Scripts.git(plain.toString()), home, Scripts.LIST).status()).as("home's own work tree").isEqualTo(4);
        Path outside = Files.createDirectories(dir.resolve("outside"));
        assertThat(run(Scripts.git(outside.toString()), home, Scripts.LIST).status()).as("not a work tree").isEqualTo(4);
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
