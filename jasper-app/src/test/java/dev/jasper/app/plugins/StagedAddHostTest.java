package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.appearance.SurfaceFixtures;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.swing.KitView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.*;
import static dev.jasper.app.plugins.StagedVaultHost.*;
import static dev.jasper.app.plugins.StagedAnchoredPickerTest.*;
import static org.assertj.core.api.Assertions.*;

/** Production Remote Add Host and Vault producer in the real host, isolated and headless. */
class StagedAddHostTest {
    @TempDir Path root;
    @Test void groupedDefaultPickerUsesRealCreationClearCancelAndPendingAppearance() throws Exception {
        var themes=onEdtValue(() -> ThemeTestSupport.controller(true));
        try(var owner=new KitViewTestAccess(root.resolve("home"),"host-add",SurfaceFixtures.theme("dark"))) {
            var vault=owner.vault();var form=owner.surface();
            onEdt(()->{
                assertThat(((Nodes.Form)kitView(form).spec().root()).submission()).isEqualTo(Nodes.FormSubmission.NONE);
                assertThat(((Nodes.Choice)node(form,"host.choose")).face().orElseThrow().density()).isEqualTo(Nodes.PickerDensity.DEFAULT);
                captureHost("empty",kitView(form),540,732);
                assertThat(((AbstractButton)require(form,"host.save")).isEnabled()).isFalse();
                click(form,"host.choose.input");captureHost("open-empty-face",kitView(form),540,732);
                captureHost("open-empty",menu(vault),0,0);search(menu(vault),"staging");captureHost("no-matches",menu(vault),0,0);
                enter(named(menu(vault),"credential.menu.search"));click(vault.surface("New Credential"),"create.login");
                var editor=vault.surface("Add Login");assertThat(editor.ownerSurface()).contains(form);
                type(editor,"name.input","fixture login");type(editor,"username.input","fixture-with-a-long-synthetic-username");type(editor,"password.input","synthetic-secret");click(editor,"save");
            });
            await(()->menus(vault).size()==1,"Add Host picker returns after genuine Login Save");
            onEdt(()->{search(menu(vault),"");key(menu(vault),"down");enter(named(menu(vault),"credential.menu.search"));});
            await(()->!((Nodes.Choice)node(form,"host.choose")).face().orElseThrow().primary().isBlank(),"chosen Login reaches Add Host");
            onEdt(()->{
                // The real Remote-scoped selection grants exactly the first secret fetch.
                try {
                    Class<?> api=vault.loader.loadClass("dev.jasper.vault.api.VaultApi");Object service=owner.remoteContext().services().require(api);
                    Object descriptor=((java.util.List<?>)api.getMethod("credentials").invoke(service)).getFirst();
                    var id=(java.util.UUID)descriptor.getClass().getMethod("id").invoke(descriptor);
                    var fetch=api.getMethod("credential",java.util.UUID.class);
                    var first=(java.util.concurrent.CompletableFuture<?>)fetch.invoke(service,id);assertThat(first).isCompleted();
                    ((AutoCloseable)((java.util.Optional<?>)first.join()).orElseThrow()).close();
                    var second=(java.util.concurrent.CompletableFuture<?>)fetch.invoke(service,id);assertThat(second).isNotDone();second.cancel(false);
                }catch(Exception failure){throw new AssertionError(failure);}
                type(form,"host.name.input","synthetic-preview-host");type(form,"host.hostname.input","synthetic-preview-host.invalid");
                captureHost("selected",kitView(form),540,732);click(form,"host.choose.input");captureHost("open",menu(vault),0,0);
                search(menu(vault),"fixt");var search=(JTextField)named(menu(vault),"credential.menu.search");search.setCaretPosition(2);var document=search.getDocument();captureHost("filtering",menu(vault),0,0);
                try { owner.theme(SurfaceFixtures.theme("light")); }catch(Exception e){throw new AssertionError(e);}captureHost("filtering-light",menu(vault),0,0);captureHost("selected-light",kitView(form),540,732);
                try { owner.theme(SurfaceFixtures.theme("dark")); }catch(Exception e){throw new AssertionError(e);}
                themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new dev.jasper.app.config.UiFontConfig("system",20));
                captureHost("filtering-enlarged",menu(vault),0,0);captureHost("selected-enlarged",kitView(form),810,1098);
                assertThat(search.getDocument()).isSameAs(document);assertThat(search.getCaretPosition()).isEqualTo(2);
                themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults());
                search(menu(vault),"staging");captureHost("no-matches-selected",menu(vault),0,0);key(menu(vault),"escape");
            });
            settle();onEdt(()->{
                assertThat(((Nodes.Choice)node(form,"host.choose")).face().orElseThrow().primary()).isEqualTo("fixture login");
                click(form,"host.choose.input");search(menu(vault),"");select(menu(vault),0);enter(named(menu(vault),"credential.menu.search"));
            });
            await(()->((Nodes.Choice)node(form,"host.choose")).face().orElseThrow().primary().isBlank(),"explicit clear");
            var keys=owner.importManagedKeys(root.resolve("synthetic-keys"));
            for(String id:keys) {
                onEdt(()->{click(form,"host.keys.add");var chooser=menu(vault);var rows=(JList<?>)named(chooser,"credential.menu.rows");
                    for(int i=0;i<rows.getModel().getSize();i++)if(((Nodes.PickerItem)rows.getModel().getElementAt(i)).id().equals(id))rows.setSelectedIndex(i);
                    enter(named(chooser,"credential.menu.search"));});
                await(()->menus(vault).isEmpty(),"managed key appended");
            }
            onEdt(()->{
                var list=(Nodes.ItemList)node(form,"host.keys");assertThat(list.items()).hasSize(2);
                assertThat(((Nodes.Choice)node(form,"host.choose")).face().orElseThrow().primary()).isEmpty();
                captureHost("ordered-keys",kitView(form),540,732);
                captureHost("ordered-keys-natural",kitView(form),0,0);
                // Source-depicted empty fields, through real editor events and genuine imported key order.
                assertThat(list.items().stream().map(dev.jasper.ui.model.ListItem::id).toList()).containsExactlyElementsOf(keys);
                type(form,"host.name.input","");type(form,"host.hostname.input","");
                assertThat(((AbstractButton)require(form,"host.save")).isEnabled()).isFalse();
                assertThat(((Nodes.ItemList)node(form,"host.keys")).selectedIds()).isEqualTo(list.selectedIds());
                // SINGLE rows have no user clear-selection action; retain the real last-imported selection.
                captureHost("source-depicted-keys",kitView(form),540,732);
                captureHost("source-depicted-keys-natural",kitView(form),0,0);
                assertThat(((JScrollPane)require(form,"host.scroll")).getVerticalScrollBar().isVisible()).isFalse();
                type(form,"host.name.input","synthetic-preview-host");type(form,"host.hostname.input","synthetic-preview-host.invalid");
                var firstUp=(AbstractButton)require(form,"@ordered/host.keys/"+list.items().getFirst().id()+"/up");
                var lastDown=(AbstractButton)require(form,"@ordered/host.keys/"+list.items().getLast().id()+"/down");
                assertThat(firstUp.isEnabled()).isFalse();assertThat(lastDown.isEnabled()).isFalse();
                var selected=list.selectedIds();click(form,"@ordered/host.keys/"+list.items().getLast().id()+"/up");
                assertThat(((Nodes.ItemList)node(form,"host.keys")).selectedIds()).isEqualTo(selected);captureHost("ordered-reordered",kitView(form),540,732);
                click(form,"host.choose.input");click(form,"host.auth.option.agent");assertThat(menus(vault)).isEmpty();
                type(form,"host.username.input","fixture");captureHost("agent-disabled",kitView(form),540,732);
                captureHost("narrow-short",kitView(form),360,420);
                var scroll=(JScrollPane)require(form,"host.scroll");assertThat(scroll.getVerticalScrollBar().isVisible()).isTrue();
                captureHost("agent-valid",kitView(form),540,732);
                // Actual request to the isolated HostStore: the UI executor has not completed persistence yet.
                click(form,"host.save");assertThat(node(form,"host.save").state().busy()).isTrue();captureHost("busy",kitView(form),540,732);
            });
            await(form::closed,"synthetic host persisted and dialog closes");
            onEdt(()->assertThat(vault.problems).isEmpty());
        }finally{onEdt(()->{themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults());themes.close();});}
    }
    @Test void surfaceFixturesExposeRealHostPersistenceErrorAndRetainDraft() throws Exception {
        var themes=onEdtValue(() -> ThemeTestSupport.controller(true));
        try(var surface=SurfaceFixtures.open("host-add","error")) {
            onEdt(()->{
                assertThat(((Nodes.Form)surface.snapshot().root()).submission()).isEqualTo(Nodes.FormSubmission.NONE);
                assertThat(((JTextField)surface.component("host.name.input")).getText()).isEqualTo("synthetic-preview-host");
                assertThat(((AbstractButton)surface.component("host.save")).isEnabled()).isTrue();
                captureHost("error",surface.view(),540,732);
            });
        }finally{onEdt(themes::close);}
    }
    static void captureHost(String state, KitView view,int width,int height) {
        try {
            Path output=Path.of("build/theme-migration-evidence/add-host/actual");Files.createDirectories(output);
            var preferred=view.getPreferredSize();if(width==0)width=preferred.width;if(height==0)height=preferred.height;
            view.setSize(width,height);layout(view);
            var image=new BufferedImage(width*2,height*2,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();try{g.scale(2,2);view.printAll(g);}finally{g.dispose();}
            ImageIO.write(image,"png",output.resolve(state+".png").toFile());
            var lines=new ArrayList<String>();lines.add("state="+state+" logical="+width+"x"+height+" preferred="+preferred+" deviceScale=2 native=false uiScale="+com.formdev.flatlaf.util.UIScale.getUserScaleFactor());
            for(String id:List.of("host.connection.card","host.name","host.name.input","host.hostname","host.port","host.username","host.jump","host.authentication.card","host.auth","host.choose","host.choose.input","host.keys","host.options.card","host.group","host.favorite","host.follow","host.preview","host.cancel","host.save")) {
                var c=named(view,id);if(c!=null){var bounds=SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),view);lines.add(id+" bounds="+bounds+" font="+c.getFont()+" baseline="+c.getBaseline(c.getWidth(),c.getHeight()));}
            }
            Files.write(output.resolve(state+".txt"),lines);
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
}
