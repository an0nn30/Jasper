package dev.jasper.app.bootstrap;

import java.awt.GraphicsEnvironment;
import java.io.PrintStream;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * How a launch that ends without starting says why (workspace-tabs spec 5.3): an owner that never answered, a
 * replacement whose predecessor never quit, a home that cannot be locked. On standard error and in the log and, with a
 * display, in a dialog in the system look: no Jasper theme exists yet. Only a launch that will not own its home calls
 * it, so the toolkit it starts serves this dialog alone.
 */
final class LaunchFailure {
    private static final System.Logger LOG = System.getLogger(LaunchFailure.class.getName());

    private LaunchFailure() { }

    /**
     * A reporter for {@link SingleInstance#claim}.
     *
     * @param error where the message is printed
     * @return reports a message to {@code error}, the log and, unless the JVM is headless, a dialog
     */
    static Consumer<String> reporter(PrintStream error) {
        Objects.requireNonNull(error, "error");
        return message -> {
            LOG.log(System.Logger.Level.ERROR, message);
            error.println(message);
            if (!GraphicsEnvironment.isHeadless()) show(message);
        };
    }

    private static void show(String message) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (ReflectiveOperationException | UnsupportedLookAndFeelException unavailable) {
                    // The toolkit's own look shows the message just as well.
                }
                JOptionPane.showMessageDialog(null, message, "Jasper", JOptionPane.ERROR_MESSAGE);
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (java.lang.reflect.InvocationTargetException | RuntimeException unavailable) {
            LOG.log(System.Logger.Level.WARNING, "Could not show why the launch ended", unavailable);
        }
    }
}
