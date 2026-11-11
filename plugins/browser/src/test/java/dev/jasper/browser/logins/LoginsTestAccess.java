package dev.jasper.browser.logins;

import dev.jasper.vault.api.VaultApi;

/** Test-only access to the {@code logins} package, for tests outside it. */
public final class LoginsTestAccess {
    private LoginsTestAccess() { }

    /**
     * The Browser's logins from {@code vault}, as {@link VaultLogins#find} gives them once the Vault's service is found:
     * for item tests over {@link FakeVault#api()}.
     *
     * @param vault a stand-in Vault
     * @return the logins
     */
    public static VaultLogins logins(VaultApi vault) {
        return new VaultLogins(vault);
    }
}
