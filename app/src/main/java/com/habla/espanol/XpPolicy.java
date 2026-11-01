package com.habla.espanol;

/**
 * Reward rules. XP is never counted twice for the same lesson or for the same
 * reviewed item on the same day, no matter how often a screen is reopened.
 */
final class XpPolicy {
    private XpPolicy() {}

    static final int LESSON_BASE = 10;
    static final int REVIEW_ITEM = 2;

    /** XP for finishing a lesson: full reward only on the first completion. */
    static int lessonReward(ReviewScheduler.Store store, String lessonId, int firstAttemptCorrect) {
        String key = "xp_lesson_" + lessonId;
        if (store.getInt(key, 0) == 1) return 0;
        store.putInt(key, 1);
        return LESSON_BASE + Math.max(0, firstAttemptCorrect);
    }

    static boolean lessonAlreadyRewarded(ReviewScheduler.Store store, String lessonId) {
        return store.getInt("xp_lesson_" + lessonId, 0) == 1;
    }

    /** Small, once-per-day reward for reviewing an item so grinding the same card pays nothing. */
    static int reviewReward(ReviewScheduler.Store store, String itemId, String dayKey) {
        String key = "xp_review_" + itemId + "_" + dayKey;
        if (store.getInt(key, 0) == 1) return 0;
        store.putInt(key, 1);
        return REVIEW_ITEM;
    }

    /** Streak update: consecutive calendar days of activity. */
    static int nextStreak(int currentStreak, String lastDay, String today, String yesterday) {
        if (today == null || today.isEmpty()) return currentStreak;
        if (today.equals(lastDay)) return Math.max(1, currentStreak);
        if (yesterday != null && yesterday.equals(lastDay)) return Math.max(1, currentStreak) + 1;
        return 1;
    }

    static int levelFromXp(int xp) {
        if (xp <= 0) return 1;
        int level = 1;
        int threshold = 100;
        int remaining = xp;
        while (remaining >= threshold) {
            remaining -= threshold;
            level++;
            threshold += 50;
        }
        return level;
    }

    static String levelTitle(int level) {
        String[] titles = {"مبتدئ", "متعلّم", "متقدّم", "متمكّن", "خبير"};
        return titles[Math.max(0, Math.min(titles.length - 1, level - 1))];
    }
}
