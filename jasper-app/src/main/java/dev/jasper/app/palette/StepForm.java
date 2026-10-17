package dev.jasper.app.palette;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;

/**
 * A step's small form, as both palette views show it in their list's place: a title row, one labelled row per field
 * (a fixed label column and an EMBEDDED field, each its own kit view) and a hidden error row. Moved unchanged out of the
 * card (Enterprise presentation plan 8a), so the Run window renders steps exactly as the card does. EDT only.
 */
final class StepForm implements AutoCloseable {
    /** The node id prefix of the step's views. */
    static final String STEP = "step";
    private static final String FIELD = "step.field.";

    private final KitContext kit;
    private final List<KitView> views = new ArrayList<>();
    private final KitView titleView, errorView;
    private final JPanel panel, titleRow, errorRow;
    private final List<Row> rows = new ArrayList<>();
    private String title;
    private List<PaletteStep.Field> fields = List.of();
    private final List<String> texts = new ArrayList<>();
    private String error;
    private int focus = -1;
    private boolean closed;

    StepForm(KitContext kit) {
        this.kit = Objects.requireNonNull(kit);
        titleView = view(titleSpec());
        errorView = view(errorSpec());
        titleRow = dev.jasper.ui.swing.SurfacePanel.row(kit, DesignMetrics.Role.PALETTE_STEP_TITLE_HEIGHT, false);
        titleRow.setLayout(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(titleView, BorderLayout.CENTER);
        errorRow = dev.jasper.ui.swing.SurfacePanel.row(kit, DesignMetrics.Role.PALETTE_STEP_TITLE_HEIGHT, false);
        errorRow.setLayout(new BorderLayout());
        errorRow.setOpaque(false);
        errorRow.add(errorView, BorderLayout.CENTER);
        errorRow.setVisible(false);
        panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setOpaque(false);
    }

    private KitView view(ViewSpec spec) {
        var view = new KitView(kit, spec, this::event);
        views.add(view);
        return view;
    }

    private void event(UiEvent event) {
        if (closed) return;
        if (event instanceof UiEvent.Edited edited && edited.id().startsWith(FIELD)) {
            int index = Integer.parseInt(edited.id().substring(FIELD.length()));
            if (index < texts.size()) texts.set(index, edited.text());
        }
    }

    private static Nodes.Text text(String id, String value, Nodes.TextRole role) { return new Nodes.Text(id, ControlState.READY, value, role); }

    private ViewSpec titleSpec() { return new ViewSpec(text(STEP + ".title", title == null ? "" : title, Nodes.TextRole.CAPTION)); }

    private ViewSpec errorSpec() {
        var shown = new ControlState(error != null, true, false, "");
        return new ViewSpec(new Nodes.Stack(STEP + ".error", shown, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.StatusDot(STEP + ".error.status", ControlState.READY, Nodes.StatusKind.DANGER),
            text(STEP + ".error.text", error == null ? "" : error, Nodes.TextRole.BODY))));
    }

    private static String label(PaletteStep.Field field) { return field.label().isBlank() ? field.name() : field.label(); }

    /** The row's own label column names the field, so the field is EMBEDDED: its label stays its accessible name. */
    private ViewSpec fieldSpec(int index) {
        return new ViewSpec(new Nodes.Field(FIELD + index, ControlState.READY, Nodes.FieldKind.TEXT, texts.get(index), "",
            label(fields.get(index)), "", java.util.Optional.empty(), Nodes.FieldPresentation.EMBEDDED));
    }

    private void render(KitView view, ViewSpec spec) { if (!view.isClosed()) view.update(spec); }

    /** The form's panel, which the owning view shows in its list's place. */
    JPanel panel() { return panel; }

    /** Shows {@code title} over one row per field, prefilled; the first field takes focus. */
    void show(String title, List<PaletteStep.Field> fields) {
        this.title = title;
        this.fields = List.copyOf(fields);
        texts.clear();
        for (PaletteStep.Field field : this.fields) texts.add(field.prefill());
        error = null;
        render(titleView, titleSpec());
        render(errorView, errorSpec());
        errorRow.setVisible(false);
        buildRows();
        focus = -1;
        focusField(1);
    }

    /** Leaves the form. */
    void hide() {
        title = null;
        fields = List.of();
        texts.clear();
        error = null;
        focus = -1;
        clearRows();
        render(titleView, titleSpec());
        render(errorView, errorSpec());
        errorRow.setVisible(false);
    }

    private void buildRows() {
        clearRows();
        panel.add(titleRow);
        for (int i = 0; i < fields.size(); i++) {
            var row = new Row(view(new ViewSpec(text(STEP + ".label." + i, label(fields.get(i)), Nodes.TextRole.CAPTION))), view(fieldSpec(i)));
            rows.add(row);
            panel.add(row.panel);
        }
        panel.add(errorRow);
        panel.add(javax.swing.Box.createVerticalGlue());
        applyGeometry();
    }

    private void clearRows() {
        for (Row row : rows) {
            row.label.close(); row.field.close();
            views.remove(row.label); views.remove(row.field);
        }
        rows.clear();
        panel.removeAll();
    }

    /** The former step geometry: 4/12 row padding, a fixed label column and a gap before the field. */
    void applyGeometry() {
        int side = com.formdev.flatlaf.util.UIScale.scale(kit.metrics().value(DesignMetrics.Role.PALETTE_STEP_SIDE_PADDING));
        titleRow.setBorder(new EmptyBorder(0, side, 0, side));
        errorRow.setBorder(new EmptyBorder(0, side, 0, side));
        panel.revalidate();
    }

    /** The form's height: its title row, its rows and the error row while an error shows. */
    int preferredHeight() {
        return titleRow.getPreferredSize().height + rows.stream().mapToInt(row -> row.panel.getPreferredSize().height).sum()
            + (error != null ? errorRow.getPreferredSize().height : 0);
    }

    boolean showing() { return focus >= 0; }
    int focusIndex() { return focus; }
    String titleText() { return title; }
    String errorText() { return error; }
    int fieldCount() { return fields.size(); }

    /** Moves focus to the next (or previous) field, wrapping, and selects its text for replacement. */
    void focusField(int delta) {
        if (fields.isEmpty()) return;
        focus = Math.floorMod(focus + delta, fields.size());
        var view = rows.get(focus).field;
        if (view.isClosed()) return;
        view.focus(FIELD + focus);
        var input = input(focus);
        if (input != null) input.selectAll();
    }

    /** The mounted kit input of the {@code index}th field (kit components are named after their node). */
    JTextComponent input(int index) { return CardView.find(rows.get(index).field, FIELD + index + ".input", JTextComponent.class); }

    Map<String, String> values() {
        var values = new LinkedHashMap<String, String>();
        for (int i = 0; i < fields.size(); i++) values.put(fields.get(i).name(), texts.get(i));
        return values;
    }

    void setError(String message) {
        error = message;
        render(errorView, errorSpec());
        errorRow.setVisible(message != null);
    }

    JPanel titleRow() { return titleRow; }
    JPanel errorRow() { return errorRow; }
    JPanel row(int index) { return rows.get(index).panel; }
    JComponent labelView(int index) { return rows.get(index).label; }
    KitView fieldView(int index) { return rows.get(index).field; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        views.forEach(KitView::close);
    }

    /** Retained views, laid out by the module's responsive native label/control composition. */
    private final class Row {
        final KitView label, field;
        final JPanel panel;
        Row(KitView label, KitView field) {
            this.label = label; this.field = field;
            panel = dev.jasper.ui.swing.SurfacePanel.labelledRow(kit, label, field);
        }
    }
}
