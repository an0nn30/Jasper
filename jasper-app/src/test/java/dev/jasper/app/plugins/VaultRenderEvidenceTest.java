package dev.jasper.app.plugins;

import com.formdev.flatlaf.util.UIScale;

import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static dev.jasper.app.plugins.StagedVaultHost.click;
import static dev.jasper.app.plugins.StagedVaultHost.type;
import static org.assertj.core.api.Assertions.*;

/**
 * Real-host, dark and light pixel evidence of the migrated Vault (Plan 2 Task 7, controller ruling 2): the staged
 * plugin runs in the application's plugin host ({@link StagedVaultHost}, unbound by {@link UnboundVault}) and its kit
 * views render through the host translation. Output goes to build/theme-migration-evidence/vault-hosted; the frozen
 * before images are untouched. Passwords are synthetic and no clipboard is touched.
 */
class VaultRenderEvidenceTest {
    static final String FAMILY = "vault-hosted";
    @TempDir Path root;

    @Test void capturesTheStagedVaultInTheRealHostDarkAndLight() throws Exception {
        for (String theme : List.of("dark", "light")) capture(theme, root.resolve(theme));
    }

    @Test void actualCompanionFormsRenderInDarkLightCustomAndEnlargedThemes() throws Exception {
        for (String theme : List.of("dark", "light", "custom", "enlarged")) {
            var controller=onEdtValue(dev.jasper.app.appearance.ThemeController::new);
            try {
                var selected=theme.equals("light")?dev.jasper.app.appearance.Theme.JASPER_LIGHT:dev.jasper.app.appearance.Theme.DARK;
                if(theme.equals("custom")) {
                    Path file=Path.of("src/test/resources/dev/jasper/app/appearance/custom-hero.toml");
                    selected=dev.jasper.app.appearance.Theme.of("companion-custom",new dev.jasper.app.appearance.Theme.UserFile(file,dev.jasper.ui.themeio.ThemeToml.parse(Files.readString(file),file.toString())));
                }
                var installed=selected;
                onEdt(()->controller.configure(installed,theme.equals("enlarged")?new dev.jasper.app.config.UiFontConfig("system",20f):dev.jasper.app.config.UiFontConfig.defaults()));
                var appearance=onEdtValue(()->new WorkspaceAppearanceFixture(controller.current()));
                try(var vault=new StagedVaultHost(root.resolve("companions-"+theme),!theme.equals("light"),appearance)) {
                    vault.createVault("synthetic-companion-password");
                    vault.seedReferenceItems(root.resolve("keys-"+theme),form->natural(theme,"import-ready",form));
                    var manager=vault.surface("Credential Vault");
                    onEdt(()->menu(manager.titleActions(),"add","Secure Note"));
                    var note=vault.awaitSurface("Add Secure Note");natural(theme,"note",note);onEdt(note::requestClose);
                    onEdt(()->click(manager,"delete"));var delete=vault.awaitSurface("Delete entry");natural(theme,"delete",delete);onEdt(delete::requestClose);
                    vault.invoke("dev.jasper.vault.change_password");
                    var password=vault.awaitSurface("Change Master Password");natural(theme,"change-password",password);onEdt(password::requestClose);
                    vault.invoke("dev.jasper.vault.generate_key");var generator=vault.awaitSurface("Generate SSH Key");natural(theme,"generator",generator);onEdt(generator::requestClose);
                    Class<?> apiType=vault.loader.loadClass("dev.jasper.vault.api.VaultApi");
                    var api=new java.util.concurrent.atomic.AtomicReference<Object>();var context=new java.util.concurrent.atomic.AtomicReference<dev.jasper.sdk.plugin.PluginContext>();
                    vault.start(new dev.jasper.sdk.PluginInfo("test.companion","Companion", "0.1.0",java.util.Set.of()),java.util.Set.of(UnboundVault.ID),started->{context.set(started);api.set(started.services().require(apiType));});
                    var owner=onEdtValue(()->context.get().terminals().window(vault.window).orElseThrow());
                    var credentials=(java.util.List<?>)onEdtValue(()->reflect(apiType,api.get(),"credentials",new Class<?>[0]));
                    Object managed=credentials.stream().filter(c->reflect(c.getClass(),c,"name",new Class<?>[0]).equals("work_rsa")).findFirst().orElseThrow();
                    var managedId=(java.util.UUID)reflect(managed.getClass(),managed,"id",new Class<?>[0]);
                    onEdt(()->{
                        try {var field=vault.plugin.getClass().getDeclaredField("managerWindow");field.setAccessible(true);Object window=field.get(vault.plugin);reflect(window.getClass(),window,"show",new Class<?>[]{dev.jasper.sdk.terminal.WindowHandle.class,java.util.Optional.class},owner,java.util.Optional.of(managedId));}
                        catch(ReflectiveOperationException e){throw new AssertionError(e);}
                        click(manager,"edit");
                    });
                    var rename=vault.awaitSurface("Edit stored SSH key");natural(theme,"rename-managed",rename);onEdt(rename::requestClose);
                    var pick=onEdtValue(()->(java.util.concurrent.CompletableFuture<?>)reflect(apiType,api.get(),"pick",new Class<?>[]{dev.jasper.sdk.terminal.WindowHandle.class},owner));
                    var picker=vault.awaitSurface("Choose a credential");natural(theme,"legacy-picker",picker);onEdt(picker::requestClose);assertThat(pick).isCompleted();
                    Object login=credentials.stream().filter(c->reflect(c.getClass(),c,"name",new Class<?>[0]).equals("amp")).findFirst().orElseThrow();
                    var id=(java.util.UUID)reflect(login.getClass(),login,"id",new Class<?>[0]);
                    var access=onEdtValue(()->(java.util.concurrent.CompletableFuture<?>)reflect(apiType,api.get(),"credential",new Class<?>[]{dev.jasper.sdk.terminal.WindowHandle.class,java.util.UUID.class},owner,id));
                    var grant=vault.awaitSurface("Allow Companion to use amp?");natural(theme,"grant",grant);onEdt(grant::requestClose);assertThat(access).isCompleted();
                    onEdt(()->type(manager,"search.input","no actual matches"));natural(theme,"filter-empty",manager);
                    vault.invoke("dev.jasper.vault.lock");onEdt(()->click(manager,"unlock"));natural(theme,"inline-unlock",manager);
                    assertThat(vault.problems).isEmpty();
                }
            } finally {onEdt(()->{controller.configure(dev.jasper.app.appearance.Theme.DARK,dev.jasper.app.config.UiFontConfig.defaults());controller.close();});}
        }
    }
    private static Object reflect(Class<?> type,Object target,String name,Class<?>[] parameters,Object... arguments) {
        try {return type.getMethod(name,parameters).invoke(target,arguments);}catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void menu(java.awt.Container root,String id,String label) {
        var popup=((JComponent)StagedVaultHost.named(root,id)).getComponentPopupMenu();
        java.util.Arrays.stream(popup.getComponents()).filter(c->c instanceof javax.swing.AbstractButton b&&b.getText().equals(label)).map(javax.swing.AbstractButton.class::cast).findFirst().orElseThrow().doClick(0);
    }
    private static void natural(String theme,String state,AuxiliarySurface surface) {
        onEdt(()->{
            var holder=surface.holder();var size=holder.getPreferredSize();
            boolean manager=surface.title().equals("Credential Vault");int width=manager?900:Math.max(1,size.width),height=manager?555:Math.max(1,size.height);
            ThemeMigrationCapture.save("vault-companions",theme,state,holder,width,height);
        });
    }

    private void capture(String theme, Path data) throws Exception {
        var controller = onEdtValue(dev.jasper.app.appearance.ThemeController::new);
        onEdt(() -> controller.configure(theme.equals("dark") ? dev.jasper.app.appearance.Theme.DARK : dev.jasper.app.appearance.Theme.JASPER_LIGHT));
        try (var vault = new StagedVaultHost(data, theme.equals("dark"))) {
            vault.invoke("dev.jasper.vault.open");
            var create = vault.surface("Create Vault");
            onEdt(() -> UnboundVault.assertUnbound(StagedVaultHost.kitView(create)));
            save(theme, "create", create, 580, 380);
            onEdt(() -> click(create, "submit"));
            save(theme, "create-invalid", create, 580, 380);
            onEdt(() -> {
                type(create, "password.input", "synthetic-host-pass"); type(create, "confirm.input", "synthetic-host-pass");
                UnboundVault.assertUnbound(StagedVaultHost.kitView(create));
                click(create, "submit");
            });
            var manager = vault.awaitSurface("Credential Vault");
            save(theme, "manager-empty", manager, 900, 580);
            vault.invoke("dev.jasper.vault.generate_key");
            var generator = vault.surface("Generate SSH Key");
            save(theme, "generate-key", generator, 640, 560);
            onEdt(() -> generator.requestClose());
            vault.invoke("dev.jasper.vault.lock");
            save(theme, "manager-locked", manager, 900, 580);
            onEdt(() -> click(manager, "unlock"));
            save(theme, "manager-unlock", manager, 900, 580);
            onEdt(() -> manager.requestClose());
            vault.invoke("dev.jasper.vault.open");
            var unlock = vault.surface("Unlock Vault");
            save(theme, "unlock", unlock, 580, 300);
            onEdt(() -> unlock.requestClose());
            assertThat(vault.problems).isEmpty();
        } finally {
            onEdt(() -> { controller.close(); new dev.jasper.app.appearance.ThemeController(); });
        }
    }

    static void save(String theme, String state, AuxiliarySurface surface, int width, int height) throws Exception {
        onEdt(() -> {
            JComponent holder = surface.holder();
            ThemeMigrationCapture.save(FAMILY, theme, state, holder, UIScale.scale(width), UIScale.scale(height));
        });
        assertThat(Files.size(Path.of("build/theme-migration-evidence", FAMILY, theme, state + ".png"))).isPositive();
    }
}
