package com.habla.espanol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Validates the shipped asset exactly as the app parses it. */
final class ContentTest {
    private ContentTest() {}

    static Curriculum load(String path) throws Exception {
        String json = new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
        return Curriculum.parse(json);
    }

    static void run(Curriculum model) {
        TestSupport.check(!model.levels.isEmpty(), "curriculum has levels");
        Set<String> expectedLevels = Set.of("A0", "A1", "A2", "B1", "B2", "C1");
        for (String level : expectedLevels) {
            TestSupport.check(model.level(level) != null, "level " + level + " exists");
        }
        int items = 0;
        Set<String> spanish = new HashSet<>();
        for (Curriculum.Level level : model.levels) {
            TestSupport.check(!level.units.isEmpty(), level.id + " has units");
            for (Curriculum.Unit unit : level.units) {
                TestSupport.check(!unit.lessons.isEmpty(), unit.id + " has lessons");
                for (Curriculum.Lesson lesson : unit.lessons) {
                    boolean authored = !lesson.exercises.isEmpty();
                    TestSupport.check(authored || !lesson.itemIds.isEmpty(),
                            lesson.id + " has items or authored exercises");
                    if (!authored) {
                        TestSupport.check(!LessonPlanner.plan(model, lesson).isEmpty(), lesson.id + " planner output");
                    }
                }
                for (Curriculum.Item item : unit.items) {
                    items++;
                    TestSupport.notEmpty(item.es, item.id + " spanish");
                    TestSupport.notEmpty(item.ar, item.id + " fusha");
                    TestSupport.notEmpty(item.egy, item.id + " egyptian");
                    TestSupport.notEmpty(item.ipa, item.id + " ipa");
                    TestSupport.check(item.ar.chars().anyMatch(c -> c >= 0x0600 && c <= 0x06FF),
                            item.id + " fusha is Arabic");
                    TestSupport.check(item.es.chars().filter(c -> c == '¿').count() == item.es.chars().filter(c -> c == '?').count(),
                            item.id + " balanced question marks");
                    if (item.es.contains("¡")) {
                        TestSupport.check(item.es.chars().filter(c -> c == '¡').count() == item.es.chars().filter(c -> c == '!').count(),
                                item.id + " balanced exclamation marks");
                    }
                    if (item.type.equals("word")) {
                        TestSupport.check(spanish.add(item.level + "|" + TextNormalizers.spanishKey(item.es)),
                                item.id + " unique spanish within level: " + item.es);
                    }
                    if (item.hasExample()) {
                        java.util.List<String> example = TextNormalizers.words(TextNormalizers.spanishKey(item.exEs));
                        TestSupport.check(exampleUsesWord(item, example),
                                item.id + " example uses the word: " + item.es + " / " + item.exEs);
                    }
                }
            }
        }
        TestSupport.check(items == model.totalItems(), "item count matches walk");
        TestSupport.check(items >= 900, "curriculum has a substantial inventory (" + items + ")");
        for (Curriculum.Dialogue dialogue : model.dialogues) {
            TestSupport.check(dialogue.lines.size() >= 4, dialogue.id + " has 4+ lines");
            TestSupport.check(dialogue.questions.size() >= 2, dialogue.id + " has 2+ questions");
            for (Curriculum.Exercise question : dialogue.questions) {
                TestSupport.check(question.answer >= 0 && question.answer < question.options.size(),
                        dialogue.id + " answer index valid");
            }
            for (Curriculum.Line line : dialogue.lines) {
                TestSupport.notEmpty(line.es, dialogue.id + " line spanish");
                TestSupport.notEmpty(line.egy, dialogue.id + " line egyptian");
            }
        }
        TestSupport.check(!model.dialoguesInSection("call_center").isEmpty(), "call centre dialogues exist");
        TestSupport.check(!model.grammar.isEmpty(), "grammar rules exist");
        System.out.println("  content: " + model.summaryLine());
    }

    /** The example must use the headword: literally, or through a defensible inflection. */
    static boolean exampleUsesWord(Curriculum.Item item, java.util.List<String> example) {
        java.util.List<String> head = TextNormalizers.words(TextNormalizers.spanishKey(item.es));
        // articles and the de+el contraction may legitimately disappear in the example sentence
        java.util.List<String> meaningful = new java.util.ArrayList<>();
        for (String token : head) {
            if (token.equals("el") || token.equals("la") || token.equals("los") || token.equals("las")
                    || token.equals("un") || token.equals("una") || token.equals("de") || token.equals("del")) continue;
            meaningful.add(token);
        }
        boolean literal = !meaningful.isEmpty();
        for (String token : meaningful) {
            if (!example.contains(token)) { literal = false; break; }
        }
        if (literal) return true;
        String core = TextNormalizers.spanishKey(item.es).replaceAll("\\((.*?)\\)", "").trim();
        for (String article : new String[]{"el ", "la ", "los ", "las ", "un ", "una "}) {
            if (core.startsWith(article)) { core = core.substring(article.length()); break; }
        }
        if (core.contains(" ")) return false;
        for (String token : example) {
            if (core.equals(token)) return true;
            if (plural(token).equals(core) || plural(core).equals(token)) return true;
            if (item.pos.equals("adj") && core.length() > 3
                    && (core.endsWith("o") || core.endsWith("a"))) {
                String stem = core.substring(0, core.length() - 1);
                if (token.equals(stem + "o") || token.equals(stem + "a")) return true;
            }
            if (item.pos.equals("verb") && core.length() > 5
                    && (core.endsWith("ar") || core.endsWith("er") || core.endsWith("ir"))) {
                String stem = core.substring(0, core.length() - 2);
                if (token.startsWith(stem)) {
                    String ending = token.substring(stem.length());
                    if (ENDING.matcher(ending).matches()) return true;
                }
            }
        }
        return false;
    }

    private static final java.util.regex.Pattern ENDING =
            java.util.regex.Pattern.compile("o|as|a|amos|áis|an|é|aste|ó|aron|aba|ía|ando|ado|ido|ar|er|ir|e|es|en|ió|ieron");

    static String plural(String word) {
        if (word.isEmpty()) return word;
        char last = word.charAt(word.length() - 1);
        if ("aeiou".indexOf(last) >= 0) return word + "s";
        if (last == 'z') return word.substring(0, word.length() - 1) + "ces";
        return word + "es";
    }
}
