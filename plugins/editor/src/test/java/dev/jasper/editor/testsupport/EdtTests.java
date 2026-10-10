package dev.jasper.editor.testsupport;

import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

/** Runs UI-owned test setup, behavior and teardown on the EDT, including the functional kit. */
public final class EdtTests implements InvocationInterceptor {
    private static void run(Invocation<Void> invocation) throws Throwable {
        Throwable[] failure = {null};
        Kits.edt(() -> { try { invocation.proceed(); } catch (Throwable caught) { failure[0] = caught; } });
        if (failure[0] != null) throw failure[0];
    }
    @Override public void interceptTestMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method, ExtensionContext context) throws Throwable { run(call); }
    @Override public void interceptBeforeEachMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method, ExtensionContext context) throws Throwable { run(call); }
    @Override public void interceptAfterEachMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method, ExtensionContext context) throws Throwable { run(call); }
}
