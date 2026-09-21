package dev.jasper.terminal.internal.emulation;

import dev.jasper.terminal.internal.text.AbsoluteRowState;
import dev.jasper.terminal.session.TerminalSession;
import com.jediterm.terminal.model.TerminalTextBuffer;

/** Test-only access to the actual owner for lock, storage and allocation assertions. */
public final class SessionInspection {
    public static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
    public static JediTermEngine engine(TerminalSession session) throws ReflectiveOperationException {
        return (JediTermEngine) field(session.internalAccess(), "engine");
    }
    public static TerminalTextBuffer buffer(TerminalSession session) throws ReflectiveOperationException {
        return (TerminalTextBuffer) field(engine(session), "buffer");
    }
    public static AbsoluteRowState rows(TerminalSession session) throws ReflectiveOperationException {
        return (AbsoluteRowState) field(engine(session), "rowState");
    }
    /** Controlled lock access without exposing vendor types to component tests. */
    public static BufferProbe probe(TerminalSession session) throws ReflectiveOperationException {
        return new BufferProbe(buffer(session));
    }
    public static final class BufferProbe {
        private final TerminalTextBuffer buffer;
        private BufferProbe(TerminalTextBuffer buffer) { this.buffer = buffer; }
        public void lock() { buffer.lock(); }
        public void unlock() { buffer.unlock(); }
    }
    /** Count line reads after the reader has stopped; close restores the storage. */
    public static LineReads countLineReads(TerminalSession session) throws ReflectiveOperationException {
        return new LineReads(buffer(session));
    }
    public static final class LineReads implements AutoCloseable {
        private final TerminalTextBuffer buffer;
        private final java.lang.reflect.Field field;
        private final Object original;
        private final java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        private LineReads(TerminalTextBuffer buffer) throws ReflectiveOperationException {
            this.buffer = buffer;
            field = TerminalTextBuffer.class.getDeclaredField("screenLinesStorage");
            field.setAccessible(true);
            original = field.get(buffer);
            Object counted = java.lang.reflect.Proxy.newProxyInstance(field.getType().getClassLoader(),
                new Class<?>[] {field.getType()}, (proxy, method, args) -> {
                    if (method.getName().equals("get")) reads.incrementAndGet();
                    return method.invoke(original, args);
                });
            field.set(buffer, counted);
        }
        public int get() { return reads.get(); }
        @Override public void close() throws IllegalAccessException { field.set(buffer, original); }
    }
}
