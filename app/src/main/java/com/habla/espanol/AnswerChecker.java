package com.habla.espanol;

import java.util.ArrayList;
import java.util.List;

/**
 * Grades every exercise type of the curriculum and explains near misses.
 * Deliberately Android-free: `tools/java_tests/AnswerCheckerTest.java` runs this
 * against the real shipped content.
 */
final class AnswerChecker {
    private AnswerChecker() {}

    /** What the learner produced: a selected option, a typed text or ordered tokens. */
    static final class Answer {
        int choice = -1;
        String text = "";
        List<String> tokens = new ArrayList<>();

        static Answer choice(int index) { Answer a = new Answer(); a.choice = index; return a; }
        static Answer text(String value) { Answer a = new Answer(); a.text = value == null ? "" : value; return a; }
        static Answer tokens(List<String> values) { Answer a = new Answer(); if (values != null) a.tokens.addAll(values); return a; }
    }

    static final class Verdict {
        final boolean correct;
        final String expected;
        final String diagnosis;
        Verdict(boolean correct, String expected, String diagnosis) {
            this.correct = correct;
            this.expected = expected;
            this.diagnosis = diagnosis;
        }
    }

    static Verdict check(Curriculum.Exercise exercise, Answer answer) {
        String expected = expectedLabel(exercise);
        switch (exercise.type) {
            case "intro":
                return new Verdict(true, expected, "");
            case "choose":
            case "listen":
            case "fill":
            case "comprehend":
            case "final":
                return choiceVerdict(exercise, answer, expected);
            case "order":
                return orderVerdict(exercise, answer, expected);
            case "translate_es_ar":
            case "translate_ar_es":
                return translateVerdict(exercise, answer, expected);
            case "match":
                return new Verdict(true, expected, "");
            case "speak":
                return speakVerdict(exercise, answer, expected);
            default:
                throw new IllegalArgumentException("cannot grade exercise type " + exercise.type);
        }
    }

    private static Verdict choiceVerdict(Curriculum.Exercise exercise, Answer answer, String expected) {
        boolean correct = answer.choice == exercise.answer;
        String diagnosis = "";
        if (!correct && answer.choice >= 0 && answer.choice < exercise.options.size()) {
            String picked = exercise.options.get(answer.choice);
            diagnosis = TextNormalizers.isArabic(picked)
                    ? "الاختيار اللي اخترته معناه: «" + picked.trim() + "» — مش المعنى المطلوب هنا."
                    : "اللي اخترته «" + picked.trim() + "» مش الشكل الصحيح المطلوب.";
        } else if (!correct) {
            diagnosis = "لسه ما اخترتش إجابة.";
        }
        return new Verdict(correct, expected, diagnosis);
    }

    private static Verdict orderVerdict(Curriculum.Exercise exercise, Answer answer, String expected) {
        String produced = TextNormalizers.join(answer.tokens);
        boolean correct = false;
        for (String accepted : exercise.answers) {
            if (TextNormalizers.sameWordOrder(accepted, produced)) { correct = true; break; }
        }
        String diagnosis = correct ? "" : "ترتيب الكلمات مهم في الإسباني؛ قارن إجابتك بالجملة الصح.";
        return new Verdict(correct, expected, diagnosis);
    }

    private static Verdict translateVerdict(Curriculum.Exercise exercise, Answer answer, String expected) {
        boolean correct = TextNormalizers.matchesAny(exercise.answers, answer.text);
        String diagnosis = "";
        if (!correct) {
            int best = Integer.MAX_VALUE;
            for (String accepted : exercise.answers) {
                String key = TextNormalizers.isArabic(accepted)
                        ? TextNormalizers.arabicKey(accepted) : TextNormalizers.spanishKey(accepted);
                String got = TextNormalizers.isArabic(accepted)
                        ? TextNormalizers.arabicKey(answer.text) : TextNormalizers.spanishKey(answer.text);
                best = Math.min(best, distance(key, got));
            }
            boolean targetArabic = exercise.type.equals("translate_es_ar");
            if (best <= 2 && best >= 0) {
                diagnosis = targetArabic
                        ? "قربت جدًا! في حرف أو حرفين ناقصين في العربي."
                        : "قربت جدًا! راجع الحروف أو الأكسنت (tilde) في الكلمة.";
            } else if (!targetArabic) {
                diagnosis = "راجع أول كلمة في الجملة الإسبانية وتصريف الفعل، وبعدها أعد المحاولة.";
            } else {
                diagnosis = "ركّز في المعنى العام للجملة مش كلمة كلمة.";
            }
        }
        return new Verdict(correct, expected, diagnosis);
    }

    private static Verdict speakVerdict(Curriculum.Exercise exercise, Answer answer, String expected) {
        boolean correct = speechMatches(expected, answer.text);
        return new Verdict(correct, expected, correct ? "" : "اللي اتسمع مش قريب من الجملة. اتكلم أبطأ وجرّب تاني.");
    }

    /** Tolerant speech comparison: exact words, ordered subset, or high similarity. */
    static boolean speechMatches(String expected, String recognized) {
        if (expected == null || recognized == null) return false;
        String expectedKey = TextNormalizers.spanishKey(expected);
        String gotKey = TextNormalizers.spanishKey(recognized);
        if (expectedKey.isEmpty() || gotKey.isEmpty()) return false;
        if (expectedKey.equals(gotKey)) return true;
        List<String> expectedWords = TextNormalizers.words(expectedKey);
        List<String> gotWords = TextNormalizers.words(gotKey);
        if (expectedWords.isEmpty() || gotWords.isEmpty()) return false;
        int matched = 0;
        int cursor = 0;
        for (String word : expectedWords) {
            for (int i = cursor; i < gotWords.size(); i++) {
                if (gotWords.get(i).equals(word)) { matched++; cursor = i + 1; break; }
            }
        }
        if (matched >= Math.max(1, Math.ceil(expectedWords.size() * 0.75))) return true;
        double similarity = 1.0 - (distance(expectedKey, gotKey) / (double) Math.max(expectedKey.length(), gotKey.length()));
        return similarity >= 0.8;
    }

    /** Human-readable correct answer for feedback. */
    static String expectedLabel(Curriculum.Exercise exercise) {
        switch (exercise.type) {
            case "order":
            case "translate_ar_es":
            case "speak":
                return exercise.answers.isEmpty() ? "" : exercise.answers.get(0);
            case "translate_es_ar":
                return exercise.answers.isEmpty() ? "" : exercise.answers.get(0);
            case "choose":
            case "listen":
            case "fill":
            case "comprehend":
            case "final":
                return exercise.answer >= 0 && exercise.answer < exercise.options.size()
                        ? exercise.options.get(exercise.answer) : "";
            case "match":
                return exercise.pairs.isEmpty() ? "" : exercise.pairs.get(0)[0] + " = " + exercise.pairs.get(0)[1];
            default:
                return "";
        }
    }

    /** Levenshtein distance (small strings only). */
    static int distance(String left, String right) {
        if (left == null) left = "";
        if (right == null) right = "";
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
