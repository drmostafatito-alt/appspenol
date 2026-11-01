package com.habla.espanol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Turns the items of a curriculum lesson into a concrete exercise sequence.
 *
 * The plan is deterministic for a lesson id (so tests can assert on it) and mixes
 * recognition, listening, production, word order and translation tasks. Lessons
 * that ship hand written exercises in the asset keep those instead.
 */
final class LessonPlanner {
    private LessonPlanner() {}

    static final int MAX_EXERCISES = 24;

    static List<Curriculum.Exercise> plan(Curriculum model, Curriculum.Lesson lesson) {
        if (!lesson.exercises.isEmpty()) return new ArrayList<>(lesson.exercises);
        List<Curriculum.Item> items = itemsOf(model, lesson);
        if (items.isEmpty()) return new ArrayList<>();
        Random random = new Random(lesson.id.hashCode() * 31L + items.size());
        List<Curriculum.Exercise> plan = new ArrayList<>();

        int intros = items.size() >= 4 ? Math.min(2, items.size()) : 1;
        for (int i = 0; i < intros; i++) plan.add(intro(items.get(i), lesson));

        for (int i = 0; i < items.size(); i++) {
            Curriculum.Item item = items.get(i);
            plan.add(choose(model, item, lesson, random, "«" + item.es + "» معناها إيه؟"));
            if (i < 3 || i % 2 == 0) plan.add(listen(model, item, lesson, random));
            if (item.hasExample()) {
                List<String> words = TextNormalizers.words(item.exEs);
                if (words.size() >= 3 && words.size() <= 9) plan.add(order(item, lesson));
                else plan.add(fill(model, item, lesson, random));
            }
            if (i % 2 == 0) plan.add(translateToSpanish(model, item, lesson, random));
            else plan.add(translateToArabic(item, lesson));
            if (i % 3 == 2) plan.add(speak(item, lesson));
            if (plan.size() >= MAX_EXERCISES - 6) break;
        }
        if (items.size() >= 4) plan.add(match(model, items, lesson, random));
        int finals = Math.min(3, items.size());
        for (int i = 0; i < finals; i++) {
            Curriculum.Item item = items.get((i * 2 + 1) % items.size());
            plan.add(choose(model, item, lesson, random, "اختبار ختامي — اختار معنى «" + item.es + "»"));
        }
        if (plan.size() > MAX_EXERCISES) plan = new ArrayList<>(plan.subList(0, MAX_EXERCISES));
        return withUniqueIds(plan, lesson);
    }

    /**
     * Exercise ids used to be derived from the item hash, which produced duplicates when a
     * lesson revisited an item (final test) or when two hashes collided. Ids are now the
     * lesson id plus the position, so every exercise in a session is addressable.
     */
    private static List<Curriculum.Exercise> withUniqueIds(List<Curriculum.Exercise> plan, Curriculum.Lesson lesson) {
        for (int i = 0; i < plan.size(); i++) plan.get(i).id = lesson.id + "_ex_" + (i + 1);
        return plan;
    }

    static List<Curriculum.Item> itemsOf(Curriculum model, Curriculum.Lesson lesson) {
        List<Curriculum.Item> items = new ArrayList<>();
        for (String id : lesson.itemIds) {
            Curriculum.Item item = model.item(id);
            if (item != null) items.add(item);
        }
        return items;
    }

    private static String id(Curriculum.Lesson lesson, String type, int index) {
        return lesson.id + "_" + type + "_" + index;
    }

    private static Curriculum.Exercise base(String type, String exerciseId, Curriculum.Item item) {
        Curriculum.Exercise exercise = new Curriculum.Exercise();
        exercise.type = type;
        exercise.id = exerciseId;
        exercise.itemId = item == null ? "" : item.id;
        exercise.audio = item == null ? "" : item.es;
        return exercise;
    }

