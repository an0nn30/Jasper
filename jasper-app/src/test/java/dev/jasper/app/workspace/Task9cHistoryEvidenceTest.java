package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.dock.*;
import dev.jasper.app.notifications.*;
import dev.jasper.app.notificationui.HistoryPanel;
import dev.jasper.app.testsupport.SurfaceEvidence;
import dev.jasper.ui.theme.ThemeRoute;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Actual tool-window host supplies the transparent history owner's receiving backdrop. No native window. */
class Task9cHistoryEvidenceTest {
    @Test void historyUsesTheActualToolWindowSurface() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes=ThemeTestSupport.controller(true);
            try {
                for (boolean themeDark : new boolean[]{true, false}) for(float font:List.of(13f,20f,32f)) {
                    themes.configure(themeDark, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new UiFontConfig("system",font));
                    var center=new NotificationCenter(Runnable::run,Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"),ZoneOffset.UTC),()->Delivery.Presence.FOCUSED);
                    var group=new NotificationGroup("fixture.history","Synthetic history","jasper",DisplayMode.CARD);center.register(group);
                    var site=new PanelSite(UUID.randomUUID(),()->{},()->{},()->true);
                    var history=new HistoryPanel(themes.appearance().context(ThemeRoute.CHROME),center,site,false);
                    var area=new ToolWindowArea(new JPanel(),new DockState());
                    try {
                        DockTestSupport.show(area,Slot.RIGHT_TOP,history,360);area.setContent(DockTestSupport.id(Slot.RIGHT_TOP),"Notifications",history);area.sync();site.notifyVisibility(true);
                        String family=(themeDark?"dark":"light")+"-"+font;
                        SurfaceEvidence.save(Path.of("build/theme-surfaces9c/history-host",family,"empty.png"),area,1000,600,2);
                        center.post(NotificationRequest.of(group.id(),Severity.WARNING,"A long literal history notification")
                            .withContent("Synthetic content with <html> literal text and a second line.\nExisting tool-window surface behind this transparent owner.")
                            .withAction(NotificationAction.of("Review operation",()->{})).withAction(NotificationAction.of("Open diagnostic details",()->{})));
                        SurfaceEvidence.save(Path.of("build/theme-surfaces9c/history-host",family,"new.png"),area,1000,600,2);
                        for(var child:history.getComponents())if(child instanceof JScrollPane scroll)scroll.getVerticalScrollBar().setValue(Integer.MAX_VALUE);
                        SurfaceEvidence.save(Path.of("build/theme-surfaces9c/history-host",family,"new-scrolled.png"),area,1000,600,2);
                        center.markAllRead();site.notifyVisibility(false);site.notifyVisibility(true);
                        SurfaceEvidence.save(Path.of("build/theme-surfaces9c/history-host",family,"earlier.png"),area,1000,600,2);
                    } finally {site.notifyClosed();area.close();}
                }
            } finally {themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());themes.close();}
        });
    }
}
