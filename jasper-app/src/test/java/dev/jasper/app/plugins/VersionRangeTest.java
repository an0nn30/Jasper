package dev.jasper.app.plugins;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VersionRangeTest {
    @Test void versionsParseOneToThreeComponentsAndCompareNumerically() {
        assertThat(Version.parse("0.1")).isEqualTo(new Version(0, 1, 0));
        assertThat(Version.parse("2")).isEqualTo(new Version(2, 0, 0));
        assertThat(Version.parse("1.10.0")).isGreaterThan(Version.parse("1.9.9"));
        assertThat(Version.parse("1.2.3").toString()).isEqualTo("1.2.3");
        for (String bad : new String[]{"", "1.", "1.2.3.4", "01.2", "1.x", "-1", "1.0.0-beta"})
            assertThatIllegalArgumentException().as(bad).isThrownBy(() -> Version.parse(bad));
    }

    @Test void rangesCombineComparators() {
        VersionRange range = VersionRange.parse(">=0.1, <0.2");
        assertThat(range.contains(Version.parse("0.1.0"))).isTrue();
        assertThat(range.contains(Version.parse("0.1.9"))).isTrue();
        assertThat(range.contains(Version.parse("0.2.0"))).isFalse();
        assertThat(range.contains(Version.parse("0.0.9"))).isFalse();
        assertThat(VersionRange.parse("=1.2.3").contains(Version.parse("1.2.3"))).isTrue();
        assertThat(VersionRange.parse(">1, <=2").contains(Version.parse("2.0.0"))).isTrue();
        assertThat(VersionRange.parse(">1, <=2").contains(Version.parse("1.0.0"))).isFalse();
        assertThat(VersionRange.parse("  ").contains(Version.parse("9.9.9"))).isTrue();
        assertThat(VersionRange.ANY.toString()).isEqualTo("any");
        assertThat(range.toString()).isEqualTo(">=0.1.0, <0.2.0");
        for (String bad : new String[]{"0.1", "~1.0", ">=", ">=1,,<2", "=>1"})
            assertThatIllegalArgumentException().as(bad).isThrownBy(() -> VersionRange.parse(bad));
    }

    @Test void pluginsBuiltForSdkZeroSevenAreIncompatibleWithZeroEight() {
        var host = Version.parse(dev.jasper.sdk.JasperSdk.VERSION);
        assertThat(VersionRange.parse(">=0.7.4, <0.8").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.8.0, <0.9").contains(host)).isTrue();
    }
}
