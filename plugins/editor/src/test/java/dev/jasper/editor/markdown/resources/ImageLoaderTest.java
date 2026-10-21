package dev.jasper.editor.markdown.resources;

import com.sun.net.httpserver.HttpServer;
import dev.jasper.editor.api.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("try")
class ImageLoaderTest {
    static byte[] png(int w, int h) throws Exception {
        var out = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB),"png",out); return out.toByteArray();
    }
    static class Doc implements EditorDocument {
        final CompletableFuture<Content> bytes = new CompletableFuture<>();
        final java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        volatile long limit;
        public String key(){return "same";} public String name(){return "same.png";} public String location(){return "same.png";} public Optional<String> host(){return Optional.empty();}
        public CompletableFuture<Content> read(long limit){assertTrue(SwingUtilities.isEventDispatchThread()); this.limit=limit;reads.incrementAndGet();return bytes;}
        public CompletableFuture<Optional<Revision>> revision(){return CompletableFuture.completedFuture(Optional.empty());}
        public CompletableFuture<Revision> write(byte[] b){throw new UnsupportedOperationException();}
        void supply(byte[] b){bytes.complete(new Content(b,new Revision(0,b.length)));}
    }
    static void await(java.util.function.BooleanSupplier ready) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!ready.getAsBoolean() && System.nanoTime()<until) Thread.sleep(5);
        assertTrue(ready.getAsBoolean());
    }
    @Test void decodedImageOwnsExactlyOneLeaseAndProviderReadsAreBounded() throws Exception {
        var budget=new ImageBudget(1024);var doc=new Doc();doc.supply(png(4,5));
        try(var loader=new ImageLoader(budget);var loaded=loader.load(doc).get(5,TimeUnit.SECONDS)) {
            assertEquals(8*1024*1024,doc.limit);assertEquals(4,loaded.image().getWidth());assertEquals(80,budget.usedBytes());
            loaded.close();loaded.close();assertEquals(0,budget.usedBytes());
        }
    }
    @Test void fourLoadsIncludePendingProvidersAndQueuedCancellationNeverReads() throws Exception {
        var docs=java.util.stream.IntStream.range(0,6).mapToObj(i->new Doc()).toList();
        var budget=new ImageBudget(1000);
        try(var loader=new ImageLoader(budget)) {
            var loads=docs.stream().map(loader::load).toList();
            await(()->docs.subList(0,4).stream().allMatch(d->d.reads.get()==1));
            assertEquals(0,docs.get(4).reads.get());loads.get(4).cancel(true);
            loads.get(0).cancel(true);await(()->docs.get(5).reads.get()==1);
            assertTrue(docs.get(0).bytes.isCancelled());assertEquals(0,docs.get(4).reads.get());
        }
        await(()->budget.usedBytes()==0);
    }
    @Test void rejectsCorruptOversizedAndBombHeadersWithoutBudgetLeak() throws Exception {
        var budget=new ImageBudget(64L*1024*1024);
        try(var loader=new ImageLoader(budget)) {
            for(byte[] bytes:List.of(new byte[]{1,2,3},new byte[8*1024*1024+1],png(4001,4000))) {
                var doc=new Doc();doc.supply(bytes);assertThrows(ExecutionException.class,()->loader.load(doc).get(5,TimeUnit.SECONDS));assertEquals(0,budget.usedBytes());
            }
        }
    }
    @Test void unsupportedDestinationsAndDowngradeAreRejectedBeforeNetwork() {
        for(String s:List.of("file:/tmp/x", "https://user:pass@localhost/x", "data:image/png,x"))
            assertThrows(IllegalArgumentException.class,()->ImageLoader.checkedUri(URI.create(s),null));
        assertThrows(IllegalArgumentException.class,()->ImageLoader.checkedUri(URI.create("http://localhost/x"),URI.create("https://localhost/x")));
    }
    @Test void streamsWithoutLengthFollowsOnlyThreeRedirectsAndSendsNoAmbientHeaders() throws Exception {
        byte[] bytes=png(2,3);var requests=new java.util.concurrent.atomic.AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.createContext("/image",e->{requests.incrementAndGet();assertNull(e.getRequestHeaders().getFirst("Cookie"));assertNull(e.getRequestHeaders().getFirst("Referer"));assertNull(e.getRequestHeaders().getFirst("Authorization"));e.sendResponseHeaders(200,0);e.getResponseBody().write(bytes);e.close();});
        server.createContext("/redirect",e->{int n=Integer.parseInt(e.getRequestURI().getQuery());e.getResponseHeaders().set("Location",n==1?"/image":"/redirect?"+(n-1));e.sendResponseHeaders(302,-1);e.close();});
        server.start();var budget=new ImageBudget(1000);
        try(var loader=new ImageLoader(budget)) {
            var base="http://localhost:"+server.getAddress().getPort();
            try(var loaded=loader.load(URI.create(base+"/redirect?3")).get(5,TimeUnit.SECONDS)){assertEquals(3,loaded.image().getHeight());}
            assertThrows(ExecutionException.class,()->loader.load(URI.create(base+"/redirect?4")).get(5,TimeUnit.SECONDS));assertEquals(1,requests.get());
        } finally {server.stop(0);}
        assertEquals(0,budget.usedBytes());
    }
    @Test void exactEncodedBoundaryAndFirstGifFrameAreAccepted() throws Exception {
        var budget=new ImageBudget(1024);
        try(var loader=new ImageLoader(budget)) {
            var padded=new Doc();padded.supply(Arrays.copyOf(png(2,2),8*1024*1024));
            try(var loaded=loader.load(padded).get(5,TimeUnit.SECONDS)){assertEquals(2,loaded.image().getWidth());}
            var bytes=new ByteArrayOutputStream();
            var writer=ImageIO.getImageWritersByFormatName("gif").next();
            try(var output=ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(output);writer.prepareWriteSequence(null);
                for(int color:List.of(0xFFFF0000,0xFF0000FF)) {
                    var frame=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);frame.setRGB(0,0,color);
                    writer.writeToSequence(new javax.imageio.IIOImage(frame,null,null),null);
                }
                writer.endWriteSequence();
            }finally{writer.dispose();}
            var gif=new Doc();gif.supply(bytes.toByteArray());
            try(var loaded=loader.load(gif).get(5,TimeUnit.SECONDS)){assertEquals(0xFFFF0000,loaded.image().getRGB(0,0));assertEquals(16,budget.usedBytes());}
        }
        assertEquals(0,budget.usedBytes());
    }
    @Test void simultaneousDecodesShareOneBudgetAndReleasedCapacityCanBeRetried() throws Exception {
        var budget=new ImageBudget(400);var docs=java.util.stream.IntStream.range(0,4).mapToObj(i->new Doc()).toList();
        try(var loader=new ImageLoader(budget)) {
            var loads=docs.stream().map(loader::load).toList();byte[] bytes=png(10,10);
            await(()->docs.stream().allMatch(d->d.reads.get()==1));docs.forEach(d->d.supply(bytes));
            for(var load:loads) try{load.get(5,TimeUnit.SECONDS);}catch(ExecutionException expected){}
            var successes=loads.stream().filter(f->!f.isCompletedExceptionally()).toList();assertEquals(1,successes.size());assertEquals(400,budget.usedBytes());
            successes.getFirst().join().close();assertEquals(0,budget.usedBytes());
            try(var retried=loader.load(docs.getFirst()).get(5,TimeUnit.SECONDS)){assertEquals(10,retried.image().getWidth());assertEquals(400,budget.usedBytes());}
        }
        assertEquals(0,budget.usedBytes());
    }
    @Test void readsRealTemporaryProviderFileWithoutTextDecoding(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var file=directory.resolve("image.bin");java.nio.file.Files.write(file,png(3,4));
        try(var executor=Executors.newSingleThreadExecutor();var loader=new ImageLoader(new ImageBudget(1024));
            var image=loader.load(new dev.jasper.editor.local.LocalDocument(file,executor)).get(5,TimeUnit.SECONDS)) {
            assertEquals(4,image.image().getHeight());
        }
    }
    @Test void streamBoundsAndUnsupportedRedirectsAreEnforcedAtEachHop() throws Exception {
        var reached=new java.util.concurrent.atomic.AtomicInteger();var server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.createContext("/large",e->{try{e.sendResponseHeaders(200,0);byte[] block=new byte[8192];for(int i=0;i<1025;i++)e.getResponseBody().write(block);}catch(java.io.IOException expected){}finally{e.close();}});
        server.createContext("/declared",e->{e.sendResponseHeaders(200,8*1024*1024+1);e.close();});
        server.createContext("/false",e->{e.sendResponseHeaders(200,4096);e.getResponseBody().write(new byte[]{1,2,3});e.close();});
        server.createContext("/redirect",e->{e.getResponseHeaders().set("Location","http://user@localhost:"+server.getAddress().getPort()+"/forbidden");e.sendResponseHeaders(302,-1);e.close();});
        server.createContext("/forbidden",e->{reached.incrementAndGet();e.sendResponseHeaders(200,-1);e.close();});server.start();
        try(var loader=new ImageLoader(new ImageBudget(1000),Duration.ofMillis(500))) {
            for(String path:List.of("/large","/declared","/false","/redirect"))
                assertThrows(ExecutionException.class,()->loader.load(URI.create("http://localhost:"+server.getAddress().getPort()+path)).get(3,TimeUnit.SECONDS));
            assertEquals(0,reached.get());
        }finally{server.stop(0);}
    }
    @Test void highDepthPngNeverAllocatesMoreThanReservedPixelStorage() throws Exception {
        var source=new BufferedImage(2,2,BufferedImage.TYPE_USHORT_GRAY);var bytes=new ByteArrayOutputStream();ImageIO.write(source,"png",bytes);
        var doc=new Doc();doc.supply(bytes.toByteArray());
        try(var loader=new ImageLoader(new ImageBudget(16));var loaded=loader.load(doc).get(5,TimeUnit.SECONDS)) {
            assertTrue(loaded.image().getColorModel().getPixelSize()<=32);
        }
        var colors=new java.awt.image.ComponentColorModel(java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_sRGB),true,false,java.awt.Transparency.TRANSLUCENT,java.awt.image.DataBuffer.TYPE_USHORT);
        var raster=java.awt.image.Raster.createInterleavedRaster(java.awt.image.DataBuffer.TYPE_USHORT,2,2,4,null);
        bytes.reset();ImageIO.write(new BufferedImage(colors,raster,false,null),"png",bytes);var rgba=new Doc();rgba.supply(bytes.toByteArray());
        try(var loader=new ImageLoader(new ImageBudget(16));var loaded=loader.load(rgba).get(5,TimeUnit.SECONDS)) {
            assertTrue(loaded.image().getColorModel().getPixelSize()<=32);
        }
    }
    @Test void deadlineIncludesStalledBody() throws Exception {
        var release=new CountDownLatch(1);var server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.createContext("/slow",e->{try{e.sendResponseHeaders(200,0);e.getResponseBody().write(1);e.getResponseBody().flush();release.await();}catch(Exception ignored){}finally{e.close();}});server.start();
        try(var loader=new ImageLoader(new ImageBudget(1000),Duration.ofMillis(150))) {
            assertThrows(ExecutionException.class,()->loader.load(URI.create("http://localhost:"+server.getAddress().getPort()+"/slow")).get(3,TimeUnit.SECONDS));
        }finally{release.countDown();server.stop(0);}
    }
}
