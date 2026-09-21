package dev.jasper.app.launch;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.assertj.core.api.Assertions.assertThat;

class LocalHostNamesTest {
    @Test void namesComeFromTheHostnameProgramAndTheEnvironmentTrimmedAndWithoutBlanks() {
        assertThat(LocalHostNames.resolve(() -> Optional.of(" Workstation.home.example\n"),
            Map.of("HOSTNAME", "workstation", "COMPUTERNAME", " ", "HOME", "/Users/me")))
            .containsExactlyInAnyOrder("Workstation.home.example", "workstation");
        assertThat(LocalHostNames.resolve(Optional::empty, Map.of())).as("unknown: only hostless reports will be local").isEmpty();
        assertThat(LocalHostNames.resolve(() -> { throw new IllegalStateException("no such program"); }, Map.of("HOSTNAME", "box")))
            .containsExactly("box");
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test void thisMachineHasAName() {
        assertThat(LocalHostNames.runHostname()).hasValueSatisfying(name -> assertThat(name).isNotBlank());
        assertThat(LocalHostNames.cached()).isNotEmpty().isSameAs(LocalHostNames.cached());
    }
}
