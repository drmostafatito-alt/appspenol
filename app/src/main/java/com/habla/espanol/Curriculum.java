package com.habla.espanol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offline curriculum model (schema v3).
 *
 * Everything the app teaches lives in `assets/content.json` and is parsed here.
 * The class has no Android dependencies so it can be unit-tested on a plain JVM
 * with the real asset file.
 */
final class Curriculum {

    static final class Item {
        String id = "", es = "", ar = "", egy = "", ipa = "", pos = "", topic = "", noteEgy = "";
        String exEs = "", exFusha = "", exEgy = "", verification = "machine_checked", type = "word";
        String level = "", unit = "";
        int freq = 0;

        boolean hasEgyptian() { return egy != null && !egy.isEmpty(); }
        boolean hasExample() { return exEs != null && !exEs.isEmpty(); }
    }

    static final class Exercise {
        String id = "", type = "choose", itemId = "", audio = "";
        String promptEgy = "", promptFusha = "", explainEgy = "", explainFusha = "", contextEgy = "";
        String rule = "";
        List<String> options = new ArrayList<>();
        List<String> answers = new ArrayList<>();
        List<String[]> pairs = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        int answer = -1;
    }

    static final class Lesson {
        String id = "", title = "", titleEs = "", goalEgy = "";
        List<String> itemIds = new ArrayList<>();
        List<Exercise> exercises = new ArrayList<>();
        int index = 0;
        String unitId = "", levelId = "";
    }

    static final class Unit {
        String id = "", title = "", titleEs = "", goalEgy = "", kind = "";
        List<Item> items = new ArrayList<>();
        List<Lesson> lessons = new ArrayList<>();
        String levelId = "";
    }

    static final class Level {
        String id = "", title = "", titleEs = "", goalEgy = "", descEgy = "";
        List<Unit> units = new ArrayList<>();
    }

    static final class Line {
        String who = "", es = "", ar = "", egy = "";
    }

    static final class Dialogue {
        String id = "", section = "", level = "", titleAr = "", titleEgy = "", titleEs = "", contextEgy = "", contextFusha = "";
        List<Line> lines = new ArrayList<>();
        List<Exercise> questions = new ArrayList<>();
    }

    static final class GrammarRule {
        String id = "", level = "", titleAr = "", ruleEgy = "", ruleFusha = "";
        List<String[]> examples = new ArrayList<>();
    }

    final List<Level> levels = new ArrayList<>();
    final List<Dialogue> dialogues = new ArrayList<>();
    final List<GrammarRule> grammar = new ArrayList<>();
    final Map<String, Item> itemsById = new LinkedHashMap<>();
    final Map<String, Lesson> lessonsById = new LinkedHashMap<>();
    final Map<String, Dialogue> dialoguesById = new LinkedHashMap<>();
    final Map<String, Integer> stats = new LinkedHashMap<>();
    String generated = "";

    static Curriculum parse(String json) {
        return fromRoot(Json.parseObject(json));
    }

