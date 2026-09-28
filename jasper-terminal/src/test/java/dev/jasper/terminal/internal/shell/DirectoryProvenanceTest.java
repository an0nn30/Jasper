package dev.jasper.terminal.internal.shell;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DirectoryProvenanceTest {
    private final DirectoryProvenance local = new DirectoryProvenance(true, Set.of("Workstation.home.example", "workstation"));

    private static Optional<DirectoryProvenance.Report> local(Path directory) { return Optional.of(new DirectoryProvenance.Report.Local(directory)); }
    private static Optional<DirectoryProvenance.Report> remote(String host, String path) {
        return Optional.of(new DirectoryProvenance.Report.Remote(new RemoteLocation(host, path)));
    }

    @Test void aLocalSessionIsLocalOnlyForNoHostLocalhostOrAnExactLocalName() {
        assertThat(local.classify("file:///Users/me/My%20Dir")).isEqualTo(local(Path.of("/Users/me/My Dir")));
        assertThat(local.classify("file://localhost/tmp")).isEqualTo(local(Path.of("/tmp")));
        assertThat(local.classify("file://LOCALHOST/tmp")).isEqualTo(local(Path.of("/tmp")));
        assertThat(local.classify("file://workstation.home.example/tmp")).as("case-insensitive, exact").isEqualTo(local(Path.of("/tmp")));
        assertThat(local.classify("file://WORKSTATION/tmp")).isEqualTo(local(Path.of("/tmp")));
    }

    @Test void thereIsNoPartialOrFirstLabelMatching() {
        assertThat(local.classify("file://workstation.office.example/srv/app")).isEqualTo(remote("workstation.office.example", "/srv/app"));
        assertThat(local.classify("file://workstation.home/srv")).isEqualTo(remote("workstation.home", "/srv"));
        assertThat(local.classify("file://build-host/srv/My%20App")).as("the path is decoded, the host kept as reported")
            .isEqualTo(remote("build-host", "/srv/My App"));
        assertThat(local.classify("file://Build_Host/srv")).as("a name Java does not parse as a host is still a name").isEqualTo(remote("Build_Host", "/srv"));
    }

    @Test void withoutKnownNamesOnlyHostlessAndLocalhostReportsAreLocal() {
        var unresolved = new DirectoryProvenance(true, Set.of());
        assertThat(unresolved.classify("file:///tmp")).isEqualTo(local(Path.of("/tmp")));
        assertThat(unresolved.classify("file://localhost/tmp")).isEqualTo(local(Path.of("/tmp")));
        assertThat(unresolved.classify("file://workstation/tmp")).as("ambiguity resolves away from local").isEqualTo(remote("workstation", "/tmp"));
    }

    @Test void everyReportOfAnAttachedSessionIsRemote() {
        var attached = new DirectoryProvenance(false, Set.of("workstation"));
        assertThat(attached.classify("file://workstation/srv")).isEqualTo(remote("workstation", "/srv"));
        assertThat(attached.classify("file://localhost/srv")).isEqualTo(remote("localhost", "/srv"));
        assertThat(attached.classify("file:///srv")).as("the program named no host").isEqualTo(remote("", "/srv"));
    }

    @Test void whatIsNotAFileUriWithAPathIsIgnored() {
        assertThat(local.classify("https://example.com/x")).isEmpty();
        assertThat(local.classify("not a uri")).isEmpty();
        assertThat(local.classify("file://host")).isEmpty();
        assertThat(local.classify("")).isEmpty();
    }

    @Test void aMapperDecidesWhereALocalReportPointsAndEmptyIgnoresIt() {
        var mapped = new DirectoryProvenance(true, Set.of("workstation"),
            path -> path.startsWith("/mnt/") ? Optional.of(Path.of("mapped" + path.length())) : Optional.empty());
        assertThat(mapped.classify("file://workstation/mnt/c")).isEqualTo(local(Path.of("mapped6")));
        assertThat(mapped.classify("file:///home/x")).as("no mapping: the report is ignored").isEmpty();
        assertThat(mapped.classify("file://elsewhere/home/x")).as("remote reports never reach the mapper")
            .isEqualTo(remote("elsewhere", "/home/x"));
        assertThat(new DirectoryProvenance(false, Set.of(), path -> { throw new AssertionError("attached sessions are never mapped"); })
            .classify("file:///srv")).isEqualTo(remote("", "/srv"));
        assertThat(new DirectoryProvenance(true, Set.of(), path -> { throw new IllegalStateException("broken"); })
            .classify("file:///tmp")).as("a failing mapper counts as no mapping").isEmpty();
        assertThat(new DirectoryProvenance(true, Set.of(), path -> null).classify("file:///tmp")).isEmpty();
    }

    @Test void theDefaultMappingIsTheFileUriPath() throws Exception {
        assertThat(DirectoryProvenance.fileUriPath("/tmp/My Dir")).contains(Path.of(new URI("file:///tmp/My%20Dir")));
        assertThat(DirectoryProvenance.fileUriPath("relative")).isEmpty();
        assertThat(new DirectoryProvenance(true, Set.of()).classify("file:///tmp")).isEqualTo(local(Path.of(new URI("file:///tmp"))));
    }
}
