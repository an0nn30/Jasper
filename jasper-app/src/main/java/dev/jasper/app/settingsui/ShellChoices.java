package dev.jasper.app.settingsui;

import dev.jasper.app.lifecycle.Subscription;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The shell profiles the Shell page offers, supplied by the application: the current choices, the platform default
 * first; a way to detect them again; and a way to hear that they changed. EDT only.
 *
 * @param choices the profiles to offer, in order
 * @param refresh asks for detection to run again
 * @param onChanged calls a listener after each change until its subscription closes
 */
public record ShellChoices(Supplier<List<Choice>> choices, Runnable refresh, Function<Runnable, Subscription> onChanged, java.util.Optional<Runnable> manageCygwin) {
    public ShellChoices(Supplier<List<Choice>> choices, Runnable refresh, Function<Runnable, Subscription> onChanged) {
        this(choices, refresh, onChanged, java.util.Optional.empty());
    }
    /** The profile id the Custom entry stands for. */
    public static final String CUSTOM = "custom";
    /** No detected shells, for pages built without an application: only Custom. */
    public static final ShellChoices NONE = new ShellChoices(List::of, () -> { }, listener -> new Subscription(() -> { }));

    public ShellChoices {
        Objects.requireNonNull(choices, "choices");
        Objects.requireNonNull(refresh, "refresh");
        Objects.requireNonNull(onChanged, "onChanged");
        Objects.requireNonNull(manageCygwin, "manageCygwin");
    }

    /**
     * One entry.
     *
     * @param id the value saved as {@code terminal.shell.profile}
     * @param label what the dropdown shows
     */
    public record Choice(String id, String label) {
        public Choice {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
        }
    }
}
