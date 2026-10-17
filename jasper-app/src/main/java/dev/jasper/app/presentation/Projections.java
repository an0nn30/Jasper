package dev.jasper.app.presentation;

import dev.jasper.app.dock.Slot;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Every consumer's projection of a presentation (spec 9.1), computed once by its table; the default look's values follow
 * from {@link ChromeSpec#DEFAULT} by the same rules. A group of fields takes the kit file's chrome only once the plan
 * that consumes it has landed ({@link Presentation#LANDED}); until then it is the default look's.
 *
 * @param desktop the bootstrap's flags, always computed from the file
 * @param workspace the workspace chrome
 * @param shell the window shells
 * @param palette the palette view and home
 * @param settings the Settings dialog
 * @param composition the app pages' composition
 * @param dialogs the dialog mapping
 * @param motion the motion policy
 * @param title the frame title template
 * @param dockLayout the ui-state key of the dock
 * @param panels the app's own panels
 */
public record Projections(DesktopFlags desktop, WorkspaceLook workspace, ShellLook shell, PaletteLook palette, SettingsChrome settings,
                          Composition composition, DialogMapping dialogs, Motion motion, TitleFormat title, DockLayoutKey dockLayout,
                          AppPanels panels) {
    /**
     * Facts of the host the 9.1 table reads.
     *
     * @param mac whether this is macOS
     * @param unifiedTitleSupported whether the unified macOS title bar is available
     */
    public record Host(boolean mac, boolean unifiedTitleSupported) { }

    /** Every projection is required. */
    public Projections {
        Objects.requireNonNull(desktop); Objects.requireNonNull(workspace); Objects.requireNonNull(shell); Objects.requireNonNull(palette);
        Objects.requireNonNull(settings); Objects.requireNonNull(composition); Objects.requireNonNull(dialogs); Objects.requireNonNull(motion);
        Objects.requireNonNull(title); Objects.requireNonNull(dockLayout); Objects.requireNonNull(panels);
    }

    /**
     * The 9.1 table.
     *
     * @param chrome the kit's chrome
     * @param classicBase whether the kit's base is classic
     * @param namesUiFace whether the kit names a UI face
     * @param providerId the provider's id, empty for the default look
     * @param host the host's facts
     * @param landed the groups whose plans have landed
     * @return the projections; never throws for a chrome the parser accepts
     */
    public static Projections of(ChromeSpec chrome, boolean classicBase, boolean namesUiFace, String providerId, Host host,
                                 Set<Presentation.Landed> landed) {
        ChromeSpec window = landed.contains(Presentation.Landed.WINDOW_CHROME) ? chrome : ChromeSpec.DEFAULT;
        ChromeSpec dock = landed.contains(Presentation.Landed.DOCK_CHROME) ? chrome : ChromeSpec.DEFAULT;
        ChromeSpec run = landed.contains(Presentation.Landed.RUN_WINDOW) ? chrome : ChromeSpec.DEFAULT;
        boolean settingsLanded = landed.contains(Presentation.Landed.SETTINGS);
        ChromeSpec page = settingsLanded ? chrome : ChromeSpec.DEFAULT;
        boolean classic = settingsLanded && classicBase, faces = settingsLanded && namesUiFace;
        boolean nativeTitle = window.titleBar() == ChromeSpec.TitleBar.NATIVE, windowMenus = window.menuBar() == ChromeSpec.MenuBar.WINDOW;
        boolean stripes = dock.toolWindows() == ChromeSpec.ToolWindows.STRIPES, runWindow = run.palette() == ChromeSpec.PaletteKind.RUN_WINDOW;
        var desktop = new DesktopFlags(chrome.menuBar() != ChromeSpec.MenuBar.WINDOW, chrome.titleBar() == ChromeSpec.TitleBar.NATIVE);
        var workspace = new WorkspaceLook(windowMenus ? WorkspaceLook.Menus.CLASSIC : WorkspaceLook.Menus.STANDARD,
            nativeTitle ? WorkspaceLook.Header.CLASSIC : WorkspaceLook.Header.UNIFIED,
            stripes ? WorkspaceLook.Stripes.LABELLED : WorkspaceLook.Stripes.RAILS,
            window.editorTabs() == ChromeSpec.EditorTabs.CLASSIC ? WorkspaceLook.Tabs.CLASSIC : WorkspaceLook.Tabs.EQUAL,
            window.statusBar() == ChromeSpec.StatusBar.CELLS ? WorkspaceLook.Status.RULED : WorkspaceLook.Status.SLASH,
            window.palette() == ChromeSpec.PaletteKind.RUN_WINDOW,
            stripes ? WorkspaceLook.FrameStyle.FLUSH : WorkspaceLook.FrameStyle.STANDARD);
        var shell = new ShellLook(window.titleBar() == ChromeSpec.TitleBar.UNIFIED && host.unifiedTitleSupported(), windowMenus, nativeTitle);
        var palette = new PaletteLook(runWindow ? PaletteLook.View.RUN_WINDOW : PaletteLook.View.CARD,
            runWindow ? PaletteLook.Home.CORNER : PaletteLook.Home.TITLE_FIELD);
        var hidden = new LinkedHashSet<String>();
        if (page.titleBar() == ChromeSpec.TitleBar.NATIVE || host.mac()) hidden.add("window.native_decorations");
        if (page.motion() == ChromeSpec.MotionKind.NONE) hidden.add("ui.animations.enabled");
        var settings = new SettingsChrome(page.titleBar() != ChromeSpec.TitleBar.NATIVE, classic, !classic, hidden,
            faces ? "Use the default font size" : "Use platform font size");
        return new Projections(desktop, workspace, shell, palette, settings, classic ? Composition.CLASSIC : Composition.ROWS,
            new DialogMapping(window.dialogs() == ChromeSpec.DialogKind.CLASSIC ? DialogMapping.Kind.CLASSIC : DialogMapping.Kind.AS_REQUESTED, nativeTitle),
            window.motion() == ChromeSpec.MotionKind.NONE ? Motion.NONE : Motion.STANDARD, new TitleFormat(window.windowTitle()),
            new DockLayoutKey(landed.contains(Presentation.Landed.DOCK_CHROME) ? providerId : ""),
            stripes ? new AppPanels("Event Log", Slot.BOTTOM_LEFT) : new AppPanels("Notifications", Slot.RIGHT_TOP));
    }

    /**
     * Spec 4.7's fallback: the default kit's chrome under the title and menus {@code committed} before AWT. A committed native
     * title keeps the native-title values; a unified one gives the default look's values on {@code host}.
     *
     * @param committed the desktop flags committed before AWT
     * @param host the host's facts
     * @return the fallback projections
     */
    public static Projections fallback(DesktopFlags committed, Host host) {
        Objects.requireNonNull(committed, "committed"); Objects.requireNonNull(host, "host");
        boolean nativeTitle = committed.nativeTitle();
        Set<String> hidden = nativeTitle || host.mac() ? Set.of("window.native_decorations") : Set.of();
        return new Projections(committed,
            new WorkspaceLook(WorkspaceLook.Menus.STANDARD, WorkspaceLook.Header.UNIFIED, WorkspaceLook.Stripes.RAILS, WorkspaceLook.Tabs.EQUAL,
                WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD),
            new ShellLook(!nativeTitle && host.unifiedTitleSupported(), !committed.screenMenuBar(), nativeTitle),
            new PaletteLook(PaletteLook.View.CARD, PaletteLook.Home.TITLE_FIELD),
            new SettingsChrome(!nativeTitle, false, true, hidden, "Use platform font size"), Composition.ROWS,
            new DialogMapping(DialogMapping.Kind.AS_REQUESTED, nativeTitle), Motion.STANDARD, new TitleFormat("{context}"), new DockLayoutKey(""),
            new AppPanels("Notifications", Slot.RIGHT_TOP));
    }
}
