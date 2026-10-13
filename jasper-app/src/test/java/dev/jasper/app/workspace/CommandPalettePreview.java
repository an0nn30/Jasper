package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.palette.*;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.Command;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.app.workspace.WindowContent;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.terminal.session.TerminalSession;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Opt-in actual-component command-palette renders; creates no native window. */
public final class CommandPalettePreview {
    private static final int LARGE_WIDTH = 900;
    private static final int LARGE_HEIGHT = 600;
    private static final int NARROW_WIDTH = 360;
    private static final int NARROW_HEIGHT = 500;

    private CommandPalettePreview() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Provide an output directory");
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        Integer expectedScale = expectedUiScale(args);
        try (var fixture = Fixture.create()) {
            if (java.util.Arrays.asList(args).contains("--task9c")) {
                fixture.renderTask9c(output);
            } else if (expectedScale == null) {
                fixture.renderMatrix(output);
            } else {
                fixture.verifyUiScale(output, expectedScale);
            }
        }
    }

    private static Integer expectedUiScale(String[] args) {
        for (int i = 1; i < args.length; i++) {
            String prefix = "--expect-ui-scale=";
            if (args[i].startsWith(prefix)) return Integer.parseInt(args[i].substring(prefix.length()));
        }
        return null;
    }

    static void capture(JComponent root, Path file, int width, int height, int scale) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            root.setSize(width, height);
            LayoutTestSupport.layoutTree(root);
            root.dispatchEvent(new ComponentEvent(root, ComponentEvent.COMPONENT_RESIZED));
            root.getRootPane().getLayeredPane().dispatchEvent(
                new ComponentEvent(root.getRootPane().getLayeredPane(), ComponentEvent.COMPONENT_RESIZED));
            LayoutTestSupport.layoutTree(root);
            var image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try {
                graphics.scale(scale, scale);
                root.printAll(graphics);
            } finally {
                graphics.dispose();
            }
            try {
                ImageIO.write(image, "png", file.toFile());
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        });
    }

    private enum Scenario {
        RECENTS("recents", "", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.ALL_ID),
        PANE_QUERY("pane-query", "pane", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.ALL_ID),
        NO_MATCH("no-match", "quasar never", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.ALL_ID),
        LONG_LABELS("long-labels-narrow", "preview fixture", NARROW_WIDTH, NARROW_HEIGHT, PaletteScope.ALL_ID),
        COMMANDS_TAB("commands-tab", "pane", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.COMMANDS_ID);

        final String slug;
        final String query;
        final int width;
        final int height;
        final String scope;

        Scenario(String slug, String query, int width, int height, String scope) {
            this.slug = slug;
            this.query = query;
            this.width = width;
            this.height = height;
            this.scope = scope;
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Queue<Runnable> pending;
        private final List<TerminalSession> sessions;
        private final ThemeController themes;
        private final CommandHistory history;
        private final WindowContent owner;
        private final JRootPane root;
        private final MacTitleBar titleBar;

        private Fixture(Queue<Runnable> pending, List<TerminalSession> sessions,
                        ThemeController themes, CommandHistory history,
                        WindowContent owner, JRootPane root, MacTitleBar titleBar) {
            this.pending = pending;
            this.sessions = sessions;
            this.themes = themes;
            this.history = history;
            this.owner = owner;
            this.root = root;
            this.titleBar = titleBar;
        }

        static Fixture create() throws Exception {
            Fixture[] result = new Fixture[1];
            SwingUtilities.invokeAndWait(() -> {
                Queue<Runnable> pending = new ArrayDeque<>();
                List<TerminalSession> sessions = new ArrayList<>();
                var launcher = new ShellLauncher(pending::add, directory -> {
                    TerminalSession session = DesktopTestSupport.shell(directory);
                    sessions.add(session);
                    return session;
                }, "sh");
                var themes = ThemeTestSupport.controller(true);
                var history = new CommandHistory();
                var owner = new WindowContent(launcher, DesktopTestSupport.HOME, path -> {}, () -> {}, () -> {},
                    themes, KeyBindings.defaults(true), history, true);
                var root = new JRootPane();
                var titleBar = WindowContent.installTitleBar(root, owner, true, title -> {});
                owner.installRootBindings(root);
                var fixture = new Fixture(pending, sessions, themes, history, owner, root, titleBar);
                fixture.addLongLabelCommands();
                history.record("find");
                history.record("split_right");
                history.record("new_tab");
                result[0] = fixture;
            });

            Fixture fixture = result[0];
            while (!fixture.pending.isEmpty()) fixture.pending.remove().run();
            DesktopTestSupport.until(() -> fixture.owner.currentPane().view() != null);
            SwingUtilities.invokeAndWait(() -> {
                fixture.owner.update();
                fixture.owner.commandPalette().toggle();
            });
            return fixture;
        }

        private void addLongLabelCommands() {
            List<String> labels = List.of(
                "Preview Fixture: Connect to the production session chooser with an unusually long destination label",
                "Preview Fixture: Open a deeply nested workspace configuration directory",
                "Preview Fixture: Restore the selected pane after a delayed launch",
                "Preview Fixture: Compare keyboard shortcuts across platforms",
                "Preview Fixture: Reopen the most recently closed development tab");
            for (int i = 0; i < labels.size(); i++) {
                var action = new AbstractAction(labels.get(i)) {
                    @Override public void actionPerformed(ActionEvent event) {}
                };
                action.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("meta shift " + (i + 1)));
                owner.commands().register(new Command("preview.fixture." + (i + 1), action,
                    List.of("preview", "fixture", "long label")));
            }
        }

        void renderMatrix(Path output) throws Exception {
            var manifest = new StringBuilder("# Command palette render manifest\n\n")
                .append("Swing UI scale: ").append(UIScale.getUserScaleFactor()).append("\n")
                .append("Pixel output scales: 1x, 2x\n\n");
            for (Scenario scenario : Scenario.values()) {
                for (boolean themeDark : new boolean[]{true, false}) {
                    configure(scenario, themeDark);
                    assertScenario(scenario);
                    for (int pixelScale : List.of(1, 2)) {
                        String name = scenario.slug + "-" + themeSlug(themeDark) + "-"
                            + scenario.width + "x" + scenario.height + "-" + pixelScale + "x.png";
                        Path file = output.resolve(name);
                        capture(root, file, scenario.width, scenario.height, pixelScale);
                        manifest.append(name).append('\n');
                        System.out.println(file);
                    }
                }
            }
            Files.writeString(output.resolve("render-manifest.txt"), manifest.toString());
        }

        void renderTask9c(Path output) throws Exception {
            Path home=Files.createTempDirectory("jasper-palette9c-");
            try {
                SwingUtilities.invokeAndWait(() -> {
                    // Every execution hook is inert. These source labels never run commands or contact hosts.
                    var rows=List.of(
                        new PaletteRow("package","package jasper","cd ~/projects/moray && ./gradlew :jasper-app:packageDist",null,null,true,null),
                        new PaletteRow("main","Main.java","jasper-app/src/main/java/dev/jasper/app/Main.java","moray",null,true,null),
                        new PaletteRow("amp","amp-amp (amp@100.110.65.45:22)",null,null,dev.jasper.app.platform.AppIcons.semantic("network"),true,null),
                        PaletteRow.of("new","New Editor File"),
                        new PaletteRow("minecraft","minecraft-local (dustin@192.168.1.124:22)",null,null,dev.jasper.app.platform.AppIcons.semantic("network"),true,null),
                        new PaletteRow("vm","dustin-vm (dustin@100.83.72.100:22)",null,null,dev.jasper.app.platform.AppIcons.semantic("network"),true,null),
                        new PaletteRow("open","Open in Editor…",null,"⌘O",dev.jasper.app.platform.AppIcons.named("FILE"),true,null),
                        new PaletteRow("builddir","open jasper build dir","cd ~/projects/moray && open ./jasper-app/build/packaging/dist/",null,null,true,null),
                        new PaletteRow("build","build jasper","cd ~/projects/moray && ./gradlew :jasper-app:run",null,null,true,null),
                        new PaletteRow("run","./gradlew :jasper-app:run","./gradlew :jasper-app:run",null,null,true,null));
                    var scopes=new java.util.LinkedHashMap<String,List<PaletteRow>>();
                    scopes.put("fixture.snippets",List.of(rows.get(0),rows.get(7),rows.get(8),rows.get(9)));
                    scopes.put("fixture.editor",List.of(rows.get(1),rows.get(3),rows.get(6)));
                    scopes.put("fixture.remote",List.of(rows.get(2),rows.get(4),rows.get(5)));
                    scopes.forEach((id,values)->owner.scopes().register(new PaletteScope(){
                        public String id(){return id;}public String label(){return id.substring(id.indexOf('.')+1);}
                        public String placeholder(){return "Search "+label();}
                        public List<PaletteVerb> verbs(){return List.of(new PaletteVerb("paste","Paste"),new PaletteVerb("run","Paste and run"),new PaletteVerb("edit","Edit file"));}
                        public PaletteResults search(String query,PaletteContext context){return new PaletteResults(values.stream().filter(row->query.isBlank()||row.title().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList(),null,null);}
                        public void execute(PaletteRow row,PaletteVerb verb,PaletteContext context){throw new AssertionError("Visual fixture must never execute");}
                        public dev.jasper.app.lifecycle.Subscription onChanged(Runnable listener){return new dev.jasper.app.lifecycle.Subscription(()->{});}
                    }));
                    for(int i=rows.size()-1;i>=0;i--) {
                        var row=rows.get(i);String scope=scopes.entrySet().stream().filter(entry->entry.getValue().contains(row)).findFirst().orElseThrow().getKey();
                        for(int count=0;count<rows.size()-i;count++)history.recordUse(scope,row.id(),row.title(),1_800_000_000_000L+i);
                    }
                    owner.commandPalette().controller().setVisibleRows(10);
                    owner.commandPalette().refresh();
                    if(PaletteTestSupport.rowCount(owner.commandPalette().component())!=10)throw new AssertionError("Missing source rows");
                    var entries=PaletteTestSupport.entries(owner.commandPalette().component());
                    if(!entries.get(0).equals("# Recently used")||!entries.get(3).equals("# Most used"))throw new AssertionError(entries);
                });
                for(boolean dark:new boolean[]{true,false})for(float font:List.of(13f,32f)) {
                    for(var size:List.of(new Dimension(791,712),new Dimension(400,520),new Dimension(791,260))) for(int deviceScale:List.of(1,2)) {
                        SwingUtilities.invokeAndWait(()->{
                            themes.configure(dark,dev.jasper.app.config.TerminalSchemeChoice.MATCH,new dev.jasper.app.config.UiFontConfig("system",font));
                            owner.commandPalette().refreshTheme();
                        });
                        Path file=output.resolve((dark?"dark":"light")+"-"+font+"-"+size.width+"x"+size.height+(deviceScale==1?"-1x":"")+".png");
                        capture(root,file,size.width,size.height,deviceScale);
                        SwingUtilities.invokeAndWait(()->{
                            var palette=owner.commandPalette().component();var query=PaletteTestSupport.query(palette);
                            var previous=java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
                            try {
                                java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(new java.awt.DefaultKeyboardFocusManager(){@Override public java.awt.Component getFocusOwner(){return query;}});
                                var event=new java.awt.event.FocusEvent(query,java.awt.event.FocusEvent.FOCUS_GAINED);
                                for(var listener:query.getFocusListeners())listener.focusGained(event);
                                dev.jasper.app.testsupport.SurfaceEvidence.save(file,root,size.width,size.height,deviceScale);
                                dev.jasper.app.testsupport.SurfaceEvidence.save(file.resolveSibling(file.getFileName()+"-palette.png"),palette,palette.getWidth(),palette.getHeight(),deviceScale);
                                try{Files.writeString(file.resolveSibling(file.getFileName()+"-owner.txt"),"actual WindowContent/WindowCommandPalette/PaletteController; synthetic focus; inert source labels; no native acceptance\nroot="+root.getSize()+"; palette="+palette.getBounds()+"; titleAnchor="+owner.paletteAnchor(root)+"; entries="+PaletteTestSupport.entries(palette)+"; selected="+PaletteTestSupport.selectedRow(palette).id()+"\n");}catch(IOException ex){throw new UncheckedIOException(ex);}
                            }finally {
                                java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);
                                var lost = new java.awt.event.FocusEvent(query,java.awt.event.FocusEvent.FOCUS_LOST);
                                for(var listener:query.getFocusListeners())listener.focusLost(lost);
                                query.getCaret().setVisible(false);
                            }
                        });
                    }
                }
            } finally {try(var paths=Files.walk(home)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        }

        private void configure(Scenario scenario, boolean dark) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                themes.configure(dark, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
                while (owner.commandPalette().stepOpen()) owner.commandPalette().escape();
                if (!scenario.scope.equals(owner.commandPalette().activeScopeId())) owner.commandPalette().open(scenario.scope);
                dev.jasper.app.palette.PaletteTestSupport.query(owner.commandPalette().component()).setText(scenario.query);
            });
        }

        private void assertScenario(Scenario scenario) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                var palette = owner.commandPalette().component();
                int count = dev.jasper.app.palette.PaletteTestSupport.rowCount(palette);
                int expected = switch (scenario) {
                    case RECENTS -> 3;
                    case PANE_QUERY, LONG_LABELS -> 5;
                    case NO_MATCH -> 0;
                    case COMMANDS_TAB -> 5;
                };
                if (count != expected) {
                    throw new AssertionError(scenario.slug + " expected " + expected + " rows, got " + count);
                }
                if (scenario == Scenario.PANE_QUERY) {
                    for (int i = 0; i < count; i++) {
                        String id = dev.jasper.app.palette.PaletteTestSupport.rowAt(palette, i).id();
                        if (id.startsWith("preview.")) throw new AssertionError("Pane query used synthetic command " + id);
                    }
                }
            });
        }

        void verifyUiScale(Path output, int expectedScale) throws Exception {
            configure(Scenario.LONG_LABELS, true);
            SwingUtilities.invokeAndWait(() -> {
                int actualUnit = UIScale.scale(1);
                var palette = owner.commandPalette().component();
                Dimension preferred = palette.getPreferredSize();
                int inputHeight = dev.jasper.app.palette.PaletteTestSupport.fieldRow(palette).getPreferredSize().height;
                int rowHeight = dev.jasper.app.palette.PaletteTestSupport.itemHeight(palette);
                if (actualUnit != expectedScale) {
                    throw new AssertionError("Expected UIScale " + expectedScale + "x, got " + actualUnit + "x");
                }
                if (preferred.width != 680 * expectedScale) {
                    throw new AssertionError("Palette width scaled more or less than once: " + preferred.width);
                }
                if (inputHeight != 44 * expectedScale) {
                    throw new AssertionError("Input height scaled more or less than once: " + inputHeight);
                }
                if (rowHeight != 24 * expectedScale) {
                    throw new AssertionError("Row height scaled more or less than once: " + rowHeight);
                }
            });
            int width = LARGE_WIDTH * expectedScale;
            int height = LARGE_HEIGHT * expectedScale;
            Path image = output.resolve("actual-ui-scale-" + expectedScale + "x.png");
            capture(root, image, width, height, 1);
            int[] bounds = new int[4];
            SwingUtilities.invokeAndWait(() -> {
                var palette = owner.commandPalette().component();
                bounds[0] = palette.getX();
                bounds[1] = palette.getY();
                bounds[2] = palette.getWidth();
                bounds[3] = palette.getHeight();
                if (palette.getWidth() != 680 * expectedScale) {
                    throw new AssertionError("Laid-out palette width scaled more or less than once: " + palette.getWidth());
                }
            });
            String report = "actual UIScale=" + expectedScale + "x\n"
                + "root=" + width + "x" + height + "\n"
                + "palette=" + bounds[0] + "," + bounds[1] + " " + bounds[2] + "x" + bounds[3] + "\n"
                + "preferredWidth=" + (680 * expectedScale) + "\n"
                + "inputHeight=" + (44 * expectedScale) + "\n"
                + "rowHeight=" + (24 * expectedScale) + "\n";
            Files.writeString(output.resolve("actual-ui-scale-" + expectedScale + "x.txt"), report);
            System.out.println(image);
            System.out.print(report);
        }

        private static String themeSlug(boolean dark) {
            return dark ? "dark" : "light";
        }

        @Override public void close() {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    if (titleBar != null) titleBar.close();
                    owner.close();
                    history.close();
                    themes.close();
                });
                for (TerminalSession session : sessions) session.exitFuture().get(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while closing preview fixture", interrupted);
            } catch (Exception failure) {
                throw new IllegalStateException("Could not close preview fixture", failure);
            }
        }

    }
}
