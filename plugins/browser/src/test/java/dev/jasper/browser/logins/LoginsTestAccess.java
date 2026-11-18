package dev.jasper.browser.logins;

import dev.jasper.browser.dialogs.SignInDialog.Outcome;
import dev.jasper.browser.page.Page;
import dev.jasper.vault.api.VaultApi;
import java.util.function.Consumer;

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

    /**
     * What answers {@code page}'s shown challenge with an outcome, as its dialog would, also once the page's sign-ins
     * have ended: for tests that hand in a password array of their own and see it zeroed.
     *
     * @param signIns the sign-ins
     * @param page a page with a challenge shown
     * @return the answer
     * @throws IllegalStateException when none shows
     */
    public static Consumer<Outcome> answerShown(HttpSignIns signIns, Page page) {
        return signIns.shownAnswer(page);
    }
}
