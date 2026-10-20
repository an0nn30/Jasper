package dev.jasper.app.plugins;

import dev.jasper.app.appearance.SurfaceFixtures;
import java.awt.*;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;

/** Source-scale actual Sessions owners; retained state and raw geometry accompany every PNG. */
public final class SessionsSurfacePreview {
    private SessionsSurfacePreview() {}
    public static void main(String[] args) throws Exception { capture(Path.of(args[0]), args); }
    public static void capture(Path output,String[] args) throws Exception {
        if(args.length>1 && !"sessions-panel".contains(args[1]))return;
        for(String theme:args.length>2?List.of(args[2]):List.of("dark","light")) {
            Path home=Files.createTempDirectory("jasper-sessions-preview-");
            try(var opened=new SessionsSurfaceFixture(home,SurfaceFixtures.theme(theme))) {
                int width=args.length>5?Integer.parseInt(args[5]):com.formdev.flatlaf.util.UIScale.scale(260);
                int height=com.formdev.flatlaf.util.UIScale.scale(395);
                opened.size(width,height);
                save(output,theme,"empty",opened,args);
                opened.populate();opened.size(width,height);save(output,theme,"selected",opened,args);
                opened.filter("no matching synthetic host");opened.size(width,height);save(output,theme,"filtered-empty",opened,args);
                opened.filter("");opened.size(width,height);save(output,theme,"disconnected",opened,args);
                opened.loadError();opened.size(width,height);save(output,theme,"load-error",opened,args);
                opened.size(width,com.formdev.flatlaf.util.UIScale.scale(120));save(output,theme,"short",opened,args);
            } finally {try(var files=Files.walk(home)){for(Path path:files.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        }
    }
    private static void save(Path output,String theme,String state,SessionsSurfaceFixture opened,String[] args) throws Exception {
        if(args.length>3 && !args[3].equals(state))return;
        Path directory=Files.createDirectories(output.resolve(theme).resolve("sessions-panel"));
        ImageIO.write(opened.capture(2),"png",directory.resolve(state+".png").toFile());
        var metadata=new StringBuilder("provenance=actual Remote factory + app host adapters + locked synthetic Vault; headless/no connection\ndeviceScale=2\nlogicalUiScale="+com.formdev.flatlaf.util.UIScale.getUserScaleFactor()+"\n");
        for(String id:List.of("sessions","sessions.header","sessions.header.title","sessions.header.count","sessions.search","sessions.rows","sessions.footer-surface","sessions.footer","card.identity","card.tile","card.name","card.address","card.actions","card.locked","card.credential","card.edit","card.connect")) {
            try {metadata.append(onEdtValue(()->{
                var c=opened.component(id);var bounds=SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),opened.view());
                return id+"="+bounds+"; font="+c.getFont()+"; preferred="+c.getPreferredSize()+"\n";
            }));}catch(AssertionError absent){metadata.append(id).append("=absent\n");}
        }
        metadata.append(onEdtValue(()->headerButtons(opened.view().panelHeader("sessions"),opened.view())));
        Files.writeString(directory.resolve(state+".txt"),metadata);
    }
    private static String headerButtons(Container parent,Container root) {
        var text=new StringBuilder();
        for(var c:parent.getComponents()) {
            if(c instanceof AbstractButton button)text.append("header-action ").append(button.getAccessibleContext().getAccessibleName()).append("=").append(SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),root)).append("; icon=").append(button.getIcon()==null ? "none" : button.getIcon().getIconWidth()+"x"+button.getIcon().getIconHeight()).append('\n');
            if(c instanceof Container nested)text.append(headerButtons(nested,root));
        }
        return text.toString();
    }

}
