package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class VmOptionsLaunchTest {
    private static Properties properties(String... pairs) {
        var properties = new Properties();
        for (int i = 0; i < pairs.length; i += 2) properties.setProperty(pairs[i], pairs[i + 1]);
        return properties;
    }

    private static VmOptionsSyntax.Parsed parsed(String text) { return VmOptionsSyntax.parse(text.getBytes(StandardCharsets.UTF_8)); }

    @Test void theLaunchersPropertiesAreRead() {
        var launch = VmOptionsLaunch.of(properties("jasper.vmOptionsFile", "/h/jasper.vmoptions", "jasper.vmOptionsDigest", "c758526d21c369d0",
            "jasper.vmOptionsSkipped", "launch-failed"));
        assertThat(launch.file()).contains(Path.of("/h/jasper.vmoptions"));
        assertThat(launch.digest()).contains("c758526d21c369d0");
        assertThat(launch.skipped()).contains("launch-failed");
        assertThat(launch.corrupted()).isFalse();
        assertThat(launch.launcherManaged()).isTrue();
        assertThat(VmOptionsLaunch.of(properties("jasper.vmOptionsFile.corrupted", "true")).corrupted()).isTrue();
        var development = VmOptionsLaunch.of(properties());
        assertThat(development.launcherManaged()).isFalse();
        assertThat(development.file()).isEmpty();
    }

    @Test void aRestartIsNeededWhenTheAppliedLinesDiffer() {
        var applied = new VmOptionsLaunch(Optional.of(Path.of("/h/jasper.vmoptions")), Optional.of(VmOptionsSyntax.digest(List.of("-Xmx2048m"))),
            false, Optional.empty());
        assertThat(applied.restartNeeded(parsed("# heap\n-Xmx2048m\n"), VmOptionsSyntax.Parsed.EMPTY)).as("comments do not count").isFalse();
        assertThat(applied.restartNeeded(parsed("-Xmx4g\n"), VmOptionsSyntax.Parsed.EMPTY)).isTrue();
        var none = new VmOptionsLaunch(Optional.of(Path.of("/h/jasper.vmoptions")), Optional.empty(), false, Optional.empty());
        assertThat(none.restartNeeded(VmOptionsSyntax.Parsed.EMPTY, VmOptionsSyntax.Parsed.EMPTY)).isFalse();
        assertThat(none.restartNeeded(parsed("Xmx300m\n"), VmOptionsSyntax.Parsed.EMPTY)).as("ignored lines apply nothing").isFalse();
        assertThat(none.restartNeeded(parsed("-Xmx4g\n"), VmOptionsSyntax.Parsed.EMPTY)).isTrue();
    }

    @Test void aSkippedOrCorruptedLaunchComparesWithTheFileAtStart() {
        var skipped = new VmOptionsLaunch(Optional.of(Path.of("/h/jasper.vmoptions")), Optional.empty(), false, Optional.of("launch-failed"));
        assertThat(skipped.restartNeeded(parsed("-Xmx3q\n"), parsed("-Xmx3q\n"))).as("restarting would fail the same way").isFalse();
        assertThat(skipped.restartNeeded(parsed("-Xmx3g\n"), parsed("-Xmx3q\n"))).isTrue();
        var corrupted = new VmOptionsLaunch(Optional.of(Path.of("/h/jasper.vmoptions")), Optional.empty(), true, Optional.empty());
        assertThat(corrupted.restartNeeded(parsed("-Xmx3g\n"), VmOptionsSyntax.parse(new byte[] {'-', 0}))).isTrue();
    }

    @Test void theRunningHeapIsTheLastXmxElseTheRuntimesMaximum() {
        assertThat(VmOptionsLaunch.runningMaxHeapMiB(List.of("-Xmx2048m", "-Dx=1", "-Xmx4096m"), 1L << 30)).isEqualTo(4096);
        assertThat(VmOptionsLaunch.runningMaxHeapMiB(List.of("-Dx=1"), 3L << 30)).isEqualTo(3072);
        assertThat(VmOptionsLaunch.runningMaxHeapMiB()).isPositive();
    }

    @Test void physicalMemoryIsKnownOnSupportedJvms() {
        assertThat(VmOptionsLaunch.physicalMemoryMiB().orElseThrow()).isPositive();
    }
}
