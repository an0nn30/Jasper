package dev.jasper.browser.tools;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hosts a public page's save never fetches from (plan 7's F12 and rulings D43 and D44): this computer, a link, a
 * private network, benchmarking's 198.18.0.0/15, NAT64's local-use 64:ff9b:1::/48, the broadcast address, a 6to4
 * address of one of those and an unspecified address, in any spelling, with nothing looked up. Pure.
 */
class PrivateHostsTest {
    @Test void eachRefusedRangeIsRefusedInAnySpelling() {
        for (String host : List.of("127.0.0.1", "127.1", "0177.0.0.1", "2130706433", "localhost", "LOCALHOST.", "a.localhost", "[::1]",
                "[::ffff:127.0.0.1]", "0.0.0.0", "[::]", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1", "100.64.0.1",
                "100.127.255.255", "169.254.169.254", "[fe80::1]", "[fe80::1%25en0]", "[fc00::1]", "[fd12:3456::1]", "[fec0::1]",
                "198.18.0.1", "198.19.255.255", "[::ffff:198.18.0.1]", "[64:ff9b:1::1]", "[64:ff9b:1:ffff::7f00:1]", "[64:ff9b::7f00:1]",
                "[::127.0.0.1]", "255.255.255.255", "[2002:7f00:1::1]", "[2002:c0a8:101::1]", "[2002:a9fe:a9fe::]"))
            assertThat(PrivateHosts.refused(host)).as(host).isTrue();
    }

    @Test void publicHostsAreNot() {
        for (String host : List.of("8.8.8.8", "93.184.216.34", "198.17.255.255", "198.20.0.1", "172.32.0.1", "100.128.0.1", "[2606:4700::1]",
                "[64:ff9b::808:808]", "[64:ff9b:2::1]", "[2002:808:808::1]", "255.255.255.254", "example.com", "localhost.example.com"))
            assertThat(PrivateHosts.refused(host)).as(host).isFalse();
    }

    @Test void anAddressIsJudgedAsItsHostIs() throws Exception {
        assertThat(PrivateHosts.refused(InetAddress.ofLiteral("198.18.0.1"))).isTrue();
        assertThat(PrivateHosts.refused(InetAddress.ofLiteral("64:ff9b:1::a00:1"))).isTrue();
        assertThat(PrivateHosts.refused(InetAddress.ofLiteral("93.184.216.34"))).isFalse();
        assertThat(PrivateHosts.literal("0177.1")).isTrue();
        assertThat(PrivateHosts.literal("example.com")).isFalse();
    }
}
