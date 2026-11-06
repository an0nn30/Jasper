package dev.jasper.browser.page;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The frames Jasper's page scripts stay out of (spec section 5): the challenge providers' frames, by their address's
 * host, and for reCAPTCHA's two hosts its path, as Chromium names a frame's address. Any other address, and anything
 * that is no http or https address, is no challenge frame.
 */
class ChallengeFramesTest {
    @Test void theChallengeProvidersFramesAreChallengeFrames() {
        for (String address : List.of(
                "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b/turnstile/if/ov2/av0/rcv0/0/abc/light/normal",
                "http://challenges.cloudflare.com/",
                "HTTPS://Challenges.Cloudflare.COM:443/x",
                "https://challenges.cloudflare.com./x",
                "https://hcaptcha.com/captcha/v1/abc/static/hcaptcha.html#frame=checkbox",
                "https://newassets.hcaptcha.com/captcha/v1/abc/static/hcaptcha.html",
                "https://a.b.hcaptcha.com:8443/",
                "https://www.google.com/recaptcha/api2/anchor?k=key",
                "https://www.google.com/recaptcha/",
                "https://www.recaptcha.net/recaptcha/api2/bframe?k=key",
                "https://client-api.arkoselabs.com/fc/gc/?token=abc",
                "https://api.funcaptcha.com/fc/gt2/public_key/abc",
                "https://user:secret@challenges.cloudflare.com/"))
            assertThat(ChallengeFrames.is(address)).as(address).isTrue();
    }

    @Test void anyOtherAddressIsNoChallengeFrame() {
        for (String address : Arrays.asList(
                "https://www.google.com/search?q=recaptcha",
                "https://example.com",
                "https://www.google.com/",
                "https://www.google.com/recaptcha",
                "https://www.google.com/search/recaptcha/",
                "https://www.google.com/?q=/recaptcha/",
                "https://google.com/recaptcha/api2/anchor",
                "https://www.recaptcha.net/",
                "https://arkoselabs.com/",
                "https://funcaptcha.com/fc/",
                "https://notchallenges.cloudflare.com/",
                "https://cloudflare.com/",
                "https://evil-hcaptcha.com/",
                "https://hcaptcha.com.evil.example/",
                "https://challenges.cloudflare.com@evil.example/",
                "https://www.google.com@evil.example/recaptcha/",
                "https://evil.example\\@challenges.cloudflare.com/",
                "https://evil.example/?frame=https://challenges.cloudflare.com/",
                "https://evil.example/recaptcha/",
                "ftp://challenges.cloudflare.com/",
                "file:///recaptcha/",
                "jasper-about:welcome",
                "about:blank",
                "data:text/html,challenges.cloudflare.com",
                "",
                null))
            assertThat(ChallengeFrames.is(address)).as(String.valueOf(address)).isFalse();
    }
}
