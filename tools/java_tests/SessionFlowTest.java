package com.habla.espanol;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Full lesson-session simulation over every lesson in the curriculum.
 *
 * For each lesson it loads the plan, answers every exercise correctly, then checks a
 * sample of wrong answers, feedback, legitimate inflections, XP-once, review scheduling,
 * durable session state and a "process restart" in the middle of the lesson.
 * A CSV/Markdown row is produced per lesson so failures are attributable, not hidden.
 */
final class SessionFlowTest {
    private SessionFlowTest() {}

    static final class Row {
        String lesson = "", level = "", unit = "", title = "";
        int exercises = 0;
        String types = "";
        boolean passed = true;
        final List<String> reasons = new ArrayList<>();

        String status() { return passed ? "PASS" : "FAIL"; }
        String reason() { return passed ? "" : String.join("؛ ", reasons); }
    }

    private static void verify(Row row, boolean condition, String reason) {
        TestSupport.checks++;
        if (condition) return;
        row.passed = false;
        if (row.reasons.size() < 6) row.reasons.add(reason);
    }

    static List<Row> run(Curriculum model) {
        List<Row> rows = new ArrayList<>();
        long now = 1_700_000_000_000L;
        int lessons = 0;
        for (Curriculum.Level level : model.levels) {
            for (Curriculum.Unit unit : level.units) {
                for (Curriculum.Lesson lesson : unit.lessons) {
                    lessons++;
                    rows.add(simulate(model, lesson, level, unit, now));
                }
            }
        }
        int failed = 0;
        int exercises = 0;
        for (Row row : rows) { if (!row.passed) failed++; exercises += row.exercises; }
        TestSupport.check(lessons >= 500, "session: every lesson was simulated (" + lessons + ")");
        TestSupport.check(failed == 0, "session: all lessons pass a full session (" + failed + " failed)");
        TestSupport.check(exercises > 5_000, "session: exercises exercised (" + exercises + ")");
        System.out.println("  session: " + lessons + " درس • " + exercises + " تمرين • فشل " + failed);
        return rows;
    }

