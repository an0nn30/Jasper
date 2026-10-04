# Start-up Speed Plan 3B: Contribution Replay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Take plugin start-up off the critical path: the first window shows every plugin's last-known contributions as stand-ins, plugins load their classes on a worker and start one per EDT turn after the first paint, and their real registrations take the stand-ins' places without a visible change.

**Architecture:** The `contributions` model gains owners, a registration sequence, stand-ins and a binding bracket (`bindingStarted`/`bindingFinished`) that suppresses change events and then announces only kinds whose visible data differ. Plugins register through `Contributions.Contributor`, so their entries are recorded as data (`Contributions.contributedBy`), icons reduce to origins recorded where `HostedUi.appearance().icon(…)` mints them, and a per-plugin cache file (`<home>/cache/plugins/<id>.toml`) holds the snapshot with the plugin's discovery, package scan and start-time class list. `PluginPreload` (a start-up worker) discovers from the cache, builds class loaders and loads and links the recorded classes; `PluginRuntime.begin` replays stand-ins before the first window, starts uncached plugins at once, and binds the rest after `FIRST_PAINT`; `EventBus` replays a launch backlog to each plugin's start-time subscriptions.

**Tech Stack:** Java/JBR 25, Gradle wrapper, Swing/FlatLaf 3.7 (`FlatSVGIcon`), tomlj 1.1.1, JUnit 5/AssertJ, the existing `PluginJars` fixture compiler. No new dependency, no SDK API change.

**Spec:** [Start-up speed design](../specs/2026-10-02-jasper-startup-speed-design.md), Part 3, section "3B. Plugins off the critical path: contribution replay". Read it with this plan and [STATUS](../../STATUS.md). This plan builds on Plans 1, 2, 4 and 3A as named in the shared contract (`StartupTrace`, `StartupTrace.Milestone.FIRST_PAINT`/`PLUGINS_BOUND`, `StartupTrace.phase`, `dev.jasper.app.bootstrap.StartupWorkers.submit`).

**Status:** Written and self-reviewed 2026-10-02 against `claude/startup-speed` at `3b1a1544` plus the shared contract, in parallel with Plans 1, 2, 4 and 3A. Revised the same day against the committed Plans 1, 2 and 4 (`92a2860e`) and the revised Plan 3A: Task 7 quotes the two lines Plan 1 adds to `PluginRuntime.start`; Task 8 quotes Plan 3A Task 8's `prepareDesktop`/`startDesktop`/`createApplication` (which carry Plans 1 and 4's lines), uses Plan 1's injectable `trace` field, and covers Plan 4's background mode (`warmUp(done)` rehearsal, `toBackground`, warm reopens). Not executed. No Gradle run, no GUI launch.

**Workspace / execution:** Work in `.claude/worktrees/startup-speed` on `claude/startup-speed` after Plans 1, 2, 4 and 3A are merged there. TDD and one commit per task; record any departure from this text in this banner and in `docs/STATUS.md`.

## Global Constraints

- "`HostedUi` records, per plugin, every UI contribution as data: actions (id, title, keywords, default bindings, context, enabled state, icon), menu sections (target and entry tree), toolbar entries, status items (id, side, priority, text, tooltip, action, visibility), panels (id, title, icon, anchor, toggle action), rail actions, palette scopes (the whole `ScopeSpec`) and file sources (id, heading)."
- "Icons reduce to their origin, recorded where `HostedUi.appearance().icon(…)` mints them: an `IconName`, a `BundledIcon` id, or a plugin-jar SVG path". Never wrap an icon: `AppIcons.forBackground` tests `instanceof ZoneIcon`.
- "The snapshot is taken once per launch when start-up has settled (all plugins bound and three quiet seconds, or at quit if sooner)". This plan adds a 30-second cap for a model that never goes quiet.
- "`<home>/cache/plugins/<id>.toml`, written atomically … Its key is the plugin id and version, every jar's name, size and modification time, and the Jasper version. A mismatched or unreadable file is a miss."
- "Before the first window is built, `ContributionReplay` adds every valid cached plugin's entries in plugin start order, so shortcut conflicts, menu placement and palette tab order are what they were."
- "`changed(kind)` fires only if visible data differ. A stand-in is not a duplicate for its owner, so the SDK's duplicate-id rule is unchanged for everyone else. When `start()` returns, unclaimed stand-ins of that plugin are removed and new registrations stay … one change event per kind." Never close-then-add a stand-in: that rebuilds twice and drops window `Action`s and palette `Command`s in between.
- "After the first paint, plugins start one per EDT turn … plugins with open panels first, then any with queued invocations, then the rest in dependency order. A plugin with no valid cache … starts before the first frame, as today, together with its dependencies."
- "Preloading off the EDT … loading and linking of the recorded class names without initialising them (no static initialisers run)". Only `Class.forName(name, false, loader)` plus a reflective member query (see Task 5) may touch plugin classes off the EDT; never `newInstance`, a static method, or `Class.forName(…, true, …)`.
- "`EventBus` keeps a backlog of everything it delivers from launch until all plugins are bound … recording at delivery time means nothing is delivered twice."
- "A resident started at login starts all plugins before any window exists". `Plugin.start` stays on the UI thread; plugins never start in parallel.
- "SDK. No API change." Only javadoc and docs change in `jasper-sdk`. SDK types appear in the app only inside `dev.jasper.app.plugins`; `verifySdkArchitecture`, `verifyPluginArchitecture` and `verifyApplicationArchitecture` stay green.
- AGENTS.md: no interface without two real implementations (seams are concrete classes, records and JDK functional types); never launch the GUI; source hygiene (escapes such as `"…"`, never raw private-use or control characters); MVP: no compatibility shims.
- Development plugins (`--plugin-dir`) are never cached: they always start before the first window.

## Review Focus

