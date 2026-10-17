package dev.jasper.app.startup;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The steps a branded look's splash shows while Jasper starts (spec 10.11), as start-up reaches them:
 * {@value #RUNTIME} as the splash opens, {@value #CONFIGURATION} while the application is made, "Loading plugin: "
 * with each selected plugin's name just before it loads, {@value #WORKSPACE} as the first window is composed, and
 * "Starting" with the product name and "Workstation…" just before it shows. The count, four plus the selected plugins,
 * is known once the plugins are selected, and step i of n fills i / n. Steps only move forward: a call that would not
 * advance is ignored, and after {@link #done()} nothing shows. A splash that cannot show a step closes, and start-up
 * goes on. EDT.
 */
public final class StartupSteps {
    private static final System.Logger LOG = System.getLogger(StartupSteps.class.getName());

    /** The first step, shown as the splash opens. */
    public static final String RUNTIME = "Starting Java runtime…";
    /** While the application applies its configuration, themes and keymap. */
    public static final String CONFIGURATION = "Reading configuration…";
    /** Before each plugin loads, followed by its name. */
    public static final String PLUGIN = "Loading plugin: ";
    /** While the first window is composed. */
    public static final String WORKSPACE = "Restoring workspace…";

    /**
     * One step.
     *
     * @param text what the splash says
     * @param index the step's number, from 1
     * @param count how many steps this start has
     */
    public record Step(String text, int index, int count) {
        /** The text is required and the number within the count. */
        public Step {
            Objects.requireNonNull(text, "text");
            if (count < 1 || index < 1 || index > count) throw new IllegalArgumentException("Step " + index + " of " + count);
        }

        /** How far the bar is filled: the step's number over the count. */
        public double fraction() { return (double) index / count; }
    }

    /** No splash: every call does nothing. */
    public static final StartupSteps NONE = new StartupSteps("Jasper", 0, step -> { }, () -> { }, true);

    private final String product;
    private final int plugins;
    private Consumer<Step> show;
    private Runnable close;
    private Step current;
    private int loaded;
    private boolean done;

    private StartupSteps(String product, int plugins, Consumer<Step> show, Runnable close, boolean done) {
        this.product = product;
        this.plugins = plugins;
        this.show = show;
        this.close = close;
        this.done = done;
        current = new Step(RUNTIME, 1, count());
    }

    /**
     * The steps of a start that shows a splash, on the first one.
     *
     * @param product the look's product name, as the last step says it
     * @param plugins how many plugins the selection starts
     * @param show shows each step after the first; EDT
     * @param close closes the splash, once; EDT
     * @return the steps
     */
    public static StartupSteps of(String product, int plugins, Consumer<Step> show, Runnable close) {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(show, "show");
        Objects.requireNonNull(close, "close");
        if (product.isBlank()) throw new IllegalArgumentException("A splash names its product");
        if (plugins < 0) throw new IllegalArgumentException("Negative plugin count: " + plugins);
        return new StartupSteps(product, plugins, show, close, false);
    }

    /** The step the splash shows now: the first until start-up reaches another. */
    public Step current() { return current; }

    /** Whether the splash is still showing these steps. */
    public boolean active() { return !done; }

    /** The application is being made. */
    public void configuration() { advance(2, CONFIGURATION); }

    /**
     * A selected plugin is about to load.
     *
     * @param name the plugin's name
     */
    public void plugin(String name) {
        Objects.requireNonNull(name, "name");
        if (done || loaded >= plugins) return;
        loaded++;
        advance(2 + loaded, PLUGIN + name);
    }

    /** The first window is being composed. */
    public void workspace() { advance(count() - 1, WORKSPACE); }

    /** The first window is about to show. */
    public void workstation() { advance(count(), "Starting " + product + " Workstation…"); }

    /**
     * Start-up showed its first window or failed: the splash closes, once, and later calls do nothing. The steps then let
     * go of the splash, so a process that keeps them (a resident with no window) holds none of its images or fonts.
     */
    public void done() {
        if (done) return;
        done = true;
        Runnable closing = close;
        show = step -> { };
        close = () -> { };
        try { closing.run(); }
        catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "The splash could not be closed", failure); }
    }

    private int count() { return 4 + plugins; }

    private void advance(int index, String text) {
        if (done || index <= current.index()) return;
        current = new Step(text, index, count());
        try { show.accept(current); }
        catch (RuntimeException failure) {
            // A splash never stops start-up: one that cannot show a step closes.
            LOG.log(System.Logger.Level.WARNING, "The splash could not show a step; closing it", failure);
            done();
        }
    }
}
