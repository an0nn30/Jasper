package dev.jasper.enterprise;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/** Spec 4.6: the Enterprise look is data, and its start does nothing. */
class EnterprisePluginTest {
    /** Spec 4.6: start cannot throw, so a failed Enterprise start is never the plugin's own doing; the look is data, so start does nothing. */
    @Test void startDoesNothing() throws Exception {
        byte[] bytes;
        try (var in = EnterprisePlugin.class.getResourceAsStream("EnterprisePlugin.class")) { bytes = in.readAllBytes(); }
        var start = ClassFile.of().parse(bytes).methods().stream().filter(method -> method.methodName().equalsString("start")).findFirst().orElseThrow();
        var steps = new ArrayList<String>();
        start.code().orElseThrow().forEach(element -> {
            if (element instanceof InvokeInstruction call) steps.add(call.owner().asInternalName() + "." + call.name().stringValue());
            if (element instanceof ThrowInstruction) steps.add("throw");
        });
        assertThat(steps).isEmpty();
    }
}
