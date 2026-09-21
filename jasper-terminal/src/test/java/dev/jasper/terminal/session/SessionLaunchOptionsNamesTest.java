package dev.jasper.terminal.session;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SessionLaunchOptionsNamesTest {
    private static SessionLaunchOptions.Builder builder() {
        return SessionLaunchOptions.builder().command(List.of("/bin/sh")).environment(Map.of()).workingDirectory(Path.of("/"));
    }

    @Test void localHostNamesDefaultToNoneAreCopiedAndSurviveToBuilder() {
        assertThat(builder().build().localHostNames()).isEmpty();
        Set<String> names = new HashSet<>(Set.of("workstation"));
        SessionLaunchOptions options = builder().localHostNames(names).build();
        names.add("later");
        assertThat(options.localHostNames()).containsExactly("workstation");
        assertThat(options.toBuilder().build()).isEqualTo(options);
        assertThatIllegalArgumentException().isThrownBy(() -> builder().localHostNames(Set.of(" ")).build());
    }
}
