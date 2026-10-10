package dev.jasper.app.themesui;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.themeimport.*;
import dev.jasper.ui.themeimport.*;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;
@ExtendWith(EdtTestExtension.class)
class ThemeImportViewTest {
    private static JComboBox<?> decision(ThemeImportView view, String id) { return KitTestSupport.require(view.view, "decision:" + id + ".input", JComboBox.class); }
    private static void pick(JComboBox<?> box, String label) {
        for (int i = 0; i < box.getItemCount(); i++) if (((dev.jasper.ui.model.Option) box.getItemAt(i)).label().equals(label)) { box.setSelectedIndex(i); return; }
        throw new AssertionError("No choice " + label);
    }
    @Test void catalogAdvisoryAppearsOnceAlongsideIndependentProviderWarnings() {
        var conversion = new ConversionResult(Theme.DARK.source().document(), List.of("provider warning"), "provider");
        var one = new ThemeImporter.Item("one", conversion, Optional.empty(), Optional.empty(), false);
        var two = new ThemeImporter.Item("two", conversion, Optional.empty(), Optional.empty(), false);
        var view = new ThemeImportView(KitTestSupport.context(),
            new ThemeImporter.ImportPlan(List.of(one, two), List.of(), List.of("catalog appearance advisory")), choices -> {}, () -> {});
        try {
            String text = KitTestSupport.require(view.view, "details", JLabel.class).getText();
            assertThat(text.split("catalog appearance advisory", -1)).hasSize(2);
            assertThat(text).contains("provider warning");
            assertThat(KitTestSupport.require(view.view, "import", JButton.class).isEnabled()).isTrue();
        } finally { view.view.close(); }
    }
    @Test void distinctProviderDecisionsAreLiteralAndDefaultsSafe() {
        var conversion=new ConversionResult(Theme.DARK.source().document(),List.of("<html>console fallback"),"provider");
        var old=new ThemeImporter.Item("old",conversion,Optional.of(Path.of("mine.toml")),Optional.of("hash"),false);
        var fresh=new ThemeImporter.Item("fresh",conversion,Optional.empty(),Optional.empty(),true);
        var result=new AtomicReference<Map<String,ThemeImporter.ConflictChoice>>();
        var view=new ThemeImportView(KitTestSupport.context(),new ThemeImporter.ImportPlan(List.of(old,fresh),List.of(new ThemeConverter.Failure("bad","broken"))),result::set,()->{});
        assertThat(view.choices()).containsEntry("old",ThemeImporter.ConflictChoice.SKIP).containsEntry("fresh",ThemeImporter.ConflictChoice.KEEP_BOTH);
        assertThat(decision(view,"old").getItemCount()).as("a name already installed may be replaced").isEqualTo(3);
        assertThat(decision(view,"fresh").getItemCount()).as("a new name cannot replace anything").isEqualTo(2);
        assertThat(view.view.spec().root()).isInstanceOfSatisfying(dev.jasper.ui.model.Nodes.Form.class,
            form -> assertThat(form.kind()).isEqualTo(dev.jasper.ui.model.Nodes.FormKind.SHEET));
        pick(decision(view,"old"),"Replace"); pick(decision(view,"fresh"),"Skip");
        var commit=KitTestSupport.require(view.view,"import",JButton.class);
        commit.doClick();
        assertThat(result.get()).containsEntry("old",ThemeImporter.ConflictChoice.REPLACE).containsEntry("fresh",ThemeImporter.ConflictChoice.SKIP);
        assertThat(commit.isEnabled()).as("one commit").isFalse();
        var details=KitTestSupport.require(view.view,"details",JLabel.class);
        assertThat(details.getText()).contains("<html>console fallback", "bad", "broken");
        assertThat(details.getClientProperty("html")).isNull();
        assertThat(details.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
        var empty=new ThemeImportView(KitTestSupport.context(),new ThemeImporter.ImportPlan(List.of(),List.of(new ThemeConverter.Failure("bad","broken"))),m->fail("commit"),()->{});
        var none=KitTestSupport.require(empty.view,"import",JButton.class);
        assertThat(none.isEnabled()).isFalse();
        none.doClick();
        view.view.close(); empty.view.close();
    }
    /** Plan 2 Task 6c (ruling G4): the warning and failure details flow over lines inside the sheet, keeping their line breaks. */
    @Test void longDetailsFlowInsideTheSheetAboveTheActions() {
        var warnings=new ArrayList<String>();
        for (int i=0;i<6;i++) warnings.add("The provider color "+i+" has no counterpart in this theme and falls back to the console default");
        var conversion=new ConversionResult(Theme.DARK.source().document(),warnings,"provider");
        var item=new ThemeImporter.Item("fresh",conversion,Optional.empty(),Optional.empty(),true);
        var view=new ThemeImportView(KitTestSupport.context(),new ThemeImporter.ImportPlan(List.of(item),List.of()),m->{},()->{});
        view.view.setSize(view.view.getPreferredSize());
        for (int pass=0;pass<3;pass++) KitTestSupport.layout(view.view);
        var details=KitTestSupport.require(view.view,"details",JLabel.class);
        int line=Math.max(details.getFontMetrics(details.getFont()).getHeight(), Math.round(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.DESCRIPTION_LINE_HEIGHT)*dev.jasper.app.platform.ThemeZone.font(dev.jasper.ui.metrics.Typography.Role.BODY).getSize2D()/13f));
        int columns=details.getFontMetrics(details.getFont()).charWidth('m')*dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.TEXT_FLOW_COLUMNS);
        assertThat(details.getPreferredSize().width).as("no wider than the flow cap").isLessThanOrEqualTo(columns);
        assertThat(details.getHeight()).as("at least one line per warning").isGreaterThanOrEqualTo(warnings.size()*line);
        assertThat(details.getHeight()%line).isZero();
        var commit=KitTestSupport.require(view.view,"import",JButton.class);
        var detailsBottom=javax.swing.SwingUtilities.convertRectangle(details.getParent(),details.getBounds(),view.view);
        var commitTop=javax.swing.SwingUtilities.convertRectangle(commit.getParent(),commit.getBounds(),view.view);
        assertThat(commitTop.y).as("the actions sit below the details").isGreaterThanOrEqualTo(detailsBottom.y+detailsBottom.height);
        view.view.close();
    }
}
