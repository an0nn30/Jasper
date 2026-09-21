package dev.jasper.app.testsupport;

/** Runs whole callback-driven test methods under the production EDT ownership contract. */
public final class EdtTestExtension implements org.junit.jupiter.api.extension.InvocationInterceptor {
    @Override public void interceptTestMethod(Invocation<Void> invocation,
            org.junit.jupiter.api.extension.ReflectiveInvocationContext<java.lang.reflect.Method> method,
            org.junit.jupiter.api.extension.ExtensionContext context) throws Throwable {
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try { invocation.proceed(); } catch (Throwable thrown) { failure.set(thrown); }
        });
        if (failure.get() != null) throw failure.get();
    }
}
