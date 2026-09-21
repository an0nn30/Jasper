package dev.jasper.sample;

import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.PluginWindow;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.ToolbarItem;
import dev.jasper.sdk.ui.WindowSpec;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Logs, listens for theme changes and, when configured, shows a short activity on Buddy
 * ({@code demo_activity = true}) and one action placed everywhere the SDK allows ({@code demo_ui = true}).
 */
public final class SamplePlugin implements Plugin {
    private static final int STEPS = 10;
    private static final String DEMO = "dev.jasper.sample.demo";

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
        if (context.config().bool("demo_ui").orElse(false)) installUi(context, stepMillis);
        if (context.config().bool("demo_activity").orElse(false))
            context.background().execute(() -> demo(context, stepMillis));
    }
    // example:plugin:end

    // example:pluginui:start
    private static void installUi(PluginContext context, long stepMillis) {
        PluginAction[] demo = new PluginAction[1];
        demo[0] = context.actions().register(ActionSpec.of(DEMO, "Run Sample Activity")
                .withIcon(context.appearance().icon("dev/jasper/sample/flask.svg"))
                .withKeywords(List.of("sample", "demo", "activity"))
                .withDefaultBinding("cmd+alt+j"),
            invoked -> {
                demo[0].setEnabled(false);
                context.background().execute(() -> demo(context, stepMillis));
            });
        context.toolbar().add(ToolbarItem.action(DEMO));
        context.menus().create("dev.jasper.sample.menu", "Sample").add(DEMO);
        context.menus().standard(StandardMenu.VIEW).add(DEMO);
        context.menus().terminalContext().add(DEMO);

        StatusItem status = context.statusBar().add(new StatusItemSpec("dev.jasper.sample.status", Side.RIGHT, 100));
        status.setText("Sample: idle");
        status.setTooltip("Run the sample activity");
        status.setAction(DEMO);
        // Handlers run on the UI thread, which is where status items and actions may be changed.
        context.events().subscribe(Activities.TOPIC, event -> {
            if (!event.sourcePluginId().equals(context.plugin().id())) return;
            status.setText(event.terminal() ? "Sample: ready" : "Sample: " + Math.round(event.fraction().orElse(0) * 100) + "%");
            if (event.terminal()) demo[0].setEnabled(true);
        });
        installPanelAndWindow(context, stepMillis);
    }
    // example:pluginui:end

    // example:pluginpanels:start
    private static void installPanelAndWindow(PluginContext context, long stepMillis) {
        var icon = context.appearance().icon("dev/jasper/sample/flask.svg");
        // One instance per window, built the first time the panel is shown there.
        context.panels().register(new PanelSpec("dev.jasper.sample.panel", "Sample", icon, Anchor.LEFT), host -> {
            var run = new JButton("Run sample activity");
            run.addActionListener(event -> context.background().execute(() -> demo(context, stepMillis)));
            var panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            panel.add(new JLabel("Sample panel"), BorderLayout.NORTH);
            panel.add(run, BorderLayout.SOUTH);
            return panel;
        });

        context.actions().register(ActionSpec.of("dev.jasper.sample.about", "About Sample").withIcon(icon), invoked -> {
            // The application builds the frame, title bar and menu bar; a singleton comes back while it is open.
            PluginWindow window = context.windows().create(
                new WindowSpec("dev.jasper.sample.about-window", "About Sample", new Dimension(360, 200), true));
            var details = new JButton("Details…");
            details.addActionListener(event -> {
                PluginDialog dialog = context.windows().dialog(new DialogSpec("Sample details", window, true));
                var close = new JButton("Close");
                close.addActionListener(closing -> dialog.close());
                var body = new JPanel(new BorderLayout(0, 8));
                body.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
                body.add(new JLabel("Sample plugin " + context.plugin().version()), BorderLayout.CENTER);
                body.add(close, BorderLayout.SOUTH);
                dialog.setContent(body);
                dialog.show();
            });
            var content = new JPanel(new BorderLayout(0, 8));
            content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            content.add(new JLabel("This window's chrome belongs to Jasper; its content to the plugin."), BorderLayout.CENTER);
            content.add(details, BorderLayout.SOUTH);
            window.setContent(content);
            window.show();
            window.toFront();
        });
        context.rail().add("dev.jasper.sample.about");
    }
    // example:pluginpanels:end

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
