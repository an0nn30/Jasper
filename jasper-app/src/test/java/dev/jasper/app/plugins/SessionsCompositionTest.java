package dev.jasper.app.plugins;

import dev.jasper.app.appearance.SurfaceFixtures;
import java.awt.*;
import java.util.List;
import java.util.Set;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.StagedRemoteHost.*;
import static org.assertj.core.api.Assertions.*;

class SessionsCompositionTest {
    @TempDir Path home;
    @Test void actualLockedVaultAndSavedHostsComposeOneHeaderCountAndPersistentIdentity() throws Exception {
        var calibration=SurfaceFixtures.theme("dark");
        try(var fixture=new SessionsSurfaceFixture(home,calibration)) {
            fixture.size(260,395);
            onEdt(()->{
                assertThat(fixture.component("sessions.search").isVisible()).isFalse();
                assertThat(labels(fixture.view().panelHeader("sessions"))).contains("Sessions").doesNotContain("0");
            });
            fixture.populate();fixture.size(260,395);
            onEdt(()->{
                var header=fixture.view().panelHeader("sessions");
                assertThat(labels(header).stream().filter("6"::equals)).hasSize(1);
                assertThat(names(header)).containsExactly("New session","Import sessions","Options","Hide");
                assertThat(labels(fixture.view())).contains("Vault locked","amp","ubuntu@100.110.65.45:22");
                assertThat(bounds(fixture.component("card.identity"),fixture.view()).x).isEqualTo(12);
                var before=bounds(fixture.component("sessions.footer-surface"),fixture.view());
                assertThat(before).isEqualTo(new Rectangle(0,308,260,87));
                assertThat(bounds(fixture.component("card.connect"),fixture.view())).isEqualTo(new Rectangle(174,358,74,28));
                var shot=fixture.capture(2);
                int accent=calibration.workspace().semantic().color(dev.jasper.ui.theme.SemanticColor.ACCENT).getRGB();
                // theme.md's white inset line tints the face's top logical row; it is still the face.
                var tint=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);var tg=tint.createGraphics();
                try{tg.setColor(new java.awt.Color(accent,true));tg.fillRect(0,0,1,1);tg.setColor(dev.jasper.ui.theme.ColorMath.opacity(java.awt.Color.WHITE,
                    dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.BUTTON_INSET_PERCENT)/100.0));tg.fillRect(0,0,1,1);}finally{tg.dispose();}
                int inset=tint.getRGB(0,0);
                int left=500,right=0,top=772,bottom=0;
                for(int y=720;y<772;y++)for(int x=350;x<500;x++)if(shot.getRGB(x,y)==accent||shot.getRGB(x,y)==inset){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
                assertThat(new Rectangle(left,top,right-left+1,bottom-top+1)).as("exact source2x Connect face; hit/text allocation remains unchanged").isEqualTo(new Rectangle(354,722,136,42));
                assertThat(shot.getRGB(422,763)).isEqualTo(accent);
                assertThat(shot.getRGB(422,764)).isNotEqualTo(accent);
                var list=(JScrollPane)fixture.component("sessions.rows");
                list.getViewport().setViewPosition(new Point(0,84));
                assertThat(bounds(fixture.component("sessions.footer-surface"),fixture.view())).isEqualTo(before);
                assertThat(((JLabel)fixture.component("card.name")).getText()).isEqualTo("amp");
                ((AbstractButton)fixture.component("row.host:00000000-0000-4000-8000-000000000002.star")).doClick(0);
            });
            StagedVaultHost.settle();fixture.size(260,395);
            onEdt(()->assertThat(((JLabel)fixture.component("card.name")).getText()).isEqualTo("amp"));
            fixture.filter("missing");fixture.size(260,395);
            onEdt(()->{
                assertThat(fixture.component("sessions.search").isVisible()).isTrue();
                assertThat(labels(fixture.view().panelHeader("sessions"))).contains("6");
                assertThat(labels(fixture.view())).contains("No hosts match");
            });
            fixture.filter("");fixture.size(260,395);
            var editor=(javax.swing.text.JTextComponent)fixture.component("sessions.search.input");
            var document=editor.getDocument();
            var footer=fixture.component("sessions.footer");
            var selected=AppContractTest.onEdtValue(()->((dev.jasper.ui.model.Nodes.ItemList)findNode(fixture.view().spec().root(),"sessions.rows")).selectedIds());
            for(String focused:List.of("sessions.rows","card.edit")) {
                Component focus=fixture.component(focused);
                var prior=KeyboardFocusManager.getCurrentKeyboardFocusManager();
                onEdt(()->KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager(){@Override public Component getFocusOwner(){return focus;}}));
                try {
                    fixture.theme(SurfaceFixtures.theme("light"));fixture.size(260,395);
                    onEdt(()->{
                        assertThat(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()).isSameAs(focus);
                        assertThat(((javax.swing.text.JTextComponent)fixture.component("sessions.search.input")).getDocument()).isSameAs(document);
                        assertThat(fixture.component("sessions.footer")).isSameAs(footer);
                        assertThat(((dev.jasper.ui.model.Nodes.ItemList)findNode(fixture.view().spec().root(),"sessions.rows")).selectedIds()).isEqualTo(selected);
                    });
                }finally{onEdt(()->KeyboardFocusManager.setCurrentKeyboardFocusManager(prior));}
                fixture.theme(SurfaceFixtures.theme("dark"));
            }
        }
    }
    @Test void narrowShortPanelKeepsActionsWholeAndFocusRevealsFooterControls() throws Exception {
        try(var fixture=new SessionsSurfaceFixture(home,SurfaceFixtures.theme("dark"))) {
            fixture.populate();
            for(int width:new int[]{260,180}) {
                fixture.size(width,65);
                onEdt(()->{
                    for(String id:List.of("card.edit","card.connect")) {
                        var button=(JComponent)fixture.component(id);
                        var b=bounds(button,fixture.view());
                        assertThat(b.width).isGreaterThanOrEqualTo(button.getPreferredSize().width);
                        assertThat(b.x).isGreaterThanOrEqualTo(0);
                        assertThat(b.x+b.width).isLessThanOrEqualTo(width);
                        fixture.view().reveal(id);
                        b=bounds(button,fixture.view());
                        assertThat(b.y).as("footer action reachable through viewport: %s",id).isGreaterThanOrEqualTo(0);
                        assertThat(b.y+b.height).isLessThanOrEqualTo(65);
                    }
                });
            }
        }
    }
    @Test void actualFocusOwnerChangeScrollsFooterAndRetainsReachableHeaderActions() throws Exception {
        class FocusOwner extends DefaultKeyboardFocusManager {
            private Component focused;
            @Override public Component getFocusOwner(){return focused;}
            void move(Component next){var previous=focused;focused=next;firePropertyChange("focusOwner",previous,next);}
        }
        var previous=KeyboardFocusManager.getCurrentKeyboardFocusManager();var manager=new FocusOwner();
        onEdt(()->KeyboardFocusManager.setCurrentKeyboardFocusManager(manager));
        try(var fixture=new SessionsSurfaceFixture(home,SurfaceFixtures.theme("dark"))) {
            fixture.populate();fixture.size(180,65);
            onEdt(()->{
                for(var action:buttons(fixture.view().panelHeader("sessions"))) {
                    var box=bounds(action,fixture.view());
                    assertThat(box.x).isGreaterThanOrEqualTo(0);assertThat(box.x+box.width).isLessThanOrEqualTo(180);
                    assertThat(box.y).isGreaterThanOrEqualTo(0);assertThat(box.y+box.height).isLessThanOrEqualTo(37);
                }
                for(String id:List.of("card.edit","card.connect")) {
                    var action=fixture.component(id);manager.move(action);
                    var box=bounds(action,fixture.view());
                    assertThat(box.y).isGreaterThanOrEqualTo(37);assertThat(box.y+box.height).isLessThanOrEqualTo(65);
                }
            });
        } finally {onEdt(()->KeyboardFocusManager.setCurrentKeyboardFocusManager(previous));}
    }
    @Test void enlargedNarrowHeaderMeasuresItsWrappedHostActionRows() throws Exception {
        var fonts=AppContractTest.onEdtValue(()->{
            var controller=dev.jasper.app.appearance.ThemeTestSupport.controller(true);
            controller.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new dev.jasper.app.config.UiFontConfig("system",20));return controller;
        });
        try(var fixture=new SessionsSurfaceFixture(home,SurfaceFixtures.theme("dark"))) {
            fixture.populate();fixture.size(180,600);
            onEdt(()->{
                var header=fixture.view().panelHeader("sessions");
                for(var action:buttons(header)) {
                    var box=bounds(action,header);
                    assertThat(box.y).as("header focus ring remains within root").isGreaterThanOrEqualTo(com.formdev.flatlaf.util.UIScale.scale(3));
                    assertThat(box.y+box.height).as("wrapped %s remains fully visible",action.getAccessibleContext().getAccessibleName()).isLessThanOrEqualTo(header.getHeight());
                }
            });
        } finally {onEdt(()->{fonts.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults());fonts.close();});}
    }
    private static Rectangle bounds(Component c,Container root){return SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),root);}
    private static dev.jasper.ui.model.Nodes.Node findNode(dev.jasper.ui.model.Nodes.Node node,String id){return StagedVaultHost.find(node,id);}
}
