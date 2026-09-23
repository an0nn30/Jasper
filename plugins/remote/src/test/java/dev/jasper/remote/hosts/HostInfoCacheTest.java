package dev.jasper.remote.hosts;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
class HostInfoCacheTest {
    @Test void cachedFactsSurviveRestartButNotEndpointChanges(@TempDir Path dir) throws Exception {
        var host = RemoteHost.create("one", "host", 22, "me", Auth.AGENT, "", Optional.empty());
        var path = dir.resolve("host-info.properties");
        var cache = new HostInfoCache(path); cache.load();
        cache.put(host, new HostInfo("Ubuntu", "10.0.0.2"));
        var reloaded = new HostInfoCache(path); reloaded.load();
        assertThat(reloaded.get(host).os()).isEqualTo("Ubuntu");
        var changed = host.withEdited("one", "other", 22, "me", Auth.AGENT, "", Optional.empty());
        assertThat(reloaded.get(changed).os()).isEmpty();
    }
}
