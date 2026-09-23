package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TransfersPanelTest {
    @Test void unknownScanIsIndeterminateAndLargeRemainingCountsDoNotOverflow() {
        var id=UUID.randomUUID();long bytes=5L*1024*1024*1024;
        var scanning=new TransferJob(id,"local","host",TransferState.SCANNING,TransferJob.Intent.RUN,0,1,bytes,0,0,0,0,false,"",0);
        var presentation=new TransferPresentation();
        var first=presentation.update(new TransferCoordinator.Snapshot(List.of(scanning),List.of(),0),1_000_000_000L);
        assertThat(first.fraction()).isEmpty();assertThat(first.text()).contains("Scanning");
        var running=new TransferJob(id,"local","host",TransferState.RUNNING,TransferJob.Intent.RUN,0,1,bytes,1024,0,0,0,true,"",0);
        var next=presentation.update(new TransferCoordinator.Snapshot(List.of(running),List.of(),1),2_000_000_000L);
        assertThat(next.detail()).contains("5.0 GiB");assertThat(next.fraction().orElseThrow()).isBetween(0.0,1.0);
    }
    @Test void selectionSurvivesProgressRefreshAndNamesStayPlainText() throws Exception {
        SwingUtilities.invokeAndWait(()-> {
            var panel=new TransfersPanel(name->new dev.jasper.sdk.testing.FakeNamedIcon(name,false));var id=UUID.randomUUID();
            var job=new TransferJob(id,"<html>source","host",TransferState.PAUSED,TransferJob.Intent.PAUSE,0,1,10,5,0,0,0,true,"",0);
            panel.jobs(List.of(job),0);assertThat(panel.selectedJob()).contains(id);panel.jobs(List.of(job),0);assertThat(panel.selectedJob()).contains(id);
            var label=(JLabel)panel.jobsTable().prepareRenderer(panel.jobsTable().getCellRenderer(0,0),0,0);assertThat(label.getClientProperty("html.disable")).isEqualTo(true);
        });
    }
    @Test void conflictScopeLabelFitsAtNormalPanelWidth() throws Exception {
        SwingUtilities.invokeAndWait(()-> {
            var panel=new TransfersPanel(name->new dev.jasper.sdk.testing.FakeNamedIcon(name,false));
            panel.setSize(760,320);layout(panel);
            var remaining=findCheckbox(panel);
            assertThat(remaining).isNotNull();
            assertThat(remaining.getWidth()).isGreaterThanOrEqualTo(remaining.getPreferredSize().width);
        });
    }
    private static void layout(java.awt.Container container) {
        container.doLayout();for(var child:container.getComponents()) if(child instanceof java.awt.Container nested) layout(nested);
    }
    private static JCheckBox findCheckbox(java.awt.Container container) {
        for(var child:container.getComponents()) { if(child instanceof JCheckBox checkbox)return checkbox;if(child instanceof java.awt.Container nested) { var found=findCheckbox(nested);if(found!=null)return found; } }
        return null;
    }

}
