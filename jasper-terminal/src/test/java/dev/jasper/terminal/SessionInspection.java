package dev.jasper.terminal;
import com.jediterm.terminal.model.TerminalTextBuffer;

/** Test-only access to the actual owner for lock, storage and allocation assertions. */
final class SessionInspection {
    static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
    static JediTermEngine engine(TerminalSession session) throws ReflectiveOperationException {
        return (JediTermEngine) field(session.internalAccess(), "engine");
    }
    static TerminalTextBuffer buffer(TerminalSession session) throws ReflectiveOperationException {
        return (TerminalTextBuffer) field(engine(session), "buffer");
    }
    static AbsoluteRowState rows(TerminalSession session) throws ReflectiveOperationException {
        return (AbsoluteRowState) field(engine(session), "rowState");
    }
}
