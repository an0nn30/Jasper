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

    public KitViewTestAccess(Path home, String id, ResolvedTheme theme) throws Exception {
        appearance = onEdtValue(() -> new WorkspaceAppearanceFixture(theme));
        vault = new StagedVaultHost(home, theme.chrome().dark(), appearance);
        try {
            if (id.equals("vault-unlock")) {
                vault.createVault("synthetic-preview-password");
                onEdt(() -> vault.surface("Credential Vault").requestClose());
                vault.invoke("dev.jasper.vault.lock");
            } else if (!id.equals("vault-create")) throw new IllegalArgumentException("Unknown surface: " + id);
            vault.invoke("dev.jasper.vault.open");
            surface = vault.awaitSurface(id.equals("vault-create") ? "Create Vault" : "Unlock Vault");
        } catch (Exception | Error failure) { vault.close(); throw failure; }
    }

    public KitView view() { return onEdtValue(() -> StagedVaultHost.kitView(surface)); }
    public Component component(String id) { return onEdtValue(() -> StagedVaultHost.require(surface, id)); }
    public void type(String id, String text) { onEdt(() -> StagedVaultHost.type(surface, id + ".input", text)); }
    public void click(String id) { onEdt(() -> StagedVaultHost.click(surface, id)); }
    public void theme(ResolvedTheme theme) { onEdt(() -> appearance.setPair(theme)); }
    public void error() throws InterruptedException {
        type("password", "synthetic-wrong-password"); click("submit");
        StagedVaultHost.await(() -> StagedVaultHost.node(surface, "message").state().visible()
            && ((AbstractButton) StagedVaultHost.require(surface, "password.reveal")).isEnabled(), "unlock error");
    }
    @Override public void close() throws Exception { vault.close(); }
}
