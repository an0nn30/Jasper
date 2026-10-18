package dev.jasper.app.bootstrap;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import java.net.UnixDomainSocketAddress;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.commandline.CommandProtocol;
import dev.jasper.app.commandline.CommandListener;
import dev.jasper.app.application.JasperApplication;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.residency.HandoffSocket;
import dev.jasper.app.residency.LaunchRequest;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ApplicationBootstrapTest {
    /** What {@code configureDesktopProperties} sets in this JVM: restored after every test, so nothing leaks into other suites. */
    private static final java.util.List<String> DESKTOP_PROPERTIES = java.util.List.of(
        "apple.laf.useScreenMenuBar", "apple.awt.application.appearance", "apple.awt.enableTemplateImages");
    private final java.util.Map<String, String> savedDesktopProperties = new java.util.HashMap<>();
    @TempDir Path directory;

    @BeforeEach void rememberDesktopProperties() {
        for (String key : DESKTOP_PROPERTIES) savedDesktopProperties.put(key, System.getProperty(key));
    }

    @AfterEach void restoreDesktopProperties() {
        savedDesktopProperties.forEach((key, value) -> {
            if (value == null) System.clearProperty(key); else System.setProperty(key, value);
        });
    }

    @Test void anUpdateIsTheJarChangingOrVanishingOnDisk() throws Exception {
        Path jar = Files.writeString(directory.resolve("jasper-app.jar"), "build 1");
        long started = HandoffSocket.lastModified(jar);
        var replaced = ApplicationBootstrap.replacedOnDisk(jar, started);
        assertThat(replaced.getAsBoolean()).isFalse();
        Files.setLastModifiedTime(jar, java.nio.file.attribute.FileTime.fromMillis(started + 60_000));
        assertThat(replaced.getAsBoolean()).as("a new build in place").isTrue();
        Files.delete(jar);
        assertThat(replaced.getAsBoolean()).as("the old build's jar is gone").isTrue();
        assertThat(ApplicationBootstrap.replacedOnDisk(null, 0).getAsBoolean())
            .as("an unknown code source never counts as updated").isFalse();
    }
    @Test void desktopPropertiesOverrideAnInheritedMenuBarSetting() {
        String menuKey = "apple.laf.useScreenMenuBar", appearanceKey = "apple.awt.application.appearance";
        // Override an inherited JVM setting before desktop startup.
        System.setProperty(menuKey, "false");
        ApplicationBootstrap.configureDesktopProperties();
        assertThat(System.getProperty(menuKey)).isEqualTo("true");
        assertThat(System.getProperty(appearanceKey)).isEqualTo("system");
    }

    @Test void helpAndErrorsExitBeforeConfigurationOrDesktopStartup() {
        var out = new ByteArrayOutputStream(); var error = new ByteArrayOutputStream();
        var stdout = new PrintStream(out); var stderr = new PrintStream(error);
        assertThat(ApplicationBootstrap.start(new String[]{"--help"}, stdout, stderr,
            (service, options) -> { throw new AssertionError("Unexpected startup"); })).isZero();
        assertThat(out.toString()).contains("--config", "--help");
        assertThat(ApplicationBootstrap.start(new String[]{"--config"}, stdout, stderr,
            (service, options) -> { throw new AssertionError("Unexpected startup"); })).isEqualTo(2);
        assertThat(error.toString()).contains("requires a file path");
    }
    @Test void explicitConfigurationIsReadBeforeDispatchToSwing() throws Exception {
        Path config = directory.resolve("config.toml");
        Files.writeString(config, "[window]\ntab_height=47\n");
        var received = new AtomicReference<ConfigService>();
        try {
            int result = ApplicationBootstrap.start(new String[]{"--config", config.toString()}, System.out, System.err, (service, options) -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isFalse(); received.set(service);
            });
            assertThat(result).isZero();
            assertThat(received.get()).isNotNull();
            assertThat(received.get().initialState().snapshot().tabHeight()).isEqualTo(47);
        } finally { if (received.get() != null) received.get().close(); }
    }

    @Test void explicitConfigurationSuppliesTheSavedAppearance() throws Exception {
        Path config = directory.resolve("elsewhere/config.toml");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "ui.appearance='light'");
        var received = new AtomicReference<ConfigService>();
        try {
            assertThat(ApplicationBootstrap.start(new String[]{"--config", config.toString()}, System.out, System.err,
                (service, options) -> received.set(service))).isZero();
            assertThat(received.get().initialState().snapshot().appearance()).isEqualTo(dev.jasper.app.config.AppearanceMode.LIGHT);
        } finally {
            if (received.get() != null) received.get().close();
        }
    }

    @Test void neitherAnotherConfigurationNorAResidentProcessHandsOff(@TempDir Path dir) throws Exception {
        AppDirs dirs = new AppDirs(dir, dir.resolve("config.toml"), dir.resolve("logs"));
        try (HandoffSocket endpoint = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(),
                dirs.daemonLock(), request -> LaunchRequest.Response.OK)) {
            assertThat(endpoint).isNotNull();
            // The control: with this endpoint listening, an ordinary launch does hand off. That is
            // what makes the two refusals below attributable to the guard rather than to silence.
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, false), dirs)).isTrue();
            // A different configuration file is a different Jasper; the resident one holds another.
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(dir.resolve("other.toml"), false, false), dirs)).isFalse();
            // And a resident process never hands off to itself.
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, true), dirs)).isFalse();
            // Recovery and development launches must never be swallowed by the process they are
            // meant to get away from.
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, false, true, null, false), dirs)).isFalse();
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, false, false, dir.resolve("p"), false), dirs)).isFalse();
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, false, false, null, true), dirs)).isFalse();
        }
    }

    @Test void withNothingListeningThereIsNothingToHandOffTo(@TempDir Path dir) {
        AppDirs dirs = new AppDirs(dir, dir.resolve("config.toml"), dir.resolve("logs"));
        assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, false), dirs)).isFalse();
    }

    @Test void aLoginLaunchStopsBeforeStartingWhenAResidentAlreadyServes(@TempDir Path dir) throws Exception {
        AppDirs dirs = new AppDirs(dir, dir.resolve("config.toml"), dir.resolve("logs"));
        var background = new AppArguments(null, false, true);
        assertThat(ApplicationBootstrap.alreadyResident(background, dirs)).as("nothing listening: it may become resident").isFalse();
        try (HandoffSocket endpoint = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(),
                dirs.daemonLock(), request -> { throw new AssertionError("a login launch never hands off"); })) {
            assertThat(endpoint).isNotNull();
            assertThat(ApplicationBootstrap.alreadyResident(background, dirs)).isTrue();
            // An ordinary launch hands off instead, and a standalone one is refused later with its own warning.
            assertThat(ApplicationBootstrap.alreadyResident(new AppArguments(null, false, false), dirs)).isFalse();
            assertThat(ApplicationBootstrap.alreadyResident(new AppArguments(null, false, true, true, null, false), dirs)).isFalse();
        }
        assertThat(ApplicationBootstrap.alreadyResident(background, dirs)).as("the resident has gone").isFalse();
    }

    @Test void residentRoleIsOnlyTrueWithTheSettingOnAndNoConfigOverride() {
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(null, false, false), true)).isTrue();
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(null, false, false), false)).isFalse();
        // A --config launch is standalone: it never claims the shared endpoint, setting or not.
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(Path.of("other.toml"), false, false), true)).isFalse();
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(Path.of("other.toml"), false, false), false)).isFalse();
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(null, false, false, true, null, false), true)).isFalse();
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(null, false, false, false, Path.of("p"), false), true)).isFalse();
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(null, false, false, false, null, true), true)).isFalse();
    }

    @Test void staleFlagsADifferentPathOrModificationTimeButNotAMatchingUnresolvedSource() {
        Path source = Path.of("/app.jar");
        assertThat(ApplicationBootstrap.stale(new LaunchRequest("t", source, 100L), source, 100L)).isFalse();
        assertThat(ApplicationBootstrap.stale(new LaunchRequest("t", Path.of("/other.jar"), 100L), source, 100L)).isTrue();
        assertThat(ApplicationBootstrap.stale(new LaunchRequest("t", source, 999L), source, 100L)).isTrue();
        // An unresolvable code source normalises to the same empty path on both sides.
        assertThat(ApplicationBootstrap.stale(new LaunchRequest("t", Path.of(""), 0L), null, 0L)).isFalse();
    }

    @Test void loginItemReconcilerDedupesAndRunsOffTheCallingThread() throws Exception {
        var applied = new java.util.concurrent.LinkedBlockingQueue<Boolean>();
        var threadNames = new java.util.concurrent.LinkedBlockingQueue<String>();
        var reconciler = ApplicationBootstrap.loginItemReconciler(enabled -> {
            threadNames.add(Thread.currentThread().getName());
            applied.add(enabled);
        });

        reconciler.accept(true);
        assertThat(applied.poll(2, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(threadNames.poll(2, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("jasper-login-item");

        // Repeating the same value is exactly what an unrelated config save replays: no call.
        reconciler.accept(true);
        reconciler.accept(true);
        assertThat(applied.poll(300, java.util.concurrent.TimeUnit.MILLISECONDS))
            .as("an unchanged value must be deduped, not reconciled again").isNull();

        // A value that actually changed still runs.
        reconciler.accept(false);
        assertThat(applied.poll(2, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(false);
    }

    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.MAC)
    @Test void reconcilingTheLoginItemWritesAndRemovesIt(@TempDir Path fakeHome) {
        Path plist = fakeHome.resolve("Library/LaunchAgents/dev.jasper.background.plist");
        ApplicationBootstrap.reconcileLoginItem(true, "/Applications/Jasper.app/Contents/MacOS/Jasper", fakeHome);
        assertThat(plist).exists();
        ApplicationBootstrap.reconcileLoginItem(false, "/Applications/Jasper.app/Contents/MacOS/Jasper", fakeHome);
        assertThat(plist).doesNotExist();
        // A development run has no installed path, so nothing is ever written.
        ApplicationBootstrap.reconcileLoginItem(true, null, fakeHome);
        assertThat(plist).doesNotExist();
    }
    @Test void failedDesktopDispatchClosesConfigurationAndPreservesError() {
        var received = new AtomicReference<ConfigService>();
        var original = new AssertionError("desktop dispatch");
        try {
            assertThatThrownBy(() -> ApplicationBootstrap.start(new String[]{"--config", directory.resolve("config.toml").toString()},
                System.out, System.err, (service, options) -> { received.set(service); throw original; })).isSameAs(original);
            assertThat(received.get().reload()).isCompletedExceptionally();
        } finally { if (received.get() != null) received.get().close(); }
    }

    @Test void onlyAPackagedMacLoginLaunchStartsHiddenFromTheDock() throws Exception {
        // A real bundle layout, because a packaged image only counts when it carries the bridge beside the launcher.
        Path bundle = directory.resolve("Jasper.app");
        Path launcher = Files.createDirectories(bundle.resolve("Contents/MacOS")).resolve("Jasper");
        Files.writeString(launcher, "placeholder");
        String app = launcher.toString();
        assertThat(ApplicationBootstrap.launchProperties(true, "Mac OS X", app))
            .as("a packaged image missing its bridge could never come back").isEmpty();
        Files.writeString(Files.createDirectories(bundle.resolve("Contents/app")).resolve("libjasper-macos.dylib"), "placeholder");
        assertThat(ApplicationBootstrap.launchProperties(true, "Mac OS X", app))
            .containsExactly(java.util.Map.entry("apple.awt.UIElement", "true"));
        assertThat(ApplicationBootstrap.launchProperties(false, "Mac OS X", app)).isEmpty();
        assertThat(ApplicationBootstrap.launchProperties(true, "Mac OS X", null))
            .as("a development run has no bridge, so it could never come back").isEmpty();
        assertThat(ApplicationBootstrap.launchProperties(true, "Windows 11", "C:\\Program Files\\Jasper\\Jasper.exe")).isEmpty();
        assertThat(ApplicationBootstrap.launchProperties(true, "Windows 11", app))
            .as("the operating system decides even when the layout looks like a bundle").isEmpty();
    }

    @Test void desktopPropertiesAlwaysUseTheMacScreenMenuBar() {
        System.clearProperty("apple.laf.useScreenMenuBar");
        ApplicationBootstrap.configureDesktopProperties();
        assertThat(System.getProperty("apple.laf.useScreenMenuBar")).isEqualTo("true");
        assertThat(System.getProperty("apple.awt.application.appearance")).isEqualTo("system");
    }

    @Test void desktopPropertiesMakeTheMenuBarGlyphATemplateImage() {
        String key = "apple.awt.enableTemplateImages";
        System.clearProperty(key);
        ApplicationBootstrap.configureDesktopProperties();
        assertThat(System.getProperty(key)).isEqualTo("true");
    }

    @Test void aNoWindowLaunchNeverHandsOffAndStepsAsideWhileAJasperServes(@TempDir Path dir) throws Exception {
        AppDirs dirs = new AppDirs(dir, dir.resolve("config.toml"), dir.resolve("logs"));
        var noWindow = new AppArguments(null, false, false, false, null, false, false, true);
        try (HandoffSocket endpoint = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(), dirs.daemonLock(),
                request -> LaunchRequest.Response.OK)) {
            assertThat(endpoint).isNotNull();
            assertThat(ApplicationBootstrap.handsOff(noWindow, dirs)).as("a handoff would open a window").isFalse();
        }
        assertThat(ApplicationBootstrap.alreadyServing(noWindow, dirs)).isFalse();
        CommandListener listener = CommandListener.bind(dirs.commandSocket(), invocation -> CompletableFuture.completedFuture(0));
        try {
            assertThat(listener).isNotNull();
            assertThat(ApplicationBootstrap.alreadyServing(noWindow, dirs)).isTrue();
            assertThat(ApplicationBootstrap.alreadyServing(new AppArguments(null, false, false), dirs)).as("a plain launch").isFalse();
        } finally {
            listener.close();
        }
    }

    @Test void onlyAPrivateLaunchServesItsOwnCommandSocket(@TempDir Path dir) {
        AppDirs dirs = new AppDirs(dir, dir.resolve("config.toml"), dir.resolve("logs"));
        long pid = ProcessHandle.current().pid();
        assertThat(ApplicationBootstrap.commandSocket(new AppArguments(null, false, false), dirs)).isEqualTo(dirs.commandSocket());
        assertThat(ApplicationBootstrap.commandSocket(new AppArguments(null, false, false).withHomeOverride(true), dirs))
            .isEqualTo(dirs.commandSocket());
        assertThat(ApplicationBootstrap.commandSocket(new AppArguments(null, false, false, true, null, false), dirs))
            .isEqualTo(dirs.commandSocket(pid));
        assertThat(ApplicationBootstrap.commandSocket(new AppArguments(dir.resolve("other.toml"), false, false), dirs))
            .isEqualTo(dirs.commandSocket(pid));
    }

    private static JasperApplication application(Path dir, Runnable terminated) throws Exception {
        var made = new AtomicReference<JasperApplication>();
        // The initial read is refused on the EDT.
        var service = new ConfigService(dir.resolve("config.toml"), true);
        DesktopTestSupport.edt(() -> made.set(new JasperApplication(service, null, new CommandHistory(), null, terminated)));
        return made.get();
    }

    @Test void aLaunchThatFindsAnotherJasperServingTheSharedSocketServesItsOwnShellsPrivately(@TempDir Path dir) throws Exception {
        AppDirs dirs = new AppDirs(dir, dir.resolve("config.toml"), dir.resolve("logs"));
        long pid = ProcessHandle.current().pid();
        Path own = dirs.commandSocket(pid);
        CommandListener other = CommandListener.bind(dirs.commandSocket(), invocation -> CompletableFuture.completedFuture(0));
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(dir, terminated::countDown);
        var served = new AtomicReference<CommandListener>();
        try {
            assertThat(other).isNotNull();
            DesktopTestSupport.edt(() -> served.set(
                ApplicationBootstrap.serveCommands(application, new AppArguments(null, false, false), dirs, new StartupResources())));
            assertThat(served.get()).as("a listener of its own").isNotNull();
            assertThat(served.get().socket()).isEqualTo(own);
            assertThat(CommandListener.live(own)).isTrue();
            assertThat(application.commandSocket()).as("what its shells' JASPER_COMMAND_SOCKET names").isEqualTo(own);
            var noWindow = new AppArguments(null, false, false, false, null, false, false, true);
            assertThat(ApplicationBootstrap.bindCommands(noWindow, dirs, invocation -> CompletableFuture.completedFuture(0)))
                .as("a --no-window launch exists only to serve the shared socket").isNull();
            DesktopTestSupport.edt(application::quitNow);
            assertThat(terminated.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(own).as("removed at quit, as a private launch's socket is").doesNotExist();
            assertThat(own.resolveSibling(own.getFileName() + ".lock")).doesNotExist();
            assertThat(CommandListener.live(dirs.commandSocket())).as("the other Jasper's socket is untouched").isTrue();
        } finally {
            if (served.get() != null) served.get().close();
            if (other != null) other.close();
        }
    }

    @Test void aNoWindowJasperThatIsNotResidentQuitsOnceItsCallIsServed(@TempDir Path dir) throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(dir, terminated::countDown);
        Path socket = dir.resolve("daemon").resolve("commands");
        CommandListener listener = CommandListener.bind(socket, invocation -> CompletableFuture.completedFuture(0));
        try {
            DesktopTestSupport.edt(() -> ApplicationBootstrap.serveWithoutWindow(application, listener));
            assertThat(terminated.await(300, TimeUnit.MILLISECONDS)).as("nothing has been served yet").isFalse();
            String token = Files.readString(CommandListener.tokenFile(socket)).strip();
            try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
                client.write(ByteBuffer.wrap(new CommandProtocol.Request(token, dir, Optional.empty(), List.of("editor"))
                    .encode().getBytes(StandardCharsets.UTF_8)));
                ByteBuffer reply = ByteBuffer.allocate(256);
                while (client.read(reply.clear()) >= 0) { }
            }
            assertThat(terminated.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            listener.close();
        }
    }

    @Test void aNoWindowLaunchWithoutTheCommandSocketQuits(@TempDir Path dir) throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(dir, terminated::countDown);
        DesktopTestSupport.edt(() -> ApplicationBootstrap.serveWithoutWindow(application, null));
        assertThat(terminated.await(10, TimeUnit.SECONDS)).isTrue();
    }

    /** Spec 4.8: the pre-pass runs before the configuration is read, and its selection reaches the launch. */
    @Test void thePrePassFeedsTheConfigurationsDefaultsAndTheLaunch() throws Exception {
        Path config = directory.resolve("config.toml");
        Files.writeString(config, "");
        var selection = dev.jasper.app.plugins.PluginSelection.of(new AppDirs(directory, config, directory.resolve("logs")), null, "",
            dev.jasper.app.testsupport.LookFixtures.plugin(directory.resolve("dev"), "dev.example.look",
                dev.jasper.app.testsupport.LookFixtures.descriptor("dev.example.look", "Example")
                    + dev.jasper.app.testsupport.LookFixtures.provider("look.kit.toml"),
                java.util.Map.of("look.kit.toml", "kit = 1\nbase = \"default\"\n[defaults]\n\"palette.visible_rows\" = 7\n")), false);
        var received = new AtomicReference<ApplicationBootstrap.Startup>();
        try {
            assertThat(ApplicationBootstrap.start(new String[]{"--config", config.toString()}, System.out, System.err,
                (dirs, options) -> new ApplicationBootstrap.Prepared(null, selection), received::set)).isZero();
            assertThat(received.get().selection()).isSameAs(selection);
            assertThat(received.get().service().initialState().snapshot().visibleRows()).isEqualTo(7);
            assertThat(received.get().service().defaults()).isSameAs(selection.presentation().defaults());
        } finally { if (received.get() != null) received.get().service().close(); }
        var plain = new AtomicReference<ApplicationBootstrap.Startup>();
        try {
            assertThat(ApplicationBootstrap.start(new String[]{"--config", config.toString()}, System.out, System.err, plain::set)).isZero();
            assertThat(plain.get().selection().isNone()).as("the test entry scans no home").isTrue();
            assertThat(plain.get().log()).isNull();
        } finally { if (plain.get() != null) plain.get().service().close(); }
    }

    /** Spec 4.6: pending only on the paths about to show a window or rehearse. */
    @Test void onlyAWindowOrAResidentRehearsalRecordsAPendingLook() {
        var plain = new AppArguments(null, false, false, false, null, false, false, false);
        var background = new AppArguments(null, false, true, false, null, false, false, false);
        var noWindow = new AppArguments(null, false, false, false, null, false, false, true);
        assertThat(ApplicationBootstrap.attemptBeforePresenting(plain, false, false)).as("the first window").isTrue();
        assertThat(ApplicationBootstrap.attemptBeforePresenting(background, true, false)).as("the resident rehearsal").isTrue();
        assertThat(ApplicationBootstrap.attemptBeforePresenting(background, false, false)).as("a background exit (quitNow)").isFalse();
        assertThat(ApplicationBootstrap.attemptBeforePresenting(noWindow, true, false)).as("--no-window, even resident").isFalse();
        assertThat(ApplicationBootstrap.attemptBeforePresenting(plain, false, true)).as("headless").isFalse();
        assertThat(ApplicationBootstrap.attemptBeforePresenting(background, true, true)).isFalse();
    }

    /** Spec 10.11: only a branded start that will open a window shows the splash. */
    @Test void onlyABrandedStartThatOpensAWindowShowsTheSplash() {
        var plain = AppArguments.parse(new String[0], directory);
        assertThat(ApplicationBootstrap.splashWanted(plain, false, true)).isTrue();
        assertThat(ApplicationBootstrap.splashWanted(plain, false, false)).as("no branding").isFalse();
        assertThat(ApplicationBootstrap.splashWanted(plain, true, true)).as("headless").isFalse();
        for (String flag : java.util.List.of("--background", "--no-window", "--safe-mode"))
            assertThat(ApplicationBootstrap.splashWanted(AppArguments.parse(new String[] {flag}, directory), false, true)).as(flag).isFalse();
    }

    /** Review Focus 5: the look's icons reach every branded launch, the splash only one that opens a window. */
    @Test void aBrandedLookGivesTheApplicationItsIconsEvenWithoutASplash() throws Exception {
        var dirs = new AppDirs(directory, directory.resolve("config.toml"), directory.resolve("logs"));
        var branded = dev.jasper.app.plugins.PluginSelection.of(dirs, null, "",
            dev.jasper.app.testsupport.BrandingFixtures.plugin(directory.resolve("dev"), "dev.example.look", "Example"), false);
        try {
            var background = new ApplicationBootstrap.Startup(null, AppArguments.parse(new String[] {"--background"}, directory), dirs, null, branded);
            assertThat(ApplicationBootstrap.openSplash(background, false)).isSameAs(dev.jasper.app.startup.StartupSteps.NONE);
            assertThat(dev.jasper.app.platform.ApplicationIcon.branded()).isTrue();
            assertThat(dev.jasper.app.platform.ApplicationIcon.images()).extracting(image -> image.getWidth(null))
                .containsExactly(16, 32, 48, 64, 128, 256, 1024);
            dev.jasper.app.platform.ApplicationIcon.useLook(java.util.List.of());
            var windowed = new ApplicationBootstrap.Startup(null, AppArguments.parse(new String[0], directory), dirs, null, branded);
            assertThat(ApplicationBootstrap.openSplash(windowed, true)).as("headless: no splash").isSameAs(dev.jasper.app.startup.StartupSteps.NONE);
            dev.jasper.app.platform.ApplicationIcon.useLook(java.util.List.of());
            var standard = new ApplicationBootstrap.Startup(null, AppArguments.parse(new String[0], directory), dirs, null,
                dev.jasper.app.plugins.PluginSelection.none());
            assertThat(ApplicationBootstrap.openSplash(standard, false)).isSameAs(dev.jasper.app.startup.StartupSteps.NONE);
            assertThat(dev.jasper.app.platform.ApplicationIcon.branded()).as("the default look keeps Jasper's icons").isFalse();
        } finally {
            dev.jasper.app.platform.ApplicationIcon.useLook(java.util.List.of());
        }
    }

    /** Spec 10.11: a windowed branded start opens the splash on its first step, four steps plus one per plugin. */
    @Test void aWindowedBrandedStartOpensTheSplashOnItsFirstStep() throws Exception {
        var dirs = new AppDirs(directory, directory.resolve("config.toml"), directory.resolve("logs"));
        var branded = dev.jasper.app.plugins.PluginSelection.of(dirs, null, "",
            dev.jasper.app.testsupport.BrandingFixtures.plugin(directory.resolve("dev"), "dev.example.look", "Example"), false);
        try {
            var windowed = new ApplicationBootstrap.Startup(null, AppArguments.parse(new String[0], directory), dirs, null, branded);
            // Headless here: the screen scale falls back to 1 and the posted window logs that it cannot open.
            var steps = ApplicationBootstrap.openSplash(windowed, false);
            assertThat(steps).isNotSameAs(dev.jasper.app.startup.StartupSteps.NONE);
            assertThat(steps.active()).isTrue();
            assertThat(steps.current()).isEqualTo(new dev.jasper.app.startup.StartupSteps.Step("Starting Java runtime…", 1,
                4 + branded.loading().size()));
            SwingUtilities.invokeAndWait(steps::workstation);
            assertThat(steps.current().text()).as("the product name").isEqualTo("Starting JasperOps Workstation…");
            SwingUtilities.invokeAndWait(steps::done);
            assertThat(steps.active()).isFalse();
        } finally {
            dev.jasper.app.platform.ApplicationIcon.useLook(java.util.List.of());
        }
    }

    /** Spec 10.11: each switch into the Dock sets the look's icon again; leaving the Dock does not. */
    @Test void enteringTheDockSetsTheIconAgain() {
        var calls = new java.util.ArrayList<String>();
        var show = ApplicationBootstrap.dockShow(visible -> calls.add("show " + visible), () -> calls.add("icon"));
        show.accept(false);
        show.accept(true);
        show.accept(false);
        show.accept(true);
        assertThat(calls).containsExactly("show false", "show true", "icon", "show false", "show true", "icon");
    }

    /** Spec 10.11: the splash shows the look's files, the version line and About's copyright. */
    @Test void theSplashContentIsTheLooksBrandingWithAboutsVersionAndCopyright() throws Exception {
        var dirs = new AppDirs(directory, directory.resolve("config.toml"), directory.resolve("logs"));
        var branded = dev.jasper.app.plugins.PluginSelection.of(dirs, null, "",
            dev.jasper.app.testsupport.BrandingFixtures.plugin(directory.resolve("dev"), "dev.example.look", "Example"), false);
        var build = dev.jasper.app.platform.BuildInfo.of(java.util.Map.of("commit", "8ba67ed3" + "0".repeat(32), "describe", "v1.2.0-14-g8ba67ed3"));
        var content = ApplicationBootstrap.splashContent(branded.presentation(), build);
        assertThat(content.product()).isEqualTo("JasperOps");
        assertThat(content.version()).isEqualTo("1.2.0 + 14 commits · Build 8ba67ed3");
        assertThat(content.copyright()).isEqualTo(dev.jasper.app.platform.BuildInfo.COPYRIGHT);
        assertThat(content.icon64()).isEqualTo(branded.presentation().branding().icon(64));
        assertThat(content.icon128()).isEqualTo(branded.presentation().branding().icon(128));
        assertThat(content.art()).isEqualTo(branded.presentation().branding().splashArt());
        assertThat(content.nameFace()).as("the fixture kit names no faces").isEmpty();
        assertThat(content.versionFace()).isEmpty();
    }

    /** Review Focus 1: a splash never outlives the start that opened it. */
    @Test void theStepsEndWhetherTheStartSucceedsOrFails() {
        var closed = new java.util.concurrent.atomic.AtomicInteger();
        var steps = dev.jasper.app.startup.StartupSteps.of("JasperOps", 0, step -> { }, closed::incrementAndGet);
        ApplicationBootstrap.presenting(steps, () -> { });
        assertThat(closed).hasValue(1);
        var failing = dev.jasper.app.startup.StartupSteps.of("JasperOps", 0, step -> { }, closed::incrementAndGet);
        assertThatThrownBy(() -> ApplicationBootstrap.presenting(failing, () -> { throw new IllegalStateException("composition failed"); }))
            .hasMessage("composition failed");
        assertThat(closed).hasValue(2);
    }

    /** Review Focus 1: a failed start closes a posted splash once, on the EDT; without a splash nothing is posted. */
    @Test void aFailedStartClosesAPostedSplashOnceAndPostsNothingWithoutOne() {
        var closed = new java.util.concurrent.atomic.AtomicInteger();
        var posted = new java.util.ArrayList<Runnable>();
        var steps = dev.jasper.app.startup.StartupSteps.of("JasperOps", 0, step -> { }, closed::incrementAndGet);
        ApplicationBootstrap.abandon(steps, posted::add);
        assertThat(closed).as("closed on the EDT, not on the failing thread").hasValue(0);
        assertThat(posted).hasSize(1);
        posted.forEach(Runnable::run);
        assertThat(closed).hasValue(1);
        ApplicationBootstrap.abandon(steps, posted::add);
        assertThat(posted).as("an ended splash posts nothing").hasSize(1);
        ApplicationBootstrap.abandon(dev.jasper.app.startup.StartupSteps.NONE, posted::add);
        assertThat(posted).as("no splash: nothing is posted, so AWT stays unstarted").hasSize(1);
        ApplicationBootstrap.abandon(dev.jasper.app.startup.StartupSteps.of("JasperOps", 0, step -> { }, closed::incrementAndGet), Runnable::run);
        assertThat(closed).as("startDesktop's catch, already on the EDT").hasValue(2);
    }
}
