package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.contributions.PanelHeaderState;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.swing.PanelHostHeader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import javax.swing.*;
import static dev.jasper.app.plugins.AppContractTest.*;

/** Actual Remote factory/host adapter and an actual locked synthetic Vault; no session or connection injection. */
public final class SessionsSurfaceFixture implements AutoCloseable {
    public static final UUID SELECTED = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private final StagedVaultHost vault;
    private final WorkspaceAppearanceFixture appearance;
    private final PluginClassLoader loader;
    private final Plugin remote;
    private final PanelSite site;
    private final KitView view;
    private final List<Object> hosts = new ArrayList<>();
    private final Object store;
    private final Class<?> hostType;
    private PanelHeaderState header = PanelHeaderState.EMPTY;

    public SessionsSurfaceFixture(Path home, ResolvedTheme theme) throws Exception {
        appearance = onEdtValue(() -> new WorkspaceAppearanceFixture(theme));
        vault = new StagedVaultHost(home, theme.chrome().dark(), appearance);
        vault.createVault("synthetic-sessions-password");
        vault.requestCloseManager();
        vault.invoke("dev.jasper.vault.lock");
        var ssh = Files.createDirectories(home.resolve("synthetic-ssh"));
        var staged = Path.of(System.getProperty("jasper.stagedPlugins")).resolve("dev.jasper.remote");
        loader = new PluginClassLoader("dev.jasper.remote", ManagedVaultLoadingTest.jars(staged), getClass().getClassLoader(), Map.of("dev.jasper.vault.api", vault.loader));
        var constructor = loader.loadClass("dev.jasper.remote.RemotePlugin").getDeclaredConstructor(java.util.concurrent.Executor.class, Function.class, BiFunction.class, Path.class);
        constructor.setAccessible(true);
        remote = (Plugin)constructor.newInstance((java.util.concurrent.Executor)SwingUtilities::invokeLater,
            (Function<Object,Optional<Object>>)c -> Optional.empty(), (BiFunction<java.time.Duration,Runnable,Runnable>)(delay, task) -> () -> {}, ssh);
        onEdt(() -> vault.host.start(new HostedPlugin(StagedRemoteHost.REMOTE, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), Set.of("dev.jasper.remote.api"), loader, () -> remote)));
        StagedVaultHost.settle();
        store = field(remote, "store");
        hostType = loader.loadClass("dev.jasper.remote.hosts.RemoteHost");
        Class<?> auth = loader.loadClass("dev.jasper.remote.hosts.Auth");
        Object credential = loader.loadClass("dev.jasper.remote.hosts.Auth$Vault").getConstructor(UUID.class)
            .newInstance(UUID.fromString("00000000-0000-4000-8000-000000000099"));
        // The screenshot's example identities are synthetic presentation data; no operation connects to them.
        String[][] values = {{"amp","100.110.65.45"}, {"amp-amp","100.110.65.45"}, {"dustin-vm","100.83.72.100"},
            {"minecraft-local","192.168.1.124"}, {"pve","100.73.226.91"}, {"pve-root","100.73.226.91"}};
        for (int i = 0; i < values.length; i++) hosts.add(hostType.getConstructor(UUID.class,String.class,String.class,int.class,String.class,auth,String.class,boolean.class,Optional.class,Instant.class,Instant.class)
            .newInstance(new UUID(0x4000L, 0x8000000000000001L + i),values[i][0],values[i][1],22,"ubuntu",credential,"",false,Optional.empty(),Instant.EPOCH,Instant.EPOCH));
        site = onEdtValue(() -> new PanelSite(vault.window, () -> {}, () -> {}, () -> true, state -> {
            header = state;
            var panels = field(remote, "panels");
            Object panel = ((Map<?,?>)panels).get(vault.window);
            if (panel != null) hostHeader(((HostedKitView)invoke(panel, "view")).component());
        }));
        view = onEdtValue(() -> (KitView)vault.contributions.panels().stream().filter(p -> p.id().equals(StagedRemoteHost.PANEL)).findFirst().orElseThrow().factory().apply(site));
        onEdt(() -> {
            zone(theme);
            hostHeader(view);
            view.setPanelHostActions("sessions", List.of(
                new Nodes.Button("host.options",ControlState.READY,"","more",Nodes.ButtonKind.GHOST,"host.options","Options"),
                new Nodes.Button("host.hide",ControlState.READY,"","collapse",Nodes.ButtonKind.GHOST,"host.hide","Hide")),e -> {});
        });
    }
    private void hostHeader(KitView target) {
        target.setPanelHostHeader("sessions", new PanelHostHeader(header.detail(), header.active(), header.actions().stream()
            .map(action -> new PanelHostHeader.Action(action.label(), action.icon(), action.invoke(), action.icon() instanceof dev.jasper.app.platform.PresentationIcon named
                ? new PanelHostHeader.Artwork(named.name(), named.pathData()) : null)).toList()));
    }
    public KitView view() { return view; }
    public void populate() throws Exception {
        for (Object host : hosts) ((CompletableFuture<?>)store.getClass().getMethod("put",hostType).invoke(store,host)).join();
        StagedVaultHost.settle();
        onEdt(() -> { site.notifyVisibility(true); select(SELECTED); });
    }
    public void select(UUID id) { invoke(panel(), "select", new Class<?>[]{UUID.class}, id); }
    public void filter(String text) { onEdt(() -> ((javax.swing.text.JTextComponent)component("sessions.search.input")).setText(text)); }
    public void loadError() { onEdt(() -> invoke(panel(),"setHosts",new Class<?>[]{List.class,Optional.class},hosts,Optional.of("Synthetic host configuration error"))); }
    public void theme(ResolvedTheme value) { onEdt(() -> {zone(value);appearance.setPair(value);}); }
    private void zone(ResolvedTheme value) {view.putClientProperty("Jasper.zone","workspace");view.putClientProperty("Jasper.workspaceTheme",value.workspace());}
    public Component component(String id) { return onEdtValue(() -> {var found=StagedVaultHost.named(view,id);if(found==null)throw new AssertionError("Missing "+id);return found;}); }
    public void size(int width,int height) { onEdt(() -> {view.setSize(width,height); for(int i=0;i<3;i++) layout(view);}); }
    public BufferedImage capture(int scale) {
        return onEdtValue(() -> {
            var image = new BufferedImage(view.getWidth()*scale,view.getHeight()*scale,BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();try{graphics.scale(scale,scale);view.printAll(graphics);}finally{graphics.dispose();}return image;
        });
    }
    public Object panel() { return ((Map<?,?>)field(remote,"panels")).get(vault.window); }
    public static void layout(Container parent) { parent.doLayout();for(var child:parent.getComponents())if(child instanceof Container nested)layout(nested); }
    private static Object field(Object object,String name) {try{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Object invoke(Object object,String name) {return invoke(object,name,new Class<?>[0]);}
    private static Object invoke(Object object,String name,Class<?>[] types,Object... args) {try{return object.getClass().getMethod(name,types).invoke(object,args);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    @Override public void close() throws Exception {try{onEdt(site::notifyClosed);}finally{try{vault.close();}finally{loader.close();}}}
}
