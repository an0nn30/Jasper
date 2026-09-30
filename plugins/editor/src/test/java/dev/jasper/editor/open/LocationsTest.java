package dev.jasper.editor.open;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.LocationResolver;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileLocation;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LocationsTest {
    static LocationResolver resolver(String sourceId, String prefix) {
        return new LocationResolver() {
            @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) { throw new UnsupportedOperationException(); }
            @Override public Optional<FileLocation.Sourced> location(String key) {
                return key.startsWith(prefix) ? Optional.of(new FileLocation.Sourced(sourceId, "box", key.substring(prefix.length()))) : Optional.empty();
            }
        };
    }

    @Test void resolversAreKeptBySourceAndRemoveOnlyThemselves() {
        var locations = new Locations();
        LocationResolver first = resolver("dev.x.hosts", "x:");
        Subscription registration = locations.register("dev.x.hosts", first);
        assertThat(locations.sources()).containsExactly("dev.x.hosts");
        assertThat(locations.resolver("dev.x.hosts")).containsSame(first);
        LocationResolver second = resolver("dev.x.hosts", "x:");
        locations.register("dev.x.hosts", second);
        registration.close();
        assertThat(locations.resolver("dev.x.hosts")).as("a later registration replaced it").containsSame(second);
        assertThatIllegalArgumentException().isThrownBy(() -> locations.register(" ", second));
    }

    @Test void aDocumentsLocationComesFromTheResolverThatKnowsItsKey() {
        var locations = new Locations();
        locations.register("dev.x.hosts", resolver("dev.x.hosts", "x:"));
        locations.register("dev.y.hosts", resolver("dev.other", "y:"));
        locations.register("dev.z.hosts", new LocationResolver() {
            @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) { throw new UnsupportedOperationException(); }
            @Override public Optional<FileLocation.Sourced> location(String key) { throw new IllegalStateException("boom"); }
        });
        assertThat(locations.location("x:/srv/a.conf")).contains(new FileLocation.Sourced("dev.x.hosts", "box", "/srv/a.conf"));
        assertThat(locations.location("y:/etc/b")).as("a resolver answering for another source is ignored").isEmpty();
        assertThat(locations.location("local:/tmp/c")).isEmpty();
    }
}
