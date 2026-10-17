package dev.jasper.app.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/** The comparison helpers the dock tests rely on. */
class MockCropsTest {
    @Test void runsJoinCloseInkAndSplitAtAGap() {
        var image = new BufferedImage(40, 4, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 40; x++) for (int y = 0; y < 4; y++) image.setRGB(x, y, 0xFFFFFF);
        for (int x : new int[] {3, 4, 6, 20, 21}) image.setRGB(x, 1, 0x1E1F22);
        var runs = MockCrops.runs(image, false, 60, 4, Color.WHITE);
        assertThat(runs).extracting(MockCrops.Run::start, MockCrops.Run::end)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(3, 6), org.assertj.core.groups.Tuple.tuple(20, 21));
        assertThat(runs.getFirst().ink()).isEqualTo(new Color(0x1E1F22));
    }

    @Test void theScrimDarkensWhiteToTheVaultMocksGrey() {
        var white = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        white.setRGB(0, 0, 0xFFFFFF);
        assertThat(MockCrops.at(MockCrops.scrim(white, MockCrops.VAULT_SCRIM), 0, 0)).isEqualTo(new Color(0xC9, 0xC9, 0xC9));
    }

    @Test void theMocksAreTheirManifestSize() {
        assertThat(MockCrops.mock(MockCrops.CONNECTED).getWidth()).isEqualTo(2884);
        assertThat(MockCrops.mock(MockCrops.DARK).getHeight()).isEqualTo(1804);
        assertThat(MockCrops.mock(MockCrops.VAULT).getWidth()).isEqualTo(3012);
    }
}