    private static String describe(Curriculum.Item item) {
        StringBuilder out = new StringBuilder();
        out.append(item.es).append(" = ").append(item.ar);
        if (item.hasEgyptian()) out.append(" — بالمصري: ").append(item.egy);
        if (!item.ipa.isEmpty()) out.append("\nالنطق: [").append(item.ipa).append(']');
        if (!item.noteEgy.isEmpty()) out.append('\n').append(item.noteEgy);
        return out.toString();
    }

    static Curriculum.Exercise intro(Curriculum.Item item, Curriculum.Lesson lesson) {
        Curriculum.Exercise exercise = base("intro", id(lesson, "intro", Math.abs(item.id.hashCode() % 100)), item);
        exercise.promptEgy = item.egy.isEmpty() ? item.ar : item.egy;
        exercise.promptFusha = item.ar;
        exercise.explainFusha = item.hasExample() ? "مثال: " + item.exEs : "";
        return exercise;
    }

    static Curriculum.Exercise choose(Curriculum model, Curriculum.Item item, Curriculum.Lesson lesson, Random random, String prompt) {
        Curriculum.Exercise exercise = base("choose", id(lesson, "choose", Math.abs(item.id.hashCode() % 10_000)), item);
        exercise.promptEgy = prompt;
        exercise.promptFusha = prompt;
        List<String> options = new ArrayList<>();
        options.add(displayTranslation(item));
        Set<String> seen = new LinkedHashSet<>();
        seen.add(TextNormalizers.arabicKey(options.get(0)));
        for (Curriculum.Item distractor : distractors(model, item, random, 12)) {
            String value = displayTranslation(distractor);
            if (seen.add(TextNormalizers.arabicKey(value))) options.add(value);
            if (options.size() == 4) break;
        }
        Collections.shuffle(options, random);
        exercise.options = options;
        exercise.answer = options.indexOf(displayTranslation(item));
        exercise.explainEgy = describe(item);
        return exercise;
    }

    static Curriculum.Exercise listen(Curriculum model, Curriculum.Item item, Curriculum.Lesson lesson, Random random) {
        Curriculum.Exercise exercise = base("listen", id(lesson, "listen", Math.abs(item.id.hashCode() % 10_000)), item);
        exercise.promptEgy = "اسمع، وبعدها اختار الشكل الإسباني اللي سمعته.";
        exercise.promptFusha = "استمع ثم اختر الشكل الإسباني الذي سمعته.";
        List<String> options = new ArrayList<>();
        options.add(item.es);
        Set<String> seen = new LinkedHashSet<>();
        seen.add(TextNormalizers.spanishKey(item.es));
        for (Curriculum.Item distractor : distractors(model, item, random, 12)) {
            if (seen.add(TextNormalizers.spanishKey(distractor.es))) options.add(distractor.es);
            if (options.size() == 4) break;
        }
        Collections.shuffle(options, random);
        exercise.options = options;
        exercise.answer = options.indexOf(item.es);
        exercise.explainEgy = "الجملة اللي سمعتها: " + item.es + " — " + (item.hasEgyptian() ? item.egy : item.ar);
        return exercise;
    }

    static Curriculum.Exercise translateToArabic(Curriculum.Item item, Curriculum.Lesson lesson) {
        Curriculum.Exercise exercise = base("translate_es_ar", id(lesson, "tr_ar", Math.abs(item.id.hashCode() % 10_000)), item);
        exercise.promptEgy = "ترجم للعربي: «" + item.es + "»";
        exercise.promptFusha = "ترجم إلى العربية: «" + item.es + "»";
        exercise.answers = new ArrayList<>();
        exercise.answers.add(item.ar);
        if (item.hasEgyptian()) exercise.answers.add(item.egy);
        exercise.explainEgy = "«" + item.es + "» معناها " + item.ar + (item.hasEgyptian() ? " (مصري: " + item.egy + ")" : "") + ".";
        return exercise;
    }

