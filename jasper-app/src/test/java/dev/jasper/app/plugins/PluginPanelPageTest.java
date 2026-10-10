package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.settings.TomlDocument;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginPanelPageTest {
    private static final String ID = "test.panel";
    @TempDir Path data;
    private final List<ConfigDiagnostic> diagnostics = new CopyOnWriteArrayList<>();
    private final int[] changes = {0};
    private PluginHost host;
    private final PageContext context = new PageContext(null, id -> { }, Runnable::run, (title, initial) -> Optional.empty(),
        () -> changes[0]++, () -> null, dev.jasper.app.testsupport.KitTestSupport::context);

    @BeforeEach void open() {
        host = AppContractTest.host(data, Duration.ofMillis(200), onEdtValue(Contributions::new), new AtomicBoolean(true),
            onEdtValue(AppContractTest::headlessWindows), onEdtValue(TerminalRegistry::new), new CopyOnWriteArrayList<>(),
            new CopyOnWriteArrayList<>(), diagnostics);
    }

    @AfterEach void stop() {
        var pending = new AtomicReference<List<java.util.concurrent.CompletableFuture<?>>>(List.of());
        onEdt(() -> pending.set(host.stop()));
        java.util.concurrent.CompletableFuture.allOf(pending.get().toArray(java.util.concurrent.CompletableFuture[]::new)).join();
    }

    @Test void kitResetRemovesSavedOverrideAndReloadRebasesCleanAndPendingRows() throws Exception {
        var view=new AtomicReference<HostedKitView>();var rows=new AtomicReference<SettingsPanelHost>();
        var size=new AtomicReference<Setting<Integer>>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(ID,"Panel","1.0.0",Set.of()),Set.of(),Set.of(),Set.of(),getClass().getClassLoader(),() -> context -> {
            size.set(context.settings().declare(SettingSpec.of("size",SettingKind.integer(1,10),4).label("Size")));
            context.settings().page((SettingsPanel) h -> {rows.set(h);var made=(HostedKitView)context.kit().create(new dev.jasper.sdk.ui.ViewSpec(h.row(size.get())),e -> {});view.set(made);return made;});
        })));
        onEdtValue(() -> size.get().set(7)).get(5,TimeUnit.SECONDS);
        var page=onEdtValue(() -> (PluginPanelPage)host.settingsPages(context).getFirst());
        onEdt(() -> {page.component();view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Action("settings:size:reset"));assertThat(rows.get().pending(size.get())).isEqualTo(4);});
        onEdtValue(page::apply).get(5,TimeUnit.SECONDS);
        var file=host.environment.settingsFiles().file(ID);
        assertThat(org.tomlj.Toml.parse(Files.readString(file)).contains("size")).isFalse();
        // Ordinary stage(default) remains an explicit override when a value was saved.
        onEdtValue(() -> size.get().set(7)).get(5,TimeUnit.SECONDS);
        onEdt(() -> rows.get().stage(size.get(),4));
        onEdtValue(page::apply).get(5,TimeUnit.SECONDS);
        assertThat(org.tomlj.Toml.parse(Files.readString(file)).getLong("size")).isEqualTo(4);
        onEdt(() -> view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Action("settings:size:reset")));
        onEdtValue(page::apply).get(5,TimeUnit.SECONDS);
        assertThat(org.tomlj.Toml.parse(Files.readString(file)).contains("size")).isFalse();
        onEdtValue(() -> size.get().set(6)).get(5,TimeUnit.SECONDS);
        onEdt(() -> {assertThat(displayedSize(view.get())).isEqualTo("6");rows.get().stage(size.get(),8);});
        onEdtValue(() -> size.get().set(5)).get(5,TimeUnit.SECONDS);
        onEdt(() -> {assertThat(displayedSize(view.get())).isEqualTo("8");assertThat(page.modified()).isTrue();});
        onEdtValue(() -> size.get().set(8)).get(5,TimeUnit.SECONDS);
        onEdt(() -> {assertThat(displayedSize(view.get())).isEqualTo("8");assertThat(page.modified()).isFalse();page.dispose();});
        onEdtValue(() -> size.get().set(9)).get(5,TimeUnit.SECONDS);
        onEdt(() -> assertThatThrownBy(view.get()::spec).isInstanceOf(IllegalStateException.class));
    }
    @Test void integerHostRowStepsAcrossItsRangeAndKeepsSearchHighlightThroughDraftRefresh() throws Exception {
        var view = new AtomicReference<HostedKitView>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(ID, "Panel", "1.0.0", Set.of()), Set.of(), Set.of(), Set.of(),
            getClass().getClassLoader(), () -> plugin -> {
                var delay = plugin.settings().declare(SettingSpec.of("delay", SettingKind.integer(0, 5000), 300)
                    .label("Step delay").description("Milliseconds between steps"));
                plugin.settings().page((SettingsPanel) rows -> {
                    var made = (HostedKitView) plugin.kit().create(new dev.jasper.sdk.ui.ViewSpec(rows.row(delay)), event -> { });
                    view.set(made);
                    return made;
                });
            })));
        var page = onEdtValue(() -> (PluginPanelPage) host.settingsPages(context).getFirst());
        onEdt(() -> {
            page.component();
            var row = (dev.jasper.sdk.ui.Nodes.SettingRow) view.get().spec().root();
            var field = (dev.jasper.sdk.ui.Nodes.Field) ((dev.jasper.sdk.ui.Nodes.Stack) row.control()).children().getFirst();
            assertThat(field.kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.FieldKind.NUMBER);
            assertThat(field.numeric()).contains(new dev.jasper.sdk.ui.Nodes.Numeric(0, 5000, 1, 0));
            assertThat(field.value()).isEqualTo("300");
            page.highlight("step delay");
            var input = dev.jasper.app.testsupport.KitTestSupport.require(view.get().component(), "settings:delay.input", javax.swing.JTextField.class);
            var binding = input.getInputMap(JComponent.WHEN_FOCUSED).get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0));
            input.getActionMap().get(binding).actionPerformed(new java.awt.event.ActionEvent(input, 0, ""));
            assertThat(input.getText()).isEqualTo("301");
            assertThat(((dev.jasper.sdk.ui.Nodes.SettingRow) view.get().spec().root()).highlighted()).isTrue();
            assertThat(dev.jasper.app.testsupport.KitTestSupport.require(view.get().component(), "settings:delay:row", JComponent.class)
                .getAccessibleContext().getAccessibleDescription()).isEqualTo("Matches search");
            input.setText("0"); assertThat(((dev.jasper.sdk.ui.Nodes.Field) ((dev.jasper.sdk.ui.Nodes.Stack)
                ((dev.jasper.sdk.ui.Nodes.SettingRow) view.get().spec().root()).control()).children().getFirst()).value()).isEqualTo("0");
            input.setText("5000");
            input.setText("1234");
            page.highlight("milliseconds");
            assertThat(((dev.jasper.sdk.ui.Nodes.SettingRow) view.get().spec().root()).highlighted()).isTrue();
            page.highlight("");
            assertThat(((dev.jasper.sdk.ui.Nodes.SettingRow) view.get().spec().root()).highlighted()).isFalse();
            assertThat(page.modified()).isTrue();
        });
        onEdtValue(page::apply).get(5, TimeUnit.SECONDS);
        assertThat(org.tomlj.Toml.parse(Files.readString(host.environment.settingsFiles().file(ID))).getLong("delay")).isEqualTo(1234);
        onEdt(page::dispose);
    }
    @Test void kitPanelRowsKeepTheirSearchHighlightThroughTheHostsRowRewriting() throws Exception {
        var view=new AtomicReference<HostedKitView>();var size=new AtomicReference<Setting<Integer>>();var rows=new AtomicReference<SettingsPanelHost>();
        java.util.function.Function<SettingsPanelHost,dev.jasper.sdk.ui.ViewSpec> spec=h -> {
            var hostRow=(dev.jasper.sdk.ui.Nodes.SettingRow)h.row(size.get());
            var ready=dev.jasper.sdk.ui.ControlState.READY;
            return new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Card("card",ready,dev.jasper.sdk.ui.Nodes.CardKind.SETTINGS,List.of(
                new dev.jasper.sdk.ui.Nodes.SettingRow(hostRow.id(),hostRow.state(),hostRow.title(),hostRow.description(),hostRow.control(),hostRow.dependent(),true),
                new dev.jasper.sdk.ui.Nodes.SettingRow("own",ready,"Own","",new dev.jasper.sdk.ui.Nodes.Toggle("own-toggle",ready,"Own",true,"own-toggle",false),false,true),
                new dev.jasper.sdk.ui.Nodes.FormRow("form",ready,"Form","",new dev.jasper.sdk.ui.Nodes.Text("form-text",ready,"x",dev.jasper.sdk.ui.Nodes.TextRole.BODY),Optional.empty(),true))));
        };
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(ID,"Panel","1.0.0",Set.of()),Set.of(),Set.of(),Set.of(),getClass().getClassLoader(),() -> context -> {
            size.set(context.settings().declare(SettingSpec.of("size",SettingKind.integer(1,10),4).label("Size")));
            context.settings().page((SettingsPanel) h -> {rows.set(h);var made=(HostedKitView)context.kit().create(spec.apply(h),e -> {});view.set(made);return made;});
        })));
        var page=onEdtValue(() -> (PluginPanelPage)host.settingsPages(context).getFirst());
        onEdt(() -> {
            page.component();
            view.get().update(spec.apply(rows.get()));
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Edited("settings:size","5"));
            var card=(dev.jasper.sdk.ui.Nodes.Card)view.get().spec().root();
            assertThat(card.children()).as("plugin and host rows keep the highlight the plugin asked for").allSatisfy(node -> assertThat(
                node instanceof dev.jasper.sdk.ui.Nodes.SettingRow row ? row.highlighted() : ((dev.jasper.sdk.ui.Nodes.FormRow)node).highlighted()).isTrue());
            for(String id:List.of("settings:size:row","own","form"))
                assertThat(dev.jasper.app.testsupport.KitTestSupport.require(view.get().component(),id,JComponent.class).getAccessibleContext().getAccessibleDescription())
                    .as(id).isEqualTo("Matches search");
            page.dispose();
        });
    }
    private static String displayedSize(HostedKitView view) {
        var row=(dev.jasper.sdk.ui.Nodes.SettingRow)view.spec().root();
        return ((dev.jasper.sdk.ui.Nodes.Field)((dev.jasper.sdk.ui.Nodes.Stack)row.control()).children().getFirst()).value();
    }

    @Test void kitPageRowsStageValidateResetApplyCancelAndOwnerStop() throws Exception {
        var view=new AtomicReference<HostedKitView>();
        var pageHost=new AtomicReference<SettingsPanelHost>();
        var setting=new AtomicReference<Setting<Integer>>();
        var callbacks=new ArrayList<String>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(ID,"Panel","1.0.0",Set.of()),Set.of(),Set.of(),Set.of(),getClass().getClassLoader(),() -> started -> {
            setting.set(started.settings().declare(SettingSpec.of("size",SettingKind.integer(1,10),4).label("Size")));
            started.settings().page(new SettingsPanel(){
                @Override public dev.jasper.sdk.ui.UiView create(SettingsPanelHost rows){
                    pageHost.set(rows);
                    var result=(HostedKitView)started.kit().create(new dev.jasper.sdk.ui.ViewSpec(rows.row(setting.get())),event -> callbacks.add(event.id()));
                    view.set(result);return result;
                }
                @Override public void apply(){callbacks.add("apply");}
                @Override public void reset(){callbacks.add("reset");}
                @Override public void dispose(){callbacks.add("dispose");}
            });
        })));
        var page=onEdtValue(() -> (PluginPanelPage)host.settingsPages(context).getFirst());
        onEdt(() -> {
            page.component();
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Edited("settings:size","99"));
            assertThat(page.valid()).isFalse();assertThat(pageHost.get().pending(setting.get())).isEqualTo(4);
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Edited("settings:size","7"));
            assertThat(page.valid()).isTrue();assertThat(page.modified()).isTrue();assertThat(setting.get().get()).isEqualTo(4);
            assertThat(pageHost.get().pending(setting.get())).isEqualTo(7);
            assertThat(callbacks).isEmpty();
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Action("plugin-action"));assertThat(callbacks).containsExactly("plugin-action");
        });
        onEdtValue(page::apply).get(5,TimeUnit.SECONDS);
        onEdt(() -> {
            assertThat(setting.get().get()).isEqualTo(7);
            // A programmatic stage must update an already mounted row, including clearing a previous edit buffer.
            pageHost.get().stage(setting.get(),9);
            var row=(dev.jasper.sdk.ui.Nodes.SettingRow)view.get().spec().root();
            var field=(dev.jasper.sdk.ui.Nodes.Field)((dev.jasper.sdk.ui.Nodes.Stack)row.control()).children().getFirst();
            assertThat(field.value()).isEqualTo("9");
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Action("settings:size:reset"));
            assertThat(pageHost.get().pending(setting.get())).isEqualTo(4);
            page.dispose();page.dispose();
            assertThat(setting.get().get()).isEqualTo(7);
            assertThat(callbacks).containsExactly("plugin-action","apply","reset","dispose");
            assertThatThrownBy(view.get()::spec).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> pageHost.get().stage(setting.get(),8)).isInstanceOf(IllegalStateException.class);
        });
        var reopened=onEdtValue(() -> (PluginPanelPage)host.settingsPages(context).getFirst());
        onEdt(() -> {reopened.component();host.stop();assertThatThrownBy(view.get()::spec).isInstanceOf(IllegalStateException.class);});
    }

    private dev.jasper.sdk.ui.UiKit pluginKit;
    private void start(Consumer<PluginSettings> declare) {
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(ID, "Panel", "1.0.0", Set.of()), Set.of(), Set.of(), Set.of(),
            PluginPanelPageTest.class.getClassLoader(), () -> started -> {pluginKit=started.kit();declare.accept(started.settings());})));
        assertThat(host.active(ID)).isTrue();
    }

    /** A plugin page with one standard row, one control of its own for {@code size}, and its own state. */
    private static final class Panel implements SettingsPanel {
        final dev.jasper.sdk.ui.UiKit kit;
        final Setting<Boolean> vim;
        final Setting<Integer> size;
        final List<String> calls = new ArrayList<>();
        boolean ownModified;
        SettingsPanelHost host;
        Panel(Setting<Boolean> vim, Setting<Integer> size, dev.jasper.sdk.ui.UiKit kit) { this.vim = vim; this.size = size; this.kit=kit; }
        @Override public dev.jasper.sdk.ui.UiView create(SettingsPanelHost host) {
            this.host=host;
            return kit.create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("page",dev.jasper.sdk.ui.ControlState.READY,dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL,List.of(host.row(vim),new dev.jasper.sdk.ui.Nodes.Text("size",dev.jasper.sdk.ui.ControlState.READY,"size "+host.pending(size),dev.jasper.sdk.ui.Nodes.TextRole.BODY)))),e -> {});
        }
        @Override public List<String> keywords() { return List.of("panel", "custom"); }
        @Override public void highlight(String query) { calls.add("highlight " + query); }
        @Override public boolean modified() { return ownModified; }
        @Override public void apply() { calls.add("apply"); ownModified = false; }
        @Override public void reset() { calls.add("reset"); ownModified = false; }
        @Override public void dispose() { calls.add("dispose"); }
    }

    private Panel startWithPanel() {
        var panel = new AtomicReference<Panel>();
        start(settings -> {
            var vim = settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode").keywords("modal"));
            var size = settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size"));
            panel.set(new Panel(vim, size, pluginKit));
            settings.page(panel.get());
        });
        return panel.get();
    }

    @Test void theOwnPageGetsRowsStagesValuesAndAppliesBothKindsOfState() throws Exception {
        Files.createDirectories(data.resolve(ID));
        Files.writeString(data.resolve(ID).resolve(ID + ".toml"), "font_size = 13\n");
        var panel = startWithPanel();
        var pages = onEdtValue(() -> host.settingsPages(context));
        assertThat(pages).singleElement().isInstanceOf(PluginPanelPage.class);
        SettingsPage page = pages.getFirst();
        onEdt(() -> {
            assertThat(page.id()).isEqualTo("plugin." + ID);
            assertThat(page.parent()).contains("tools");
            assertThat(page.searchTerms()).contains("Panel", "Vim mode", "modal", "panel", "custom");
            page.component();
            assertThat(panel.host).isNotNull();
            panel.host.stage(panel.size, 20);
            assertThat(panel.host.pending(panel.size)).isEqualTo(20);
            assertThatIllegalArgumentException().isThrownBy(() -> panel.host.stage(panel.size, 99));
            assertThat(page.modified()).isTrue();
            panel.ownModified = true;
            page.highlight("vim");
            assertThat(panel.calls).containsExactly("highlight vim");
        });
        var applied = new AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
        onEdt(() -> applied.set(page.apply()));
        applied.get().get(5, TimeUnit.SECONDS);
        onEdt(() -> { });
        assertThat(TomlDocument.leaves(Files.readString(data.resolve(ID).resolve(ID + ".toml")))).containsEntry(List.of("font_size"), 20L);
        onEdt(() -> {
            assertThat(panel.calls).contains("apply");
            assertThat(page.modified()).isFalse();
            page.dispose();
            assertThat(panel.calls).contains("dispose");
        });
    }

    @Test void hostOwnedFailureAndStoppedPagesFollowLiveThemeAndCloseTheirViews() {
        start(settings -> {
            settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode"));
            settings.page(rows -> { throw new IllegalStateException("<html>boom"); });
        });
        onEdt(() -> {
            try (var themes = new dev.jasper.app.appearance.ThemeController()) {
                var live = new PageContext(null, id -> {}, Runnable::run, (title, initial) -> Optional.empty(), () -> {}, () -> null,
                    () -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME));
                var page = (PluginPanelPage) host.settingsPages(live).getFirst();
                var stub = new PluginStubPage("dev.example.off", "Off", "<html>unavailable", () -> {}, live.kit());
                try {
                    var body = page.component(); var stopped = stub.component();
                    var failure = (JLabel) StagedVaultHost.named(page.content(), "plugin-page-text");
                    var reason = (JLabel) StagedVaultHost.named(stopped, "plugin-stub.reason");
                    for (var theme : List.of(dev.jasper.app.appearance.Theme.JASPER_LIGHT, dev.jasper.app.appearance.Theme.DARK)) {
                        themes.configure(theme, new dev.jasper.app.config.UiFontConfig("system", 24));
                        assertThat(page.component()).isSameAs(body);
                        assertThat(stub.component()).isSameAs(stopped);
                        assertThat(failure.getText()).isEqualTo("This page could not be shown: <html>boom");
                        assertThat(reason.getText()).isEqualTo("Not running: <html>unavailable");
                        assertThat(failure.getClientProperty("html")).isNull();
                        assertThat(failure.getForeground()).isEqualTo(live.kit().get().theme().semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY));
                        assertThat(reason.getForeground()).isEqualTo(live.kit().get().theme().semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_SECONDARY));
                        assertThat(failure.getFont().getSize2D()).isGreaterThanOrEqualTo(24);
                        dev.jasper.sdk.contracts.ThemeMigrationCapture.save("plugin-settings-host",theme.id(),"failed",body,640,360);
                        dev.jasper.sdk.contracts.ThemeMigrationCapture.save("plugin-settings-host",theme.id(),"stopped",stopped,640,360);
                    }
                } finally { page.dispose(); stub.dispose(); }
                assertThat(((dev.jasper.ui.swing.KitView) page.content()).isClosed()).isTrue();
                assertThat(page.message().isClosed()).isTrue();
            }
        });
    }

    @Test void aPageThatFailsToBuildShowsWhyAndSettingsCarriesOn() {
        start(settings -> {
            settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode"));
            settings.page(rows -> { throw new IllegalStateException("boom"); });
        });
        var page = (PluginPanelPage) onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            assertThat(page.component()).isNotNull();
            assertThat(page.content()).isInstanceOf(dev.jasper.ui.swing.KitView.class);
            assertThat(((JLabel) StagedVaultHost.named(page.content(), "plugin-page-text")).getText()).isEqualTo("This page could not be shown: boom");
        });
        assertThat(host.failures(ID)).isPositive();
    }

    @Test void aFailureWithoutAMessageShowsWhatItWas() {
        start(settings -> {
            settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode"));
            settings.page(rows -> { throw new IllegalStateException(); });
        });
        var page = (PluginPanelPage) onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            page.component();
            assertThat(((JLabel) StagedVaultHost.named(page.content(), "plugin-page-text")).getText())
                .isEqualTo("This page could not be shown: java.lang.IllegalStateException");
        });
    }

    @Test void aRejectedFileShowsItsMessageAndBlocksOnlyThisPagesEdits() throws Exception {
        write("font_size = \"big\"\n");
        var panel = startWithPanel();
        var page = (PluginPanelPage) onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            page.component();
            assertThat(page.message()).isInstanceOf(dev.jasper.ui.swing.KitView.class);
            assertThat(page.message().isVisible()).isTrue();
            assertThat(((JLabel) StagedVaultHost.named(page.message(), "plugin-page-text")).getText()).contains("has errors");
            assertThat(page.valid()).as("an untouched page does not hold up the dialog").isTrue();
            panel.host.stage(panel.size, 20);
            assertThat(page.valid()).as("an edit cannot be saved to a rejected file").isFalse();
            page.reset();
            assertThat(page.valid()).isTrue();
        });
        write("font_size = 20\n");
        reload();
        onEdt(() -> {
            assertThat(page.message().isVisible()).isFalse();
            assertThat(panel.host.pending(panel.size)).isEqualTo(20);
            page.dispose();
        });
    }

    /** A plugin page laid out like the Sample's: standard rows in a vertical box. */
    private static final class BoxPanel implements SettingsPanel {
        final dev.jasper.sdk.ui.UiKit kit;
        final Setting<Boolean> vim;
        final Setting<Integer> size;
        final List<JComponent> rows = new ArrayList<>();
        BoxPanel(Setting<Boolean> vim, Setting<Integer> size, dev.jasper.sdk.ui.UiKit kit) { this.vim = vim; this.size = size; this.kit=kit; }
        @Override public dev.jasper.sdk.ui.UiView create(SettingsPanelHost host) {
            var view=(HostedKitView)kit.create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("page",dev.jasper.sdk.ui.ControlState.READY,dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL,List.of(host.row(vim),host.row(size)))),e -> {});
            for(var id:List.of("settings:vim:row","settings:font_size:row")) rows.add(dev.jasper.app.testsupport.KitTestSupport.require(view.component(),id,JComponent.class));
            return view;
        }
    }

    @Test void rowsStayTogetherAtTheTopOfATallPage() {
        var panel = new AtomicReference<BoxPanel>();
        start(settings -> {
            var vim = settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode"));
            var size = settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size"));
            panel.set(new BoxPanel(vim, size, pluginKit));
            settings.page(panel.get());
        });
        var page = onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            var scroll = new JScrollPane(page.component());
            scroll.setSize(700, 1400);
            LayoutTestSupport.layoutTree(scroll);
            JComponent first = panel.get().rows.get(0), second = panel.get().rows.get(1);
            assertThat(first.getHeight()).isEqualTo(first.getPreferredSize().height);
            assertThat(second.getY()).as("the second row follows the first")
                .isLessThan(first.getY() + first.getPreferredSize().height + 100);
            assertThat(second.getHeight()).isEqualTo(second.getPreferredSize().height);
            page.dispose();
        });
    }

    @Test void closingWithoutApplyResetsThePanelAndClosingAfterApplyDoesNot() throws Exception {
        var panel = startWithPanel();
        var cancelled = onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            cancelled.component();
            panel.ownModified = true;
            cancelled.dispose();
            assertThat(panel.calls).containsExactly("reset", "dispose");
            panel.calls.clear();
        });
        var applied = onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            applied.component();
            panel.host.stage(panel.size, 20);
            panel.ownModified = true;
        });
        var saving = new AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
        onEdt(() -> saving.set(applied.apply()));
        saving.get().get(5, TimeUnit.SECONDS);
        onEdt(() -> { });
        onEdt(() -> {
            applied.dispose();
            assertThat(panel.calls).containsExactly("apply", "dispose");
        });
    }

    @Test void stagingTheDefaultOfAnUnsetKeyLeavesThePageUnmodified() {
        var panel = startWithPanel();
        var page = onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            page.component();
            panel.host.stage(panel.size, 20);
            assertThat(page.modified()).isTrue();
            panel.host.stage(panel.size, 13);
            assertThat(page.modified()).isFalse();
            page.dispose();
        });
    }

    @Test void eachPluginGetsOnePageAndASupersededCopyGetsNone() {
        assertThat(PluginRuntime.stubbed(status("dev.example.x", PluginStatus.State.SKIPPED,
            "superseded by version 2.0.0 from DEVELOPMENT"))).isFalse();
        var running = new PluginStubPage("dev.example.run", "Run", "", () -> { }, context.kit());
        var statuses = List.of(
            status("dev.example.run", PluginStatus.State.ACTIVE, ""),
            status("dev.example.run", PluginStatus.State.DISABLED, "safe mode"),
            status("dev.example.quiet", PluginStatus.State.ACTIVE, ""),
            status("dev.example.quiet", PluginStatus.State.DISABLED, "safe mode"),
            status("dev.example.old", PluginStatus.State.SKIPPED, "superseded by version 2.0.0 from USER"),
            status("dev.example.two", PluginStatus.State.NEEDS_CONSENT, "not reviewed yet"),
            status("dev.example.two", PluginStatus.State.NEEDS_CONSENT, "not reviewed yet"),
            status("dev.example.gap", PluginStatus.State.SKIPPED, "requires dev.example.none, which is not installed"));
        var pages = PluginRuntime.withStubs(List.of(running), statuses,
            status -> new PluginStubPage(status.id(), status.name(), status.reason(), () -> { }, context.kit()));
        assertThat(pages).map(SettingsPage::id).doesNotHaveDuplicates()
            .containsExactlyInAnyOrder("plugin.dev.example.run", "plugin.dev.example.two", "plugin.dev.example.gap");
        assertThat(pages).contains(running);
    }

    @Test void pluginsThatAreNotRunningGetAStubAndOnlyWhenTheUserMeantThemToRun() {
        var opened = new ArrayList<String>();
        var stub = new PluginStubPage("dev.example.off", "Off", "not reviewed yet", () -> opened.add("open"), context.kit());
        onEdt(() -> {
            assertThat(stub.id()).isEqualTo("plugin.dev.example.off");
            assertThat(stub.parent()).contains("tools");
            assertThat(stub.component()).isInstanceOf(dev.jasper.ui.swing.KitView.class);
            assertThat(stub.text()).isEqualTo("Start Off to edit its settings.");
            assertThat(stub.reason()).isEqualTo("Not running: not reviewed yet");
            var open = (javax.swing.AbstractButton) StagedVaultHost.named(stub.component(), "plugin-stub.open");
            open.doClick(0);
            stub.dispose(); stub.dispose();
            open.doClick(0); // closed host content cannot call into the owner again.
        });
        assertThat(opened).containsExactly("open");
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.NEEDS_CONSENT, "not reviewed yet"))).isTrue();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.FAILED, "boom"))).isTrue();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.SKIPPED, "requires x"))).isTrue();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.DISABLED, "safe mode"))).isTrue();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.DISABLED, "disabled"))).isFalse();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.ACTIVE, ""))).isFalse();
    }

    private static PluginStatus status(PluginStatus.State state, String reason) { return status("dev.example.x", state, reason); }

    private static PluginStatus status(String id, PluginStatus.State state, String reason) {
        return new PluginStatus(id, id.substring(id.lastIndexOf('.') + 1), "1.0.0", PluginCandidate.Origin.USER, state, reason);
    }

    private void write(String text) throws Exception {
        Files.createDirectories(data.resolve(ID));
        Files.writeString(data.resolve(ID).resolve(ID + ".toml"), text);
    }

    private void reload() {
        onEdt(() -> {
            var changed = host.environment.settingsFiles().refresh(true);
            for (String id : changed) host.environment.clearReports().accept(host.environment.settingsFiles().file(id));
            host.settingsChanged(changed);
        });
    }
}
