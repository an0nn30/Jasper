package dev.jasper.app.themestore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionsTest {
    @Test void numericPartsCompareAsNumbersAndAQualifierIsOlder() {
        assertThat(Versions.newer("2.3.1", "2.3.0")).isTrue();
        assertThat(Versions.newer("2.10", "2.9")).isTrue();
        assertThat(Versions.newer("2.3.0", "2.3.1")).isFalse();
        assertThat(Versions.newer("2.3", "2.3.0")).isFalse();
        assertThat(Versions.newer("2.3.1", "2.3.1")).isFalse();
        assertThat(Versions.newer("1.0.0", "1.0.0-beta")).isTrue();
        assertThat(Versions.newer("1.0.0-beta", "1.0.0")).isFalse();
        assertThat(Versions.newer("2024.2", "2024.1.3")).isTrue();
        assertThat(Versions.newer("1.0-rc2", "1.0-rc1")).isTrue();
    }
}
