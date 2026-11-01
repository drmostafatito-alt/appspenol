package com.habla.espanol;

/**
 * Durable state of one lesson session.
 *
 * The session used to live only in the Activity, so closing the app in the middle
 * of a lesson threw away the position, the score and the mistakes. This class
 * serialises the minimum state needed to reopen a lesson exactly where it stopped:
 * lesson id, exercise cursor, total exercises, first-try score and the mistake log.
 *
 * It is Android-free on purpose so `tools/java_tests/SessionFlowTest.java` can prove
 * the round-trip for every lesson in the curriculum.
 */
final class LessonSession {
    static final String KEY = "lesson_session";
    static final String VERSION = "s1";
    private static final char SEP = '|';
    private static final int MAX_MISTAKES = 400;
    private static final int MAX_FIELD = 120;

    String lessonId = "";
    String dialogueId = "";
    int cursor = 0;
    int total = 0;
    int firstCorrect = 0;
    int firstAttempted = 0;
    int attempted = 0;
    int correct = 0;
    boolean reviewMode = false;
    long startedAt = 0;
    String mistakes = "";
    String itemIds = "";

    private LessonSession() {}

    static LessonSession begin(String lessonId, String dialogueId, int total, boolean reviewMode, long now) {
        LessonSession session = new LessonSession();
        session.lessonId = safe(lessonId);
        session.dialogueId = safe(dialogueId);
        session.total = Math.max(0, total);
        session.reviewMode = reviewMode;
        session.startedAt = now;
        return session;
    }

    /** Rebuilds the counters that the Activity also keeps locally. */
    static LessonSession of(String lessonId, String dialogueId, int cursor, int total, int firstCorrect,
                            int firstAttempted, int attempted, int correct, boolean reviewMode, long startedAt) {
        LessonSession session = begin(lessonId, dialogueId, total, reviewMode, startedAt);
        session.cursor = clamp(cursor, 0, Math.max(0, total));
        session.firstCorrect = Math.max(0, firstCorrect);
        session.firstAttempted = Math.max(0, firstAttempted);
        session.attempted = Math.max(0, attempted);
        session.correct = Math.max(0, correct);
        return session;
    }

    void record(boolean correctAnswer, boolean retry, String itemId, String type) {
        attempted++;
        if (correctAnswer) correct++;
        if (!retry) {
            firstAttempted++;
            if (correctAnswer) firstCorrect++;
        }
        if (!correctAnswer && itemId != null && !itemId.isEmpty()) {
            String entry = safe(itemId) + ":" + safe(type);
            mistakes = mistakes.isEmpty() ? entry : mistakes + "," + entry;
            if (mistakes.length() > MAX_MISTAKES) mistakes = mistakes.substring(mistakes.length() - MAX_MISTAKES);
        }
    }

    int advance() {
        cursor = clamp(cursor + 1, 0, Math.max(0, total));
        return cursor;
    }

    boolean finished() {
        return cursor >= total;
    }

    int mistakeCount() {
        if (mistakes.isEmpty()) return 0;
        int count = 1;
        for (int i = 0; i < mistakes.length(); i++) if (mistakes.charAt(i) == ',') count++;
        return count;
    }

    String encode() {
        StringBuilder out = new StringBuilder();
        out.append(VERSION).append(SEP)
                .append(safe(lessonId)).append(SEP)
                .append(cursor).append(SEP)
                .append(total).append(SEP)
                .append(firstCorrect).append(SEP)
                .append(firstAttempted).append(SEP)
                .append(attempted).append(SEP)
                .append(correct).append(SEP)
                .append(reviewMode ? 1 : 0).append(SEP)
                .append(startedAt).append(SEP)
                .append(safe(dialogueId)).append(SEP)
                .append(mistakes).append(SEP)
                .append(itemIds);
        return out.toString();
    }

    /** Returns null when the stored value is missing or corrupted — never a half-session. */
    static LessonSession decode(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String[] parts = raw.split("\\" + SEP, -1);
        if (parts.length != 13 || !VERSION.equals(parts[0])) return null;
        if (!validId(parts[1])) return null;
        LessonSession session = new LessonSession();
        session.lessonId = parts[1];
        session.cursor = number(parts[2]);
        session.total = number(parts[3]);
        session.firstCorrect = number(parts[4]);
        session.firstAttempted = number(parts[5]);
        session.attempted = number(parts[6]);
        session.correct = number(parts[7]);
        if (session.cursor < 0 || session.total < 0 || session.firstCorrect < 0 || session.firstAttempted < 0
                || session.attempted < 0 || session.correct < 0) return null;
        if (session.firstCorrect > session.firstAttempted || session.correct > session.attempted) return null;
        if (session.total == 0) return null;
        session.cursor = clamp(session.cursor, 0, session.total);
        session.reviewMode = "1".equals(parts[8]);
        long started = 0;
        try { started = Long.parseLong(parts[9]); } catch (NumberFormatException ignored) { return null; }
        session.startedAt = Math.max(0, started);
        session.dialogueId = validId(parts[10]) ? parts[10] : "";
        session.mistakes = parts[11].length() > MAX_MISTAKES ? parts[11].substring(0, MAX_MISTAKES) : parts[11];
        session.itemIds = cleanList(parts[12]);
        if (session.finished() && session.attempted == 0) return null;
        return session;
    }

    static void save(ReviewScheduler.Store store, LessonSession session) {
        if (store == null) return;
        if (session == null) { store.putString(KEY, ""); return; }
        store.putString(KEY, session.encode());
    }

    static LessonSession load(ReviewScheduler.Store store) {
        if (store == null) return null;
        return decode(store.getString(KEY, ""));
    }

    static void clear(ReviewScheduler.Store store) {
        if (store != null) store.putString(KEY, "");
    }

    /** Progress the learner sees when reopening: "12 من 20". */
    String progressLabel() {
        return Math.min(cursor, total) + " من " + total;
    }

    private static int number(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException error) { return -1; }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.length() > MAX_FIELD) trimmed = trimmed.substring(0, MAX_FIELD);
        return trimmed.replace(String.valueOf(SEP), "_").replace(",", "_").replace("\n", " ");
    }

    /** Comma separated curriculum item ids, used to rebuild a saved review session. */
    void setItems(java.util.List<String> ids) {
        StringBuilder out = new StringBuilder();
        if (ids != null) {
            for (String id : ids) {
                if (id == null || !validId(id)) continue;
                if (out.length() > 0) out.append(',');
                out.append(id);
                if (out.length() > 6000) break;
            }
        }
        itemIds = out.toString();
    }

    java.util.List<String> items() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        if (itemIds.isEmpty()) return ids;
        for (String id : itemIds.split(",")) if (validId(id)) ids.add(id);
        return ids;
    }

    private static String cleanList(String raw) {
        StringBuilder out = new StringBuilder();
        for (String id : raw.split(",")) {
            if (!validId(id)) continue;
            if (out.length() > 0) out.append(',');
            out.append(id);
            if (out.length() > 6000) break;
        }
        return out.toString();
    }

    private static boolean validId(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_FIELD) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) return false;
        }
        return true;
    }
}
