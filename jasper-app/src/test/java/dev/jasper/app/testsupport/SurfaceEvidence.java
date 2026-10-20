package dev.jasper.app.testsupport;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Headless actual-owner pixels and measured geometry; synthetic fixture data only, never native acceptance. */
public final class SurfaceEvidence {
    private SurfaceEvidence() { }
    public static void save(Path file, JComponent root, int width, int height, int scale) {
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("EDT capture required");
        root.setSize(width,height);
        for(int i=0;i<5;i++)LayoutTestSupport.layoutTree(root);
        var image=new BufferedImage(width*scale,height*scale,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();try{g.scale(scale,scale);root.printAll(g);}finally{g.dispose();}
        var details=new StringBuilder("owner="+root.getClass().getName()+"\nheadless=true; nativeAcceptance=false; deviceScale="+scale+"; uiScale="+com.formdev.flatlaf.util.UIScale.getUserScaleFactor()+"\n");
        describe(root,root,details);
        try{Files.createDirectories(file.getParent());ImageIO.write(image,"png",file.toFile());Files.writeString(file.resolveSibling(file.getFileName()+".txt"),details);}
        catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    private static void describe(Component component,JComponent root,StringBuilder text) {
        if(!component.isVisible())return;
        if(component.getName()!=null || component==root) {
            var bounds=component==root?new Rectangle(0,0,root.getWidth(),root.getHeight()):SwingUtilities.convertRectangle(component.getParent(),component.getBounds(),root);
            text.append(component.getName()).append(' ').append(component.getClass().getName()).append(' ').append(bounds)
                .append(" preferred=").append(component.getPreferredSize()).append(" font=").append(component.getFont()).append('\n');
        }
        if(component instanceof Container container)for(var child:container.getComponents())describe(child,root,text);
    }
}