1. **The cache is valid by its key but stale in content** (Remote's keybinding settings file changed a default shortcut; detection added a WSL profile). The window must end with what the plugin registered, one notification per changed kind, shortcuts rebound and icons refreshed. Task 1 (`unclaimedStandInsGoAndNewRegistrationsStay…`) and Task 2 (`aBindingWithAnotherDefaultShortcutOrIcon…`) pin this.
2. **A stand-in action is invoked for a window that closes before its plugin binds, or for a plugin whose start then fails.** The invocation must never run in a dead window, and a failed plugin's queued work is dropped with one notice. Task 7 (`openPanelsComeFirst…`, `aFailedStartRemovesItsStandIns…`) pins this.
3. **Quit during warm-up** (some plugins never bound), and Plan 4's Quit-to-background. A real quit writes bound plugins' caches from the live model, unbound plugins keep their old cache, loaders close and shutdown is not blocked; going to the background is not a quit: plugins keep running and binding, and a later window in the warm process never replays stand-ins. Task 7 (`quittingDuringWarmUp…`) and Task 8 (`goingToTheBackgroundIsNotAQuit…`) pin this.
4. **A corrupt, truncated, foreign-schema, oversized or other-build cache file, or a jar touched with the same size.** Every one is a miss that starts the plugin before the first frame; none throws at start-up. Tasks 4 and 5 pin this.
5. **An event flood before all plugins bound** (a busy tmux retitling every frame). The backlog is bounded; replay delivers the recorded prefix in order and nothing twice. Task 6 (`theBacklogIsDroppedOnceAllPluginsAreBoundAndIsBounded`) pins this.

---

## File map

All production paths are under `jasper-app/src/main/java/dev/jasper/app/` unless they start with `jasper-sdk/` or `docs/`; tests mirror them under `jasper-app/src/test/java/dev/jasper/app/`.

| Files | Responsibility |
|---|---|
| `contributions/Contributed.java` (new), `StandInHooks.java` (new), `StandInScope.java` (new), `Contributions.java`, `ActionEntry.java`, `MenuSection.java`, `StatusEntry.java`, `PanelEntry.java`, `PanelSite.java` | Owners, registration sequence, recording as data, stand-ins, adoption and the binding bracket (Task 1) |
| `plugins/HostedUi.java`, `HostedPalette.java`, `HostedFileSources.java`, `workspace/WindowContributions.java` | Plugins register through their own `Contributor`; windows follow adopted icons and default shortcuts (Task 2) |
| `plugins/IconOrigin.java` (new), `IconOrigins.java` (new), `HostedUi.java`, `HostedContext.java`, `PluginHost.java`, `PluginIconFiles.java`, `platform/AppIcons.java` | Icon origins recorded at mint time; SVG bytes without plugin code; the chooser asks for its sources (Task 3) |
| `platform/AppDirs.java`, `plugins/CachedPlugin.java` (new), `PluginCacheFile.java` (new), `TomlText.java` | The cache file, its key and its atomic I/O (Task 4) |
| `plugins/PluginDiscovery.java`, `PluginLoader.java`, `PluginClassLoader.java`, `PluginPreload.java` (new), `PluginRuntime.java` | Discovery from the cache, loader claims, class preloading on a worker (Task 5) |
| `plugins/EventBus.java` | The launch backlog and its replay (Task 6) |
| `plugins/PluginRuntime.java`, `ContributionReplay.java` (new) | `begin`, the binding schedule, queued invocations, cache writing (Task 7) |
| `application/JasperApplication.java`, `bootstrap/ApplicationBootstrap.java`, package-info files | Wiring into the launch (Task 8) |
| `jasper-sdk/…/plugin/Plugin.java`, `…/plugin/package-info.java`, `…/events/Events.java`, `…/terminal/TerminalEvents.java`, `docs/*.md` | Documentation and STATUS (Task 9) |

---

### Task 1: Owners, recording, stand-ins and binding in the contributions model

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/contributions/Contributed.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/contributions/StandInHooks.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/contributions/StandInScope.java`
- Modify (whole file): `jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java`, `ActionEntry.java`, `MenuSection.java`, `StatusEntry.java`, `PanelEntry.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/PanelSite.java`, `package-info.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/contributions/ContributionStandInsTest.java` (new)

**Interfaces:**
- Consumes: the existing model API, which keeps every signature and behaviour for the application's own (ownerless) registrations.
- Produces:
  - `public sealed interface Contributed<I>` with records `Action<I>(String id, String title, I icon, List<String> keywords, Optional<String> defaultBinding, Optional<String> otherDefault, KeyContext context, boolean enabled)`, `Toolbar<I>(boolean dropdown, I icon, String title, List<String> actionIds)`, `Menu<I>(MenuTarget target, List<MenuEntry> entries)`, `Status<I>(String id, boolean left, int priority, String text, I icon, String tooltip, String actionId, boolean visible, ProgressState progress)`, `Panel<I>(String id, String title, I icon, PanelRegion region, Optional<String> toggleActionId)`, `Rail<I>(String actionId)`, `Scope<I>(String id, String label, I icon, String description, String placeholder, List<PaletteVerb> verbs, boolean monospaceRows, boolean inAll, boolean inRecents, Optional<String> shortcutActionId)`, `FileSource<I>(String id, String heading)`, and `<J> Contributed<J> mapIcons(Function<? super I, ? extends J> icons)`.
  - `public record StandInHooks(BiConsumer<String, Contributions.Invocation> invoked, Runnable panelShown, Runnable scopeUsed)`.
  - `Contributions.contributor(String owner)` returning `Contributions.Contributor` with `addAction(id, title, icon, keywords, defaultBinding, otherDefault, context, handler)`, `addToolbar(ToolbarEntry)`, `addMenuSection(MenuTarget)`, `addStatus(id, left, priority)`, `addPanel(id, title, icon, region, toggleActionId, factory)`, `addRailAction(actionId)`, `addScope(PaletteScope)`, `addFileSource(ChooserSource)`.
  - `Contributions.contributedBy(String owner): List<Contributed<Icon>>`, `addStandIns(String owner, List<Contributed<Icon>> items, StandInHooks hooks)`, `hasStandIns(String owner): boolean`, `bindingStarted(String owner)`, `bindingFinished(String owner)`, `dropStandIns(String owner)`.
  - `ActionEntry.owner()`, `standIn()`; `MenuSection.owner()`, `standIn()`; `StatusEntry.owner()`, `standIn()`; `PanelEntry.owner()`, `standIn()`; `PanelSite.closed()`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/contributions/ContributionStandInsTest.java`:

```java
package dev.jasper.app.contributions;

import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.palette.PaletteContext;
import dev.jasper.app.palette.PaletteResults;
import dev.jasper.app.palette.PaletteRow;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.PaletteTarget;
import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ContributionStandInsTest {
    private static final String OWNER = "dev.x";
    private final Contributions model = new Contributions();
    private final List<Contributions.Kind> changes = new ArrayList<>();
    private final List<String> invoked = new ArrayList<>();
    private final List<String> wanted = new ArrayList<>();
    private final StandInHooks hooks = new StandInHooks((id, invocation) -> invoked.add(id),
        () -> wanted.add("panel"), () -> wanted.add("scope"));
    private final Icon icon = new ImageIcon();

    private static Contributions.Invocation somewhere() { return new Contributions.Invocation(UUID.randomUUID(), Optional.empty()); }

    private static ActionEntry action(Contributions.Contributor plugin, String id, String title, Consumer<Contributions.Invocation> run) {
        return plugin.addAction(id, title, null, List.of(), Optional.empty(), Optional.empty(), KeyContext.GLOBAL, run);
    }

    /** One plugin's start: what it registers, in one order. */
    private void register(Contributions.Contributor plugin, Consumer<Contributions.Invocation> run) {
        plugin.addAction("dev.x.run", "Run", icon, List.of("go"), Optional.of("cmd+alt+j"), Optional.empty(), KeyContext.GLOBAL, run);
        plugin.addToolbar(new ToolbarEntry.Dropdown(icon, "Hosts", List.of("dev.x.run")));
        plugin.addMenuSection(MenuTarget.standard(MenuTarget.Slot.FILE)).set(List.of(new MenuEntry.Item("dev.x.run")));
        plugin.addStatus("dev.x.state", true, 10).setText("Ready");
        plugin.addRailAction("dev.x.run");
    }

    @Test void anOwnersContributionsAreRecordedInRegistrationOrderAsData() {
        model.addAction("app.own", "Own", null, List.of(), Optional.empty(), invocation -> { });
        var plugin = model.contributor(OWNER);
        register(plugin, invocation -> { });
        plugin.addPanel("dev.x.hosts", "Hosts", icon, PanelRegion.LEFT, Optional.empty(), site -> new JLabel());
        action(model.contributor("dev.y"), "dev.y.other", "Other", invocation -> { });

        List<Contributed<Icon>> recorded = model.contributedBy(OWNER);
        assertThat(recorded).containsExactly(
            new Contributed.Action<>("dev.x.run", "Run", icon, List.of("go"), Optional.of("cmd+alt+j"), Optional.empty(), KeyContext.GLOBAL, true),
            new Contributed.Toolbar<>(true, icon, "Hosts", List.of("dev.x.run")),
            new Contributed.Menu<Icon>(MenuTarget.standard(MenuTarget.Slot.FILE), List.of(new MenuEntry.Item("dev.x.run"))),
            new Contributed.Status<Icon>("dev.x.state", true, 10, "Ready", null, null, null, true, null),
            new Contributed.Rail<Icon>("dev.x.run"),
            new Contributed.Panel<>("dev.x.hosts", "Hosts", icon, PanelRegion.LEFT, Optional.empty()));
        assertThat(recorded.getFirst().mapIcons(value -> "origin")).isEqualTo(new Contributed.Action<>("dev.x.run", "Run", "origin",
            List.of("go"), Optional.of("cmd+alt+j"), Optional.empty(), KeyContext.GLOBAL, true));
        assertThat(new Contributed.Toolbar<Icon>(false, null, "", List.of("dev.x.run")).mapIcons(value -> "never").icon()).isNull();
    }

    @Test void aBindingThatRegistersWhatWasRecordedTakesTheStandInsPlacesWithoutAChangeEvent() {
        var first = new Contributions();
        register(first.contributor(OWNER), invocation -> { });
        List<Contributed<Icon>> recorded = first.contributedBy(OWNER);

        model.addAction("app.before", "Before", null, List.of(), Optional.empty(), invocation -> { });
        model.addStandIns(OWNER, recorded, hooks);
        model.addAction("app.after", "After", null, List.of(), Optional.empty(), invocation -> { });
        ActionEntry standIn = model.action("dev.x.run").orElseThrow();
        assertThat(standIn.standIn()).isTrue();
        assertThat(model.hasStandIns(OWNER)).isTrue();
        model.onChanged(changes::add);

        var ran = new ArrayList<Contributions.Invocation>();
        model.bindingStarted(OWNER);
        register(model.contributor(OWNER), ran::add);
        model.bindingFinished(OWNER);

        assertThat(changes).as("nothing a window shows changed").isEmpty();
        assertThat(model.hasStandIns(OWNER)).isFalse();
        assertThat(model.actions()).extracting(ActionEntry::id).containsExactly("app.before", "dev.x.run", "app.after");
        ActionEntry real = model.action("dev.x.run").orElseThrow();
        assertThat(real).isNotSameAs(standIn);
        assertThat(real.standIn()).isFalse();
        assertThat(real.owner()).isEqualTo(OWNER);
        var invocation = somewhere();
        real.invoke(invocation);
        standIn.invoke(somewhere());
        assertThat(ran).containsExactly(invocation);
        assertThat(invoked).isEmpty();
        assertThat(model.contributedBy(OWNER)).isEqualTo(recorded);
    }

    @Test void unkeyedKindsAreClaimedInOrderAndAChangeIsAnnouncedOncePerKind() {
        var first = new Contributions();
        var old = first.contributor(OWNER);
        old.addToolbar(new ToolbarEntry.Button("dev.x.a"));
        old.addToolbar(new ToolbarEntry.Button("dev.x.b"));
        old.addMenuSection(MenuTarget.standard(MenuTarget.Slot.FILE)).set(List.of(new MenuEntry.Item("dev.x.a")));
        old.addMenuSection(MenuTarget.standard(MenuTarget.Slot.FILE)).set(List.of(new MenuEntry.Item("dev.x.b")));
        old.addRailAction("dev.x.a");
        model.addToolbar(new ToolbarEntry.Button("app.find"));
        model.addStandIns(OWNER, first.contributedBy(OWNER), hooks);
        model.addToolbar(new ToolbarEntry.Button("app.settings"));
        model.onChanged(changes::add);

        model.bindingStarted(OWNER);
        var plugin = model.contributor(OWNER);
        plugin.addToolbar(new ToolbarEntry.Button("dev.x.a"));
        plugin.addToolbar(new ToolbarEntry.Button("dev.x.c"));
        plugin.addMenuSection(MenuTarget.standard(MenuTarget.Slot.FILE)).set(List.of(new MenuEntry.Item("dev.x.a")));
        plugin.addMenuSection(MenuTarget.standard(MenuTarget.Slot.FILE)).set(List.of(new MenuEntry.Item("dev.x.c")));
        plugin.addRailAction("dev.x.a");
        model.bindingFinished(OWNER);

        assertThat(model.toolbar()).containsExactly(new ToolbarEntry.Button("app.find"), new ToolbarEntry.Button("dev.x.a"),
            new ToolbarEntry.Button("dev.x.c"), new ToolbarEntry.Button("app.settings"));
        assertThat(model.menus()).extracting(MenuSection::entries).containsExactly(
            List.of(new MenuEntry.Item("dev.x.a")), List.of(new MenuEntry.Item("dev.x.c")));
        assertThat(model.railActions()).containsExactly("dev.x.a");
        assertThat(changes).containsExactly(Contributions.Kind.TOOLBAR, Contributions.Kind.MENUS);
    }

    @Test void unclaimedStandInsGoAndNewRegistrationsStayWithOneEventPerKind() {
        var first = new Contributions();
        var old = first.contributor(OWNER);
        action(old, "dev.x.keep", "Keep", invocation -> { });
        action(old, "dev.x.gone", "Gone", invocation -> { });
        old.addStatus("dev.x.gone-status", false, 5);
        model.addStandIns(OWNER, first.contributedBy(OWNER), hooks);
        model.onChanged(changes::add);

        model.bindingStarted(OWNER);
        var plugin = model.contributor(OWNER);
        action(plugin, "dev.x.keep", "Keep", invocation -> { });
        action(plugin, "dev.x.new", "New", invocation -> { });
        model.bindingFinished(OWNER);

        assertThat(model.actions()).extracting(ActionEntry::id).containsExactly("dev.x.keep", "dev.x.new");
        assertThat(model.status()).isEmpty();
        assertThat(changes).containsExactly(Contributions.Kind.ACTIONS, Contributions.Kind.STATUS);
    }

    @Test void standInActionsKeepTheirRecordedEnablementAndHandInvocationsToTheHooks() {
        var first = new Contributions();
        var old = first.contributor(OWNER);
        action(old, "dev.x.lock", "Lock", invocation -> { }).setEnabled(false);
        action(old, "dev.x.open", "Open", invocation -> { });
        model.addStandIns(OWNER, first.contributedBy(OWNER), hooks);
        assertThat(model.action("dev.x.lock").orElseThrow().enabled()).isFalse();
        model.action("dev.x.lock").orElseThrow().invoke(somewhere());
        model.action("dev.x.open").orElseThrow().invoke(somewhere());
        assertThat(invoked).containsExactly("dev.x.open");
        model.onChanged(changes::add);

        model.bindingStarted(OWNER);
        var plugin = model.contributor(OWNER);
        action(plugin, "dev.x.lock", "Lock", invocation -> { }).setEnabled(false);
        action(plugin, "dev.x.open", "Open", invocation -> { });
        model.bindingFinished(OWNER);
        assertThat(changes).as("disabled right after registering, as recorded").isEmpty();
    }

    @Test void aStandInIsADuplicateForEveryoneButItsOwnerAndOnlyOnce() {
        var first = new Contributions();
        var old = first.contributor(OWNER);
        action(old, "dev.x.run", "Run", invocation -> { });
        old.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Tool"));
        old.addStatus("dev.x.state", true, 1);
        model.addStandIns(OWNER, first.contributedBy(OWNER), hooks);

        var other = model.contributor("dev.y");
        assertThatIllegalArgumentException().isThrownBy(() -> action(other, "dev.x.run", "Run", invocation -> { }));
        assertThatIllegalArgumentException().isThrownBy(() -> other.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Tool")));
        assertThatIllegalArgumentException().isThrownBy(() -> other.addStatus("dev.x.state", true, 1));
        assertThatIllegalArgumentException().isThrownBy(() ->
            model.addAction("dev.x.run", "App", null, List.of(), Optional.empty(), invocation -> { }));

        model.bindingStarted(OWNER);
        var plugin = model.contributor(OWNER);
        action(plugin, "dev.x.run", "Run", invocation -> { });
        assertThatIllegalArgumentException().as("claimed once").isThrownBy(() -> action(plugin, "dev.x.run", "Run", invocation -> { }));
        assertThat(plugin.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Renamed")).target().title()).isEqualTo("Renamed");
        model.bindingFinished(OWNER);
        assertThat(model.menus()).singleElement().extracting(section -> section.target().title()).isEqualTo("Renamed");
        assertThat(model.status()).isEmpty();
    }

    @Test void aStandInPanelShowsAPlaceholderThatReceivesTheRealPanelAfterItsOwnerStarts() {
        var first = new Contributions();
        first.contributor(OWNER).addPanel("dev.x.hosts", "Hosts", icon, PanelRegion.LEFT, Optional.empty(), site -> new JLabel());
        model.addStandIns(OWNER, first.contributedBy(OWNER), hooks);
        PanelEntry entry = model.panels().getFirst();
        assertThat(entry.standIn()).isTrue();
        assertThat(model.action("dev.x.hosts.toggle")).isPresent();
        var site = new PanelSite(UUID.randomUUID(), () -> { }, () -> { }, () -> true);
        JComponent placeholder = entry.factory().apply(site);
        assertThat(placeholder.getComponentCount()).isZero();
        assertThat(wanted).containsExactly("panel");
        model.onChanged(changes::add);

        var real = new JLabel("hosts");
        List<String> heard = new ArrayList<>();
        model.bindingStarted(OWNER);
        PanelEntry claimed = model.contributor(OWNER).addPanel("dev.x.hosts", "Hosts", icon, PanelRegion.LEFT, Optional.empty(), built -> {
            assertThat(built).as("the same site").isSameAs(site);
            built.onVisibility(value -> heard.add("visible:" + value));
            return real;
        });
        assertThat(placeholder.getComponentCount()).as("not before start returns").isZero();
        model.bindingFinished(OWNER);

        assertThat(claimed).isSameAs(entry);
        assertThat(entry.standIn()).isFalse();
        assertThat(placeholder.getComponent(0)).isSameAs(real);
        assertThat(heard).containsExactly("visible:true");
        assertThat(changes).isEmpty();
    }

    @Test void aStandInScopeAnswersWithALoadingRowAndSearchesAgainOnceTheRealScopeBinds() {
        var first = new Contributions();
        first.contributor(OWNER).addScope(PaletteTestSupport.scope("dev.x.hosts", null));
        model.addStandIns(OWNER, first.contributedBy(OWNER), hooks);
        PaletteScope standIn = model.scopes().getFirst();
        var context = new PaletteContext(false, PaletteTarget.none());
        PaletteResults loading = standIn.search("", context);
        assertThat(loading.rows()).singleElement().satisfies(row -> {
            assertThat(row.title()).isEqualTo("Loading dev.x.hosts…");
            assertThat(row.enabled()).isFalse();
        });
        assertThat(wanted).contains("scope");
        int[] searches = {0};
        standIn.onChanged(() -> searches[0]++);
        model.onChanged(changes::add);

        model.bindingStarted(OWNER);
        model.contributor(OWNER).addScope(PaletteTestSupport.scope("dev.x.hosts", null));
        assertThat(searches[0]).as("not before start returns").isZero();
        model.bindingFinished(OWNER);

        assertThat(searches[0]).isEqualTo(1);
        assertThat(model.scopes()).singleElement().isSameAs(standIn);
        assertThat(standIn.search("", context).rows()).extracting(PaletteRow::title).containsExactly("Alpha", "Beta");
        assertThat(changes).isEmpty();
    }

    @Test void aPanelRequestDuringABindingFirstAnnouncesWhatChangedSoFar() {
        model.onChanged(changes::add);
        List<Contributions.Kind> seenAtRequest = new ArrayList<>();
        model.onPanelRequest(request -> seenAtRequest.addAll(changes));
        model.bindingStarted(OWNER);
        model.contributor(OWNER).addPanel("dev.x.new", "New", icon, PanelRegion.LEFT, Optional.empty(), site -> new JLabel());
        model.requestPanel(new Contributions.PanelRequest(UUID.randomUUID(), "dev.x.new", Contributions.PanelRequest.Op.SHOW));
        model.bindingFinished(OWNER);
        assertThat(seenAtRequest).contains(Contributions.Kind.PANELS);
        assertThat(changes).filteredOn(kind -> kind == Contributions.Kind.PANELS).hasSize(1);
        assertThatIllegalStateException().isThrownBy(() -> model.bindingFinished(OWNER));
    }

    @Test void droppingAPluginsStandInsRemovesThemAllAndAnnouncesEachKindOnce() {
        var first = new Contributions();
        register(first.contributor(OWNER), invocation -> { });
        first.contributor(OWNER).addPanel("dev.x.hosts", "Hosts", icon, PanelRegion.LEFT, Optional.empty(), site -> new JLabel());
        model.addStandIns(OWNER, first.contributedBy(OWNER), hooks);
        model.onChanged(changes::add);
        model.dropStandIns(OWNER);
        assertThat(model.hasStandIns(OWNER)).isFalse();
        assertThat(model.actions()).isEmpty();
        assertThat(model.toolbar()).isEmpty();
        assertThat(model.menus()).isEmpty();
        assertThat(model.panels()).isEmpty();
        assertThat(changes).containsExactly(Contributions.Kind.ACTIONS, Contributions.Kind.TOOLBAR, Contributions.Kind.MENUS,
            Contributions.Kind.STATUS, Contributions.Kind.PANELS, Contributions.Kind.RAIL);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.contributions.ContributionStandInsTest'`
Expected: FAIL to compile: `Contributed`, `StandInHooks`, `Contributions.contributor`, `contributedBy`, `addStandIns` and `bindingStarted` do not exist.

- [ ] **Step 3: Create `Contributed.java`**

```java
package dev.jasper.app.contributions;

import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.palette.PaletteVerb;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * One contribution as plain data: what {@link Contributions#contributedBy} records of an owner and what
 * {@link Contributions#addStandIns} replays before that owner starts at the next launch. {@code I} is how an icon is
 * held: a live {@link javax.swing.Icon} in the model, a recorded origin in the plugin cache; null means none. Records
 * compare by value, which is how the model decides whether a binding changed anything a window shows.
 *
 * @param <I> how an icon is held
 */
public sealed interface Contributed<I> permits Contributed.Action, Contributed.Toolbar, Contributed.Menu,
        Contributed.Status, Contributed.Panel, Contributed.Rail, Contributed.Scope, Contributed.FileSource {
    /**
     * The same contribution with each icon converted; a null icon, or one the function maps to null, is none.
     *
     * @param icons converts one icon
     * @param <J> the new icon form
     * @return the converted contribution
     */
    <J> Contributed<J> mapIcons(Function<? super I, ? extends J> icons);

    private static <I, J> J convert(I icon, Function<? super I, ? extends J> icons) {
        return icon == null ? null : icons.apply(icon);
    }

    /**
     * An action.
     *
     * @param id the action id
     * @param title its title
     * @param icon its icon, or null
     * @param keywords its palette keywords
     * @param defaultBinding its default shortcut
     * @param otherDefault its Linux and Windows default, when it names one
     * @param context where its shortcut applies
     * @param enabled whether it is enabled
     * @param <I> how an icon is held
     */
    record Action<I>(String id, String title, I icon, List<String> keywords, Optional<String> defaultBinding,
                     Optional<String> otherDefault, KeyContext context, boolean enabled) implements Contributed<I> {
        /** Copies the keywords; rejects nulls but the icon. */
        public Action {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(title, "title");
            keywords = List.copyOf(keywords);
            Objects.requireNonNull(defaultBinding, "defaultBinding"); Objects.requireNonNull(otherDefault, "otherDefault");
            Objects.requireNonNull(context, "context");
        }

        @Override public <J> Action<J> mapIcons(Function<? super I, ? extends J> icons) {
            return new Action<>(id, title, Contributed.convert(icon, icons), keywords, defaultBinding, otherDefault, context, enabled);
        }
    }

    /**
     * A toolbar button for one action, or a dropdown of several.
     *
     * @param dropdown whether it is a dropdown
     * @param icon a dropdown's icon, or null
     * @param title a dropdown's title, empty for a button
     * @param actionIds the button's one action, or the dropdown's actions
     * @param <I> how an icon is held
     */
    record Toolbar<I>(boolean dropdown, I icon, String title, List<String> actionIds) implements Contributed<I> {
        /** Copies the ids. */
        public Toolbar { Objects.requireNonNull(title, "title"); actionIds = List.copyOf(actionIds); }

        @Override public <J> Toolbar<J> mapIcons(Function<? super I, ? extends J> icons) {
            return new Toolbar<>(dropdown, Contributed.convert(icon, icons), title, actionIds);
        }
    }

    /**
     * A menu section.
     *
     * @param target where it goes
     * @param entries its entries
     * @param <I> how an icon is held
     */
    record Menu<I>(MenuTarget target, List<MenuEntry> entries) implements Contributed<I> {
        /** Copies the entries. */
        public Menu { Objects.requireNonNull(target, "target"); entries = List.copyOf(entries); }

        @Override public <J> Menu<J> mapIcons(Function<? super I, ? extends J> icons) { return new Menu<>(target, entries); }
    }

    /**
     * A status item.
     *
     * @param id its id
     * @param left whether it sits on the left
     * @param priority its order on its side
     * @param text its text
     * @param icon its icon, or null
     * @param tooltip its tooltip, or null
     * @param actionId the action a click runs, or null
     * @param visible whether it shows
     * @param progress its progress presentation, or null for an ordinary item
     * @param <I> how an icon is held
     */
    record Status<I>(String id, boolean left, int priority, String text, I icon, String tooltip, String actionId,
                     boolean visible, ProgressState progress) implements Contributed<I> {
        /** Rejects a null id or text. */
        public Status { Objects.requireNonNull(id, "id"); Objects.requireNonNull(text, "text"); }

        @Override public <J> Status<J> mapIcons(Function<? super I, ? extends J> icons) {
            return new Status<>(id, left, priority, text, Contributed.convert(icon, icons), tooltip, actionId, visible, progress);
        }
    }

    /**
     * A panel.
     *
     * @param id its id
     * @param title its title
     * @param icon its icon, or null
     * @param region where it sits by default
     * @param toggleActionId the action it named as its toggle; empty when the application registers one
     * @param <I> how an icon is held
     */
    record Panel<I>(String id, String title, I icon, PanelRegion region, Optional<String> toggleActionId) implements Contributed<I> {
        /** Rejects nulls but the icon. */
        public Panel {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(title, "title");
            Objects.requireNonNull(region, "region"); Objects.requireNonNull(toggleActionId, "toggleActionId");
        }

        @Override public <J> Panel<J> mapIcons(Function<? super I, ? extends J> icons) {
            return new Panel<>(id, title, Contributed.convert(icon, icons), region, toggleActionId);
        }
    }

    /**
     * An action on the rail.
     *
     * @param actionId the action
     * @param <I> how an icon is held
     */
    record Rail<I>(String actionId) implements Contributed<I> {
        /** Rejects a null id. */
        public Rail { Objects.requireNonNull(actionId, "actionId"); }

        @Override public <J> Rail<J> mapIcons(Function<? super I, ? extends J> icons) { return new Rail<>(actionId); }
    }

    /**
     * A palette scope's tab and verbs.
     *
     * @param id its id
     * @param label its tab label
     * @param icon its icon, or null
     * @param description its one-line description
     * @param placeholder its query placeholder
     * @param verbs its verbs, in order
     * @param monospaceRows whether its rows are monospaced
     * @param inAll whether All searches it
     * @param inRecents whether its rows may show before anything is typed
     * @param shortcutActionId the action whose shortcut switches to it
     * @param <I> how an icon is held
     */
    record Scope<I>(String id, String label, I icon, String description, String placeholder, List<PaletteVerb> verbs,
                    boolean monospaceRows, boolean inAll, boolean inRecents, Optional<String> shortcutActionId) implements Contributed<I> {
        /** Copies the verbs; a missing description or placeholder is empty. */
        public Scope {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(label, "label");
            description = description == null ? "" : description;
            placeholder = placeholder == null ? "" : placeholder;
            verbs = List.copyOf(verbs);
            Objects.requireNonNull(shortcutActionId, "shortcutActionId");
        }

        @Override public <J> Scope<J> mapIcons(Function<? super I, ? extends J> icons) {
            return new Scope<>(id, label, Contributed.convert(icon, icons), description, placeholder, verbs, monospaceRows, inAll,
                inRecents, shortcutActionId);
        }
    }

    /**
     * A file-chooser source.
     *
     * @param id its id
     * @param heading its heading in the chooser
     * @param <I> how an icon is held
     */
    record FileSource<I>(String id, String heading) implements Contributed<I> {
        /** Rejects nulls. */
        public FileSource { Objects.requireNonNull(id, "id"); Objects.requireNonNull(heading, "heading"); }

        @Override public <J> FileSource<J> mapIcons(Function<? super I, ? extends J> icons) { return new FileSource<>(id, heading); }
    }
}
```

- [ ] **Step 4: Create `StandInHooks.java`**

```java
package dev.jasper.app.contributions;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * What one plugin's stand-ins report before the plugin starts, so whoever starts it can bring it forward. EDT.
 *
 * @param invoked a stand-in action was invoked: its id and where; the stand-in runs nothing itself
 * @param panelShown a window showed a stand-in panel, which holds an empty placeholder until the plugin starts
 * @param scopeUsed a palette searched or activated a stand-in scope, which answers with one loading row
 */
public record StandInHooks(BiConsumer<String, Contributions.Invocation> invoked, Runnable panelShown, Runnable scopeUsed) {
    /** Rejects nulls. */
    public StandInHooks {
        Objects.requireNonNull(invoked, "invoked");
        Objects.requireNonNull(panelShown, "panelShown");
        Objects.requireNonNull(scopeUsed, "scopeUsed");
    }
}
```

- [ ] **Step 5: Create `StandInScope.java`**

```java
package dev.jasper.app.contributions;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.palette.PaletteContext;
import dev.jasper.app.palette.PaletteResults;
import dev.jasper.app.palette.PaletteRow;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.PaletteStep;
import dev.jasper.app.palette.PaletteVerb;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.Icon;

/**
 * A recorded palette scope until its plugin registers the real one, and that scope under this identity afterwards:
 * windows keep this instance registered, so its tab never moves. Unbound, it answers every query with one disabled
 * loading row and reports that it is wanted; bound, it forwards everything. EDT.
 */
final class StandInScope implements PaletteScope {
    /** The id of the loading row. */
    static final String LOADING = "jasper.loading";
    private final Contributed.Scope<Icon> recorded;
    private final Runnable used;
    private final List<Runnable> listeners = new ArrayList<>();
    private final Map<Runnable, Subscription> forwarded = new IdentityHashMap<>();
    private PaletteScope delegate;
    private PaletteContext activated;

    StandInScope(Contributed.Scope<Icon> recorded, Runnable used) { this.recorded = recorded; this.used = used; }

    /** From now on the real scope answers; listeners already registered follow it. */
    void bind(PaletteScope real) {
        delegate = real;
        for (Runnable listener : listeners) forwarded.put(listener, follow(real, listener));
    }

    /** After the plugin's start: an activation made meanwhile reaches the real scope, then every open palette searches again. */
    void announce() {
        if (delegate == null) return;
        PaletteContext context = activated;
        activated = null;
        if (context != null) delegate.activated(context);
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private static Subscription follow(PaletteScope scope, Runnable listener) {
        Subscription subscription = scope.onChanged(listener);
        return subscription == null ? new Subscription(() -> { }) : subscription;
    }

    @Override public String id() { return delegate != null ? delegate.id() : recorded.id(); }
    @Override public String label() { return delegate != null ? delegate.label() : recorded.label(); }
    @Override public Icon icon() { return delegate != null ? delegate.icon() : recorded.icon(); }
    @Override public String description() { return delegate != null ? delegate.description() : recorded.description(); }
    @Override public String placeholder() { return delegate != null ? delegate.placeholder() : recorded.placeholder(); }
    @Override public List<PaletteVerb> verbs() { return delegate != null ? delegate.verbs() : recorded.verbs(); }
    @Override public boolean monospaceRows() { return delegate != null ? delegate.monospaceRows() : recorded.monospaceRows(); }
    @Override public boolean inAll() { return delegate != null ? delegate.inAll() : recorded.inAll(); }
    @Override public boolean inRecents() { return delegate != null ? delegate.inRecents() : recorded.inRecents(); }

    @Override public Optional<String> shortcutActionId() {
        return delegate != null ? delegate.shortcutActionId() : recorded.shortcutActionId();
    }

    @Override public void activated(PaletteContext context) {
        if (delegate != null) { delegate.activated(context); return; }
        activated = context;
        used.run();
    }

    @Override public PaletteResults search(String query, PaletteContext context) {
        if (delegate != null) return delegate.search(query, context);
        used.run();
        return new PaletteResults(List.of(new PaletteRow(LOADING, "Loading " + recorded.label() + "…", null, null, null, false, null)),
            null, null);
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        return delegate != null && delegate.available(row, verb, context);
    }

    @Override public PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        return delegate == null ? null : delegate.step(row, verb, context);
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        if (delegate != null) delegate.execute(row, verb, context);
    }

    @Override public Subscription onChanged(Runnable listener) {
        listeners.add(listener);
        if (delegate != null) forwarded.put(listener, follow(delegate, listener));
        return new Subscription(() -> {
            listeners.remove(listener);
            Subscription following = forwarded.remove(listener);
            if (following != null) following.close();
        });
    }
}
```

- [ ] **Step 6: Replace `ActionEntry.java`**

```java
package dev.jasper.app.contributions;

import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.config.KeyBindings;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.Icon;

/**
 * A contributed action. Mutators notify every window; all of them do nothing after {@link #close()}. A stand-in
 * (see {@link Contributions#addStandIns}) hands each invocation to its plugin's scheduler instead of running anything.
 */
public final class ActionEntry {
    private final Contributions model;
    private final String id;
    private final Icon icon;
    private final List<String> keywords;
    private final Optional<String> defaultBinding;
    private final Optional<String> otherDefault;
    private final KeyContext context;
    private final String owner;
    private final boolean standIn;
    private final long sequence;
    private Consumer<Contributions.Invocation> handler;
    private String title;
    private boolean enabled;
    private boolean closed;

    ActionEntry(Contributions model, String id, String title, Icon icon, List<String> keywords,
                Optional<String> defaultBinding, Optional<String> otherDefault, KeyContext context,
                Consumer<Contributions.Invocation> handler, String owner, boolean standIn, long sequence, boolean enabled) {
        this.model = model; this.id = id; this.title = title; this.icon = icon;
        this.keywords = List.copyOf(keywords); this.defaultBinding = defaultBinding; this.otherDefault = otherDefault;
        this.context = context; this.handler = handler; this.owner = owner; this.standIn = standIn;
        this.sequence = sequence; this.enabled = enabled;
    }

    public String id() { return id; }
    public String title() { return title; }
    /** The icon, or null. */
    public Icon icon() { return icon; }
    public List<String> keywords() { return keywords; }
    public Optional<String> defaultBinding() { return defaultBinding; }
    /** The Linux and Windows default when the contributor named one; otherwise it follows from {@link #defaultBinding()}. */
    public Optional<String> otherDefault() { return otherDefault; }
    /** Where the action's shortcut applies. */
    public KeyContext context() { return context; }
    /** The plugin that registered it, or null for the application's own. */
    public String owner() { return owner; }
    /** Whether it replays a recorded action whose plugin has not started yet. */
    public boolean standIn() { return standIn; }
    long sequence() { return sequence; }
    /** The action as the Keymap resolves it; only for namespaced (dotted) ids. */
    public KeyBindings.Extension extension() { return new KeyBindings.Extension(id, defaultBinding, otherDefault, context); }
    public boolean enabled() { return enabled && !closed; }

    /** What a window shows of it, as data. */
    Contributed.Action<Icon> contributed() {
        return new Contributed.Action<>(id, title, icon, keywords, defaultBinding, otherDefault, context, enabled());
    }

    /** Runs the contributor's handler unless the action is disabled or closed. */
    public void invoke(Contributions.Invocation invocation) {
        if (enabled()) handler.accept(invocation);
    }

    public void setEnabled(boolean value) {
        if (closed || enabled == value) return;
        enabled = value;
        model.changed(Contributions.Kind.ACTIONS);
    }

    public void setTitle(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("An action needs a title");
        if (closed || title.equals(value)) return;
        title = value;
        model.changed(Contributions.Kind.ACTIONS);
    }

    public void close() {
        if (closed) return;
        closed = true;
        handler = invocation -> { };
        model.remove(this);
    }

    /** Its plugin's real action took its place: it ends without a removal of its own. */
    void retire() {
        closed = true;
        handler = invocation -> { };
    }
}
```

- [ ] **Step 7: Replace `MenuSection.java`**

```java
package dev.jasper.app.contributions;

import java.util.List;

/** One contributor's entries for one menu target; replaced as a whole. */
public final class MenuSection {
    private final Contributions model;
    private final MenuTarget target;
    private final String owner;
    private final boolean standIn;
    private final long sequence;
    private List<MenuEntry> entries = List.of();
    private boolean closed;

    MenuSection(Contributions model, MenuTarget target, String owner, boolean standIn, long sequence) {
        this.model = model; this.target = target; this.owner = owner; this.standIn = standIn; this.sequence = sequence;
    }

    public MenuTarget target() { return target; }
    public List<MenuEntry> entries() { return entries; }
    /** The plugin that added it, or null for the application's own. */
    public String owner() { return owner; }
    /** Whether it replays a recorded section whose plugin has not started yet. */
    public boolean standIn() { return standIn; }
    long sequence() { return sequence; }

    public void set(List<MenuEntry> next) {
        if (closed) return;
        entries = List.copyOf(next);
        model.changed(Contributions.Kind.MENUS);
    }

    public void close() {
        if (closed) return;
        closed = true;
        entries = List.of();
        model.remove(this);
    }

    /** Its plugin's real section took its place: it ends without a removal of its own. */
    void retire() {
        closed = true;
        entries = List.of();
    }
}
```

- [ ] **Step 8: Replace `StatusEntry.java`**

```java
package dev.jasper.app.contributions;

import javax.swing.Icon;

/** A contributed status item, rendered identically in every window. */
public final class StatusEntry {
    private final Contributions model;
    private final String id;
    private final boolean left;
    private final int priority;
    private final String owner;
    private final boolean standIn;
    private final long sequence;
    private String text = "";
    private Icon icon;
    private String tooltip;
    private String actionId;
    private ProgressState progress;
    private boolean visible = true;
    private boolean closed;

    StatusEntry(Contributions model, String id, boolean left, int priority, String owner, boolean standIn, long sequence) {
        this.model = model; this.id = id; this.left = left; this.priority = priority;
        this.owner = owner; this.standIn = standIn; this.sequence = sequence;
    }

    public String id() { return id; }
    public boolean left() { return left; }
    public int priority() { return priority; }
    public String text() { return text; }
    /** The icon, or null. */
    public Icon icon() { return icon; }
    /** The tooltip, or null. */
    public String tooltip() { return tooltip; }
    /** The contributed action a click invokes, or null. */
    public String actionId() { return actionId; }
    public boolean visible() { return visible && !closed; }
    /** The plugin that added it, or null for the application's own. */
    public String owner() { return owner; }
    /** Whether it replays a recorded item whose plugin has not started yet. */
    public boolean standIn() { return standIn; }
    long sequence() { return sequence; }

    /** Progress presentation, or null for an ordinary status item. */
    public ProgressState progress() { return progress; }
    public void setProgress(ProgressState value) { if (!closed) { progress = java.util.Objects.requireNonNull(value); notifyOwner(); } }

    public void setText(String value) { if (!closed) { text = value == null ? "" : value.replace("\r", "").replace("\n", " "); notifyOwner(); } }
    public void setIcon(Icon value) { if (!closed) { icon = value; notifyOwner(); } }
    public void setTooltip(String value) { if (!closed) { tooltip = value; notifyOwner(); } }
    public void setActionId(String value) { if (!closed) { actionId = value; notifyOwner(); } }
    public void setVisible(boolean value) { if (!closed) { visible = value; notifyOwner(); } }

    /** What a window shows of it, as data. */
    Contributed.Status<Icon> contributed() {
        return new Contributed.Status<>(id, left, priority, text, icon, tooltip, actionId, visible(), progress);
    }

    /** A stand-in's recorded state, set without notifying: it is announced with its addition. */
    void restore(String text, Icon icon, String tooltip, String actionId, boolean visible, ProgressState progress) {
        this.text = text; this.icon = icon; this.tooltip = tooltip; this.actionId = actionId;
        this.visible = visible; this.progress = progress;
    }

    private void notifyOwner() { model.changed(Contributions.Kind.STATUS); }

    public void close() {
        if (closed) return;
        closed = true;
        model.remove(this);
    }

    /** Its plugin's real item took its place: it ends without a removal of its own. */
    void retire() { closed = true; }
}
```

- [ ] **Step 9: Replace `PanelEntry.java` and add `PanelSite.closed()`**

`PanelEntry.java`:

```java
package dev.jasper.app.contributions;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * A contributed panel. Each window asks the factory for its own instance, lazily, on first show. A stand-in's factory
 * builds an empty placeholder in the panel background; when its plugin registers the real panel, the entry keeps its
 * identity, and after the plugin's start returns each live placeholder receives the real panel, whose site hears again
 * that it is visible.
 */
public final class PanelEntry {
    private record Placeholder(PanelSite site, JPanel panel) { }

    private final Contributions model;
    private final String id;
    private final String owner;
    private final long sequence;
    private final List<Placeholder> placeholders = new ArrayList<>();
    private boolean standIn;
    private String title;
    private Icon icon;
    private PanelRegion defaultRegion;
    private Function<PanelSite, JComponent> factory;
    private String toggleActionId;
    private boolean namedToggle;
    private boolean closed;

    PanelEntry(Contributions model, String id, String title, Icon icon, PanelRegion defaultRegion,
               String toggleActionId, boolean namedToggle, Function<PanelSite, JComponent> factory,
               String owner, boolean standIn, long sequence) {
        this.model = model; this.id = id; this.title = title; this.icon = icon;
        this.defaultRegion = defaultRegion; this.factory = factory;
        this.toggleActionId = toggleActionId; this.namedToggle = namedToggle;
        this.owner = owner; this.standIn = standIn; this.sequence = sequence;
    }

    public String id() { return id; }
    public String title() { return title; }
    public Icon icon() { return icon; }
    public PanelRegion defaultRegion() { return defaultRegion; }
    /** The action View > Tools lists: the one the contributor named, or the application's {@code <id>.toggle}. */
    public String toggleActionId() { return toggleActionId; }
    /** Whether the contributor named its own toggle, so the application registered none. */
    public boolean namedToggle() { return namedToggle; }
    /** May return null or throw; the window then shows a placeholder. */
    public Function<PanelSite, JComponent> factory() { return factory; }
    /** The plugin that registered it, or null for the application's own. */
    public String owner() { return owner; }
    /** Whether it replays a recorded panel whose plugin has not started yet. */
    public boolean standIn() { return standIn; }
    long sequence() { return sequence; }

    /** What a window shows of it, as data. */
    Contributed.Panel<Icon> contributed() {
        return new Contributed.Panel<>(id, title, icon, defaultRegion, namedToggle ? Optional.of(toggleActionId) : Optional.empty());
    }

    /** A stand-in's factory: one empty placeholder per window, and {@code shown} so its plugin starts soon. */
    void standInFactory(Runnable shown) {
        factory = site -> {
            var panel = new JPanel(new BorderLayout());
            panel.getAccessibleContext().setAccessibleName(title);
            placeholders.add(new Placeholder(site, panel));
            shown.run();
            return panel;
        };
    }

    /** Its plugin registered the real panel: the entry keeps its identity and place, and takes the real values. */
    void adopt(String title, Icon icon, PanelRegion defaultRegion, String toggleActionId, boolean namedToggle,
               Function<PanelSite, JComponent> factory) {
        this.title = title; this.icon = icon; this.defaultRegion = defaultRegion;
        this.toggleActionId = toggleActionId; this.namedToggle = namedToggle; this.factory = factory;
        standIn = false;
    }

    /** After the plugin's start returned: each live placeholder receives the real panel, whose site hears it is visible. */
    void replacePlaceholders() {
        List<Placeholder> waiting = List.copyOf(placeholders);
        placeholders.clear();
        if (closed || standIn) return;
        for (Placeholder placeholder : waiting) {
            if (placeholder.site().closed()) continue;
            JComponent built = null;
            try { built = factory.apply(placeholder.site()); }
            catch (RuntimeException failure) { /* The contributor's adapter has logged it; the window only needs a placeholder. */ }
            placeholder.panel().add(built != null ? built : new JLabel("This panel could not be loaded.", SwingConstants.CENTER),
                BorderLayout.CENTER);
            placeholder.panel().revalidate();
            placeholder.panel().repaint();
            if (placeholder.site().visible()) placeholder.site().notifyVisibility(true);
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        placeholders.clear();
        model.remove(this);
    }
}
```

In `PanelSite.java`, after `visible()`, add:

```java
    /** Whether the owning window has discarded this instance. */
    public boolean closed() { return done; }
```

- [ ] **Step 10: Replace `Contributions.java`**

```java
package dev.jasper.app.contributions;

import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.files.ChooserSource;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.palette.PaletteScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * What extensions have contributed, in registration order. Windows subscribe and re-render the part
 * that changed. Everything here is EDT-only; callbacks into contributors are their concern to contain.
 *
 * <p>Every entry knows its owner (a plugin id; none for the application's own) and its place in registration order,
 * so {@link #contributedBy} records an owner's contributions as data and {@link #addStandIns} replays them at the next
 * launch before that owner starts. Between {@link #bindingStarted} and {@link #bindingFinished} the owner's real
 * registrations take its stand-ins' places and windows hear nothing; afterwards each kind whose visible data changed
 * is announced once.
 */
public final class Contributions {
    private final ContentRegistry content = new ContentRegistry();
    public ContentRegistry content() { return content; }
    private final QuitGuards quitGuards = new QuitGuards();
    public QuitGuards quitGuards() { return quitGuards; }

    /** Which part changed. */
    public enum Kind { ACTIONS, TOOLBAR, MENUS, STATUS, PANELS, RAIL, SCOPES }

    /** Where an action was invoked: the window, and the pane it concerns if the window has one. */
    public record Invocation(UUID windowId, Optional<UUID> paneId) {
        public Invocation {
            Objects.requireNonNull(windowId, "windowId");
            Objects.requireNonNull(paneId, "paneId");
        }
    }

    /** A request to show, hide or toggle a panel in one window; each window answers only its own. */
    public record PanelRequest(UUID windowId, String panelId, Op op) {
        public enum Op { SHOW, HIDE, TOGGLE }
    }

    /** Asks one window to open its palette. */
    public record PaletteRequest(UUID windowId, String scopeId, Optional<String> query, Optional<String> rowId) {
        public PaletteRequest {
            Objects.requireNonNull(windowId, "windowId"); Objects.requireNonNull(scopeId, "scopeId");
            Objects.requireNonNull(query, "query"); Objects.requireNonNull(rowId, "rowId");
        }
    }

    /** A list entry with no identity of its own: its value, owner (null: the application), stand-in flag and place. */
    private static final class Slot<T> {
        T value;
        final String owner;
        boolean standIn;
        final long sequence;

        Slot(T value, String owner, boolean standIn, long sequence) {
            this.value = value; this.owner = owner; this.standIn = standIn; this.sequence = sequence;
        }

        boolean standsInFor(String plugin) { return standIn && plugin != null && plugin.equals(owner); }

        boolean ownedBy(String plugin) { return !standIn && plugin.equals(owner); }
    }

    private static final System.Logger LOG = System.getLogger(Contributions.class.getName());
    private final Map<String, ActionEntry> actions = new LinkedHashMap<>();
    private final List<Slot<ToolbarEntry>> toolbar = new ArrayList<>();
    private final List<MenuSection> menus = new ArrayList<>();
    private final List<StatusEntry> status = new ArrayList<>();
    private final List<Consumer<Kind>> listeners = new ArrayList<>();
    private final Map<String, PanelEntry> panels = new LinkedHashMap<>();
    private final Map<String, ActionEntry> panelToggles = new LinkedHashMap<>();
    /** Slots, not values: the same action may be placed twice, and removal is by identity. */
    private final List<Slot<String>> railActions = new ArrayList<>();
    private final List<Consumer<PanelRequest>> panelListeners = new ArrayList<>();
    private MenuSection panelsMenu;
    private final List<Slot<PaletteScope>> scopes = new ArrayList<>();
    private final List<Slot<ChooserSource>> fileSources = new ArrayList<>();
    private final List<Consumer<PaletteRequest>> paletteListeners = new ArrayList<>();
    /** The next registration's place in registration order, across every kind. */
    private long sequence;
    /** The owner whose start is running, or null. */
    private String binding;
    private final Map<Kind, Object> baseline = new EnumMap<>(Kind.class);
    private final EnumSet<Kind> pending = EnumSet.noneOf(Kind.class);
    private final List<Runnable> afterBinding = new ArrayList<>();

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Contributions belong to the EDT");
    }

    public ActionEntry addAction(String id, String title, Icon iconOrNull, List<String> keywords,
                                 Optional<String> defaultBinding, Consumer<Invocation> handler) {
        return addAction(id, title, iconOrNull, keywords, defaultBinding, Optional.empty(), KeyContext.GLOBAL, handler);
    }

    /** An action with a separate Linux and Windows default (empty: derived) and the context its shortcut applies in. */
    public ActionEntry addAction(String id, String title, Icon iconOrNull, List<String> keywords, Optional<String> defaultBinding,
                                 Optional<String> otherDefault, KeyContext context, Consumer<Invocation> handler) {
        return registerAction(null, id, title, iconOrNull, keywords, defaultBinding, otherDefault, context, handler);
    }

    public Subscription addToolbar(ToolbarEntry entry) { return registerToolbar(null, entry); }

    public MenuSection addMenuSection(MenuTarget target) { return registerMenu(null, target); }

    public StatusEntry addStatus(String id, boolean left, int priority) { return registerStatus(null, id, left, priority); }

    public PanelEntry addPanel(String id, String title, Icon icon, PanelRegion defaultRegion,
                               Function<PanelSite, JComponent> factory) {
        return addPanel(id, title, icon, defaultRegion, Optional.empty(), factory);
    }

    /** A panel toggled by {@code toggleActionId} when present; otherwise the model registers {@code <id>.toggle}. */
    public PanelEntry addPanel(String id, String title, Icon icon, PanelRegion defaultRegion, Optional<String> toggleActionId,
                               Function<PanelSite, JComponent> factory) {
        return registerPanel(null, id, title, icon, defaultRegion, toggleActionId, factory);
    }

    public Subscription addRailAction(String actionId) { return registerRail(null, actionId); }

    /**
     * A scope every window registers in its palette, one instance shared by all of them. Ids are
     * unique among contributed scopes; a window also refuses a clash with one of its built-in ids.
     */
    public Subscription addScope(PaletteScope scope) { return registerScope(null, scope); }

    /** A place the file chooser offers besides the local disk; ids are unique. */
    public Subscription addFileSource(ChooserSource source) { return registerFileSource(null, source); }

    /**
     * Registration on behalf of one plugin.
     *
     * @param owner the plugin id
     * @return additions recorded as the plugin's own, which take the places of its stand-ins
     */
    public Contributor contributor(String owner) { return new Contributor(owner); }

    /**
     * One plugin's registrations. Each is recorded as the plugin's. While the plugin has stand-ins, an addition with a
     * stand-in's key takes that stand-in's place; for everyone else the key stays taken. The key is the id for actions,
     * status items, panels, scopes and top-level menus, and for toolbar entries, rail actions and the other menu
     * sections the plugin's next unclaimed stand-in of that kind (and menu target), in order.
     */
    public final class Contributor {
        private final String owner;

        private Contributor(String owner) { this.owner = Objects.requireNonNull(owner, "owner"); }

        public ActionEntry addAction(String id, String title, Icon iconOrNull, List<String> keywords, Optional<String> defaultBinding,
                                     Optional<String> otherDefault, KeyContext context, Consumer<Invocation> handler) {
            return registerAction(owner, id, title, iconOrNull, keywords, defaultBinding, otherDefault, context, handler);
        }

        public Subscription addToolbar(ToolbarEntry entry) { return registerToolbar(owner, entry); }

        public MenuSection addMenuSection(MenuTarget target) { return registerMenu(owner, target); }

        public StatusEntry addStatus(String id, boolean left, int priority) { return registerStatus(owner, id, left, priority); }

        public PanelEntry addPanel(String id, String title, Icon icon, PanelRegion defaultRegion, Optional<String> toggleActionId,
                                   Function<PanelSite, JComponent> factory) {
            return registerPanel(owner, id, title, icon, defaultRegion, toggleActionId, factory);
        }

        public Subscription addRailAction(String actionId) { return registerRail(owner, actionId); }

        public Subscription addScope(PaletteScope scope) { return registerScope(owner, scope); }

        public Subscription addFileSource(ChooserSource source) { return registerFileSource(owner, source); }
    }

    public List<ActionEntry> actions() { return List.copyOf(actions.values()); }
    public Optional<ActionEntry> action(String id) { return Optional.ofNullable(actions.get(id)); }
    public List<ToolbarEntry> toolbar() { return toolbar.stream().map(slot -> slot.value).toList(); }
    public List<MenuSection> menus() { return List.copyOf(menus); }

    /** Ascending priority; equal priorities keep registration order. */
    public List<StatusEntry> status() {
        return status.stream().sorted(Comparator.comparingInt(StatusEntry::priority)).toList();
    }

    public List<PanelEntry> panels() { return List.copyOf(panels.values()); }
    public List<String> railActions() { return railActions.stream().map(slot -> slot.value).toList(); }
    public List<PaletteScope> scopes() { return scopes.stream().map(slot -> slot.value).toList(); }
    public List<ChooserSource> fileSources() { return fileSources.stream().map(slot -> slot.value).toList(); }

    public Subscription onChanged(Consumer<Kind> listener) {
        requireEdt();
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    public Subscription onPaletteRequest(Consumer<PaletteRequest> listener) {
        requireEdt();
        paletteListeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> paletteListeners.remove(listener));
    }

    public void requestPalette(PaletteRequest request) {
        requireEdt();
        flush();
        for (Consumer<PaletteRequest> listener : List.copyOf(paletteListeners)) {
            try { listener.accept(request); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A palette request listener failed", failure); }
        }
    }

    public Subscription onPanelRequest(Consumer<PanelRequest> listener) {
        requireEdt();
        panelListeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> panelListeners.remove(listener));
    }

    public void requestPanel(PanelRequest request) {
        requireEdt();
        flush();
        for (Consumer<PanelRequest> listener : List.copyOf(panelListeners)) {
            try { listener.accept(request); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A panel request listener failed", failure); }
        }
    }

    /**
     * An owner's contributions as data, in registration order across every kind: neither stand-ins nor the application's
     * own toggle for a panel that names none, which its panel brings back.
     *
     * @param owner a plugin id
     * @return what a replay at the next launch would show
     */
    public List<Contributed<Icon>> contributedBy(String owner) {
        requireEdt();
        Objects.requireNonNull(owner, "owner");
        record Placed(long sequence, Contributed<Icon> item) { }
        List<Placed> placed = new ArrayList<>();
        for (ActionEntry entry : actions.values())
            if (!entry.standIn() && owner.equals(entry.owner()) && !panelToggles.containsValue(entry))
                placed.add(new Placed(entry.sequence(), entry.contributed()));
        for (Slot<ToolbarEntry> slot : toolbar) if (slot.ownedBy(owner)) placed.add(new Placed(slot.sequence, toolbarData(slot.value)));
        for (MenuSection section : menus)
            if (!section.standIn() && owner.equals(section.owner()))
                placed.add(new Placed(section.sequence(), new Contributed.Menu<>(section.target(), section.entries())));
        for (StatusEntry entry : status)
            if (!entry.standIn() && owner.equals(entry.owner())) placed.add(new Placed(entry.sequence(), entry.contributed()));
        for (PanelEntry entry : panels.values())
            if (!entry.standIn() && owner.equals(entry.owner())) placed.add(new Placed(entry.sequence(), entry.contributed()));
        for (Slot<String> slot : railActions) if (slot.ownedBy(owner)) placed.add(new Placed(slot.sequence, new Contributed.Rail<>(slot.value)));
        for (Slot<PaletteScope> slot : scopes) if (slot.ownedBy(owner)) placed.add(new Placed(slot.sequence, scopeData(slot.value)));
        for (Slot<ChooserSource> slot : fileSources)
            if (slot.ownedBy(owner)) placed.add(new Placed(slot.sequence, new Contributed.FileSource<>(slot.value.id(), slot.value.heading())));
        placed.sort(Comparator.comparingLong(Placed::sequence));
        return placed.stream().map(Placed::item).toList();
    }

    /**
     * Adds an owner's recorded contributions before it starts, in the order given, each standing in for it until its
     * real registration takes the stand-in's place. A stand-in action hands invocations to {@code hooks}; a stand-in
     * panel's factory builds an empty placeholder; a stand-in scope answers with a loading row. File sources get no
     * stand-in: a chooser that names one starts its plugin instead. A recorded item that cannot be added is skipped.
     *
     * @param owner the plugin id
     * @param items what {@link #contributedBy} recorded, with live icons
     * @param hooks where the stand-ins report that they are wanted
     */
    public void addStandIns(String owner, List<Contributed<Icon>> items, StandInHooks hooks) {
        requireEdt();
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(hooks, "hooks");
        for (Contributed<Icon> item : items) {
            try { addStandIn(owner, item, hooks); }
            catch (RuntimeException invalid) {
                LOG.log(System.Logger.Level.WARNING, "A recorded contribution of " + owner + " was not replayed", invalid);
            }
        }
    }

    /** Whether the owner still has stand-ins in the model. */
    public boolean hasStandIns(String owner) {
        requireEdt();
        return actions.values().stream().anyMatch(entry -> entry.standIn() && owner.equals(entry.owner()))
            || toolbar.stream().anyMatch(slot -> slot.standsInFor(owner))
            || menus.stream().anyMatch(section -> section.standIn() && owner.equals(section.owner()))
            || status.stream().anyMatch(entry -> entry.standIn() && owner.equals(entry.owner()))
            || panels.values().stream().anyMatch(entry -> entry.standIn() && owner.equals(entry.owner()))
            || railActions.stream().anyMatch(slot -> slot.standsInFor(owner))
            || scopes.stream().anyMatch(slot -> slot.standsInFor(owner));
    }

    /**
     * The owner's start is about to run. Until {@link #bindingFinished}, windows hear nothing, except that a panel or
     * palette request first announces what has changed so far. Bindings do not nest.
     *
     * @param owner the plugin id
     */
    public void bindingStarted(String owner) {
        requireEdt();
        Objects.requireNonNull(owner, "owner");
        if (binding != null) throw new IllegalStateException("Already binding " + binding);
        binding = owner;
        for (Kind kind : Kind.values()) baseline.put(kind, visible(kind));
    }

    /**
     * The owner's start returned or failed: its unclaimed stand-ins go, each kind whose visible data changed is
     * announced once, then placeholders receive their real panels and stand-in scopes ask their palettes to search again.
     *
     * @param owner the plugin id given to {@link #bindingStarted}
     */
    public void bindingFinished(String owner) {
        requireEdt();
        if (binding == null || !binding.equals(owner)) throw new IllegalStateException("Not binding " + owner);
        removeStandIns(owner);
        binding = null;
        EnumSet<Kind> changed = EnumSet.copyOf(pending);
        pending.clear();
        for (Kind kind : Kind.values())
            if (changed.contains(kind) && !Objects.equals(baseline.get(kind), visible(kind))) fire(kind);
        baseline.clear();
        List<Runnable> deferred = List.copyOf(afterBinding);
        afterBinding.clear();
        for (Runnable task : deferred) {
            try { task.run(); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "Finishing the start of " + owner + " failed", failure); }
        }
    }

    /** Removes an owner's stand-ins when it will not start at all, announcing each kind that changed once. */
    public void dropStandIns(String owner) {
        bindingStarted(owner);
        bindingFinished(owner);
    }

    private ActionEntry registerAction(String owner, String id, String title, Icon icon, List<String> keywords,
                                       Optional<String> defaultBinding, Optional<String> otherDefault, KeyContext context,
                                       Consumer<Invocation> handler) {
        requireEdt();
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(otherDefault, "otherDefault");
        Objects.requireNonNull(context, "context");
        ActionEntry existing = actions.get(id);
        boolean claim = existing != null && existing.standIn() && owner != null && owner.equals(existing.owner());
        if (existing != null && !claim) throw new IllegalArgumentException("Action already registered: " + id);
        var entry = new ActionEntry(this, id, title, icon, keywords, defaultBinding, otherDefault, context, handler, owner, false,
            claim ? existing.sequence() : sequence++, true);
        if (claim) existing.retire();
        // A claim keeps the stand-in's place: a LinkedHashMap keeps a replaced key's position.
        actions.put(id, entry);
        changed(Kind.ACTIONS);
        return entry;
    }

    private Subscription registerToolbar(String owner, ToolbarEntry entry) {
        requireEdt();
        Objects.requireNonNull(entry, "entry");
        Slot<ToolbarEntry> slot = firstStandIn(toolbar, owner);
        if (slot != null) { slot.value = entry; slot.standIn = false; }
        else toolbar.add(slot = new Slot<>(entry, owner, false, sequence++));
        changed(Kind.TOOLBAR);
        Slot<ToolbarEntry> placed = slot;
        // Identity removal: two equal records are still two toolbar controls.
        return new Subscription(() -> { if (toolbar.remove(placed)) changed(Kind.TOOLBAR); });
    }

    private MenuSection registerMenu(String owner, MenuTarget target) {
        requireEdt();
        Objects.requireNonNull(target, "target");
        MenuSection claimed = null;
        for (MenuSection existing : menus)
            if (existing.standIn() && owner != null && owner.equals(existing.owner()) && sameSlot(existing.target(), target)) {
                claimed = existing;
                break;
            }
        if (target.type() == MenuTarget.Type.TOP_LEVEL) requireFreeMenu(target.key(), claimed);
        var section = new MenuSection(this, target, owner, false, claimed == null ? sequence++ : claimed.sequence());
        if (claimed == null) menus.add(section);
        else { menus.set(menus.indexOf(claimed), section); claimed.retire(); }
        changed(Kind.MENUS);
        return section;
    }

    /** Top-level menus are keyed by id whatever their title; the other sections by where they go. */
    private static boolean sameSlot(MenuTarget recorded, MenuTarget registered) {
        return recorded.type() == registered.type() && recorded.key().equals(registered.key())
            && (recorded.type() == MenuTarget.Type.TOP_LEVEL || recorded.title().equals(registered.title()));
    }

    private void requireFreeMenu(String key, MenuSection except) {
        for (MenuSection existing : menus)
            if (existing != except && existing.target().type() == MenuTarget.Type.TOP_LEVEL && existing.target().key().equals(key))
                throw new IllegalArgumentException("Menu already exists: " + key);
    }

    private StatusEntry registerStatus(String owner, String id, boolean left, int priority) {
        requireEdt();
        StatusEntry claimed = null;
        for (StatusEntry existing : status) {
            if (!existing.id().equals(id)) continue;
            if (existing.standIn() && owner != null && owner.equals(existing.owner())) { claimed = existing; break; }
            throw new IllegalArgumentException("Status item already exists: " + id);
        }
        var entry = new StatusEntry(this, id, left, priority, owner, false, claimed == null ? sequence++ : claimed.sequence());
        if (claimed == null) status.add(entry);
        else { status.set(status.indexOf(claimed), entry); claimed.retire(); }
        changed(Kind.STATUS);
        return entry;
    }

    private PanelEntry registerPanel(String owner, String id, String title, Icon icon, PanelRegion defaultRegion,
                                     Optional<String> toggleActionId, Function<PanelSite, JComponent> factory) {
        requireEdt();
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(defaultRegion, "defaultRegion");
        PanelEntry existing = panels.get(id);
        if (existing != null && !(existing.standIn() && owner != null && owner.equals(existing.owner())))
            throw new IllegalArgumentException("Panel already registered: " + id);
        String toggle = toggleActionId.orElse(id + ".toggle");
        PanelEntry entry;
        if (existing != null) {
            entry = existing;
            entry.adopt(title, icon, defaultRegion, toggle, toggleActionId.isPresent(), factory);
            retoggle(entry, title, icon, toggleActionId.isPresent());
            afterBinding(entry::replacePlaceholders);
        } else {
            entry = new PanelEntry(this, id, title, icon, defaultRegion, toggle, toggleActionId.isPresent(), factory, owner, false, sequence++);
            panels.put(id, entry);
            if (toggleActionId.isEmpty()) panelToggles.put(id, autoToggle(id, title, icon, null));
        }
        rebuildPanelsMenu();
        changed(Kind.PANELS);
        return entry;
    }

    /** A claimed panel's toggle follows what its plugin registered now: its own action, or the application's. */
    private void retoggle(PanelEntry entry, String title, Icon icon, boolean named) {
        ActionEntry auto = panelToggles.get(entry.id());
        if (named) {
            if (auto != null) { panelToggles.remove(entry.id()); auto.close(); }
        } else if (auto == null || !auto.title().equals("Toggle " + title) || !Objects.equals(auto.icon(), icon)) {
            panelToggles.put(entry.id(), autoToggle(entry.id(), title, icon, auto));
        }
    }

    /** The application's toggle for a panel that names none; it takes {@code previous}'s place when given. */
    private ActionEntry autoToggle(String panelId, String title, Icon icon, ActionEntry previous) {
        String id = panelId + ".toggle";
        if (previous == null && actions.containsKey(id)) throw new IllegalArgumentException("Action already registered: " + id);
        var entry = new ActionEntry(this, id, "Toggle " + title, icon, List.of("panel", "show", "hide"), Optional.empty(),
            Optional.empty(), KeyContext.GLOBAL,
            invocation -> requestPanel(new PanelRequest(invocation.windowId(), panelId, PanelRequest.Op.TOGGLE)),
            null, false, previous == null ? sequence++ : previous.sequence(), true);
        if (previous != null) previous.retire();
        actions.put(id, entry);
        changed(Kind.ACTIONS);
        return entry;
    }

    private Subscription registerRail(String owner, String actionId) {
        requireEdt();
        Objects.requireNonNull(actionId, "actionId");
        Slot<String> slot = firstStandIn(railActions, owner);
        if (slot != null) { slot.value = actionId; slot.standIn = false; }
        else railActions.add(slot = new Slot<>(actionId, owner, false, sequence++));
        changed(Kind.RAIL);
        Slot<String> placed = slot;
        return new Subscription(() -> { if (railActions.remove(placed)) changed(Kind.RAIL); });
    }

    private Subscription registerScope(String owner, PaletteScope scope) {
        requireEdt();
        String id = PaletteScope.requireValidId(Objects.requireNonNull(scope, "scope").id());
        if (scope.verbs().isEmpty()) throw new IllegalArgumentException("Scope needs at least one verb: " + id);
        Slot<PaletteScope> claimed = null;
        for (Slot<PaletteScope> slot : scopes) {
            if (!slot.value.id().equals(id)) continue;
            if (slot.standsInFor(owner)) { claimed = slot; break; }
            throw new IllegalArgumentException("Scope already contributed: " + id);
        }
        Slot<PaletteScope> placed;
        if (claimed != null) {
            StandInScope standIn = (StandInScope) claimed.value;
            standIn.bind(scope);
            claimed.standIn = false;
            afterBinding(standIn::announce);
            placed = claimed;
        } else scopes.add(placed = new Slot<>(scope, owner, false, sequence++));
        changed(Kind.SCOPES);
        return new Subscription(() -> { if (scopes.remove(placed)) changed(Kind.SCOPES); });
    }

    private Subscription registerFileSource(String owner, ChooserSource source) {
        requireEdt();
        Objects.requireNonNull(source, "source");
        for (Slot<ChooserSource> existing : fileSources)
            if (existing.value.id().equals(source.id())) throw new IllegalArgumentException("File source already contributed: " + source.id());
        var slot = new Slot<>(source, owner, false, sequence++);
        fileSources.add(slot);
        return new Subscription(() -> fileSources.remove(slot));
    }

    private void addStandIn(String owner, Contributed<Icon> item, StandInHooks hooks) {
        switch (item) {
            case Contributed.Action<Icon> recorded -> {
                String id = recorded.id();
                if (actions.containsKey(id)) throw new IllegalArgumentException("Action already registered: " + id);
                actions.put(id, new ActionEntry(this, id, recorded.title(), recorded.icon(), recorded.keywords(),
                    recorded.defaultBinding(), recorded.otherDefault(), recorded.context(),
                    invocation -> hooks.invoked().accept(id, invocation), owner, true, sequence++, recorded.enabled()));
                changed(Kind.ACTIONS);
            }
            case Contributed.Toolbar<Icon> recorded -> {
                ToolbarEntry entry = recorded.dropdown()
                    ? new ToolbarEntry.Dropdown(recorded.icon(), recorded.title(), recorded.actionIds())
                    : new ToolbarEntry.Button(recorded.actionIds().getFirst());
                toolbar.add(new Slot<>(entry, owner, true, sequence++));
                changed(Kind.TOOLBAR);
            }
            case Contributed.Menu<Icon> recorded -> {
                if (recorded.target().type() == MenuTarget.Type.TOP_LEVEL) requireFreeMenu(recorded.target().key(), null);
                var section = new MenuSection(this, recorded.target(), owner, true, sequence++);
                menus.add(section);
                section.set(recorded.entries());
            }
            case Contributed.Status<Icon> recorded -> {
                for (StatusEntry existing : status)
                    if (existing.id().equals(recorded.id())) throw new IllegalArgumentException("Status item already exists: " + recorded.id());
                var entry = new StatusEntry(this, recorded.id(), recorded.left(), recorded.priority(), owner, true, sequence++);
                entry.restore(recorded.text(), recorded.icon(), recorded.tooltip(), recorded.actionId(), recorded.visible(), recorded.progress());
                status.add(entry);
                changed(Kind.STATUS);
            }
            case Contributed.Panel<Icon> recorded -> {
                String id = recorded.id();
                if (panels.containsKey(id)) throw new IllegalArgumentException("Panel already registered: " + id);
                var entry = new PanelEntry(this, id, recorded.title(), recorded.icon(), recorded.region(),
                    recorded.toggleActionId().orElse(id + ".toggle"), recorded.toggleActionId().isPresent(), null, owner, true, sequence++);
                entry.standInFactory(hooks.panelShown());
                panels.put(id, entry);
                if (recorded.toggleActionId().isEmpty()) panelToggles.put(id, autoToggle(id, recorded.title(), recorded.icon(), null));
                rebuildPanelsMenu();
                changed(Kind.PANELS);
            }
            case Contributed.Rail<Icon> recorded -> {
                railActions.add(new Slot<>(recorded.actionId(), owner, true, sequence++));
                changed(Kind.RAIL);
            }
            case Contributed.Scope<Icon> recorded -> {
                PaletteScope.requireValidId(recorded.id());
                if (recorded.verbs().isEmpty()) throw new IllegalArgumentException("Scope needs at least one verb: " + recorded.id());
                for (Slot<PaletteScope> slot : scopes)
                    if (slot.value.id().equals(recorded.id())) throw new IllegalArgumentException("Scope already contributed: " + recorded.id());
                scopes.add(new Slot<>(new StandInScope(recorded, hooks.scopeUsed()), owner, true, sequence++));
                changed(Kind.SCOPES);
            }
            case Contributed.FileSource<Icon> recorded -> { /* No stand-in: a chooser naming it starts its plugin instead. */ }
        }
    }

    private void removeStandIns(String owner) {
        for (ActionEntry entry : List.copyOf(actions.values())) if (entry.standIn() && owner.equals(entry.owner())) entry.close();
        if (toolbar.removeIf(slot -> slot.standsInFor(owner))) changed(Kind.TOOLBAR);
        for (MenuSection section : List.copyOf(menus)) if (section.standIn() && owner.equals(section.owner())) section.close();
        for (StatusEntry entry : List.copyOf(status)) if (entry.standIn() && owner.equals(entry.owner())) entry.close();
        for (PanelEntry entry : List.copyOf(panels.values())) if (entry.standIn() && owner.equals(entry.owner())) entry.close();
        if (railActions.removeIf(slot -> slot.standsInFor(owner))) changed(Kind.RAIL);
        if (scopes.removeIf(slot -> slot.standsInFor(owner))) changed(Kind.SCOPES);
    }

    private static <T> Slot<T> firstStandIn(List<Slot<T>> slots, String owner) {
        for (Slot<T> slot : slots) if (slot.standsInFor(owner)) return slot;
        return null;
    }

    private void afterBinding(Runnable task) {
        if (binding != null) afterBinding.add(task); else task.run();
    }

    private static Contributed<Icon> toolbarData(ToolbarEntry entry) {
        return switch (entry) {
            case ToolbarEntry.Button button -> new Contributed.Toolbar<Icon>(false, null, "", List.of(button.actionId()));
            case ToolbarEntry.Dropdown dropdown -> new Contributed.Toolbar<>(true, dropdown.icon(), dropdown.title(), dropdown.actionIds());
        };
    }

    private static Contributed<Icon> scopeData(PaletteScope scope) {
        return new Contributed.Scope<>(scope.id(), scope.label(), scope.icon(), scope.description(), scope.placeholder(), scope.verbs(),
            scope.monospaceRows(), scope.inAll(), scope.inRecents(), scope.shortcutActionId());
    }

    /** Everything a window shows of one kind, as data: equal values mean a window would render the same. */
    private Object visible(Kind kind) {
        return switch (kind) {
            case ACTIONS -> actions.values().stream().map(ActionEntry::contributed).toList();
            case TOOLBAR -> toolbar();
            case MENUS -> menus.stream().map(section -> new Contributed.Menu<Icon>(section.target(), section.entries())).toList();
            case STATUS -> status.stream().map(StatusEntry::contributed).toList();
            case PANELS -> panels.values().stream().map(PanelEntry::contributed).toList();
            case RAIL -> railActions();
            case SCOPES -> scopes.stream().map(slot -> scopeData(slot.value)).toList();
        };
    }

    /** While binding, announces what has changed so far, so a request reaches windows that know the newest state. */
    private void flush() {
        if (binding == null || pending.isEmpty()) return;
        EnumSet<Kind> changed = EnumSet.copyOf(pending);
        pending.clear();
        for (Kind kind : changed) {
            Object now = visible(kind);
            if (Objects.equals(baseline.get(kind), now)) continue;
            baseline.put(kind, now);
            fire(kind);
        }
    }

    void remove(PanelEntry entry) {
        if (!panels.remove(entry.id(), entry)) return;
        ActionEntry toggle = panelToggles.remove(entry.id());
        if (toggle != null) toggle.close();
        rebuildPanelsMenu();
        changed(Kind.PANELS);
    }

    /** One app-owned View section, "Tools", listing every panel's toggle action; gone when there are no panels. */
    private void rebuildPanelsMenu() {
        if (panels.isEmpty()) {
            if (panelsMenu != null) { panelsMenu.close(); panelsMenu = null; }
            return;
        }
        if (panelsMenu == null) panelsMenu = addMenuSection(MenuTarget.standard(MenuTarget.Slot.VIEW));
        List<MenuEntry> toggles = new ArrayList<>();
        for (PanelEntry panel : panels.values()) toggles.add(new MenuEntry.Item(panel.toggleActionId()));
        panelsMenu.set(List.of(new MenuEntry.Submenu("Tools", toggles)));
    }

    void remove(ActionEntry entry) { if (actions.remove(entry.id(), entry)) changed(Kind.ACTIONS); }
    void remove(MenuSection section) { if (menus.remove(section)) changed(Kind.MENUS); }
    void remove(StatusEntry entry) { if (status.remove(entry)) changed(Kind.STATUS); }

    void changed(Kind kind) {
        if (binding != null) { pending.add(kind); return; }
        fire(kind);
    }

    private void fire(Kind kind) {
        for (Consumer<Kind> listener : List.copyOf(listeners)) {
            try { listener.accept(kind); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A contributions listener failed", failure); }
        }
    }
}
```

In `contributions/package-info.java`, replace the first sentence block with:

```java
/**
 * EDT model of what extensions contribute to the chrome: actions, toolbar entries, menu sections,
 * status entries, panels, palette scopes and file-chooser sources, in app-native types. The plugin runtime writes it, every window renders it; neither
 * sees the other. The application owns the single instance; contributors close their own entries. Entries record their
 * owner and registration order, so a plugin's contributions replay as stand-ins at the next launch until it starts.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.commands, dev.jasper.app.config, dev.jasper.app.files, dev.jasper.app.lifecycle, dev.jasper.app.palette.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.contributions;
```

- [ ] **Step 11: Run the new and the existing model tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.contributions.*'`
Expected: PASS, including the unchanged `ContributionsTest` and `PanelContributionsTest`.

- [ ] **Step 12: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/contributions jasper-app/src/test/java/dev/jasper/app/contributions/ContributionStandInsTest.java
git commit -m "$(cat <<'EOF'
feat(contributions): record owners, replay stand-ins and bind without churn

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Plugins register as themselves; windows follow what binding changed

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java`, `HostedPalette.java`, `HostedFileSources.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/WindowContributionsTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/WindowPanelsTest.java`

**Interfaces:**
- Consumes: Task 1's `Contributions.contributor(String)`, `Contributor.add*`, `addStandIns`, `bindingStarted`/`bindingFinished`, `StandInHooks`.
- Produces: every plugin registration carries its plugin id as owner; `WindowContributions` rebinds a window when the contributed extensions change without a structural change, and refreshes action icons on every sync.

- [ ] **Step 1: Write the failing tests**

Add to `HostedUiTest` (it already runs on the EDT through `EdtTestExtension`):

```java
    @Test void registrationsAreThePluginsOwnAndTakeTheirStandInsPlaces() {
        var recorded = new Contributions();
        recorded.contributor("dev.x.tool").addAction("dev.x.tool.run", "Run", null, List.of(), Optional.empty(), Optional.empty(),
            KeyContext.GLOBAL, invocation -> { });
        model.addStandIns("dev.x.tool", recorded.contributedBy("dev.x.tool"),
            new dev.jasper.app.contributions.StandInHooks((id, invocation) -> { }, () -> { }, () -> { }));
        model.bindingStarted("dev.x.tool");
        ui.actions().register(ActionSpec.of("dev.x.tool.run", "Run"), context -> { });
        ui.statusBar().add(new StatusItemSpec("dev.x.tool.state", Side.LEFT, 1));
        model.bindingFinished("dev.x.tool");
        assertThat(model.action("dev.x.tool.run")).get().satisfies(action -> {
            assertThat(action.owner()).isEqualTo("dev.x.tool");
            assertThat(action.standIn()).isFalse();
        });
        assertThat(model.contributedBy("dev.x.tool")).hasSize(2);
    }
```

Add to `WindowContributionsTest` (imports: `dev.jasper.app.commands.KeyContext`, `dev.jasper.app.contributions.StandInHooks`, `javax.swing.ImageIcon`). `cmd+alt+k` is free in both keymaps (off macOS it is `ctrl+alt+shift+k`):

```java
    @Test void aBindingWithAnotherDefaultShortcutOrIconUpdatesTheWindowAndKeepsItsCommand() throws Exception {
        edt(() -> {
            var recorded = new Contributions();
            recorded.contributor("dev.x").addAction("dev.x.run", "Run", new ImageIcon(), List.of(), Optional.of("cmd+alt+j"),
                Optional.empty(), KeyContext.GLOBAL, invocation -> { });
            var model = new Contributions();
            model.addStandIns("dev.x", recorded.contributedBy("dev.x"), new StandInHooks((id, invocation) -> { }, () -> { }, () -> { }));
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model);
            Command command = owner.commands().find("dev.x.run").orElseThrow();
            assertThat(command.action().getValue(Action.ACCELERATOR_KEY)).isEqualTo(stroke("cmd+alt+j"));

            var icon = new ImageIcon();
            List<Contributions.Invocation> seen = new ArrayList<>();
            model.bindingStarted("dev.x");
            model.contributor("dev.x").addAction("dev.x.run", "Run", icon, List.of(), Optional.of("cmd+alt+k"), Optional.empty(),
                KeyContext.GLOBAL, seen::add);
            model.bindingFinished("dev.x");

            assertThat(owner.commands().find("dev.x.run")).containsSame(command);
            assertThat(command.action().getValue(Action.ACCELERATOR_KEY)).isEqualTo(stroke("cmd+alt+k"));
            assertThat(command.action().getValue(Action.SMALL_ICON)).isSameAs(icon);
            assertThat(owner.dispatchShortcut(stroke("cmd+alt+k"), null)).isTrue();
            assertThat(seen).hasSize(1);
        });
    }
