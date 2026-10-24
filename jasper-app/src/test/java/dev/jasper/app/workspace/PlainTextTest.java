package dev.jasper.app.workspace;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/** Text from a plugin, a program or the user shows as typed: no component builds an HTML view for it. */
@ExtendWith(EdtTestExtension.class)
class PlainTextTest {
    private static final String MARKUP = "<html><b>x</b>";

    @BeforeEach void installModuleTheme() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> dev.jasper.app.appearance.ThemeTestSupport.install(true));
    }

    private static Action action(String name) {
        return new AbstractAction(name) { @Override public void actionPerformed(ActionEvent event) { } };
    }

    private static void literal(JComponent component) {
        assertThat(component.getClientProperty(BasicHTML.propertyKey)).as("%s builds no HTML view", component.getClass().getName()).isNull();
    }

    /** The tooltip {@code component} shows for {@code text}. */
    private static void literalTip(JComponent component, String text) {
        var tip = component.createToolTip();
        tip.setTipText(text);
        assertThat(tip.getTipText()).isEqualTo(text);
        literal(tip);
    }

    @Test void menusAndTheirItemsShowAContributedTitleAsText() {
        var selected = action(MARKUP);
        selected.putValue(Action.SELECTED_KEY, true);
        var items = List.<JMenuItem>of(PlainText.menu(MARKUP), PlainText.item(MARKUP), PlainText.item(action(MARKUP)),
            PlainText.checkBox(MARKUP, null, true), WindowToolbar.menuItem(action(MARKUP)), WindowToolbar.menuItem(selected),
            new MenuBarItems.Item(action(MARKUP)));
        for (var item : items) {
            assertThat(item.getText()).isEqualTo(MARKUP);
            literal(item);
        }
        assertThat(WindowToolbar.menuItem(selected)).isInstanceOf(JCheckBoxMenuItem.class);
        assertThat(WindowToolbar.menuItem(selected).isSelected()).as("the item follows its action's selection").isTrue();
    }

    @Test void buttonsShowAContributedLabelAndTooltipAsText() {
        var toolbar = new ClassicToolbarButton(action(MARKUP), MARKUP, false, null);
        assertThat(toolbar.getText()).isEqualTo(MARKUP);
        literal(toolbar);
        literalTip(toolbar, toolbar.getToolTipText());
        var button = PlainText.button();
        button.setText(MARKUP);
        literal(button);
        literalTip(button, MARKUP);
        literalTip(PlainText.toggle(), MARKUP);
        literalTip(new StripeLabels.Label(action(MARKUP), null, (label, graphics) -> { }), MARKUP);
    }

    @Test void labelsShowAPathAndAPluginDetailAsText() {
        var breadcrumbs = new PathBreadcrumbs();
        breadcrumbs.setLocation(new PathBreadcrumbs.Location(List.of(new PathBreadcrumbs.Crumb(MARKUP, MARKUP, null)), MARKUP));
        var crumb = labels(breadcrumbs).stream().filter(label -> MARKUP.equals(label.getText())).findFirst().orElseThrow();
        literal(crumb);
        literalTip(crumb, MARKUP);
        var header = new ToolWindowHeader("Sessions", JPopupMenu::new, () -> { });
        header.presentation(new dev.jasper.app.contributions.PanelHeaderState(MARKUP, false, List.of()));
        var detail = labels(header).stream().filter(label -> MARKUP.equals(label.getText())).findFirst().orElseThrow();
        literal(detail);
        literalTip(detail, detail.getToolTipText());
    }

    @Test void theStatusBarsDirectoryTooltipIsText() {
        var status = new WindowStatusBar();
        literalTip(status.left, MARKUP);
    }

    @Test void aRailToggleShowsAPanelTitleAsItsTooltip() {
        var stripe = new ToolStripe(dev.jasper.app.dock.Side.LEFT);
        stripe.render(List.of(new ToolStripe.Button("panel", MARKUP, null, dev.jasper.app.dock.Slot.LEFT_TOP, false,
            Optional.empty(), OptionalInt.empty())), List.of());
        var toggle = descendants(stripe).stream().filter(c -> c instanceof javax.swing.JToggleButton).map(JComponent.class::cast)
            .findFirst().orElseThrow();
        literalTip(toggle, toggle.getToolTipText());
    }

    private static List<JLabel> labels(Container root) {
        return descendants(root).stream().filter(JLabel.class::isInstance).map(JLabel.class::cast).toList();
    }

    private static List<Component> descendants(Container root) {
        var found = new ArrayList<Component>();
        for (Component child : root.getComponents()) {
            found.add(child);
            if (child instanceof Container container) found.addAll(descendants(container));
        }
        return found;
    }
}
