package dev.jasper.editor.ui;

import dev.jasper.editor.EditorPlugin;
import dev.jasper.editor.api.*;
import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.markdown.MarkdownMode;
import dev.jasper.editor.markdown.resources.Destinations;
import dev.jasper.sdk.*;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.TerminalTypography;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** Shipped plugin, two hosted windows, real local I/O and an opaque fake SFTP storage route. */
class MarkdownAcceptanceTest {
    @TempDir Path home;
    static void edt(Runnable action) throws Exception {SwingUtilities.invokeAndWait(action);}
    static void drain(FakePluginHost host) throws Exception {
        for(int i=0;i<30;i++){host.runBackground();edt(host::flush);Thread.sleep(2);}
    }
    static EditorWindow window(EditorPlugin plugin) {
        try {var field=EditorPlugin.class.getDeclaredField("window");field.setAccessible(true);return (EditorWindow)field.get(plugin);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    @Test void shippedPluginKeepsDocumentsRoutesPreferencesAndLifetimesAcrossWindows() throws Exception {
        var requests=new java.util.concurrent.atomic.AtomicInteger();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/image",exchange -> {requests.incrementAndGet();byte[] bytes=Files.readAllBytes(home.resolve("picture.png"));exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}exchange.close();});server.start();
        String web="\n![web](http://127.0.0.1:"+server.getAddress().getPort()+"/image)\n";
        var local=Files.writeString(home.resolve("local.md"),"# Local\n\n![allowed](picture.png)\n"+web);
        var pixels=new java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javax.imageio.ImageIO.write(pixels,"png",home.resolve("picture.png").toFile());
        var remote=new Remote("jump-A/login-one","remote.md","# Remote\n\n![allowed](picture.png)\n"+web);
        remote.files.put("picture.png",Files.readAllBytes(home.resolve("picture.png")));
        FakePluginHost[] ref={null};PluginContext[] context={null};EditorPlugin plugin=new EditorPlugin();
        EditorTab[] tabs=new EditorTab[2];Object[] sourceDocuments=new Object[2];Object[] loader={null};
        try {
            edt(() -> {
                var host=new FakePluginHost(home.resolve("plugins"));ref[0]=host;
                host.declareSubcommand("dev.jasper.editor","editor","Open files");
                context[0]=host.start(new PluginInfo("dev.jasper.editor","Editor","0.11.0",Set.of("palette.contribute","terminal.environment","editor.provide","terminal.open")),Set.of(),Set.of(),plugin);
                UUID first=host.addTerminalWindow(),second=host.addTerminalWindow();
                var consumer=host.start(new PluginInfo("test.consumer","Consumer","1.0.0",Set.of()),Set.of("dev.jasper.editor"),Set.of(),c -> {});
                var api=consumer.services().require(Editor.class);
                api.open(local,context[0].terminals().window(first).orElseThrow());
                tabs[0]=window(plugin).selected().orElseThrow();
                api.open(remote,context[0].terminals().window(second).orElseThrow());
                tabs[1]=window(plugin).selected().orElseThrow();
            });
            var host=ref[0];drain(host);
            edt(() -> {
                for(int i=0;i<tabs.length;i++) {
                    var tab=tabs[i];sourceDocuments[i]=tab.area().getDocument();tab.area().discardAllEdits();tab.area().append("\n# Unsaved\n");
                    tab.surface().setFont(new java.awt.Font("Serif",0,13));tab.markdownMode(MarkdownMode.SPLIT,true);
                }
                loader[0]=field(plugin,"imageLoader");
                tabs[1].markdownMode(MarkdownMode.PREVIEW,true);
                assertThat(tabs[0].markdown().mode()).isEqualTo(MarkdownMode.SPLIT);
                host.setSetting("dev.jasper.editor","follow_terminal_font",true);
                host.setTerminalTypography(new TerminalTypography("Monospaced",13.5,List.of("Serif"),true,.5));
            });drain(host);
            edt(() -> {
                for(var tab:tabs){assertThat(tab.area().getFont().getSize2D()).isEqualTo(13.5f);assertThat(tab.markdown().currentResult()).isPresent();}
                assertThat(requests).hasValue(0);
                assertThat(tabs[0].markdown().needsImageConsent()).isTrue();assertThat(tabs[1].markdown().needsImageConsent()).isTrue();
                var appearance=context[0].appearance();
                host.setWorkspace(Variant.DARK,PreviewPaneTest.palette(true),appearance.editorPalette(),EditorThemeTest.syntax(0x552200));
                var query=new org.fife.ui.rtextarea.SearchContext();query.setSearchFor("Unsaved");
                assertThat(new PreviewSearch(tabs[1].surface().preview()).markAll(query)).isEqualTo(1);
                tabs[1].area().undoLastAction();assertThat(tabs[1].area().getText()).doesNotContain("Unsaved");
                tabs[1].area().redoLastAction();
                tabs[1].session().save();tabs[0].session().save();
            });drain(host);
            // Grant consent after undo/redo has settled its replacement render generation.
            edt(() -> {assertThat(requests).hasValue(0);tabs[1].markdown().allowImages();});
            long deadline=System.nanoTime()+5_000_000_000L;
            while(requests.get()==0 && System.nanoTime()<deadline){drain(host);}
            assertThat(requests).hasValue(1);
            edt(() -> {assertThat(tabs[0].markdown().needsImageConsent()).isTrue();assertThat(tabs[1].markdown().needsImageConsent()).isFalse();
                assertThat((Map<?,?>)field(tabs[0].surface().preview(),"images")).isNotEmpty();});
            assertThat(Files.readString(local)).contains("Unsaved");
            assertThat(new String(remote.files.get("remote.md"),StandardCharsets.UTF_8)).contains("Unsaved");
            edt(() -> {
                assertThat(remote.resolved).contains("jump-A/login-one:picture.png");
                tabs[0].area().append("dirty target\n");
                new MarkdownNavigation(window(plugin),context[0].platform()::browse).follow(tabs[0],Destinations.parse("local.md#unsaved"));
                tabs[1].active(false);tabs[1].area().append("pending edit\n");
                host.contentTabs().getLast().moveToNewWindow();host.contentTabs().getLast().select();tabs[1].active(true);
            });drain(host);
            edt(() -> {
                assertThat(tabs[0].area().getText()).contains("dirty target");assertThat(tabs[0].session().dirty()).isTrue();
                assertThat(tabs[1].session().document()).containsSame(remote);
                for(int i=0;i<tabs.length;i++)assertThat(tabs[i].area().getDocument()).isSameAs(sourceDocuments[i]);
                tabs[1].session().saveAs(new LocalDocument(home.resolve("copy.md"),context[0].background()));
            });drain(host);
            edt(() -> {assertThat(tabs[1].markdown().needsImageConsent()).isTrue();tabs[1].session().reload();tabs[1].area().append("late render\n");tabs[1].markdown().flushDebounce();host.close();});
            drain(host);
            edt(() -> {
                assertThat(host.contentTabs()).isEmpty();assertThat(host.failures()).isEmpty();
                assertThat(field(field(loader[0],"budget"),"used")).isEqualTo(0L);
                for(var tab:tabs)for(String name:List.of("running","pending","installing"))assertThat(field(tab.markdown(),name)).isNull();
                for(var tab:tabs){assertThat(tab.closed()).isTrue();assertThat(tab.markdown().currentResult()).isEmpty();assertThat(tab.surface().preview().installedResult()).isEmpty();}
                try {var field=FakePluginHost.class.getDeclaredField("typographyHandlers");field.setAccessible(true);assertThat((List<?>)field.get(host)).isEmpty();}
                catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
        } finally {if(ref[0]!=null)edt(ref[0]::close);server.stop(0);}
    }
    /**
     * The merge fix wave's L1: a preview follows whether its pane shows, not focus. [Markdown | terminal] in one tab with
     * the focus on the terminal: the preview stays rendered with its images, and an edit renders again.
     */
    @Test void aPreviewBesideTheFocusedTerminalRendersAndKeepsItsImages() throws Exception {
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_ARGB),"png",home.resolve("picture.png").toFile());
        var notes=Files.writeString(home.resolve("notes.md"),"# Notes\n\n![picture](picture.png)\n");
        try (var started=new Started(home,"preview")) {
            var host=started.host;
            var handle=started.open(notes);
            var tab=started.tab(handle);
            edt(() -> mount(tab.surface()));
            started.until(() -> tab.markdown().currentResult().isPresent() && !images(tab).isEmpty(),"the preview with its image");
            dev.jasper.editor.markdown.MarkdownResult[] rendered={null};
            edt(() -> {
                rendered[0]=tab.markdown().currentResult().orElseThrow();
                handle.split(dev.jasper.sdk.ui.SplitChoice.TERMINAL,dev.jasper.sdk.terminal.Direction.RIGHT);
                assertThat(host.windowTabs(started.main).getLast()).as("[Markdown | terminal] in one tab").hasSize(2);
                assertThat(handle.isActive()).as("the terminal has the focus").isFalse();
            });
            drain(host);
            edt(() -> {
                assertThat(tab.markdown().currentResult()).as("still rendered").containsSame(rendered[0]);
                assertThat(images(tab)).as("its image kept").isNotEmpty();
                tab.area().append("\n# Later\n");tab.markdown().flushDebounce();
            });
            started.until(() -> tab.markdown().currentResult().isPresent() && tab.surface().preview().editor().getText().contains("Later")
                && !images(tab).isEmpty(),"the edit rendered");
            assertThat(host.failures()).isEmpty();
        }
    }

    /** The merge fix wave's L1: a Markdown document restored in Preview into a pane without the focus renders. */
    @Test void aRestoredPreviewInAPaneWithoutTheFocusRenders() throws Exception {
        var notes=Files.writeString(home.resolve("notes.md"),"# Restored\n\nBack again\n");
        try (var started=new Started(home,"preview")) {
            var host=started.host;
            Object[] handle={null};
            edt(() -> {
                assertThat(host.restoreTab("dev.jasper.editor",started.main,"notes.md",
                    RestoreState.of("document","key","local:"+notes.toAbsolutePath().normalize()))).isPresent();
                handle[0]=host.contentTab("dev.jasper.editor","notes.md").orElseThrow();
                assertThat(((dev.jasper.sdk.ui.ContentTab)handle[0]).isActive()).as("not the window's active item").isFalse();
            });
            var tab=started.tab((dev.jasper.sdk.ui.ContentTab)handle[0]);
            edt(() -> mount(tab.surface()));
            started.until(() -> tab.markdown().currentResult().isPresent(),"the restored preview");
            edt(() -> {
                assertThat(tab.markdown().mode()).isEqualTo(MarkdownMode.PREVIEW);
                assertThat(tab.surface().preview().editor().getText()).contains("Restored","Back again");
            });
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * The merge fix wave's L1: a preview whose tab is hidden, another tab of its window shown, suspends and releases its
     * image; shown again, it renders again. The window's strip hides a tab by hiding its workspace, which tells the
     * preview's view it no longer shows ({@code HostedContentTest}); here the surface's holder hides.
     */
    @Test void aPreviewWhoseTabIsHiddenSuspendsAndReleasesItsImages() throws Exception {
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_ARGB),"png",home.resolve("picture.png").toFile());
        var notes=Files.writeString(home.resolve("notes.md"),"# Notes\n\n![picture](picture.png)\n");
        var other=Files.writeString(home.resolve("other.md"),"# Other\n");
        try (var started=new Started(home,"preview")) {
            var host=started.host;
            var handle=started.open(notes);
            var tab=started.tab(handle);
            javax.swing.JPanel[] holder={null};
            edt(() -> holder[0]=mount(tab.surface()));
            started.until(() -> tab.markdown().currentResult().isPresent() && !images(tab).isEmpty(),"the preview with its image");
            var shown=started.open(other);
            edt(() -> {
                assertThat(handle.isActive()).isFalse();
                holder[0].setVisible(false);
                assertThat(tab.surface().isShowing()).isFalse();
                assertThat(tab.markdown().currentResult()).as("suspended").isEmpty();
                assertThat(images(tab)).as("its pixels released").isEmpty();
                assertThat(field(field(tab.markdown(),"resources"),"active")).isEqualTo(false);
            });
            drain(host);
            edt(() -> assertThat(tab.markdown().currentResult()).as("nothing renders while hidden").isEmpty());
            edt(() -> {holder[0].setVisible(true);handle.select();assertThat(shown.isActive()).isFalse();});
            started.until(() -> tab.markdown().currentResult().isPresent() && !images(tab).isEmpty(),"the preview shown again");
            assertThat(host.failures()).isEmpty();
        }
    }

    private static Map<?,?> images(EditorTab tab) { return (Map<?,?>)field(tab.surface().preview(),"images"); }

    /**
     * Puts a document's surface where a person sees it, in a holder its window shows, as Jasper's kit mounts it in the
     * tab's view; this fake host's kit mounts no content. UI thread.
     */
    @Test void aPreviewMountedHiddenWaitsUntilItShows() throws Exception {
        // A restored tab that is not the shown one is realized hidden: it must not render (or hold images) until shown.
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_ARGB),"png",home.resolve("picture.png").toFile());
        var notes=Files.writeString(home.resolve("notes.md"),"# Notes\n\n![picture](picture.png)\n");
        try (var started=new Started(home,"preview")) {
            var host=started.host;
            var handle=started.open(notes);
            var tab=started.tab(handle);
            javax.swing.JPanel[] holder={null};
            edt(() -> {
                var hidden=new javax.swing.JPanel(new java.awt.BorderLayout());
                hidden.setVisible(false);
                hidden.add(tab.surface());hidden.setSize(800,600);hidden.addNotify();hidden.doLayout();
                assertThat(tab.surface().isDisplayable()).isTrue();
                assertThat(tab.surface().isShowing()).isFalse();
                holder[0]=hidden;
            });
            drain(host);
            edt(() -> {
                assertThat(tab.markdown().currentResult()).as("nothing renders while hidden").isEmpty();
                assertThat(images(tab)).as("no pixels held while hidden").isEmpty();
            });
            edt(() -> holder[0].setVisible(true));
            started.until(() -> tab.markdown().currentResult().isPresent() && !images(tab).isEmpty(),"the preview once shown");
            assertThat(host.failures()).isEmpty();
        }
    }

