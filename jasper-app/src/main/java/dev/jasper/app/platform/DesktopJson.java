package dev.jasper.app.platform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Strict, bounded JSON for the flat scalar desktop protocol, independent of theme JSON. */
final class DesktopJson {
    private final String text;
    private int at;

    private DesktopJson(String text) {
        if (text.length() > DesktopProtocol.MAX_MESSAGE) throw invalid();
        this.text = text;
    }

    static Map<String, Object> parse(String text) {
        return new DesktopJson(text).object();
    }

    private Map<String, Object> object() {
        var values = new LinkedHashMap<String, Object>();
        space(); require('{'); space();
        if (!take('}')) {
            do {
                space();
                String key = string();
                if (values.size() == 32 || values.containsKey(key)) throw invalid();
                space(); require(':'); space();
                Object value = scalar();
                if (key.equals("reason") && value instanceof String reason && reason.codePointCount(0, reason.length()) > 1024)
                    throw invalid();
                values.put(key, value);
                space();
                if (take('}')) break;
                require(',');
            } while (true);
        }
        space();
        if (at != text.length()) throw invalid();
        return Collections.unmodifiableMap(values);
    }

    private Object scalar() {
        if (at == text.length()) throw invalid();
        return switch (text.charAt(at)) {
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Object literal(String token, Object value) {
        if (!text.startsWith(token, at)) throw invalid();
        at += token.length();
        return value;
    }

    private Number number() {
        int start = at;
        take('-');
        if (!take('0')) digits();
        boolean floating = false;
        if (take('.')) { floating = true; digits(); }
        if (take('e') || take('E')) {
            floating = true;
            if (!take('+')) take('-');
            digits();
        }
        String token = text.substring(start, at);
        try {
            if (!floating) return Long.valueOf(token);
            double value = Double.parseDouble(token);
            if (!Double.isFinite(value)) throw invalid();
            return value;
        } catch (NumberFormatException e) { throw invalid(); }
    }

    private void digits() {
        int start = at;
        while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') at++;
        if (start == at) throw invalid();
    }

    private String string() {
        require('"');
        var result = new StringBuilder();
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') {
                String value = result.toString();
                for (int i = 0; i < value.length(); i++) {
                    char unit = value.charAt(i);
                    if (Character.isHighSurrogate(unit)) {
                        if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid();
                    } else if (Character.isLowSurrogate(unit)) throw invalid();
                }
                return value;
            }
            if (c < 0x20) throw invalid();
            if (c == '\\') {
                if (at == text.length()) throw invalid();
                c = switch (text.charAt(at++)) {
                    case '"' -> '"'; case '\\' -> '\\'; case '/' -> '/';
                    case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                    case 'u' -> unicode();
                    default -> throw invalid();
                };
            }
            result.append(c);
            if (result.length() > DesktopProtocol.MAX_MESSAGE) throw invalid();
        }
        throw invalid();
    }

    private char unicode() {
        if (text.length() - at < 4) throw invalid();
        int value = 0;
        for (int i = 0; i < 4; i++) {
            char c = text.charAt(at++);
            int digit = c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'f' ? c - 'a' + 10 : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
            if (digit < 0) throw invalid();
            value = value * 16 + digit;
        }
        return (char) value;
    }

    private void space() {
        while (at < text.length() && (text.charAt(at) == ' ' || text.charAt(at) == '\n' || text.charAt(at) == '\r' || text.charAt(at) == '\t')) at++;
    }
    private boolean take(char c) {
        if (at < text.length() && text.charAt(at) == c) { at++; return true; }
        return false;
    }
    private void require(char c) { if (!take(c)) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid desktop protocol JSON"); }
}
