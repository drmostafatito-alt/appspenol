package com.habla.espanol;

import java.util.ArrayList;
import java.util.List;

/** Spaced repetition and reward rules. */
final class SchedulerTest {
    private SchedulerTest() {}

    static void run() {
        TestSupport.MapStore store = new TestSupport.MapStore();
        long now = 1_700_000_000_000L;
        ReviewScheduler.State fresh = ReviewScheduler.read(store, "a");
        TestSupport.equals(2.5f, fresh.ease, "new card ease");
        TestSupport.check(ReviewScheduler.isNew(store, "a"), "new card is new");

        ReviewScheduler.grade(store, "a", ReviewScheduler.GOOD, now);
        ReviewScheduler.State good = ReviewScheduler.read(store, "a");
        TestSupport.check(good.dueAt > now, "good answer schedules the future");
        TestSupport.equals(1, good.mastery, "mastery grows");
        long firstInterval = good.intervalMs;
        TestSupport.check(firstInterval >= 86_400_000L, "first interval is at least a day");

        ReviewScheduler.grade(store, "a", ReviewScheduler.GOOD, now + firstInterval);
        ReviewScheduler.State second = ReviewScheduler.read(store, "a");
        TestSupport.check(second.intervalMs > firstInterval, "intervals expand: " + firstInterval + " -> " + second.intervalMs);

        ReviewScheduler.grade(store, "a", ReviewScheduler.AGAIN, now + firstInterval * 2);
        ReviewScheduler.State lapsed = ReviewScheduler.read(store, "a");
        TestSupport.equals(1, lapsed.lapses, "lapse counted");
        TestSupport.check(lapsed.dueAt <= now + firstInterval * 2 + 20 * 60_000L, "again returns within 20 minutes");
        TestSupport.check(lapsed.ease < second.ease, "easy factor drops after a lapse");
        TestSupport.check(store.getInt("misses_a", 0) >= 1, "miss counter written for legacy dashboard");

        ReviewScheduler.grade(store, "b", ReviewScheduler.EASY, now);
        TestSupport.check(ReviewScheduler.read(store, "b").intervalMs >= 2 * 86_400_000L, "easy gives 2+ days");

        List<String> ids = new ArrayList<>(List.of("a", "b"));
        TestSupport.check(ReviewScheduler.dueCount(store, ids, now + 30 * 86_400_000L) == 2,
                "both cards eventually become due");
        TestSupport.check(ReviewScheduler.dueItems(store, ids, now + 30 * 86_400_000L, 1).size() == 1,
                "due list honours the limit");

        TestSupport.MapStore xp = new TestSupport.MapStore();
        int first = XpPolicy.lessonReward(xp, "lesson1", 5);
        int secondReward = XpPolicy.lessonReward(xp, "lesson1", 5);
        TestSupport.equals(15, first, "lesson reward = base + correct");
        TestSupport.equals(0, secondReward, "repeating a lesson never pays twice");
        TestSupport.check(XpPolicy.lessonAlreadyRewarded(xp, "lesson1"), "lesson flagged as rewarded");
        TestSupport.equals(2, XpPolicy.reviewReward(xp, "item", "2026-01-01"), "daily review reward");
        TestSupport.equals(0, XpPolicy.reviewReward(xp, "item", "2026-01-01"), "review reward once per day");
        TestSupport.equals(2, XpPolicy.reviewReward(xp, "item", "2026-01-02"), "reward resets the next day");

        TestSupport.equals(1, XpPolicy.nextStreak(0, "", "d2", "d1"), "first day streak");
        TestSupport.equals(3, XpPolicy.nextStreak(2, "d1", "d2", "d1"), "consecutive day");
        TestSupport.equals(1, XpPolicy.nextStreak(5, "d0", "d2", "d1"), "gap resets streak");
        TestSupport.equals(2, XpPolicy.nextStreak(2, "d2", "d2", "d1"), "same day keeps streak");

        TestSupport.equals(1, XpPolicy.levelFromXp(0), "level 1 at zero");
        TestSupport.check(XpPolicy.levelFromXp(100) == 2, "100 XP unlocks level 2");
        TestSupport.check(XpPolicy.levelFromXp(10_000) > 5, "high XP gives high level");
    }
}
