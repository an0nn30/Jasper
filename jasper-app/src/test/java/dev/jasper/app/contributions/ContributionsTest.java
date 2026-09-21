package dev.jasper.app.contributions;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ContributionsTest {
    private final Contributions model = new Contributions();
    private final List<Contributions.Kind> changes = new ArrayList<>();

    @Test void actionsAreUniqueObservableAndInvocable() {
        model.onChanged(changes::add);
        var seen = new ArrayList<Contributions.Invocation>();
        ActionEntry run = model.addAction("dev.x.run", "Run", null, List.of("go"), Optional.of("cmd+alt+j"), seen::add);
        assertThatIllegalArgumentException().isThrownBy(() ->
            model.addAction("dev.x.run", "Again", null, List.of(), Optional.empty(), invocation -> { }));
        var invocation = new Contributions.Invocation(UUID.randomUUID(), Optional.empty());
        run.invoke(invocation);
        run.setEnabled(false);
        run.invoke(invocation);
        run.setTitle("Run Now");
        assertThat(seen).containsExactly(invocation);
        assertThat(model.action("dev.x.run")).get().extracting(ActionEntry::title).isEqualTo("Run Now");
        assertThat(model.actions()).containsExactly(run);
        run.close();
        run.close();
        run.setTitle("ignored after close");
        assertThat(model.actions()).isEmpty();
        assertThat(changes).containsExactly(Contributions.Kind.ACTIONS, Contributions.Kind.ACTIONS,
            Contributions.Kind.ACTIONS, Contributions.Kind.ACTIONS);
    }

    @Test void toolbarMenusAndStatusKeepOrderAndNotify() {
        model.onChanged(changes::add);
        var first = model.addToolbar(new ToolbarEntry.Button("dev.x.a"));
        model.addToolbar(new ToolbarEntry.Dropdown(new javax.swing.ImageIcon(), "Hosts", List.of("dev.x.a")));
        first.close();
        assertThat(model.toolbar()).singleElement().isInstanceOf(ToolbarEntry.Dropdown.class);

        MenuSection view = model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.VIEW));
        MenuSection top = model.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Tool"));
        assertThatIllegalArgumentException().isThrownBy(() -> model.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Again")));
        view.set(List.of(new MenuEntry.Item("dev.x.a"), new MenuEntry.Separator(),
            new MenuEntry.Submenu("More", List.of(new MenuEntry.Item("dev.x.a")))));
        assertThat(model.menus()).containsExactly(view, top);
        assertThat(view.entries()).hasSize(3);
        top.close();
        assertThat(model.menus()).containsExactly(view);
        model.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Reused after close"));

        StatusEntry late = model.addStatus("dev.x.late", false, 20);
        StatusEntry early = model.addStatus("dev.x.early", false, 10);
        assertThatIllegalArgumentException().isThrownBy(() -> model.addStatus("dev.x.early", true, 0));
        early.setText("line one\nline two");
        early.setActionId("dev.x.a");
        early.setVisible(false);
        assertThat(model.status()).containsExactly(early, late);
        assertThat(early.text()).isEqualTo("line one line two");
        assertThat(early.visible()).isFalse();
        early.close();
        early.setText("ignored");
        assertThat(model.status()).containsExactly(late);
        assertThat(changes).contains(Contributions.Kind.TOOLBAR, Contributions.Kind.MENUS, Contributions.Kind.STATUS)
            .doesNotContain(Contributions.Kind.ACTIONS);
    }

    @Test void aFailingListenerDoesNotStopTheOthersAndClosedListenersAreSilent() {
        var quiet = model.onChanged(changes::add);
        model.onChanged(kind -> { throw new IllegalStateException("listener failure"); });
        model.onChanged(changes::add);
        assertThatCode(() -> model.addToolbar(new ToolbarEntry.Button("dev.x.a"))).doesNotThrowAnyException();
        assertThat(changes).hasSize(2);
        quiet.close();
        model.addToolbar(new ToolbarEntry.Button("dev.x.a"));
        assertThat(changes).hasSize(3);
    }
}
