package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.ui.swing.KitView;
import java.awt.Component;
import java.nio.file.Path;
import javax.swing.AbstractButton;
import static dev.jasper.app.plugins.AppContractTest.*;

/** Test-only owner-package bridge to a real, isolated staged plugin and its mounted view. */
public final class KitViewTestAccess implements AutoCloseable {
    private final WorkspaceAppearanceFixture appearance;
    private final StagedVaultHost vault;
    private final AuxiliarySurface surface;
    private PluginClassLoader remoteLoader;
    private dev.jasper.sdk.plugin.Plugin remote;
    private final String surfaceId;
    StagedVaultHost vault() { return vault; }
    public AuxiliarySurface surface() { return surface; }

    public KitViewTestAccess(Path home, String id, ResolvedTheme theme) throws Exception {
        surfaceId = id;
        appearance = onEdtValue(() -> new WorkspaceAppearanceFixture(theme));
        vault = new StagedVaultHost(home, theme.chrome().dark(), appearance);
        try {
            if (id.equals("fileserver-add")) {
                vault.createVault("synthetic-preview-password");
                onEdt(() -> vault.surface("Credential Vault").close());
                var staged=Path.of(System.getProperty("jasper.stagedPlugins")).resolve("dev.jasper.fileserver");
                remoteLoader=new PluginClassLoader("dev.jasper.fileserver",ManagedVaultLoadingTest.jars(staged),getClass().getClassLoader(),java.util.Map.of("dev.jasper.vault.api",vault.loader));
                var pluginClass=remoteLoader.loadClass("dev.jasper.fileserver.FileServerPlugin");
                var environmentClass=remoteLoader.loadClass("dev.jasper.fileserver.FileServerPlugin$Environment");
                var environment=environmentClass.getDeclaredConstructor(java.util.concurrent.Executor.class,java.util.function.Supplier.class,java.util.function.IntPredicate.class,
                    java.util.function.Consumer.class,java.util.function.Consumer.class,Path.class,String.class,java.time.ZoneId.class,java.util.function.BiFunction.class);environment.setAccessible(true);
                Object settings=environment.newInstance((java.util.concurrent.Executor)javax.swing.SwingUtilities::invokeLater,(java.util.function.Supplier<java.util.List<?>>)java.util.List::of,
                    (java.util.function.IntPredicate)port->true,(java.util.function.Consumer<String>)text->{},(java.util.function.Consumer<Path>)path->{},home,"Mac OS X",java.time.ZoneOffset.UTC,
                    (java.util.function.BiFunction<java.time.Duration,Runnable,Runnable>)(delay,task)->()->{});
                var constructor=pluginClass.getDeclaredConstructor(environmentClass);constructor.setAccessible(true);
                remote=(dev.jasper.sdk.plugin.Plugin)constructor.newInstance(settings);
                onEdt(()->vault.host.start(new HostedPlugin(FileServerRenderEvidenceTest.INFO,java.util.Set.of(),java.util.Set.of("dev.jasper.vault"),java.util.Set.of(),remoteLoader,()->remote)));
                StagedVaultHost.settle();vault.invoke("dev.jasper.fileserver.new");surface=vault.awaitSurface("New File Server");return;
            }
            if (id.equals("host-add")) {
                vault.createVault("synthetic-preview-password");
                onEdt(() -> vault.surface("Credential Vault").close());
                var ssh=java.nio.file.Files.createDirectories(home.resolve("synthetic-ssh"));
                var staged=Path.of(System.getProperty("jasper.stagedPlugins")).resolve("dev.jasper.remote");
                remoteLoader=new PluginClassLoader("dev.jasper.remote",ManagedVaultLoadingTest.jars(staged),getClass().getClassLoader(),java.util.Map.of("dev.jasper.vault.api",vault.loader));
                var constructor=remoteLoader.loadClass("dev.jasper.remote.RemotePlugin").getDeclaredConstructor(java.util.concurrent.Executor.class,java.util.function.Function.class,java.util.function.BiFunction.class,Path.class);
                constructor.setAccessible(true);
                remote=(dev.jasper.sdk.plugin.Plugin)constructor.newInstance((java.util.concurrent.Executor)javax.swing.SwingUtilities::invokeLater,
                    (java.util.function.Function<Object,java.util.Optional<Object>>)c->java.util.Optional.empty(),
                    (java.util.function.BiFunction<java.time.Duration,Runnable,Runnable>)(delay,task)->()->{},ssh);
                onEdt(()->vault.host.start(new HostedPlugin(StagedRemoteHost.REMOTE,java.util.Set.of(),java.util.Set.of("dev.jasper.vault","dev.jasper.editor"),java.util.Set.of("dev.jasper.remote.api"),remoteLoader,()->remote)));
                StagedVaultHost.settle();
                onEdt(()->{
                    try {
                        var contextField=remote.getClass().getDeclaredField("context");contextField.setAccessible(true);
                        var context=(dev.jasper.sdk.plugin.PluginContext)contextField.get(remote);
                        var edit=remote.getClass().getDeclaredMethod("editHost",dev.jasper.sdk.terminal.WindowHandle.class,java.util.Optional.class);edit.setAccessible(true);
                        edit.invoke(remote,context.terminals().window(vault.window).orElseThrow(),java.util.Optional.empty());
                    }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
                });
                surface=vault.awaitSurface("Add Host");
                return;
            }
            if (java.util.Set.of("vault-manager", "vault-login", "vault-key").contains(id)) {
                vault.createVault("synthetic-preview-password");
                var manager = vault.awaitSurface("Credential Vault");
                if (id.equals("vault-manager")) { surface = manager; return; }
                onEdt(() -> {
                    var menu = ((javax.swing.JComponent)StagedVaultHost.named(manager.titleActions(), "add")).getComponentPopupMenu();
                    String label = id.equals("vault-login") ? "Login" : "SSH Key";
                    java.util.Arrays.stream(menu.getComponents()).filter(c -> c instanceof AbstractButton b && b.getText().equals(label))
                        .map(AbstractButton.class::cast).findFirst().orElseThrow().doClick(0);
                });
                surface = vault.awaitSurface(id.equals("vault-login") ? "Add Login" : "Add SSH Key");return;
            }
            if (id.equals("vault-unlock")) {
                vault.createVault("synthetic-preview-password");
                onEdt(() -> vault.surface("Credential Vault").requestClose());
                vault.invoke("dev.jasper.vault.lock");
            } else if (!id.equals("vault-create")) throw new IllegalArgumentException("Unknown surface: " + id);
            vault.invoke("dev.jasper.vault.open");
            surface = vault.awaitSurface(id.equals("vault-create") ? "Create Vault" : "Unlock Vault");
        } catch (Exception | Error failure) { vault.close(); throw failure; }
    }

