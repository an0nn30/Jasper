package dev.jasper.enterprise;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.enterprise.api.Workstation;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Spec 14.2: Workstation is published while Enterprise runs, and start can do nothing else. */
class EnterprisePluginTest {
    static final PluginInfo ENTERPRISE = new PluginInfo("dev.jasper.enterprise", "Enterprise", "0.1.0", Set.of("presentation.provide"));
    static final PluginInfo CONSUMER = new PluginInfo("dev.example.consumer", "Consumer", "1.0.0", Set.of());

    @Test void aPluginThatOptionallyRequiresEnterpriseFindsWorkstation() {
        try (var host = new FakePluginHost()) {
            host.start(ENTERPRISE, Set.of(), Set.of(), new EnterprisePlugin());
            var found = new AtomicReference<Optional<Workstation>>();
            host.start(CONSUMER, Set.of(), Set.of("dev.jasper.enterprise"), context -> found.set(context.services().find(Workstation.class)));
            assertThat(found.get()).isPresent();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void withoutEnterpriseAConsumerFindsNone() {
        try (var host = new FakePluginHost()) {
            var found = new AtomicReference<Optional<Workstation>>();
            host.start(CONSUMER, Set.of(), Set.of("dev.jasper.enterprise"), context -> found.set(context.services().find(Workstation.class)));
            assertThat(found.get()).isEmpty();
        }
    }

    /** Spec 4.6: start cannot throw, so a failed Enterprise start is never the plugin's own doing. */
    @Test void startOnlyPublishesWorkstation() throws Exception {
        byte[] bytes;
        try (var in = EnterprisePlugin.class.getResourceAsStream("EnterprisePlugin.class")) { bytes = in.readAllBytes(); }
        var start = ClassFile.of().parse(bytes).methods().stream().filter(method -> method.methodName().equalsString("start")).findFirst().orElseThrow();
        var steps = new ArrayList<String>();
        start.code().orElseThrow().forEach(element -> {
            if (element instanceof InvokeInstruction call) steps.add(call.owner().asInternalName() + "." + call.name().stringValue());
            if (element instanceof ThrowInstruction) steps.add("throw");
        });
        assertThat(steps).containsExactly("dev/jasper/sdk/plugin/PluginContext.services", "dev/jasper/enterprise/EnterprisePlugin$1.<init>",
            "dev/jasper/sdk/services/Services.publish");
    }
}
