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
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.ViewSpec;
import java.awt.Dimension;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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
    private static final String FLASK = "dev.jasper.sample:flask";
    private static final String RUN_BUTTON = "sample.run", DETAILS_BUTTON = "sample.about.details", CLOSE_BUTTON = "sample.close";

    /** Created by the runtime. */
    public SamplePlugin() { }

    // example:plugin:start
    @Override public void start(PluginContext context) {
        context.kit().registerIcon(new dev.jasper.sdk.ui.IconAsset("dev.jasper.sample:flask", "M 6 2 H 10 M 6.67 6 H 9.33 M 6.67 2 V 6 L 4 13.33 Q 3.8 14 4.33 14 H 11.67 Q 12.2 14 12 13.33 L 9.33 6 V 2"));
        context.log().log(System.Logger.Level.INFO, "Sample plugin " + context.plugin().version() + " started");
        context.events().subscribe(AppEvents.THEME_CHANGED,
            event -> context.log().log(System.Logger.Level.INFO, "The look is now " + event.variant()));
        var settings = new SampleSettings(context.settings());
        context.settings().page(new SampleSettingsPanel(context.kit(), settings));
        long stepMillis = settings.stepMillis.get();
        if (settings.ui.get()) installUi(context, stepMillis);
        if (settings.terminal.get()) installTerminalDemo(context);
        if (settings.session.get()) installSessionDemo(context, stepMillis);
        if (settings.scope.get()) installPaletteDemo(context);
        if (settings.activity.get()) context.background().execute(() -> demo(context, stepMillis));
    }
    // example:plugin:end

    // example:pluginui:start
    private static void installUi(PluginContext context, long stepMillis) {
        PluginAction[] demo = new PluginAction[1];
        demo[0] = context.actions().register(ActionSpec.of(DEMO, "Run Sample Activity")
                .withIcon("dev.jasper.sample:flask")
                .withKeywords(List.of("sample", "demo", "activity"))
                .withDefaultBinding("cmd+alt+j"),
            invoked -> {
                demo[0].setEnabled(false);
                context.background().execute(() -> demo(context, stepMillis));
            });
        context.toolbar().add(ToolbarItem.action(DEMO));
        context.menus().create("dev.jasper.sample.menu", "Sample").add(DEMO);
        context.menus().standard(StandardMenu.TOOLS).add(DEMO);
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
        // One instance per window, built the first time the panel is shown there. Its body is the kit's empty state:
        // a tile, a title, one sentence and one secondary action, centered in the panel.
        context.panels().register(new PanelSpec("dev.jasper.sample.panel", "Sample", FLASK, Anchor.LEFT), host ->
            context.kit().create(new ViewSpec(new Nodes.Panel("sample.panel", ControlState.READY,
                new Nodes.Header("sample.header", ControlState.READY, "Sample", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
                Optional.empty(), new Nodes.Empty("sample.body", ControlState.READY,
                    new Nodes.Icon("sample.icon", ControlState.READY, FLASK, Nodes.Hue.NONE, Nodes.IconSize.EMPTY, "Sample"),
                    "Sample Activity", "Runs a short demo activity in the background.",
                    List.of(new Nodes.Button(RUN_BUTTON, ControlState.READY, "Run Sample Activity", "", Nodes.ButtonKind.SECONDARY, RUN_BUTTON, ""))),
                Optional.empty())), event -> {
                    if (event instanceof UiEvent.Action action && action.id().equals(RUN_BUTTON))
                        context.background().execute(() -> demo(context, stepMillis));
                }));

        context.actions().register(ActionSpec.of("dev.jasper.sample.about", "About Sample").withIcon(FLASK), invoked -> {
            // The application builds the frame, title bar and menu bar; a singleton comes back while it is open.
            PluginWindow window = context.windows().create(
                new WindowSpec("dev.jasper.sample.about-window", "About Sample", new Dimension(360, 200), true));
            var content = context.kit().create(new ViewSpec(new Nodes.Empty("sample.about", ControlState.READY,
                new Nodes.Icon("sample.about.icon", ControlState.READY, FLASK, Nodes.Hue.NONE, Nodes.IconSize.EMPTY, "Sample"), "Sample Plugin",
                "This window's chrome belongs to Jasper; its content to the plugin.",
                List.of(new Nodes.Button(DETAILS_BUTTON, ControlState.READY, "Details…", "", Nodes.ButtonKind.SECONDARY, DETAILS_BUTTON, "")))), event -> {
                if (!(event instanceof UiEvent.Action action) || !action.id().equals(DETAILS_BUTTON)) return;
                PluginDialog dialog = context.windows().dialog(new DialogSpec("Sample Details", window, true));
                // A sheet's canonical body: a form card of label/value rows, then the one primary action.
                var body = context.kit().create(new ViewSpec(new Nodes.Form("sample.details", ControlState.READY,
                    Nodes.FormKind.SHEET, "Sample Details", Optional.empty(), List.of(new Nodes.Card("sample.details.card", ControlState.READY,
                        Nodes.CardKind.FORM, List.of(new Nodes.FormRow("sample.version.row", ControlState.READY, "Version", "",
                            new Nodes.Text("sample.version", ControlState.READY, context.plugin().version(), Nodes.TextRole.BODY), Optional.empty())))),
                    Optional.empty(), List.of(new Nodes.Button(CLOSE_BUTTON, ControlState.READY, "Close", "", Nodes.ButtonKind.PRIMARY, CLOSE_BUTTON, "")),
                    CLOSE_BUTTON)), answer -> {
                    if (answer instanceof UiEvent.Action chosen && chosen.id().equals(CLOSE_BUTTON)) dialog.close();
                });
                dialog.setContent(body);
                dialog.show();
            });
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
        context.actions().register(ActionSpec.of(GREETINGS_OPEN, "Sample Greetings…").withDefaultBinding("cmd+alt+y"), invoked ->
            context.palette().open(invoked.window(), GREETINGS, Optional.empty(), Optional.empty()));
        List<String> greetings = List.of("good morning", "hello", "hi there");
        context.palette().register(new PaletteScope() {
            @Override public ScopeSpec spec() {
                return ScopeSpec.of(GREETINGS, "Greetings", "Search greetings",
                        List.of(new PaletteVerb("paste", "Paste"), new PaletteVerb("paste_run", "Paste and run")))
                    .withShortcutActionId(GREETINGS_OPEN).withItemNoun("greeting");
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
