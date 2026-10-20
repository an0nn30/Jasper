package dev.jasper.ui.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Plan 4c: every Enterprise fixture has its classic-* pair, whose crop is the fixture's size at 2x inside its mock. */
class EnterpriseGalleryTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
    static final Pattern PAIR = Pattern.compile("\"name\": \"(classic-[^\"]+)\",\\s*\"mock\": \"([^\"]+)\",\\s*\"crop\": \\[\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),"
        + "\\s*(\\d+)\\s*\\],\\s*\"render\": \"([^\"]+)\",\\s*\"min_agreement\": ([0-9.]+),\\s*\"colours\": \\[([^\\]]*)\\]");

    record Pair(String mock, int x, int y, int width, int height, String render, double floor, String colours) {}

    static LinkedHashMap<String, Pair> pairs() throws Exception {
        var pairs = new LinkedHashMap<String, Pair>();
        var matcher = PAIR.matcher(Files.readString(ROOT.resolve("tools/visual/pairs.json")));
        while (matcher.find())
            pairs.put(matcher.group(1), new Pair(matcher.group(2), Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)),
                Integer.parseInt(matcher.group(5)), Integer.parseInt(matcher.group(6)), matcher.group(7), Double.parseDouble(matcher.group(8)), matcher.group(9)));
        return pairs;
    }

    @Test void everyEnterpriseFixtureHasItsPairAtTwiceItsSize() throws Exception {
        var pairs = pairs();
        var fixtures = EnterpriseFixtures.create();
        assertThat(pairs.keySet()).containsExactlyElementsOf(fixtures.stream().map(KitGallery.Fixture::name).toList());
        for (var fixture : fixtures) {
            var pair = pairs.get(fixture.name());
            assertThat(pair.mock()).isEqualTo("docs/design/enterprise/reference/" + fixture.reference());
            assertThat(pair.render()).isEqualTo("jasper-ui/build/gallery/enterprise-light-2x/" + fixture.name() + ".png");
            assertThat(pair.width()).as(fixture.name()).isEqualTo(2 * fixture.width());
            assertThat(pair.height()).as(fixture.name()).isEqualTo(2 * fixture.height());
            assertThat(pair.floor()).as(fixture.name()).isBetween(0.0, 1.0);
            assertThat(pair.colours()).as(fixture.name()).contains("#");
            var mock = ImageIO.read(ROOT.resolve(pair.mock()).toFile());
            assertThat(pair.x() + pair.width()).as(fixture.name() + " inside its mock").isLessThanOrEqualTo(mock.getWidth());
            assertThat(pair.y() + pair.height()).as(fixture.name() + " inside its mock").isLessThanOrEqualTo(mock.getHeight());
        }
    }
}
