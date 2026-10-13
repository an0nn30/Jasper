package dev.jasper.app.application;

import dev.jasper.app.about.AboutView;
import dev.jasper.app.about.LicensesView;
import dev.jasper.app.about.ThirdPartyNotices;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.platform.BuildInfo;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.ui.swing.KitContext;
import java.awt.Dimension;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The About and Licenses windows. About Jasper is an application action (the palette, Help on Windows and Linux, and on
 * macOS the application menu through JasperApplication); About's Licenses… opens Licenses, which stays open when About
 * closes. Each is one auxiliary window: opening it again brings it to the front. The auxiliary windows remember their
 * bounds and close them at shutdown; closing a window stops its view. EDT only.
 */
final class AboutWindows {
    static final String ABOUT = "app.about", ABOUT_ID = "jasper.about", LICENSES_ID = "jasper.licenses";
    static final Dimension ABOUT_SIZE = new Dimension(340, 400), LICENSES_SIZE = new Dimension(760, 520);

    /**
     * What the windows show and ask of the application.
     *
     * @param build this build
     * @param environment the OS and Java lines Copy adds (its Jasper line is not used)
     * @param zone the zone the commit date is shown in
     * @param copy puts text on the clipboard; false when that failed (the application has said so)
     * @param browse opens a homepage
     * @param after runs a task once after a delay, returning what cancels it
     */
    record Hooks(BuildInfo build, IncidentReport.Environment environment, ZoneId zone, Predicate<String> copy, Consumer<URI> browse,
                 BiFunction<Duration, Runnable, Runnable> after) {
        Hooks {
            Objects.requireNonNull(build, "build");
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(zone, "zone");
            Objects.requireNonNull(copy, "copy");
            Objects.requireNonNull(browse, "browse");
            Objects.requireNonNull(after, "after");
        }
    }

    private final AuxiliaryWindows windows;
    private final Supplier<KitContext> kit;
    private final Hooks hooks;
    private AuxiliarySurface about, licenses;

    AboutWindows(Contributions contributions, AuxiliaryWindows windows, Supplier<KitContext> kit, Hooks hooks) {
        this.windows = Objects.requireNonNull(windows, "windows");
        this.kit = Objects.requireNonNull(kit, "kit");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        contributions.addAction(ABOUT, "About Jasper", null, List.of("version", "commit", "build", "license", "licenses", "credits"),
            Optional.empty(), invocation -> showAbout());
    }

    /** Help's entries: Keyboard Shortcuts, then About Jasper except on macOS, where About is in the application menu. */
    static List<MenuEntry> helpEntries(boolean macOs) {
        var shortcuts = new MenuEntry.Item("app.shortcuts");
        return macOs ? List.of(shortcuts) : List.of(shortcuts, new MenuEntry.Item(ABOUT));
    }

    /** Opens About, or brings it forward. A view that fails to build takes its window with it. */
    void showAbout() {
        if (about == null || about.closed()) {
            AuxiliarySurface window = windows.window(ABOUT_ID, "About Jasper", ABOUT_SIZE, true);
            AboutView view;
            try {
                view = new AboutView(kit.get(), new AboutView.Hooks(hooks.build(), hooks.environment().os(), hooks.environment().java(),
                    hooks.zone(), hooks.copy(), this::showLicenses, window::close, hooks.after()));
            } catch (RuntimeException | Error failure) {
                window.close();
                throw failure;
            }
            window.setContent(view);
            window.onClosed(view::close);
            about = window;
        }
        about.show();
        about.toFront();
    }

    /** Opens Licenses, or brings it forward, with Jasper (at this build's version) first. */
    void showLicenses() {
        if (licenses == null || licenses.closed()) {
            AuxiliarySurface window = windows.window(LICENSES_ID, "Licenses", LICENSES_SIZE, true);
            LicensesView view;
            try {
                view = new LicensesView(kit.get(), ThirdPartyNotices.listed(hooks.build().versionLine()),
                    new LicensesView.Hooks(hooks.browse(), window::close));
            } catch (RuntimeException | Error failure) {
                window.close();
                throw failure;
            }
            window.setContent(view);
            window.onClosed(view::close);
            licenses = window;
        }
        licenses.show();
        licenses.toFront();
    }

    /** Test access: the open About window. */
    Optional<AuxiliarySurface> aboutSurface() { return about == null || about.closed() ? Optional.empty() : Optional.of(about); }

    /** Test access: the open Licenses window. */
    Optional<AuxiliarySurface> licensesSurface() { return licenses == null || licenses.closed() ? Optional.empty() : Optional.of(licenses); }
}
