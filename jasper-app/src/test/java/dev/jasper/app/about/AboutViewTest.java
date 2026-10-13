package dev.jasper.app.about;

import dev.jasper.app.platform.BuildInfo;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class AboutViewTest {
    private static final String COMMIT = "8ba67ed3" + "0".repeat(32);
    private final List<String> copied = new ArrayList<>();
    private final List<Duration> delays = new ArrayList<>();
    private final List<Runnable> pending = new ArrayList<>();
    private int licenses, closes, cancels;
    private boolean clipboardWorks = true;
    private final List<AboutView> opened = new ArrayList<>();

    @AfterEach void closeViews() { opened.forEach(AboutView::close); }

    private static BuildInfo build(String... pairs) {
        var facts = new HashMap<>(Map.of("commit", COMMIT, "branch", "main", "commitDate", "2026-10-13T14:02:11-04:00",
            "dirty", "false", "headTags", "", "describe", ""));
        for (int i = 0; i < pairs.length; i += 2) facts.put(pairs[i], pairs[i + 1]);
        return BuildInfo.of(facts);
    }

    private AboutView view(BuildInfo build) {
        var view = new AboutView(KitTestSupport.context(), new AboutView.Hooks(build, "TestOS 1 (arm)", "TestJava 25 (Vendor)",
            ZoneOffset.UTC, text -> clipboardWorks && copied.add(text), () -> licenses++, () -> closes++, (delay, task) -> {
                delays.add(delay);
                pending.add(task);
                return () -> cancels++;
            }));
        opened.add(view);
        view.setSize(340, 400);
        for (int i = 0; i < 3; i++) KitTestSupport.layout(view);
        return view;
    }

    private static <T extends Component> T first(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container container) for (Component child : container.getComponents()) {
            T found = first(child, type);
            if (found != null) return found;
        }
        return null;
    }

    private static AbstractButton copyButton(AboutView view) { return KitTestSupport.require(view, AboutView.COPY, AbstractButton.class); }

    @Test void aReleaseShowsItsVersionWithoutTheChip() {
        var view = view(build("headTags", "v1.2.0", "describe", "v1.2.0-0-g8ba67ed3"));
        assertThat(KitTestSupport.texts(view)).contains("Jasper", "1.2.0", "main @ 8ba67ed3", "Committed 13 Oct 2026",
            "PolyForm Noncommercial 1.0.0", "Copyright 2026 Dustin McKenna-Watts");
        assertThat(KitTestSupport.require(view, AboutView.LICENSES, AbstractButton.class).getText()).isEqualTo("Licenses…");
        assertThat(copyButton(view).getText()).isEqualTo("Copy");
        assertThat(KitTestSupport.find(view, AboutView.BADGE, Component.class)).isNull();
    }

    @Test void aDevelopmentBuildShowsTheChipItsVersionAndWhereItWasBuilt() {
        var view = view(build("describe", "v1.2.0-14-g8ba67ed3", "dirty", "true"));
        assertThat(KitTestSupport.texts(view)).contains("1.2.0 + 14 commits", "Development build", "main @ 8ba67ed3, modified");
        assertThat(KitTestSupport.find(view, AboutView.BADGE, Component.class)).isNotNull();
    }

    @Test void anUnknownBuildShowsNoVersionOrDateAndSaysSo() {
        var view = view(BuildInfo.UNKNOWN);
        assertThat(KitTestSupport.find(view, AboutView.VERSION, Component.class)).isNull();
        assertThat(KitTestSupport.find(view, AboutView.COMMITTED, Component.class)).isNull();
        assertThat(KitTestSupport.texts(view)).contains("Development build", "commit unknown");
    }

    @Test void theLocationIsReadOnlySelectableText() {
        var view = view(build());
        JTextArea location = first(view, JTextArea.class);
        assertThat(location.getText()).isEqualTo("main @ 8ba67ed3");
        assertThat(location.isEditable()).isFalse();
        location.selectAll();
        assertThat(location.getSelectedText()).isEqualTo("main @ 8ba67ed3");
    }

    /** The text UI adds plain Tab to a read-only area's traversal keys; an editable one would keep it for inserting a tab. */
    @Test void tabLeavesTheLocationText() {
        JTextArea location = first(view(build()), JTextArea.class);
        assertThat(location.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS))
            .contains(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
        assertThat(location.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS))
            .contains(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK));
    }

    @Test void theIconIsMountedAtSixtyFourPixels() {
        var view = view(build());
        var icon = first(view, AboutView.IconCanvas.class);
        assertThat(icon).isNotNull();
        assertThat(icon.getPreferredSize().width).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(64));
    }

    @Test void copyPutsTheBuildOnTheClipboardAndSaysCopiedForTwoSeconds() {
        var view = view(build("describe", "v1.2.0-14-g8ba67ed3", "dirty", "true"));
        copyButton(view).doClick();
        assertThat(copied).containsExactly("""
            Jasper 1.2.0 + 14 commits, development build (main @ 8ba67ed3, modified)
            Commit %s on main, modified; committed 2026-10-13
            OS: TestOS 1 (arm)
            Java: TestJava 25 (Vendor)
            """.formatted(COMMIT));
        assertThat(copyButton(view).getText()).isEqualTo("Copied");
        assertThat(delays).containsExactly(Duration.ofSeconds(2));
        pending.getFirst().run();
        assertThat(copyButton(view).getText()).isEqualTo("Copy");
    }

    @Test void aReleaseCopiesItsVersionAlone() {
        var view = view(build("headTags", "v1.2.0"));
        copyButton(view).doClick();
        assertThat(copied.getFirst()).startsWith("Jasper 1.2.0\nCommit " + COMMIT + " on main; committed 2026-10-13\n");
    }

    @Test void aFailedCopyKeepsTheLabel() {
        clipboardWorks = false;
        var view = view(build());
        copyButton(view).doClick();
        assertThat(copyButton(view).getText()).isEqualTo("Copy");
        assertThat(delays).isEmpty();
    }

    @Test void aSecondCopyRestartsTheTwoSeconds() {
        var view = view(build());
        copyButton(view).doClick();
        copyButton(view).doClick();
        assertThat(cancels).isEqualTo(1);
        assertThat(delays).hasSize(2);
        pending.getLast().run();
        assertThat(copyButton(view).getText()).isEqualTo("Copy");
    }

    @Test void licensesReachesTheApplication() {
        var view = view(build());
        KitTestSupport.require(view, AboutView.LICENSES, AbstractButton.class).doClick();
        assertThat(licenses).isEqualTo(1);
    }

    @Test void closingCancelsAPendingRevert() {
        var view = view(build());
        copyButton(view).doClick();
        view.close();
        assertThat(cancels).isEqualTo(1);
        pending.getFirst().run();
        view.close();
    }
}