    static Curriculum fromRoot(Map<String, Object> root) {
        Curriculum model = new Curriculum();
        model.generated = Json.str(root, "generated");
        Map<String, Object> stats = Json.map(root.get("stats"));
        for (Map.Entry<String, Object> entry : stats.entrySet()) {
            if (entry.getValue() instanceof Number) model.stats.put(entry.getKey(), ((Number) entry.getValue()).intValue());
        }
        for (Object rawLevel : Json.list(root, "levels")) {
            Map<String, Object> levelMap = Json.map(rawLevel);
            Level level = new Level();
            level.id = Json.str(levelMap, "id");
            level.title = Json.str(levelMap, "title");
            level.titleEs = Json.str(levelMap, "title_es");
            level.goalEgy = Json.str(levelMap, "goal_egy");
            level.descEgy = Json.str(levelMap, "desc_egy");
            if (level.id.isEmpty()) throw new IllegalArgumentException("level without id");
            for (Object rawUnit : Json.list(levelMap, "units")) {
                Map<String, Object> unitMap = Json.map(rawUnit);
                Unit unit = new Unit();
                unit.id = Json.str(unitMap, "id");
                unit.title = Json.str(unitMap, "title");
                unit.titleEs = Json.str(unitMap, "title_es");
                unit.goalEgy = Json.str(unitMap, "goal_egy");
                unit.kind = Json.str(unitMap, "kind");
                unit.levelId = level.id;
                if (unit.id.isEmpty()) throw new IllegalArgumentException("unit without id in " + level.id);
                for (Object rawItem : Json.list(unitMap, "items")) {
                    Item item = readItem(Json.map(rawItem), level.id, unit.id);
                    if (model.itemsById.containsKey(item.id)) throw new IllegalArgumentException("duplicate item id " + item.id);
                    model.itemsById.put(item.id, item);
                    unit.items.add(item);
                }
                int index = 0;
                for (Object rawLesson : Json.list(unitMap, "lessons")) {
                    Lesson lesson = readLesson(Json.map(rawLesson));
                    lesson.index = index++;
                    lesson.unitId = unit.id;
                    lesson.levelId = level.id;
                    if (model.lessonsById.containsKey(lesson.id)) throw new IllegalArgumentException("duplicate lesson id " + lesson.id);
                    model.lessonsById.put(lesson.id, lesson);
                    unit.lessons.add(lesson);
                }
                level.units.add(unit);
            }
            model.levels.add(level);
        }
        for (Object rawDialogue : Json.list(root, "dialogues")) {
            Map<String, Object> map = Json.map(rawDialogue);
            Dialogue dialogue = new Dialogue();
            dialogue.id = Json.str(map, "id");
            dialogue.section = Json.str(map, "section");
            dialogue.level = Json.str(map, "level");
            dialogue.titleAr = Json.str(map, "title_ar");
            dialogue.titleEgy = Json.str(map, "title_egy");
            dialogue.titleEs = Json.str(map, "title_es");
            dialogue.contextEgy = Json.str(map, "context_egy");
            dialogue.contextFusha = Json.str(map, "context_fusha");
            for (Object rawLine : Json.list(map, "lines")) {
                Map<String, Object> lineMap = Json.map(rawLine);
                Line line = new Line();
                line.who = Json.str(lineMap, "who");
                line.es = Json.str(lineMap, "es");
                line.ar = Json.str(lineMap, "ar");
                line.egy = Json.str(lineMap, "egy");
                dialogue.lines.add(line);
            }
            for (Object rawQuestion : Json.list(map, "questions")) {
                dialogue.questions.add(readExercise(Json.map(rawQuestion)));
            }
            if (model.dialoguesById.containsKey(dialogue.id)) throw new IllegalArgumentException("duplicate dialogue id " + dialogue.id);
            model.dialoguesById.put(dialogue.id, dialogue);
            model.dialogues.add(dialogue);
        }
        for (Object rawRule : Json.list(root, "grammar")) {
            Map<String, Object> map = Json.map(rawRule);
            GrammarRule rule = new GrammarRule();
            rule.id = Json.str(map, "id");
            rule.level = Json.str(map, "level");
            rule.titleAr = Json.str(map, "title_ar");
            rule.ruleEgy = Json.str(map, "rule_egy");
            rule.ruleFusha = Json.str(map, "rule_fusha");
            for (Object rawExample : Json.list(map, "examples")) {
                Map<String, Object> example = Json.map(rawExample);
                rule.examples.add(new String[]{Json.str(example, "es"), Json.str(example, "ar"), Json.str(example, "egy")});
            }
            model.grammar.add(rule);
        }
        model.validate();
        return model;
    }

    private static Item readItem(Map<String, Object> map, String levelId, String unitId) {
        Item item = new Item();
        item.id = Json.str(map, "id");
        item.es = Json.str(map, "es");
        item.ar = Json.str(map, "ar");
        item.egy = Json.str(map, "ar_egy");
        item.ipa = Json.str(map, "ipa");
        item.pos = Json.str(map, "pos");
        item.topic = Json.str(map, "topic");
        item.noteEgy = Json.str(map, "note_egy");
        item.exEs = Json.str(map, "example_es");
        item.exFusha = Json.str(map, "example_fusha");
        item.exEgy = Json.str(map, "example_egy");
        item.verification = Json.str(map, "verification");
        if (item.verification.isEmpty()) item.verification = "machine_checked";
        item.type = Json.str(map, "type");
        if (item.type.isEmpty()) item.type = "word";
        item.freq = Json.integer(map, "freq", 0);
        item.level = levelId;
        item.unit = unitId;
        return item;
    }