```

Add to `WindowPanelsTest` (imports: `dev.jasper.app.contributions.StandInHooks`, `javax.swing.JPanel`):

```java
    @Test void aSavedOpenStandInPanelShowsAPlaceholderAtItsSavedSizeAndThenTheRealPanel() throws Exception {
        edt(() -> {
            var icon = new ImageIcon();
            var recorded = new Contributions();
            recorded.contributor("dev.x").addPanel("dev.x.hosts", "Hosts", icon, PanelRegion.LEFT, Optional.empty(), site -> new JLabel());
            var model = new Contributions();
            List<String> wanted = new ArrayList<>();
            model.addStandIns("dev.x", recorded.contributedBy("dev.x"),
                new StandInHooks((id, invocation) -> { }, () -> wanted.add("panel"), () -> { }));
            var state = UiState.inMemory();
            state.putPanel("dev.x.hosts", new UiState.Panel("LEFT", true, 333));
            WindowContent owner = window();
            owner.connectContributions(model, state);
            JComponent placeholder = owner.regions().content(PanelRegion.LEFT);
            assertThat(placeholder).isInstanceOf(JPanel.class);
            assertThat(placeholder.getComponentCount()).as("no spinner, nothing at all").isZero();
            assertThat(owner.regions().size(PanelRegion.LEFT)).isEqualTo(333);
            assertThat(owner.rail().buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Hosts");
            assertThat(wanted).containsExactly("panel");

            var real = new JLabel("real hosts");
            List<Boolean> heard = new ArrayList<>();
            model.bindingStarted("dev.x");
            model.contributor("dev.x").addPanel("dev.x.hosts", "Hosts", icon, PanelRegion.LEFT, Optional.empty(), site -> {
                site.onVisibility(heard::add);
                return real;
            });
            model.bindingFinished("dev.x");

            assertThat(owner.regions().content(PanelRegion.LEFT)).isSameAs(placeholder);
            assertThat(placeholder.getComponent(0)).isSameAs(real);
            assertThat(heard).containsExactly(true);
        });
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.HostedUiTest' --tests 'dev.jasper.app.workspace.WindowContributionsTest' --tests 'dev.jasper.app.workspace.WindowPanelsTest'`
Expected: FAIL. `HostedUiTest`: `register` throws "Action already registered", because `HostedUi` still registers as the application, which a stand-in blocks. `WindowContributionsTest`: the accelerator stays `cmd+alt+j` and the icon is the stand-in's. `WindowPanelsTest` already passes (Task 1 did the model side); it stays as the window-level pin.

- [ ] **Step 3: Register through the plugin's contributor**

In `HostedUi.java`, add a field after `private final Contributions model;`:

```java
    /** Registration as this plugin: recorded as its own, and taking its stand-ins' places. */
    private final Contributions.Contributor contributed;
```

In the 13-argument constructor, after the line that assigns `this.pluginId … this.onUi = onUi;`, add:

```java
        this.contributed = model.contributor(pluginId);
```

Then replace each registration call on `model` (reads such as `model.panels()`, `model.fileSources()` and `model.requestPanel` stay):

- in `actions()`: `ActionEntry entry = model.addAction(spec.id(), …` becomes `ActionEntry entry = contributed.addAction(spec.id(), …` with the same arguments;
- in `toolbar()`: `var registration = model.addToolbar(entry);` becomes `var registration = contributed.addToolbar(entry);`;
- in `menus()`: the three `model.addMenuSection(` calls become `contributed.addMenuSection(`;
- in `statusBar()`: both `model.addStatus(spec.id(), spec.side() == Side.LEFT, spec.priority())` become `contributed.addStatus(spec.id(), spec.side() == Side.LEFT, spec.priority())`;
- in `panels().register`: `var entry = model.addPanel(spec.id(), …` becomes `var entry = contributed.addPanel(spec.id(), …` with the same arguments;
- in `rail()`: `var registration = model.addRailAction(actionId);` becomes `var registration = contributed.addRailAction(actionId);`.

In `HostedPalette.register`, replace

```java
        dev.jasper.app.lifecycle.Subscription added = model.addScope(new Adapted(scope, spec));
```

with

```java
        dev.jasper.app.lifecycle.Subscription added = model.contributor(pluginId).addScope(new Adapted(scope, spec));
```

In `HostedFileSources.register`, replace

```java
        dev.jasper.app.lifecycle.Subscription added = model.addFileSource(adapted);
```

with

```java
        dev.jasper.app.lifecycle.Subscription added = model.contributor(pluginId).addFileSource(adapted);
```

- [ ] **Step 4: Let windows follow adopted shortcuts and icons**

In `WindowContributions.java`, add a field after `private final Map<String, Subscription> commands = new LinkedHashMap<>();`:

```java
    /** The extensions this window's bindings were last built from. */
    private List<KeyBindings.Extension> bound = List.of();
```

Replace `extensions()` with:

```java
    /** Contributed actions in registration order, which is plugin load order: the keybinding precedence. */
    List<KeyBindings.Extension> extensions() {
        bound = currentExtensions();
        return bound;
    }

    private List<KeyBindings.Extension> currentExtensions() {
        List<KeyBindings.Extension> result = new ArrayList<>();
        for (ActionEntry entry : model.actions())
            if (KeyBindings.extensionId(entry.id())) result.add(entry.extension());
        return List.copyOf(result);
    }
```

In `changed`, replace the `ACTIONS` arm's first statement

```java
                if (syncActions()) owner.rebind();
```

with

```java
                // A plugin that took its stand-ins' places may bring default shortcuts other than the recorded ones.
                if (syncActions() || !currentExtensions().equals(bound)) owner.rebind();
```

In `syncActions`, after `action.setEnabled(entry.enabled());` add:

```java
            // A plugin that took a stand-in's place may draw the same artwork from another icon instance.
            if (action.getValue(Action.SMALL_ICON) != entry.icon()) {
                action.putValue(Command.ICON, entry.icon());
                action.putValue(Action.SMALL_ICON, entry.icon());
            }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.workspace.WindowContributionsTest' --tests 'dev.jasper.app.workspace.WindowPanelsTest' --tests 'dev.jasper.app.workspace.WindowToolbarTest' --tests 'dev.jasper.app.workspace.WindowStatusBarContributionsTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java jasper-app/src/main/java/dev/jasper/app/plugins/HostedPalette.java \
  jasper-app/src/main/java/dev/jasper/app/plugins/HostedFileSources.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java jasper-app/src/test/java/dev/jasper/app/workspace/WindowContributionsTest.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/WindowPanelsTest.java
git commit -m "$(cat <<'EOF'
feat(plugins): register plugin contributions as their own; windows follow bindings

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Icon origins recorded where plugins mint icons

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/IconOrigin.java`, `IconOrigins.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java`, `HostedContext.java`, `PluginHost.java`, `PluginIconFiles.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/AppIcons.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/IconOriginsTest.java` (new), `PluginIconFilesTest.java`, `HostedChooserTest.java`; `jasper-app/src/test/java/dev/jasper/app/platform/AppIconsPluginTest.java`

**Interfaces:**
- Consumes: `AppIcons.named(String)`, `AppIcons.bundled(String)`, `AppIcons.plugin(ClassLoader, String)`; Task 2's `HostedUi.contributed`.
- Produces:
  - `sealed interface IconOrigin` with `Named(String name)`, `Bundled(String id)`, `PluginSvg(String pluginId, String path)`.
  - `final class IconOrigins` with `Icon record(Icon icon, IconOrigin origin)`, `Optional<IconOrigin> origin(Icon icon)`, `void stop()`.
  - `PluginHost.icons` (`IconOrigins`) and `PluginHost.sourcesNeeded` (`volatile Consumer<Set<String>>`, a no-op until Task 7 sets it).
  - A 15-argument `HostedUi` constructor ending `…, IconOrigins icons, Consumer<Set<String>> sourcesNeeded)`; the 13- and 11-argument constructors keep working.
  - `PluginIconFiles.resource(List<Path> jars, String name): Optional<byte[]>`.
  - `AppIcons.svg(byte[] data): Icon`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/plugins/IconOriginsTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.BundledIcon;
import dev.jasper.sdk.ui.IconName;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class IconOriginsTest {
    private final TerminalFixture fixture = new TerminalFixture();
    private final HostedTerminals terminals = new HostedTerminals("dev.x.tool", new CapabilityGate("dev.x.tool", java.util.Set.of()),
        fixture.registry, Runnable::run, () -> true, () -> true,
        new HostedSessions("dev.x.tool", new Containment(() -> true), Runnable::run, id -> null));

    @Test void iconsAPluginMintsAreRecordedWithTheirOriginsUntilTheCacheIsWritten() {
        var origins = new IconOrigins();
        var hosted = new HostedUi("dev.x.tool", new Contributions(), new Containment(() -> true), Runnable::run, () -> true, () -> true,
            getClass().getClassLoader(), () -> Variant.DARK, handler -> () -> { }, AppContractTest.headlessWindows(), terminals,
            null, null, origins, ids -> { });
        Icon named = hosted.appearance().icon(IconName.TERMINAL);
        Icon bundled = hosted.appearance().icon(BundledIcon.ACTIONS_ADD_FILE);
        Icon svg = hosted.appearance().icon("dev/jasper/app/icons/palette-probe.svg");

        assertThat(named).as("never wrapped: windows test icons by type").isSameAs(AppIcons.named("TERMINAL"));
        assertThat(origins.origin(named)).contains(new IconOrigin.Named("TERMINAL"));
        assertThat(origins.origin(bundled)).contains(new IconOrigin.Bundled("actions/addFile"));
        assertThat(origins.origin(svg)).contains(new IconOrigin.PluginSvg("dev.x.tool", "dev/jasper/app/icons/palette-probe.svg"));
        assertThat(origins.origin(new ImageIcon())).isEmpty();
        assertThat(origins.origin(null)).isEmpty();

        origins.stop();
        assertThat(origins.origin(named)).isEmpty();
        hosted.appearance().icon(IconName.TERMINAL);
        assertThat(origins.origin(named)).as("nothing is recorded once the cache was written").isEmpty();
    }
}
```

Add to `PluginIconFilesTest`:

```java
    @Test void readsAnyResourceFromTheJarsWithoutPluginCode() throws Exception {
        var candidate = candidate("dev.example.resource", "");
        PluginJars.addResource(candidate.jars().get(0), "icons/tool.svg", SVG);
        assertThat(PluginIconFiles.resource(candidate.jars(), "icons/tool.svg")).hasValueSatisfying(bytes ->
            assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(SVG));
        assertThat(PluginIconFiles.resource(candidate.jars(), "icons/none.svg")).isEmpty();
        PluginJars.addResource(candidate.jars().get(0), "icons/big.svg", "x".repeat(PluginIconFiles.MAX_BYTES + 1));
        assertThat(PluginIconFiles.resource(candidate.jars(), "icons/big.svg")).isEmpty();
    }
```

Add to `AppIconsPluginTest`:

```java
    @Test void svgBytesDrawLikeThePluginsOwnIcon() throws Exception {
        var themes = new ThemeController(Theme.INTELLIJ_LIGHT);
        try (var input = getClass().getClassLoader().getResourceAsStream("dev/jasper/app/icons/palette-probe.svg")) {
            var icon = AppIcons.svg(input.readAllBytes());
            assertThat(icon.getIconWidth()).isEqualTo(16);
            assertThat(IntellijIconsTest.centre(icon))
                .isEqualTo(IntellijIconsTest.centre(AppIcons.plugin(getClass().getClassLoader(), "dev/jasper/app/icons/palette-probe.svg")));
            assertThatNullPointerException().isThrownBy(() -> AppIcons.svg(null));
        } finally { new ThemeController(); }
    }
```

Add to `HostedChooserTest`:

```java
    @Test void aChooserAsksForTheSourcesItNamesBeforeListingThem() {
        var id = fixture.addWindow();
        ChooserSource late = new ChooserSource() {
            @Override public String id() { return "dev.x.tool.late"; }
            @Override public String heading() { return "Late"; }
            @Override public List<Root> roots() { return List.of(); }
            @Override public Runnable onChanged(Runnable listener) { return () -> { }; }
            @Override public java.util.concurrent.CompletableFuture<Browser> connect(String root, Optional<UUID> owner) {
                return new java.util.concurrent.CompletableFuture<>();
            }
        };
        List<Set<String>> asked = new ArrayList<>();
        var waiting = new HostedUi("dev.x.tool", contributions, new Containment(() -> true), Runnable::run, () -> true, open::get,
            getClass().getClassLoader(), () -> Variant.DARK, h -> () -> { }, windows, terminals, null, null, new IconOrigins(),
            ids -> { asked.add(ids); contributions.addFileSource(late); });
        waiting.windows().choose(new FileChoice(terminals.windowHandle(id), "Open", FileChoice.Mode.OPEN, false, Optional.empty(),
            Optional.empty(), Set.of("dev.x.tool.late")));
        assertThat(asked).containsExactly(Set.of("dev.x.tool.late"));
        assertThat(requests.getLast().sources()).containsExactly(late);
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.IconOriginsTest' --tests 'dev.jasper.app.plugins.PluginIconFilesTest' --tests 'dev.jasper.app.plugins.HostedChooserTest' --tests 'dev.jasper.app.platform.AppIconsPluginTest'`
Expected: FAIL to compile: `IconOrigins`, `IconOrigin`, the 15-argument `HostedUi` constructor, `PluginIconFiles.resource` and `AppIcons.svg` do not exist.

- [ ] **Step 3: Create `IconOrigin.java` and `IconOrigins.java`**

```java
package dev.jasper.app.plugins;

/**
 * Where a plugin's icon came from, recorded when the plugin minted it through its appearance, so the next launch can
 * draw the same icon before the plugin starts: a semantic name, a bundled catalog id, or an SVG in a plugin's jars.
 */
sealed interface IconOrigin permits IconOrigin.Named, IconOrigin.Bundled, IconOrigin.PluginSvg {
    /** From {@code Appearance.icon(IconName)}. */
    record Named(String name) implements IconOrigin { }

    /** From {@code Appearance.icon(BundledIcon)}. */
    record Bundled(String id) implements IconOrigin { }

    /** From {@code Appearance.icon(String)}: a resource in the jars of {@code pluginId}. */
    record PluginSvg(String pluginId, String path) implements IconOrigin { }
}
```

```java
package dev.jasper.app.plugins;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import javax.swing.Icon;

/**
 * The origin of each icon plugins minted this launch, by identity, until the plugin cache has been written. Bundled
 * artwork is shared, so one instance may have two equivalent origins; either draws it again. Any thread.
 */
final class IconOrigins {
    private final Map<Icon, IconOrigin> origins = Collections.synchronizedMap(new IdentityHashMap<>());
    private volatile boolean recording = true;

    /** Remembers where {@code icon} came from and returns it unchanged: windows test icons by type, so nothing wraps it. */
    Icon record(Icon icon, IconOrigin origin) {
        if (recording && icon != null) origins.put(icon, origin);
        return icon;
    }

    /** Where {@code icon} came from; empty for an icon no plugin minted, and once recording has stopped. */
    Optional<IconOrigin> origin(Icon icon) {
        return icon == null ? Optional.empty() : Optional.ofNullable(origins.get(icon));
    }

    /** The cache is written: forget everything and record nothing more. */
    void stop() {
        recording = false;
        origins.clear();
    }
}
```

- [ ] **Step 4: Record at mint time, and let a chooser ask for its sources**

In `HostedUi.java`, add fields after `private final List<Runnable> closers = new ArrayList<>();`:

```java
    private final IconOrigins icons;
    private final Consumer<Set<String>> sourcesNeeded;
```

Replace the 13-argument constructor with a delegating one followed by the canonical 15-argument constructor:

```java
    HostedUi(String pluginId, Contributions model, Containment containment, Consumer<Runnable> ui, BooleanSupplier onUi,
             BooleanSupplier open, ClassLoader loader, Supplier<Variant> variant,
             Function<Consumer<Variant>, Subscription> themeSubscriber, AuxiliaryWindows windows, HostedTerminals terminals,
             Supplier<dev.jasper.app.appearance.ResolvedTheme> appearance,
             Function<Consumer<dev.jasper.app.appearance.ResolvedTheme>, dev.jasper.app.lifecycle.Subscription> subscribe) {
        this(pluginId, model, containment, ui, onUi, open, loader, variant, themeSubscriber, windows, terminals, appearance, subscribe,
            new IconOrigins(), ids -> { });
    }

    /**
     * As the 13-argument constructor, plus where minted icons' origins are recorded and whom a file chooser asks for the
     * sources it names before listing them (the runtime starts their plugins).
     */
    HostedUi(String pluginId, Contributions model, Containment containment, Consumer<Runnable> ui, BooleanSupplier onUi,
             BooleanSupplier open, ClassLoader loader, Supplier<Variant> variant,
             Function<Consumer<Variant>, Subscription> themeSubscriber, AuxiliaryWindows windows, HostedTerminals terminals,
             Supplier<dev.jasper.app.appearance.ResolvedTheme> appearance,
             Function<Consumer<dev.jasper.app.appearance.ResolvedTheme>, dev.jasper.app.lifecycle.Subscription> subscribe,
             IconOrigins icons, Consumer<Set<String>> sourcesNeeded) {
        this.pluginId = pluginId; this.model = model; this.containment = containment; this.ui = ui; this.onUi = onUi;
        this.open = open; this.loader = loader; this.variant = variant; this.themeSubscriber = themeSubscriber;
        this.windows = windows;
        this.terminals = terminals;
        this.contributed = model.contributor(pluginId);
        this.icons = java.util.Objects.requireNonNull(icons, "icons");
        this.sourcesNeeded = java.util.Objects.requireNonNull(sourcesNeeded, "sourcesNeeded");
        if (appearance != null) {
            appearanceSnapshot = java.util.Objects.requireNonNull(appearance.get());
            var registration = subscribe.apply(next -> {
                var previous = appearanceSnapshot;
                appearanceSnapshot = next;
                if (!previous.workspace().equals(next.workspace())) {
                    for (var handler : List.copyOf(workspaceHandlers)) {
                        if (workspaceHandlers.contains(handler) && open.getAsBoolean())
                            containment.run(pluginId, "workspace appearance", () -> handler.accept(workspaceVariant()));
                    }
                }
            });
            closers.add(registration::close);
        }
    }
```

(The canonical body is the former 13-argument body plus the `contributed`, `icons` and `sourcesNeeded` assignments; Task 2's `this.contributed = model.contributor(pluginId);` moves here.)

In `appearance()`, replace the three `icon` methods with:

```java
            @Override public Icon icon(dev.jasper.sdk.ui.IconName name) {
                String key = java.util.Objects.requireNonNull(name, "name").name();
                return icons.record(AppIcons.named(key), new IconOrigin.Named(key));
            }
            @Override public Icon icon(dev.jasper.sdk.ui.BundledIcon name) {
                String id = java.util.Objects.requireNonNull(name, "name").id();
                return icons.record(AppIcons.bundled(id), new IconOrigin.Bundled(id));
            }
            @Override public Icon icon(String svgResourcePath) {
                return icons.record(AppIcons.plugin(loader, svgResourcePath), new IconOrigin.PluginSvg(pluginId, svgResourcePath));
            }
```

In `chooseLocations`, directly after `guard("choose");`, add:

```java
        // A source whose plugin has not started yet would be missing; its plugin starts now, before the list is read.
        if (!sourceIds.isEmpty()) sourcesNeeded.accept(Set.copyOf(sourceIds));
```

In `PluginHost.java`, add after `final ServiceRegistry services = new ServiceRegistry();`:

```java
    /** Where plugins' icons came from, for the plugin cache. */
    final IconOrigins icons = new IconOrigins();
    /** Whom a file chooser asks for the sources it names; the runtime starts their plugins. UI thread. */
    volatile Consumer<Set<String>> sourcesNeeded = ids -> { };
```

In `HostedContext`'s constructor, change the end of the `HostedUi` construction from

```java
            host.environment.windows(), terminals, host.environment.appearance(), host.environment.appearanceSubscriber());
```

to

```java
            host.environment.windows(), terminals, host.environment.appearance(), host.environment.appearanceSubscriber(),
            host.icons, ids -> host.sourcesNeeded.accept(ids));
```

- [ ] **Step 5: Read SVG bytes without plugin code**

In `PluginIconFiles.java`, add (import `java.util.List`):

```java
    /** One resource from {@code jars}, in jar order, without plugin code; empty when absent, unreadable or larger than {@link #MAX_BYTES}. */
    static Optional<byte[]> resource(List<Path> jars, String name) {
        for (Path jar : jars) {
            try (var file = new JarFile(jar.toFile())) {
                JarEntry entry = file.getJarEntry(name);
                if (entry == null) continue;
                try (var input = file.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                    return bytes.length > MAX_BYTES ? Optional.empty() : Optional.of(bytes);
                }
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.DEBUG, "Could not read " + name + " from " + jar, failure);
            }
        }
        return Optional.empty();
    }
```

In `AppIcons.java`, after `plugin(…)`, add:

```java
    /**
     * A 16-pixel SVG from its bytes, drawn as authored: a plugin's icon before the plugin has started, read from its
     * jar without its code.
     */
    public static Icon svg(byte[] data) {
        Objects.requireNonNull(data, "data");
        try { return new FlatSVGIcon(new java.io.ByteArrayInputStream(data)).derive(16, 16); }
        catch (IOException unreadable) { throw new IllegalArgumentException("Unreadable SVG icon", unreadable); }
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.platform.AppIconsPluginTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/main/java/dev/jasper/app/platform/AppIcons.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/IconOriginsTest.java jasper-app/src/test/java/dev/jasper/app/plugins/PluginIconFilesTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/HostedChooserTest.java jasper-app/src/test/java/dev/jasper/app/platform/AppIconsPluginTest.java
git commit -m "$(cat <<'EOF'
feat(plugins): record icon origins at mint time and let choosers ask for sources

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: The plugin cache file

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java`, `jasper-app/src/main/java/dev/jasper/app/plugins/TomlText.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/CachedPlugin.java`, `PluginCacheFile.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginCacheFileTest.java` (new), `TomlTextTest.java`; `jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java`

**Interfaces:**
- Consumes: Task 1's `Contributed<I>` records, Task 3's `IconOrigin`; `TomlStateFile.readBounded` and `writeAtomically`; `TomlText.quote`.
- Produces:
  - `AppDirs.cache(): Path` (`<root>/cache`) and `AppDirs.pluginCache(): Path` (`<root>/cache/plugins`).
  - `record CachedPlugin(String id, String version, PluginCandidate.Origin origin, Path directory, List<CachedPlugin.JarStamp> jars, String jasper, String descriptor, Map<String, String> packages, List<String> classes, List<Contributed<IconOrigin>> items)` with `record JarStamp(String name, long size, long modified)`, `boolean matches(PluginCandidate.Origin origin, Path directory, List<JarStamp> jars, String jasper)` and `static List<JarStamp> stamps(List<Path> jars)` (null when a jar cannot be read).
  - `PluginCacheFile.file(Path directory, String id)`, `read(Path directory, String id): Optional<CachedPlugin>`, `write(Path directory, CachedPlugin plugin)`, `delete(Path directory, String id)`, `text(CachedPlugin): String`, `parse(String): CachedPlugin` (throws `IOException`), `MAX_BYTES`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/plugins/PluginCacheFileTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.contributions.Contributed;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.contributions.ProgressState;
import dev.jasper.app.palette.PaletteVerb;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class PluginCacheFileTest {
    @TempDir Path directory;

    static CachedPlugin sample(Path pluginDirectory, String version) {
        return new CachedPlugin("dev.example.tool", version, PluginCandidate.Origin.BUNDLED, pluginDirectory,
            List.of(new CachedPlugin.JarStamp("tool.jar", 1234, 1_700_000_000_000L)), "sdk 0.12.0; app none",
            "id = \"dev.example.tool\"\nname = \"Tool \\\"quoted\\\"\"\n",
            Map.of("dev.example.tool", "tool.jar", "dev.example.tool.api", "tool.jar"),
            List.of("dev.example.tool.Main", "dev.example.tool.Main$1"),
            List.<Contributed<IconOrigin>>of(
                new Contributed.Action<>("dev.example.tool.run", "Run “it”", new IconOrigin.Named("TERMINAL"), List.of("go", "start"),
                    Optional.of("cmd+alt+j"), Optional.of("ctrl+alt+j"), KeyContext.panel("dev.example.tool.panel"), false),
                new Contributed.Toolbar<>(true, new IconOrigin.Bundled("actions/addFile"), "Hosts", List.of("dev.example.tool.run")),
                new Contributed.Toolbar<IconOrigin>(false, null, "", List.of("dev.example.tool.run")),
                new Contributed.Menu<IconOrigin>(MenuTarget.topLevel("dev.example.tool.menu", "Tool"), List.of(new MenuEntry.Item("dev.example.tool.run"),
                    new MenuEntry.Separator(), new MenuEntry.Submenu("More", List.of(new MenuEntry.Item("dev.example.tool.run"))))),
                new Contributed.Menu<IconOrigin>(MenuTarget.terminalContext(), List.of()),
                new Contributed.Status<>("dev.example.tool.state", false, 60, "Line\ttab", new IconOrigin.PluginSvg("dev.example.tool", "icons/tool.svg"),
                    "tip", "dev.example.tool.run", false,
                    new ProgressState("Copying", "2 of 3", "Copying two of three", OptionalDouble.of(0.5), "dev.example.tool.run", null)),
                new Contributed.Status<IconOrigin>("dev.example.tool.plain", true, 1, "", null, null, null, true, null),
                new Contributed.Panel<>("dev.example.tool.panel", "Tool", new IconOrigin.Named("TERMINAL"), PanelRegion.BOTTOM,
                    Optional.of("dev.example.tool.run")),
                new Contributed.Panel<IconOrigin>("dev.example.tool.side", "Side", null, PanelRegion.LEFT, Optional.empty()),
                new Contributed.Rail<IconOrigin>("dev.example.tool.run"),
                new Contributed.Scope<IconOrigin>("dev.example.tool.scope", "Tool", null, "Find things", "Search tools",
                    List.of(new PaletteVerb("open", "Open"), new PaletteVerb("copy", "Copy")), true, true, false, Optional.of("dev.example.tool.run")),
                new Contributed.FileSource<IconOrigin>("dev.example.tool.places", "Places")));
    }

    @Test void everyKindRoundTripsWithItsIconOrigin() {
        CachedPlugin plugin = sample(directory.resolve("plugins/dev.example.tool"), "1.2.0");
        PluginCacheFile.write(directory.resolve("cache/plugins"), plugin);
        assertThat(PluginCacheFile.read(directory.resolve("cache/plugins"), "dev.example.tool")).contains(plugin);
    }

    @Test void aMissingTruncatedGarbledForeignOrOversizedFileIsAMiss() throws Exception {
        Path file = PluginCacheFile.file(directory, "dev.example.tool");
        assertThat(PluginCacheFile.read(directory, "dev.example.tool")).as("missing").isEmpty();
        Files.writeString(file, "schema = 1\nid = \"dev.example.tool\"\n\n[[items]]\nkind = \"action\"\n");
        assertThat(PluginCacheFile.read(directory, "dev.example.tool")).as("truncated").isEmpty();
        Files.writeString(file, "not = [toml");
        assertThat(PluginCacheFile.read(directory, "dev.example.tool")).as("garbage").isEmpty();
        Files.write(file, new byte[] {(byte) 0xC3, (byte) 0x28});
        assertThat(PluginCacheFile.read(directory, "dev.example.tool")).as("invalid UTF-8").isEmpty();
        CachedPlugin plugin = sample(directory, "1.2.0");
        Files.writeString(file, PluginCacheFile.text(plugin).replace("schema = 1", "schema = 2"));
        assertThat(PluginCacheFile.read(directory, "dev.example.tool")).as("another schema").isEmpty();
        PluginCacheFile.write(directory, plugin);
        Files.copy(file, PluginCacheFile.file(directory, "dev.example.other"));
        assertThat(PluginCacheFile.read(directory, "dev.example.other")).as("another plugin's entry under this name").isEmpty();
        Files.writeString(file, "x".repeat(PluginCacheFile.MAX_BYTES + 1));
        assertThat(PluginCacheFile.read(directory, "dev.example.tool")).as("too large").isEmpty();
    }

    @Test void theKeyIsTheOriginDirectoryEveryJarsNameSizeAndTimeAndTheJasperVersion() throws Exception {
        Path pluginDirectory = Files.createDirectories(directory.resolve("plugins/dev.example.tool"));
        Path jar = Files.writeString(pluginDirectory.resolve("tool.jar"), "jar bytes");
        List<CachedPlugin.JarStamp> stamps = CachedPlugin.stamps(List.of(jar));
        var plugin = new CachedPlugin("dev.example.tool", "1.2.0", PluginCandidate.Origin.BUNDLED, pluginDirectory, stamps, "jasper 1",
            "id = \"dev.example.tool\"", Map.of(), List.of(), List.of());
        assertThat(plugin.matches(PluginCandidate.Origin.BUNDLED, pluginDirectory, stamps, "jasper 1")).isTrue();
        assertThat(plugin.matches(PluginCandidate.Origin.BUNDLED, pluginDirectory.resolve("../dev.example.tool"), stamps, "jasper 1"))
            .as("compared normalised").isTrue();
        assertThat(plugin.matches(PluginCandidate.Origin.USER, pluginDirectory, stamps, "jasper 1")).as("origin").isFalse();
        assertThat(plugin.matches(PluginCandidate.Origin.BUNDLED, directory.resolve("elsewhere"), stamps, "jasper 1")).as("directory").isFalse();
        assertThat(plugin.matches(PluginCandidate.Origin.BUNDLED, pluginDirectory, stamps, "jasper 2")).as("Jasper").isFalse();
        Files.setLastModifiedTime(jar, FileTime.fromMillis(Files.getLastModifiedTime(jar).toMillis() + 2_000));
        assertThat(plugin.matches(PluginCandidate.Origin.BUNDLED, pluginDirectory, CachedPlugin.stamps(List.of(jar)), "jasper 1"))
            .as("touched, same size").isFalse();
        Path extra = Files.writeString(pluginDirectory.resolve("extra.jar"), "x");
        assertThat(plugin.matches(PluginCandidate.Origin.BUNDLED, pluginDirectory, CachedPlugin.stamps(List.of(extra, jar)), "jasper 1"))
            .as("another jar").isFalse();
        assertThat(CachedPlugin.stamps(List.of(pluginDirectory.resolve("gone.jar")))).as("unreadable").isNull();
    }

    @Test void writesReplaceTheEntryAtomicallyCreateTheFolderAndLeaveNoTemporaryFile() throws Exception {
        Path cache = directory.resolve("new/cache/plugins");
        PluginCacheFile.write(cache, sample(directory, "1.2.0"));
        CachedPlugin second = sample(directory, "1.3.0");
        PluginCacheFile.write(cache, second);
        assertThat(PluginCacheFile.read(cache, "dev.example.tool")).contains(second);
        try (var files = Files.list(cache)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("dev.example.tool.toml");
        }
        PluginCacheFile.delete(cache, "dev.example.tool");
        assertThat(PluginCacheFile.read(cache, "dev.example.tool")).isEmpty();
        PluginCacheFile.delete(cache, "dev.example.tool");
    }
}
```

Add to `TomlTextTest`:

```java
    @Test void escapesDeleteWhichTomlForbidsRawInAString() {
        String text = "a" + (char) 0x7f + "b";
        assertThat(TomlText.quote(text)).isEqualTo("\"a\\u007Fb\"");
        assertThat(Toml.parse("k = " + TomlText.quote(text)).getString("k")).isEqualTo(text);
    }
```

Add to `AppDirsTest`:

```java
    @Test void cachesLiveUnderTheRootAndResolvingCreatesNothing() {
        var dirs = AppDirs.resolve("Mac OS X", Map.of(), home);
        assertThat(dirs.cache()).isEqualTo(dirs.root().resolve("cache"));
        assertThat(dirs.pluginCache()).isEqualTo(dirs.root().resolve("cache/plugins"));
        assertThat(dirs.pluginCache()).doesNotExist();
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PluginCacheFileTest' --tests 'dev.jasper.app.plugins.TomlTextTest' --tests 'dev.jasper.app.platform.AppDirsTest'`
Expected: FAIL to compile: `CachedPlugin`, `PluginCacheFile`, `AppDirs.cache` and `AppDirs.pluginCache` do not exist.

- [ ] **Step 3: Add the cache locations and escape DEL**

In `AppDirs.java`, after `uiState()`, add:

```java
    /** Rebuildable data: deleting it costs only a slower next launch. */
    public Path cache() {
        return root.resolve("cache");
    }

    /** The plugin cache: one {@code <id>.toml} per plugin, its discovery, its start-time classes and its recorded contributions. */
    public Path pluginCache() {
        return cache().resolve("plugins");
    }
```

In `TomlText.quote`, change

```java
                default -> { if (c < 0x20) out.append(String.format("\\u%04X", (int) c)); else out.append(c); }
```

to

```java
                default -> { if (c < 0x20 || c == 0x7f) out.append(String.format("\\u%04X", (int) c)); else out.append(c); }
```

- [ ] **Step 4: Create `CachedPlugin.java`**

```java
package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributed;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One plugin's entry in the plugin cache: what discovery, loading and the last launch's start learned, and the key that
 * keeps it valid. The key is the plugin id and version, its origin and directory, every jar's name, size and
 * modification time, and the Jasper version; the descriptor is the id and version's source.
 *
 * @param id the plugin id
 * @param version its version, as its descriptor gave it
 * @param origin where it was found
 * @param directory its directory, absolute and normalised
 * @param jars its jars as the key saw them, in discovery order
 * @param jasper the Jasper version the entry was written by
 * @param descriptor the {@code plugin.toml} text
 * @param packages each package its jars define a class in, mapped to the first jar that does
 * @param classes the classes its loader defined by the end of its start, in definition order
 * @param items its contributions at the end of start-up, in registration order
 */
record CachedPlugin(String id, String version, PluginCandidate.Origin origin, Path directory, List<JarStamp> jars, String jasper,
                    String descriptor, Map<String, String> packages, List<String> classes, List<Contributed<IconOrigin>> items) {
    /** A jar as the key sees it. */
    record JarStamp(String name, long size, long modified) { }

    CachedPlugin {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(version, "version"); Objects.requireNonNull(origin, "origin");
        directory = directory.toAbsolutePath().normalize();
        jars = List.copyOf(jars);
        Objects.requireNonNull(jasper, "jasper"); Objects.requireNonNull(descriptor, "descriptor");
        packages = Collections.unmodifiableMap(new TreeMap<>(packages));
        classes = List.copyOf(classes);
        items = List.copyOf(items);
    }

    /** Whether this entry describes the plugin as it is on disk now, for this Jasper. */
    boolean matches(PluginCandidate.Origin origin, Path directory, List<JarStamp> jars, String jasper) {
        return this.origin == origin && this.directory.equals(directory.toAbsolutePath().normalize())
            && this.jars.equals(jars) && this.jasper.equals(jasper);
    }

    /** The stamps of {@code jars}, in order; null when one cannot be read. Reads attributes only, never a jar. */
    static List<JarStamp> stamps(List<Path> jars) {
        List<JarStamp> stamps = new ArrayList<>();
        for (Path jar : jars) {
            try {
                BasicFileAttributes attributes = Files.readAttributes(jar, BasicFileAttributes.class);
                stamps.add(new JarStamp(jar.getFileName().toString(), attributes.size(), attributes.lastModifiedTime().toMillis()));
            } catch (IOException | RuntimeException unreadable) {
                return null;
            }
        }
        return List.copyOf(stamps);
    }
}
```

- [ ] **Step 5: Create `PluginCacheFile.java`**

```java
package dev.jasper.app.plugins;

import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.contributions.Contributed;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.contributions.ProgressState;
import dev.jasper.app.palette.PaletteVerb;
import dev.jasper.app.persistence.TomlStateFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * The plugin cache's files, {@code <home>/cache/plugins/<id>.toml}: read whole or not at all, written atomically,
 * rebuilt whenever they are missing, unreadable or stale. Any thread.
 */
final class PluginCacheFile {
    static final long SCHEMA = 1;
    static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final System.Logger LOG = System.getLogger(PluginCacheFile.class.getName());

    private PluginCacheFile() { }

    static Path file(Path directory, String id) { return directory.resolve(id + ".toml"); }

    /** The entry for {@code id}; empty when there is none or it cannot be read, which is a miss like any other. */
    static Optional<CachedPlugin> read(Path directory, String id) {
        try {
            Optional<String> text = TomlStateFile.readBounded(file(directory, id), MAX_BYTES, "plugin cache");
            if (text.isEmpty()) return Optional.empty();
            CachedPlugin plugin = parse(text.get());
            return plugin.id().equals(id) ? Optional.of(plugin) : Optional.empty();
        } catch (IOException | RuntimeException unreadable) {
            LOG.log(System.Logger.Level.DEBUG, "The plugin cache of " + id + " is unreadable; it is rebuilt", unreadable);
            return Optional.empty();
        }
    }

    /** Replaces the entry atomically; a failure costs only a slower next launch. */
    static void write(Path directory, CachedPlugin plugin) {
        try { TomlStateFile.writeAtomically(file(directory, plugin.id()), ".plugin-cache-", text(plugin)); }
        catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not write the plugin cache of " + plugin.id(), failure);
        }
    }

    /** Forgets {@code id}'s entry, so its next launch starts it before the first window. */
    static void delete(Path directory, String id) {
        try { Files.deleteIfExists(file(directory, id)); }
        catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Could not delete the plugin cache of " + id, failure); }
    }

    static String text(CachedPlugin plugin) {
        var out = new StringBuilder();
        line(out, "schema", Long.toString(SCHEMA));
        line(out, "id", quote(plugin.id()));
        line(out, "version", quote(plugin.version()));
        line(out, "origin", quote(plugin.origin().name()));
        line(out, "directory", quote(plugin.directory().toString()));
        line(out, "jasper", quote(plugin.jasper()));
        line(out, "descriptor", quote(plugin.descriptor()));
        line(out, "classes", strings(plugin.classes()));
        out.append("\n[packages]\n");
        for (var entry : new TreeMap<>(plugin.packages()).entrySet()) line(out, quote(entry.getKey()), quote(entry.getValue()));
        for (CachedPlugin.JarStamp jar : plugin.jars()) {
            out.append("\n[[jars]]\n");
            line(out, "name", quote(jar.name()));
            line(out, "size", Long.toString(jar.size()));
            line(out, "modified", Long.toString(jar.modified()));
        }
        for (Contributed<IconOrigin> item : plugin.items()) {
            out.append("\n[[items]]\n");
            item(out, item);
        }
        return out.toString();
    }

    private static void item(StringBuilder out, Contributed<IconOrigin> item) {
        switch (item) {
            case Contributed.Action<IconOrigin> action -> {
                line(out, "kind", quote("action"));
                line(out, "id", quote(action.id()));
                line(out, "title", quote(action.title()));
                icon(out, action.icon());
                line(out, "keywords", strings(action.keywords()));
                action.defaultBinding().ifPresent(value -> line(out, "binding", quote(value)));
                action.otherDefault().ifPresent(value -> line(out, "other_binding", quote(value)));
                line(out, "context", quote(action.context().kind().name()));
                line(out, "context_owner", quote(action.context().owner()));
                line(out, "enabled", Boolean.toString(action.enabled()));
            }
            case Contributed.Toolbar<IconOrigin> toolbar -> {
                line(out, "kind", quote("toolbar"));
                line(out, "dropdown", Boolean.toString(toolbar.dropdown()));
                icon(out, toolbar.icon());
                line(out, "title", quote(toolbar.title()));
                line(out, "actions", strings(toolbar.actionIds()));
            }
            case Contributed.Menu<IconOrigin> menu -> {
                line(out, "kind", quote("menu"));
                line(out, "type", quote(menu.target().type().name()));
                line(out, "key", quote(menu.target().key()));
                line(out, "title", quote(menu.target().title()));
                line(out, "entries", entries(menu.entries()));
            }
            case Contributed.Status<IconOrigin> status -> {
                line(out, "kind", quote("status"));
                line(out, "id", quote(status.id()));
                line(out, "left", Boolean.toString(status.left()));
                line(out, "priority", Integer.toString(status.priority()));
                line(out, "text", quote(status.text()));
                icon(out, status.icon());
                if (status.tooltip() != null) line(out, "tooltip", quote(status.tooltip()));
                if (status.actionId() != null) line(out, "action", quote(status.actionId()));
                line(out, "visible", Boolean.toString(status.visible()));
                if (status.progress() != null) line(out, "progress", progress(status.progress()));
            }
            case Contributed.Panel<IconOrigin> panel -> {
                line(out, "kind", quote("panel"));
                line(out, "id", quote(panel.id()));
                line(out, "title", quote(panel.title()));
                icon(out, panel.icon());
                line(out, "region", quote(panel.region().name()));
                panel.toggleActionId().ifPresent(value -> line(out, "toggle", quote(value)));
            }
            case Contributed.Rail<IconOrigin> rail -> {
                line(out, "kind", quote("rail"));
                line(out, "action", quote(rail.actionId()));
            }
            case Contributed.Scope<IconOrigin> scope -> {
                line(out, "kind", quote("scope"));
                line(out, "id", quote(scope.id()));
                line(out, "label", quote(scope.label()));
                icon(out, scope.icon());
                line(out, "description", quote(scope.description()));
                line(out, "placeholder", quote(scope.placeholder()));
                line(out, "verbs", scope.verbs().stream()
                    .map(verb -> "{ id = " + quote(verb.id()) + ", label = " + quote(verb.label()) + " }")
                    .collect(Collectors.joining(", ", "[", "]")));
                line(out, "monospace", Boolean.toString(scope.monospaceRows()));
                line(out, "in_all", Boolean.toString(scope.inAll()));
                line(out, "in_recents", Boolean.toString(scope.inRecents()));
                scope.shortcutActionId().ifPresent(value -> line(out, "shortcut", quote(value)));
            }
            case Contributed.FileSource<IconOrigin> source -> {
                line(out, "kind", quote("file_source"));
                line(out, "id", quote(source.id()));
                line(out, "heading", quote(source.heading()));
            }
        }
    }

    private static void line(StringBuilder out, String key, String value) { out.append(key).append(" = ").append(value).append('\n'); }

    private static void icon(StringBuilder out, IconOrigin icon) {
        if (icon == null) return;
        line(out, "icon", switch (icon) {
            case IconOrigin.Named named -> "{ named = " + quote(named.name()) + " }";
            case IconOrigin.Bundled bundled -> "{ bundled = " + quote(bundled.id()) + " }";
            case IconOrigin.PluginSvg svg -> "{ plugin = " + quote(svg.pluginId()) + ", svg = " + quote(svg.path()) + " }";
        });
    }

    private static String entries(List<MenuEntry> entries) {
        return entries.stream().map(entry -> switch (entry) {
            case MenuEntry.Item item -> "{ item = " + quote(item.actionId()) + " }";
            case MenuEntry.Separator separator -> "{ separator = true }";
            case MenuEntry.Submenu submenu -> "{ submenu = " + quote(submenu.title()) + ", entries = " + entries(submenu.entries()) + " }";
        }).collect(Collectors.joining(", ", "[", "]"));
    }

    private static String progress(ProgressState state) {
        List<String> parts = new ArrayList<>();
        parts.add("text = " + quote(state.text()));
        parts.add("detail = " + quote(state.detail()));
        parts.add("accessible = " + quote(state.accessibleDescription()));
        if (state.fraction().isPresent()) parts.add("fraction = " + state.fraction().getAsDouble());
        if (state.actionId() != null) parts.add("action = " + quote(state.actionId()));
        if (state.secondaryActionId() != null) parts.add("secondary = " + quote(state.secondaryActionId()));
        return "{ " + String.join(", ", parts) + " }";
    }

    private static String strings(List<String> values) {
        return values.stream().map(PluginCacheFile::quote).collect(Collectors.joining(", ", "[", "]"));
    }

    private static String quote(String text) { return TomlText.quote(text); }

    static CachedPlugin parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException("Not valid TOML: " + toml.errors().getFirst().getMessage());
        if (!Long.valueOf(SCHEMA).equals(toml.get(List.of("schema")))) throw new IOException("Unknown plugin cache schema");
        List<CachedPlugin.JarStamp> jars = new ArrayList<>();
        TomlArray jarTables = toml.getArrayOrEmpty("jars");
        for (int i = 0; i < jarTables.size(); i++) {
            if (!(jarTables.get(i) instanceof TomlTable jar)) throw new IOException("A jar must be a table");
            jars.add(new CachedPlugin.JarStamp(string(jar, "name"), number(jar, "size"), number(jar, "modified")));
        }
        Map<String, String> packages = new TreeMap<>();
        TomlTable packageTable = toml.getTableOrEmpty("packages");
        for (String key : packageTable.keySet()) {
            if (!(packageTable.get(List.of(key)) instanceof String jar)) throw new IOException("A package's jar must be a string");
            packages.put(key, jar);
        }
        List<Contributed<IconOrigin>> items = new ArrayList<>();
        TomlArray itemTables = toml.getArrayOrEmpty("items");
        for (int i = 0; i < itemTables.size(); i++) {
            if (!(itemTables.get(i) instanceof TomlTable item)) throw new IOException("An item must be a table");
            items.add(item(item));
        }
        return new CachedPlugin(string(toml, "id"), string(toml, "version"), PluginCandidate.Origin.valueOf(string(toml, "origin")),
            Path.of(string(toml, "directory")), jars, string(toml, "jasper"), string(toml, "descriptor"), packages,
            strings(toml, "classes"), items);
    }

    private static Contributed<IconOrigin> item(TomlTable table) throws IOException {
        String kind = string(table, "kind");
        return switch (kind) {
            case "action" -> new Contributed.Action<>(string(table, "id"), string(table, "title"), icon(table), strings(table, "keywords"),
                Optional.ofNullable(optional(table, "binding")), Optional.ofNullable(optional(table, "other_binding")),
                new KeyContext(KeyContext.Kind.valueOf(string(table, "context")), string(table, "context_owner")), flag(table, "enabled"));
            case "toolbar" -> new Contributed.Toolbar<>(flag(table, "dropdown"), icon(table), string(table, "title"), strings(table, "actions"));
            case "menu" -> new Contributed.Menu<>(new MenuTarget(MenuTarget.Type.valueOf(string(table, "type")), string(table, "key"),
                string(table, "title")), entries(table));
            case "status" -> new Contributed.Status<>(string(table, "id"), flag(table, "left"), Math.toIntExact(number(table, "priority")),
                string(table, "text"), icon(table), optional(table, "tooltip"), optional(table, "action"), flag(table, "visible"),
                progress(table));
            case "panel" -> new Contributed.Panel<>(string(table, "id"), string(table, "title"), icon(table),
                PanelRegion.valueOf(string(table, "region")), Optional.ofNullable(optional(table, "toggle")));
            case "rail" -> new Contributed.Rail<>(string(table, "action"));
            case "scope" -> new Contributed.Scope<>(string(table, "id"), string(table, "label"), icon(table), string(table, "description"),
                string(table, "placeholder"), verbs(table), flag(table, "monospace"), flag(table, "in_all"), flag(table, "in_recents"),
                Optional.ofNullable(optional(table, "shortcut")));
            case "file_source" -> new Contributed.FileSource<>(string(table, "id"), string(table, "heading"));
            default -> throw new IOException("Unknown contribution kind: " + kind);
        };
    }

    private static String string(TomlTable table, String key) throws IOException {
        if (!(table.get(List.of(key)) instanceof String value)) throw new IOException(key + " must be a string");
        return value;
    }

    private static String optional(TomlTable table, String key) throws IOException {
        Object value = table.get(List.of(key));
        if (value == null) return null;
        if (!(value instanceof String text)) throw new IOException(key + " must be a string");
        return text;
    }

    private static long number(TomlTable table, String key) throws IOException {
        if (!(table.get(List.of(key)) instanceof Long value)) throw new IOException(key + " must be an integer");
        return value;
    }

    private static boolean flag(TomlTable table, String key) throws IOException {
        if (!(table.get(List.of(key)) instanceof Boolean value)) throw new IOException(key + " must be a boolean");
        return value;
    }

    private static List<String> strings(TomlTable table, String key) throws IOException {
        if (!(table.get(List.of(key)) instanceof TomlArray array)) throw new IOException(key + " must be an array");
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof String value)) throw new IOException(key + " must hold strings");
            values.add(value);
        }
        return values;
    }

    private static IconOrigin icon(TomlTable table) throws IOException {
        Object value = table.get(List.of("icon"));
        if (value == null) return null;
        if (!(value instanceof TomlTable icon)) throw new IOException("icon must be a table");
        if (icon.contains(List.of("named"))) return new IconOrigin.Named(string(icon, "named"));
        if (icon.contains(List.of("bundled"))) return new IconOrigin.Bundled(string(icon, "bundled"));
        return new IconOrigin.PluginSvg(string(icon, "plugin"), string(icon, "svg"));
    }

    private static List<MenuEntry> entries(TomlTable table) throws IOException {
        if (!(table.get(List.of("entries")) instanceof TomlArray array)) throw new IOException("entries must be an array");
        List<MenuEntry> entries = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof TomlTable entry)) throw new IOException("A menu entry must be a table");
            if (entry.contains(List.of("item"))) entries.add(new MenuEntry.Item(string(entry, "item")));
            else if (entry.contains(List.of("submenu"))) entries.add(new MenuEntry.Submenu(string(entry, "submenu"), entries(entry)));
            else if (Boolean.TRUE.equals(entry.get(List.of("separator")))) entries.add(new MenuEntry.Separator());
            else throw new IOException("Unknown menu entry");
        }
        return entries;
    }

    private static ProgressState progress(TomlTable table) throws IOException {
        Object value = table.get(List.of("progress"));
        if (value == null) return null;
        if (!(value instanceof TomlTable state)) throw new IOException("progress must be a table");
        Object fraction = state.get(List.of("fraction"));
        if (fraction != null && !(fraction instanceof Double)) throw new IOException("fraction must be a number");
        return new ProgressState(string(state, "text"), string(state, "detail"), string(state, "accessible"),
            fraction == null ? OptionalDouble.empty() : OptionalDouble.of((Double) fraction),
            optional(state, "action"), optional(state, "secondary"));
    }

    private static List<PaletteVerb> verbs(TomlTable table) throws IOException {
        if (!(table.get(List.of("verbs")) instanceof TomlArray array)) throw new IOException("verbs must be an array");
        List<PaletteVerb> verbs = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof TomlTable verb)) throw new IOException("A verb must be a table");
            verbs.add(new PaletteVerb(string(verb, "id"), string(verb, "label")));
        }
        return verbs;
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PluginCacheFileTest' --tests 'dev.jasper.app.plugins.TomlTextTest' --tests 'dev.jasper.app.platform.AppDirsTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java jasper-app/src/main/java/dev/jasper/app/plugins/TomlText.java \
  jasper-app/src/main/java/dev/jasper/app/plugins/CachedPlugin.java jasper-app/src/main/java/dev/jasper/app/plugins/PluginCacheFile.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/PluginCacheFileTest.java jasper-app/src/test/java/dev/jasper/app/plugins/TomlTextTest.java \
  jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java
git commit -m "$(cat <<'EOF'
feat(plugins): add the plugin cache file, its key and atomic writes

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Discovery from the cache and class preloading off the EDT

**Files:**
- Modify (whole file): `jasper-app/src/main/java/dev/jasper/app/plugins/PluginDiscovery.java`, `PluginLoader.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginClassLoader.java`, `PluginRuntime.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginPreload.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginPreloadTest.java` (new), `PluginLoaderTest.java`, `PluginDiscoveryTest.java`

**Interfaces:**
- Consumes: Task 4's `CachedPlugin`, `PluginCacheFile`; Task 3's `IconOrigin`, `PluginIconFiles.resource`; Task 1's `Contributed.mapIcons`; Plan 1's `StartupTrace.current().phase(String, long)`.
- Produces:
  - `PluginDiscovery.scan(Path root, Origin origin, List<String> problems, BiFunction<Path, List<Path>, Optional<PluginCandidate>> cache, BiConsumer<Path, String> descriptors)` and the matching six-argument `single(…)`; `PluginDiscovery.NO_CACHE`.
  - `PluginLoader.packages(PluginCandidate): Map<String, String>` (opens every jar), `PluginLoader.load(PluginCandidate, Map<String, String> packages, Map<String, Loaded>, ClassLoader)` (opens none), `PluginLoader.Loaded.packages()`.
  - `PluginClassLoader.definedClasses(): List<String>`.
  - `final class PluginPreload` with `record Discovered(List<PluginCandidate> candidates, List<String> problems, PluginResolver.Resolution resolution, Map<String, CachedPlugin> hits, Map<Path, String> descriptors, Map<Path, List<CachedPlugin.JarStamp>> stamps, Map<IconOrigin.PluginSvg, byte[]> svgs)` (with `boolean cached(PluginCandidate)`), constructor `(PluginRuntime.Options options, Path legacyDataRoot, boolean maintain, Path cacheDirectory, String jasper, Map<String, Map<String, Object>> pluginTables)`, `void run()`, `Discovered discovery()`, `PluginLoader.Loaded loaded(String id) throws PluginLoader.LoadFailure`, `List<PluginClassLoader> createdLoaders()`, `void close()`, `boolean caching()`, `Path cacheDirectory()`, `String jasper()`, `static int preloadClasses(ClassLoader, List<String>, BooleanSupplier stop)`.
  - `public static final class PluginRuntime.Preload` with `public void run()`; `public static PluginRuntime.Preload PluginRuntime.preload(Options options, Path legacyDataRootOrNull, Path cacheDirectory, Path codeSource, Map<String, Map<String, Object>> pluginTables)`; `static String PluginRuntime.jasperVersion(Path codeSource)`; `PluginRuntime.LOCK_WAIT` becomes package-private.

**Linking without initialising.** The JDK offers no API whose specification links a class without initialising it: `ClassLoader.resolveClass` is specified to link but is a no-op in OpenJDK (it only null-checks; checked in the JDK 21 sources), and `Class.forName(name, false, loader)` loads and defines without linking. HotSpot links (and so verifies) a class before answering a reflective member query (in `jvm.cpp`, `JVM_GetClassDeclaredConstructors` and its siblings call `InstanceKlass::link_class`; this is from the HotSpot sources as known, not re-checked against JBR 25 while writing this plan, since only its runtime image is on disk), and the JLS (12.4.1) does not count those queries among the uses that initialise a class. If the executor wants evidence, `-Xlog:class+init=info` on a throwaway `java` run over the fixture jar shows "Start class verification" lines for the preloaded classes; that is optional and not part of `check`. So preloading calls `Class.forName(name, false, loader)` and then `getDeclaredConstructors()`: loading, defining and jar-signature verification are guaranteed; linking and bytecode verification are what HotSpot does today, not a specification guarantee. If a future JDK stopped linking there, preloading would still load and verify signatures, and nothing would break. No static initialiser runs either way; the test below pins that.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/plugins/PluginPreloadTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributed;
import dev.jasper.app.testsupport.PluginJars;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class PluginPreloadTest {
    static final String MAIN = """
        package fix.statics;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            static { System.setProperty("fix.statics.main", "initialised"); }
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) { Helper.touch(); }
        }
        """;
    static final String HELPER = """
        package fix.statics;
        final class Helper {
            static { System.setProperty("fix.statics.helper", "initialised"); }
            static void touch() { }
        }
        """;

    @TempDir Path root;

    @AfterEach void forgetTheSideEffects() {
        System.clearProperty("fix.statics.main");
        System.clearProperty("fix.statics.helper");
    }

    private Path bundled() { return root.resolve("bundled"); }
    private Path cache() { return root.resolve("cache"); }

    private PluginRuntime.Options options() {
        return new PluginRuntime.Options(bundled(), root.resolve("user"), null, false, root.resolve("plugins.toml"), root.resolve("plugins.lock"));
    }

    private PluginPreload preload(String jasper) { return new PluginPreload(options(), null, false, cache(), jasper, Map.of()); }

    private Path plugin(String id, String entry, Map<String, String> sources) throws Exception {
        return PluginJars.build(bundled().resolve(id), id.substring(id.lastIndexOf('.') + 1) + ".jar",
            PluginJars.descriptor(id, "1.2.0", entry), sources, List.of());
    }

    /** What the runtime writes once a plugin has started: its discovery, packages, classes and contributions. */
    private void learn(PluginPreload.Discovered found, String id, Map<String, String> packages, List<String> classes,
                       List<Contributed<IconOrigin>> items) {
        PluginCandidate candidate = found.resolution().load().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
        PluginCacheFile.write(cache(), new CachedPlugin(id, candidate.descriptor().version().toString(), candidate.origin(),
            candidate.directory(), found.stamps().get(candidate.directory()), "test", found.descriptors().get(candidate.directory()),
            packages, classes, items));
    }

    @Test void aCacheHitAnswersDiscoveryWithoutOpeningAJarAndAnyChangeIsAMiss() throws Exception {
        Path jar = plugin("dev.example.tool", "fix.tool.Main", Map.of("fix.tool.Main", PluginJars.emptyPlugin("fix.tool", "Main")));
        var first = preload("test").discovery();
        assertThat(first.hits()).isEmpty();
        learn(first, "dev.example.tool", Map.of("fix.tool", "tool.jar"), List.of("fix.tool.Main"), List.of());

        // Same name, size and modification time, but no longer a readable jar: only the cache can answer.
        FileTime stamp = Files.getLastModifiedTime(jar);
        Files.write(jar, new byte[(int) Files.size(jar)]);
        Files.setLastModifiedTime(jar, stamp);
        var hit = preload("test").discovery();
        assertThat(hit.problems()).isEmpty();
        assertThat(hit.hits()).containsOnlyKeys("dev.example.tool");
        assertThat(hit.resolution().load()).singleElement().satisfies(candidate -> {
            assertThat(candidate.descriptor().version().toString()).isEqualTo("1.2.0");
            assertThat(hit.cached(candidate)).isTrue();
        });

        assertThat(preload("another build").discovery().hits()).as("another Jasper").isEmpty();
        Files.setLastModifiedTime(jar, FileTime.fromMillis(stamp.toMillis() + 5_000));
        var miss = preload("test").discovery();
        assertThat(miss.hits()).isEmpty();
        assertThat(miss.problems()).singleElement().asString().contains("cannot be read");
    }

    @Test void preloadingLoadsTheRecordedClassesWithoutRunningAStaticInitialiser() throws Exception {
        plugin("dev.example.statics", "fix.statics.Main", Map.of("fix.statics.Main", MAIN, "fix.statics.Helper", HELPER));
        learn(preload("test").discovery(), "dev.example.statics", Map.of("fix.statics", "statics.jar"),
            List.of("fix.statics.Main", "fix.statics.Helper"), List.of());

        var preload = preload("test");
        preload.run();
        PluginLoader.Loaded loaded = preload.loaded("dev.example.statics");
        assertThat(loaded.loader().definedClasses()).containsExactlyInAnyOrder("fix.statics.Main", "fix.statics.Helper");
        assertThat(System.getProperty("fix.statics.main")).as("loaded and linked, not initialised").isNull();
        assertThat(System.getProperty("fix.statics.helper")).isNull();

        loaded.instantiate().start(null);
        assertThat(System.getProperty("fix.statics.main")).isEqualTo("initialised");
        assertThat(System.getProperty("fix.statics.helper")).isEqualTo("initialised");
        preload.close();
        for (PluginClassLoader loader : preload.createdLoaders()) loader.close();
    }

    @Test void eachLoaderIsCreatedOnceWhicheverThreadAsksFirst() throws Exception {
        plugin("dev.example.tool", "fix.tool.Main", Map.of("fix.tool.Main", PluginJars.emptyPlugin("fix.tool", "Main")));
        var preload = preload("test");
        var worker = CompletableFuture.runAsync(preload::run);
        PluginLoader.Loaded here = preload.loaded("dev.example.tool");
        worker.get(10, TimeUnit.SECONDS);
        assertThat(preload.loaded("dev.example.tool").loader()).isSameAs(here.loader());
        assertThat(preload.createdLoaders()).containsExactly(here.loader());
        here.loader().close();
    }

    @Test void aHitsRecordedPluginSvgIconsAreReadFromItsJars() throws Exception {
        Path jar = plugin("dev.example.tool", "fix.tool.Main", Map.of("fix.tool.Main", PluginJars.emptyPlugin("fix.tool", "Main")));
        PluginJars.addResource(jar, "icons/tool.svg", PluginIconFilesTest.SVG);
        var svg = new IconOrigin.PluginSvg("dev.example.tool", "icons/tool.svg");
        learn(preload("test").discovery(), "dev.example.tool", Map.of("fix.tool", "tool.jar"), List.of(),
            List.of(new Contributed.Status<>("dev.example.tool.state", true, 1, "", svg, null, null, true, null)));
        var found = preload("test").discovery();
        assertThat(found.svgs()).containsOnlyKeys(svg);
        assertThat(new String(found.svgs().get(svg), StandardCharsets.UTF_8)).isEqualTo(PluginIconFilesTest.SVG);
    }

    @Test void theJasperVersionNamesTheSdkAndTheApplicationBuild() throws Exception {
        Path jar = Files.writeString(root.resolve("jasper-app.jar"), "app");
        Files.setLastModifiedTime(jar, FileTime.fromMillis(1_700_000_000_000L));
        assertThat(PluginRuntime.jasperVersion(jar)).isEqualTo("sdk " + dev.jasper.sdk.JasperSdk.VERSION + "; app jasper-app.jar@1700000000000");
        assertThat(PluginRuntime.jasperVersion(null)).isEqualTo("sdk " + dev.jasper.sdk.JasperSdk.VERSION + "; app none");
    }
}
```

Add to `PluginLoaderTest` (import `java.nio.file.Files`):

```java
    @Test void aKnownPackageScanBuildsTheLoaderWithoutOpeningAJar() throws Exception {
        var candidate = candidate("fix.tool", "", Map.of("fix.tool.Main", PluginJars.emptyPlugin("fix.tool", "Main"),
            "fix.tool.inner.Helper", "package fix.tool.inner;\npublic final class Helper { }\n"), List.of());
        Map<String, String> packages = PluginLoader.packages(candidate);
        assertThat(packages).containsExactlyInAnyOrderEntriesOf(Map.of("fix.tool", "fix.tool.jar", "fix.tool.inner", "fix.tool.jar"));
        assertThatThrownBy(() -> PluginLoader.load(candidate, Map.of("dev.jasper.sdk.evil", "evil.jar"), Map.of(), sdk))
            .isInstanceOf(PluginLoader.LoadFailure.class).hasMessageContaining("evil.jar").hasMessageContaining("dev.jasper.sdk.evil");
        Files.write(candidate.jars().getFirst(), new byte[16]);
        assertThatThrownBy(() -> PluginLoader.packages(candidate)).isInstanceOf(PluginLoader.LoadFailure.class);
        var loaded = PluginLoader.load(candidate, packages, Map.of(), sdk);
        try (var loader = loaded.loader()) {
            assertThat(loaded.packages()).isEqualTo(packages);
        }
    }

    @Test void aLoaderRemembersTheClassesItDefinedInOrder() throws Exception {
        var loaded = PluginLoader.load(candidate("fix.tool", "", Map.of("fix.tool.Main", PluginJars.emptyPlugin("fix.tool", "Main"),
            "fix.tool.inner.Helper", "package fix.tool.inner;\npublic final class Helper { }\n"), List.of()), Map.of(), sdk);
        try (var loader = loaded.loader()) {
            loader.loadClass("fix.tool.inner.Helper");
            loaded.instantiate();
            loader.loadClass("javax.swing.JPanel");
            assertThat(loader.definedClasses()).as("its own classes only").containsExactly("fix.tool.inner.Helper", "fix.tool.Main");
        }
    }
```

Add to `PluginDiscoveryTest` (imports `java.util.HashMap`, `java.util.Optional`):

```java
    @Test void aCacheAnswerOpensNoJarAndDescriptorsReadFromJarsAreReported() throws Exception {
        PluginJars.build(root.resolve("dev.example.a"), "a.jar", PluginJars.descriptor("dev.example.a", "1.0.0", "x.A"), Map.of(), List.of());
        PluginJars.build(root.resolve("dev.example.b"), "b.jar", PluginJars.descriptor("dev.example.b", "1.0.0", "x.B"), Map.of(), List.of());
        var texts = new HashMap<Path, String>();
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> found = PluginDiscovery.scan(root, PluginCandidate.Origin.BUNDLED, problems, (directory, jars) -> {
            if (!directory.getFileName().toString().equals("dev.example.a")) return Optional.empty();
            try { return Optional.of(new PluginCandidate(DescriptorParser.parse(PluginJars.descriptor("dev.example.a", "9.9.9", "x.A")),
                directory, jars, PluginCandidate.Origin.BUNDLED)); }
            catch (DescriptorParser.InvalidDescriptor impossible) { throw new IllegalStateException(impossible); }
        }, texts::put);
        assertThat(problems).isEmpty();
        assertThat(found).extracting(candidate -> candidate.descriptor().version().toString()).containsExactly("9.9.9", "1.0.0");
        assertThat(texts).containsOnlyKeys(root.resolve("dev.example.b"));
        assertThat(texts.get(root.resolve("dev.example.b"))).contains("dev.example.b");
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PluginPreloadTest' --tests 'dev.jasper.app.plugins.PluginLoaderTest' --tests 'dev.jasper.app.plugins.PluginDiscoveryTest'`
Expected: FAIL to compile: `PluginPreload`, `PluginLoader.packages`, the four-argument `PluginLoader.load`, `Loaded.packages`, `PluginClassLoader.definedClasses`, the five-argument `scan` and `PluginRuntime.jasperVersion` do not exist.

- [ ] **Step 3: Replace `PluginDiscovery.java`**

```java
package dev.jasper.app.plugins;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.jar.JarFile;

/**
 * Finds plugin directories and reads their descriptors. Opens jars briefly; loads no classes. An installed
 * plugin keeps its jars in {@code jars/}; a bundled or development directory holds them directly; a folder
 * with no jars is a plugin's settings and data, not a plugin. A cache may answer for a directory from its jar
 * list alone, and then no jar is opened.
 */
final class PluginDiscovery {
    static final String JARS = "jars";
    private static final int MAX_DESCRIPTOR_BYTES = 64 * 1024;
    /** Answers for no directory: every directory's jars are opened. */
    static final BiFunction<Path, List<Path>, Optional<PluginCandidate>> NO_CACHE = (directory, jars) -> Optional.empty();
    private static final BiConsumer<Path, String> IGNORED = (directory, text) -> { };

    private PluginDiscovery() { }

    /** Each subdirectory of {@code root} named after its plugin id. A missing root is simply empty. */
    static List<PluginCandidate> scan(Path root, PluginCandidate.Origin origin, List<String> problems) {
        return scan(root, origin, problems, NO_CACHE, IGNORED);
    }

    /**
     * As {@link #scan(Path, PluginCandidate.Origin, List)}, first asking {@code cache} for each directory given its
     * jars; an answer opens no jar. {@code descriptors} hears the {@code plugin.toml} text of every directory read from its jars.
     */
    static List<PluginCandidate> scan(Path root, PluginCandidate.Origin origin, List<String> problems,
                                      BiFunction<Path, List<Path>, Optional<PluginCandidate>> cache, BiConsumer<Path, String> descriptors) {
        List<PluginCandidate> found = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) return found;
        List<Path> directories;
        try (var children = Files.list(root)) {
            directories = children.filter(Files::isDirectory)
                .filter(path -> !path.getFileName().toString().startsWith(".")).sorted().toList();
        }
        catch (IOException failure) { problems.add(root + ": cannot be listed: " + failure.getMessage()); return found; }
        for (Path directory : directories) {
            Optional<PluginCandidate> candidate = single(directory, origin, problems, true, cache, descriptors);
            if (candidate.isEmpty()) continue;
            String name = directory.getFileName().toString();
            if (!candidate.get().id().equals(name)) {
                problems.add(directory + ": directory " + name + " holds plugin " + candidate.get().id() + "; rename it to the plugin id");
                continue;
            }
            found.add(candidate.get());
        }
        return found;
    }

    /** One plugin directory; used directly for {@code --plugin-dir}, where the directory name is free. */
    static Optional<PluginCandidate> single(Path directory, PluginCandidate.Origin origin, List<String> problems) {
        return single(directory, origin, problems, false);
    }

    /** {@code quietWhenNoJars}: a folder without any jar is skipped without a problem (settings and data of another plugin). */
    static Optional<PluginCandidate> single(Path directory, PluginCandidate.Origin origin, List<String> problems, boolean quietWhenNoJars) {
        return single(directory, origin, problems, quietWhenNoJars, NO_CACHE, IGNORED);
    }

    /** As {@link #single(Path, PluginCandidate.Origin, List, boolean)}, asking {@code cache} first and reporting read descriptors. */
    static Optional<PluginCandidate> single(Path directory, PluginCandidate.Origin origin, List<String> problems, boolean quietWhenNoJars,
                                            BiFunction<Path, List<Path>, Optional<PluginCandidate>> cache, BiConsumer<Path, String> descriptors) {
        Path jarDirectory = Files.isDirectory(directory.resolve(JARS)) ? directory.resolve(JARS) : directory;
        List<Path> jars;
        try (var children = Files.list(jarDirectory)) {
            jars = children.filter(path -> path.getFileName().toString().endsWith(".jar") && Files.isRegularFile(path)).sorted().toList();
        } catch (IOException failure) { problems.add(directory + ": cannot be listed: " + failure.getMessage()); return Optional.empty(); }
        if (jars.isEmpty() && quietWhenNoJars) return Optional.empty();
        Optional<PluginCandidate> cached = cache.apply(directory, jars);
        if (cached.isPresent()) return cached;
        String descriptor = null;
        for (Path jar : jars) {
            try (var file = new JarFile(jar.toFile())) {
                var entry = file.getEntry("plugin.toml");
                if (entry == null) continue;
                if (descriptor != null) { problems.add(directory + ": more than one jar has a plugin.toml"); return Optional.empty(); }
                try (var input = file.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes(MAX_DESCRIPTOR_BYTES + 1);
                    if (bytes.length > MAX_DESCRIPTOR_BYTES) { problems.add(directory + ": plugin.toml is too large"); return Optional.empty(); }
                    descriptor = new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (IOException failure) { problems.add(jar + ": cannot be read: " + failure.getMessage()); return Optional.empty(); }
        }
        if (descriptor == null) { problems.add(directory + ": no plugin.toml in any jar"); return Optional.empty(); }
        try {
            var candidate = new PluginCandidate(DescriptorParser.parse(descriptor), directory, jars, origin);
            descriptors.accept(directory, descriptor);
            return Optional.of(candidate);
        }
        catch (DescriptorParser.InvalidDescriptor invalid) { problems.add(directory + ": " + invalid.getMessage()); return Optional.empty(); }
    }
}
```

- [ ] **Step 4: Replace `PluginLoader.java`; let the loader remember what it defines**

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.plugin.Plugin;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;

/** Builds a plugin's classloader after checking what its jars define; instantiates the entry class. */
final class PluginLoader {
    static final class LoadFailure extends Exception {
        private static final long serialVersionUID = 1L;
        LoadFailure(String message, Throwable cause) { super(message, cause); }
    }

    /** A plugin's loader, and each package its jars define a class in, mapped to the first jar that does. */
    record Loaded(PluginCandidate candidate, PluginClassLoader loader, Map<String, String> packages) {
        Plugin instantiate() throws LoadFailure {
            String entry = candidate.descriptor().entry();
            try {
                Class<?> type = Class.forName(entry, true, loader);
                if (!Plugin.class.isAssignableFrom(type)) throw new LoadFailure(entry + " does not implement Plugin", null);
                return (Plugin) type.getConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new LoadFailure("Cannot create entry class " + entry + ": " + failure, failure);
            }
        }
    }

    private PluginLoader() { }

    /** Opens every jar: each package holding a class, mapped to the first jar, in jar order, that defines one there. */
    static Map<String, String> packages(PluginCandidate candidate) throws LoadFailure {
        Map<String, String> packages = new LinkedHashMap<>();
        for (var jar : candidate.jars()) {
            try (var file = new JarFile(jar.toFile())) {
                var entries = file.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    int slash = name.lastIndexOf('/');
                    if (!name.endsWith(".class") || slash < 0) continue;
                    packages.putIfAbsent(name.substring(0, slash).replace('/', '.'), jar.getFileName().toString());
                }
            } catch (IOException failure) { throw new LoadFailure("Cannot read " + jar + ": " + failure.getMessage(), failure); }
        }
        return packages;
    }

    /** {@code loadedById} must already hold every present dependency, hard or optional. Opens every jar. */
    static Loaded load(PluginCandidate candidate, Map<String, Loaded> loadedById, ClassLoader sdk) throws LoadFailure {
        return load(candidate, packages(candidate), loadedById, sdk);
    }

    /** As {@link #load(PluginCandidate, Map, ClassLoader)} from a known package scan (the plugin cache's): opens no jar. */
    static Loaded load(PluginCandidate candidate, Map<String, String> packages, Map<String, Loaded> loadedById, ClassLoader sdk)
            throws LoadFailure {
        Map<String, ClassLoader> imports = new LinkedHashMap<>();
        for (PluginDescriptor.Requirement requirement : candidate.descriptor().requires()) {
            Loaded dependency = loadedById.get(requirement.id());
            if (dependency == null) continue;
            for (String exported : dependency.candidate().descriptor().exports()) imports.put(exported, dependency.loader());
        }
        for (var entry : packages.entrySet()) {
            if (DescriptorParser.forbidden(entry.getKey()))
                throw new LoadFailure(entry.getValue() + " defines a class in the reserved package " + entry.getKey(), null);
            if (imports.containsKey(entry.getKey()))
                throw new LoadFailure(entry.getValue() + " defines a class in " + entry.getKey() + ", which a dependency exports", null);
        }
        return new Loaded(candidate, new PluginClassLoader(candidate.id(), candidate.jars(), sdk, imports), Map.copyOf(packages));
    }
}
```

In `PluginClassLoader.java` (imports `java.util.Queue`, `java.util.concurrent.ConcurrentLinkedQueue`, `java.util.concurrent.atomic.AtomicInteger`), add after the `imports` field:

```java
    /** How many class names a loader remembers: far more than any plugin defines while it starts. */
    static final int RECORDED = 20_000;
    private final Queue<String> defined = new ConcurrentLinkedQueue<>();
    private final AtomicInteger definedCount = new AtomicInteger();
```

and before `route`:

```java
    /** Defines one of this plugin's own classes, remembering its name for the plugin cache. */
    @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> type = super.findClass(name);
        if (definedCount.getAndIncrement() < RECORDED) defined.add(name);
        return type;
    }

    /** The classes this loader has defined so far, in the order it defined them. Any thread. */
    List<String> definedClasses() { return List.copyOf(defined); }
```

- [ ] **Step 5: Create `PluginPreload.java`**

```java
package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributed;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.sdk.JasperSdk;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/**
 * A launch's plugin work that needs no UI thread: pending maintenance, discovery (from the plugin cache when every jar
 * is unchanged, opening no jar), resolution, class loaders and class preloading. A start-up worker runs it all in
 * order; the UI thread takes over any step the worker has not reached, so nothing ever waits on a worker that never
 * ran. Each step runs once, on whichever thread claims it first; waits follow dependency edges only, which resolution
 * keeps acyclic. Thread-safe.
 */
final class PluginPreload {
    private static final System.Logger LOG = System.getLogger(PluginPreload.class.getName());

    /**
     * What discovery found and decided.
     *
     * @param candidates every plugin found, in discovery order
     * @param problems what could not be used, for the log
     * @param resolution what loads, in start order, and why the rest does not
     * @param hits the valid cache entries, by plugin id
     * @param descriptors each found directory's {@code plugin.toml} text, from its jar or its cache entry
     * @param stamps each bundled or user directory's jar stamps, for the next cache entry's key
     * @param svgs the bytes of the plugin SVG icons that hits recorded, read from their jars
     */
    record Discovered(List<PluginCandidate> candidates, List<String> problems, PluginResolver.Resolution resolution,
                      Map<String, CachedPlugin> hits, Map<Path, String> descriptors,
                      Map<Path, List<CachedPlugin.JarStamp>> stamps, Map<IconOrigin.PluginSvg, byte[]> svgs) {
        Discovered {
            candidates = List.copyOf(candidates); problems = List.copyOf(problems);
            hits = Map.copyOf(hits); descriptors = Map.copyOf(descriptors); stamps = Map.copyOf(stamps); svgs = Map.copyOf(svgs);
        }

        /** Whether this candidate's cache entry is valid, so it may stand in until it starts. Development plugins never are. */
        boolean cached(PluginCandidate candidate) {
            CachedPlugin hit = hits.get(candidate.id());
            return hit != null && candidate.origin() != PluginCandidate.Origin.DEV && hit.origin() == candidate.origin()
                && hit.directory().equals(candidate.directory().toAbsolutePath().normalize());
        }
    }

    private final PluginRuntime.Options options;
    private final Path legacyDataRoot;
    private final boolean maintain;
    private final Path cacheDirectory;
    private final String jasper;
    private final Map<String, Map<String, Object>> pluginTables;
    private final ClassLoader sdk = PluginPreload.class.getClassLoader();
    private final AtomicBoolean discoveryClaimed = new AtomicBoolean();
    private final CompletableFuture<Discovered> discovered = new CompletableFuture<>();
    private final ConcurrentHashMap<String, CompletableFuture<PluginLoader.Loaded>> loaders = new ConcurrentHashMap<>();
    private volatile boolean closed;

    /**
     * @param options locations and mode
     * @param legacyDataRoot the pre-2026-09-22 data root for maintenance, or null
     * @param maintain whether discovery first applies pending removals and installs
     * @param cacheDirectory the plugin cache, or null for none (no hits, nothing written)
     * @param jasper the Jasper version every cache entry's key carries
     * @param pluginTables the configuration's {@code [plugins."<id>"]} tables, to seed a missing settings file
     */
    PluginPreload(PluginRuntime.Options options, Path legacyDataRoot, boolean maintain, Path cacheDirectory, String jasper,
                  Map<String, Map<String, Object>> pluginTables) {
        this.options = options; this.legacyDataRoot = legacyDataRoot; this.maintain = maintain;
        this.cacheDirectory = cacheDirectory; this.jasper = jasper; this.pluginTables = pluginTables;
    }

    boolean caching() { return cacheDirectory != null; }
    Path cacheDirectory() { return cacheDirectory; }
    String jasper() { return jasper; }

    /** The worker's body: discovery, then each plugin's loader and, for a cache hit, its recorded classes. Never throws. */
    void run() {
        try {
            Discovered found = discovery();
            for (PluginCandidate candidate : found.resolution().load()) {
                if (closed) return;
                long began = System.nanoTime();
                try {
                    PluginLoader.Loaded unit = loaded(candidate.id());
                    if (found.cached(candidate)) preloadClasses(unit.loader(), found.hits().get(candidate.id()).classes(), () -> closed);
                } catch (PluginLoader.LoadFailure failure) {
                    LOG.log(System.Logger.Level.DEBUG, "Plugin " + candidate.id() + " cannot be loaded: " + failure.getMessage());
                }
                StartupTrace.current().phase("plugin " + candidate.id() + " preload", began);
            }
        } catch (RuntimeException | LinkageError failure) {
            LOG.log(System.Logger.Level.WARNING, "Plugin preloading stopped; plugins load their classes as they start", failure);
        }
    }

    /** Discovery's result: computed now when no thread has started it, otherwise waited for. */
    Discovered discovery() {
        if (discoveryClaimed.compareAndSet(false, true)) {
            try { discovered.complete(discover()); }
            catch (Throwable failure) { discovered.completeExceptionally(failure); throw failure; }
        }
        try { return discovered.join(); }
        catch (CompletionException failure) { throw rethrown(failure); }
    }

    /** One plugin's loader, created once (after its dependencies') by whichever thread asks first. */
    PluginLoader.Loaded loaded(String id) throws PluginLoader.LoadFailure {
        Discovered found = discovery();
        PluginCandidate candidate = found.resolution().load().stream().filter(other -> other.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Not loaded this launch: " + id));
        var mine = new CompletableFuture<PluginLoader.Loaded>();
        CompletableFuture<PluginLoader.Loaded> existing = loaders.putIfAbsent(id, mine);
        if (existing == null) {
            try { mine.complete(create(candidate, found)); }
            catch (PluginLoader.LoadFailure | RuntimeException failure) { mine.completeExceptionally(failure); }
            catch (Error failure) { mine.completeExceptionally(failure); throw failure; }
        }
        try { return (existing == null ? mine : existing).join(); }
        catch (CompletionException failure) {
            if (failure.getCause() instanceof PluginLoader.LoadFailure load) throw load;
            throw rethrown(failure);
        }
    }

    /** The loaders created so far, for closing at shutdown. */
    List<PluginClassLoader> createdLoaders() {
        List<PluginClassLoader> created = new ArrayList<>();
        for (CompletableFuture<PluginLoader.Loaded> future : loaders.values())
            if (future.isDone() && !future.isCompletedExceptionally()) created.add(future.join().loader());
        return created;
    }

    /** Stops the worker after its current step: the application is going away. */
    void close() { closed = true; }

    /**
     * Loads each class without initialising it, then asks for its declared constructors, before which HotSpot links,
     * and so verifies, the class (JLS 12.4.1: no reflective query initialises). Jar signatures are verified as classes
     * load. A class that fails here is left for the plugin's start to load, or fail on, itself.
     *
     * @return how many classes were loaded
     */
    static int preloadClasses(ClassLoader loader, List<String> names, BooleanSupplier stop) {
        int loaded = 0;
        for (String name : names) {
            if (stop.getAsBoolean()) break;
            try {
                Class<?> type = Class.forName(name, false, loader);
                type.getDeclaredConstructors();
                loaded++;
            } catch (ClassNotFoundException | LinkageError | SecurityException skipped) {
                LOG.log(System.Logger.Level.DEBUG, "Not preloaded: " + name);
            }
        }
        return loaded;
    }

    private Discovered discover() {
        if (maintain)
            PluginMaintenance.apply(options.userDirectory(), new PluginStateStore(options.stateFile(), options.lockFile(), PluginRuntime.LOCK_WAIT),
                legacyDataRoot, Version.parse(JasperSdk.VERSION));
        List<String> problems = new ArrayList<>();
        Map<String, CachedPlugin> hits = new HashMap<>();
        Map<Path, String> descriptors = new HashMap<>();
        Map<Path, List<CachedPlugin.JarStamp>> stamps = new HashMap<>();
        List<PluginCandidate> candidates = new ArrayList<>(PluginDiscovery.scan(options.bundledDirectory(), PluginCandidate.Origin.BUNDLED,
            problems, lookup(PluginCandidate.Origin.BUNDLED, hits, descriptors, stamps), descriptors::put));
        candidates.addAll(PluginDiscovery.scan(options.userDirectory(), PluginCandidate.Origin.USER,
            problems, lookup(PluginCandidate.Origin.USER, hits, descriptors, stamps), descriptors::put));
        if (options.developmentDirectory() != null)
            PluginDiscovery.single(options.developmentDirectory(), PluginCandidate.Origin.DEV, problems, false, PluginDiscovery.NO_CACHE,
                descriptors::put).ifPresent(candidates::add);
        for (PluginCandidate candidate : candidates) {
            try { PluginSettingsFiles.prepare(options.userDirectory(), candidate, pluginTables.get(candidate.id())); }
            catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Could not prepare the settings of " + candidate.id(), failure); }
        }
        Map<String, PluginStateStore.Entry> state;
        try { state = new PluginStateStore(options.stateFile(), options.lockFile(), PluginRuntime.LOCK_WAIT).read(); }
        catch (IOException unreadable) {
            // Without readable consent, user plugins stay inert rather than running unreviewed.
            LOG.log(System.Logger.Level.WARNING, "Plugin state is unreadable; user plugins will need consent", unreadable);
            state = Map.of();
        }
        var resolution = PluginResolver.resolve(candidates, state, Version.parse(JasperSdk.VERSION), options.safeMode());
        return new Discovered(candidates, problems, resolution, hits, descriptors, stamps, svgs(hits, candidates));
    }

    /** Answers a directory from its cache entry when the key still matches, recording the stamps either way. */
    private BiFunction<Path, List<Path>, Optional<PluginCandidate>> lookup(PluginCandidate.Origin origin, Map<String, CachedPlugin> hits,
                                                                         Map<Path, String> descriptors,
                                                                         Map<Path, List<CachedPlugin.JarStamp>> stamps) {
        return (directory, jars) -> {
            List<CachedPlugin.JarStamp> stamped = CachedPlugin.stamps(jars);
            if (stamped == null) return Optional.empty();
            stamps.put(directory, stamped);
            if (cacheDirectory == null) return Optional.empty();
            String id = directory.getFileName().toString();
            Optional<CachedPlugin> cached = PluginCacheFile.read(cacheDirectory, id);
            if (cached.isEmpty() || !cached.get().matches(origin, directory, stamped, jasper)) return Optional.empty();
            try {
                PluginDescriptor descriptor = DescriptorParser.parse(cached.get().descriptor());
                if (!descriptor.id().equals(id) || !descriptor.version().toString().equals(cached.get().version())) return Optional.empty();
                hits.put(id, cached.get());
                descriptors.put(directory, cached.get().descriptor());
                return Optional.of(new PluginCandidate(descriptor, directory, jars, origin));
            } catch (DescriptorParser.InvalidDescriptor | RuntimeException invalid) {
                // A cache entry is never trusted beyond a miss: the jars are read as if there were none.
                return Optional.empty();
            }
        };
    }

    /** The bytes the hits' recorded plugin SVG icons need, read from their plugins' jars without plugin code. */
    private static Map<IconOrigin.PluginSvg, byte[]> svgs(Map<String, CachedPlugin> hits, List<PluginCandidate> candidates) {
        Map<IconOrigin.PluginSvg, byte[]> svgs = new HashMap<>();
        for (CachedPlugin hit : hits.values())
            for (Contributed<IconOrigin> item : hit.items())
                item.mapIcons(origin -> {
                    if (origin instanceof IconOrigin.PluginSvg svg && !svgs.containsKey(svg))
                        candidates.stream().filter(candidate -> candidate.id().equals(svg.pluginId())).findFirst()
                            .flatMap(candidate -> PluginIconFiles.resource(candidate.jars(), svg.path()))
                            .ifPresent(bytes -> svgs.put(svg, bytes));
                    return origin;
                });
        return svgs;
    }

    private PluginLoader.Loaded create(PluginCandidate candidate, Discovered found) throws PluginLoader.LoadFailure {
        Map<String, PluginLoader.Loaded> dependencies = new LinkedHashMap<>();
        for (PluginDescriptor.Requirement requirement : candidate.descriptor().requires()) {
            if (found.resolution().load().stream().noneMatch(other -> other.id().equals(requirement.id()))) continue;
            try { dependencies.put(requirement.id(), loaded(requirement.id())); }
            catch (PluginLoader.LoadFailure unavailable) { /* Its dependents load without its exports and are skipped at start, as before. */ }
        }
        Map<String, String> packages = found.cached(candidate) ? found.hits().get(candidate.id()).packages() : PluginLoader.packages(candidate);
        return PluginLoader.load(candidate, packages, dependencies, sdk);
    }

    private static RuntimeException rethrown(CompletionException failure) {
        if (failure.getCause() instanceof RuntimeException runtime) return runtime;
        if (failure.getCause() instanceof Error error) throw error;
        return failure;
    }
}
```

- [ ] **Step 6: Expose the preload from `PluginRuntime`**

In `PluginRuntime.java`, change `private static final Duration LOCK_WAIT = Duration.ofSeconds(2);` to `static final Duration LOCK_WAIT = Duration.ofSeconds(2);`, and add after the `Outcome` record:

```java
    /**
     * The launch's plugin work for a start-up worker, made by {@link #preload}. {@link #begin} takes over whatever the
     * worker has not reached yet.
     */
    public static final class Preload {
        final PluginPreload work;

        Preload(PluginPreload work) { this.work = work; }

        /**
         * Applies pending removals and installs, discovers plugins (from the plugin cache when nothing changed, opening
         * no jar), builds their class loaders, and loads and links the classes each cached plugin defined while starting,
         * without initialising them. Off the UI thread; never throws.
         */
        public void run() { work.run(); }
    }

    /**
     * Prepares a launch's plugin work for a start-up worker, which calls {@link Preload#run}. Once per launch, before
     * {@link #begin}.
     *
     * @param options locations and mode, as the runtime gets them
     * @param legacyDataRootOrNull the pre-2026-09-22 {@code plugin-data/} directory, migrated once, or null
     * @param cacheDirectory the plugin cache, {@code <home>/cache/plugins}
     * @param codeSource the application's code source, part of every cache entry's key; may be null
     * @param pluginTables the {@code [plugins."<id>"]} tables of the configuration, used only to seed a missing settings file
     * @return the work
     */
    public static Preload preload(Options options, Path legacyDataRootOrNull, Path cacheDirectory, Path codeSource,
                                  Map<String, Map<String, Object>> pluginTables) {
        return new Preload(new PluginPreload(Objects.requireNonNull(options, "options"), legacyDataRootOrNull, true,
            Objects.requireNonNull(cacheDirectory, "cacheDirectory"), jasperVersion(codeSource), Map.copyOf(pluginTables)));
    }

    /** The Jasper version in a cache entry's key: the SDK version and the application jar's name and modification time. */
    static String jasperVersion(Path codeSource) {
        if (codeSource == null) return "sdk " + JasperSdk.VERSION + "; app none";
        long modified = 0;
        try { modified = Files.getLastModifiedTime(codeSource).toMillis(); }
        catch (IOException | RuntimeException unreadable) { /* Zero never equals a real stamp, so such an entry never matches a real build. */ }
        return "sdk " + JasperSdk.VERSION + "; app " + codeSource.getFileName() + "@" + modified;
    }
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*'`
Expected: PASS, including the unchanged `PluginRuntimeTest` (`start` still discovers and loads through the old path until Task 7).

- [ ] **Step 8: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/plugins/PluginPreloadTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/PluginLoaderTest.java jasper-app/src/test/java/dev/jasper/app/plugins/PluginDiscoveryTest.java
git commit -m "$(cat <<'EOF'
feat(plugins): discover from the cache and preload plugin classes off the EDT

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: The event backlog

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/EventBus.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/EventBusTest.java`

**Interfaces:**
- Consumes: the existing `EventBus` (`subscribe`, `publish`, `enqueue`, `deliver`, `removeAll`).
- Produces: `EventBus.BACKLOG_LIMIT` (4096), `void replay(String pluginId)`, `void dropBacklog()`. Recording starts when the bus is built (with the plugin host, before the first window).

- [ ] **Step 1: Write the failing tests**

Add to `EventBusTest`:

```java
    record Pong(int n) { }
    private static final Topic<Pong> PONG = Topic.of("test.alpha.pong", Pong.class);

    @Test void aPluginsStartSubscriptionsReceiveTheBacklogInOrderOnceAndBeforeLaterEvents() {
        List<String> seen = new ArrayList<>();
        bus.publish("test.alpha", PING, new Ping(1));
        bus.publish("test.alpha", PONG, new Pong(2));
        drain();
        bus.publish("test.alpha", PING, new Ping(3));
        bus.subscribe("test.beta", PING, ping -> seen.add("ping " + ping.n()));
        bus.subscribe("test.beta", PONG, pong -> seen.add("pong " + pong.n()));
        bus.replay("test.beta");
        assertThat(seen).as("delivered before it subscribed, in order").containsExactly("ping 1", "pong 2");
        bus.publish("test.alpha", PING, new Ping(4));
        drain();
        assertThat(seen).as("queued when it started, then later: each once").containsExactly("ping 1", "pong 2", "ping 3", "ping 4");
        bus.replay("test.beta");
        assertThat(seen).hasSize(4);
    }

    @Test void whatASubscriptionSawLiveIsNotReplayedAndLaterSubscriptionsGetNoBacklog() {
        List<String> seen = new ArrayList<>();
        bus.subscribe("test.beta", PING, ping -> seen.add("early " + ping.n()));
        bus.publish("test.alpha", PING, new Ping(1));
        drain();
        bus.subscribe(EventBus.APP, PING, ping -> seen.add("app " + ping.n()));
        bus.replay("test.beta");
        bus.replay(EventBus.APP);
        assertThat(seen).containsExactly("early 1");
        bus.subscribe("test.beta", PING, ping -> seen.add("late " + ping.n()));
        bus.replay("test.beta");
        assertThat(seen).containsExactly("early 1");
    }

    @Test void aReplayedHandlerThatFailsIsContainedAndTheRestStillArrive() {
        List<Integer> seen = new ArrayList<>();
        bus.publish("test.alpha", PING, new Ping(1));
        bus.publish("test.alpha", PING, new Ping(2));
        drain();
        bus.subscribe("test.beta", PING, ping -> { if (ping.n() == 1) throw new IllegalStateException("first"); seen.add(ping.n()); });
        bus.replay("test.beta");
        assertThat(seen).containsExactly(2);
        assertThat(containment.failures("test.beta")).isEqualTo(1);
    }

    @Test void theBacklogIsDroppedOnceAllPluginsAreBoundAndIsBounded() {
        bus.publish("test.alpha", PING, new Ping(1));
        drain();
        bus.dropBacklog();
        List<Integer> seen = new ArrayList<>();
        bus.subscribe("test.gamma", PING, ping -> seen.add(ping.n()));
        bus.replay("test.gamma");
        assertThat(seen).isEmpty();

        var flooded = new EventBus(ui::add, containment);
        for (int i = 0; i < EventBus.BACKLOG_LIMIT + 10; i++) flooded.publish("test.alpha", PING, new Ping(i));
        drain();
        List<Integer> replayed = new ArrayList<>();
        flooded.subscribe("test.delta", PING, ping -> replayed.add(ping.n()));
        flooded.replay("test.delta");
        assertThat(replayed).hasSize(EventBus.BACKLOG_LIMIT);
        assertThat(replayed.subList(0, 3)).containsExactly(0, 1, 2);
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.EventBusTest'`
Expected: FAIL to compile: `replay`, `dropBacklog` and `BACKLOG_LIMIT` do not exist.

- [ ] **Step 3: Record deliveries and replay them**

In `EventBus.java`, update the class javadoc's last sentence to add:

```java
 * From launch until every plugin is bound, each delivery is also recorded in a bounded backlog, so a plugin that starts
 * late can receive, right after its start, what it would have heard had it started first.
```

Add imports `java.util.ArrayList`, `java.util.HashSet`, `java.util.List`, `java.util.Set`. Replace the `Entry` record and add the backlog fields after `private final AtomicInteger pending = new AtomicInteger();`:

```java
    /** {@code backlogMark}: how many recorded deliveries this subscription missed; -1 when it gets no replay. */
    private record Entry(String pluginId, String topicId, Consumer<Object> handler, AtomicBoolean closed, int backlogMark) { }
    private record Delivered(String topicId, Object payload) { }

    /** How many deliveries the launch backlog keeps: far more than a start-up produces. */
    static final int BACKLOG_LIMIT = 4096;
    private static final System.Logger LOG = System.getLogger(EventBus.class.getName());
    private final Object backlogLock = new Object();
    /** Every delivery since launch, until every plugin is bound; null afterwards. Guarded by {@code backlogLock}. */
    private List<Delivered> backlog = new ArrayList<>();
    /** Plugins whose start-time subscriptions have had their replay. Guarded by {@code backlogLock}. */
    private final Set<String> replayed = new HashSet<>();
    private boolean truncated;
```

In `subscribe`, replace

```java
        @SuppressWarnings("unchecked")
        var entry = new Entry(pluginId, topic.id(), (Consumer<Object>) handler, new AtomicBoolean());
```

with

```java
        int mark;
        synchronized (backlogLock) {
            mark = backlog == null || pluginId.equals(APP) || replayed.contains(pluginId) ? -1 : backlog.size();
        }
        @SuppressWarnings("unchecked")
        var entry = new Entry(pluginId, topic.id(), (Consumer<Object>) handler, new AtomicBoolean(), mark);
```

Replace `deliver` with:

```java
    /** UI thread: dispatches to the subscribers present now; each handler is contained. Recorded first, while the backlog lasts. */
    void deliver(String topicId, Object payload) {
        record(topicId, payload);
        var list = subscribers.get(topicId);
        if (list == null) return;
        for (Entry entry : list) {
            if (entry.closed().get()) continue;
            if (entry.pluginId().equals(APP)) entry.handler().accept(payload);
            else containment.run(entry.pluginId(), "handler for " + topicId, () -> entry.handler().accept(payload));
        }
    }

    /**
     * Before dispatching, so a subscription made while this event is dispatched (which the dispatch does not reach)
     * counts it as missed.
     */
    private void record(String topicId, Object payload) {
        synchronized (backlogLock) {
            if (backlog == null) return;
            if (backlog.size() < BACKLOG_LIMIT) { backlog.add(new Delivered(topicId, payload)); return; }
            if (truncated) return;
            truncated = true;
        }
        LOG.log(System.Logger.Level.WARNING, "The plugin event backlog is full; plugins that start later miss what follows");
    }

    /**
     * UI thread, right after {@code pluginId}'s start returned: each subscription it has made receives, in order, the
     * recorded deliveries it missed, and nothing it heard live. Deliveries still queued reach it normally afterwards, so
     * nothing arrives twice. Once per plugin; later subscriptions get no replay.
     */
    void replay(String pluginId) {
        List<Delivered> missed;
        synchronized (backlogLock) {
            if (backlog == null || pluginId.equals(APP) || !replayed.add(pluginId)) return;
            missed = List.copyOf(backlog);
        }
        List<Entry> mine = new ArrayList<>();
        for (var list : subscribers.values())
            for (Entry entry : list) if (entry.pluginId().equals(pluginId) && entry.backlogMark() > 0) mine.add(entry);
        if (mine.isEmpty()) return;
        for (int i = 0; i < missed.size(); i++) {
            Delivered event = missed.get(i);
            for (Entry entry : mine) {
                if (i >= entry.backlogMark() || entry.closed().get() || !entry.topicId().equals(event.topicId())) continue;
                containment.run(pluginId, "handler for " + event.topicId(), () -> entry.handler().accept(event.payload()));
            }
        }
    }

    /** Every plugin is bound: the backlog goes, and recording stops. */
    void dropBacklog() {
        synchronized (backlogLock) {
            backlog = null;
            replayed.clear();
        }
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.EventBusTest' --tests 'dev.jasper.app.plugins.PluginHostTest' --tests 'dev.jasper.app.plugins.TerminalBridgeTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins/EventBus.java jasper-app/src/test/java/dev/jasper/app/plugins/EventBusTest.java
git commit -m "$(cat <<'EOF'
feat(plugins): replay the launch's events to a late plugin's start subscriptions

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Binding plugins after the first paint

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/ContributionReplay.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`, `package-info.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginScheduleTest.java` (new), `ContributionReplayTest.java` (new)

**Interfaces:**
- Consumes: Task 1 (`addStandIns`, `bindingStarted`/`bindingFinished`, `dropStandIns`, `hasStandIns`, `contributedBy`, `StandInHooks`, `ActionEntry.standIn`), Task 3 (`IconOrigins`, `PluginHost.icons`, `PluginHost.sourcesNeeded`, `AppIcons.svg`), Task 4 (`CachedPlugin`, `PluginCacheFile`), Task 5 (`PluginPreload`, `PluginRuntime.Preload`, `PluginClassLoader.definedClasses`, `Loaded.packages`), Task 6 (`EventBus.replay`, `dropBacklog`), Plan 1 (`StartupTrace.current().mark(StartupTrace.Milestone.PLUGINS_BOUND)`, `phase`).
- Produces (public on `PluginRuntime`):
  - `void begin(Preload preload, boolean dark, boolean everyPluginNow, Consumer<Runnable> afterFirstPaint)`.
  - `void whenAllBound(Runnable action)`; `void bindRemaining()`.
  - `start(Map, boolean)` keeps its signature and now means "begin without a cache, every plugin now".
- Produces (package-private): `void bindSourceProviders(Set<String> sourceIds)`, `void schedulers(Consumer<Runnable> turn, BiFunction<Duration, Runnable, Runnable> afterDelay)`, `static Set<String> early(PluginPreload.Discovered, boolean)`; `ContributionReplay.replay(Contributions, String, List<Contributed<IconOrigin>>, Map<IconOrigin.PluginSvg, byte[]>, StandInHooks)` and `ContributionReplay.icon(IconOrigin, Map<IconOrigin.PluginSvg, byte[]>)`.
- Removes: the two lines Plan 1 added after the `"Plugins started in "` log line of the old `start` body, `StartupTrace.current().phase("plugins", began);` and `StartupTrace.current().mark(StartupTrace.Milestone.PLUGINS_BOUND);`. The mark moves to the moment the last plugin binds (`finish`); the `plugins` phase now accumulates each plugin's own start (`startPlugin`), so waiting for the first paint is not counted as plugin time.
- Settling (Plan 1) still happens: a cold launch settles on `OUTPUT_PAINTED` + `PLUGINS_BOUND`, and `PLUGINS_BOUND` now follows the first paint by one EDT turn per deferred plugin (well inside Plan 1's 10-second cap, and at most `FIRST_PAINT_FALLBACK` plus those turns when nothing paints); a `--background` launch binds everything inside `begin` (`everyPluginNow`), so `PLUGINS_BOUND` precedes `WARMED_UP`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/plugins/ContributionReplayTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.platform.AppIcons;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ContributionReplayTest {
    @Test void originsBecomeTheSameIconsAgainAndUnknownOnesBecomeNone() {
        assertThat(ContributionReplay.icon(new IconOrigin.Named("TERMINAL"), Map.of())).isSameAs(AppIcons.named("TERMINAL"));
        assertThat(ContributionReplay.icon(new IconOrigin.Bundled("actions/addFile"), Map.of())).isSameAs(AppIcons.bundled("actions/addFile"));
        assertThat(ContributionReplay.icon(new IconOrigin.Named("NO_SUCH_ICON"), Map.of())).isNull();
        assertThat(ContributionReplay.icon(new IconOrigin.Bundled("no/such"), Map.of())).isNull();
        var svg = new IconOrigin.PluginSvg("dev.x", "icons/tool.svg");
        assertThat(ContributionReplay.icon(svg, Map.of())).as("its bytes were not read").isNull();
        assertThat(ContributionReplay.icon(svg, Map.of(svg, PluginIconFilesTest.SVG.getBytes(StandardCharsets.UTF_8)))).isNotNull();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/plugins/PluginScheduleTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.files.ChooserSource;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

class PluginScheduleTest {
    private static final String PLUGIN = """
        package fix.NAME;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import dev.jasper.sdk.ui.ActionSpec;
        import dev.jasper.sdk.ui.Anchor;
        import dev.jasper.sdk.ui.IconName;
        import dev.jasper.sdk.ui.PanelSpec;
        import java.nio.file.Files;
        import java.nio.file.StandardOpenOption;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) throws Exception {
                if (Files.exists(context.dataDirectory().resolve("fail"))) throw new IllegalStateException("asked to fail");
                Files.writeString(context.dataDirectory().resolve("started"), "yes");
                context.actions().register(ActionSpec.of("dev.example.NAME.run", "Run NAME"), invoked -> {
                    try {
                        Files.writeString(context.dataDirectory().resolve("ran"), invoked.window().id() + "\\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                });
                context.panels().register(new PanelSpec("dev.example.NAME.panel", "NAME", context.appearance().icon(IconName.TERMINAL),
                    Anchor.LEFT), host -> new javax.swing.JLabel("NAME"));
            }
        }
        """;

    private static final String SOURCE = """
        package fix.delta;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import dev.jasper.sdk.files.FileBrowser;
        import dev.jasper.sdk.files.FileRoot;
        import dev.jasper.sdk.files.FileSource;
        import dev.jasper.sdk.files.FileSourceSpec;
        import java.util.List;
        import java.util.concurrent.CompletableFuture;
        import java.util.concurrent.CompletionStage;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.fileSources().register(new FileSource() {
                    @Override public FileSourceSpec spec() { return new FileSourceSpec("dev.example.delta.places", "Places"); }
                    @Override public List<FileRoot> roots() { return List.of(); }
                    @Override public dev.jasper.sdk.Subscription onChanged(Runnable listener) { return () -> { }; }
                    @Override public CompletionStage<FileBrowser> connect(String rootId, dev.jasper.sdk.WindowOwner owner) {
                        return CompletableFuture.failedFuture(new java.io.IOException("no roots"));
                    }
                });
            }
        }
        """;

    @TempDir Path root;
    private final BuddyTestSupport deck = new BuddyTestSupport();
    private final List<String> notices = new CopyOnWriteArrayList<>();
    private final Deque<Runnable> turns = new ArrayDeque<>();
    private final List<Runnable> timers = new CopyOnWriteArrayList<>();

    private Path bundled() { return root.resolve("bundled"); }
    private Path cache() { return root.resolve("cache"); }
    private Path data(String name) { return root.resolve("user/dev.example." + name + "/data"); }

    private void plugin(String name) throws Exception {
        String id = "dev.example." + name;
        PluginJars.build(bundled().resolve(id), name + ".jar", PluginJars.descriptor(id, "1.0.0", "fix." + name + ".Main"),
            Map.of("fix." + name + ".Main", PLUGIN.replace("NAME", name)), List.of());
    }

    private PluginRuntime.Options options() {
        return new PluginRuntime.Options(bundled(), root.resolve("user"), null, false, root.resolve("plugins.toml"), root.resolve("plugins.lock"));
    }

    private PluginRuntime.Preload preload() { return PluginRuntime.preload(options(), null, cache(), null, Map.of()); }

    private PluginRuntime runtime(Contributions model, TerminalRegistry terminals) {
        return onEdtValue(() -> {
            var runtime = new PluginRuntime(options(), new ActivityNotifier(deck.companion(), () -> { }), PluginRuntime.SettingsReports.IGNORED,
                model, AppContractTest.headlessWindows(), terminals, notices::add, path -> { });
            runtime.schedulers(turns::add, (delay, task) -> { timers.add(task); return () -> timers.remove(task); });
            return runtime;
        });
    }

    private static void stop(PluginRuntime runtime) throws Exception {
        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(runtime.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
    }

    /** A launch with nothing cached: every plugin starts before the first frame, and quitting writes the cache. */
    private void firstLaunch(String... started) throws Exception {
        var model = onEdtValue(Contributions::new);
        PluginRuntime runtime = runtime(model, onEdtValue(TerminalRegistry::new));
        var trigger = new AtomicReference<Runnable>();
        onEdt(() -> runtime.begin(preload(), true, false, trigger::set));
        assertThat(trigger.get()).as("nothing waits for a paint").isNull();
        for (String name : started) assertThat(data(name).resolve("started")).exists();
        stop(runtime);
        for (String name : started) Files.delete(data(name).resolve("started"));
        timers.clear();
    }

    @Test void aPluginWithoutAValidCacheStartsBeforeTheFirstFrameAndQuittingRecordsIt() throws Exception {
        plugin("alpha");
        firstLaunch("alpha");
        assertThat(cache().resolve("dev.example.alpha.toml")).exists().content()
            .contains("dev.example.alpha.run", "dev.example.alpha.panel", "fix.alpha.Main", "named = \"TERMINAL\"");
    }

    @Test void cachedPluginsStandInUntilTheFirstPaintThenStartOnePerTurnInOrder() throws Exception {
        plugin("alpha");
        plugin("beta");
        firstLaunch("alpha", "beta");
        var model = onEdtValue(Contributions::new);
        PluginRuntime runtime = runtime(model, onEdtValue(TerminalRegistry::new));
        var preload = preload();
        preload.run();
        var trigger = new AtomicReference<Runnable>();
        var bound = new AtomicBoolean();
        onEdt(() -> {
            runtime.begin(preload, true, false, trigger::set);
            runtime.whenAllBound(() -> bound.set(true));
            assertThat(model.action("dev.example.alpha.run")).get().satisfies(action -> assertThat(action.standIn()).isTrue());
            assertThat(model.panels()).extracting(PanelEntry::id).containsExactly("dev.example.alpha.panel", "dev.example.beta.panel");
        });
        assertThat(data("alpha").resolve("started")).doesNotExist();
        assertThat(turns).as("nothing before the first paint").isEmpty();

        onEdt(trigger.get());
        onEdt(() -> turns.poll().run());
        assertThat(data("alpha").resolve("started")).exists();
        assertThat(data("beta").resolve("started")).doesNotExist();
        assertThat(bound).isFalse();
        onEdt(() -> turns.poll().run());
        assertThat(data("beta").resolve("started")).exists();
        assertThat(bound).isTrue();
        assertThat(turns).isEmpty();
        onEdt(() -> assertThat(model.action("dev.example.alpha.run")).get().satisfies(action -> assertThat(action.standIn()).isFalse()));
        stop(runtime);
    }

    @Test void openPanelsComeFirstThenQueuedInvocationsWhichRunOnlyInWindowsThatStillExist() throws Exception {
        plugin("alpha");
        plugin("beta");
        plugin("gamma");
        firstLaunch("alpha", "beta", "gamma");
        var model = onEdtValue(Contributions::new);
        TerminalFixture fixture = onEdtValue(TerminalFixture::new);
        UUID window = onEdtValue(fixture::addWindow);
        PluginRuntime runtime = runtime(model, fixture.registry);
        var trigger = new AtomicReference<Runnable>();
        onEdt(() -> {
            runtime.begin(preload(), true, false, trigger::set);
            var gamma = model.action("dev.example.gamma.run").orElseThrow();
            gamma.invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
            gamma.invoke(new Contributions.Invocation(window, Optional.empty()));
            PanelEntry beta = model.panels().stream().filter(panel -> panel.id().equals("dev.example.beta.panel")).findFirst().orElseThrow();
            beta.factory().apply(new PanelSite(window, () -> { }, () -> { }, () -> true));
        });
        assertThat(turns).as("nothing before the first paint, whatever was asked").isEmpty();
        onEdt(trigger.get());
        List<String> order = new ArrayList<>();
        for (int turn = 0; turn < 3; turn++) {
            onEdt(() -> turns.poll().run());
            for (String name : List.of("alpha", "beta", "gamma"))
                if (Files.exists(data(name).resolve("started")) && !order.contains(name)) order.add(name);
        }
        assertThat(order).containsExactly("beta", "gamma", "alpha");
        assertThat(Files.readString(data("gamma").resolve("ran"))).as("once, in the window that still exists").isEqualTo(window + "\n");
        stop(runtime);
    }

    @Test void aFailedStartRemovesItsStandInsDropsItsInvocationsWithANoticeAndForgetsItsCache() throws Exception {
        plugin("alpha");
        firstLaunch("alpha");
        Files.writeString(data("alpha").resolve("fail"), "yes");
        var model = onEdtValue(Contributions::new);
        TerminalFixture fixture = onEdtValue(TerminalFixture::new);
        UUID window = onEdtValue(fixture::addWindow);
        PluginRuntime runtime = runtime(model, fixture.registry);
        var trigger = new AtomicReference<Runnable>();
        onEdt(() -> {
            runtime.begin(preload(), true, false, trigger::set);
            model.action("dev.example.alpha.run").orElseThrow().invoke(new Contributions.Invocation(window, Optional.empty()));
        });
        onEdt(trigger.get());
        onEdt(() -> turns.poll().run());
        onEdt(() -> {
            assertThat(model.action("dev.example.alpha.run")).isEmpty();
            assertThat(model.panels()).isEmpty();
            assertThat(runtime.statusLines()).singleElement().asString().contains("FAILED");
        });
        assertThat(notices).containsExactly("dev.example.alpha could not start, so Run alpha did not run.");
        stop(runtime);
        assertThat(cache().resolve("dev.example.alpha.toml")).doesNotExist();
    }

    @Test void theChooserStartsTheSourcesItNeedsAndSettingsStartsEveryRemainingPluginAtOnce() throws Exception {
        plugin("alpha");
        PluginJars.build(bundled().resolve("dev.example.delta"), "delta.jar",
            PluginJars.descriptor("dev.example.delta", "1.0.0", "fix.delta.Main") + "capabilities = [\"files.source\"]\n",
            Map.of("fix.delta.Main", SOURCE), List.of());
        firstLaunch("alpha");
        var model = onEdtValue(Contributions::new);
        PluginRuntime runtime = runtime(model, onEdtValue(TerminalRegistry::new));
        onEdt(() -> {
            runtime.begin(preload(), true, false, action -> { });
            assertThat(model.fileSources()).isEmpty();
            runtime.bindSourceProviders(Set.of("dev.example.delta.places", "dev.example.nobody.places"));
            assertThat(model.fileSources()).extracting(ChooserSource::id).containsExactly("dev.example.delta.places");
            assertThat(Files.exists(data("alpha").resolve("started"))).isFalse();
            runtime.bindRemaining();
            assertThat(Files.exists(data("alpha").resolve("started"))).isTrue();
        });
        stop(runtime);
    }

    @Test void theCacheIsWrittenAfterThreeQuietSecondsAndAChangeRestartsTheWait() throws Exception {
        plugin("alpha");
        var model = onEdtValue(Contributions::new);
        PluginRuntime runtime = runtime(model, onEdtValue(TerminalRegistry::new));
        onEdt(() -> runtime.begin(preload(), true, false, action -> { }));
        assertThat(timers).as("the thirty-second cap and the quiet timer").hasSize(2);
        Runnable firstQuiet = timers.getLast();
        onEdt(() -> model.addAction("app.late", "Late", null, List.of(), Optional.empty(), invocation -> { }));
        assertThat(timers).as("restarted, not added").hasSize(2).doesNotContain(firstQuiet);
        Path file = cache().resolve("dev.example.alpha.toml");
        assertThat(file).doesNotExist();
        onEdt(() -> timers.getLast().run());
        assertThat(timers).as("both cancelled once written").isEmpty();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.exists(file) && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(file).exists();
        stop(runtime);
    }

    @Test void quittingDuringWarmUpKeepsTheUnstartedPluginsCacheAndStopsAtOnce() throws Exception {
        plugin("alpha");
        plugin("beta");
        firstLaunch("alpha", "beta");
        String betaBefore = Files.readString(cache().resolve("dev.example.beta.toml"));
        var model = onEdtValue(Contributions::new);
        PluginRuntime runtime = runtime(model, onEdtValue(TerminalRegistry::new));
        var trigger = new AtomicReference<Runnable>();
        onEdt(() -> runtime.begin(preload(), true, false, trigger::set));
        onEdt(trigger.get());
        onEdt(() -> turns.poll().run());
        assertThat(data("alpha").resolve("started")).exists();
        stop(runtime);
        assertThat(data("beta").resolve("started")).as("never started").doesNotExist();
        assertThat(Files.readString(cache().resolve("dev.example.beta.toml"))).isEqualTo(betaBefore);
        assertThat(cache().resolve("dev.example.alpha.toml")).exists();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PluginScheduleTest' --tests 'dev.jasper.app.plugins.ContributionReplayTest'`
Expected: FAIL to compile: `PluginRuntime.begin`, `schedulers`, `whenAllBound`, `bindRemaining`, `bindSourceProviders` and `ContributionReplay` do not exist.

- [ ] **Step 3: Create `ContributionReplay.java`**

```java
package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributed;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.StandInHooks;
import dev.jasper.app.platform.AppIcons;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;

/**
 * Replays a plugin's cached contributions as stand-ins before it starts: recorded icon origins become icons again
 * without plugin code, then the model adds the items in their recorded order. EDT.
 */
final class ContributionReplay {
    private ContributionReplay() { }

    static void replay(Contributions model, String owner, List<Contributed<IconOrigin>> items,
                       Map<IconOrigin.PluginSvg, byte[]> svgs, StandInHooks hooks) {
        model.addStandIns(owner, items.stream().map(item -> item.mapIcons(origin -> icon(origin, svgs))).toList(), hooks);
    }

    /** The icon an origin names; null when this build does not know it, so the stand-in simply has no icon. */
    static Icon icon(IconOrigin origin, Map<IconOrigin.PluginSvg, byte[]> svgs) {
        try {
            return switch (origin) {
                case IconOrigin.Named named -> AppIcons.named(named.name());
                case IconOrigin.Bundled bundled -> AppIcons.bundled(bundled.id());
                case IconOrigin.PluginSvg svg -> svgs.containsKey(svg) ? AppIcons.svg(svgs.get(svg)) : null;
            };
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Restructure `PluginRuntime` around `begin`**

Imports to add: `dev.jasper.app.contributions.ActionEntry`, `dev.jasper.app.contributions.Contributed`, `dev.jasper.app.contributions.StandInHooks`, `dev.jasper.app.startup.StartupTrace`, `java.util.Collection`, `java.util.HashMap`, `java.util.LinkedHashSet`, `java.util.function.BiFunction`, `java.util.stream.Collectors`.

Replace the class javadoc with:

```java
/**
 * The application's single entry to plugins: discover, resolve, load and start them once per launch,
 * forward configuration and theme changes, show their activities on Buddy, and stop them at shutdown.
 * A plugin with a valid entry in the plugin cache stands in through its recorded contributions and starts
 * after the first window has painted, one per UI-thread turn; the others start before the first window.
 * Install, enable, disable and update take effect at the next start. EDT only, except {@link #executing},
 * {@link #maintain}, {@link #preload} and {@link Preload#run}.
 */
```

Delete the field `private final List<PluginClassLoader> loaders = new ArrayList<>();` and add, after `private ExecutorService adminWorker;`:

```java
    private static final Duration QUIET = Duration.ofSeconds(3);
    private static final Duration QUIET_CAP = Duration.ofSeconds(30);

    /** A stand-in action's invocation, run once its plugin has started. */
    private record Queued(String actionId, String title, Contributions.Invocation invocation) { }

    private PluginPreload preload;
    private PluginPreload.Discovered discovered;
    /** Every plugin this launch loads, in start order. */
    private final Map<String, PluginCandidate> loading = new LinkedHashMap<>();
    /** Plugins standing in until their turn after the first paint, in start order. */
    private final Map<String, PluginCandidate> pending = new LinkedHashMap<>();
    private final Set<String> panelFirst = new LinkedHashSet<>();
    private final Set<String> invokedFirst = new LinkedHashSet<>();
    private final Map<String, List<Queued>> queued = new HashMap<>();
    private final Map<String, List<String>> startClasses = new HashMap<>();
    private final Map<String, Map<String, String>> packages = new HashMap<>();
    private final List<Runnable> whenBound = new ArrayList<>();
    /** Which plugin's cached contributions name each file source, for a chooser that needs one now. */
    private Map<String, String> sourceOwners = Map.of();
    private Consumer<Runnable> turn = SwingUtilities::invokeLater;
    private BiFunction<Duration, Runnable, Runnable> afterDelay = PluginRuntime::swingTimer;
    private boolean beginning, binding, scheduling, turnPosted, allBound, stopped, cachesWritten;
    private long began;
    private dev.jasper.app.lifecycle.Subscription quiet;
    private Runnable quietTimer = () -> { };
    private Runnable quietCap = () -> { };
    private CompletableFuture<Void> cacheWrite = CompletableFuture.completedFuture(null);
```

Replace the whole `start(Map<String, Map<String, Object>> pluginTables, boolean dark)` method with:

```java
    /**
     * Discovers, resolves, loads and starts every plugin now, in dependency order, without the plugin cache: for
     * launches without a start-up worker (tests, previews). Call once, before the first window.
     *
     * @param pluginTables the {@code [plugins."<id>"]} tables of the configuration, used only to seed a missing settings file
     * @param dark whether the current look is dark
     */
    public void start(Map<String, Map<String, Object>> pluginTables, boolean dark) {
        begin(new Preload(new PluginPreload(options, null, false, null, "", pluginTables)), dark, true, action -> { });
    }

    /**
     * Starts plugins for a launch, once, before the first window. Discovery is {@code preload}'s (its worker's result,
     * or done now when the worker has not got that far). Unless {@code everyPluginNow}, a plugin with a valid cache entry
     * stands in through its recorded contributions and starts after {@code afterFirstPaint} fires, one per UI-thread
     * turn: plugins with an open panel first, then plugins with queued invocations, then the rest in dependency order.
     * Every other plugin starts now, with what it depends on, in start order with the replays.
     *
     * @param preload the launch's plugin work, from {@link #preload} (or made by {@link #start})
     * @param dark whether the current look is dark
     * @param everyPluginNow start every plugin now, as a resident does before any window exists
     * @param afterFirstPaint runs its argument on the UI thread once the first window has painted
     */
    public void begin(Preload preload, boolean dark, boolean everyPluginNow, Consumer<Runnable> afterFirstPaint) {
        if (host != null) throw new IllegalStateException("Plugins already started");
        Objects.requireNonNull(afterFirstPaint, "afterFirstPaint");
        this.dark = dark;
        began = System.nanoTime();
        this.preload = Objects.requireNonNull(preload, "preload").work;
        PluginPreload.Discovered found = this.preload.discovery();
        discovered = found;
        var byId = new LinkedHashMap<String, PluginCandidate>();
        for (PluginCandidate candidate : found.candidates()) byId.putIfAbsent(candidate.id(), candidate);
        launched = Map.copyOf(byId);
        statuses.addAll(found.resolution().rejected());
        PluginHost created = new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, id -> options.userDirectory().resolve(id).resolve("data"),
            reports.report(), DRAIN_GRACE, contributions, () -> this.dark, windows, terminals, notice, editor, settingsFiles, reports.clear(), profiles,
            dev.jasper.app.platform.Browser::open, appearance, appearanceSubscriber));
        host = created;
        created.sourcesNeeded = this::bindSourceProviders;
        bridge = created.bus.subscribe(EventBus.APP, Activities.TOPIC, this::forward);
        connectionBridge = created.bus.subscribe(EventBus.APP, Activities.CONNECTIONS, this::forwardConnection);
        settingsPoller = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("jasper-plugin-settings").factory());
        settingsPoller.scheduleWithFixedDelay(() -> {
            Set<String> changed = settingsFiles.refresh(false);
            if (!changed.isEmpty()) SwingUtilities.invokeLater(() -> applySettings(changed));
        }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
        found.problems().forEach(problem -> LOG.log(System.Logger.Level.WARNING, "Plugin discovery: " + problem));
        Set<String> early = early(found, everyPluginNow);
        Map<String, String> owners = new HashMap<>();
        beginning = true;
        try {
            for (PluginCandidate candidate : found.resolution().load()) {
                loading.put(candidate.id(), candidate);
                if (early.contains(candidate.id())) { bind(candidate); continue; }
                pending.put(candidate.id(), candidate);
                List<Contributed<IconOrigin>> recorded = found.hits().get(candidate.id()).items();
                ContributionReplay.replay(contributions, candidate.id(), recorded, found.svgs(), hooks(candidate.id()));
                for (Contributed<IconOrigin> item : recorded)
                    if (item instanceof Contributed.FileSource<IconOrigin> source) owners.put(source.id(), candidate.id());
            }
        } finally { beginning = false; }
        sourceOwners = Map.copyOf(owners);
        if (pending.isEmpty()) finish();
        else afterFirstPaint.accept(this::openSchedule);
    }

    /** Every plugin, or each one without a valid cache entry, with everything it depends on. */
    static Set<String> early(PluginPreload.Discovered found, boolean everyPluginNow) {
        Map<String, PluginCandidate> load = new LinkedHashMap<>();
        for (PluginCandidate candidate : found.resolution().load()) load.put(candidate.id(), candidate);
        Set<String> early = new HashSet<>();
        for (PluginCandidate candidate : load.values())
            if (everyPluginNow || !found.cached(candidate)) withDependencies(candidate, load, early);
        return early;
    }

    private static void withDependencies(PluginCandidate candidate, Map<String, PluginCandidate> load, Set<String> into) {
        if (!into.add(candidate.id())) return;
        for (var requirement : candidate.descriptor().requires()) {
            PluginCandidate dependency = load.get(requirement.id());
            if (dependency != null) withDependencies(dependency, load, into);
        }
    }

    /** What a plugin's stand-ins report while it waits for its turn. */
    private StandInHooks hooks(String id) {
        return new StandInHooks((actionId, invocation) -> {
                String title = contributions.action(actionId).map(ActionEntry::title).orElse(actionId);
                queued.computeIfAbsent(id, key -> new ArrayList<>()).add(new Queued(actionId, title, invocation));
                invokedFirst.add(id);
                scheduleNext();
            },
            () -> { panelFirst.add(id); scheduleNext(); },
            () -> { invokedFirst.add(id); scheduleNext(); });
    }

    /** The first window has painted (or the fallback fired): pending plugins start from now on. */
    private void openSchedule() {
        scheduling = true;
        scheduleNext();
    }

    private void scheduleNext() {
        if (!scheduling || turnPosted || stopped || pending.isEmpty()) return;
        turnPosted = true;
        turn.accept(this::bindTurn);
    }

    /** One UI-thread turn: one plugin, so input and painting interleave with start-up. */
    private void bindTurn() {
        turnPosted = false;
        // A start that pumps a nested event loop reaches here mid-binding; its own end schedules the next turn.
        if (stopped || binding || pending.isEmpty()) return;
        bind(next());
    }

    /** Open panels' plugins first, then plugins with queued invocations, then start order; always a dependency first. */
    private PluginCandidate next() {
        String wanted = firstPending(panelFirst);
        if (wanted == null) wanted = firstPending(invokedFirst);
        PluginCandidate target = wanted != null ? pending.get(wanted) : pending.values().iterator().next();
        return firstPendingDependency(target);
    }

    private String firstPending(Set<String> ids) {
        for (String id : ids) if (pending.containsKey(id)) return id;
        return null;
    }

    private PluginCandidate firstPendingDependency(PluginCandidate candidate) {
        for (var requirement : candidate.descriptor().requires()) {
            PluginCandidate dependency = pending.get(requirement.id());
            if (dependency != null) return firstPendingDependency(dependency);
        }
        return candidate;
    }

    /** Starts one plugin whose dependencies have had their turn, then runs or drops what was queued for it. UI thread. */
    private void bind(PluginCandidate candidate) {
        String id = candidate.id();
        pending.remove(id);
        PluginStatus.State state = startPlugin(candidate);
        panelFirst.remove(id);
        invokedFirst.remove(id);
        if (state == PluginStatus.State.ACTIVE) runQueued(id); else dropQueued(candidate);
        if (beginning) return;
        if (pending.isEmpty()) finish(); else scheduleNext();
    }

    private PluginStatus.State startPlugin(PluginCandidate candidate) {
        String id = candidate.id();
        long started = System.nanoTime();
        binding = true;
        try {
            PluginLoader.Loaded unit;
            try { unit = preload.loaded(id); }
            catch (PluginLoader.LoadFailure failure) {
                statuses.add(PluginStatus.of(candidate, PluginStatus.State.FAILED, failure.getMessage()));
                if (contributions.hasStandIns(id)) contributions.dropStandIns(id);
                return PluginStatus.State.FAILED;
            }
            packages.put(id, unit.packages());
            Set<String> hard = new HashSet<>(), optional = new HashSet<>();
            for (var requirement : candidate.descriptor().requires())
                (requirement.optional() ? optional : hard).add(requirement.id());
            PluginHost.Outcome outcome;
            contributions.bindingStarted(id);
            try {
                outcome = host.start(new HostedPlugin(candidate.descriptor().info(), hard, optional,
                    candidate.descriptor().exports(), unit.loader(), unit::instantiate));
                host.bus.replay(id);
            } finally {
                contributions.bindingFinished(id);
            }
            startClasses.put(id, unit.loader().definedClasses());
            statuses.add(PluginStatus.of(candidate, outcome.state(), outcome.reason()));
            return outcome.state();
        } finally {
            binding = false;
            // "plugins" accumulates every plugin's start, as Plan 1 measured it; the per-plugin phase names the slow one.
            StartupTrace.current().phase("plugins", started);
            StartupTrace.current().phase("plugin " + id, started);
        }
    }

    /** Runs what users asked of the plugin's stand-ins, in order, where the action still exists and the window is open. */
    private void runQueued(String id) {
        List<Queued> waiting = queued.remove(id);
        if (waiting == null) return;
        for (Queued call : waiting) {
            if (terminals.window(call.invocation().windowId()).isEmpty()) continue;
            contributions.action(call.actionId()).filter(action -> !action.standIn()).ifPresent(action -> action.invoke(call.invocation()));
        }
    }

    private void dropQueued(PluginCandidate candidate) {
        List<Queued> waiting = queued.remove(candidate.id());
        if (waiting == null || waiting.isEmpty()) return;
        String titles = waiting.stream().map(Queued::title).distinct().collect(Collectors.joining(", "));
        notice.accept(candidate.descriptor().name() + " could not start, so " + titles + " did not run.");
    }

    /** Starts these pending plugins, and what they need, now. Does nothing while another plugin is starting. */
    private void bindNow(Collection<String> ids) {
        if (binding || host == null) return;
        for (String id : List.copyOf(ids))
            while (pending.containsKey(id)) bind(firstPendingDependency(pending.get(id)));
    }

    /** Starts every plugin still waiting for its turn, now: Settings shows every plugin's page. UI thread. */
    public void bindRemaining() { bindNow(List.copyOf(pending.keySet())); }

    /** A chooser names these sources: their plugins, if still waiting, start now. UI thread. */
    void bindSourceProviders(Set<String> sourceIds) {
        bindNow(sourceIds.stream().map(sourceOwners::get).filter(Objects::nonNull).distinct().toList());
    }

    /**
     * Runs {@code action} once every plugin has started or failed to, now if that has happened. UI thread.
     *
     * @param action what waits for the plugins
     */
    public void whenAllBound(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (allBound) action.run(); else whenBound.add(action);
    }

    /** Every plugin has had its turn: what waited for that happens now. */
    private void finish() {
        if (allBound) return;
        allBound = true;
        host.bus.dropBacklog();
        launch = new PluginCatalog.Launch(discovered.candidates(), statuses, PluginCatalog.versions(discovered.resolution().load()),
            options.safeMode());
        admin = new PluginAdmin(options, Version.parse(JasperSdk.VERSION), () -> launch, host.containment::failures, LOCK_WAIT);
        adminWorker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("jasper-plugin-admin").factory());
        statusLines().forEach(line -> LOG.log(System.Logger.Level.INFO, "Plugin " + line));
        LOG.log(System.Logger.Level.INFO, "Plugins bound " + (System.nanoTime() - began) / 1_000_000 + " ms after they began");
        StartupTrace.current().mark(StartupTrace.Milestone.PLUGINS_BOUND);
        List<Runnable> waiting = List.copyOf(whenBound);
        whenBound.clear();
        waiting.forEach(Runnable::run);
        if (preload.caching()) watchForQuiet();
    }

    /** The cache records the steady state: three seconds without a contribution change, at most thirty, or quit. */
    private void watchForQuiet() {
        quietCap = afterDelay.apply(QUIET_CAP, this::settled);
        quiet = contributions.onChanged(kind -> restartQuietTimer());
        restartQuietTimer();
    }

    private void restartQuietTimer() {
        quietTimer.run();
        quietTimer = afterDelay.apply(QUIET, this::settled);
    }

    private void settled() {
        if (!cachesWritten && !stopped) cacheWrite = writeCaches();
    }

    private void cancelQuiet() {
        if (quiet != null) { quiet.close(); quiet = null; }
        quietTimer.run();
        quietTimer = () -> { };
        quietCap.run();
        quietCap = () -> { };
    }

    /**
     * Records every started plugin from the live model, off the UI thread: its contributions with their icons' origins,
     * its discovery, packages and start-time classes. A plugin that failed or was skipped loses its entry, so its next
     * launch starts it before the first window; a plugin still waiting keeps its entry; development plugins have none.
     */
    private CompletableFuture<Void> writeCaches() {
        cachesWritten = true;
        cancelQuiet();
        Path directory = preload.cacheDirectory();
        List<CachedPlugin> writes = new ArrayList<>();
        List<String> deletes = new ArrayList<>();
        for (PluginCandidate candidate : loading.values()) {
            String id = candidate.id();
            if (candidate.origin() == PluginCandidate.Origin.DEV || pending.containsKey(id)) continue;
            if (!host.active(id)) { deletes.add(id); continue; }
            List<CachedPlugin.JarStamp> stamps = discovered.stamps().get(candidate.directory());
            String descriptor = discovered.descriptors().get(candidate.directory());
            Map<String, String> scanned = packages.get(id);
            if (stamps == null || descriptor == null || scanned == null) continue;
            List<Contributed<IconOrigin>> items = contributions.contributedBy(id).stream()
                .map(item -> item.mapIcons(icon -> host.icons.origin(icon).orElse(null))).toList();
            var entry = new CachedPlugin(id, candidate.descriptor().version().toString(), candidate.origin(), candidate.directory(), stamps,
                preload.jasper(), descriptor, scanned, startClasses.getOrDefault(id, List.of()), items);
            if (!entry.equals(discovered.hits().get(id))) writes.add(entry);
        }
        host.icons.stop();
        if (writes.isEmpty() && deletes.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            for (CachedPlugin entry : writes) PluginCacheFile.write(directory, entry);
            for (String id : deletes) PluginCacheFile.delete(directory, id);
        }, work -> Thread.ofPlatform().daemon().name("jasper-plugin-cache").start(work));
    }

    /** Test seam: where binding turns and the quiet timers go. Before {@link #begin}. */
    void schedulers(Consumer<Runnable> turn, BiFunction<Duration, Runnable, Runnable> afterDelay) {
        this.turn = Objects.requireNonNull(turn, "turn");
        this.afterDelay = Objects.requireNonNull(afterDelay, "afterDelay");
    }

    private static Runnable swingTimer(Duration delay, Runnable task) {
        var timer = new javax.swing.Timer((int) Math.max(1, Math.min(Integer.MAX_VALUE, delay.toMillis())), event -> task.run());
        timer.setRepeats(false);
        timer.start();
        return timer::stop;
    }
```

Replace `stop()` with:

```java
    /**
     * Stops plugins in reverse order, first writing the plugin cache from the live model when start-up has not settled
     * yet. The futures complete when background work has drained, the cache is written and the classloaders are closed;
     * the caller bounds the wait.
     *
     * @return asynchronous remainders of the shutdown
     */
    public List<CompletableFuture<?>> stop() {
        stopped = true;
        if (settingsPoller != null) settingsPoller.shutdownNow();
        PluginHost current = host;
        if (current == null) return List.of();
        List<CompletableFuture<?>> work = new ArrayList<>();
        work.add(preload.caching() && !cachesWritten ? writeCaches() : cacheWrite);
        cancelQuiet();
        if (adminWorker != null) adminWorker.shutdown();
        work.addAll(current.stop());
        preload.close();
        List<PluginClassLoader> closing = preload.createdLoaders();
        work.add(CompletableFuture.allOf(work.toArray(CompletableFuture[]::new)).whenComplete((done, failure) -> {
            for (PluginClassLoader loader : closing) {
                try { loader.close(); }
                catch (IOException ignored) { LOG.log(System.Logger.Level.DEBUG, "Could not close " + loader.getName()); }
            }
        }));
        return work;
    }
```

At the start of `settingsPages(PageContext context)`, before `PluginHost current = host;`, add:

```java
        // Settings shows every plugin's page, so plugins still waiting for their turn start now.
        bindRemaining();
```

In `plugins/package-info.java`, extend the first sentence and the allowed list:

```java
/**
 * The plugin runtime: descriptors, locked consent state, resolution, per-plugin classloaders, the
 * queued EDT event bus with its launch backlog, activities, the service registry, plugin lifetimes, the plugin cache
 * and contribution replay. {@link dev.jasper.app.plugins.PluginRuntime}
 * is the only public type and is owned and closed by the application. This is the only application
 * package that may reference {@code dev.jasper.sdk}; everything it needs from other packages arrives
 * as app-native values or JDK functional types.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.appearance, dev.jasper.app.commands, dev.jasper.app.contributions, dev.jasper.app.files, dev.jasper.app.launch, dev.jasper.app.notifications, dev.jasper.app.palette, dev.jasper.app.persistence, dev.jasper.app.platform, dev.jasper.app.config, dev.jasper.app.settings, dev.jasper.app.lifecycle, dev.jasper.app.settingsui, dev.jasper.app.startup, dev.jasper.app.terminals, dev.jasper.app.windows, dev.jasper.sdk, dev.jasper.sdk.activity, dev.jasper.sdk.events, dev.jasper.sdk.files, dev.jasper.sdk.plugin, dev.jasper.sdk.services, dev.jasper.sdk.settings, dev.jasper.sdk.terminal, dev.jasper.sdk.ui, dev.jasper.terminal.session.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.plugins;
```

- [ ] **Step 5: Run the new and the existing runtime tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.pluginmanager.*' --tests 'dev.jasper.app.application.JasperApplicationPluginsTest'`
Expected: PASS. `PluginRuntimeTest`, `BundledSamplePluginTest`, `RemoteShortcutConfigTest` and the manager tests still call `start(…)`, which now begins with every plugin at once.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/plugins/PluginScheduleTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/ContributionReplayTest.java
git commit -m "$(cat <<'EOF'
feat(plugins): replay cached plugins and bind them one per turn after the first paint

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Wiring replay into the launch

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (`preparePlugins`, `startPlugins`, `openSettings`, a new `afterFirstPaint`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (`prepareDesktop`, `startDesktop`, `createApplication` as Plan 3A left them), `bootstrap/package-info.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/ContributionReplayWindowTest.java` (new), `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java`

**Interfaces:**
- Consumes: Task 7's `PluginRuntime.begin`, `whenAllBound`, `bindRemaining`; Task 5's `PluginRuntime.preload` and `Preload.run`; Task 4's `AppDirs.pluginCache()`; Plan 1's `JasperApplication.trace` field and `StartupTrace.when(StartupTrace.Milestone.FIRST_PAINT, SwingUtilities::invokeLater, action)`; Plan 3A's `Startup.workers()` and `StartupWorkers.submit(String, Runnable)`, and its `prepareDesktop`/`startDesktop`/`createApplication` exactly as Plan 3A Task 8 shows them (with Plan 1's marks and phases, Plan 4's `staleBuild`, indicator and Dock wiring, and the `--background` branch's `warmUp(() -> { mark WARMED_UP; loadThemeCatalog(); })`); Plan 4's `DockControl`, `backgroundOnQuit(DockControl, boolean)`, `residency(boolean)` and package-private `quitRequested()` (test only).

**Across Plan 4's background mode** (resident by default, Quit goes to the background):
- A `--background` launch (login) passes `everyPluginNow`, so `begin` binds every plugin inside `createApplication`, before the present lambda's `warmUp(done)` rehearsal composes its never-shown window: the rehearsal sees real contributions only, nothing waits for a `FIRST_PAINT` it never marks, and `PLUGINS_BOUND` is marked before `WARMED_UP`, so Plan 1 settles on `WARMED_UP` + `PLUGINS_BOUND`.
- Replay happens once per process, in `begin` (which refuses a second call). A stand-in lives only while its plugin is pending; a window opened later by a reopen, the menu bar item or the handoff renders the model as it is then, which in a warm process holds real entries only.
- `toBackground()` closes windows and keeps the process: it never calls `PluginRuntime.stop()`, so it is not the "quit" of "or at quit if sooner". Pending plugins keep binding one per turn (turns and the quiet timers are EDT tasks, not window tasks; a Quit before the first frame is covered by `FIRST_PAINT_FALLBACK`); queued invocations for windows that closed are dropped; placeholders in closed windows are skipped; the cache is written by the quiet timer, in the background. Only a real quit (Quit Jasper Completely, a retire request, a restart, a log-out's `quitForSystem`) reaches `shutdown()` → `PluginRuntime.stop()`, which writes the cache from the live model when it has not been written yet.
- Produces: `public static PluginRuntime.Preload JasperApplication.preparePlugins(AppDirs dirs, Path codeSource, Path developmentDirectory, boolean safeMode, Map<String, Map<String, Object>> pluginTables)` (replaces `preparePlugins(AppDirs)`); `public void JasperApplication.startPlugins(Path codeSource, Path developmentDirectory, boolean safeMode, AppDirs dirs, PluginRuntime.Preload preload, boolean everyPluginNow)` (the four-argument form stays for tests and tools: no cache, every plugin now); `static final Duration JasperApplication.FIRST_PAINT_FALLBACK` (5 s).

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/ContributionReplayWindowTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

/** One replay end to end against a real window: what the first frame shows before any plugin starts, and how binding lands. */
class ContributionReplayWindowTest {
    private static final String PLUGIN = """
        package fix.replay;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import dev.jasper.sdk.ui.ActionSpec;
        import dev.jasper.sdk.ui.Anchor;
        import dev.jasper.sdk.ui.IconName;
        import dev.jasper.sdk.ui.PanelSpec;
        import dev.jasper.sdk.ui.Side;
        import dev.jasper.sdk.ui.StandardMenu;
        import dev.jasper.sdk.ui.StatusItemSpec;
        import dev.jasper.sdk.ui.ToolbarItem;
        import java.nio.file.Files;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) throws Exception {
                Files.writeString(context.dataDirectory().resolve("started"), "yes");
                context.actions().register(ActionSpec.of("dev.example.replay.run", "Run Replay"), invoked -> {
                    try { Files.writeString(context.dataDirectory().resolve("ran"), "yes"); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                });
                context.menus().standard(StandardMenu.FILE).add("dev.example.replay.run");
                context.toolbar().add(ToolbarItem.action("dev.example.replay.run"));
                context.statusBar().add(new StatusItemSpec("dev.example.replay.state", Side.LEFT, 10)).setText("Replay ready");
                context.panels().register(new PanelSpec("dev.example.replay.panel", "Replay", context.appearance().icon(IconName.TERMINAL),
                    Anchor.LEFT), host -> {
                        var label = new javax.swing.JLabel("real replay");
                        host.onVisibility(visible -> label.setName("visible:" + visible));
                        return label;
                    });
            }
        }
        """;

    @TempDir Path root;
    private final BuddyTestSupport deck = new BuddyTestSupport();

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    private PluginRuntime.Options options() {
        return new PluginRuntime.Options(root.resolve("bundled"), root.resolve("user"), null, false, root.resolve("plugins.toml"),
            root.resolve("plugins.lock"));
    }

    private PluginRuntime runtime(Contributions model) {
        var windows = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { },
            title -> { }, () -> new java.awt.Rectangle(0, 0, 10, 10), (title, initial) -> Optional.empty()));
        return new PluginRuntime(options(), new ActivityNotifier(deck.companion(), () -> { }), PluginRuntime.SettingsReports.IGNORED,
            model, windows, new TerminalRegistry());
    }

    private static void stop(PluginRuntime runtime) throws Exception {
        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        edt(() -> pending.set(runtime.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
    }

    @Test void theFirstWindowShowsTheCachedSetBeforeAnyPluginStartsAndBindingChangesNothingItShows() throws Exception {
        PluginJars.build(root.resolve("bundled/dev.example.replay"), "replay.jar",
            PluginJars.descriptor("dev.example.replay", "1.0.0", "fix.replay.Main"), Map.of("fix.replay.Main", PLUGIN), List.of());
        Path started = root.resolve("user/dev.example.replay/data/started");
        Path cache = root.resolve("cache/plugins");

        // A first launch: nothing is cached, so the plugin starts before any window; quitting records it.
        var first = new AtomicReference<PluginRuntime>();
        edt(() -> {
            first.set(runtime(new Contributions()));
            first.get().begin(PluginRuntime.preload(options(), null, cache, null, Map.of()), true, false, action -> { });
        });
        stop(first.get());
        assertThat(cache.resolve("dev.example.replay.toml")).exists();
        Files.delete(started);

        // The next launch: the worker preloads; the window shows the stand-ins; the plugin binds after the first paint.
        var preload = PluginRuntime.preload(options(), null, cache, null, Map.of());
        preload.run();
        var model = new AtomicReference<Contributions>();
        var runtime = new AtomicReference<PluginRuntime>();
        var firstPaint = new AtomicReference<Runnable>();
        var state = UiState.inMemory();
        state.putPanel("dev.example.replay.panel", new UiState.Panel("LEFT", true, 300));
        var owner = new AtomicReference<WindowContent>();
        List<Contributions.Kind> changes = new ArrayList<>();
        edt(() -> {
            model.set(new Contributions());
            runtime.set(runtime(model.get()));
            runtime.get().begin(preload, true, false, firstPaint::set);
            owner.set(DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>())));
            owner.get().connectContributions(model.get(), state);
            WindowContent window = owner.get();
            assertThat(window.commands().find("dev.example.replay.run")).as("a palette command and a shortcut target").isPresent();
            assertThat(window.status().contributedItems(true)).extracting(JButton::getText).containsExactly("Replay ready");
            assertThat(window.rail().buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Replay");
            assertThat(window.regions().content(PanelRegion.LEFT).getComponentCount()).as("an empty placeholder").isZero();
            assertThat(model.get().menus()).anySatisfy(section -> assertThat(section.entries())
                .contains(new MenuEntry.Item("dev.example.replay.run")));
            assertThat(model.get().toolbar()).containsExactly(new ToolbarEntry.Button("dev.example.replay.run"));
            model.get().onChanged(changes::add);
        });
        assertThat(started).as("no plugin code ran before the first paint").doesNotExist();

        edt(firstPaint.get());
        DesktopTestSupport.until(() -> Files.exists(started) && !model.get().hasStandIns("dev.example.replay"));
        edt(() -> {
            assertThat(changes).as("binding produced no change notification").isEmpty();
            JComponent placeholder = owner.get().regions().content(PanelRegion.LEFT);
            assertThat(placeholder.getComponent(0)).isInstanceOf(JLabel.class)
                .satisfies(real -> assertThat(real.getName()).isEqualTo("visible:true"));
            owner.get().commands().find("dev.example.replay.run").orElseThrow().action()
                .actionPerformed(new java.awt.event.ActionEvent(owner.get(), 0, "run"));
        });
        assertThat(root.resolve("user/dev.example.replay/data/ran")).exists();
        stop(runtime.get());
    }
}
```

Add to `JasperApplicationPluginsTest`:

```java
    @Test void aResidentLaunchStartsEveryPluginBeforeAnyWindowEvenWhenCached() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        PluginJars.build(dirs.plugins().resolve("dev.example.life/jars"), "life.jar",
            PluginJars.descriptor("dev.example.life", "1.0.0", "fix.life.Main"), Map.of("fix.life.Main", FIXTURE), List.of());
        java.nio.file.Files.writeString(dirs.pluginState(),
            "version = 1\n\n[plugins.\"dev.example.life\"]\nenabled = true\nconsented = []\nremove = false\n");
        Path started = dirs.plugins().resolve("dev.example.life/data/started");
        for (int launch = 0; launch < 2; launch++) {
            var preload = JasperApplication.preparePlugins(dirs, null, null, false, Map.of());
            preload.run();
            boolean resident = launch == 1;
            var terminated = new CountDownLatch(1);
            JasperApplication[] application = new JasperApplication[1];
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                application[0].startPlugins(null, null, false, dirs, preload, resident);
            });
            assertThat(started).as(resident ? "cached, but a resident starts everything now" : "nothing cached yet").exists();
            java.nio.file.Files.delete(started);
            edt(application[0]::quit);
            assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(dirs.pluginCache().resolve("dev.example.life.toml")).as("recorded at quit").exists();
        }
    }

    @Test void goingToTheBackgroundIsNotAQuitPluginsKeepRunningAndARealQuitRecordsThem() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        PluginJars.build(dirs.plugins().resolve("dev.example.life/jars"), "life.jar",
            PluginJars.descriptor("dev.example.life", "1.0.0", "fix.life.Main"), Map.of("fix.life.Main", FIXTURE), List.of());
        java.nio.file.Files.writeString(dirs.pluginState(),
            "version = 1\n\n[plugins.\"dev.example.life\"]\nenabled = true\nconsented = []\nremove = false\n");
        Path data = dirs.plugins().resolve("dev.example.life/data");
        var preload = JasperApplication.preparePlugins(dirs, null, null, false, Map.of());
        preload.run();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, null, false, dirs, preload, false);
            application[0].residency(true);
            application[0].backgroundOnQuit(new DockControl(visible -> { }, () -> { }, () -> false), true);
            application[0].quitRequested();
        });
        assertThat(data.resolve("started")).exists();
        assertThat(data.resolve("stopped")).as("the background keeps plugins running").doesNotExist();
        assertThat(terminated.getCount()).as("still running").isEqualTo(1);
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(data.resolve("stopped")).exists();
        assertThat(dirs.pluginCache().resolve("dev.example.life.toml")).as("a real quit records the plugins").exists();
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ContributionReplayWindowTest' --tests 'dev.jasper.app.application.JasperApplicationPluginsTest'`
Expected: `ContributionReplayWindowTest` PASSES already (it uses only Tasks 1 to 7; it pins the integrated behaviour). `JasperApplicationPluginsTest` FAILS to compile: the five-argument `preparePlugins` and the six-argument `startPlugins` do not exist. (`DockControl`, `backgroundOnQuit`, `residency` and `quitRequested` are Plan 4's and exist already.)

- [ ] **Step 3: Give `JasperApplication` the preload and the schedule**

Replace `preparePlugins(AppDirs dirs)` with:

```java
    /**
     * A launch's plugin work for a start-up worker, before {@link #startPlugins} needs it: pending removals and installs,
     * discovery from the plugin cache, class loaders and class preloading.
     */
    public static PluginRuntime.Preload preparePlugins(AppDirs dirs, Path codeSource, Path developmentDirectory, boolean safeMode,
                                                       Map<String, Map<String, Object>> pluginTables) {
        // The pre-2026-09-22 data directory, migrated into plugins/<id>/data once.
        return PluginRuntime.preload(pluginOptions(codeSource, developmentDirectory, safeMode, dirs), dirs.root().resolve("plugin-data"),
            dirs.pluginCache(), codeSource, pluginTables);
    }

    private static PluginRuntime.Options pluginOptions(Path codeSource, Path developmentDirectory, boolean safeMode, AppDirs dirs) {
        return new PluginRuntime.Options(PluginRuntime.bundledDirectory(codeSource), dirs.plugins(), developmentDirectory, safeMode,
            dirs.pluginState(), dirs.pluginLock());
    }

    /**
     * How long binding waits for a first paint that never comes (a window that cannot paint, or Quit to the background
     * before the first frame) before plugins start anyway.
     */
    static final Duration FIRST_PAINT_FALLBACK = Duration.ofSeconds(5);

    /**
     * Runs {@code action} on the EDT once the first terminal has painted, or after {@link #FIRST_PAINT_FALLBACK},
     * through Plan 1's injectable {@code trace} field; it may run twice (the runtime ignores the second).
     */
    private void afterFirstPaint(Runnable action) {
        trace.when(StartupTrace.Milestone.FIRST_PAINT, SwingUtilities::invokeLater, action);
        afterDelay(FIRST_PAINT_FALLBACK, action);
    }
```

(`trace` is Plan 1's `private volatile StartupTrace trace = StartupTrace.current();`, which `JasperApplication.trace(StartupTrace)` replaces in tests; Plan 1 already imports `StartupTrace` here.)

Replace the head of `startPlugins` (its javadoc and signature) with the delegating four-argument form plus the new six-argument signature:

```java
    /** As the six-argument form, starting every plugin now without the plugin cache: tests and tools. */
    public void startPlugins(Path codeSource, Path developmentDirectory, boolean safeMode, AppDirs dirs) {
        startPlugins(codeSource, developmentDirectory, safeMode, dirs, null, true);
    }

    /**
     * Starts plugins once, before the first window. With {@code preload} (from {@link #preparePlugins}), a plugin whose
     * plugin-cache entry is valid stands in through its recorded contributions and starts after the first paint, unless
     * {@code everyPluginNow} (a resident at login starts everything before any window). Without {@code preload}, every
     * plugin starts now. {@code codeSource} locates bundled plugins beside the application jar and may be null;
     * {@code developmentDirectory} is the {@code --plugin-dir} value or null.
     */
    public void startPlugins(Path codeSource, Path developmentDirectory, boolean safeMode, AppDirs dirs,
                             PluginRuntime.Preload preload, boolean everyPluginNow) {
```

In its body:

- replace `plugins = new PluginRuntime(new PluginRuntime.Options(PluginRuntime.bundledDirectory(codeSource), dirs.plugins(),` and the following `developmentDirectory, safeMode, dirs.pluginState(), dirs.pluginLock()), activityNotifier,` with `plugins = new PluginRuntime(pluginOptions(codeSource, developmentDirectory, safeMode, dirs), activityNotifier,`;
- replace `plugins.start(configuration == null ? Map.of() : configuration.snapshot().plugins(), themes.current().chrome().dark());` with:

```java
        boolean dark = themes.current().chrome().dark();
        if (preload == null) plugins.start(configuration == null ? Map.of() : configuration.snapshot().plugins(), dark);
        else plugins.begin(preload, dark, everyPluginNow, this::afterFirstPaint);
```

- replace the method's last statement `reportBindingProblems();` with:

```java
        // A user binding for an action whose plugin has not started yet would read as unknown: report once all have.
        plugins.whenAllBound(this::reportBindingProblems);
```

In `openSettings`, after `if (settings == null || quitting || stopped) return;`, add:

```java
            // Settings lists every plugin's page and the Plugins manager's state: plugins still waiting start now.
            if (plugins != null) plugins.bindRemaining();
```

- [ ] **Step 4: Submit the preload from the bootstrap**

In `ApplicationBootstrap.prepareDesktop` (Plan 3A Task 8's version), directly after
`startWorkers(workers, snapshot, dirs.shellIntegration(), options);` insert:

```java
            // Maintenance, discovery from the plugin cache, class loaders and class preloading, beside everything below.
            PluginRuntime.Preload plugins = JasperApplication.preparePlugins(dirs, HandoffSocket.codeSource(), options.pluginDir(),
                options.safeMode(), snapshot.plugins());
            workers.submit("plugins", plugins::run);
```

then delete the two lines

```java
            // Before any plugin is discovered, and here rather than on the EDT: it may wait for the state lock.
            JasperApplication.preparePlugins(dirs);
```

and pass the preload on: `SwingUtilities.invokeLater(() -> startDesktop(startup, dirs, themes, log, exceptions, shutdown, endpoint));` becomes `SwingUtilities.invokeLater(() -> startDesktop(startup, dirs, themes, plugins, log, exceptions, shutdown, endpoint));`.

Give `startDesktop` a `PluginRuntime.Preload plugins` parameter after `StartupThemes themes`, and in its `compose` call change `history -> createApplication(service, history, dirs, options, startup.themeProblems(), workers, themes)` to `history -> createApplication(service, history, dirs, options, startup.themeProblems(), workers, themes, plugins)`.

Give `createApplication` a last parameter `PluginRuntime.Preload plugins`, and change its `application.startPlugins(HandoffSocket.codeSource(), options.pluginDir(), options.safeMode(), dirs);` to:

```java
            // A resident starting at login (--background) has no first paint to wait for: every plugin starts now.
            application.startPlugins(HandoffSocket.codeSource(), options.pluginDir(), options.safeMode(), dirs, plugins, options.background());
```

Import `dev.jasper.app.plugins.PluginRuntime`. In `bootstrap/package-info.java`, add `dev.jasper.app.plugins, ` to the allowed-dependency list immediately before `dev.jasper.app.residency` (the edge is acyclic: `plugins` depends on nothing in `bootstrap` or `application`).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ContributionReplayWindowTest' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.bootstrap.*'`
Expected: PASS.

Run: `./gradlew verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java jasper-app/src/main/java/dev/jasper/app/bootstrap \
  jasper-app/src/test/java/dev/jasper/app/workspace/ContributionReplayWindowTest.java \
  jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java
git commit -m "$(cat <<'EOF'
perf(startup): preload plugins on a start-up worker and bind them after the first paint

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: SDK documentation, maintained docs, STATUS and the full gate

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/Plugin.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/package-info.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/events/Events.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/TerminalEvents.java`
- Modify: `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/diagnostics.md`, `docs/STATUS.md`

**Interfaces:** documentation only; no API change (`JasperSdk.VERSION` stays).

- [ ] **Step 1: The SDK's javadoc**

In `Plugin.java`, replace the javadoc of `start` with:

```java
    /**
     * Registers the plugin's contributions. If this throws, everything the context handed out is
     * rolled back, the context is closed and the plugin is reported as failed.
     *
     * <p>{@code start} may run after the first window is shown. Until then Jasper shows what the plugin contributed at
     * the end of its previous start, and the registrations made here take those places without a visible change, so
     * register the same contributions in the same order each time and expect windows, tabs and panes to exist already.
     * Subscriptions made here receive the events delivered since launch, in order, right after {@code start} returns
     * and before anything published later.
     *
     * @param context this plugin's services
     * @throws Exception to fail the start
     */
```

In `plugin/package-info.java`, replace the javadoc with:

```java
/**
 * The entry point a plugin implements and the context the runtime hands it. Plugins start once per launch, on the UI
 * thread, dependencies first; a plugin that started at the previous launch may start after the first window is shown
 * (see {@link dev.jasper.sdk.plugin.Plugin#start}). This package depends on every other SDK package and nothing depends
 * on it.
 */
package dev.jasper.sdk.plugin;
```

In `Events.java`, replace the class javadoc with:

```java
/**
 * The central bus. Publication always enqueues; handlers run later on the UI thread in one global
 * first-in, first-out order, never synchronously and never re-entrantly. The one replay is at start-up: the
 * subscriptions a plugin makes while it starts receive, right after its start returns, the events delivered since
 * launch. Otherwise there is no replay: read current state from its owner, then listen.
 */
```

In `TerminalEvents.java`, replace the class javadoc's last sentence ("Like every event they arrive later, on the event thread, and are not replayed: read the current state first, then listen.") with:

```java
 * Like every event they arrive later, on the event thread; beyond the launch's events, which subscriptions made while
 * a plugin starts receive right after its start, they are not replayed: read the current state first, then listen.
```

- [ ] **Step 2: The plugin author's guide and the SDK architecture**

In `docs/plugin-authoring.md`, after the paragraph that begins "`start` and `stop` run on the Swing event dispatch thread", add:

```markdown
`start` may run after the first window is shown. Once start-up has settled, Jasper records what each plugin
contributed (actions, menu sections, toolbar entries, status items, panels, rail actions, palette scopes and file
sources) in `<home>/cache/plugins/<id>.toml`. At the next launch those appear as stand-ins in the first window, and the
plugin starts after the window has painted; its registrations then take the stand-ins' places without a visible
change. Invoking a stand-in action runs it once the plugin has started; a panel shown early holds an empty placeholder
until your factory runs; a palette scope shows a loading row. So register the same contributions in the same order
every time, mint icons through `context.appearance().icon(…)` (other icons are not recorded), and do not assume no
window exists yet. A plugin that is new or updated, or whose cache entry is missing, starts before the first window.
```

In the "Rules that matter" list, replace the **Events** bullet's last sentence "There is no replay: read current state, then listen." with "Subscriptions made in `start` receive the events delivered since launch, in order, right after `start` returns (a plugin may start after the first panes opened); after that there is no replay: read current state, then listen."

In `docs/sdk-architecture.md`, replace "`JasperApplication.startPlugins` runs once on the EDT before the first window:" with:

```markdown
`ApplicationBootstrap` hands `PluginRuntime.preload` to a start-up worker right after the configuration read: it applies
pending removals and installs, discovers plugins (from the plugin cache when every jar is unchanged, opening no jar),
builds their class loaders and loads and links the classes each cached plugin defined while starting, without
initialising them. `JasperApplication.startPlugins` then runs once on the EDT before the first window
(`PluginRuntime.begin`), taking over whatever the worker has not reached:
```

and add after step 4 of that list:

```markdown
5. `ContributionReplay` adds the recorded contributions of each plugin with a valid cache entry as stand-ins, in start
   order; a plugin without one starts now with its dependencies. After the first paint the rest start one per EDT turn
   (open panels' plugins first, then plugins with queued invocations, then start order). Inside
   `Contributions.bindingStarted`/`bindingFinished` a plugin's registrations take its stand-ins' places and windows
   hear only kinds whose visible data changed; unclaimed stand-ins go. `EventBus.replay` then gives the plugin's
   start-time subscriptions the events delivered since launch. When the model has been quiet for three seconds after
   the last plugin bound (or at quit), the cache is rewritten from the live model.
```

- [ ] **Step 3: The application's architecture, maintenance and diagnostics guides**

In `docs/app-architecture.md`, replace the sentence "Plugins start once, before the first window." with:

```markdown
Plugins without a valid plugin-cache entry start once, before the first window; the others stand in through their
recorded contributions and start after the first paint, one per EDT turn ([SDK architecture](sdk-architecture.md)
describes the launch).
```

In the `contributions` row of the package table, append: "Entries carry their owner and registration order; stand-ins replay a plugin's last contributions until its binding adopts them in place."

In `docs/app-maintenance.md`, add a section before "## Review checklist":

```markdown
## Plugin contribution replay

A plugin's chrome survives between launches as data. To add a contribution kind or a field a window shows:

1. Add it to `contributions.Contributed` (a record, with `mapIcons`), to the entry or slot that stores it in
   `Contributions`, to `Contributions.visible` and `contributedBy`, and to `addStandIn` (and its claim in the matching
   `register…`). A field that a window shows but `visible` misses makes binding silently stale; one it shows but the
   cache misses makes every launch's binding announce a change.
2. Write and parse it in `plugins.PluginCacheFile`; bump `SCHEMA` when old files would parse wrongly (an old file is
   then a miss, never an error).
3. Pin it in `ContributionStandInsTest` (adoption without a change event) and `PluginCacheFileTest` (round trip).

Icons are recorded by origin in `HostedUi.appearance()` only; an icon a plugin built itself replays as none. The cache
lives in `<home>/cache/plugins`; deleting it is always safe (every plugin then starts before the first window and the
files are written again). A stand-in that never binds is removed when its plugin's start returns; a failed start
deletes the plugin's entry. `ContributionReplayWindowTest` is the end-to-end check.
```

In `docs/diagnostics.md`, add at the end:

```markdown
Plugin start-up caches live in `cache/plugins/` in the same Jasper folder as `logs/`: one TOML file per plugin with
its discovery result, the classes it loaded while starting and what it contributed to the window. They contain no
terminal content. Deleting the folder is always safe: the next launch starts every plugin before its first window and
writes the files again.
```

- [ ] **Step 4: Hygiene and the full gate**

Run the AGENTS.md source-hygiene check (Python, from the repository root):

```bash
python3 - <<'PY'
import pathlib
for root in ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins"):
    for p in pathlib.Path(root).rglob("*.java"):
        text = p.read_text(encoding="utf-8")
        bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
        if bad: print(p, "bad chars:", bad)
PY
```

Expected: no output.

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL, including `verifySdkArchitecture`, `verifyPluginArchitecture`, `verifyApplicationArchitecture`, javadoc doclint and `AppDocumentationTest`.

Read the totals:

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = e = s = 0
for p in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    r = ET.parse(p).getroot()
    t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
print(f"{t:,}/{f}/{e}/{s}")
PY
```

Expected: one line `tests/failures/errors/skips` with zero failures and errors.

- [ ] **Step 5: STATUS**

At the top of "## Current state — 2026-10-02" in `docs/STATUS.md`, add the entry below, with the line Step 4 printed in
place of `TOTALS`:

```markdown
- **Start-up Plan 3B: contribution replay** (branch `claude/startup-speed`, plan
  `docs/superpowers/plans/2026-10-02-startup-3b-contribution-replay.md`, spec Part 3 section 3B): plugins leave the
  critical path. The contributions model records owners and registration order; plugins register through
  `Contributions.Contributor`; `HostedUi` records icon origins at mint time; `<home>/cache/plugins/<id>.toml` holds each
  plugin's discovery, package scan, start-time classes and contributions, keyed by id, version, origin, directory, every
  jar's name/size/mtime and the Jasper version. A start-up worker (`PluginRuntime.Preload`) discovers from the cache,
  builds loaders and loads and links recorded classes without initialising them (HotSpot links on
  `getDeclaredConstructors`; loading alone is the guarantee). `PluginRuntime.begin` replays stand-ins before the first
  window, starts uncached plugins at once, and binds the rest one per EDT turn after `FIRST_PAINT` (open panels, then
  queued invocations, then start order), adopting stand-ins with no change event unless visible data differ; the
  `EventBus` replays the launch backlog to each late plugin's start-time subscriptions; `PLUGINS_BOUND` marks the last
  binding. Settings and the file chooser start the plugins they need at once. Deviations: a 30 s cap on the "three quiet
  seconds" wait; queued invocations of a failed plugin are dropped with a new notice (there was no start-failure
  notice); development plugins are never cached. Full check: **TOTALS**. Not done: GUI check (user-run: launch twice,
  quitting completely in between; the second launch's first frame shows the rail, toolbar, menus and status items at once and an open Remote panel
  fills in place; a shortcut pressed before plugins bind runs once they do), `startupBench` before/after with the
  user's approval, push.
```

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: PASS (the STATUS entry adds no link, so the documentation check is unchanged by it).

- [ ] **Step 6: Commit**

```bash
git add jasper-sdk/src/main/java/dev/jasper/sdk docs/plugin-authoring.md docs/sdk-architecture.md docs/app-architecture.md \
  docs/app-maintenance.md docs/diagnostics.md docs/STATUS.md
git commit -m "$(cat <<'EOF'
docs(plugins): document late plugin starts, the event replay and the plugin cache

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## User-run acceptance (GUI; never automated)

Ask the user before each item; none runs in `check`.

1. Launch the packaged app twice (`packageApp`, then open it), quitting completely in between (the menu bar item's Quit
   Jasper Completely; Cmd+Q only goes to the background). The second launch's first frame shows the rail, the
   toolbar's Sessions dropdown, the File and SSH menus, the Vault and File Server status items and the palette tabs
   immediately; nothing pops in or moves when plugins bind.
2. With Remote's Sessions panel left open at a complete quit, the next launch shows the panel at its saved width with the panel
   background, then the real panel in place (no spinner, no flicker of the rail).
3. Press a plugin shortcut (for example Remote's Connect) within the first half second: it runs once Remote binds.
4. Open Settings during the first second: every plugin's page is listed.
5. With residency on, press Cmd+Q within the first second, wait, then click the Dock icon: the new window shows real
   plugin contributions at once (no stand-ins, no pop-in), and the plugins were never stopped in between.
6. `startupBench` cold, before and after this plan (the user approves the batch): `plugins bound` may rise, `first
   paint` and `output painted` should fall by about the plugins' former start time.
