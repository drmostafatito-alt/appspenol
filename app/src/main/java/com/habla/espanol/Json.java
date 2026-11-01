package com.habla.espanol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader used for the offline curriculum asset.
 *
 * The app ships a single JSON file and has no third-party dependencies, so a
 * compact recursive-descent parser keeps the APK small and lets the same code
 * run in plain JVM unit tests (no Android runtime required).
 */
final class Json {
    private final String text;
    private int pos;

    private Json(String text) { this.text = text; }

    static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("JSON text is null");
        Json parser = new Json(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos != text.length()) throw parser.error("trailing content");
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) throw new IllegalArgumentException("JSON root must be an object");
        return (Map<String, Object>) value;
    }

    private Object readValue() {
        if (pos >= text.length()) throw error("unexpected end of input");
        char c = text.charAt(pos);
        switch (c) {
            case '{': return readObject();
            case '[': return readArray();
            case '"': return readString();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipWhitespace();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWhitespace();
            if (peek() != '"') throw error("expected object key");
            String key = readString();
            skipWhitespace();
            if (peek() != ':') throw error("expected ':'");
            pos++;
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') { pos++; continue; }
            if (c == '}') { pos++; return map; }
            throw error("expected ',' or '}'");
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipWhitespace();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') { pos++; continue; }
            if (c == ']') { pos++; return list; }
            throw error("expected ',' or ']'");
        }
    }

    private String readString() {
        StringBuilder out = new StringBuilder();
        pos++;
        while (true) {
            if (pos >= text.length()) throw error("unterminated string");
            char c = text.charAt(pos++);
            if (c == '"') return out.toString();
            if (c != '\\') { out.append(c); continue; }
            if (pos >= text.length()) throw error("unterminated escape");
            char esc = text.charAt(pos++);
            switch (esc) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    if (pos + 4 > text.length()) throw error("bad unicode escape");
                    out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default: throw error("bad escape \\" + esc);
            }
        }
    }

    private Double readNumber() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
        if (start == pos) throw error("unexpected character '" + text.charAt(start) + "'");
        return Double.valueOf(text.substring(start, pos));
    }

    private void expect(String word) {
        if (!text.startsWith(word, pos)) throw error("expected " + word);
        pos += word.length();
    }

    private char peek() {
        if (pos >= text.length()) throw error("unexpected end of input");
        return text.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++;
            else break;
        }
    }

    private IllegalArgumentException error(String message) {
        int line = 1;
        for (int i = 0; i < Math.min(pos, text.length()); i++) if (text.charAt(i) == '\n') line++;
        return new IllegalArgumentException("JSON error at line " + line + " (offset " + pos + "): " + message);
    }

    // ---- typed helpers used by the curriculum loader ----

    static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : (value instanceof String ? (String) value : String.valueOf(value));
    }

    static String strOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String && !((String) value).isEmpty() ? (String) value : null;
    }

    static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        return fallback;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof List ? (List<Object>) value : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    static List<String> strings(Map<String, Object> map, String key) {
        List<String> out = new ArrayList<>();
        for (Object value : list(map, key)) if (value instanceof String) out.add((String) value);
        return out;
    }
}
