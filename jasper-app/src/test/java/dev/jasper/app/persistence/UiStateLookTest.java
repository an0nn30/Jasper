package dev.jasper.app.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.jasper.app.dock.Slot;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 10.7 and Rulings 17: each look keeps its own dock in ui-state; the default look's keys do not change. */
class UiStateLookTest {
    static final String ENTERPRISE = "dev.jasper.enterprise";
    @TempDir Path dir;

    /** Review Focus 2. */
    @Test void eachLookKeepsItsOwnLayoutAndTheOthersSurviveASave() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        var standard = UiState.load(file);
        standard.putPanel("dev.x.hosts", new UiState.Panel(Slot.LEFT_TOP, 0, true));
        standard.setDock(new UiState.Dock(300, 280, 180, 0.25, 0.5, 0.75));
        standard.save();
        String before = Files.readString(file);

        var enterprise = UiState.load(file, ENTERPRISE);
        assertThat(enterprise.look()).isEqualTo(ENTERPRISE);
        assertThat(enterprise.panel("dev.x.hosts")).as("a first start under the look: the registered defaults").isEmpty();
        assertThat(enterprise.dock()).isEqualTo(UiState.Dock.DEFAULT);
        enterprise.putPanel("dev.x.hosts", new UiState.Panel(Slot.RIGHT_TOP, 0, true));
        enterprise.setDock(new UiState.Dock(260, 350, 200, 0.5, 0.55, 0.5));
        enterprise.setRailVisible(false);
        enterprise.save();
        assertThat(Files.readString(file)).contains("""

            [look."dev.jasper.enterprise".dock]
            left_width = 260
            right_width = 350
            bottom_height = 200
            left_ratio = 0.5
            right_ratio = 0.55
            bottom_ratio = 0.5

            [look."dev.jasper.enterprise".panels."dev.x.hosts"]
            slot = "RIGHT_TOP"
            order = 0
            visible = true
            """);

        var back = UiState.load(file);
        assertThat(back.panel("dev.x.hosts")).hasValue(new UiState.Panel(Slot.LEFT_TOP, 0, true));
        assertThat(back.dock()).isEqualTo(new UiState.Dock(300, 280, 180, 0.25, 0.5, 0.75));
        assertThat(back.railVisible()).as("rail visibility is global").isFalse();
        back.setRailVisible(true);
        back.save();
        assertThat(Files.readString(file)).as("the default look's save keeps the other look's tables").contains("[look.\"dev.jasper.enterprise\".dock]");
        assertThat(Files.readString(file)).as("the default look's tables as it wrote them").contains(before.substring(before.indexOf("[dock]")));
        assertThat(UiState.load(file, ENTERPRISE).panel("dev.x.hosts")).hasValue(new UiState.Panel(Slot.RIGHT_TOP, 0, true));
    }

    @Test void aBadLookEntryFallsBackAlone() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 2

            [dock]
            left_width = 300

            [look."dev.jasper.enterprise".dock]
            left_width = 99999
            right_width = 400

            [look."dev.jasper.enterprise".panels."dev.x.bad"]
            slot = "NOWHERE"

            [look."dev.jasper.enterprise".panels."dev.x.good"]
            slot = "BOTTOM_LEFT"
            order = 2
            visible = false

            [look."Not An Id".panels."dev.x.good"]
            slot = "RIGHT_TOP"
            """);
        var enterprise = UiState.load(file, ENTERPRISE);
        assertThat(enterprise.dock().leftWidth()).as("an out-of-range width falls back alone").isEqualTo(UiState.Dock.DEFAULT.leftWidth());
        assertThat(enterprise.dock().rightWidth()).isEqualTo(400);
        assertThat(enterprise.panel("dev.x.bad")).isEmpty();
        assertThat(enterprise.panel("dev.x.good")).hasValue(new UiState.Panel(Slot.BOTTOM_LEFT, 2, false));
        assertThat(UiState.load(file).dock().leftWidth()).isEqualTo(300);
        assertThat(UiState.load(file).panel("dev.x.good")).isEmpty();
    }

    @Test void aLookKeyIsAPluginIdOrEmpty() {
        assertThat(UiState.inMemory().look()).isEmpty();
        assertThat(UiState.inMemory(ENTERPRISE).look()).isEqualTo(ENTERPRISE);
        assertThatIllegalArgumentException().isThrownBy(() -> UiState.inMemory("Not An Id"));
    }
}
