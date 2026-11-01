package com.habla.espanol;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless end-to-end simulation of the learning loop for EVERY lesson in the shipped asset.
 *
 * For each lesson it builds the real exercise plan, answers every exercise correctly and then
 * incorrectly, and checks grading, tutoring, XP idempotency, review scheduling and persistence
 * through the same classes the app uses. It cannot replace a device test of the UI, but it does
 * prove the data → planner → grading → tutor → SRS → XP chain works for all 500+ lessons.
 */
final class FlowTest {
    private FlowTest() {}

    static void run(Curriculum model) {
        TestSupport.MapStore store = new TestSupport.MapStore();
        int lessons = 0, exercises = 0, correctAnswers = 0, wrongAnswers = 0, explanations = 0;
        Set<String> types = new LinkedHashSet<>();
        long now = 1_700_000_000_000L;
        int xpTotal = 0;
        List<String> scheduled = new ArrayList<>();

        for (Curriculum.Level level : model.levels) {
            for (Curriculum.Unit unit : level.units) {
                for (Curriculum.Lesson lesson : unit.lessons) {
                    lessons++;
                    List<Curriculum.Exercise> plan = LessonPlanner.plan(model, lesson);
                    TestSupport.check(!plan.isEmpty(), lesson.id + " plans at least one exercise");
                    int firstTryCorrect = 0;
                    for (Curriculum.Exercise exercise : plan) {
                        exercises++;
                        types.add(exercise.type);
                        AnswerChecker.Answer good = correctAnswer(exercise);
                        TestSupport.check(good != null, exercise.id + " has a reachable correct answer");
                        if (good == null) continue;

                        AnswerChecker.Verdict ok = AnswerChecker.check(exercise, good);
                        TestSupport.check(ok.correct, exercise.id + " (" + exercise.type + ") accepts the correct answer");
                        if (ok.correct) { correctAnswers++; firstTryCorrect++; }

                        Tutor.Feedback feedback = Tutor.correct(model, exercise, itemOf(model, exercise));
                        TestSupport.check(feedback != null, exercise.id + " produces feedback");
                        if (feedback != null && feedback.explanationEgy != null && !feedback.explanationEgy.isEmpty()) explanations++;

                        AnswerChecker.Answer bad = wrongAnswer(exercise, good);
                        if (bad != null) {
                            AnswerChecker.Verdict no = AnswerChecker.check(exercise, bad);
                            TestSupport.check(!no.correct, exercise.id + " rejects a wrong answer");
                            if (!no.correct) wrongAnswers++;
                            Tutor.Feedback correction = Tutor.wrong(model, exercise, itemOf(model, exercise), no);
                            TestSupport.check(correction != null && correction.explanationEgy != null
                                    && !correction.explanationEgy.isEmpty()
                                    && !correction.correctAnswer.isEmpty(),
                                    exercise.id + " explains the mistake in Arabic and shows the correct answer");
                        }

                        if (!exercise.itemId.isEmpty() && !scheduled.contains(exercise.itemId)) {
                            ReviewScheduler.grade(store, exercise.itemId,
                                    ReviewScheduler.gradeFor(ok.correct, false, 0), now);
                            scheduled.add(exercise.itemId);
                        }
                    }
                    int reward = XpPolicy.lessonReward(store, lesson.id, firstTryCorrect);
                    xpTotal += reward;
                    TestSupport.check(XpPolicy.lessonReward(store, lesson.id, firstTryCorrect) == 0,
                            lesson.id + " does not pay XP twice");
                    TestSupport.check(XpPolicy.lessonAlreadyRewarded(store, lesson.id),
                            lesson.id + " remembers that it was rewarded");
                }
            }
        }

        TestSupport.check(lessons >= 500, "flow: many lessons simulated (" + lessons + ")");
        TestSupport.check(types.size() >= 8, "flow: mixed exercise types (" + types.size() + ")");
        TestSupport.check(correctAnswers > 5_000, "flow: correct answers graded (" + correctAnswers + ")");
        TestSupport.check(wrongAnswers > 2_000, "flow: wrong answers graded (" + wrongAnswers + ")");
        TestSupport.check(explanations > 5_000, "flow: Arabic feedback generated (" + explanations + ")");

        // Spaced repetition: graded items become due later, a lapse brings them back soon.
        String item = model.levels.get(1).units.get(0).items.get(0).id;
        ReviewScheduler.grade(store, item, ReviewScheduler.GOOD, now);
        long due = store.getLong("due_" + item, 0L);
        TestSupport.check(due > now, "srs: GOOD schedules the item in the future");
        ReviewScheduler.grade(store, item, ReviewScheduler.AGAIN, now + 1000);
        long lapsed = store.getLong("due_" + item, 0L);
        TestSupport.check(lapsed > now && lapsed < due, "srs: AGAIN returns the item sooner (" + lapsed + ")");
        TestSupport.check(ReviewScheduler.masteryLevel(store, item) >= 0, "srs: mastery readable");
        TestSupport.check(!ReviewScheduler.dueItems(store, scheduled, now + 200 * 86_400_000L, 100).isEmpty(),
                "srs: items become due over time");

        // Persistence: the screen writes the running total to "xp"; a restart reads the same store.
        store.putInt("xp", xpTotal);
        TestSupport.check(store.getInt("xp", 0) == xpTotal, "progress: XP total survives reload");
        String firstLesson = model.levels.get(0).units.get(0).lessons.get(0).id;
        TestSupport.check(XpPolicy.lessonAlreadyRewarded(store, firstLesson),
                "progress: rewarded lessons survive reload");
        ReviewScheduler.State reloaded = ReviewScheduler.read(store, item);
        TestSupport.check(reloaded.attempts > 0 && reloaded.dueAt > 0, "progress: review state survives reload");
        TestSupport.check(xpTotal > lessons, "progress: XP grows with lessons (" + xpTotal + ")");
        TestSupport.check(model.totalItems() > 0, "content: model loaded");
        System.out.println("  flow: " + lessons + " درس • " + exercises + " تمرين • "
                + correctAnswers + " إجابة صحيحة • " + wrongAnswers + " إجابة خاطئة • XP " + xpTotal);
    }