    private static Lesson readLesson(Map<String, Object> map) {
        Lesson lesson = new Lesson();
        lesson.id = Json.str(map, "id");
        lesson.title = Json.str(map, "title");
        lesson.titleEs = Json.str(map, "title_es");
        lesson.goalEgy = Json.str(map, "goal_egy");
        lesson.itemIds.addAll(Json.strings(map, "items"));
        for (Object rawExercise : Json.list(map, "exercises")) lesson.exercises.add(readExercise(Json.map(rawExercise)));
        return lesson;
    }

    private static Exercise readExercise(Map<String, Object> map) {
        Exercise exercise = new Exercise();
        exercise.id = Json.str(map, "id");
        exercise.type = Json.str(map, "type");
        exercise.itemId = Json.str(map, "item");
        exercise.audio = Json.str(map, "audio");
        exercise.promptEgy = Json.str(map, "prompt_egy");
        exercise.promptFusha = Json.str(map, "prompt_fusha");
        exercise.explainEgy = Json.str(map, "explain_egy");
        exercise.explainFusha = Json.str(map, "explain_fusha");
        exercise.contextEgy = Json.str(map, "context_egy");
        exercise.rule = Json.str(map, "rule");
        exercise.options.addAll(Json.strings(map, "options"));
        exercise.answers.addAll(Json.strings(map, "answers"));
        exercise.lines.addAll(Json.strings(map, "lines"));
        exercise.answer = Json.integer(map, "answer", -1);
        for (Object rawPair : Json.list(map, "pairs")) {
            if (!(rawPair instanceof List)) continue;
            List<?> pair = (List<?>) rawPair;
            if (pair.size() >= 2) exercise.pairs.add(new String[]{String.valueOf(pair.get(0)), String.valueOf(pair.get(1))});
        }
        if (exercise.type.isEmpty()) throw new IllegalArgumentException("exercise without type: " + exercise.id);
        return exercise;
    }

    /** Structural checks that must hold for every shipped asset. Throws on failure. */
    void validate() {
        if (levels.isEmpty()) throw new IllegalArgumentException("curriculum has no levels");
        for (Level level : levels) {
            if (level.units.isEmpty()) throw new IllegalArgumentException("level " + level.id + " has no units");
            for (Unit unit : level.units) {
                if (unit.lessons.isEmpty()) throw new IllegalArgumentException("unit " + unit.id + " has no lessons");
                if (unit.items.isEmpty()) {
                    // Dialogue-comprehension units carry only exercises, no vocabulary items.
                    boolean hasExercises = false;
                    for (Lesson lesson : unit.lessons) if (!lesson.exercises.isEmpty()) hasExercises = true;
                    if (!hasExercises) throw new IllegalArgumentException("unit " + unit.id + " has no items");
                }
                for (Lesson lesson : unit.lessons) {
                    if (lesson.exercises.isEmpty() && lesson.itemIds.isEmpty()) {
                        throw new IllegalArgumentException("lesson " + lesson.id + " has neither items nor exercises");
                    }
                    for (Exercise exercise : lesson.exercises) validateExercise(exercise, lesson.id);
                }
            }
        }
        for (Dialogue dialogue : dialogues) {
            if (dialogue.lines.isEmpty()) throw new IllegalArgumentException("dialogue " + dialogue.id + " has no lines");
            for (Exercise question : dialogue.questions) validateExercise(question, dialogue.id);
        }
    }

    private void validateExercise(Exercise exercise, String owner) {
        String where = owner + "/" + exercise.type + "/" + exercise.id;
        switch (exercise.type) {
            case "intro":
            case "speak":
                if (!itemsById.containsKey(exercise.itemId)) throw new IllegalArgumentException(where + ": unknown item");
                break;
            case "choose":
            case "listen":
            case "fill":
            case "comprehend":
            case "final":
                if (exercise.options.size() < 2) throw new IllegalArgumentException(where + ": needs options");
                if (exercise.answer < 0 || exercise.answer >= exercise.options.size()) throw new IllegalArgumentException(where + ": answer out of range");
                break;
            case "order":
                if (exercise.options.size() < 2) throw new IllegalArgumentException(where + ": needs word chips");
                if (exercise.answers.isEmpty()) throw new IllegalArgumentException(where + ": needs target sentence");
                break;
            case "translate_es_ar":
            case "translate_ar_es":
                if (exercise.answers.isEmpty()) throw new IllegalArgumentException(where + ": needs accepted answers");
                break;
            case "match":
                if (exercise.pairs.size() < 2) throw new IllegalArgumentException(where + ": needs pairs");
                break;
            default:
                throw new IllegalArgumentException(where + ": unknown exercise type");
        }
    }