    static Curriculum.Exercise translateToSpanish(Curriculum model, Curriculum.Item item, Curriculum.Lesson lesson, Random random) {
        Curriculum.Exercise exercise = base("translate_ar_es", id(lesson, "tr_es", Math.abs(item.id.hashCode() % 10_000)), item);
        String arabic = item.hasEgyptian() ? item.egy : item.ar;
        exercise.promptEgy = "اكتب بالإسباني: «" + arabic + "»";
        exercise.promptFusha = "اكتب بالإسبانية: «" + item.ar + "»";
        exercise.answers = new ArrayList<>();
        exercise.answers.add(item.es);
        String unaccented = TextNormalizers.spanishKey(item.es);
        if (!unaccented.equals(TextNormalizers.spanishKey(item.es))) exercise.answers.add(unaccented);
        exercise.options = new ArrayList<>(exercise.answers);
        exercise.explainEgy = "الإجابة: " + item.es + (item.ipa.isEmpty() ? "" : " — النطق: [" + item.ipa + "]");
        return exercise;
    }

    static Curriculum.Exercise order(Curriculum.Item item, Curriculum.Lesson lesson) {
        Curriculum.Exercise exercise = base("order", id(lesson, "order", Math.abs(item.id.hashCode() % 10_000)), item);
        exercise.promptEgy = "رتّب الكلمات: «" + (item.hasEgyptian() ? item.exEgy : item.exFusha) + "»";
        exercise.promptFusha = "رتّب الكلمات لتكوين الجملة الصحيحة.";
        List<String> words = new ArrayList<>(TextNormalizers.words(item.exEs));
        exercise.answers = new ArrayList<>();
        exercise.answers.add(item.exEs);
        Collections.shuffle(words, new Random(item.id.hashCode()));
        exercise.options = words;
        exercise.explainEgy = "الجملة الصح: " + item.exEs + " — " + (item.exEgy.isEmpty() ? item.exFusha : item.exEgy);
        return exercise;
    }

    static Curriculum.Exercise fill(Curriculum model, Curriculum.Item item, Curriculum.Lesson lesson, Random random) {
        Curriculum.Exercise exercise = base("fill", id(lesson, "fill", Math.abs(item.id.hashCode() % 10_000)), item);
        List<String> words = new ArrayList<>(TextNormalizers.words(item.exEs));
        int blank = -1;
        for (int i = 0; i < words.size(); i++) if (words.get(i).length() > (blank < 0 ? 0 : words.get(blank).length())) blank = i;
        if (blank < 0) blank = words.size() - 1;
        String target = words.get(blank);
        words.set(blank, "____");
        exercise.promptEgy = "كمّل الجملة: «" + TextNormalizers.join(words) + "» (معناها: " + (item.exEgy.isEmpty() ? item.exFusha : item.exEgy) + ")";
        exercise.promptFusha = "أكمل الجملة بالكلمة الناقصة.";
        List<String> options = new ArrayList<>();
        options.add(target);
        Set<String> seen = new LinkedHashSet<>();
        seen.add(TextNormalizers.spanishKey(target));
        for (Curriculum.Item distractor : distractors(model, item, random, 16)) {
            for (String word : TextNormalizers.words(distractor.es)) {
                if (word.length() > 2 && seen.add(TextNormalizers.spanishKey(word))) options.add(word);
                if (options.size() == 4) break;
            }
            if (options.size() == 4) break;
        }
        // Letter/number lessons have no other multi-letter words in their items, so the pool is
        // widened to the example sentences of neighbouring items before giving up.
        if (options.size() < 3) {
            for (Curriculum.Item distractor : distractors(model, item, random, 24)) {
                if (!distractor.hasExample()) continue;
                for (String word : TextNormalizers.words(distractor.exEs)) {
                    if (word.length() > 2 && seen.add(TextNormalizers.spanishKey(word))) options.add(word);
                    if (options.size() == 4) break;
                }
                if (options.size() == 4) break;
            }
        }
        if (options.size() < 3) {
            for (String word : TextNormalizers.words(item.exEs)) {
                if (word.length() > 2 && seen.add(TextNormalizers.spanishKey(word))) options.add(word);
                if (options.size() == 4) break;
            }
        }
        if (options.size() < 2) return choose(model, item, lesson, random, "«" + item.es + "» معناها إيه؟");
        Collections.shuffle(options, random);
        exercise.options = options;
        exercise.answer = options.indexOf(target);
        exercise.explainEgy = "الجملة كاملة: " + item.exEs + " — " + (item.exEgy.isEmpty() ? item.exFusha : item.exEgy);
        return exercise;
    }

