package dev.jasper.editor.text;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;

class TextCodecTest {
    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] out = new byte[first.length + second.length];
        System.arraycopy(first, 0, out, 0, first.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }

    private static void roundTrips(byte[] original) throws Exception {
        DecodedText decoded = TextCodec.decode(original);
        assertThat(TextCodec.encode(decoded.text(), decoded.format())).isEqualTo(original);
    }

    @Test void utf8WithAndWithoutABomRoundTrips() throws Exception {
        byte[] plain = "name = \"jasper\"\nport = 8080\n".getBytes(UTF_8);
        DecodedText decoded = TextCodec.decode(plain);
        assertThat(decoded.text()).isEqualTo("name = \"jasper\"\nport = 8080\n");
        assertThat(decoded.format()).isEqualTo(new TextFormat(UTF_8, false, LineSeparator.LF));
        assertThat(decoded.mixed()).isFalse();
        roundTrips(plain);
        roundTrips("no trailing newline".getBytes(UTF_8));
        byte[] bom = concat(bytes(0xEF, 0xBB, 0xBF), "é\n".getBytes(UTF_8));
        assertThat(TextCodec.decode(bom).text()).isEqualTo("é\n");
        assertThat(TextCodec.decode(bom).format().label()).isEqualTo("UTF-8 BOM");
        roundTrips(bom);
    }

    @Test void crlfAndCrAreRecordedNormalisedAndWrittenBack() throws Exception {
        byte[] crlf = "a\r\nb\r\n".getBytes(UTF_8);
        assertThat(TextCodec.decode(crlf).text()).isEqualTo("a\nb\n");
        assertThat(TextCodec.decode(crlf).format().separator()).isEqualTo(LineSeparator.CRLF);
        roundTrips(crlf);
        byte[] cr = "a\rb".getBytes(UTF_8);
        assertThat(TextCodec.decode(cr).text()).isEqualTo("a\nb");
        assertThat(TextCodec.decode(cr).format().separator()).isEqualTo(LineSeparator.CR);
        roundTrips(cr);
    }

    @Test void mixedEndingsUseTheMajorityAndSavingConvertsEveryLine() throws Exception {
        DecodedText decoded = TextCodec.decode("a\nb\r\nc\r\nd".getBytes(UTF_8));
        assertThat(decoded.mixed()).isTrue();
        assertThat(decoded.format().separator()).isEqualTo(LineSeparator.CRLF);
        assertThat(new String(TextCodec.encode(decoded.text(), decoded.format()), UTF_8)).isEqualTo("a\r\nb\r\nc\r\nd");
        assertThat(TextCodec.decode("a\nb\r\n".getBytes(UTF_8)).format().separator()).as("a tie prefers LF").isEqualTo(LineSeparator.LF);
    }

    @Test void utf16WithABomRoundTripsInBothByteOrders() throws Exception {
        byte[] little = concat(bytes(0xFF, 0xFE), "héllo\r\n".getBytes(StandardCharsets.UTF_16LE));
        DecodedText decoded = TextCodec.decode(little);
        assertThat(decoded.text()).isEqualTo("héllo\n");
        assertThat(decoded.format().label()).isEqualTo("UTF-16LE BOM");
        roundTrips(little);
        roundTrips(concat(bytes(0xFE, 0xFF), "héllo\n".getBytes(StandardCharsets.UTF_16BE)));
    }

    @Test void invalidUtf8FallsBackToIso88591WhichRoundTripsEveryByte() throws Exception {
        byte[] latin = bytes('c', 'a', 'f', 0xE9, '\n', 0xFF, 0x80);
        DecodedText decoded = TextCodec.decode(latin);
        assertThat(decoded.format().charset()).isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(decoded.format().label()).isEqualTo("ISO-8859-1");
        assertThat(decoded.text()).startsWith("café\n");
        roundTrips(latin);
    }

    @Test void aNulByteWithoutAUtf16BomIsBinary() {
        assertThatExceptionOfType(BinaryFileException.class).isThrownBy(() -> TextCodec.decode(bytes('P', 'K', 3, 4, 0, 0)));
        assertThatCode(() -> TextCodec.decode(concat(bytes(0xFF, 0xFE), "a".getBytes(StandardCharsets.UTF_16LE)))).doesNotThrowAnyException();
    }

    @Test void anEmptyFileRoundTripsToZeroBytes() throws Exception {
        DecodedText decoded = TextCodec.decode(new byte[0]);
        assertThat(decoded.text()).isEmpty();
        assertThat(decoded.format()).isEqualTo(TextFormat.DEFAULT);
        assertThat(TextCodec.encode("", decoded.format())).isEmpty();
    }

    @Test void textTheCharsetCannotHoldNamesItsLine() {
        var latin = new TextFormat(StandardCharsets.ISO_8859_1, false, LineSeparator.LF);
        assertThatExceptionOfType(UnencodableException.class).isThrownBy(() -> TextCodec.encode("ok\nsmile \uD83D\uDE00\n", latin))
            .satisfies(failure -> {
                assertThat(failure.line()).isEqualTo(2);
                assertThat(failure.charset()).isEqualTo(StandardCharsets.ISO_8859_1);
            });
    }

    @Test void tabIndentationIsDetectedByMajority() {
        assertThat(TextCodec.indentsWithTabs("a:\n\tb\n\tc\n  d\n")).isTrue();
        assertThat(TextCodec.indentsWithTabs("a:\n  b\n  c\n\td\n")).isFalse();
        assertThat(TextCodec.indentsWithTabs("flat\n")).isFalse();
    }
}