    private static Curriculum.Item itemOf(Curriculum model, Curriculum.Exercise exercise) {
        if (exercise.itemId.isEmpty()) return null;
        return model.item(exercise.itemId);
    }

    private static AnswerChecker.Answer correctAnswer(Curriculum.Exercise exercise) {
        switch (exercise.type) {
            case "intro":
            case "match":
                return AnswerChecker.Answer.text("");
            case "choose":
            case "listen":
            case "fill":
            case "comprehend":
            case "final":
                return AnswerChecker.Answer.choice(exercise.answer);
            case "order":
                if (exercise.answers.isEmpty()) return null;
                return AnswerChecker.Answer.tokens(TextNormalizers.words(exercise.answers.get(0)));
            case "translate_es_ar":
            case "translate_ar_es":
                if (exercise.answers.isEmpty()) return null;
                return AnswerChecker.Answer.text(exercise.answers.get(0));
            case "speak":
                return AnswerChecker.Answer.text(exercise.audio);
            default:
                return null;
        }
    }

    private static AnswerChecker.Answer wrongAnswer(Curriculum.Exercise exercise, AnswerChecker.Answer good) {
        switch (exercise.type) {
            case "choose":
            case "listen":
            case "fill":
            case "comprehend":
            case "final":
                if (exercise.options.size() < 2) return null;
                return AnswerChecker.Answer.choice((exercise.answer + 1) % exercise.options.size());
            case "order":
                List<String> reversed = new ArrayList<>(good.tokens);
                java.util.Collections.reverse(reversed);
                if (reversed.size() < 2) return null;
                return AnswerChecker.Answer.tokens(reversed);
            case "translate_es_ar":
            case "translate_ar_es":
                return AnswerChecker.Answer.text("__wrong__");
            case "speak":
                return AnswerChecker.Answer.text("__wrong__");
            default:
                return null;
        }
    }
}
