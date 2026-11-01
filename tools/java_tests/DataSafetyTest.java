package com.habla.espanol;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Data-safety checks: old progress must survive an app update, XP never goes backwards,
 * review history is additive, and preference types are validated on import.
 */
final class DataSafetyTest {
    private DataSafetyTest() {}

    static void run(Curriculum model) {
        legacyFavorites();
        legacyProgressSurvivesUpdate();
        additiveReviewKeys();
        xpAndStreakMonotonic();
        importedPreferenceValidation();
        sessionDoesNotTouchProgress();
        deterministicContent(model);
    }

    /** Old favorites were stored as "[1_2_3,1_2_4]" and must still parse. */
    private static void legacyFavorites() {
        Set<String> parsed = LearningLogic.legacyFavorites("[12_3_4,12_3_5]");
        TestSupport.check(parsed.size() == 2 && parsed.contains("12_3_4"), "data: legacy favorites parse");
        TestSupport.check(LearningLogic.legacyFavorites("[]").isEmpty(), "data: empty legacy favorites parse");
        boolean threw = false;
        try { LearningLogic.legacyFavorites("not-a-list"); } catch (IllegalArgumentException expected) { threw = true; }
        TestSupport.check(threw, "data: corrupt legacy favorites are rejected, not silently wiped");
        threw = false;
        try { LearningLogic.legacyFavorites("[oops,1_2_3]"); } catch (IllegalArgumentException expected) { threw = true; }
        TestSupport.check(threw, "data: corrupt favorite ids are rejected");
    }

    /** A store written by 8.0 must read back byte-for-byte after the 8.1 code runs. */
    private static void legacyProgressSurvivesUpdate() {
        TestSupport.MapStore store = new TestSupport.MapStore();
        store.putInt("xp", 1234);
        store.putInt("streak", 7);
        store.putString("last_day", "2026-10-31");
        store.putInt("daily_goal", 3);
        store.putInt("complete_a0_alfabeto_1", 1);
        store.putInt("misses_a0_hola_1", 2);
        store.putInt("total_first_correct", 500);
        store.putInt("total_first_attempted", 640);
        store.putStringSet("done_lessons", new LinkedHashSet<>(java.util.Arrays.asList("a0_alfabeto_1", "a1_familia_1")));
        store.putStringSet("dictionary_favorites", new LinkedHashSet<>(java.util.Arrays.asList("a0_hola_1")));
        store.putFloat("ease_a0_hola_1", 2.4f);
        store.putLong("due_a0_hola_1", 1_700_000_000_000L);
        store.putLong("interval_a0_hola_1", 86_400_000L);
        store.putFloat("tts_rate", 0.92f);
        store.putString("last_track", "A1");

        Map<String, Object> before = new HashMap<>(store.values);
        // Simulating the update: the new code only reads and adds keys.
        ReviewScheduler.State state = ReviewScheduler.read(store, "a0_hola_1");
        TestSupport.check(state.ease > 2.3f && state.ease < 2.5f, "data: ease_ from 8.0 is readable");
        TestSupport.check(state.dueAt == 1_700_000_000_000L, "data: due_ from 8.0 is readable");
        TestSupport.check(state.attempts == 0 && state.mastery == 0, "data: new counters default without overwriting");

        for (Map.Entry<String, Object> entry : before.entrySet()) {
            Object after = store.values.get(entry.getKey());
            TestSupport.check(java.util.Objects.equals(entry.getValue(), after),
                    "data: key preserved across update: " + entry.getKey());
        }
        TestSupport.check(store.getInt("xp", 0) == 1234, "data: XP preserved");
        TestSupport.check(store.getStringSet("done_lessons", new LinkedHashSet<>()).size() == 2,
                "data: completed lessons preserved");
        TestSupport.check(store.getStringSet("dictionary_favorites", new LinkedHashSet<>()).contains("a0_hola_1"),
                "data: favorites preserved");
    }

    /** New review keys are additive; grading never deletes historical counters. */
    private static void additiveReviewKeys() {
        TestSupport.MapStore store = new TestSupport.MapStore();
        store.putInt("misses_item1", 4);
        store.putLong("due_item1", 1L);
        ReviewScheduler.State state = ReviewScheduler.grade(store, "item1", ReviewScheduler.GOOD, 2_000_000L);
        TestSupport.check(store.getInt("misses_item1", 0) == 3, "data: a correct answer lowers misses by one");
        TestSupport.check(state.attempts == 1 && state.correct == 1, "data: attempt log starts at one");
        TestSupport.check(store.getString("srs_log_item1", "").startsWith("q2|"), "data: review log is written");
        ReviewScheduler.grade(store, "item1", ReviewScheduler.AGAIN, 3_000_000L);
        TestSupport.check(store.getInt("srs_attempts_item1", 0) == 2, "data: attempts accumulate");
        TestSupport.check(store.getInt("misses_item1", 0) == 4, "data: a lapse increments misses again");
        TestSupport.check(store.getInt("lapses_item1", 0) >= 1, "data: lapses are recorded");
        TestSupport.check(store.getLong("due_item1", 0L) > 3_000_000L, "data: due date moves forward");
        // Corrupt / negative stored values must never break reading.
        TestSupport.MapStore corrupt = new TestSupport.MapStore();
        corrupt.putInt("mastery_bad", 99);
        corrupt.putInt("srs_attempts_bad", -5);
        ReviewScheduler.State safe = ReviewScheduler.read(corrupt, "bad");
        TestSupport.check(safe.mastery <= 5 && safe.attempts == 0, "data: corrupt counters are clamped, not thrown");
    }

