package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.logging.*;
import java.util.stream.Stream;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.WorkspaceStyleFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceStylesTest {
    private LookAndFeel previousLaf;
    private Map<String, Object> previousDefaults;
    private static final Set<String> MANAGED_DEFAULTS = Set.of("ClassLoader", "defaultFont", "Label.font",
        "List.font", "TextField.font", "PasswordField.font", "FormattedTextField.font", "TextArea.font",
        "ComboBox.font", "Button.font", "Jasper.uiFontFamilyOverride");

    @BeforeEach void captureChrome() throws Exception {
        edt(() -> {
            previousLaf = UIManager.getLookAndFeel();
            previousDefaults = new HashMap<>();
            for (Object key : UIManager.getDefaults().keySet()) {
                if (key instanceof String text && (text.startsWith("Jasper.workspace.") || MANAGED_DEFAULTS.contains(text)))
                    previousDefaults.put(text, UIManager.getDefaults().get(key));
            }
        });
    }

    @AfterEach void restoreChrome() throws Exception {
        edt(() -> {
            try { UIManager.setLookAndFeel(previousLaf); }
            catch (UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            var keys = new HashSet<>(previousDefaults.keySet());
            keys.addAll(MANAGED_DEFAULTS);
            for (Object key : UIManager.getDefaults().keySet())
                if (key instanceof String text && text.startsWith("Jasper.workspace.")) keys.add(text);
            for (String key : keys) UIManager.put(key, previousDefaults.get(key));
        });
    }

    private record Case(String type, Supplier<JComponent> component, Set<String> keys) {}

    /** Every listed key is an executable FlatLaf 3.7 delegate contract, including common setter keys. */
    private static Stream<Arguments> controls() {
        return Stream.of(
            row("Panel", () -> new JPanel(), "background foreground"),
            row("Label", () -> new JLabel("label"), "background foreground disabledForeground"),
            row("List", () -> new JList<>(new String[] {"first", "second"}), "background foreground selectionBackground selectionForeground selectionInactiveBackground selectionInactiveForeground cellFocusColor"),
            row("Table", () -> new JTable(new Object[][] {{"value"}}, new Object[] {"column"}), "background foreground selectionBackground selectionForeground selectionInactiveBackground selectionInactiveForeground gridColor cellFocusColor"),
            row("TableHeader", () -> new JTable(new Object[][] {{"value"}}, new Object[] {"column"}).getTableHeader(), "background foreground separatorColor bottomSeparatorColor hoverBackground"),
            row("Tree", () -> new JTree(), "background foreground selectionBackground selectionForeground selectionInactiveBackground selectionInactiveForeground selectionBorderColor"),
            row("TextField", () -> new JTextField("text"), "background foreground selectionColor selectedTextColor caretColor disabledTextColor disabledBackground inactiveBackground borderColor focusedBorderColor focusColor"),
            row("PasswordField", () -> new JPasswordField("secret"), "background foreground selectionColor selectedTextColor caretColor disabledTextColor disabledBackground inactiveBackground borderColor focusedBorderColor focusColor"),
            row("FormattedTextField", () -> new JFormattedTextField("text"), "background foreground selectionColor selectedTextColor caretColor disabledTextColor disabledBackground inactiveBackground borderColor focusedBorderColor focusColor"),
            row("TextArea", () -> new JTextArea("text"), "background foreground selectionColor selectedTextColor caretColor disabledTextColor disabledBackground inactiveBackground"),
            row("TextPane", () -> new JTextPane(), "background foreground selectionColor selectedTextColor caretColor disabledTextColor disabledBackground inactiveBackground"),
            row("EditorPane", () -> new JEditorPane(), "background foreground selectionColor selectedTextColor caretColor disabledTextColor disabledBackground inactiveBackground"),
            row("ComboBox", () -> new JComboBox<>(new String[] {"first", "second"}), "background foreground disabledForeground editableBackground disabledBackground buttonBackground buttonEditableBackground buttonArrowColor buttonDisabledArrowColor popupBackground borderColor focusedBorderColor focusColor"),
            row("Button", () -> new JButton("button"), "background foreground disabledText hoverBackground pressedBackground selectedBackground selectedForeground default.background default.foreground borderColor focusedBorderColor focusColor"),
            row("ToggleButton", () -> new JToggleButton("toggle"), "background foreground disabledText hoverBackground pressedBackground selectedBackground selectedForeground default.background default.foreground borderColor focusedBorderColor focusColor"),
            row("CheckBox", () -> new JCheckBox("check"), "foreground disabledText icon.background icon.borderColor icon.selectedBackground icon.selectedBorderColor icon.checkmarkColor icon.hoverBackground icon.pressedBackground icon.focusColor"),
            row("RadioButton", () -> new JRadioButton("radio"), "foreground disabledText icon.background icon.borderColor icon.selectedBackground icon.selectedBorderColor icon.checkmarkColor icon.hoverBackground icon.pressedBackground icon.focusColor"),
            row("ScrollPane", () -> new JScrollPane(new JLabel("content")), "background borderColor focusedBorderColor focusColor"),
            row("Viewport", () -> new JViewport(), ""),
            row("ScrollBar", () -> new JScrollBar(), "background foreground track thumb hoverThumbColor pressedThumbColor buttonArrowColor buttonDisabledArrowColor"),
            row("SplitPane", () -> new JSplitPane(), "background draggingColor gripColor oneTouchArrowColor"),
            row("TabbedPane", () -> new JTabbedPane(), "background foreground selectedBackground selectedForeground hoverColor underlineColor inactiveUnderlineColor tabSeparatorColor contentAreaColor"),
            row("ToolBar", () -> new JToolBar(), "background foreground separatorColor"),
            row("Separator", () -> new JSeparator(), "background foreground"),
            row("ProgressBar", () -> new JProgressBar(), "background foreground")
        ).map(c -> Arguments.of(c.type(), c.component(), c.keys()));
    }

    private static Case row(String type, Supplier<JComponent> component, String keys) {
        return new Case(type, component, keys.isEmpty() ? Set.of() : Set.of(keys.split(" ")));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("controls")
    void representativeControlsAcceptAllRecipeKeys(String type, Supplier<JComponent> factory, Set<String> supportedKeys) throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var component = factory.get();
            var recipe = WorkspaceStyles.recipe(component, independent().workspace());
            assertThat(recipe.type()).isEqualTo(type);
            assertThat(recipe.style().keySet()).as(component.getUI().getClass().getName()).containsExactlyInAnyOrderElementsOf(supportedKeys);
            assertThat(recipe.direct().keySet()).isSubsetOf("background", "foreground", "selectionBackground",
                "selectionForeground", "caretColor", "disabledTextColor");
            for (String key : recipe.direct().keySet()) assertThat(recipe.style()).doesNotContainKey(key);
            Logger logger = Logger.getLogger("com.formdev.flatlaf");
            var severe = new ArrayList<LogRecord>();
            Handler handler = new Handler() {
                @Override public void publish(LogRecord record) { if (record.getLevel().intValue() >= Level.SEVERE.intValue()) severe.add(record); }
                @Override public void flush() {}
                @Override public void close() {}
            };
            logger.addHandler(handler);
            try {
                applyRecipe(component, recipe);
                paint(component, 160, 50);
                assertThat(severe).extracting(LogRecord::getMessage).as(type + " FlatLaf style failures").isEmpty();
            } finally { logger.removeHandler(handler); }
            assertThat(recipe.style()).isUnmodifiable();
            assertThat(recipe.direct()).isUnmodifiable();
        });
    }

    @Test void flatLafSubclassKeepsFullTabbedPaneRecipe() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var deck = new TerminalDeck();
            deck.addTab("one", new JPanel());
            var theme = independent().workspace();
            var recipe = WorkspaceStyles.recipe(deck, theme);
            assertThat(recipe.type()).isEqualTo("TabbedPane");
            assertThat(recipe.style()).containsEntry("selectedBackground", theme.tokens().get(Token.TAB_ACTIVE))
                .containsEntry("underlineColor", theme.tokens().get(Token.TAB_UNDERLINE))
                .containsEntry("contentAreaColor", theme.tokens().get(Token.SURFACE));
            applyRecipe(deck, recipe);
            var ui = (com.formdev.flatlaf.ui.FlatStylingSupport.StyleableUI) deck.getUI();
            assertThat(ui.getStyleableValue(deck, "selectedBackground"))
                .isEqualTo(theme.tokens().get(Token.TAB_ACTIVE));
            assertThat(ui.getStyleableValue(deck, "underlineColor"))
                .isEqualTo(theme.tokens().get(Token.TAB_UNDERLINE));
        });
    }

    @ParameterizedTest(name = "effective {0}") @MethodSource("controls")
    void everyRecipeKeyReachesRealDelegateOrComponent(String type, Supplier<JComponent> factory, Set<String> keys) throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var component = factory.get();
            var recipe = WorkspaceStyles.recipe(component, independent().workspace());
            applyRecipe(component, recipe);
            for (var entry : recipe.style().entrySet()) {
                Object effective = effective(component, entry.getKey());
                assertThat(effective).as(type + "." + entry.getKey()).isEqualTo(entry.getValue());
            }
            for (var entry : recipe.direct().entrySet())
                assertThat(effective(component, entry.getKey())).as(type + "." + entry.getKey())
                    .isEqualTo(entry.getValue());
        });
    }

    private static Object effective(JComponent component, String key) {
        return switch (key) {
            case "background" -> component.getBackground();
            case "foreground" -> component.getForeground();
            case "selectionBackground" -> component instanceof JList<?> list ? list.getSelectionBackground()
                : component instanceof JTable table ? table.getSelectionBackground()
                : component instanceof JTextComponent text ? text.getSelectionColor() : styleable(component, key);
            case "selectionForeground" -> component instanceof JList<?> list ? list.getSelectionForeground()
                : component instanceof JTable table ? table.getSelectionForeground()
                : component instanceof JTextComponent text ? text.getSelectedTextColor() : styleable(component, key);
            case "selectionColor" -> ((JTextComponent) component).getSelectionColor();
            case "selectedTextColor" -> ((JTextComponent) component).getSelectedTextColor();
            case "caretColor" -> ((JTextComponent) component).getCaretColor();
            case "disabledTextColor" -> ((JTextComponent) component).getDisabledTextColor();
            case "gridColor" -> ((JTable) component).getGridColor();
            default -> styleable(component, key);
        };
    }

    private static Object styleable(JComponent component, String key) {
        return ((com.formdev.flatlaf.ui.FlatStylingSupport.StyleableUI) component.getUI())
            .getStyleableValue(component, key);
    }

    @Test void compositeFamiliesExposeSemanticColorsAndPaint() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var theme = independent().workspace();
            var combo = new JComboBox<>(new String[] {"one", "two"});
            applyRecipe(combo, WorkspaceStyles.recipe(combo, theme));
            assertThat(combo.getBackground()).isEqualTo(theme.tokens().get(Token.INPUT_BACKGROUND));
            assertThat(styleable(combo, "buttonArrowColor")).isEqualTo(theme.tokens().get(Token.TEXT));
            assertThat(styleable(combo, "popupBackground")).isEqualTo(theme.tokens().get(Token.SURFACE_SUNKEN));
            assertThat(hasPixel(paint(combo, 180, 32), theme.tokens().get(Token.INPUT_BACKGROUND))).isTrue();

            for (AbstractButton button : List.of(new JCheckBox("chosen"), new JRadioButton("chosen"))) {
                button.setSelected(true);
                applyRecipe(button, WorkspaceStyles.recipe(button, theme));
                assertThat(styleable(button, "icon.selectedBackground")).isEqualTo(theme.tokens().get(Token.ACCENT));
                assertThat(styleable(button, "icon.checkmarkColor")).isEqualTo(theme.tokens().get(Token.TEXT_ON_ACCENT));
                assertThat(hasPixel(paint(button, 180, 32), theme.tokens().get(Token.ACCENT)))
                    .as(button.getClass().getSimpleName() + " selected icon").isTrue();
            }

            var bar = new JScrollBar();
            bar.setValues(20, 30, 0, 100);
            applyRecipe(bar, WorkspaceStyles.recipe(bar, theme));
            assertThat(styleable(bar, "track")).isEqualTo(theme.tokens().get(Token.SCROLLBAR_TRACK));
            assertThat(styleable(bar, "thumb")).isEqualTo(theme.tokens().get(Token.SCROLLBAR_THUMB));
            assertThat(hasPixel(paint(bar, 24, 150), theme.tokens().get(Token.SCROLLBAR_THUMB))).isTrue();

            var split = new JSplitPane();
            applyRecipe(split, WorkspaceStyles.recipe(split, theme));
            assertThat(styleable(split, "draggingColor")).isEqualTo(theme.tokens().get(Token.DIVIDER));
            assertThat(styleable(split, "gripColor")).isEqualTo(theme.tokens().get(Token.DIVIDER));
            assertThat(hasPixel(paint(split, 180, 80), theme.tokens().get(Token.SURFACE)))
                .as("split-pane surface").isTrue();

            var tabs = new JTabbedPane();
            tabs.addTab("one", new JPanel());
            applyRecipe(tabs, WorkspaceStyles.recipe(tabs, theme));
            assertThat(styleable(tabs, "selectedBackground")).isEqualTo(theme.tokens().get(Token.TAB_ACTIVE));
            assertThat(styleable(tabs, "underlineColor")).isEqualTo(theme.tokens().get(Token.TAB_UNDERLINE));
            assertThat(hasPixel(paint(tabs, 180, 80), theme.tokens().get(Token.TAB_ACTIVE))).isTrue();

            var toolbar = new JToolBar();
            toolbar.add(new JButton("item"));
            applyRecipe(toolbar, WorkspaceStyles.recipe(toolbar, theme));
            assertThat(toolbar.getBackground()).isEqualTo(theme.tokens().get(Token.SURFACE));
            assertThat(styleable(toolbar, "separatorColor")).isEqualTo(theme.tokens().get(Token.SEPARATOR));
            assertThat(hasPixel(paint(toolbar, 180, 36), theme.tokens().get(Token.SURFACE))).isTrue();

            var separator = new JSeparator();
            applyRecipe(separator, WorkspaceStyles.recipe(separator, theme));
            assertThat(separator.getForeground()).isEqualTo(theme.tokens().get(Token.SEPARATOR));
            assertThat(hasPixel(paint(separator, 180, 6), theme.tokens().get(Token.SEPARATOR))).isTrue();
        });
    }

    private static boolean hasPixel(BufferedImage image, Color color) {
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                if (new Color(image.getRGB(x, y), true).equals(color)) return true;
        return false;
    }

    @Test void listAndHeaderUseWorkspaceColorsWithoutChangingChrome() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var resolved = independent();
            var laf = UIManager.getLookAndFeel();
            Color chrome = UIManager.getColor("Panel.background");
            var list = new JList<>(new String[] {"first", "second"});
            var table = new JTable(new Object[][] {{"value"}}, new Object[] {"column"});
            applyRecipe(list, WorkspaceStyles.recipe(list, resolved.workspace()));
            applyRecipe(table.getTableHeader(), WorkspaceStyles.recipe(table.getTableHeader(), resolved.workspace()));
            assertThat(list.getSelectionBackground()).isEqualTo(resolved.workspace().tokens().get(Token.SELECTION));
            assertThat(list.getSelectionForeground()).isEqualTo(resolved.workspace().tokens().get(Token.SELECTION_TEXT));
            assertThat(table.getTableHeader().getBackground()).isEqualTo(resolved.workspace().tokens().get(Token.SURFACE_RAISED));
            paint(list, 160, 60);
            paint(table.getTableHeader(), 160, 24);
            assertThat(UIManager.getLookAndFeel()).isSameAs(laf);
            assertThat(UIManager.getColor("Panel.background")).isEqualTo(chrome);
        });
    }

    @Test void selectedAndDisabledControlsUseDelegateColorsAndPaint() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var theme = independent().workspace();
            var list = new JList<>(new String[] {"selected", "other"});
            list.setSelectedIndex(0);
            applyRecipe(list, WorkspaceStyles.recipe(list, theme));
            assertThat(list.getSelectionBackground()).isEqualTo(theme.tokens().get(Token.SELECTION));
            assertThat(list.getSelectionForeground()).isEqualTo(theme.tokens().get(Token.SELECTION_TEXT));
            var listImage = paint(list, 180, 64);
            assertThat(new Color(listImage.getRGB(170, 8), true)).isEqualTo(theme.tokens().get(Token.SELECTION));

            var field = new JTextField("disabled");
            applyRecipe(field, WorkspaceStyles.recipe(field, theme));
            field.setEnabled(false);
            assertThat(field.getDisabledTextColor()).isEqualTo(theme.tokens().get(Token.TEXT_DISABLED));
            assertThat(field.getBackground()).isEqualTo(theme.tokens().get(Token.INPUT_BACKGROUND));
            assertThat(new Color(paint(field, 160, 32).getRGB(150, 16), true))
                .isEqualTo(theme.tokens().get(Token.INPUT_BACKGROUND));

            var toggle = new JToggleButton("selected");
            applyRecipe(toggle, WorkspaceStyles.recipe(toggle, theme));
            toggle.setSelected(true);
            var ui = (com.formdev.flatlaf.ui.FlatStylingSupport.StyleableUI) toggle.getUI();
            assertThat(ui.getStyleableValue(toggle, "selectedBackground")).isEqualTo(theme.tokens().get(Token.SELECTION));
            assertThat(ui.getStyleableValue(toggle, "selectedForeground")).isEqualTo(theme.tokens().get(Token.SELECTION_TEXT));
            assertThat(new Color(paint(toggle, 160, 36).getRGB(4, 18), true))
                .isEqualTo(theme.tokens().get(Token.SELECTION));
        });
    }

    @Test void focusAndProgressColorsReachRealDelegates() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var theme = independent().workspace();
            var field = new JTextField("focus");
            applyRecipe(field, WorkspaceStyles.recipe(field, theme));
            var fieldUi = (com.formdev.flatlaf.ui.FlatStylingSupport.StyleableUI) field.getUI();
            assertThat(fieldUi.getStyleableValue(field, "focusColor")).isEqualTo(theme.tokens().get(Token.FOCUS_RING));
            assertThat(fieldUi.getStyleableValue(field, "focusedBorderColor")).isEqualTo(theme.tokens().get(Token.FOCUS_RING));
            var unfocused = paint(field, 160, 32);
            field.putClientProperty("JComponent.focusOwner", (java.util.function.Predicate<JComponent>) c -> true);
            var focused = paint(field, 160, 32);
            assertThat(focused.getRGB(1, 16)).as("painted focus ring").isNotEqualTo(unfocused.getRGB(1, 16));
            var progress = new JProgressBar(0, 100);
            progress.setValue(50);
            applyRecipe(progress, WorkspaceStyles.recipe(progress, theme));
            assertThat(progress.getBackground()).isEqualTo(theme.tokens().get(Token.SURFACE_SUNKEN));
            assertThat(progress.getForeground()).isEqualTo(theme.tokens().get(Token.ACCENT));
            var image = paint(progress, 160, 24);
            assertThat(new Color(image.getRGB(20, 12), true)).isEqualTo(theme.tokens().get(Token.ACCENT));
            assertThat(new Color(image.getRGB(140, 12), true)).isEqualTo(theme.tokens().get(Token.SURFACE_SUNKEN));
        });
    }

    @Test void unsupportedComponentAndCustomDelegateUseOnlySupportedDirectColors() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var theme = independent().workspace();
            var unsupported = new JSlider();
            assertThat(WorkspaceStyles.recipe(unsupported, theme)).isEqualTo(
                new WorkspaceStyles.Recipe("", Map.of(), Map.of()));
            var list = new JList<>(new String[] {"first"});
            list.setUI(new javax.swing.plaf.basic.BasicListUI());
            var recipe = WorkspaceStyles.recipe(list, theme);
            assertThat(recipe.type()).isEqualTo("List");
            assertThat(recipe.style()).isEmpty();
            assertThat(recipe.direct()).containsKeys("background", "foreground", "selectionBackground", "selectionForeground");
            applyRecipe(list, recipe);
            assertThat(list.getSelectionBackground()).isEqualTo(theme.tokens().get(Token.SELECTION));
            var check = new JCheckBox("custom icon");
            var icon = new ImageIcon(new java.awt.image.BufferedImage(12, 12,
                java.awt.image.BufferedImage.TYPE_INT_ARGB));
            check.setIcon(icon);
            assertThat(WorkspaceStyles.recipe(check, theme).style().keySet())
                .noneMatch(key -> key.startsWith("icon."));
            assertThat(check.getIcon()).isSameAs(icon);
            var scroll = new JScrollPane(new JLabel("content"));
            var customBorder = new javax.swing.border.LineBorder(Color.RED, 3);
            scroll.setBorder(customBorder);
            var scrollRecipe = WorkspaceStyles.recipe(scroll, theme);
            assertThat(scrollRecipe.style()).doesNotContainKeys("borderColor", "focusedBorderColor", "focusColor");
            applyRecipe(scroll, scrollRecipe);
            assertThat(scroll.getBorder()).isSameAs(customBorder);
            var tree = new JTree();
            tree.setUI(new javax.swing.plaf.basic.BasicTreeUI());
            assertThat(WorkspaceStyles.recipe(tree, theme).direct()).containsOnlyKeys("background", "foreground");
        });
    }

    @Test void sameDarknessChangesEffectiveColors() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var original = independent();
            var changed = recolored(original, Token.INPUT_BACKGROUND, new Color(0x124578));
            var field = new JTextField("text");
            applyRecipe(field, WorkspaceStyles.recipe(field, original.workspace()));
            Color before = field.getBackground();
            applyRecipe(field, WorkspaceStyles.recipe(field, changed.workspace()));
            assertThat(field.getBackground()).isEqualTo(new Color(0x124578)).isNotEqualTo(before);
            var image = paint(field, 160, 32);
            assertThat(new Color(image.getRGB(5, 16), true)).isEqualTo(new Color(0x124578));
        });
    }

    @Test void completeThemeWithoutOptionalKeysSupportsRecipes() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var original = independent();
            var ui = new LinkedHashMap<>(original.workspace().uiKeys());
            ui.remove("ToolWindow.background");
            var old = original.workspace();
            var complete = new GeneratedTheme(old.name(), old.dark(), old.author(), old.tokens(), old.syntax(),
                old.terminal(), ui, old.icons());
            var label = new JLabel("content");
            applyRecipe(label, WorkspaceStyles.recipe(label, complete));
            assertThat(label.getForeground()).isEqualTo(complete.tokens().get(Token.TEXT));
        });
    }
}
