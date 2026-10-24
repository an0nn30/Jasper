package dev.jasper.app.palette;

import dev.jasper.app.appearance.DesktopOwnerPaints.Sample;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.swing.KitContext;
import java.util.*;
import java.util.List;
import javax.swing.SwingUtilities;
import static dev.jasper.app.appearance.DesktopOwnerPaints.*;
import static dev.jasper.app.appearance.DesktopCompositionPaints.text;

/** Test-only access to the real Run window, including its title, result cells, detail row and box owners. */
public final class DesktopRunPaintFixture {
    private DesktopRunPaintFixture() { }
    public static void measure(KitContext c,boolean high,List<Sample> out) {
        try(var view=RunWindowFixtures.view(c,new ArrayList<>())) {
            var entries=new ArrayList<PaletteEntry>();
            entries.add(new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES,"Matches"));
            entries.add(RunWindowFixtures.item(RunWindowFixtures.SESSIONS,"a","MMMM","MMMM",null));
            entries.add(RunWindowFixtures.item(RunWindowFixtures.SESSIONS,"b","MMMM","MMMM",null));
            entries.add(new PaletteEntry.More(RunWindowFixtures.SESSIONS));
            view.setEntries(entries,null,"Nothing found");view.setSize(500,480);KitTestSupport.layout(view);
            String dialog=c.kit().colors().dialogSurface().key();
            text(view,"run.title","run","title","text.primary","surface.header.active",high,out);
            text(view,"run.shortcut","run","shortcut","text.tertiary","surface.header.active",high,out);
            text(view,"run.open","run","open-label","text.strong.secondary",dialog,high,out);
            text(view,"run.detail.value","run","detail-value","text.secondary",dialog,high,out);
            text(view,"entry.0.title","run","heading","text.strong.secondary","surface.tableHeader",high,out);
            text(view,"entry.0.count","run","count","text.placeholder","surface.tableHeader",high,out);
            text(view,"entry.1.title","run","selected-title","text.onAccent","accent",high,out);
            text(view,"entry.1.detail","run","selected-detail","text.onAccent.secondary","accent",high,out);
            text(view,"entry.2.detail","run","row-detail","text.placeholder","surface.card",high,out);
            text(view,"entry.3.title","run","more","text.strong.secondary","surface.card",high,out);
            view.fieldBox().focusShown(false);var image=paint(view);
            for(var box:List.of(view.fieldBox(),view.listBox())) {
                var at=SwingUtilities.convertPoint(box,0,0,view);double x=at.x+box.getWidth()/2.0;
                boolean field=box==view.fieldBox();
                out.add(new Sample("run-edge",field?"field":"list",field?"border.input":"border.control",field?"surface.input":dialog,
                    pixel(image,x,at.y+(field?com.formdev.flatlaf.util.UIScale.scale(c.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.FOCUS_RING)):0)+.25),
                    pixel(image,x,at.y+(field?box.getHeight()/2.0:-3)),3));
            }
        }
    }
}
