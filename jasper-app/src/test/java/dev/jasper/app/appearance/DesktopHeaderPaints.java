package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.theme.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import static dev.jasper.app.appearance.DesktopOwnerPaints.*;
import static dev.jasper.app.appearance.DesktopFacePaints.*;
import static dev.jasper.app.appearance.DesktopCompositionPaints.text;

/** Real borrowed panel header strips in their focused and inactive frames. */
final class DesktopHeaderPaints {
    private DesktopHeaderPaints() { }
    static void measure(KitContext c,boolean high,List<Sample> out) {
        var node=new Nodes.Panel("panel",ControlState.READY,new Nodes.Header("header",ControlState.READY,"MMMM",OptionalInt.of(88),Optional.empty(),Optional.empty(),List.of()),
            Optional.empty(),new Nodes.Text("body",ControlState.READY,"Contents",Nodes.TextRole.BODY),Optional.empty());
        for(boolean focused:List.of(false,true))try(var view=new KitView(c,new ViewSpec(node),e->{})) {
            view.setPanelHostHeader("panel",new PanelHostHeader("MMMM",true,List.of(),focused?PanelHostHeader.Frame.FLUSH_FOCUSED:PanelHostHeader.Frame.FLUSH));
            view.paintsSurface(true);view.setSize(400,100);KitTestSupport.layout(view);
            String bg=focused?"surface.header.active":"surface.header",state=focused?"focused":"normal";
            text(view,"header.title","panel-header",state+"-title","text.primary",bg,high,out);
            text(view,"header.count","panel-header",state+"-count","text.strong.secondary",bg,high,out);
            var detail=KitTestSupport.require(view,"header.host-detail",JLabel.class);var icon=detail.getIcon();detail.setIcon(null);
            text(view,"header.host-detail","panel-header",state+"-detail","text.secondary",bg,high,out);detail.setIcon(icon);
            var image=crop(view,detail);var painter=new ChromePainter(c.kit());var pair=focused?ChromePainter.FOCUSED_HEADER_DOT:ChromePainter.HEADER_DOT;
            int ground=locate(image,painter.headerSurface(c.theme(),focused),"panel header background");
            int ink=locate(image,SurfaceContrast.painted(c.theme(),pair,painter.headerSurface(c.theme(),focused)),"panel header dot");
            out.add(new Sample("panel-header",state+"-dot","status.success",bg,ink,ground,3));
        }
    }
}
