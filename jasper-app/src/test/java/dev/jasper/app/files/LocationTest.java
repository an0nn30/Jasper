package dev.jasper.app.files;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LocationTest {
    @Test void localAndSourcedLocationsStoreAndParse() {
        Location local = Location.local(Path.of("/work/../work/x"));
        assertThat(local.isLocal()).isTrue();
        assertThat(local.localPath()).isEqualTo(Path.of("/work/x").toAbsolutePath());
        assertThat(Location.parse(local.stored())).contains(local);
        Location remote = new Location("dev.x.hosts", "box", "/srv/a|b");
        assertThat(remote.stored()).isEqualTo("@dev.x.hosts|box|/srv/a|b");
        assertThat(Location.parse(remote.stored())).contains(remote);
        assertThat(Location.parse("relative")).isEmpty();
        assertThat(Location.parse("@broken")).isEmpty();
        assertThat(Location.home("dev.x.hosts", "box").path()).isEqualTo(Location.HOME);
        assertThat(remote.at("/etc")).isEqualTo(new Location("dev.x.hosts", "box", "/etc"));
        assertThatIllegalStateException().isThrownBy(remote::localPath);
        assertThat(Optional.of(remote).filter(Location::isLocal)).isEmpty();
    }
}
