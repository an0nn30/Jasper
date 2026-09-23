package dev.jasper.sample;

import dev.jasper.sdk.ui.OldGnomeIcon;
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
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PendingSession;
import dev.jasper.sdk.terminal.SessionSpec;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Logs, listens for theme changes and, when configured, shows a short activity on Buddy
 * ({@code demo_activity = true}) and one action placed everywhere the SDK allows ({@code demo_ui = true}).
 */
public final class SamplePlugin implements Plugin {
    private static final int STEPS = 10;
    private static final String DEMO = "dev.jasper.sample.demo";
    private static final String GREET = "dev.jasper.sample.greet";
    private static final String ECHO = "dev.jasper.sample.echo";
    private static final String GREETINGS = "dev.jasper.sample.greetings";
    private static final String GREETINGS_OPEN = "dev.jasper.sample.greetings.open";

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
        if (context.config().bool("demo_terminal").orElse(false)) installTerminalDemo(context);
        if (context.config().bool("demo_session").orElse(false)) installSessionDemo(context, stepMillis);
                if (context.config().bool("demo_scope").orElse(false)) installPaletteDemo(context);
        if (context.config().bool("demo_activity").orElse(false))
            context.background().execute(() -> demo(context, stepMillis));
    }
    // example:plugin:end

    // example:pluginui:start
    private static void installUi(PluginContext context, long stepMillis) {
        PluginAction[] demo = new PluginAction[1];
        demo[0] = context.actions().register(ActionSpec.of(DEMO, "Run Sample Activity")
                .withIcon(context.appearance().icon("dev/jasper/sample/flask.svg", OldGnomeIcon.EXECUTE))
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
        var icon = context.appearance().icon("dev/jasper/sample/flask.svg", OldGnomeIcon.EXECUTE);
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

    // example:pluginterminal:start
    private static void installTerminalDemo(PluginContext context) {
        // Capabilities are declared in plugin.toml and consented to by the user. A gated call without one
        // throws MissingCapabilityException, so a plugin that can live without a capability checks first.
        if (!context.plugin().capabilities().containsAll(List.of(Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_INJECT))) {
            context.log().log(System.Logger.Level.INFO, "The terminal demo needs terminal.observe and terminal.inject");
            return;
        }
        context.actions().register(ActionSpec.of(GREET, "Insert Sample Greeting").withKeywords(List.of("sample", "type", "terminal")), invoked ->
            // The pane the action was invoked on, or else the one the user used last. sendText adds nothing:
            // without a newline the text waits at the prompt, and the user decides whether to run it.
            invoked.pane().or(() -> context.terminals().activePane())
                .ifPresent(pane -> pane.sendText("echo 'hello from the sample plugin'")));
        context.menus().terminalContext().add(GREET);

        StatusItem last = context.statusBar().add(new StatusItemSpec("dev.jasper.sample.last", Side.LEFT, 100));
        last.setVisible(false);
        // Terminal events name panes by id and arrive later, on the event thread; there is no replay.
        context.events().subscribe(TerminalEvents.COMMAND_FINISHED, finished -> {
            last.setText(finished.command() + ": " + (finished.exitStatus().isPresent() ? "exit " + finished.exitStatus().getAsInt() : "done"));
            last.setVisible(true);
        });
    }
    // example:pluginterminal:end

    // example:pluginsession:start
    private static void installSessionDemo(PluginContext context, long stepMillis) {
        if (!context.plugin().capabilities().contains(Capabilities.SESSION_PROVIDE)) {
            context.log().log(System.Logger.Level.INFO, "The session demo needs session.provide");
            return;
        }
        context.actions().register(ActionSpec.of(ECHO, "Open Sample Echo Session").withKeywords(List.of("sample", "session", "echo")), invoked ->
            // The pane appears at once, waiting. The connector runs on the event thread for the first connect
            // and for every Reconnect, so it only hands the work to the background executor.
            context.terminals().openTab(invoked.window(), OpenRequest.session(SessionSpec.of("Sample echo",
                pending -> context.background().execute(() -> connectEcho(pending, stepMillis))))));
    }

    private static void connectEcho(PendingSession pending, long stepMillis) {
        pending.status("Connecting to the sample echo…");
        // A real connect blocks here; onCancelled is where it would be aborted.
        var waiting = Thread.currentThread();
        var registration = pending.onCancelled(waiting::interrupt);
        try { Thread.sleep(stepMillis); }
        catch (InterruptedException cancelled) { return; }
        finally { registration.close(); }
        // From attach on, Jasper owns the connection and closes it exactly once, even if the user cancelled meanwhile.
        pending.attach(new EchoSession().connection());
    }
    // example:pluginsession:end

    // example:pluginpalette:start
    private static void installPaletteDemo(PluginContext context) {
        if (!context.plugin().capabilities().containsAll(List.of(Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_INJECT))) {
            context.log().log(System.Logger.Level.INFO, "The palette demo needs palette.contribute and terminal.inject");
            return;
        }
        // Register the action first: the scope names it, and its shortcut then reaches the scope both ways.
        // Closed, this handler opens the palette on the scope; open, the palette switches to it or dismisses.
        context.actions().register(ActionSpec.of(GREETINGS_OPEN, "Sample Greetings…").withDefaultBinding("cmd+alt+g"), invoked ->
            context.palette().open(invoked.window(), GREETINGS, Optional.empty(), Optional.empty()));
        List<String> greetings = List.of("good morning", "hello", "hi there");
        context.palette().register(new PaletteScope() {
            @Override public ScopeSpec spec() {
                return ScopeSpec.of(GREETINGS, "Greetings", "Search greetings, or > to switch scope",
                        List.of(new PaletteVerb("paste", "Paste"), new PaletteVerb("paste_run", "Paste and run")))
                    .withAliases(List.of("greet")).withShortcutActionId(GREETINGS_OPEN);
            }
            // Search runs on the UI thread for every keystroke: rank what is already in memory, never read files here.
            @Override public PaletteResults search(String query, PaletteQuery palette) {
                String needle = query.strip().toLowerCase(Locale.ROOT);
                var rows = new ArrayList<PaletteRow>();
                for (String greeting : greetings)
                    if (greeting.contains(needle)) rows.add(PaletteRow.of("greeting." + rows.size(), greeting).withToken(greeting));
                return new PaletteResults(rows, needle.isEmpty() ? Optional.of("Greetings") : Optional.empty(), Optional.empty());
            }
            // The row comes back exactly as returned, token included. The target is the pane the palette was opened
            // from; pasting into it needs terminal.inject, like any other pane.
            @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery palette) { return palette.target().isPresent(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery palette) {
                palette.target().ifPresent(pane -> {
                    pane.paste("echo '" + row.token() + "'");
                    if (verb.id().equals("paste_run")) pane.sendText("\r");
                });
            }
            @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
        });
    }
    // example:pluginpalette:end

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