    private static javax.swing.JPanel mount(DocumentArea surface) {
        var holder=new javax.swing.JPanel(new java.awt.BorderLayout());
        holder.add(surface);holder.setSize(800,600);holder.addNotify();holder.doLayout();
        assertThat(surface.isShowing()).isTrue();
        return holder;
    }

    /** The shipped Editor in a fake host whose main window holds a terminal, its Markdown default view {@code view}. */
    private static final class Started implements AutoCloseable {
        final FakePluginHost host;
        final EditorPlugin plugin=new EditorPlugin();
        final UUID main;
        final PluginContext consumer;
        Started(Path home,String view) throws Exception {
            FakePluginHost[] made={null};UUID[] window={null};PluginContext[] context={null};
            edt(() -> {
                var fake=new FakePluginHost(home.resolve("plugins"));made[0]=fake;
                fake.setSetting("dev.jasper.editor","markdown_view_mode",view);
                fake.declareSubcommand("dev.jasper.editor","editor","Open files");
                fake.start(new PluginInfo("dev.jasper.editor","Editor","0.11.0",Set.of("palette.contribute","terminal.environment","editor.provide","terminal.open")),Set.of(),Set.of(),plugin);
                window[0]=fake.addTerminalWindow();fake.addTerminalTab(window[0],"zsh");
                context[0]=fake.start(new PluginInfo("test.consumer","Consumer","1.0.0",Set.of()),Set.of("dev.jasper.editor"),Set.of(),c -> {});
                assertThat(fake.failures()).as("both started").isEmpty();
            });
            host=made[0];main=window[0];consumer=context[0];
        }
        /** Opens {@code file} in the main window, where it is the active item, once it has read. */
        dev.jasper.sdk.ui.ContentTab open(Path file) throws Exception {
            int before=host.contentTabs().size();
            edt(() -> consumer.services().require(Editor.class).open(file,consumer.terminals().window(main).orElseThrow()));
            drain(host);
            return host.contentTabs().get(before);
        }
        EditorTab tab(dev.jasper.sdk.ui.ContentTab handle) {
            var hosted=(Map<?,?>)field(window(plugin),"hosted");
            return (EditorTab)hosted.entrySet().stream().filter(entry -> entry.getValue()==handle).findFirst().orElseThrow().getKey();
        }
        void until(java.util.function.BooleanSupplier done,String what) throws Exception {
            long deadline=System.nanoTime()+10_000_000_000L;boolean[] met={false};
            while(System.nanoTime()<deadline){drain(host);edt(() -> met[0]=done.getAsBoolean());if(met[0])return;}
            throw new AssertionError("timed out waiting for "+what);
        }
        @Override public void close() {
            try { edt(host::close); }
            catch (Exception failure) { throw new AssertionError(failure); }
        }
    }

