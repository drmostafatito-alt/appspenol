package com.habla.espanol;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Every lesson of the shipped curriculum must produce gradable exercises. */
final class PlannerTest {
    private PlannerTest() {}

    private static final Set<String> TYPES = Set.of("intro", "choose", "listen", "order", "fill",
            "translate_es_ar", "translate_ar_es", "speak", "match", "comprehend", "final");

    static void run(Curriculum model) {
        int lessons = 0;
        int exercises = 0;
        for (Curriculum.Level level : model.levels) {
            for (Curriculum.Unit unit : level.units) {
                for (Curriculum.Lesson lesson : unit.lessons) {
                    lessons++;
                    List<Curriculum.Exercise> plan = LessonPlanner.plan(model, lesson);
                    TestSupport.check(!plan.isEmpty(), lesson.id + " produced a plan");
                    TestSupport.check(plan.size() <= LessonPlanner.MAX_EXERCISES,
                            lesson.id + " plan respects the cap (" + plan.size() + ")");
                    Set<String> types = new HashSet<>();
                    for (Curriculum.Exercise exercise : plan) {
                        exercises++;
                        types.add(exercise.type);
                        TestSupport.check(TYPES.contains(exercise.type), lesson.id + " known type " + exercise.type);
                        TestSupport.notEmpty(exercise.id, lesson.id + " exercise id");
                        TestSupport.notEmpty(exercise.promptEgy, lesson.id + "/" + exercise.type + " prompt");
                        switch (exercise.type) {
                            case "choose":
                            case "listen":
                            case "fill":
                            case "final":
                                TestSupport.check(exercise.options.size() >= 3,
                                        exercise.id + " has 3+ options");
                                TestSupport.check(exercise.answer >= 0 && exercise.answer < exercise.options.size(),
                                        exercise.id + " answer index");
                                TestSupport.check(new HashSet<>(exercise.options).size() == exercise.options.size(),
                                        exercise.id + " unique options");
                                break;
                            case "order":
                                TestSupport.check(exercise.options.size() >= 2, exercise.id + " chips");
                                TestSupport.check(!exercise.answers.isEmpty(), exercise.id + " target sentence");
                                break;
                            case "translate_es_ar":
                            case "translate_ar_es":
                                TestSupport.check(!exercise.answers.isEmpty(), exercise.id + " accepted answers");
                                break;
                            case "speak":
                                TestSupport.notEmpty(exercise.audio, exercise.id + " speech target");
                                break;
                            case "match":
                                TestSupport.check(exercise.pairs.size() >= 2, exercise.id + " pairs");
                                break;
                            default:
                                break;
                        }
                        if (exercise.type.equals("choose") || exercise.type.equals("fill")) {
                            TestSupport.notEmpty(exercise.explainEgy, exercise.id + " explanation");
                        }
                    }
                    TestSupport.check(types.contains("choose"), lesson.id + " includes a meaning question");
                    if (plan.size() >= 8) {
                        TestSupport.check(types.size() >= 3, lesson.id + " mixes exercise types");
                    }
                }
            }
        }
        TestSupport.check(lessons > 100, "curriculum has many lessons (" + lessons + ")");
        TestSupport.check(exercises > 1000, "curriculum produces many exercises (" + exercises + ")");
        System.out.println("  planner: " + lessons + " lessons / " + exercises + " exercises");
    }
}
