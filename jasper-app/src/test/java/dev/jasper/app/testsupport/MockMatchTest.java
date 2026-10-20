package dev.jasper.app.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import org.junit.jupiter.api.Test;

/** The matcher reads the mocks as Plan 10a's measurements do, and finds a shifted copy as compare_mock.py does. */
class MockMatchTest {
    static final String CONNECTED = "01 Workstation/Workstation - Connected (plugins in use).png";
    static final MockMatch.Crop SESSIONS = new MockMatch.Crop(CONNECTED, 2136, 230, 700, 782);

    @Test void theMockAgreesWithItselfAndAShiftedCopyIsFound() {
        var mock = SESSIONS.read();
        assertThat(MockMatch.agreement(mock, mock)).isEqualTo(1.0);
        var shifted = new MockMatch.Crop(CONNECTED, 2140, 234, 700, 782).read();
        assertThat(MockMatch.agreement(mock, shifted)).as("a 4 px shift is inside the search").isGreaterThan(0.95);
    }

    @Test void theSessionsColumnReadsAsMeasured() {
        var measured = MockMatch.parse("0+36 #F7F7F8, 36+1 #DDE0E4, 37+20 #F0F1F3, 57+1 #D3D6DA, 58+44 #FFFFFF, 102+22 #2F65CA, "
            + "124+111 #FFFFFF, 235+1 #C4C7CC, 236+22 #F7F7F8, 258+1 #DDE0E4, 259+22 #F7F7F8, 281+1 #E6E8EB, 282+22 #F7F7F8, "
            + "304+1 #E6E8EB, 305+22 #F7F7F8, 327+1 #E6E8EB, 328+22 #F7F7F8, 350+1 #E6E8EB, 351+40 #F7F7F8");
        assertThat(MockMatch.mismatch(measured, MockMatch.bands(SESSIONS.read(), 4), 0)).isEmpty();
        assertThat(MockMatch.mismatch(measured, MockMatch.bands(SESSIONS.read(), 4).subList(1, 19), 0)).isNotEmpty();
    }

    @Test void aGradientIsOneBandAndColoursAreFound() {
        var combo = new MockMatch.Crop(CONNECTED, 660, 110, 720, 68).read();
        assertThat(MockMatch.mismatch(MockMatch.parse("0+6 #ECEDEF, 6+1 #B4B8BF, 7+20 #FFFFFF~#F1F2F4, 27+1 #B4B8BF, 28+6 #ECEDEF"),
            MockMatch.bands(combo, 130), 0)).isEmpty();
        assertThat(MockMatch.present(combo, new Color(0x2F65CA))).isTrue();
        assertThat(MockMatch.present(combo, new Color(0xD03A3A))).isFalse();
        assertThat(MockMatch.deltaE(Color.WHITE, Color.WHITE)).isZero();
        assertThat(MockMatch.deltaE(Color.WHITE, Color.BLACK)).isGreaterThan(99);
    }

    @Test void bandsMayNameThemeRoles() {
        var theme = dev.jasper.ui.testsupport.KitFixtures.enterpriseLight();
        var bands = MockMatch.parse("0+6 surface.app, 6+1 border.control, 7+20 button.secondary.bg~button.end, 27+1 #B4B8BF", theme);
        assertThat(bands).hasSize(4);
        assertThat(bands.get(0).first()).isEqualTo(dev.jasper.ui.testsupport.KitFixtures.colour(theme, "surface.app"));
        assertThat(bands.get(2).last()).isEqualTo(dev.jasper.ui.testsupport.KitFixtures.colour(theme, "button.end"));
        assertThat(bands.get(3).first()).isEqualTo(new java.awt.Color(0xB4B8BF));
    }
}
