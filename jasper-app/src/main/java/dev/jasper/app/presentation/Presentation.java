package dev.jasper.app.presentation;

import dev.jasper.app.config.DefaultSource;
import dev.jasper.ui.kit.Kit;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The one presentation a process runs with (spec 3.2 and 4.9), chosen on the bootstrap thread and replaced at most once,
 * by {@link #fallback(Projections.Host)}, before plugins start. Consumers receive its kit or a projection, never this value.
 *
 * @param provider the providing plugin, empty for the default look
 * @param kit the jasper-ui kit every context is made with
 * @param chrome the parsed chrome points
 * @param classicBase whether the kit's base is classic
 * @param faces the face files the kit names
 * @param defaults the kit's setting defaults
 * @param projections every consumer's projection
 */
public record Presentation(Optional<Provider> provider, Kit kit, ChromeSpec chrome, boolean classicBase, KitParser.Faces faces,
                           DefaultSource defaults, Projections projections) {
    /**
     * The plugin that provides a look.
     *
     * @param id its id
     * @param name its name, as notices say it
     * @param version its version
     * @param digest the look's digest
     */
    public record Provider(String id, String name, String version, String digest) {
        /** Every part is required. */
        public Provider { Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(version); Objects.requireNonNull(digest); }
    }

    /**
     * The groups of projections whose plans consume them (spec 19.1 rows 6 to 9): the window shell (desktop flags,
     * menus, shell, motion, title), the window content (header, tabs, status, run item, dialogs), the dock, the Run window
     * and Settings.
     */
    public enum Landed { WINDOW_CHROME, WINDOW_CONTENT, DOCK_CHROME, RUN_WINDOW, SETTINGS }

    /** The groups that have landed; every other group's values map to the default projection. */
    public static final Set<Landed> LANDED = Set.of(Landed.WINDOW_CHROME);

    /** Every part is required. */
    public Presentation {
        Objects.requireNonNull(provider); Objects.requireNonNull(kit); Objects.requireNonNull(chrome); Objects.requireNonNull(faces);
        Objects.requireNonNull(defaults); Objects.requireNonNull(projections);
    }

    /**
     * The default look.
     *
     * @param host the host's facts
     * @return the default look's presentation
     */
    public static Presentation standard(Projections.Host host) {
        return new Presentation(Optional.empty(), Kit.DEFAULT, ChromeSpec.DEFAULT, false, KitParser.Faces.NONE, DefaultSource.CORE,
            Projections.of(ChromeSpec.DEFAULT, false, false, "", host, LANDED));
    }

    /**
     * {@code provider}'s look from its parsed kit.
     *
     * @param provider the providing plugin
     * @param parsed its parsed kit file
     * @param host the host's facts
     * @return the provider's presentation
     */
    public static Presentation of(Provider provider, KitParser.Parsed parsed, Projections.Host host) {
        return new Presentation(Optional.of(provider), parsed.kit(), parsed.chrome(), parsed.classicBase(), parsed.faces(), parsed.defaults(),
            Projections.of(parsed.chrome(), parsed.classicBase(), parsed.faces().ui().isPresent(), provider.id(), host, LANDED));
    }

    /**
     * Spec 4.7: the default kit and chrome under the flags already committed on {@code host}, keeping the defaults read at start.
     *
     * @param host the host's facts
     * @return the fallback presentation
     */
    public Presentation fallback(Projections.Host host) {
        return new Presentation(Optional.empty(), Kit.DEFAULT, ChromeSpec.DEFAULT, false, KitParser.Faces.NONE, defaults,
            Projections.fallback(committedDesktop(), host));
    }

    /**
     * The desktop flags the bootstrap applied: the look's once window chrome has landed, else the default look's.
     *
     * @return the committed flags
     */
    public DesktopFlags committedDesktop() {
        return LANDED.contains(Landed.WINDOW_CHROME) ? projections.desktop() : DesktopFlags.STANDARD;
    }

    /**
     * The provider's id, or {@code default}: how the start-up trace names the look.
     *
     * @return the look's name in the trace
     */
    public String look() { return provider.map(Provider::id).orElse("default"); }
}
