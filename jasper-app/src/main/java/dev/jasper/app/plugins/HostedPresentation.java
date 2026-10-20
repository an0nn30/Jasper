package dev.jasper.app.plugins;

import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.DefaultSource;
import dev.jasper.app.presentation.PluginLook;
import dev.jasper.app.presentation.Projections;
import dev.jasper.sdk.ui.Presentation;
import dev.jasper.ui.kit.Kit;
import java.awt.Font;
import java.util.Objects;
import java.util.Optional;

/** The presentation every plugin's appearance reports: the look this process runs with. */
final class HostedPresentation {
    private HostedPresentation() { }

    /**
     * The look's facts for plugins: its plugin projection, the faces and body size of its kit, and its default toolbar
     * height.
     *
     * @param look what plugins learn of the look
     * @param kit the kit the look installed
     * @param defaults the look's defaults
     * @return the presentation plugins read
     */
    static Presentation of(PluginLook look, Kit kit, DefaultSource defaults) {
        Objects.requireNonNull(look, "look"); Objects.requireNonNull(kit, "kit"); Objects.requireNonNull(defaults, "defaults");
        String ui = kit.faces().uiFace(400), data = kit.faces().dataRegular();
        return new Presentation(look.classic(), look.classicIcons() ? Presentation.Icons.CLASSIC : Presentation.Icons.NEW_UI,
            look.stripes() ? Presentation.ToolWindows.STRIPES : Presentation.ToolWindows.RAILS,
            Presentation.TitleBar.valueOf(look.titleBar().name()), Presentation.Menus.valueOf(look.menus().name()),
            Presentation.ToolbarSearch.valueOf(look.search().name()),
            Optional.ofNullable(ui).filter(face -> !face.isBlank()),
            Optional.ofNullable(data).filter(face -> !face.isBlank() && !face.equals(Font.MONOSPACED)),
            Math.round(kit.type().body()), defaults.value(CoreSettings.WINDOW_TOOLBAR_HEIGHT));
    }

    /** The default look on this platform, for a runtime that was given no presentation.
     * @return the default look's presentation
     */
    static Presentation standard() {
        // The default presentation's projections, kit and core defaults; it alone reads the default kit (StaticDefaultTest).
        var standard = dev.jasper.app.presentation.Presentation.standard(
            new Projections.Host(Projections.Platform.of(System.getProperty("os.name")), false));
        return of(standard.projections().plugins(), standard.kit(), standard.defaults());
    }
}
