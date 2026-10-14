package dev.jasper.editor.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Detection over a fake file system: {@link #files} is every program that exists. Paths hang off this machine's root,
 * so the expectations hold on Windows too, where {@code /opt/homebrew/bin} resolves against the current drive.
 */
class DetectionTest {
    private static final Path ROOT = Path.of("").toAbsolutePath().getRoot();
    private static final Path HOME = ROOT.resolve("Users").resolve("me");
    private static final String MAC = "Mac OS X", LINUX = "Linux", WINDOWS = "Windows 11";

    private final Set<Path> files = new HashSet<>();

    /** A path under the root, as detection names what it finds: absolute and normalized. */
    private static Path path(String relative) { return ROOT.resolve(relative).toAbsolutePath().normalize(); }

    /** A program on the fake file system. */
    private Path program(String relative) {
        Path program = path(relative);
        files.add(program);
        return program;
    }

    private Detection.Probe probe(String osName, List<Path> path, Map<String, String> environment) {
        return new Detection.Probe(osName, path, HOME, environment, files::contains);
    }

    private List<String> detect(String osName, List<Path> path, Map<String, String> environment) {
        return Detection.detect(probe(osName, path, environment)).stream()
            .map(found -> found.editor().id() + "=" + found.executable()).toList();
    }

    @Test void pathOrderWinsAndEachEditorIsFoundOnce() {
        Path first = program("a/bin/nvim");
        program("b/bin/nvim");
        Path vim = program("b/bin/vim");
        assertThat(detect(LINUX, List.of(path("a/bin"), path("b/bin")), Map.of())).containsExactly("nvim=" + first, "vim=" + vim);
    }

    @Test void homebrewLocalAndTheUsersBinAreSearchedAfterAShortPath() {
        Path nvim = program("opt/homebrew/bin/nvim");
        Path micro = program("usr/local/bin/micro");
        Path hx = program("Users/me/.local/bin/hx");
        assertThat(detect(MAC, List.of(path("usr/bin"), path("bin")), Map.of()))
            .containsExactly("nvim=" + nvim, "micro=" + micro, "helix=" + hx);
    }

    @Test void macAppToolsAreFoundOnMacOsOnly() {
        Path code = program("Applications/Visual Studio Code.app/Contents/Resources/app/bin/code");
        Path zed = program("Users/me/Applications/Zed.app/Contents/MacOS/cli");
        Path mate = program("Applications/TextMate.app/Contents/Resources/mate");
        assertThat(detect(MAC, List.of(), Map.of())).containsExactly("code=" + code, "zed=" + zed, "textmate=" + mate);
        assertThat(detect(LINUX, List.of(), Map.of())).isEmpty();
    }

    @Test void aToolOnPathWinsOverTheOneInsideItsApp() {
        Path onPath = program("opt/homebrew/bin/code");
        program("Applications/Visual Studio Code.app/Contents/Resources/app/bin/code");
        assertThat(detect(MAC, List.of(), Map.of())).containsExactly("code=" + onPath);
    }

    @Test void intellijsAppBundleIsNeverFound() {
        program("Applications/IntelliJ IDEA.app/Contents/MacOS/idea");
        program("Applications/IntelliJ IDEA CE.app/Contents/MacOS/idea");
        assertThat(detect(MAC, List.of(), Map.of())).as("its binary does not wait").isEmpty();
        Path idea = program("usr/local/bin/idea");
        assertThat(detect(MAC, List.of(), Map.of())).containsExactly("idea=" + idea);
    }

    @Test void helixIsFoundAsHxOrHelix() {
        Path helix = program("usr/bin/helix");
        assertThat(detect(LINUX, List.of(path("usr/bin")), Map.of())).containsExactly("helix=" + helix);
        Path hx = program("usr/bin/hx");
        assertThat(detect(LINUX, List.of(path("usr/bin")), Map.of())).as("hx first").containsExactly("helix=" + hx);
    }

    @Test void windowsTriesExeCmdAndBatAndNotepadsOwnFolders() {
        program("tools/code");                                      // VS Code's script for POSIX shells, beside code.cmd
        Path code = program("tools/code.cmd");
        Path vim = program("tools/vim.bat");
        Path subl = program("tools/subl.exe");
        Path notepadpp = program("Program Files (x86)/Notepad++/notepad++.exe");
        Path notepad = program("Windows/System32/notepad.exe");
        program("opt/homebrew/bin/nvim");
        var environment = Map.of("ProgramFiles", path("Program Files").toString(),
            "ProgramFiles(x86)", path("Program Files (x86)").toString(), "SYSTEMROOT", path("Windows").toString());
        assertThat(detect(WINDOWS, List.of(path("tools")), environment)).as("no Unix folders; names ignore case")
            .containsExactly("vim=" + vim, "code=" + code, "sublime=" + subl, "notepadpp=" + notepadpp, "notepad=" + notepad);
    }

    @Test void anEmptySystemHasNoEditors() {
        assertThat(detect(MAC, List.of(), Map.of())).isEmpty();
        assertThat(detect(LINUX, List.of(path("usr/bin")), Map.of())).isEmpty();
        assertThat(detect(WINDOWS, List.of(), Map.of())).isEmpty();
    }

    @Test void theSystemProbeReadsThisJvm() {
        var probe = Detection.Probe.system();
        assertThat(probe.osName()).isEqualTo(System.getProperty("os.name"));
        assertThat(probe.path()).allSatisfy(folder -> assertThat(folder.isAbsolute()).as("%s", folder).isTrue());
        assertThat(probe.home()).isEqualTo(Path.of(System.getProperty("user.home")));
    }

    @Test void findReturnsWhatDetectListsForAnInstalledEditor() {
        program("a/bin/nvim");
        Path vim = program("b/bin/vim");
        Path code = program("Applications/Visual Studio Code.app/Contents/Resources/app/bin/code");
        var probe = probe(MAC, List.of(path("a/bin"), path("b/bin")), Map.of());
        var detected = Detection.detect(probe);
        for (Detected listed : detected) {
            assertThat(Detection.find(probe, listed.editor())).as(listed.editor().id()).contains(listed);
        }
        assertThat(Detection.find(probe, KnownEditors.byId("vim").orElseThrow())).map(Detected::executable).contains(vim);
        assertThat(Detection.find(probe, KnownEditors.byId("code").orElseThrow())).map(Detected::executable).contains(code);
    }

    @Test void findIsEmptyForAnEditorThatIsNotInstalled() {
        program("b/bin/vim");
        var probe = probe(LINUX, List.of(path("b/bin")), Map.of());
        assertThat(Detection.find(probe, KnownEditors.byId("nvim").orElseThrow())).isEmpty();
        assertThat(Detection.find(probe, KnownEditors.byId("vim").orElseThrow())).isPresent();
        assertThat(Detection.find(probe(LINUX, List.of(), Map.of()), KnownEditors.byId("vim").orElseThrow())).isEmpty();
    }

    @Test void findKeepsTheOrderRuleAProgramOnPathWinsOverTheOneInsideItsApp() {
        Path onPath = program("opt/homebrew/bin/code");
        program("Applications/Visual Studio Code.app/Contents/Resources/app/bin/code");
        var probe = probe(MAC, List.of(), Map.of());
        assertThat(Detection.find(probe, KnownEditors.byId("code").orElseThrow())).map(Detected::executable).contains(onPath);
    }

    @Test void findLooksInWindowsFoldersAndNotOnOtherSystems() {
        Path notepad = program("Windows/System32/notepad.exe");
        var environment = Map.of("SYSTEMROOT", path("Windows").toString());
        var editor = KnownEditors.byId("notepad").orElseThrow();
        assertThat(Detection.find(probe(WINDOWS, List.of(), environment), editor)).map(Detected::executable).contains(notepad);
        assertThat(Detection.find(probe(LINUX, List.of(), environment), editor)).isEmpty();
    }
}
