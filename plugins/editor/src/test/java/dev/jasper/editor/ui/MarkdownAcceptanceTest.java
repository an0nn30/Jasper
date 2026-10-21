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
                host.contentTabs().getLast().move(true);host.contentTabs().getLast().select();tabs[1].active(true);
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
