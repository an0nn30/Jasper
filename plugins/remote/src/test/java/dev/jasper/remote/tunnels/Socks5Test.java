package dev.jasper.remote.tunnels;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class Socks5Test {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }

    Socks5.Target accept(byte[] request) throws IOException { return Socks5.accept(new ByteArrayInputStream(request), out); }

    @Test void aDomainNameIsPassedOnUnresolved() throws IOException {
        byte[] name = "db.internal".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var request = new ByteArrayOutputStream();
        request.writeBytes(bytes(5, 2, 0, 2, 5, 1, 0, 3, name.length));
        request.writeBytes(name);
        request.writeBytes(bytes(0x15, 0x38));
        assertThat(accept(request.toByteArray())).isEqualTo(new Socks5.Target("db.internal", 5432));
        assertThat(out.toByteArray()).as("no authentication chosen").containsExactly(5, 0);
    }

    @Test void ipv4AndIpv6AddressesAreRead() throws IOException {
        assertThat(accept(bytes(5, 1, 0, 5, 1, 0, 1, 127, 0, 0, 1, 0x1F, 0x90))).isEqualTo(new Socks5.Target("127.0.0.1", 8080));
        out.reset();
        assertThat(accept(bytes(5, 1, 0, 5, 1, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 22)))
            .isEqualTo(new Socks5.Target("0:0:0:0:0:0:0:1", 22));
    }

    @Test void aClientThatInsistsOnAuthenticationIsTurnedAway() {
        assertThatThrownBy(() -> accept(bytes(5, 1, 2))).isInstanceOf(Socks5.Refused.class);
        assertThat(out.toByteArray()).containsExactly(5, 0xFF);
    }

    @Test void onlyConnectIsServed() {
        assertThatThrownBy(() -> accept(bytes(5, 1, 0, 5, 2, 0, 1, 127, 0, 0, 1, 0, 80))).isInstanceOf(Socks5.Refused.class);
        assertThat(out.toByteArray()).containsExactly(5, 0, 5, Socks5.COMMAND_NOT_SUPPORTED, 0, 1, 0, 0, 0, 0, 0, 0);
    }

    @Test void anUnknownAddressTypeIsRefused() {
        assertThatThrownBy(() -> accept(bytes(5, 1, 0, 5, 1, 0, 9))).isInstanceOf(Socks5.Refused.class);
        assertThat(out.toByteArray()).containsExactly(5, 0, 5, Socks5.ADDRESS_NOT_SUPPORTED, 0, 1, 0, 0, 0, 0, 0, 0);
    }

    @Test void socks4IsNotSpokenAndTheReplyCarriesItsCode() throws IOException {
        assertThatThrownBy(() -> accept(bytes(4, 1, 0, 80, 127, 0, 0, 1, 0))).isInstanceOf(Socks5.Refused.class);
        assertThat(out.toByteArray()).isEmpty();
        Socks5.reply(out, Socks5.HOST_UNREACHABLE);
        assertThat(out.toByteArray()).containsExactly(5, 4, 0, 1, 0, 0, 0, 0, 0, 0);
    }
}