    private static Row simulate(Curriculum model, Curriculum.Lesson lesson, Curriculum.Level level,
                                Curriculum.Unit unit, long now) {
        Row row = new Row();
        row.lesson = lesson.id;
        row.level = level.id;
        row.unit = unit.id;
        row.title = lesson.title;

        TestSupport.MapStore store = new TestSupport.MapStore();
        List<Curriculum.Exercise> plan = LessonPlanner.plan(model, lesson);
        row.exercises = plan.size();
        verify(row, !plan.isEmpty(), "خطة الدرس فاضية");
        if (plan.isEmpty()) return row;

        // The plan must be deterministic, otherwise a resumed session would change under the learner.
        List<Curriculum.Exercise> again = LessonPlanner.plan(model, lesson);
        verify(row, sameIds(plan, again), "الخطة مش ثابتة عند إعادة التوليد");
        verify(row, plan.size() <= LessonPlanner.MAX_EXERCISES, "الخطة أطول من الحد (" + plan.size() + ")");

        Set<String> types = new LinkedHashSet<>();
        Set<String> ids = new LinkedHashSet<>();
        for (Curriculum.Exercise exercise : plan) {
            types.add(exercise.type);
            verify(row, ids.add(exercise.id), "تمرين مكرر: " + exercise.id);
            verify(row, Answers.gradable(exercise.type), "نوع تمرين غير مدعوم: " + exercise.type);
            verify(row, !exercise.promptEgy.trim().isEmpty() || exercise.type.equals("match"),
                    "تمرين بلا نص سؤال: " + exercise.id);
            verify(row, !exercise.explainEgy.trim().isEmpty() || exercise.type.equals("intro"),
                    "تمرين بلا تفسير: " + exercise.id);
            if (isChoice(exercise.type)) {
                verify(row, exercise.options.size() >= 2, "خيارات أقل من 2: " + exercise.id);
                verify(row, exercise.answer >= 0 && exercise.answer < exercise.options.size(),
                        "مؤشر إجابة خارج الخيارات: " + exercise.id);
                verify(row, distinctKeys(exercise.options), "خيارات مكررة: " + exercise.id);
            }
            if (exercise.type.equals("order")) verify(row, !exercise.answers.isEmpty(), "ترتيب بلا جملة: " + exercise.id);
            if (exercise.type.startsWith("translate")) verify(row, !exercise.answers.isEmpty(), "ترجمة بلا إجابة: " + exercise.id);
            if (exercise.type.equals("speak")) verify(row, !exercise.audio.isEmpty() || !exercise.answers.isEmpty(),
                    "تحدث بلا نص: " + exercise.id);
            if (exercise.type.equals("match")) verify(row, exercise.pairs.size() >= 2, "مطابقة بأقل من زوجين: " + exercise.id);
        }
        row.types = String.join("+", types);
        // Generated lessons must mix at least four exercise types; the twelve hand-written
        // dialogue lessons ship three (listening, meaning, comprehension) by design.
        int minimumTypes = lesson.exercises.isEmpty() ? 4 : 3;
        TestSupport.check(types.size() >= minimumTypes,
                "session: " + lesson.id + " mixes exercise types (" + types.size() + ")");

        // ---- full correct run with a durable session that survives a restart at every step ----
        LessonSession state = LessonSession.begin(lesson.id, "", plan.size(), false, now);
        int correctCount = 0;
        for (int i = 0; i < plan.size(); i++) {
            Curriculum.Exercise exercise = plan.get(i);
            Curriculum.Item item = exercise.itemId.isEmpty() ? null : model.item(exercise.itemId);
            AnswerChecker.Answer answer = Answers.correct(exercise);
            if (answer == null) {
                verify(row, false, "مفيش إجابة صحيحة قابلة للتوليد: " + exercise.type);
                break;
            }
            AnswerChecker.Verdict verdict = AnswerChecker.check(exercise, answer, item);
            verify(row, verdict.correct, "إجابة صحيحة مرفوضة: " + exercise.id);
            if (verdict.correct) correctCount++;
            Tutor.Feedback feedback = Tutor.correct(model, exercise, item);
            verify(row, feedback.correct && !feedback.explanationEgy.trim().isEmpty(),
                    "تفسير الإجابة الصحيحة ناقص: " + exercise.id);
            state.record(verdict.correct, false, exercise.itemId, exercise.type);
            state.advance();
            LessonSession.save(store, state);

            // restart simulation: only the strings in storage survive
            TestSupport.MapStore restarted = new TestSupport.MapStore();
            restarted.values.putAll(store.values);
            LessonSession restored = LessonSession.load(restarted);
            verify(row, restored != null, "الجلسة مش قابلة للاستعادة عند التمرين " + (i + 1));
            if (restored != null) {
                verify(row, restored.cursor == i + 1, "موضع الجلسة غلط بعد الاستعادة عند " + (i + 1));
                verify(row, restored.total == plan.size(), "عدد تمارين الجلسة غلط بعد الاستعادة");
                verify(row, restored.firstAttempted == state.firstAttempted
                                && restored.firstCorrect == state.firstCorrect,
                        "عدّاد الإجابات مش محفوظ عند " + (i + 1));
                verify(row, restored.lessonId.equals(lesson.id), "معرّف الدرس مش محفوظ");
            }
        }
        verify(row, state.finished(), "الجلسة مش مكتملة بعد آخر تمرين");
        verify(row, correctCount == plan.size(), "عدد الإجابات الصحيحة " + correctCount + " من " + plan.size());

        // ---- wrong answers, tutor feedback and rejection ----
        int wrongChecked = 0;
        for (Curriculum.Exercise exercise : plan) {
            if (wrongChecked >= 3) break;
            AnswerChecker.Answer bad = Answers.wrong(exercise);
            if (bad == null) continue;
            Curriculum.Item item = exercise.itemId.isEmpty() ? null : model.item(exercise.itemId);
            AnswerChecker.Verdict verdict = AnswerChecker.check(exercise, bad, item);
            verify(row, !verdict.correct, "إجابة خاطئة مقبولة: " + exercise.id);
            if (!verdict.correct) {
                Tutor.Feedback feedback = Tutor.wrong(model, exercise, item, verdict);
                verify(row, !feedback.explanationEgy.trim().isEmpty(), "شرح الخطأ ناقص: " + exercise.id);
                verify(row, !feedback.correctAnswer.trim().isEmpty(), "الإجابة الصح مش معروضة: " + exercise.id);
                verify(row, !feedback.headline.trim().isEmpty(), "عنوان التصحيح ناقص: " + exercise.id);
                String rendered = feedback.renderEgy();
                verify(row, !rendered.trim().isEmpty(), "نص التصحيح فاضي: " + exercise.id);
                wrongChecked++;
            }
        }
        verify(row, wrongChecked > 0, "مفيش تمرين واحد قابل للإجابة الخاطئة");

        // ---- legitimate inflections accepted, real mistakes rejected ----
        int inflectionChecked = 0;
        for (Curriculum.Exercise exercise : plan) {
            if (!exercise.type.equals("translate_ar_es") || exercise.answers.isEmpty()) continue;
            Curriculum.Item item = exercise.itemId.isEmpty() ? null : model.item(exercise.itemId);
            if (item == null) continue;
            String expected = exercise.answers.get(0);
            // Written accents and case are optional — but ñ is a real letter, not an accent,
            // so "nino" must stay wrong for "niño".
            if (expected.indexOf('ñ') < 0 && expected.indexOf('Ñ') < 0) {
                AnswerChecker.Verdict noAccents = AnswerChecker.check(exercise,
                        AnswerChecker.Answer.text(Answers.withoutAccents(expected).toUpperCase(java.util.Locale.ROOT)), item);
                verify(row, noAccents.correct, "الإجابة بدون أكسنت مرفوضة: " + exercise.id);
            } else {
                AnswerChecker.Verdict strippedN = AnswerChecker.check(exercise,
                        AnswerChecker.Answer.text(expected.replace('ñ', 'n').replace('Ñ', 'N')), item);
                verify(row, !strippedN.correct, "ñ مقبولة كأنها n: " + exercise.id);
            }
            // gender / plural of a noun or adjective
            if ("noun".equals(item.pos) || "adj".equals(item.pos)) {
                String variant = inflect(expected);
                if (variant != null) {
                    AnswerChecker.Verdict inflected = AnswerChecker.check(
                            exercise, AnswerChecker.Answer.text(variant), item);
                    verify(row, inflected.correct, "تصريف مشروع مرفوض: " + expected + " → " + variant);
                    inflectionChecked++;
                }
            }
            // a real conjugation error must stay wrong
            AnswerChecker.Verdict wrongTense = AnswerChecker.check(exercise,
                    AnswerChecker.Answer.text(expected + "ndo"), item);
            verify(row, !wrongTense.correct, "إجابة مشوهة مقبولة: " + exercise.id);
        }
        // Lessons without noun/adjective translation items simply check no inflection.

        // ---- XP exactly once, even if the lesson is reopened ----
        int reward = XpPolicy.lessonReward(store, lesson.id, state.firstCorrect);
        verify(row, reward > 0, "مفيش نقاط عند أول إكمال");
        verify(row, XpPolicy.lessonReward(store, lesson.id, state.firstCorrect) == 0, "النقاط بتتكرر عند إعادة الفتح");
        verify(row, XpPolicy.lessonAlreadyRewarded(store, lesson.id), "الدرس مش متعلّم كمكافأ");

        // ---- every studied item is scheduled ----
        Set<String> scheduled = new LinkedHashSet<>();
        for (Curriculum.Exercise exercise : plan) {
            if (exercise.itemId.isEmpty()) continue;
            if (!scheduled.add(exercise.itemId)) continue;
            ReviewScheduler.grade(store, exercise.itemId, ReviewScheduler.GOOD, now);
            ReviewScheduler.State srs = ReviewScheduler.read(store, exercise.itemId);
            verify(row, srs.attempts == 1 && srs.dueAt > now, "عنصر مش مجدول للمراجعة: " + exercise.itemId);
        }
        // Generated lessons always carry item-bearing exercises. The twelve dialogue lessons
        // carry vocabulary links on the lesson itself (used by the app at lesson end), not on
        // their hand-written comprehension exercises.
        verify(row, !scheduled.isEmpty() || !lesson.itemIds.isEmpty(),
                "الدرس مش بيضيف أي عنصر للمراجعة");

        // ---- wrong answer enqueues immediately (what the app does on a mistake) ----
        if (!scheduled.isEmpty()) {
            String first = scheduled.iterator().next();
            ReviewScheduler.grade(store, first, ReviewScheduler.AGAIN, now + 5_000);
            ReviewScheduler.State lapsedState = ReviewScheduler.read(store, first);
            verify(row, lapsedState.lapses >= 1 && lapsedState.dueAt > now,
                    "الخطأ مش بيرجّع العنصر للمراجعة: " + first);
        }

        return row;
    }

