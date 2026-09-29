package dev.jasper.app.launch;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class WslDistributionsTest {
    private static final String UBUNTU_ONLY = """

        HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss
            DefaultVersion    REG_DWORD    0x1
            DefaultDistribution    REG_SZ    {c1fdd1b7-4743-4101-b846-d96c30089e95}
            OOBEComplete    REG_DWORD    0x1

        HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss\\{c1fdd1b7-4743-4101-b846-d96c30089e95}
            State    REG_DWORD    0x1
            DistributionName    REG_SZ    Ubuntu
            Version    REG_DWORD    0x2
            BasePath    REG_SZ    C:\\Users\\dustin\\AppData\\Local\\Packages\\CanonicalGroupLimited.Ubuntu_79rhkp1fndgsc\\LocalState
            Flags    REG_DWORD    0x7
            DefaultUid    REG_DWORD    0x3e8
            RunOOBE    REG_DWORD    0x0
            PackageFamilyName    REG_SZ    CanonicalGroupLimited.Ubuntu_79rhkp1fndgsc
            VhdFileName    REG_SZ    ext4.vhdx
            Flavor    REG_SZ    ubuntu
            OsVersion    REG_SZ    26.04

        """.replace("\n", "\r\n");

    private static final String MORE_DISTROS = UBUNTU_ONLY + """
        HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss\\{5d3a2f5e-8c1b-4f0e-9a51-2b7c0d9e4f11}
            State    REG_DWORD    0x1
            DistributionName    REG_SZ    Debian
            Version    REG_DWORD    0x2
            Flags    REG_DWORD    0xf

        HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss\\{6e4b3a6f-9d2c-4a1f-8b62-3c8d1eaf5a22}
            State    REG_DWORD    0x1
            DistributionName    REG_SZ    docker-desktop
            Flags    REG_DWORD    0xf

        HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss\\{7f5c4b70-ae3d-4b20-9c73-4d9e2fb06b33}
            State    REG_DWORD    0x1
            DistributionName    REG_SZ    docker-desktop-data
            Flags    REG_DWORD    0xf

        HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss\\{80d65c81-bf4e-4c31-8d84-5eaf30c17c44}
            State    REG_DWORD    0x3
            DistributionName    REG_SZ    Installing
            Flags    REG_DWORD    0xf

        """.replace("\n", "\r\n");

    private static final String LIST = "  NAME              STATE           VERSION\r\n"
        + "* Ubuntu            Stopped         1\r\n"
        + "  Debian            Running         2\r\n"
        + "  docker-desktop    Stopped         2\r\n";

    @Test void theRegistryNamesEachDistributionItsWslVersionAndTheDefault() {
        assertThat(WslDistributions.parseRegistry(UBUNTU_ONLY)).as("Version is the filesystem version; Flags 0x7 is WSL 1")
            .containsExactly(new WslDistro("Ubuntu", 1, true));
        assertThat(WslDistributions.parseRegistry(MORE_DISTROS)).as("Docker's and half-installed distributions are hidden")
            .containsExactly(new WslDistro("Debian", 2, false), new WslDistro("Ubuntu", 1, true));
        assertThat(WslDistributions.parseRegistry(UBUNTU_ONLY.replace("{c1fdd1b7-4743-4101-b846-d96c30089e95}\r\n    O",
            "{C1FDD1B7-4743-4101-B846-D96C30089E95}\r\n    O"))).as("GUIDs compare without regard to case")
            .containsExactly(new WslDistro("Ubuntu", 1, true));
        assertThat(WslDistributions.parseRegistry("")).isEmpty();
        assertThat(WslDistributions.parseRegistry("ERROR: The system was unable to find the specified registry key or value.\r\n")).isEmpty();
    }

    @Test void wslExeListOutputIsReadAsUtf16OrUtf8() {
        var expected = List.of(new WslDistro("Debian", 2, false), new WslDistro("Ubuntu", 1, true));
        assertThat(WslDistributions.parseList(LIST.getBytes(UTF_16LE))).containsExactlyElementsOf(expected);
        assertThat(WslDistributions.parseList(("\uFEFF" + LIST).getBytes(UTF_16LE))).as("with a byte order mark").containsExactlyElementsOf(expected);
        assertThat(WslDistributions.parseList(LIST.getBytes(UTF_8))).as("WSL_UTF8=1").containsExactlyElementsOf(expected);
        assertThat(WslDistributions.parseList("Windows Subsystem for Linux has no installed distributions.\r\n".getBytes(UTF_16LE))).isEmpty();
        assertThat(WslDistributions.parseList(new byte[0])).isEmpty();
    }

    @Test void detectionReadsTheRegistryFirstAndFallsBackToWslExe() {
        var asked = new ArrayList<List<String>>();
        Function<List<String>, Optional<byte[]>> registryOnly = command -> {
            asked.add(command);
            return command.getFirst().equals("reg.exe") ? Optional.of(UBUNTU_ONLY.getBytes(ISO_8859_1)) : Optional.empty();
        };
        assertThat(WslDistributions.detect("Windows 11", registryOnly)).containsExactly(new WslDistro("Ubuntu", 1, true));
        assertThat(asked).containsExactly(List.of("reg.exe", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss", "/s"));
        asked.clear();
        Function<List<String>, Optional<byte[]>> listOnly = command -> {
            asked.add(command);
            return command.getFirst().equals("wsl.exe") ? Optional.of(LIST.getBytes(UTF_16LE)) : Optional.empty();
        };
        assertThat(WslDistributions.detect("Windows 11", listOnly)).containsExactly(new WslDistro("Debian", 2, false), new WslDistro("Ubuntu", 1, true));
        assertThat(asked).containsExactly(WslDistributions.REGISTRY_QUERY, List.of("wsl.exe", "-l", "-v"));
        assertThat(WslDistributions.detect("Windows 11", command -> Optional.empty())).as("no WSL, or wsl.exe missing").isEmpty();
        asked.clear();
        String dockerOnly = MORE_DISTROS.substring(MORE_DISTROS.indexOf("HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss\\{6e4b"));
        for (String registry : List.of("", dockerOnly)) {
            Function<List<String>, Optional<byte[]>> readable = command -> {
                asked.add(command);
                return command.getFirst().equals("reg.exe") ? Optional.of(registry.getBytes(ISO_8859_1)) : Optional.of(LIST.getBytes(UTF_16LE));
            };
            assertThat(WslDistributions.detect("Windows 11", readable)).as("the registry answered: %s", registry.isEmpty() ? "no Lxss key" : "only Docker").isEmpty();
            assertThat(asked).as("so wsl.exe is not asked").containsExactly(WslDistributions.REGISTRY_QUERY);
            asked.clear();
        }
        assertThat(WslDistributions.detect("Windows 11", command -> { throw new IllegalStateException("broken runner"); })).isEmpty();
        assertThat(WslDistributions.detect("Mac OS X", command -> { throw new AssertionError("never runs a process off Windows"); })).isEmpty();
        assertThat(WslDistributions.detect("Linux")).isEmpty();
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void theRunnerReturnsOutputOnlyForATimelySuccessfulExit() {
        assertThat(WslDistributions.run(List.of("/bin/sh", "-c", "printf abc"), Duration.ofSeconds(5)))
            .hasValueSatisfying(bytes -> assertThat(new String(bytes, UTF_8)).isEqualTo("abc"));
        assertThat(WslDistributions.run(List.of("/bin/sh", "-c", "printf abc; exit 3"), Duration.ofSeconds(5))).isEmpty();
        assertThat(WslDistributions.run(List.of("/bin/sh", "-c", "exec sleep 10"), Duration.ofMillis(200))).isEmpty();
    }

    @Test void theRunnerAcceptsOnlyTheGivenExitCodes() {
        String launcher = java.nio.file.Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        // With no arguments the launcher prints its usage to standard error and exits with 1, as reg.exe does for a missing key.
        assertThat(WslDistributions.run(List.of(launcher), Duration.ofSeconds(30), Set.of(0))).isEmpty();
        assertThat(WslDistributions.run(List.of(launcher), Duration.ofSeconds(30), Set.of(0, 1))).isPresent();
        assertThat(WslDistributions.successes(WslDistributions.REGISTRY_QUERY)).as("reg.exe exits 1 when the Lxss key is missing: no WSL")
            .containsExactlyInAnyOrder(0, 1);
        assertThat(WslDistributions.successes(WslDistributions.LIST_QUERY)).containsExactly(0);
    }

    @Test void aMissingProgramIsNoOutput() {
        assertThat(WslDistributions.run(List.of("jasper-no-such-program-" + UUID.randomUUID()), Duration.ofSeconds(5))).isEmpty();
    }

    @Test void aDistroHasANameAndAWslVersion() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WslDistro(" ", 1, false));
        assertThatIllegalArgumentException().isThrownBy(() -> new WslDistro("Ubuntu", 3, false));
    }
}