    dev.jasper.sdk.plugin.PluginContext remoteContext() {
        try {var field=remote.getClass().getDeclaredField("context");field.setAccessible(true);return (dev.jasper.sdk.plugin.PluginContext)field.get(remote);}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    java.util.List<String> importManagedKeys(Path directory) throws Exception {
        Class<?> algorithm=vault.loader.loadClass("dev.jasper.vault.keygen.KeyAlgorithm");
        Object ed=algorithm.getField("ED25519").get(null);
        Class<?> generator=vault.loader.loadClass("dev.jasper.vault.keygen.KeyGenerator");
        Object producer=generator.getConstructor(Path.class).newInstance(directory);
        Class<?> sourceType=vault.loader.loadClass("dev.jasper.vault.api.SshKeySource");
        var sources=new java.util.ArrayList<Object>();
        for(String name:java.util.List.of("id_ed25519","work_ed25519")) {
            Object key=generator.getMethod("generate",algorithm,String.class,String.class,char[].class).invoke(producer,ed,name,"synthetic fixture",new char[0]);
            Path source=(Path)key.getClass().getMethod("privatePath").invoke(key);
            sources.add(sourceType.getConstructor(java.util.UUID.class,String.class,Path.class).newInstance(java.util.UUID.randomUUID(),name,source));
        }
        var pending=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<?>>();
        Class<?> api=vault.loader.loadClass("dev.jasper.vault.api.VaultApi");
        onEdt(()->{try{pending.set((java.util.concurrent.CompletableFuture<?>)api.getMethod("importSshKeys",dev.jasper.sdk.terminal.WindowHandle.class,java.util.List.class,java.util.List.class)
            .invoke(remoteContext().services().require(api),remoteContext().terminals().window(vault.window).orElseThrow(),sources,java.util.List.of()));}
            catch(ReflectiveOperationException e){throw new AssertionError(e);}});
        var review=vault.awaitSurface("Import SSH keys");
        StagedVaultHost.await(()->StagedVaultHost.node(review,"commit").state().enabled(),"managed keys ready for real review");
        onEdt(()->StagedVaultHost.click(review,"commit"));
        StagedVaultHost.await(()->pending.get().isDone(),"managed keys committed");
        Object result=((java.util.Optional<?>)pending.get().join()).orElseThrow();
        var keys=(java.util.Map<?,?>)result.getClass().getMethod("keys").invoke(result);
        var ids=new java.util.ArrayList<String>();
        for(Object descriptor:keys.values())ids.add(descriptor.getClass().getMethod("id").invoke(descriptor).toString());
        return ids;
    }

    public KitView view() { return onEdtValue(() -> StagedVaultHost.kitView(surface)); }
    public Component component(String id) { return onEdtValue(() -> StagedVaultHost.require(surface, id)); }
    public void type(String id, String text) { onEdt(() -> StagedVaultHost.type(surface, id + ".input", text)); }
    public void click(String id) { onEdt(() -> StagedVaultHost.click(surface, id)); }
    public void theme(ResolvedTheme theme) { onEdt(() -> appearance.setPair(theme)); }
    public void error() throws InterruptedException {
        if (surfaceId.equals("host-add") || surfaceId.equals("fileserver-add")) {
            try {
                var storeField=remote.getClass().getDeclaredField("store");storeField.setAccessible(true);var store=storeField.get(remote);
                Path file=(Path)store.getClass().getMethod("file").invoke(store);java.nio.file.Files.createDirectories(file);
            } catch(ReflectiveOperationException | java.io.IOException failure){throw new AssertionError(failure);}
            String prefix=surfaceId.equals("host-add")?"host":"server";
            click(prefix+".save");StagedVaultHost.await(()->StagedVaultHost.named(surface.holder(),prefix+".message")!=null,"persistence error");return;
        }
        type("password", "synthetic-wrong-password"); click("submit");
        StagedVaultHost.await(() -> StagedVaultHost.node(surface, "message").state().visible()
            && ((AbstractButton) StagedVaultHost.require(surface, "password.reveal")).isEnabled(), "unlock error");
    }
    @Override public void close() throws Exception { try { vault.close(); } finally { if(remoteLoader!=null)remoteLoader.close(); } }
}
