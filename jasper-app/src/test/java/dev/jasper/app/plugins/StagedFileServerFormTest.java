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

/** Actual FileServerPlugin NEW action, real Vault producer and persistence, never starts a server. */
class StagedFileServerFormTest {
    static int m(dev.jasper.ui.metrics.DesignMetrics.Role role) { return dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(role); }

    @TempDir Path root;
    @Test void actualGroupedFormAndAnchoredVaultCreationPreserveStateAndGrant() throws Exception {
        var themes=onEdtValue(() -> ThemeTestSupport.controller(true));
        try(var owner=new KitViewTestAccess(root.resolve("home"),"fileserver-add",SurfaceFixtures.theme("dark"))) {
            var vault=owner.vault();var form=owner.surface();
            onEdt(()->{
                captureServer("anonymous",kitView(form),540,690);
                assertThat(require(form,"server.require-tls.row.title").getX()-require(form,"server.tls.row.title").getX()).isEqualTo(m(dev.jasper.ui.metrics.DesignMetrics.Role.SETTING_DEPENDENT_INDENT));
                assertThat(((Nodes.Form)kitView(form).spec().root()).submission()).isEqualTo(Nodes.FormSubmission.NONE);
                assertThat(node(form,"server.save").state().enabled()).isFalse();
                click(form,"server.login.option.vault");click(form,"server.writable");
            });
            settle();owner.theme(SurfaceFixtures.theme("light"));
            onEdt(()->{captureServer("vault-old",kitView(form),540,730);captureServer("vault-old-natural",kitView(form),0,0);});
            owner.theme(SurfaceFixtures.theme("dark"));
            onEdt(()->{
                click(form,"server.account.choose.input");captureServer("picker-open-empty",menu(vault),0,0);
                search(menu(vault),"share");enter(named(menu(vault),"credential.menu.search"));click(vault.surface("New Credential"),"create.login");
                var editor=vault.surface("Add Login");assertThat(editor.ownerSurface()).contains(form);
                type(editor,"name.input","Share login");type(editor,"username.input","fixture-user");type(editor,"password.input","synthetic-secret");click(editor,"save");
            });
            await(()->menus(vault).size()==1,"picker returns after Login save");
            onEdt(()->{assertThat(((Nodes.PickerMenu)menu(vault).spec().root()).query()).isEqualTo("share");key(menu(vault),"down");enter(named(menu(vault),"credential.menu.search"));});
            await(()->!((Nodes.Choice)node(form,"server.account.choose")).face().orElseThrow().primary().isEmpty(),"real selection");
            onEdt(()->{
                try {
                    Class<?> api=vault.loader.loadClass("dev.jasper.vault.api.VaultApi");Object service=owner.remoteContext().services().require(api);
                    Object descriptor=((List<?>)api.getMethod("credentials").invoke(service)).getFirst();var id=(UUID)descriptor.getClass().getMethod("id").invoke(descriptor);
                    var fetch=api.getMethod("credential",UUID.class);var first=(java.util.concurrent.CompletableFuture<?>)fetch.invoke(service,id);assertThat(first).isCompleted();
                    ((AutoCloseable)((Optional<?>)first.join()).orElseThrow()).close();var second=(java.util.concurrent.CompletableFuture<?>)fetch.invoke(service,id);assertThat(second).isNotDone();second.cancel(false);
                }catch(Exception e){throw new AssertionError(e);}
                captureServer("selected",kitView(form),540,730);
                click(form,"server.account.choose.input");search(menu(vault),"sha");var field=(JTextField)named(menu(vault),"credential.menu.search");field.setCaretPosition(2);var document=field.getDocument();
                captureServer("filtering",menu(vault),0,0);
                try{owner.theme(SurfaceFixtures.theme("light"));}catch(Exception e){throw new AssertionError(e);}captureServer("light",kitView(form),540,730);captureServer("light-natural",kitView(form),0,0);
                assertThat(field.getDocument()).isSameAs(document);assertThat(field.getCaretPosition()).isEqualTo(2);
                try{owner.theme(SurfaceFixtures.theme("dark"));}catch(Exception e){throw new AssertionError(e);}
                themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new dev.jasper.app.config.UiFontConfig("system",20));captureServer("enlarged",kitView(form),810,1095);captureServer("enlarged-natural",kitView(form),0,0);
                themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults());
                key(menu(vault),"escape");
            });
            for(String kind:List.of("login","key")) {
                onEdt(()->{click(form,"server.account.choose.input");search(menu(vault),"cancel-fixture");enter(named(menu(vault),"credential.menu.search"));click(vault.surface("New Credential"),"create."+kind);});
                var editor=vault.awaitSurface(kind.equals("login")?"Add Login":"Add SSH Key");onEdt(()->click(editor,"cancel"));
                await(()->menus(vault).size()==1,"creation cancel returns");onEdt(()->{assertThat(((Nodes.PickerMenu)menu(vault).spec().root()).query()).isEqualTo("cancel-fixture");key(menu(vault),"escape");});
            }
            onEdt(()->{
                click(form,"server.account.choose.input");search(menu(vault),"");select(menu(vault),0);enter(named(menu(vault),"credential.menu.search"));
                assertThat(((Nodes.Choice)node(form,"server.account.choose")).face().orElseThrow().primary()).isEmpty();
                for(String protocol:List.of("HTTP","FTP","SFTP","TFTP")) {
                    click(form,"server.protocol.option."+protocol);
                    for(String login:protocol.equals("TFTP")?List.of("anon"):List.of("anon","vault","session")) {
                        if(!protocol.equals("TFTP"))click(form,"server.login.option."+login);
                        captureServer(protocol.toLowerCase()+"-"+login,kitView(form),540,730);
                    }
                }
                click(form,"server.protocol.option.HTTP");click(form,"server.writable");
                type(form,"server.name.input","synthetic-preview-server");type(form,"server.folder.input",root.resolve("home").toString());
                var field=(JTextField)require(form,"server.name.input");var document=field.getDocument();field.select(2,7);
                try{owner.theme(SurfaceFixtures.theme("light"));owner.theme(SurfaceFixtures.theme("dark"));}catch(Exception e){throw new AssertionError(e);}
                assertThat(field.getDocument()).isSameAs(document);assertThat(field.getSelectionStart()).isEqualTo(2);assertThat(field.getSelectionEnd()).isEqualTo(7);
                type(form,"server.folder.input",root.resolve("home/very-long-synthetic-folder-path/with-many-segments/that-stays-in-the-input").toString());captureServer("long-path",kitView(form),540,690);
                type(form,"server.folder.input",root.resolve("home").toString());captureServer("short",kitView(form),360,420);
                assertThat(((JScrollPane)require(form,"server.editor.scroll")).getVerticalScrollBar().isVisible()).isTrue();
                StagedRemoteHost.press(field,java.awt.event.KeyEvent.VK_ENTER,'\n');assertThat(form.closed()).isFalse();
                captureServer("valid",kitView(form),540,690);click(form,"server.save");assertThat(node(form,"server.save").state().busy()).isTrue();captureServer("busy",kitView(form),540,690);
            });
            await(form::closed,"real persistence closes editor");onEdt(()->assertThat(vault.problems).isEmpty());
            vault.invoke("dev.jasper.fileserver.new");var second=vault.awaitSurface("New File Server");
            onEdt(()->{click(second,"server.login.option.vault");click(second,"server.account.choose.input");assertThat(menus(vault)).hasSize(1);second.close();assertThat(menus(vault)).isEmpty();assertThat(vault.problems).isEmpty();});
        }finally{onEdt(()->{themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults());themes.close();});}
    }
    @Test void actualSaveFailureRetainsDraftAndSupportsRetry() throws Exception {
        var themes=onEdtValue(() -> ThemeTestSupport.controller(true));
        try(var surface=SurfaceFixtures.open("fileserver-add","error")) {
            onEdt(()->{assertThat(((JTextField)surface.component("server.name.input")).getText()).isEqualTo("synthetic-preview-server");assertThat(((AbstractButton)surface.component("server.save")).isEnabled()).isTrue();captureServer("error",surface.view(),540,690);});
        }finally{onEdt(themes::close);}
    }
    private static String wrappedBaseline(Component component,String id,int top) {
        if(!id.equals("server.login.help") && !component.getClass().getSimpleName().equals("WrappedText"))return "";
        var image=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try {g.scale(2,2);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);g.setFont(component.getFont());return " wrappedFirstPaintBaselineLogical="+(top+g.getFontMetrics().getAscent());}finally{g.dispose();}
    }
    static void captureServer(String state,KitView view,int width,int height) {
        try {
            Path output=Path.of("build/theme-migration-evidence/fileserver-add/actual");Files.createDirectories(output);
            var preferred=view.getPreferredSize();if(width==0)width=preferred.width;if(height==0)height=preferred.height;
            view.setSize(width,height);layout(view);var image=new BufferedImage(width*2,height*2,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();try{g.scale(2,2);view.printAll(g);}finally{g.dispose();}
            ImageIO.write(image,"png",output.resolve(state+".png").toFile());
            var lines=new ArrayList<String>();lines.add("state="+state+" logical="+width+"x"+height+" preferred="+preferred+" deviceScale=2 native=false uiScale="+com.formdev.flatlaf.util.UIScale.getUserScaleFactor());
            for(String id:List.of("server.general.title","server.general.card","server.name","server.name.input","server.protocol","server.folder","server.folder.choose","server.reachable","server.port","server.access.title","server.access.card","server.login","server.account.choose","server.login.help","server.writable.row","server.security.title","server.security.card","server.tls.row","server.tls.row.title","server.require-tls.row","server.require-tls.row.title","server.require-tls.row.description","server.autostart.row","server.preview","server.cancel","server.save")) {
                var c=named(view,id);if(c!=null){var b=SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),view);lines.add(id+" bounds="+b+" font="+c.getFont()+" size2D="+c.getFont().getSize2D()+" swingReportedBaseline="+c.getBaseline(c.getWidth(),c.getHeight())+wrappedBaseline(c,id,b.y));}
            }
            Files.write(output.resolve(state+".txt"),lines);
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
}
