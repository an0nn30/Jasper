package dev.jasper.sample;

import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import java.util.Optional;

/** Logs, listens for theme changes and, when {@code demo_activity = true}, shows a short activity on Buddy. */
public final class SamplePlugin implements Plugin {
    private static final int STEPS = 10;

    /** Created by the runtime. */
    public SamplePlugin() { }

    // example:plugin:start
    @Override public void start(PluginContext context) {
        context.log().log(System.Logger.Level.INFO, "Sample plugin " + context.plugin().version() + " started");
        context.events().subscribe(AppEvents.THEME_CHANGED,
            event -> context.log().log(System.Logger.Level.INFO, "The look is now " + event.variant()));
        long delay = context.config().integer("demo_step_millis").orElse(300);
        if (delay < 0 || delay > 5000) {
            context.config().report("demo_step_millis", "Use 0 to 5000; using 300.");
            delay = 300;
        }
        long stepMillis = delay;
        if (context.config().bool("demo_activity").orElse(false))
            context.background().execute(() -> demo(context, stepMillis));
    }
    // example:plugin:end

    private static void demo(PluginContext context, long stepMillis) {
        ActivityHandle activity = context.activities().begin(
            new ActivitySpec("Sample plugin", "Warming up", Optional.empty(), Optional.empty()));
        try {
            for (int step = 1; step <= STEPS; step++) {
                Thread.sleep(stepMillis);
                activity.progress(step / (double) STEPS, "Step " + step + " of " + STEPS);
            }
            activity.succeed("Ready");
        } catch (InterruptedException stopped) {
            activity.cancelled();
            Thread.currentThread().interrupt();
        }
    }
}
