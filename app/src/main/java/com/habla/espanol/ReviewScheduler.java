package com.habla.espanol;

/**
 * Spaced repetition for every curriculum item (SM-2 style, 4 answer grades).
 *
 * Storage keys are the historical ones (`ease_`, `interval_`, `due_`, `misses_`)
 * so progress saved by earlier versions keeps working; new keys are additive:
 *   reps_<id>, lapses_<id>, mastery_<id>, srs_correct_<id>, srs_attempts_<id>, srs_log_<id>
 */
final class ReviewScheduler {
    static final int AGAIN = 0, HARD = 1, GOOD = 2, EASY = 3;
    private static final long MINUTE = 60_000L;
    private static final long DAY = 86_400_000L;
    private static final long MAX_INTERVAL = 180 * DAY;

    /** Storage abstraction so the scheduler can be tested without Android. */
    interface Store {
        int getInt(String key, int fallback);
        void putInt(String key, int value);
        long getLong(String key, long fallback);
        void putLong(String key, long value);
        float getFloat(String key, float fallback);
        void putFloat(String key, float value);
        String getString(String key, String fallback);
        void putString(String key, String value);
    }

    static final class State {
        float ease = 2.5f;
        long intervalMs = 0;
        long dueAt = 0;
        int reps = 0;
        int lapses = 0;
        int mastery = 0;      // 0..5
        int attempts = 0;
        int correct = 0;
        String log = "";      // compact history, newest first: "q2|t3"
    }

    private ReviewScheduler() {}

    static State read(Store store, String itemId) {
        State state = new State();
        state.ease = clampEase(store.getFloat("ease_" + itemId, 2.5f));
        state.intervalMs = Math.max(0, store.getLong("interval_" + itemId, 0));
        state.dueAt = store.getLong("due_" + itemId, 0);
        state.reps = Math.max(0, store.getInt("reps_" + itemId, 0));
        state.lapses = Math.max(0, store.getInt("lapses_" + itemId, 0));
        state.mastery = Math.max(0, Math.min(5, store.getInt("mastery_" + itemId, 0)));
        state.attempts = Math.max(0, store.getInt("srs_attempts_" + itemId, 0));
        state.correct = Math.max(0, store.getInt("srs_correct_" + itemId, 0));
        state.log = store.getString("srs_log_" + itemId, "");
        return state;
    }

    static State grade(Store store, String itemId, int quality, long now) {
        if (quality < AGAIN || quality > EASY) throw new IllegalArgumentException("invalid quality " + quality);
        State state = read(store, itemId);
        state.attempts++;
        if (quality == AGAIN) {
            state.lapses++;
            state.reps = 0;
            state.ease = clampEase(state.ease - 0.2f);
            state.intervalMs = 0;
            state.dueAt = now + 10 * MINUTE;
            state.mastery = Math.max(0, state.mastery - 1);
            store.putInt("misses_" + itemId, store.getInt("misses_" + itemId, 0) + 1);
        } else {
            state.correct++;
            state.reps++;
            if (quality == HARD) {
                state.ease = clampEase(state.ease - 0.15f);
                state.intervalMs = state.intervalMs <= 0 ? DAY : Math.min(MAX_INTERVAL, (long) (state.intervalMs * 1.2));
            } else if (quality == GOOD) {
                state.intervalMs = state.intervalMs <= 0 ? DAY : Math.min(MAX_INTERVAL, (long) (state.intervalMs * Math.max(1.2f, state.ease)));
                state.mastery = Math.min(5, state.mastery + 1);
            } else {
                state.ease = clampEase(state.ease + 0.1f);
                state.intervalMs = state.intervalMs <= 0 ? 2 * DAY : Math.min(MAX_INTERVAL, (long) (state.intervalMs * Math.max(1.3f, state.ease) * 1.15));
                state.mastery = Math.min(5, state.mastery + 1);
            }
            state.dueAt = now + state.intervalMs;
            store.putInt("misses_" + itemId, Math.max(0, store.getInt("misses_" + itemId, 0) - 1));
        }
        state.log = (("q" + quality + "|" + state.attempts) + (state.log.isEmpty() ? "" : "," + state.log));
        if (state.log.length() > 60) state.log = state.log.substring(0, 60);

        store.putFloat("ease_" + itemId, state.ease);
        store.putLong("interval_" + itemId, state.intervalMs);
        store.putLong("due_" + itemId, state.dueAt);
        store.putInt("reps_" + itemId, state.reps);
        store.putInt("lapses_" + itemId, state.lapses);
        store.putInt("mastery_" + itemId, state.mastery);
        store.putInt("srs_attempts_" + itemId, state.attempts);
        store.putInt("srs_correct_" + itemId, state.correct);
        store.putString("srs_log_" + itemId, state.log);
        return state;
    }

    /** Maps an exercise outcome to a review grade. */
    static int gradeFor(boolean correct, boolean usedHint, int previousLapses) {
        if (!correct) return AGAIN;
        if (usedHint) return HARD;
        if (previousLapses > 2) return HARD;
        return GOOD;
    }

    static boolean isDue(Store store, String itemId, long now) {
        long due = store.getLong("due_" + itemId, 0);
        return due > 0 && due <= now;
    }

    static boolean isNew(Store store, String itemId) {
        return store.getInt("srs_attempts_" + itemId, 0) == 0;
    }

    static int masteryLevel(Store store, String itemId) {
        return Math.max(0, Math.min(5, store.getInt("mastery_" + itemId, 0)));
    }

    /** Items that are due now, ordered by urgency (oldest due date first). */
    static java.util.List<String> dueItems(Store store, java.util.List<String> itemIds, long now, int limit) {
        java.util.ArrayList<String> due = new java.util.ArrayList<>();
        for (String id : itemIds) {
            long dueAt = store.getLong("due_" + id, 0);
            if (dueAt > 0 && dueAt <= now) due.add(id);
        }
        due.sort((a, b) -> Long.compare(store.getLong("due_" + a, 0), store.getLong("due_" + b, 0)));
        if (limit > 0 && due.size() > limit) return new java.util.ArrayList<>(due.subList(0, limit));
        return due;
    }

    static int dueCount(Store store, java.util.List<String> itemIds, long now) {
        int count = 0;
        for (String id : itemIds) {
            long dueAt = store.getLong("due_" + id, 0);
            if (dueAt > 0 && dueAt <= now) count++;
        }
        return count;
    }

    static int masteredCount(Store store, java.util.List<String> itemIds) {
        int count = 0;
        for (String id : itemIds) if (store.getInt("mastery_" + id, 0) >= 3) count++;
        return count;
    }

    static long nextDue(Store store, java.util.List<String> itemIds, long now) {
        long next = 0;
        for (String id : itemIds) {
            long dueAt = store.getLong("due_" + id, 0);
            if (dueAt > now && (next == 0 || dueAt < next)) next = dueAt;
        }
        return next;
    }

    private static float clampEase(float ease) {
        return Math.max(1.3f, Math.min(3.0f, ease));
    }
}
