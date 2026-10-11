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
        try(var fixture=new SessionsSurfaceFixture(home,SurfaceFixtures.theme("sessions-calibration"))) {
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
                fixture.theme(SurfaceFixtures.theme("sessions-calibration"));
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
            var controller=new dev.jasper.app.appearance.ThemeController();
            controller.configure(dev.jasper.app.appearance.Theme.DARK,new dev.jasper.app.config.UiFontConfig("system",20));return controller;
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
        } finally {onEdt(()->{fonts.configure(dev.jasper.app.appearance.Theme.DARK,dev.jasper.app.config.UiFontConfig.defaults());fonts.close();});}
    }
    private static Rectangle bounds(Component c,Container root){return SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),root);}
    private static dev.jasper.ui.model.Nodes.Node findNode(dev.jasper.ui.model.Nodes.Node node,String id){return StagedVaultHost.find(node,id);}
}