    static Curriculum.Exercise speak(Curriculum.Item item, Curriculum.Lesson lesson) {
        Curriculum.Exercise exercise = base("speak", id(lesson, "speak", Math.abs(item.id.hashCode() % 10_000)), item);
        exercise.promptEgy = "قول الجملة دي بصوتك:";
        exercise.promptFusha = "انطق هذه الجملة.";
        exercise.audio = item.hasExample() ? item.exEs : item.es;
        exercise.answers = new ArrayList<>();
        exercise.answers.add(exercise.audio);
        exercise.explainEgy = "الهدف: نطق واضح. التقييم تقريبي بالتعرف على الكلام، ومش تحليل صوتي احترافي.";
        return exercise;
    }

    static Curriculum.Exercise match(Curriculum model, List<Curriculum.Item> items, Curriculum.Lesson lesson, Random random) {
        Curriculum.Exercise exercise = base("match", id(lesson, "match", 1), items.get(0));
        exercise.promptEgy = "وصّل كل كلمة إسبانية بمعناها العربي.";
        exercise.promptFusha = "صل كل كلمة إسبانية بمعناها العربي.";
        List<Curriculum.Item> pool = new ArrayList<>(items);
        Collections.shuffle(pool, random);
        Set<String> spanishSeen = new LinkedHashSet<>();
        for (Curriculum.Item item : pool) {
            if (!spanishSeen.add(TextNormalizers.spanishKey(item.es))) continue;
            exercise.pairs.add(new String[]{item.es, item.hasEgyptian() ? item.egy : item.ar});
            if (exercise.pairs.size() == 4) break;
        }
        exercise.explainEgy = "المطابقة بتقوّي الربط بين الشكل الإسباني والمعنى.";
        return exercise;
    }

    static List<Curriculum.Item> distractors(Curriculum model, Curriculum.Item item, Random random, int limit) {
        List<Curriculum.Item> preferred = new ArrayList<>();
        List<Curriculum.Item> sameLevel = new ArrayList<>();
        for (Curriculum.Item candidate : model.itemsById.values()) {
            if (candidate.id.equals(item.id)) continue;
            if (TextNormalizers.spanishKey(candidate.es).equals(TextNormalizers.spanishKey(item.es))) continue;
            if (candidate.level.equals(item.level)) {
                sameLevel.add(candidate);
                if (candidate.unit.equals(item.unit) || candidate.topic.equals(item.topic)) preferred.add(candidate);
            }
        }
        Collections.shuffle(preferred, random);
        Collections.shuffle(sameLevel, random);
        List<Curriculum.Item> pool = new ArrayList<>(preferred);
        for (Curriculum.Item candidate : sameLevel) if (!containsItem(pool, candidate)) pool.add(candidate);
        if (pool.size() < limit) {
            List<Curriculum.Item> others = new ArrayList<>();
            for (Curriculum.Item candidate : model.itemsById.values()) {
                if (candidate.id.equals(item.id) || containsItem(pool, candidate)) continue;
                others.add(candidate);
            }
            Collections.shuffle(others, random);
            pool.addAll(others);
        }
        return pool.size() > limit ? new ArrayList<>(pool.subList(0, limit)) : pool;
    }

    private static boolean containsItem(List<Curriculum.Item> pool, Curriculum.Item item) {
        for (Curriculum.Item candidate : pool) if (candidate.id.equals(item.id)) return true;
        return false;
    }

    static String displayTranslation(Curriculum.Item item) {
        return item.hasEgyptian() ? item.egy : item.ar;
    }
}
