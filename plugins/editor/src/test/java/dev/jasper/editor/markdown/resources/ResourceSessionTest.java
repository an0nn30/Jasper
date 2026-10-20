package dev.jasper.editor.markdown.resources;

import dev.jasper.editor.api.*;
import dev.jasper.editor.markdown.MarkdownResult;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceSessionTest {
    static MarkdownResult result(String... destinations) {
        var images=new ArrayList<MarkdownResult.Image>();for(int i=0;i<destinations.length;i++)images.add(new MarkdownResult.Image(i,destinations[i],"alt"));
        return new MarkdownResult("",List.of(),Map.of(),List.of(),images);
    }
    static class Parent extends ImageLoaderTest.Doc {
        final Queue<ImageLoaderTest.Doc> children = new ArrayDeque<>();
        final AtomicInteger resolutions = new AtomicInteger();
        @Override public Optional<EditorDocument> resolveRelative(String path) {assertTrue(SwingUtilities.isEventDispatchThread());resolutions.incrementAndGet();return Optional.of(children.remove());}
    }
    static void edt(Runnable action) throws Exception {SwingUtilities.invokeAndWait(action);}
    @Test void emptyAltUsesFilenameInFailurePlaceholder() throws Exception {
        var reasons=new ArrayList<String>();
        try(var loader=new ImageLoader(new ImageBudget(1000))) {
            edt(()->{var s=new ResourceSession(loader,()->null,(id,image)->{},(id,reason)->reasons.add(reason));s.active(true);
                s.refresh(new MarkdownResult("",List.of(),Map.of(),List.of(),List.of(new MarkdownResult.Image(0,"images/missing.png",""))));s.close();});
            assertTrue(reasons.stream().anyMatch(reason->reason.contains("missing.png") && reason.contains("Save")));
        }
    }
    @Test void repeatedDestinationsShareLeaseAndReloadReadsFreshProviderBytes() throws Exception {
        var budget=new ImageBudget(1024);var parent=new Parent();var first=new ImageLoaderTest.Doc();first.supply(ImageLoaderTest.png(2,2));var second=new ImageLoaderTest.Doc();second.supply(ImageLoaderTest.png(3,3));parent.children.add(first);parent.children.add(second);
        var images=new ConcurrentHashMap<Integer,BufferedImage>();var ref=new AtomicReference<ResourceSession>();
        try(var loader=new ImageLoader(budget)) {
            edt(()->{var session=new ResourceSession(loader,()->parent,(id,image)->{assertTrue(SwingUtilities.isEventDispatchThread());if(image==null)images.remove(id);else images.put(id,image);},(id,reason)->{});ref.set(session);session.active(true);session.refresh(result("a.png","a.png"));});
            ImageLoaderTest.await(()->images.size()==2);assertSame(images.get(0),images.get(1));assertEquals(1,parent.resolutions.get());assertEquals(16,budget.usedBytes());
            edt(()->{ref.get().invalidate();ref.get().refresh(result("a.png"));});
            assertEquals(1,parent.resolutions.get());assertEquals(16,budget.usedBytes());
            edt(()->{ref.get().reload();ref.get().refresh(result("a.png"));});
            ImageLoaderTest.await(()->images.size()==1 && images.get(0).getWidth()==3);assertEquals(2,parent.resolutions.get());assertEquals(36,budget.usedBytes());
            edt(()->ref.get().close());assertEquals(0,budget.usedBytes());
        }
    }
    @Test void hiddenAndClosedCompletionCannotInstallAndCancelsProvider() throws Exception {
        var parent=new Parent();var child=new ImageLoaderTest.Doc();parent.children.add(child);var installed=new AtomicInteger();var budget=new ImageBudget(1000);var ref=new AtomicReference<ResourceSession>();
        try(var loader=new ImageLoader(budget)) {
            edt(()->{var s=new ResourceSession(loader,()->parent,(id,image)->{if(image!=null)installed.incrementAndGet();},(id,r)->{});ref.set(s);s.active(true);s.refresh(result("a.png"));});
            ImageLoaderTest.await(()->child.reads.get()==1);edt(()->ref.get().active(false));assertTrue(child.bytes.isCancelled());
            child.supply(ImageLoaderTest.png(1,1));edt(()->ref.get().close());assertEquals(0,installed.get());assertEquals(0,budget.usedBytes());
        }
    }
    @Test void routesWithEqualLabelsDoNotShareBytesAndFailedEntryCanRetry() throws Exception {
        var a=new Parent();var b=new Parent();var bad=new ImageLoaderTest.Doc();bad.supply(new byte[]{0});a.children.add(bad);
        var good=new ImageLoaderTest.Doc();good.supply(ImageLoaderTest.png(2,2));a.children.add(good);
        var other=new ImageLoaderTest.Doc();other.supply(ImageLoaderTest.png(3,3));b.children.add(other);
        var failures=new AtomicInteger();var images=new ConcurrentHashMap<String,BufferedImage>();var sessions=new ResourceSession[2];
        var budget=new ImageBudget(1000);
        try(var loader=new ImageLoader(budget)) {
            edt(()->{for(int i=0;i<2;i++){int n=i;sessions[i]=new ResourceSession(loader,()->n==0?a:b,(id,img)->{if(img==null)images.remove(""+n);else images.put(""+n,img);},(id,r)->{if(r.contains("corrupt"))failures.incrementAndGet();});sessions[i].active(true);sessions[i].refresh(result("a.png"));}});
            ImageLoaderTest.await(()->failures.get()>0 && images.size()==1);edt(()->sessions[0].retryFailed());ImageLoaderTest.await(()->images.size()==2);
            assertEquals(2,images.get("0").getWidth());assertEquals(3,images.get("1").getWidth());assertEquals(52,budget.usedBytes());edt(()->{sessions[0].close();sessions[1].close();});assertEquals(0,budget.usedBytes());
        }
    }
    @Test void decodedCompletionQueuedBehindCloseReleasesLeaseWithoutInstalling() throws Exception {
        var budget=new ImageBudget(1000);var parent=new Parent();var child=new ImageLoaderTest.Doc();parent.children.add(child);
        var installed=new AtomicInteger();byte[] bytes=ImageLoaderTest.png(2,2);
        try(var loader=new ImageLoader(budget)) {
            var session=new AtomicReference<ResourceSession>();
            edt(()->{var value=new ResourceSession(loader,()->parent,(id,image)->{if(image!=null)installed.incrementAndGet();},(id,r)->{});session.set(value);value.active(true);value.refresh(result("a.png"));});
            ImageLoaderTest.await(()->child.reads.get()==1);
            edt(()->{child.supply(bytes);try{ImageLoaderTest.await(()->budget.usedBytes()==16);}catch(Exception error){throw new AssertionError(error);}session.get().close();});
            ImageLoaderTest.await(()->budget.usedBytes()==0);assertEquals(0,installed.get());
        }
    }
    @Test void staleRefreshDoesNotInstallOldIdsAndUnsupportedImagesNeverResolve() throws Exception {
        var parent=new Parent();var old=new ImageLoaderTest.Doc();var replacement=new ImageLoaderTest.Doc();parent.children.add(old);parent.children.add(replacement);
        var installed=new ConcurrentHashMap<Integer,BufferedImage>();var ref=new AtomicReference<ResourceSession>();
        try(var loader=new ImageLoader(new ImageBudget(1000))) {
            edt(()->{var s=new ResourceSession(loader,()->parent,(id,image)->{if(image==null)installed.remove(id);else installed.put(id,image);},(id,r)->{});ref.set(s);s.active(true);s.refresh(result("old.png"));});
            ImageLoaderTest.await(()->old.reads.get()==1);
            edt(()->ref.get().refresh(result("file:/forbidden.png","fresh.png")));assertTrue(old.bytes.isCancelled());
            replacement.supply(ImageLoaderTest.png(3,3));ImageLoaderTest.await(()->installed.containsKey(1));assertFalse(installed.containsKey(0));assertEquals(2,parent.resolutions.get());
            edt(()->ref.get().close());
        }
    }
    @Test void webConsentSurvivesEditsButResetsOnSaveAsAndIsNeverShared() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(),0),0);var requests=new AtomicInteger();byte[] bytes=ImageLoaderTest.png(1,1);
        server.createContext("/",e->{requests.incrementAndGet();e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});server.start();
        String base="http://localhost:"+server.getAddress().getPort();var sessions=new ResourceSession[2];var images=new AtomicInteger();var identity=new AtomicReference<EditorDocument>(new Parent());
        try(var loader=new ImageLoader(new ImageBudget(1000))) {
            edt(()->{for(int i=0;i<2;i++){sessions[i]=new ResourceSession(loader,identity::get,(id,img)->{if(img!=null)images.incrementAndGet();},(id,r)->{});sessions[i].active(true);sessions[i].refresh(result(base+"/a"));}});
            assertEquals(0,requests.get());edt(()->sessions[0].allowExternal());ImageLoaderTest.await(()->images.get()==1);assertEquals(1,requests.get());
            edt(()->sessions[0].refresh(result(base+"/b")));ImageLoaderTest.await(()->requests.get()==2);
            edt(()->{identity.set(new Parent());sessions[0].identityChanged();sessions[0].refresh(result(base+"/c"));});assertEquals(2,requests.get());
            edt(()->{sessions[0].close();sessions[1].close();});
        }finally{server.stop(0);}
    }
}
