package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import dev.jasper.app.appearance.DesktopOwnerPaints.Sample;
import dev.jasper.app.config.*;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.icons.ChromeArtwork;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.swing.*;
import static dev.jasper.app.appearance.DesktopOwnerPaints.*;
import static dev.jasper.app.appearance.DesktopFacePaints.locate;

/** Headless access to actual window chrome owners; sampled colors come from their mounted raster. */
public final class DesktopChromePaintFixture {
    private DesktopChromePaintFixture() { }
    private static void sample(KitContext c,List<Sample> out,String owner,String state,PaintPair pair,BufferedImage image,double minimum) {
        int ground=locate(image,pair.against().color(c.theme()),owner+" "+state+" background");
        int ink=locate(image,SurfaceContrast.painted(c.theme(),pair),owner+" "+state+" foreground");
        out.add(new Sample(owner,state,pair.slot().key(),pair.against().key(),ink,ground,minimum));
    }
    private static JPanel mount(KitContext c,JComponent child,ColorRole ground) {
        var host=new JPanel(new BorderLayout());host.setBackground(ground.color(c.theme()));host.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        host.add(child);host.setSize(child.getPreferredSize().width+16,child.getPreferredSize().height+16);KitTestSupport.layout(host);return host;
    }
    public static void measure(KitContext c,boolean high,List<Sample> out) {
        var selection=ThemeSelection.of(ThemeLibrary.bundledOnly(),"desktop-colors","desktop-colors",TerminalSchemeChoice.MATCH,(k,p)->{throw new AssertionError(p);});
        var themes=ThemeController.start(c.theme().dark(),selection,c.kit(),kit->theme->true,f->{throw f;}).controller();
        try {
            themes.configure(c.theme().dark(),selection,UiFontConfig.defaults(),c.theme());
            var f=new WindowPairsTest.Fixture(themes);
            for(boolean active:List.of(true,false)) {
                f.tabs.setActive(active);KitTestSupport.layout(f.tabs);
                for(var entry:List.of(f.first,f.second)) {
                    boolean selected=entry==f.second;var image=crop(f.tabs,entry);
                    sample(c,out,"window-tab",(selected?"selected":"normal")+(active?"":"-inactive"),WindowPairs.tabLabel(active,selected),image,high?7:4.5);
                }
            }
            f.tabs.setActive(true);
            for(String state:List.of("normal","hover","pressed")) {
                f.button.getModel().setRollover(state.equals("hover"));f.button.getModel().setArmed(state.equals("pressed"));f.button.getModel().setPressed(state.equals("pressed"));
                var pair=state.equals("normal")?WindowPairs.BUTTON_LABEL:state.equals("hover")?WindowPairs.HOVERED_BUTTON_LABEL:WindowPairs.PRESSED_BUTTON_LABEL;
                var host=mount(c,f.button,pair.against());sample(c,out,"toolbar",state,pair,crop(host,f.button),high?7:4.5);
            }
            for(var pair:List.of(WindowPairs.CELL_TEXT,WindowPairs.WARNING_CELL))sample(c,out,"status",pair.slot().key(),pair,paint(f.warning),high?7:4.5);
            sample(c,out,"status","danger",WindowPairs.DANGER_CELL,paint(f.danger),high?7:4.5);
            for(int i=0;i<4;i++) {
                var component=List.of(f.run,f.slashRun,f.activeRun,f.slashActiveRun).get(i);
                var pair=List.of(WindowPairs.RUN,WindowPairs.SLASH_RUN,WindowPairs.ACTIVE_RUN,WindowPairs.SLASH_ACTIVE_RUN).get(i);
                var bar=new WindowStatusBar(i%2==1?new SlashSegments():new RuledCells(),component);
                bar.refreshTheme();bar.setSize(600,28);KitTestSupport.layout(bar);
                sample(c,out,"status-run",Integer.toString(i),pair,crop(bar,component),high?7:4.5);
            }
            for(boolean hover:List.of(false,true)) {
                var image=f.search(hover);
                for(var pair:List.of(WindowPairs.PLACEHOLDER,WindowPairs.KEYCAP_TEXT))sample(c,out,"window-search",pair.slot().key()+"-"+pair.against().key()+"-"+hover,pair,image,high?7:4.5);
            }
            stripes(c,high,out);
            equalTabs(c,themes,high,out);
            raisedSearch(c,high,out);
            slashStatus(c,high,out);
        } finally {DesktopTestSupport.closeOwnersNow();themes.close();}
    }
    private static void equalTabs(KitContext c,ThemeController themes,boolean high,List<Sample> out) {
        var owner=DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()),themes,WorkspaceChrome.STANDARD);
        owner.setAnimations(new AnimationSettings(false,0,0,0));
        owner.currentTerminal().rename("first");owner.newTab(DesktopTestSupport.HOME);owner.currentTerminal().rename("second");owner.update();
        var strip=owner.windowTabs();strip.setSize(400,32);KitTestSupport.layout(strip);
        for(boolean active:List.of(true,false))for(boolean hover:List.of(false,true)) {
            strip.setActive(active);
            for(String title:List.of("first","second")) {
                var button=KitTestSupport.require(strip,"select:"+title,AbstractButton.class);var entry=(JComponent)button.getParent();
                entry.dispatchEvent(new java.awt.event.MouseEvent(entry,hover?java.awt.event.MouseEvent.MOUSE_ENTERED:java.awt.event.MouseEvent.MOUSE_EXITED,0,0,hover?5:-5,5,0,false));
                var image=crop(strip,button);String saved=button.getText();button.setText("");var blank=crop(strip,button);button.setText(saved);
                boolean selected=title.equals("second");String bg=selected?"surface.terminal":"surface.tabstrip";
                out.add(glyph("equal-tab",(selected?"selected":"unselected")+(active?"":"-inactive")+(hover?"-hover":""),selected?"text.primary":active?"text.secondary":"text.tertiary",bg,image,blank,high?7:4.5));
            }
        }
        strip.setActive(true);var selected=KitTestSupport.require(strip,"select:second",AbstractButton.class);var entry=(JComponent)selected.getParent();var image=crop(strip,entry);
        out.add(new Sample("equal-tab","indicator","tab.indicator","surface.terminal",locate(image,SemanticColor.TAB_INDICATOR.color(c.theme()),"equal tab indicator"),locate(image,SemanticColor.SURFACE_TERMINAL.color(c.theme()),"equal tab selected fill"),3));
    }
    private static void raisedSearch(KitContext c,boolean high,List<Sample> out) {
        var field=new TitleSearchField(()->{},()->"MMMM");field.setContext("MMMM",null,null);
        var root=mount(c,field,SemanticColor.SURFACE_APP);
        for(boolean hover:List.of(false,true)) {
            field.dispatchEvent(new java.awt.event.MouseEvent(field,hover?java.awt.event.MouseEvent.MOUSE_ENTERED:java.awt.event.MouseEvent.MOUSE_EXITED,0,0,5,5,0,false));
            var image=crop(root,field);int bg=locate(image,SemanticColor.SURFACE_FIELD_RAISED.color(c.theme()),"raised search background");
            for(var role:List.of(SemanticColor.TEXT_FIELD_RAISED,SemanticColor.TEXT_PLACEHOLDER)) {
                var expected=role==SemanticColor.TEXT_PLACEHOLDER?SurfaceContrast.on(c.theme().semantic(),role,SemanticColor.SURFACE_FIELD_RAISED,SurfaceContrast.INDICATOR):role.color(c.theme());
                out.add(new Sample("raised-search",role.key()+"-"+hover,role.key(),"surface.field.raised",locate(image,expected,"raised search text"),bg,high?7:4.5));
            }
            var at=SwingUtilities.convertPoint(field,0,0,root);var full=paint(root);
            out.add(new Sample("raised-search",hover?"hover-edge":"edge",hover?"border.field.raised.hover":"border.field.raised","surface.app",
                pixel(full,at.x+field.getWidth()/2.0,at.y+.25),pixel(full,2,2),3));
        }
    }
    private static void slashStatus(KitContext c,boolean high,List<Sample> out) {
        for(var severity:Arrays.asList(null,dev.jasper.app.config.ConfigDiagnostic.Severity.WARNING,dev.jasper.app.config.ConfigDiagnostic.Severity.ERROR)) {
            var bar=new WindowStatusBar(new SlashSegments(),null);bar.setMetadata(RuledCellsTest.local("MMMM"),"80 x 24",true);bar.setConfiguration(RuledCellsTest.state(severity));
            bar.refreshTheme();bar.setSize(600,28);KitTestSupport.layout(bar);var image=paint(bar);
            sample(c,out,"slash-status",String.valueOf(severity)+"-text",new PaintPair(SemanticColor.TEXT_SECONDARY,SemanticColor.SURFACE_STATUS,SurfaceContrast.Guard.TEXT),image,high?7:4.5);
            var role=severity==null?SemanticColor.STATUS_SUCCESS:severity==dev.jasper.app.config.ConfigDiagnostic.Severity.ERROR?SemanticColor.STATUS_DANGER:SemanticColor.STATUS_WARNING;
            sample(c,out,"slash-status",String.valueOf(severity),new PaintPair(role,SemanticColor.SURFACE_STATUS,SurfaceContrast.Guard.TEXT),image,high?7:4.5);
        }
    }
    private static void stripes(KitContext c,boolean high,List<Sample> out) {
        for(String state:List.of("normal","hover","selected")) {
            var stripe=new BottomStrip(c.kit(),ChromeArtwork.CLASSIC);
            stripe.render(List.of(new ToolStripe.Button("a","MMMM",null,Slot.BOTTOM_LEFT,state.equals("selected"))),List.of());
            stripe.setSize(400,stripe.getPreferredSize().height);KitTestSupport.layout(stripe);var button=stripe.buttons().getFirst();button.getModel().setRollover(state.equals("hover"));
            var pair=state.equals("selected")?ChromePainter.OPEN_STRIPE_LABEL:state.equals("hover")?ChromePainter.HOVERED_STRIPE_LABEL:ChromePainter.STRIPE_LABEL;
            sample(c,out,"stripe",state,pair,crop(stripe,button),high?7:4.5);
        }
        for(var tone:PanelBadge.Tone.values()) {
            var stripe=new BottomStrip(c.kit(),ChromeArtwork.CLASSIC);
            stripe.render(List.of(new ToolStripe.Button("a","MMMM",null,Slot.BOTTOM_LEFT,false,Optional.of(new PanelBadge(88,tone,"Unread")))),List.of());
            stripe.setSize(400,stripe.getPreferredSize().height);KitTestSupport.layout(stripe);
            sample(c,out,"stripe-pill",tone.name(),tone==PanelBadge.Tone.ERROR?ChromePainter.ERROR_PILL:ChromePainter.ACCENT_PILL,crop(stripe,stripe.buttons().getFirst()),high?7:4.5);
        }
    }
}
