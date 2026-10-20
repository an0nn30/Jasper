package dev.jasper.indexer.index;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectIndexTest {
    @Test void precomputesNamesAndLowerCaseKeys() {
        var index = new ProjectIndex(ProjectKey.local(Path.of("/w/app")), List.of("src/FilesScope.java", "README.md"),
            Strategy.GIT, Instant.EPOCH, false);
        assertThat(index.size()).isEqualTo(2);
        assertThat(index.file(0)).isEqualTo("src/FilesScope.java");
        assertThat(index.name(0)).isEqualTo("FilesScope.java");
        assertThat(index.lowerName(0)).isEqualTo("filesscope.java");
        assertThat(index.lowerPath(0)).isEqualTo("src/filesscope.java");
        assertThat(index.name(1)).isEqualTo("README.md");
        assertThat(index.files()).containsExactly("src/FilesScope.java", "README.md");
    }
}
