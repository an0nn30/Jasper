package dev.jasper.vault.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

/** Shared modal form behavior, including failure recovery and deterministic secret cleanup. */
@SuppressWarnings("this-escape") // Swing layout setup; subclasses do not override construction-time methods.
public class EditorForm extends JPanel implements AutoCloseable {
    final JPanel fields = new JPanel(new GridBagLayout());
    private int fieldRow;
    final JButton save = new JButton("Save"), cancel = new JButton("Cancel");
    final JLabel error = new JLabel(" ");
    private final List<SecretDocument> secrets = new ArrayList<>();
    private boolean closed, busy;
    private final Runnable dismiss;

    protected EditorForm(Runnable dismiss) {
        super(new BorderLayout(8, 8)); this.dismiss = dismiss;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(fields, BorderLayout.CENTER);
        var footer = new JPanel(new BorderLayout(8, 8));
        footer.add(error, BorderLayout.NORTH);
        var buttons = new JPanel(); buttons.add(cancel); buttons.add(save);
        footer.add(buttons, BorderLayout.SOUTH); add(footer, BorderLayout.SOUTH);
        cancel.addActionListener(event -> { close(); dismiss.run(); });
    }
    protected final void field(String label, JComponent component) {
        var at = new GridBagConstraints();
        at.gridy = fieldRow++; at.gridx = 0; at.anchor = GridBagConstraints.NORTHWEST;
        at.insets = new Insets(4, 0, 4, 12);
        fields.add(new JLabel(label), at);
        at.gridx = 1; at.weightx = 1; at.insets = new Insets(4, 0, 4, 0);
        at.fill = GridBagConstraints.HORIZONTAL;
        if (component instanceof javax.swing.JScrollPane) { at.weighty = 1; at.fill = GridBagConstraints.BOTH; }
        fields.add(component, at);
    }
    protected final SecretDocument secret() { var document = new SecretDocument(); secrets.add(document); return document; }
    protected final JPasswordField passwordField(SecretDocument document) {
        var field = new JPasswordField(24); field.setDocument(document); return field;
    }
    protected final void submit(Supplier<CompletableFuture<Void>> action) {
        save.addActionListener(event -> {
            if (closed || busy) return;
            busy = true; enabled(fields, false); save.setEnabled(false); error.setText("Saving...");
            CompletableFuture<Void> pending;
            try { pending = action.get(); }
            catch (RuntimeException failure) { pending = CompletableFuture.failedFuture(failure); }
            pending.whenComplete((ignored, failure) -> {
                busy = false;
                if (closed) return;
                if (failure == null) { close(); dismiss.run(); return; }
                enabled(fields, true); save.setEnabled(true);
                error.setText(message(failure));
            });
        });
    }
    static String message(Throwable failure) {
        while (failure.getCause() != null && (failure instanceof java.util.concurrent.CompletionException
            || failure instanceof java.util.concurrent.ExecutionException)) failure = failure.getCause();
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
    private static void enabled(Container container, boolean value) {
        for (Component child : container.getComponents()) {
            child.setEnabled(value); if (child instanceof Container nested) enabled(nested, value);
        }
    }
    @Override public void close() { closed = true; secrets.forEach(SecretDocument::clear); }
}
