package dev.jasper.app.plugins;

import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.ui.swing.KitContext;
import java.util.List;
import java.util.function.Supplier;

/** Synthetic status inputs through the real runtime stub filter and real Settings owner. */
public final class SettingsStubTestAccess {
    private SettingsStubTestAccess() {}
    public static List<SettingsPage> pages(Supplier<KitContext> kit) {
        var statuses=List.of(
            status("consent",PluginStatus.State.NEEDS_CONSENT,"not reviewed yet"),
            status("skipped",PluginStatus.State.SKIPPED,"requires unavailable dependency"),
            status("failed",PluginStatus.State.FAILED,"synthetic startup failure"),
            status("safe",PluginStatus.State.DISABLED,"safe mode"),
            status("disabled",PluginStatus.State.DISABLED,"disabled by the user"),
            status("active",PluginStatus.State.ACTIVE,""),
            status("old",PluginStatus.State.SKIPPED,"superseded by version2"));
        var pages=PluginRuntime.withStubs(List.of(),statuses,status->new PluginStubPage(status.id(),status.name(),status.reason(),()->{},kit));
        if(pages.size()!=4)throw new AssertionError("Expected only4 applicable conditional stubs");
        return pages;
    }
    private static PluginStatus status(String name,PluginStatus.State state,String reason) {
        return new PluginStatus("fixture."+name,name,"1.0.0",PluginCandidate.Origin.USER,state,reason);
    }
}
