package dev.jasper.app.workspace;

import dev.jasper.app.platform.DesktopEnvironment;
import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.platform.DesktopRenderFixtures;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.*;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.swing.FlatLafInstaller;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Test-only window-owner bridge; actual menus, terminal views, binding and attached sessions, without native windows. */
public final class DesktopRetentionFixture {
    private DesktopRetentionFixture() { }
    public static void verify() throws Exception {
        var fixtures = DesktopRenderFixtures.read();
        for(var kit:List.of(Kit.DEFAULT,Kit.CLASSIC)) {
            var pending = new ArrayDeque<Runnable>();
            var windows = new WindowContent[2]; var themes = new ThemeController[1];
            var selection = ThemeSelection.of(ThemeLibrary.bundledOnly(),"desktop-colors","desktop-colors",
                TerminalSchemeChoice.parse("slate-cobalt/high-contrast").orElseThrow(),(key,problem)->{throw new AssertionError(problem);});
            try {
                edt(()->{
                    themes[0]=ThemeController.start(true,selection,kit, selected->theme->{FlatLafInstaller.install(theme,selected);return true;},
                        failure->{throw failure;}).controller();
                    var launcher = new ShellLauncher(pending::add,path->session(),"retained-session");
                    for(int i=0;i<2;i++) windows[i]=content(launcher,themes[0],kit==Kit.CLASSIC ? WindowPairsTest.CLASSIC : WorkspaceChrome.STANDARD);
                });
                while(!pending.isEmpty())pending.remove().run();
                until(()->Arrays.stream(windows).allMatch(w->w.currentPane().session()!=null && w.currentPane().session().title().equals("ready")));
                edt(()->{
                    var sessions=Arrays.stream(windows).map(w->w.currentPane().session()).toList();
                    var views=Arrays.stream(windows).map(w->w.currentPane().view()).toList();
                    var menus=Arrays.stream(windows).map(w->w.menuBar().getMenu(0).getPopupMenu()).toList();
                    var palette=themes[0].current().palette();
                    for(int i=0;i<2;i++) {
                        var w=windows[i];w.setSize(900,600);dev.jasper.app.testsupport.KitTestSupport.layout(w);
                        var view=views.get(i);view.setSize(700,300);
                        // A simulated double click uses TerminalView's registered mouse route; no OS focus is requested.
                        view.dispatchEvent(new MouseEvent(view,MouseEvent.MOUSE_PRESSED,0,0,12,8,2,false,MouseEvent.BUTTON1));
                        view.dispatchEvent(new MouseEvent(view,MouseEvent.MOUSE_RELEASED,0,0,12,8,2,false,MouseEvent.BUTTON1));
                        assertThat(view.selectedText()).contains("retained");
                        var menu=menus.get(i);menu.setSize(menu.getPreferredSize()); menu.doLayout(); menu.setUI(new com.formdev.flatlaf.ui.FlatPopupMenuUI() {
                            @Override public Popup getPopup(JPopupMenu popup,int x,int y) {return new Popup(){@Override public void show(){} @Override public void hide(){}};}
                        }); menu.setVisible(true);
                    }
                    var before=views.stream().map(DesktopRetentionFixture::paint).toList();
                    for(var fixture:fixtures) {
                        var next=DesktopPalette.resolve(fixture.dark(),DesktopPalette.SourceMode.AUTO,DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "KDE")),fixture.input()).theme();
                        for(int repeat=0;repeat<2;repeat++) {
                            themes[0].configure(fixture.dark(),selection,UiFontConfig.defaults(),next);
                            assertThat(themes[0].current().palette()).isEqualTo(palette);
                            for(int i=0;i<2;i++) {
                                var view=views.get(i);
                                assertThat(windows[i].currentPane().session()).isSameAs(sessions.get(i));
                                assertThat(windows[i].currentPane().view()).isSameAs(view);
                                assertThat(view.selectedText()).contains("retained");
                                assertThat(view.palette()).isEqualTo(palette);
                                assertThat(windows[i].menuBar().getMenu(0).getPopupMenu()).isSameAs(menus.get(i));
                                assertThat(menus.get(i).isVisible()).isTrue();
                                var image=paint(view);
                                // First row contains visible selected text; the cursor is on the third row.
                                assertThat(image.getRGB(0,0,image.getWidth(),20,null,0,image.getWidth()))
                                    .as("retained terminal pixels "+fixture.name()).containsExactly(before.get(i).getRGB(0,0,image.getWidth(),20,null,0,image.getWidth()));
                                assertThat(Arrays.stream(image.getRGB(0,0,image.getWidth(),40,null,0,image.getWidth())).distinct().count()).isGreaterThan(4);
                                if(repeat==0) try {
                                    var output=Files.createDirectories(Path.of("build/desktop-colors"));
                                    String stem=fixture.name()+"-"+(fixture.dark()?"dark":"light")+"-"+kit.id()+"-"+i;
                                    javax.imageio.ImageIO.write(paint(windows[i]),"png",output.resolve(stem+"-window.png").toFile());
                                    javax.imageio.ImageIO.write(paint(menus.get(i)),"png",output.resolve(stem+"-menu.png").toFile());
                                } catch(IOException failure){throw new UncheckedIOException(failure);}
                            }
                        }
                    }
                    try {
                        var output=Files.createDirectories(Path.of("build/desktop-colors"));
                        for(int i=0;i<2;i++) {
                            javax.imageio.ImageIO.write(paint(windows[i]),"png",output.resolve("window-"+kit.id()+"-"+i+".png").toFile());
                            javax.imageio.ImageIO.write(paint(menus.get(i)),"png",output.resolve("menu-"+kit.id()+"-"+i+".png").toFile());
                        }
                    } catch(IOException failure){throw new UncheckedIOException(failure);}
                    menus.forEach(menu->menu.setVisible(false));
                });
            } finally {closeOwners();if(themes[0]!=null)edt(themes[0]::close);}
        }
    }
    private static BufferedImage paint(JComponent component) {
        var image=new BufferedImage(component.getWidth(),component.getHeight(),BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();try{component.paint(g);}finally{g.dispose();}return image;
    }
    private static TerminalSession session() {
        var stop=new CountDownLatch(1); var exit=new CompletableFuture<Integer>();
        var bytes=new ByteArrayInputStream("retained terminal text\r\nsecond visible row\r\n\033]2;ready\007".getBytes(StandardCharsets.UTF_8));
        var input=new InputStream(){
            @Override public int read() throws IOException {
                int value=bytes.read();if(value>=0)return value;
                try{stop.await();return -1;}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IOException(failure);}
            }
            @Override public int read(byte[] target,int offset,int length) throws IOException {
                if(bytes.available()>0)return bytes.read(target,offset,length);return read();
            }
            @Override public void close(){stop.countDown();}
        };
        return TerminalSession.attach(new AttachedConnection(input,OutputStream.nullOutputStream(),(columns,rows)->{},exit,
            ()->{input.close();exit.complete(0);}),new GridSize(80,24),100);
    }
}