    private static Object field(Object object,String name) {
        try{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static final class Remote implements EditorDocument {
        final String route,name;final Map<String,byte[]> files;final List<String> resolved;long version=1;
        Remote(String route,String name,String text){this(route,name,new HashMap<>(),new ArrayList<>());files.put(name,text.getBytes(StandardCharsets.UTF_8));}
        Remote(String route,String name,Map<String,byte[]> files,List<String> resolved){this.route=route;this.name=name;this.files=files;this.resolved=resolved;}
        public String key(){return route+":"+name;}public String name(){return name;}public String location(){return "display only";}public Optional<String> host(){return Optional.of("same-display-host");}
        public Optional<EditorDocument> resolveRelative(String path){resolved.add(route+":"+path);return Optional.of(new Remote(route,path,files,resolved));}
        public CompletableFuture<Content> read(long maximum){var bytes=files.get(name);return CompletableFuture.completedFuture(new Content(bytes,new Revision(version,bytes.length)));}
        public CompletableFuture<Optional<Revision>> revision(){return CompletableFuture.completedFuture(Optional.of(new Revision(version,files.get(name).length)));}
        public CompletableFuture<Revision> write(byte[] bytes){files.put(name,bytes.clone());return CompletableFuture.completedFuture(new Revision(++version,bytes.length));}
    }
}
