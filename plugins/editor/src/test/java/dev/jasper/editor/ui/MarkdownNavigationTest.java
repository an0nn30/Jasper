package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.MarkdownMode;
import dev.jasper.editor.markdown.resources.Destination;
import dev.jasper.editor.markdown.resources.Destinations;
import dev.jasper.editor.testsupport.MarkdownFixture;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.*;
import java.util.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class MarkdownNavigationTest {
    @TempDir Path home;
    FakePluginHost host;EditorWindow window;MarkdownNavigation navigation;
    List<String> recents=new ArrayList<>();
    static void edt(Runnable r) throws Exception {SwingUtilities.invokeAndWait(r);}
    @BeforeEach void setup() throws Exception {
        edt(() -> {
            dev.jasper.editor.syntax.Languages.register();host=new FakePluginHost(home.resolve("plugins"));
            var context=host.start(new PluginInfo("dev.jasper.editor","Editor","0.1.0",Set.of()),Set.of(),Set.of(),c -> {});
            var settings=new EditorSettings("Monospaced",13,List.of(),false,1,false,4,false,8,false);
            var env=new EditorEnvironment(context,SwingUtilities::invokeLater,owner -> q -> q.dismissed(),owner -> (m,f) -> false,() -> settings,b -> java.util.concurrent.CompletableFuture.completedFuture(null),false);
            window=new EditorWindow(env,() -> {},d -> recents.add(d.key()));navigation=new MarkdownNavigation(window,context.platform()::browse);
        });
    }
    @AfterEach void close() throws Exception {edt(host::close);}
    void drain() throws Exception {for(int i=0;i<12;i++){host.runBackground();edt(() -> {});}}
    EditorTab open(String name,String text) throws Exception {
        var path=Files.writeString(home.resolve(name),text);edt(() -> window.openLocal(path));drain();EditorTab[] tab={null};edt(() -> tab[0]=window.selected().orElseThrow());return tab[0];
    }
    @Test void sameFileSourceFragmentsParseOnWorkerAndNeverPublishPreview() throws Exception {
        var tab=open("origin.md","# First\n\n# Target\n");
        edt(() -> {navigation.follow(tab,new Destination.Fragment("target"));assertThat(tab.area().getCaretPosition()).isZero();});
        drain();edt(() -> {
            assertThat(tab.area().getCaretPosition()).isEqualTo(tab.area().getText().indexOf("# Target"));
            assertThat(tab.markdown().mode()).isEqualTo(MarkdownMode.SOURCE);
            assertThat(tab.markdown().currentResult()).isEmpty();assertThat(tab.surface().preview().installedResult()).isEmpty();
        });
    }
    @Test void relativeFragmentReusesDirtyTargetAndOriginalProviderWithoutReload() throws Exception {
        var target=open("target.md","# Disk\n");edt(() -> target.area().setText("# Unsaved\n\n# New Heading\n"));
        var origin=open("origin.md","[next](target.md#new-heading)");int before=recents.size();
        edt(() -> navigation.follow(origin,Destinations.parse("target.md#new-heading")));drain();
        edt(() -> {
            assertThat(window.selected()).contains(target);assertThat(target.session().dirty()).isTrue();
            assertThat(target.area().getText()).contains("Unsaved").doesNotContain("Disk");
            assertThat(target.area().getCaretPosition()).isEqualTo(target.area().getText().indexOf("# New Heading"));
            assertThat(recents).hasSize(before+1);
        });
    }
    @Test void unsupportedMissingAndUntitledLinksGiveNoticeAndWebHandoffRequiresActivation() throws Exception {
        var tab=open("origin.md","![image](missing.png) [web](https://example.com/a.md)\n");
        edt(() -> {
            assertThat(host.browsed()).isEmpty();assertThat(recents).hasSize(1);
            navigation.follow(tab,Destinations.parse("javascript:alert(1)"));assertThat(tab.navigationNotice()).isPresent();
            navigation.follow(tab,Destinations.parse("https://example.com/a.md"));assertThat(host.browsed()).hasSize(1);
            navigation.follow(tab,new Destination.Fragment("missing"));
        });drain();
        edt(() -> {assertThat(tab.navigationNotice()).contains("Heading not found");assertThat(tab.area().getCaretPosition()).isZero();});
    }
    @Test void editAndCloseInvalidateSourceFragmentJobs() throws Exception {
        var tab=open("origin.md","# Target\n");
        edt(() -> {navigation.follow(tab,new Destination.Fragment("target"));tab.area().setText("# Elsewhere\n");tab.area().setCaretPosition(3);});
        drain();edt(() -> {assertThat(tab.area().getCaretPosition()).isEqualTo(3);navigation.follow(tab,new Destination.Fragment("elsewhere"));tab.dispose();});
        drain();edt(() -> assertThat(tab.area().getCaretPosition()).isEqualTo(3));
    }
    @Test void previewFragmentUsesOnlyInstalledCurrentResultAndKeyboardSharesClickRoute() throws Exception {
        var tab=open("preview.md","[web](https://example.com)\n\n![image](missing.png)\n\n# Target\n\n"+"tail paragraph\n\n".repeat(25));
        edt(() -> {tab.surface().setFont(new java.awt.Font("Dialog",0,13));tab.markdownMode(MarkdownMode.PREVIEW,false);});drain();
        edt(() -> {
            assertThat(tab.markdown().currentResult()).isPresent();
            assertThat(recents).hasSize(1);
            var pane=tab.surface().preview();
            dev.jasper.editor.testsupport.PreviewFixture.layout(tab.surface().previewScroll,300,100);
            navigation.follow(tab,new Destination.Fragment("target"));
            assertThat(pane.editor().getCaretPosition()).isEqualTo(pane.anchor("target").orElseThrow());
            assertThat(tab.area().getCaretPosition()).isZero();
            pane.editor().setCaretPosition(0);pane.nextLink(true);pane.activateSelectedLink();
            assertThat(host.browsed()).hasSize(1);
            pane.editor().fireHyperlinkUpdate(new javax.swing.event.HyperlinkEvent(pane.editor(),javax.swing.event.HyperlinkEvent.EventType.ACTIVATED,null,"jasper-link:0"));
            assertThat(host.browsed()).hasSize(2);
            pane.editor().fireHyperlinkUpdate(new javax.swing.event.HyperlinkEvent(pane.editor(),javax.swing.event.HyperlinkEvent.EventType.ACTIVATED,null,"https://other.example"));
            assertThat(host.browsed()).hasSize(2);
            tab.area().append("changed");
            pane.activateSelectedLink();assertThat(host.browsed()).hasSize(2);
        });
    }
    @Test void delayedTargetWaitsForLoadAndClosingItDropsNavigation() throws Exception {
        var originDoc=new Provider("origin.md");originDoc.finish("# Origin\n");
        var targetDoc=new Provider("target.md");originDoc.relative=targetDoc;
        edt(() -> window.open(originDoc));drain();EditorTab[] origin=new EditorTab[1],target=new EditorTab[1];
        edt(() -> {origin[0]=window.selected().orElseThrow();navigation.follow(origin[0],Destinations.parse("target.md#later"));target[0]=window.selected().orElseThrow();
            assertThat(target[0].session().state()).isEqualTo(dev.jasper.editor.session.DocumentSession.State.LOADING);
            assertThat(recents).containsExactly(originDoc.key());});
        targetDoc.finish("# First\n\n# Later\n");drain();
        edt(() -> {assertThat(target[0].area().getCaretPosition()).isEqualTo(target[0].area().getText().indexOf("# Later"));
            assertThat(originDoc.resolved).containsExactly("target.md");assertThat(targetDoc.reads).isEqualTo(1);
            assertThat(recents).containsExactly(originDoc.key(),targetDoc.key());});
        var delayed=new Provider("delayed.md");originDoc.relative=delayed;
        edt(() -> {navigation.follow(origin[0],Destinations.parse("delayed.md#later"));target[0]=window.selected().orElseThrow();host.contentTabs().getLast().close();});
        delayed.finish("# Later\n");drain();
        edt(() -> {assertThat(target[0].closed()).isTrue();assertThat(recents).doesNotContain(delayed.key());assertThat(window.selected().orElse(null)).isNotSameAs(target[0]);});
    }
    @Test void saveAsIdentityChangeInvalidatesPendingFragment() throws Exception {
        var tab=open("original.md","# First\n\n# Target\n");var replacement=new Provider("replacement.md");
        var parse=new MarkdownFixture.ManualExecutor();
        edt(() -> {
            tab.markdown().close();var controller=new MarkdownController(tab,parse,SwingUtilities::invokeLater,tab::render);controller.active(true);
            navigation.follow(tab,new Destination.Fragment("target"));assertThat(parse.size()).isEqualTo(1);tab.session().saveAs(replacement);
        });drain();parse.runNext();drain();
        edt(() -> {assertThat(tab.session().document()).containsSame(replacement);assertThat(tab.area().getCaretPosition()).isZero();});
    }
    @Test void untitledAndUnsupportedProvidersAndMissingFilesStayInNormalEditorFlow() throws Exception {
        var tab=open("origin.md","# Origin\n");
        edt(() -> navigation.follow(tab,Destinations.parse("missing.md#target")));drain();
        edt(() -> {assertThat(window.selected().orElseThrow().session().state()).isEqualTo(dev.jasper.editor.session.DocumentSession.State.FAILED);assertThat(recents).hasSize(1);});
        edt(() -> {window.newUntitled();var untitled=window.selected().orElseThrow();navigation.follow(untitled,Destinations.parse("relative.md"));
            assertThat(untitled.navigationNotice()).contains("Save this document before opening relative links");});
        var unsupported=new dev.jasper.editor.testsupport.StubDocument("opaque","opaque.md","remote opaque", "server");
        edt(() -> window.open(unsupported));drain();
        edt(() -> {var current=window.selected().orElseThrow();navigation.follow(current,Destinations.parse("relative.md"));assertThat(current.navigationNotice()).contains("This document cannot resolve relative links");});
    }
    @Test void fragmentsInPlainTextAndOversizeMarkdownGiveNoticeInsteadOfWaitingForever() throws Exception {
        var plain=open("plain.txt","plain text");
        edt(() -> navigation.follow(plain,new Destination.Fragment("missing")));drain();
        edt(() -> assertThat(plain.navigationNotice()).contains("Heading not found"));
        var large=open("large.md","# Heading");
        edt(() -> {large.area().setText("x".repeat(dev.jasper.editor.markdown.MarkdownLimits.SOURCE_UNITS+1));navigation.follow(large,new Destination.Fragment("heading"));});
        edt(() -> assertThat(large.navigationNotice()).contains("Could not navigate to heading"));
    }
    private static final class Provider implements dev.jasper.editor.api.EditorDocument {
        final String name;final java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Content> read=new java.util.concurrent.CompletableFuture<>();
        Provider relative;int reads;List<String> resolved=new ArrayList<>();
        Provider(String name){this.name=name;}
        void finish(String text){byte[] bytes=text.getBytes(java.nio.charset.StandardCharsets.UTF_8);read.complete(new dev.jasper.editor.api.Content(bytes,new dev.jasper.editor.api.Revision(1,bytes.length)));}
        public String key(){return "route:"+name;}public String name(){return name;}public String location(){return "opaque:"+name;}
        public Optional<String> host(){return Optional.of("remote");}
        public Optional<dev.jasper.editor.api.EditorDocument> resolveRelative(String path){resolved.add(path);return Optional.ofNullable(relative);}
        public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Content> read(long limit){reads++;return read;}
        public java.util.concurrent.CompletableFuture<Optional<dev.jasper.editor.api.Revision>> revision(){return java.util.concurrent.CompletableFuture.completedFuture(Optional.of(new dev.jasper.editor.api.Revision(1,1)));}
        public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Revision> write(byte[] bytes){return java.util.concurrent.CompletableFuture.completedFuture(new dev.jasper.editor.api.Revision(2,bytes.length));}
    }

}