    private static String inflect(String spanish) {
        String word = spanish.trim();
        if (word.isEmpty() || word.indexOf(' ') >= 0) return null;
        char last = word.charAt(word.length() - 1);
        if (last == 'o') return word.substring(0, word.length() - 1) + "a";
        if (last == 'a') return word.substring(0, word.length() - 1) + "o";
        if ("aeiouáéíóú".indexOf(last) >= 0) return word + "s";
        return word + "es";
    }

    private static boolean isChoice(String type) {
        return type.equals("choose") || type.equals("listen") || type.equals("fill")
                || type.equals("comprehend") || type.equals("final");
    }

    private static boolean sameIds(List<Curriculum.Exercise> left, List<Curriculum.Exercise> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) if (!left.get(i).id.equals(right.get(i).id)) return false;
        return true;
    }

    private static boolean distinctKeys(List<String> values) {
        Set<String> keys = new LinkedHashSet<>();
        for (String value : values) {
            String key = TextNormalizers.isArabic(value)
                    ? TextNormalizers.arabicKey(value) : TextNormalizers.spanishKey(value);
            if (!keys.add(key)) return false;
        }
        return true;
    }

    static void writeReport(List<Row> rows, Path path) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("lesson_id,level,unit,title,exercises,types,status,reason\n");
        for (Row row : rows) {
            csv.append(csvValue(row.lesson)).append(',')
                    .append(row.level).append(',')
                    .append(row.unit).append(',')
                    .append(csvValue(row.title)).append(',')
                    .append(row.exercises).append(',')
                    .append(csvValue(row.types)).append(',')
                    .append(row.status()).append(',')
                    .append(csvValue(row.reason())).append('\n');
        }
        Files.createDirectories(path.getParent());
        Files.write(path, csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    static void writeMarkdown(List<Row> rows, Path path) throws IOException {
        int passed = 0;
        int exercises = 0;
        for (Row row : rows) { if (row.passed) passed++; exercises += row.exercises; }
        StringBuilder md = new StringBuilder();
        md.append("# تقرير اختبار جلسات الدروس\n\n");
        md.append("تم تشغيل جلسة كاملة (تحميل → تمارين → إجابات صحيحة وخاطئة → تفسير → نقاط → ")
                .append("مراجعة → حفظ → استعادة) على **كل** درس في `content.json`.\n\n");
        md.append("- الدروس المختبرة: **").append(rows.size()).append("**\n");
        md.append("- الدروس الناجحة: **").append(passed).append("**\n");
        md.append("- الدروس الفاشلة: **").append(rows.size() - passed).append("**\n");
        md.append("- التمارين المُشغّلة فعليًا: **").append(exercises).append("**\n\n");
        md.append("## ملاحظة عن حدود الاختبار\n");
        md.append("هذا اختبار منطقي على JVM يستدعي نفس الفئات التي تستدعيها الواجهة ")
                .append("(`LessonPlanner`، `AnswerChecker`، `Tutor`، `ReviewScheduler`، `XpPolicy`، `LessonSession`). ")
                .append("لا يشغّل `MainActivity` نفسها، لأنها تحتاج Android runtime. ")
                .append("لذلك يثبت البيانات والمنطق والتنقل المنطقي، ولا يثبت الرسم على الشاشة.\n\n");
        if (rows.size() - passed > 0) {
            md.append("## الدروس الفاشلة وأسبابها\n\n| الدرس | المستوى | التمارين | السبب |\n|---|---|---|---|\n");
            for (Row row : rows) {
                if (!row.passed) md.append('|').append(row.lesson).append('|').append(row.level)
                        .append('|').append(row.exercises).append('|').append(row.reason()).append("|\n");
            }
        } else {
            md.append("## الأسباب\n\nلا يوجد أي درس فاشل في هذه الجولة.\n");
        }
        md.append("\nالتقرير التفصيلي لكل درس: `releases/LESSON_SESSION_REPORT.csv`.\n");
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(md.toString());
        }
    }

    private static String csvValue(String value) {
        if (value == null) return "";
        String clean = value.replace("\n", " ").replace("\r", " ").replace(",", "،").replace("\"", "”");
        return clean;
    }
}