    private static void xpAndStreakMonotonic() {
        TestSupport.MapStore store = new TestSupport.MapStore();
        int first = XpPolicy.lessonReward(store, "lesson_x", 8);
        int second = XpPolicy.lessonReward(store, "lesson_x", 8);
        TestSupport.check(first > 0 && second == 0, "data: lesson XP is paid once");
        int reviewFirst = XpPolicy.reviewReward(store, "item_x", "2026-11-01");
        int reviewAgain = XpPolicy.reviewReward(store, "item_x", "2026-11-01");
        TestSupport.check(reviewFirst > 0 && reviewAgain == 0, "data: review XP is once per day");
        int nextDay = XpPolicy.reviewReward(store, "item_x", "2026-11-02");
        TestSupport.check(nextDay > 0, "data: review XP resets on a new day");
        TestSupport.check(XpPolicy.nextStreak(5, "2026-10-31", "2026-11-01", "2026-10-31") == 6,
                "data: streak continues the next day");
        TestSupport.check(XpPolicy.nextStreak(5, "2026-11-01", "2026-11-01", "2026-10-31") == 5,
                "data: streak does not double count the same day");
        TestSupport.check(XpPolicy.nextStreak(5, "2026-10-20", "2026-11-01", "2026-10-31") == 1,
                "data: streak restarts after a gap");
        TestSupport.check(XpPolicy.levelFromXp(0) == 1 && XpPolicy.levelFromXp(100) == 2,
                "data: level thresholds are stable");
    }

    private static void importedPreferenceValidation() {
        TestSupport.check(LearningLogic.validPreferenceType("xp", 12), "data: import accepts xp as int");
        TestSupport.check(!LearningLogic.validPreferenceType("xp", "12"), "data: import rejects xp as string");
        TestSupport.check(LearningLogic.validPreferenceType("ease_a", 2.3f), "data: import accepts ease as float");
        TestSupport.check(!LearningLogic.validPreferenceType("ease_a", 2), "data: import rejects ease as int");
        TestSupport.check(LearningLogic.validPreferenceType("due_a", 99L), "data: import accepts due as long");
        TestSupport.check(LearningLogic.validPreferenceType("dictionary_favorites", new LinkedHashSet<String>()),
                "data: import accepts favorites set");
        TestSupport.check(!LearningLogic.validPreferenceType("dictionary_favorites", "a,b"),
                "data: import rejects favorites as flat string");
        TestSupport.check(LearningLogic.validPreferenceType("dark_mode", true), "data: import accepts booleans");
        TestSupport.check(!LearningLogic.validPreferenceType("dark_mode", "yes"), "data: import rejects boolean as string");
        TestSupport.check(LearningLogic.validPreferenceType("unknown_key", 1), "data: unknown keys are kept");
    }

    /** A mid-lesson session snapshot must not collide with any progress key. */
    private static void sessionDoesNotTouchProgress() {
        TestSupport.MapStore store = new TestSupport.MapStore();
        store.putInt("xp", 900);
        store.putInt("streak", 4);
        store.putStringSet("dictionary_favorites", new LinkedHashSet<>(java.util.Arrays.asList("a_1")));
        LessonSession session = LessonSession.begin("a1_x_1", "", 12, false, 1_000L);
        session.record(true, false, "a1_item", "choose");
        session.advance();
        LessonSession.save(store, session);
        TestSupport.check(store.getInt("xp", 0) == 900, "data: saving a session never rewrites XP");
        TestSupport.check(store.getStringSet("dictionary_favorites", new LinkedHashSet<>()).size() == 1,
                "data: saving a session never rewrites favorites");
        TestSupport.check(store.values.containsKey(LessonSession.KEY), "data: session lives in its own key");
        LessonSession restored = LessonSession.load(store);
        TestSupport.check(restored != null && restored.cursor == 1 && restored.lessonId.equals("a1_x_1"),
                "data: session restores after restart");
        TestSupport.check(LessonSession.decode("garbage") == null, "data: corrupt session is ignored");
        TestSupport.check(LessonSession.decode("s1|bad id|1|2|0|0|0|0|0|0|||") == null,
                "data: session with an invalid lesson id is ignored");
        TestSupport.check(LessonSession.decode("s1|a1_x_1|-1|12|0|0|0|0|0|0|||") == null,
                "data: session with a negative cursor is ignored");
        LessonSession cleared = LessonSession.begin("a1_x_1", "", 3, false, 1L);
        LessonSession.clear(store);
        TestSupport.check(LessonSession.load(store) == null && cleared != null, "data: finishing clears the session");
    }

    /** Content ids must be stable across reloads so saved progress still points at real items. */
    private static void deterministicContent(Curriculum model) {
        Set<String> ids = new LinkedHashSet<>(model.itemsById.keySet());
        Set<String> lessonIds = new LinkedHashSet<>(model.lessonsById.keySet());
        TestSupport.check(ids.size() > 2_000, "data: item ids loaded (" + ids.size() + ")");
        TestSupport.check(lessonIds.size() > 400, "data: lesson ids loaded (" + lessonIds.size() + ")");
        for (Curriculum.Lesson lesson : model.lessonsById.values()) {
            for (String itemId : lesson.itemIds) {
                TestSupport.check(model.itemsById.containsKey(itemId),
                        "data: lesson " + lesson.id + " points at missing item " + itemId);
            }
        }
        Curriculum reparsed = Curriculum.parse(ContentTest.rawJson);
        TestSupport.check(reparsed.itemsById.keySet().equals(ids), "data: item ids are deterministic across reloads");
        TestSupport.check(reparsed.lessonsById.keySet().equals(lessonIds),
                "data: lesson ids are deterministic across reloads");
    }
}
