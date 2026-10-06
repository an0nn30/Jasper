package dev.jasper.vault.service;

import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.model.Grant;
import java.util.UUID;
import java.util.function.Consumer;

/** A consent prompt over synthetic metadata whose answers go to {@code decisions}. */
public final class GrantCaptureSupport {
    private GrantCaptureSupport() { }
    public static GrantPrompt prompt(String consumer, String name, String subtitle, Consumer<GrantPrompt.Decision> decisions) {
        UUID id = UUID.randomUUID();
        return new GrantPrompt(new Grant("dev.jasper.ssh", id), consumer, new CredentialDescriptor(id, name, subtitle, Kind.ACCOUNT_PASSWORD), null,
            (prompt, decision) -> decisions.accept(decision));
    }
}
