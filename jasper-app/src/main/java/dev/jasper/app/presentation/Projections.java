package dev.jasper.app.presentation;

import dev.jasper.app.dock.Slot;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Every consumer's projection of a presentation (spec 9.1), computed once by its table; the default look's values follow
 * from {@link ChromeSpec#DEFAULT} by the same rules.
 *
 * @param desktop the bootstrap's flags
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
     * @return the projections; never throws for a chrome the parser accepts
     */
    public static Projections of(ChromeSpec chrome, boolean classicBase, boolean namesUiFace, String providerId, Host host) {
        boolean nativeTitle = chrome.titleBar() == ChromeSpec.TitleBar.NATIVE, windowMenus = chrome.menuBar() == ChromeSpec.MenuBar.WINDOW;
        boolean stripes = chrome.toolWindows() == ChromeSpec.ToolWindows.STRIPES, runWindow = chrome.palette() == ChromeSpec.PaletteKind.RUN_WINDOW;
        var desktop = new DesktopFlags(chrome.menuBar() != ChromeSpec.MenuBar.WINDOW, chrome.titleBar() == ChromeSpec.TitleBar.NATIVE);
        var workspace = new WorkspaceLook(windowMenus ? WorkspaceLook.Menus.CLASSIC : WorkspaceLook.Menus.STANDARD,
            chrome.titleBar() == ChromeSpec.TitleBar.NATIVE ? WorkspaceLook.Header.CLASSIC : WorkspaceLook.Header.UNIFIED,
            stripes ? WorkspaceLook.Stripes.LABELLED : WorkspaceLook.Stripes.RAILS,
            chrome.editorTabs() == ChromeSpec.EditorTabs.CLASSIC ? WorkspaceLook.Tabs.CLASSIC : WorkspaceLook.Tabs.EQUAL,
            chrome.statusBar() == ChromeSpec.StatusBar.CELLS ? WorkspaceLook.Status.RULED : WorkspaceLook.Status.SLASH,
            chrome.palette() == ChromeSpec.PaletteKind.RUN_WINDOW,
            stripes ? WorkspaceLook.FrameStyle.FLUSH : WorkspaceLook.FrameStyle.STANDARD,
            chrome.icons() == ChromeSpec.Icons.CLASSIC ? WorkspaceLook.Icons.CLASSIC : WorkspaceLook.Icons.NEW_UI);
        var shell = new ShellLook(chrome.titleBar() == ChromeSpec.TitleBar.UNIFIED && host.unifiedTitleSupported(), windowMenus, nativeTitle);
        var palette = new PaletteLook(runWindow ? PaletteLook.View.RUN_WINDOW : PaletteLook.View.CARD,
            runWindow ? PaletteLook.Home.UPPER : PaletteLook.Home.TITLE_FIELD);
        var hidden = new LinkedHashSet<String>();
        if (chrome.titleBar() == ChromeSpec.TitleBar.NATIVE || host.mac()) hidden.add("window.native_decorations");
        if (chrome.motion() == ChromeSpec.MotionKind.NONE) hidden.add("ui.animations.enabled");
        if (stripes) hidden.add("window.rail_width");
        var settings = new SettingsChrome(chrome.titleBar() != ChromeSpec.TitleBar.NATIVE, classicBase, !classicBase, hidden, namesUiFace);
        return new Projections(desktop, workspace, shell, palette, settings, classicBase ? Composition.CLASSIC : Composition.ROWS,
            new DialogMapping(chrome.dialogs() == ChromeSpec.DialogKind.CLASSIC ? DialogMapping.Kind.CLASSIC : DialogMapping.Kind.AS_REQUESTED, nativeTitle),
            chrome.motion() == ChromeSpec.MotionKind.NONE ? Motion.NONE : Motion.STANDARD, new TitleFormat(chrome.windowTitle()),
            new DockLayoutKey(providerId),
            stripes ? new AppPanels("Event Log", Slot.BOTTOM_LEFT) : new AppPanels("Notifications", Slot.RIGHT_TOP));
    }

    /**
     * The default look's projections on {@code host}: what a Settings page made outside a dialog, or a dialog made without
     * a presentation, runs with.
     *
     * @param host the host's facts
     * @return the projections of {@link ChromeSpec#DEFAULT} under the default kit
     */
    public static Projections standard(Host host) {
        return of(ChromeSpec.DEFAULT, false, false, "", host);
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
                WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD, WorkspaceLook.Icons.NEW_UI),
            new ShellLook(!nativeTitle && host.unifiedTitleSupported(), !committed.screenMenuBar(), nativeTitle),
            new PaletteLook(PaletteLook.View.CARD, PaletteLook.Home.TITLE_FIELD),
            new SettingsChrome(!nativeTitle, false, true, hidden, false), Composition.ROWS,
            new DialogMapping(DialogMapping.Kind.AS_REQUESTED, nativeTitle), Motion.STANDARD, new TitleFormat("{context}"), new DockLayoutKey(""),
            new AppPanels("Notifications", Slot.RIGHT_TOP));
    }
}
