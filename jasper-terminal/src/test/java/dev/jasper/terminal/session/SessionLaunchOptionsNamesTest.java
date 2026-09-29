package dev.jasper.terminal.session;

import dev.jasper.terminal.config.GridSize;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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

    @Test void theLocalPathMapperDefaultsToTodaysMappingAndSurvivesToBuilder() {
        assertThat(builder().build().localPathMapper()).isSameAs(SessionLaunchOptions.DEFAULT_LOCAL_PATHS);
        Function<String, Optional<Path>> mapper = path -> Optional.of(Path.of("/mapped"));
        SessionLaunchOptions options = builder().localPathMapper(mapper).build();
        assertThat(options.localPathMapper()).isSameAs(mapper);
        assertThat(options.toBuilder().build()).isEqualTo(options);
        assertThatNullPointerException().isThrownBy(() -> builder().localPathMapper(null).build());
        assertThat(new SessionLaunchOptions(List.of("/bin/sh"), Map.of(), Path.of("/"), new GridSize(80, 24), 0, Set.of())
            .localPathMapper()).isSameAs(SessionLaunchOptions.DEFAULT_LOCAL_PATHS);
    }
}
