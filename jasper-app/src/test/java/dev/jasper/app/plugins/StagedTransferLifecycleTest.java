package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.*;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.*;

/** Real staged classloader, SQLite native library, and HostedContext teardown at active I/O boundaries. */
class StagedTransferLifecycleTest {
    @TempDir Path root;
    @ParameterizedTest @ValueSource(strings={"read","checkpoint","published"})
    void hostShutdownDrainsOwnedLoopsAndReleasesDurableQueue(String boundary) throws Exception {
        root=root.toRealPath();Path source=Files.write(root.resolve("source"),new byte[9*1024*1024]),destination=Files.createDirectory(root.resolve("destination"));
        Path staged=Path.of(System.getProperty("jasper.stagedPlugins"));
        List<Path> jars;try(var files=Files.list(staged.resolve("dev.jasper.remote"))) { jars=files.filter(p->p.toString().endsWith(".jar")).toList(); }
        try(var loader=new PluginClassLoader("dev.jasper.remote",jars,getClass().getClassLoader(),Map.of())) {
            Class<?> endpointType=loader.loadClass("dev.jasper.remote.sftp.FileEndpoint"),localType=loader.loadClass("dev.jasper.remote.sftp.LocalEndpoint"),coordinatorType=loader.loadClass("dev.jasper.remote.transfer.TransferCoordinator"),refType=loader.loadClass("dev.jasper.remote.transfer.EndpointRef"),requestType=loader.loadClass("dev.jasper.remote.transfer.TransferRequest");
            var host=AppContractTest.host(root.resolve("home"),Map.of(),Duration.ofSeconds(3));var coordinator=new AtomicReference<Object>();var database=new AtomicReference<Path>();
            var blocked=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean();
            BiFunction<Object,WindowHandle,CompletableFuture<Object>> opener=(ref,owner)-> {
                try {
                    Object local=localType.getConstructor().newInstance();
                    Object proxy=Proxy.newProxyInstance(loader,new Class<?>[]{endpointType},(ignored,method,args)-> {
                        if(method.getName().equals("abort") || method.getName().equals("close")) release.countDown();
                        try {
                            Object value=method.invoke(local,args);
                            if(method.getName().equals("publish") && boundary.equals("published") && once.compareAndSet(false,true)) { blocked.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IOException("Fixture timed out"); }
                            if(method.getName().equals("read") && args[0].equals(source.toString()) && (long)args[1]==0) {
                                return new FilterInputStream((InputStream)value) {
                                    long bytes;
                                    @Override public int read(byte[] b,int off,int length) throws IOException {
                                        if(!boundary.equals("published") && (boundary.equals("read") || bytes>=8L*1024*1024) && once.compareAndSet(false,true)) {
                                            blocked.countDown();try { if(!release.await(5,TimeUnit.SECONDS))throw new IOException("Fixture timed out"); }catch(InterruptedException stop) { Thread.currentThread().interrupt();throw new IOException(stop); }
                                        }
                                        int count=super.read(b,off,length);if(count>0)bytes+=count;return count;
                                    }
                                };
                            }
                            return value;
                        }catch(InvocationTargetException error){throw error.getCause();}
                    });return CompletableFuture.completedFuture(proxy);
                } catch(Exception error) { return CompletableFuture.failedFuture(error); }
            };
            Plugin plugin=new Plugin() {
                @Override public void start(PluginContext context) throws Exception {
                    database.set(context.dataDirectory().resolve("transfers"));
                    coordinator.set(coordinatorType.getConstructor(Path.class,Executor.class,Executor.class,BiFunction.class,IntSupplier.class).newInstance(database.get(),context.background(),(Executor)SwingUtilities::invokeLater,opener,(IntSupplier)()->2));
                }
                @Override public void stop() { try {coordinatorType.getMethod("close").invoke(coordinator.get());}catch(Exception failure){throw new AssertionError(failure);} }
            };
            onEdt(()->assertThat(host.start(new HostedPlugin(new PluginInfo("dev.jasper.remote","Remote","0.3.0",Set.of()),Set.of(),Set.of(),Set.of(),loader,()->plugin)).state()).isEqualTo(PluginStatus.State.ACTIVE));
            try {
                Object local=refType.getMethod("local").invoke(null),request=requestType.getConstructor(refType,List.class,refType,String.class).newInstance(local,List.of(source.toString()),local,destination.toString());
                var enqueued=(CompletableFuture<?>)coordinatorType.getMethod("enqueue",requestType,WindowHandle.class).invoke(coordinator.get(),request,null);UUID id=(UUID)enqueued.get(5,TimeUnit.SECONDS);
                assertThat(blocked.await(10,TimeUnit.SECONDS)).as(boundary).isTrue();
                var pending=new AtomicReference<List<CompletableFuture<?>>>();onEdt(()->pending.set(host.stop()));
                CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5,TimeUnit.SECONDS);
                ((CompletableFuture<?>)coordinatorType.getMethod("stopped").invoke(coordinator.get())).get(5,TimeUnit.SECONDS);
                assertThat(host.failures("dev.jasper.remote")).isZero();
                Class<?> storeType=loader.loadClass("dev.jasper.remote.transfer.store.TransferStore");
                try(var store=(AutoCloseable)storeType.getConstructor(Path.class).newInstance(database.get())) {
                    Object job=storeType.getMethod("job",UUID.class).invoke(store,id);
                    assertThat(job.getClass().getMethod("state").invoke(job).toString()).isEqualTo("PAUSED");
                    if(boundary.equals("checkpoint"))assertThat((long)job.getClass().getMethod("confirmedBytes").invoke(job)).isEqualTo(8L*1024*1024);
                }
                if(boundary.equals("published"))assertThat(Files.size(destination.resolve("source"))).isEqualTo(Files.size(source));
                assertThat(loader.loadClass("org.sqlite.JDBC").getClassLoader()).isSameAs(loader);
            } finally { release.countDown();onEdt(host::stop); }
        }
    }
}
