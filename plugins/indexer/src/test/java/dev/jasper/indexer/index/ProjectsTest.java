package dev.jasper.indexer.index;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectsTest {
    static final ProjectKey APP = ProjectKey.local(Path.of("/w/app"));
    static final ProjectKey INNER = ProjectKey.local(Path.of("/w/app/vendor/lib"));

    static ProjectIndex index(ProjectKey key, boolean truncated) { return new ProjectIndex(key, List.of("a"), Strategy.WALK, Instant.EPOCH, truncated); }

    @Test void statesFollowSnapshotsAndOutcomes() {
        var projects = new Projects();
        projects.add(new Project(APP, "app", false));
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.NEEDS_REINDEX);
        projects.indexing(APP, true);
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.INDEXING);
        projects.indexing(APP, false);
        projects.indexed(APP, index(APP, false));
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.FRESH);
        projects.outcome(APP, Projects.Outcome.FAILED);
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.STALE);
        projects.indexed(APP, index(APP, true));
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.TRUNCATED);
        projects.outcome(APP, Projects.Outcome.MISSING);
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.MISSING);
        assertThat(projects.indexes()).as("a missing project leaves search").isEmpty();
    }

    @Test void theDeepestRootContainsAFolder() {
        var projects = new Projects();
        projects.add(new Project(APP, "app", false));
        projects.add(new Project(INNER, "lib", true));
        var host = UUID.randomUUID();
        projects.add(new Project(ProjectKey.remote(host, "/srv/app"), "app", false));
        assertThat(projects.containing(Path.of("/w/app/vendor/lib/src")).map(entry -> entry.project().key())).contains(INNER);
        assertThat(projects.containing(Path.of("/w/app/src")).map(entry -> entry.project().key())).contains(APP);
        assertThat(projects.containing(Path.of("/w/other"))).isEmpty();
        assertThat(projects.containing(host, "/srv/app/x").map(entry -> entry.project().name())).contains("app");
        assertThat(projects.containing(UUID.randomUUID(), "/srv/app/x")).isEmpty();
    }

    @Test void listenersHearEveryChangeUntilTheyLeave() {
        var projects = new Projects();
        var heard = new AtomicInteger();
        var subscription = projects.onChanged(heard::incrementAndGet);
        projects.add(new Project(APP, "app", false));
        projects.loaded(new Project(INNER, "lib", false), Optional.of(index(INNER, false)));
        projects.remove(APP);
        assertThat(heard).hasValue(3);
        subscription.close();
        projects.remove(INNER);
        assertThat(heard).hasValue(3);
        assertThat(projects.entries()).isEmpty();
    }
}
