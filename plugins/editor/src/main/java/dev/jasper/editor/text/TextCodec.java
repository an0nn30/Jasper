package dev.jasper.editor.text;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Bytes to editable text and back (spec 4.2). Opening a file and saving it unedited writes the same bytes. */
public final class TextCodec {
    /** How much of a file without a UTF-16 byte-order mark is searched for a NUL byte. */
    static final int BINARY_PROBE = 8192;
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF16LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] UTF16BE_BOM = {(byte) 0xFE, (byte) 0xFF};

    private TextCodec() { }

    /**
     * Decodes a file: a byte-order mark selects its charset; otherwise strict UTF-8, falling back to ISO-8859-1,
     * which maps every byte. Line endings become {@code \n}; the most common one is recorded.
     *
     * @param bytes the file's content
     * @return the text and how to save it
     * @throws BinaryFileException when a NUL byte appears in the first 8 KiB without a UTF-16 byte-order mark
     */
    public static DecodedText decode(byte[] bytes) throws BinaryFileException {
        Charset charset = StandardCharsets.UTF_8;
        int skip = 0;
        boolean bom = true;
        if (startsWith(bytes, UTF8_BOM)) skip = UTF8_BOM.length;
        else if (startsWith(bytes, UTF16LE_BOM)) { charset = StandardCharsets.UTF_16LE; skip = UTF16LE_BOM.length; }
        else if (startsWith(bytes, UTF16BE_BOM)) { charset = StandardCharsets.UTF_16BE; skip = UTF16BE_BOM.length; }
        else {
            bom = false;
            for (int i = 0; i < Math.min(bytes.length, BINARY_PROBE); i++) if (bytes[i] == 0) throw new BinaryFileException();
        }
        String raw;
        try {
            raw = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, skip, bytes.length - skip)).toString();
        } catch (CharacterCodingException malformed) {
            charset = StandardCharsets.ISO_8859_1;
            bom = false;
            raw = new String(bytes, StandardCharsets.ISO_8859_1);
        }
        int lf = 0, crlf = 0, cr = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\n') lf++;
            else if (c == '\r') {
                if (i + 1 < raw.length() && raw.charAt(i + 1) == '\n') { crlf++; i++; }
                else cr++;
            }
        }
        LineSeparator separator = crlf > lf && crlf >= cr ? LineSeparator.CRLF
            : cr > lf && cr > crlf ? LineSeparator.CR : LineSeparator.LF;
        boolean mixed = (lf > 0 ? 1 : 0) + (crlf > 0 ? 1 : 0) + (cr > 0 ? 1 : 0) > 1;
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        return new DecodedText(text, new TextFormat(charset, bom, separator), mixed);
    }

    /**
     * Encodes text with {@code \n} line breaks in a format, adding its byte-order mark.
     *
     * @param text the text
     * @param format how to store it
     * @return the file's bytes
     * @throws UnencodableException when the charset cannot store a character; it names the first such line
     */
    public static byte[] encode(String text, TextFormat format) throws UnencodableException {
        String stored = format.separator() == LineSeparator.LF ? text : text.replace("\n", format.separator().chars());
        CharsetEncoder encoder = format.charset().newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer body;
        try { body = encoder.encode(CharBuffer.wrap(stored)); }
        catch (CharacterCodingException failure) {
            throw new UnencodableException(format.charset(), firstUnencodableLine(text, format.charset()));
        }
        byte[] mark = !format.bom() ? new byte[0]
            : format.charset().equals(StandardCharsets.UTF_16LE) ? UTF16LE_BOM
            : format.charset().equals(StandardCharsets.UTF_16BE) ? UTF16BE_BOM
            : format.charset().equals(StandardCharsets.UTF_8) ? UTF8_BOM : new byte[0];
        int length = body.remaining();
        byte[] out = new byte[mark.length + length];
        System.arraycopy(mark, 0, out, 0, mark.length);
        body.get(out, mark.length, length);
        return out;
    }

    /**
     * @param text text with {@code \n} line breaks
     * @return whether more indented lines start with a tab than with a space
     */
    public static boolean indentsWithTabs(String text) {
        int tabs = 0, spaces = 0;
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("\t")) tabs++;
            else if (line.startsWith(" ")) spaces++;
        }
        return tabs > spaces;
    }

    static int firstUnencodableLine(String text, Charset charset) {
        CharsetEncoder encoder = charset.newEncoder();
        int line = 1;
        for (int i = 0; i < text.length(); ) {
            int point = text.codePointAt(i);
            if (point == '\n') line++;
            else if (!encoder.canEncode(new String(Character.toChars(point)))) return line;
            i += Character.charCount(point);
        }
        return line;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }
}
