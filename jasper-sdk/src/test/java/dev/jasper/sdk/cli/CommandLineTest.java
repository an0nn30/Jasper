package dev.jasper.sdk.cli;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CommandLineTest {
    @Test void theClientIsADefaultThatAnOlderHostLeavesEmpty() throws Exception {
        assertThat(CommandLine.class.getMethod("client").isDefault()).isTrue();
        CommandLine older = (CommandLine) java.lang.reflect.Proxy.newProxyInstance(CommandLine.class.getClassLoader(), new Class<?>[]{CommandLine.class},
            (proxy, method, args) -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args) : null);
        assertThat(older.client()).as("a host that names no client").isEmpty();
    }
}
