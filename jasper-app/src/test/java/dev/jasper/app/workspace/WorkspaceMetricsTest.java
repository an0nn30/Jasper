package dev.jasper.app.workspace;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.app.config.AnimationSettings;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class WorkspaceMetricsTest {
    @Test void approvedFolderAndDockGeometryIsModuleOwnedAndUnscaled() {
        var metrics = DesignMetrics.DEFAULT;
        assertThat(metrics.value(DesignMetrics.Role.TAB_MIN_WIDTH)).isEqualTo(160);
        assertThat(metrics.value(DesignMetrics.Role.TAB_MAX_WIDTH)).isEqualTo(240);
        assertThat(metrics.value(DesignMetrics.Role.TAB_ACCENT_WIDTH)).isEqualTo(64);
        assertThat(metrics.value(DesignMetrics.Role.TAB_ACCENT_HEIGHT)).isEqualTo(2);
        assertThat(metrics.value(DesignMetrics.Role.TAB_OPEN_MILLIS)).isEqualTo(80);
        assertThat(metrics.value(DesignMetrics.Role.TAB_CLOSE_MILLIS)).isEqualTo(100);
        assertThat(metrics.value(DesignMetrics.Role.TAB_SWITCH_MILLIS)).isEqualTo(130);
        assertThat(AnimationSettings.defaults()).isEqualTo(new AnimationSettings(true, 80, 100, 130));
        assertThat(metrics.value(DesignMetrics.Role.WORKSPACE_SURFACE_ARC)).isEqualTo(16);
    }
}
