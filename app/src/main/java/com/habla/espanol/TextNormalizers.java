package com.habla.espanol;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text normalisation shared by answer checking, search and SRS bookkeeping.
 * Pure Java (no Android) so the grading rules are unit-testable.
 */
final class TextNormalizers {
    private TextNormalizers() {}

    private static final char[] ARABIC_DIACRITICS = {
            0x0610, 0x0611, 0x0612, 0x0613, 0x0614, 0x0615, 0x061A,
            0x064B, 0x064C, 0x064D, 0x064E, 0x064F, 0x0650, 0x0651, 0x0652, 0x0653,
            0x0654, 0x0655, 0x0656, 0x0657, 0x0658, 0x0670, 0x06D6, 0x06DC, 0x06DF,
            0x06E0, 0x06E1, 0x06E2, 0x06E7, 0x06E8, 0x06EA, 0x06EB, 0x06EC, 0x06ED
    };

    /** Lowercase, accent-free Spanish key ("¿Cómo estás?" -> "como estas"). */
    static String spanishKey(String text) {
        if (text == null) return "";
        String value = text.toLowerCase(Locale.ROOT).replace("ñ", "\u0001");
        value = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        value = value.replace('\u0001', 'ñ');
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean keep = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ' ' || c == '\'' || c == '-' || c == 'ñ';
            out.append(keep ? c : ' ');
        }
        return collapse(out.toString());
    }

    /** Arabic key: diacritics and tatweel removed, alef/ya/ta-marbuta unified. */
    static String arabicKey(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isDiacritic(c) || c == 0x0640) continue;
            switch (c) {
                case 'أ': case 'إ': case 'آ': case 'ٱ': c = 'ا'; break;
                case 'ى': case 'ئ': case 'ؤ': c = 'ي'; break;
                case 'ة': c = 'ه'; break;
                default: break;
            }
            boolean keep = (c >= 0x0600 && c <= 0x06FF) || (c >= '0' && c <= '9') || c == ' ';
            out.append(keep ? c : ' ');
        }
        return collapse(out.toString());
    }

    private static boolean isDiacritic(char c) {
        for (char diacritic : ARABIC_DIACRITICS) if (c == diacritic) return true;
        return false;
    }

    private static String collapse(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    /** True when the learner answer matches any accepted variant (accents/punctuation optional). */
    static boolean matchesAny(List<String> accepted, String answer) {
        if (answer == null) return false;
        String spanish = spanishKey(answer);
        String arabic = arabicKey(answer);
        if (spanish.isEmpty() && arabic.isEmpty()) return false;
        for (String variant : accepted) {
            if (variant == null || variant.trim().isEmpty()) continue;
            String key = isArabic(variant) ? arabicKey(variant) : spanishKey(variant);
            if (key.isEmpty()) continue;
            if (isArabic(variant) ? key.equals(arabic) : key.equals(spanish)) return true;
        }
        return false;
    }

    /**
     * Accepts the legitimate grammatical variants a learner may type for a noun or an
     * adjective: plural (-s / -es) and gender (-o / -a). Verb conjugations are NOT
     * accepted — "hablo" is a different tense, not an inflection of "hablar".
     */
    static boolean inflectionMatches(String expected, String answer, String pos) {
        if (expected == null || answer == null) return false;
        if (!isNounLike(pos)) return false;
        String target = spanishKey(expected);
        String got = spanishKey(answer);
        if (target.isEmpty() || got.isEmpty()) return false;
        if (target.indexOf(' ') >= 0 || got.indexOf(' ') >= 0) return false;
        if (target.equals(got)) return true;
        if (got.equals(target + "s") || got.equals(target + "es")) return true;
        if (target.equals(got + "s") || target.equals(got + "es")) return true;
        if (target.length() > 2 && got.length() == target.length()) {
            char lastTarget = target.charAt(target.length() - 1);
            char lastGot = got.charAt(got.length() - 1);
            if (lastTarget == 'o' && lastGot == 'a' && target.substring(0, target.length() - 1)
                    .equals(got.substring(0, got.length() - 1))) return true;
            if (lastTarget == 'a' && lastGot == 'o' && target.substring(0, target.length() - 1)
                    .equals(got.substring(0, got.length() - 1))) return true;
        }
        return false;
    }

    private static boolean isNounLike(String pos) {
        if (pos == null) return false;
        switch (pos) {
            case "noun":
            case "adj":
            case "num":
            case "word":
                return true;
            default:
                return false;
        }
    }

    static boolean isArabic(String text) {
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) >= 0x0600 && text.charAt(i) <= 0x06FF) return true;
        return false;
    }

    static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String word : text.trim().split("\\s+")) if (!word.isEmpty()) out.add(word);
        return out;
    }

    /** Word-order judgement that ignores accents, punctuation and duplicated spaces. */
    static boolean sameWordOrder(String expected, String actual) {
        List<String> left = words(spanishKey(expected));
        List<String> right = words(spanishKey(actual));
        if (left.isEmpty() || left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) if (!left.get(i).equals(right.get(i))) return false;
        return true;
    }

    /** Join tokens with single spaces, dropping punctuation noise. */
    static String join(List<String> tokens) {
        StringBuilder out = new StringBuilder();
        for (String token : tokens) {
            if (token == null || token.trim().isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(token.trim());
        }
        return out.toString();
    }

    static boolean looksSpanish(String text) {
        String key = spanishKey(text);
        return !key.isEmpty() && !TextNormalizers.isArabic(text);
    }
}
