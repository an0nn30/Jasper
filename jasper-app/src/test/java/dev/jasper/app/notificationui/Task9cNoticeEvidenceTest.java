package dev.jasper.app.notificationui;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.notifications.*;
import dev.jasper.app.testsupport.*;
import dev.jasper.ui.theme.ThemeRoute;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** All real notice severities and history/error owners; no OS notification consumer is attached. */
class Task9cNoticeEvidenceTest {
    @Test void noticeAndHistoryStatesUseInertActionsAndActualControllers() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var themes=ThemeTestSupport.controller(true);
            try {
                for (boolean themeDark : new boolean[]{true, false})for(float font:List.of(13f,20f,32f)) {
                    themes.configure(themeDark, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new UiFontConfig("system",font));var kit=themes.appearance().context(ThemeRoute.CHROME);
                    String family=(themeDark?"dark":"light")+"-"+font;
                    var clock=Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"),ZoneOffset.UTC);
                    var center=new NotificationCenter(Runnable::run,clock,()->Delivery.Presence.FOCUSED);
                    var group=new NotificationGroup("fixture.cards","Synthetic fixture","jasper",DisplayMode.CARD);center.register(group);
                    var site=new PanelSite(UUID.randomUUID(),()->{},()->{},()->true);
                    var history=new HistoryPanel(kit,center,site,false,"Notifications");
                    try {
                        site.notifyVisibility(true);save(history,family,"history-empty",360,420);
                        for(var severity:Severity.values()) {
                            var request=NotificationRequest.of(group.id(),severity,"A long literal "+severity+" notification with repeated sightings")
                                .withContent("A synthetic operation needs attention. <html> remains literal.\nThe second line and all action labels remain readable.")
                                .withActivate(()->{});
                            for(int i=0;i<6;i++)request=request.withAction(NotificationAction.of("Review operation "+(i+1),()->{}));
                            var id=center.post(request).orElseThrow();center.post(request);
                            try(var card=new NotificationCard(kit,center,center.find(id).orElseThrow(),false, id2 -> { })) {
                                save(card,family,severity+"-auto-hide",360,card.getPreferredSize().height);
                                assertThat(card.contains(0,0)).as("notice retains prior rectangular hit policy").isTrue();
                                card.setHovered(true);save(card,family,severity+"-hover-close",360,card.getPreferredSize().height);
                                var menu=KitTestSupport.require(card.moreView(),NotificationCard.MORE,JComponent.class).getComponentPopupMenu();
                                save(menu,family,severity+"-more-popup",menu.getPreferredSize().width,menu.getPreferredSize().height);
                            }
                            try(var sticky=new NotificationCard(kit,center,center.find(id).orElseThrow(),true, id2 -> { })) {
                                save(sticky,family,severity+"-sticky",360,sticky.getPreferredSize().height);
                                if(severity==Severity.ERROR)SurfaceEvidence.save(Path.of("build/theme-surfaces9c/notices",family,"ERROR-sticky-1x.png"),sticky,360,sticky.getPreferredSize().height,1);
                            }
                        }
                        save(history,family,"history-new",360,560);
                        center.markAllRead();site.notifyVisibility(false);site.notifyVisibility(true);
                        save(history,family,"history-earlier",360,420);
                        for(var entry:center.history())center.expire(entry.id());
                        save(history,family,"history-expired",300,420);
                        assertThat(history.clearAllButton().isFocusable()).isTrue();
                    } finally {site.notifyClosed();}
                    var incidents=new Incidents(clock);
                    var errors=new ErrorsView(themes.appearance().context(ThemeRoute.WORKSPACE),incidents,new ErrorsView.Hooks(id->"Example plugin",id->"1.0.0",
                        new IncidentReport.Environment("task9c","Headless","JBR25"),text->{},id->{},()->{}),ZoneOffset.UTC);
                    try {
                        save(errors,family,"errors-empty",900,540);
                        var failure=new IllegalStateException("Literal <html> problem\nA second line remains selectable");
                        var incident=incidents.record("dev.fixture.plugin","Fixture operation","FixtureThread",Capture.of(failure));
                        incidents.record("dev.fixture.plugin","Fixture operation","FixtureThread",Capture.of(failure));
                        errors.select(incident.id());save(errors,family,"errors-plugin-repeated",900,540);save(errors,family,"errors-plugin-narrow-short",600,320);
                        var detail=KitTestSupport.require(errors,"errors.detail.body-scroll",JScrollPane.class);
                        detail.getVerticalScrollBar().setValue(Integer.MAX_VALUE);
                        save(errors,family,"errors-plugin-narrow-short-scrolled",600,320);
                        var traceViewport=(JViewport)errors.traceArea().getParent();
                        detail.getVerticalScrollBar().setValue(SwingUtilities.convertPoint(traceViewport,0,0,detail.getViewport().getView()).y);
                        save(errors,family,"errors-plugin-narrow-trace",600,320);
                        var core=incidents.record("jasper","Core fixture","FixtureThread",Capture.of(new IllegalArgumentException("Core incident")));
                        errors.select(core.id());save(errors,family,"errors-core",900,540);
                        incidents.clear();save(errors,family,"errors-cleared",900,540);
                    } finally {errors.close();}
                }
            } finally {themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());themes.close();}
        });
    }
    private static void save(JComponent panel,String theme,String state,int width,int height) {
        SurfaceEvidence.save(Path.of("build/theme-surfaces9c/notices",theme,state+".png"),panel,width,height,2);
    }
}
