package com.habla.espanol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared answer generators used by the session / planner / grading tests. */
final class Answers {
    private Answers() {}

    static boolean gradable(String type) {
        switch (type) {
            case "intro":
            case "match":
            case "choose":
            case "listen":
            case "fill":
            case "comprehend":
            case "final":
            case "order":
            case "translate_es_ar":
            case "translate_ar_es":
            case "speak":
                return true;
            default:
                return false;
        }
    }

    /** A correct answer, or null when the exercise cannot be answered as shipped. */
    static AnswerChecker.Answer correct(Curriculum.Exercise exercise) {
        switch (exercise.type) {
            case "intro":
            case "match":
                return AnswerChecker.Answer.text("");
            case "choose":
            case "listen":
            case "fill":
            case "comprehend":
            case "final":
                if (exercise.answer < 0 || exercise.answer >= exercise.options.size()) return null;
                return AnswerChecker.Answer.choice(exercise.answer);
            case "order":
                if (exercise.answers.isEmpty()) return null;
                return AnswerChecker.Answer.tokens(TextNormalizers.words(exercise.answers.get(0)));
            case "translate_es_ar":
            case "translate_ar_es":
            case "speak":
                if (!exercise.answers.isEmpty()) return AnswerChecker.Answer.text(exercise.answers.get(0));
                if (!exercise.audio.isEmpty()) return AnswerChecker.Answer.text(exercise.audio);
                return null;
            default:
                return null;
        }
    }

    /** A wrong answer, or null when the exercise has only one possible value. */
    static AnswerChecker.Answer wrong(Curriculum.Exercise exercise) {
        switch (exercise.type) {
            case "choose":
            case "listen":
            case "fill":
            case "comprehend":
            case "final":
                if (exercise.options.size() < 2) return null;
                return AnswerChecker.Answer.choice((exercise.answer + 1) % exercise.options.size());
            case "order": {
                AnswerChecker.Answer good = correct(exercise);
                if (good == null || good.tokens.size() < 2) return null;
                List<String> reversed = new ArrayList<>(good.tokens);
                Collections.reverse(reversed);
                return AnswerChecker.Answer.tokens(reversed);
            }
            case "translate_es_ar":
            case "translate_ar_es":
                return AnswerChecker.Answer.text("__wrong_answer__");
            case "speak":
                return AnswerChecker.Answer.text("__wrong_answer__");
            default:
                return null;
        }
    }

    /** The same answer typed without Spanish accents / with different case. */
    static String withoutAccents(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").trim();
    }
}
