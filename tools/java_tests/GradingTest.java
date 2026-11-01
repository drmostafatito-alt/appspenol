package com.habla.espanol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Answer checking: the correct answer always passes, wrong ones never do. */
final class GradingTest {
    private GradingTest() {}

    static void run(Curriculum model) {
        int checked = 0;
        for (Curriculum.Level level : model.levels) {
            for (Curriculum.Unit unit : level.units) {
                for (Curriculum.Lesson lesson : unit.lessons) {
                    for (Curriculum.Exercise exercise : LessonPlanner.plan(model, lesson)) {
                        AnswerChecker.Answer correct = correctAnswer(exercise);
                        if (correct == null) continue;
                        checked++;
                        TestSupport.check(AnswerChecker.check(exercise, correct).correct,
                                "correct answer accepted: " + exercise.id + " / " + exercise.type);
                        if (exercise.type.equals("choose") || exercise.type.equals("fill")
                                || exercise.type.equals("listen") || exercise.type.equals("final")) {
                            int wrong = (exercise.answer + 1) % exercise.options.size();
                            if (wrong != exercise.answer) {
                                TestSupport.check(!AnswerChecker.check(exercise, AnswerChecker.Answer.choice(wrong)).correct,
                                        "wrong choice rejected: " + exercise.id);
                            }
                        }
                        if (exercise.type.equals("translate_es_ar") || exercise.type.equals("translate_ar_es")) {
                            TestSupport.check(!AnswerChecker.check(exercise, AnswerChecker.Answer.text("xyz123")).correct,
                                    "garbage translation rejected: " + exercise.id);
                        }
                    }
                }
            }
        }
        TestSupport.check(checked > 100, "graded many exercises (" + checked + ")");

        // translation tolerance
        Curriculum.Exercise translate = new Curriculum.Exercise();
        translate.type = "translate_ar_es";
        translate.answers = new ArrayList<>(List.of("¿Cómo estás?"));
        TestSupport.check(AnswerChecker.check(translate, AnswerChecker.Answer.text("como estas")).correct,
                "accents and punctuation are optional");
        TestSupport.check(AnswerChecker.check(translate, AnswerChecker.Answer.text("  COMO   ESTAS ")).correct,
                "case and spaces are normalised");
        TestSupport.check(!AnswerChecker.check(translate, AnswerChecker.Answer.text("como esta")).correct,
                "missing final s is rejected");

        Curriculum.Exercise toArabic = new Curriculum.Exercise();
        toArabic.type = "translate_es_ar";
        toArabic.answers = new ArrayList<>(List.of("أهلاً", "مرحبًا"));
        TestSupport.check(AnswerChecker.check(toArabic, AnswerChecker.Answer.text("مرحبا")).correct,
                "arabic without diacritics accepted");
        TestSupport.check(AnswerChecker.check(toArabic, AnswerChecker.Answer.text("اهلا")).correct,
                "alef variants accepted");

        Curriculum.Exercise order = new Curriculum.Exercise();
        order.type = "order";
        order.answers = new ArrayList<>(List.of("Yo soy de Egipto"));
        TestSupport.check(AnswerChecker.check(order, AnswerChecker.Answer.tokens(
                new ArrayList<>(Arrays.asList("Yo", "soy", "de", "Egipto")))).correct, "correct word order");
        TestSupport.check(!AnswerChecker.check(order, AnswerChecker.Answer.tokens(
                new ArrayList<>(Arrays.asList("soy", "Yo", "de", "Egipto")))).correct, "swapped order rejected");

        TestSupport.check(AnswerChecker.speechMatches("Buenos días", "buenos dias"), "speech ignores accents");
        TestSupport.check(AnswerChecker.speechMatches("Quiero un café", "quiero un cafe por favor"),
                "speech allows extra words");
        TestSupport.check(!AnswerChecker.speechMatches("Quiero un café", "no entiendo nada"), "speech rejects unrelated");
        System.out.println("  grading: " + checked + " generated exercises round-tripped");
    }

    private static AnswerChecker.Answer correctAnswer(Curriculum.Exercise exercise) {
        switch (exercise.type) {
            case "choose":
            case "listen":
            case "fill":
            case "comprehend":
            case "final":
                return AnswerChecker.Answer.choice(exercise.answer);
            case "order":
                List<String> tokens = new ArrayList<>(TextNormalizers.words(exercise.answers.get(0)));
                return AnswerChecker.Answer.tokens(tokens);
            case "translate_es_ar":
            case "translate_ar_es":
                return AnswerChecker.Answer.text(exercise.answers.get(0));
            case "speak":
                return AnswerChecker.Answer.text(exercise.audio);
            case "match":
            case "intro":
                return null;
            default:
                return null;
        }
    }
}
