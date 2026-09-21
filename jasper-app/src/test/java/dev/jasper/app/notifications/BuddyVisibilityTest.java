package dev.jasper.app.notifications;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyVisibilityTest {
    @Test void shownOnlyWhenEnabledAndSomeWindowIsShowingAndNotIconified() {
        var visibility = new BuddyVisibility();
        assertThat(visibility.enabled()).isTrue();
        assertThat(visibility.shown()).isFalse();
        visibility.window("a", true, false);
        assertThat(visibility.shown()).isTrue();
        visibility.window("a", true, true);
        assertThat(visibility.shown()).isFalse();
        visibility.window("b", true, false);
        assertThat(visibility.shown()).isTrue();
        visibility.window("b", false, false);
        assertThat(visibility.shown()).isFalse();
        visibility.window("a", true, false);
        visibility.remove("a");
        assertThat(visibility.shown()).isFalse();
    }

    @Test void sessionToggleOverridesTheSavedDefaultUntilTheSavedValueChanges() {
        var visibility = new BuddyVisibility();
        visibility.window("a", true, false);
        visibility.toggle();
        assertThat(visibility.enabled()).isFalse();
        assertThat(visibility.shown()).isFalse();
        visibility.configure(true); // unchanged saved value keeps the session choice
        assertThat(visibility.enabled()).isFalse();
        visibility.configure(false); // changed saved value resets the session choice
        assertThat(visibility.enabled()).isFalse();
        visibility.toggle();
        assertThat(visibility.enabled()).isTrue();
        assertThat(visibility.shown()).isTrue();
        visibility.configure(true);
        assertThat(visibility.enabled()).isTrue();
        visibility.toggle();
        assertThat(visibility.shown()).isFalse();
    }
}
