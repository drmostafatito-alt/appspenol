package com.habla.espanol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tiny assertion + fake storage helpers shared by the JVM test classes. */
final class TestSupport {
    static int checks = 0;
    static final List<String> failures = new ArrayList<>();

    private TestSupport() {}

    static void check(boolean condition, String message) {
        checks++;
        if (!condition) failures.add(message);
    }

    static void equals(Object expected, Object actual, String message) {
        checks++;
        boolean same = expected == null ? actual == null : expected.equals(actual);
        if (!same) failures.add(message + " (expected=" + expected + ", actual=" + actual + ")");
    }

    static void notEmpty(String value, String message) {
        checks++;
        if (value == null || value.trim().isEmpty()) failures.add(message + " (empty)");
    }

    static final class MapStore implements ReviewScheduler.Store {
        final Map<String, Object> values = new HashMap<>();

        public int getInt(String key, int fallback) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : fallback;
        }
        public void putInt(String key, int value) { values.put(key, value); }
        public long getLong(String key, long fallback) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : fallback;
        }
        public void putLong(String key, long value) { values.put(key, value); }
        public float getFloat(String key, float fallback) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : fallback;
        }
        public void putFloat(String key, float value) { values.put(key, value); }
        public String getString(String key, String fallback) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : fallback;
        }
        public void putString(String key, String value) { values.put(key, value); }
    }
}