    // ---- queries ----

    Level level(String id) {
        for (Level level : levels) if (level.id.equals(id)) return level;
        return null;
    }

    List<Item> allItems() {
        return new ArrayList<>(itemsById.values());
    }

    List<Item> levelItems(String levelId) {
        List<Item> out = new ArrayList<>();
        for (Item item : itemsById.values()) if (item.level.equals(levelId)) out.add(item);
        return out;
    }

    Item item(String id) {
        return itemsById.get(id);
    }

    Dialogue dialogue(String id) {
        return dialoguesById.get(id);
    }

    List<Dialogue> dialoguesInSection(String section) {
        List<Dialogue> out = new ArrayList<>();
        for (Dialogue dialogue : dialogues) if (dialogue.section.equals(section)) out.add(dialogue);
        return out;
    }

    Item findBySpanish(String spanish) {
        String needle = looseSpanishKey(spanish);
        if (needle.isEmpty()) return null;
        for (Item item : itemsById.values()) if (looseSpanishKey(item.es).equals(needle)) return item;
        return null;
    }

    /** Spanish key ignoring accents, case and trailing punctuation so scenario phrases match. */
    private static String looseSpanishKey(String value) {
        String key = TextNormalizers.spanishKey(value == null ? "" : value).trim();
        while (!key.isEmpty()) {
            char last = key.charAt(key.length() - 1);
            if (last == '.' || last == '!' || last == '?' || last == ',' || last == ';' || last == ':') {
                key = key.substring(0, key.length() - 1).trim();
            } else {
                break;
            }
        }
        return key;
    }

    /** Search over both languages, accents-insensitive. */
    List<Item> search(String query, String levelId, String topic, boolean favoritesOnly, Set<String> favorites, int limit) {
        String spanishNeedle = TextNormalizers.spanishKey(query);
        String arabicNeedle = TextNormalizers.arabicKey(query);
        List<Item> out = new ArrayList<>();
        for (Item item : itemsById.values()) {
            if (levelId != null && !levelId.isEmpty() && !item.level.equals(levelId)) continue;
            if (topic != null && !topic.isEmpty() && !item.topic.equals(topic)) continue;
            if (favoritesOnly && (favorites == null || !favorites.contains(item.id))) continue;
            if (!spanishNeedle.isEmpty() || !arabicNeedle.isEmpty()) {
                boolean hit = (!spanishNeedle.isEmpty() && TextNormalizers.spanishKey(item.es).contains(spanishNeedle))
                        || (!arabicNeedle.isEmpty() && (TextNormalizers.arabicKey(item.ar).contains(arabicNeedle)
                        || TextNormalizers.arabicKey(item.egy).contains(arabicNeedle)));
                if (!hit) continue;
            }
            out.add(item);
            if (out.size() >= limit) break;
        }
        return out;
    }

    Set<String> topics() {
        Set<String> topics = new LinkedHashSet<>();
        for (Item item : itemsById.values()) if (!item.topic.isEmpty()) topics.add(item.topic);
        return topics;
    }

    int totalItems() { return itemsById.size(); }

    int totalLessons() {
        int total = 0;
        for (Lesson lesson : lessonsById.values()) total++;
        return total;
    }

    int totalExercises() {
        int total = 0;
        for (Lesson lesson : lessonsById.values()) total += lesson.exercises.size();
        for (Dialogue dialogue : dialogues) total += dialogue.questions.size();
        return total;
    }

    List<Lesson> lessonsOf(String levelId) {
        List<Lesson> out = new ArrayList<>();
        for (Lesson lesson : lessonsById.values()) if (lesson.levelId.equals(levelId)) out.add(lesson);
        return out;
    }

    String summaryLine() {
        return totalItems() + " عنصر • " + totalLessons() + " درس • " + totalExercises() + " تمرين • " + dialogues.size() + " حوار";
    }

    String levelTitle(String id) {
        Level level = level(id);
        return level == null ? id : level.title;
    }

    static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
}
