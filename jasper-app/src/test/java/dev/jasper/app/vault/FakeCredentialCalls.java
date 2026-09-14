package dev.jasper.app.vault;

import com.sun.jna.Pointer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class FakeCredentialCalls extends NativeCredentialCalls {
    final List<String> calls = new ArrayList<>();
    final Map<String, Function<Object[], Object>> handlers = new HashMap<>();
    int error;
    void on(String name, Function<Object[], Object> handler) { handlers.put(name, handler); }
    @Override Object invoke(String name, Class<?> result, Object... args) {
        calls.add(name);
        Function<Object[], Object> handler = handlers.get(name);
        if (handler == null) throw new AssertionError("Unexpected native call: " + name);
        return handler.apply(args);
    }
    @Override int lastError() { return error; }
    @Override Pointer symbol(String name) { return (Pointer) invoke(name, Pointer.class); }
    @Override Pointer function(String name) { return (Pointer) invoke(name, Pointer.class); }
    @Override void require(String... names) { throw new AssertionError("Native loading in fake test"); }
}
