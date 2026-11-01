package com.habla.espanol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Validates the shipped asset exactly as the app parses it. */
final class ContentTest {
    private ContentTest() {}

    /** Raw JSON of the shipped asset, reused by tests that re-parse it. */
    static String rawJson = "";

    static Curriculum load(String path) throws Exception {
        String json = new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
        rawJson = json;
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
        java.util.List<String> meaningful = new java.util.ArrayList<>();
        for (String token : head) {
            if (GLUE.contains(token)) continue;
            meaningful.add(token);
        }
        if (!meaningful.isEmpty()) {
            boolean all = true;
            for (String token : meaningful) {
                if (!tokenMatches(token, example, item.pos)) { all = false; break; }
            }
            if (all) return true;
        } else if (!head.isEmpty() && containsSequence(example, head)) {
            // Phrases made only of glue words ("¿y tú?", "¿por qué?") must appear as a sequence.
            return true;
        }
        // Single-word fallback: the old, narrower rules remain so nothing gets weaker.
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
        }
        return false;
    }

    /** Words that may legitimately disappear (articles, prepositions, conjunctions, pronouns). */
    private static final java.util.Set<String> GLUE = new java.util.HashSet<>(java.util.Arrays.asList(
            "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "a", "al", "en", "con",
            "por", "para", "que", "y", "o", "mi", "tu", "su", "me", "te", "se", "nos", "le", "lo"));

    /** Inflected forms of irregular verbs that no stem rule can predict. */
    private static final java.util.Map<String, java.util.Set<String>> IRREGULAR = new java.util.HashMap<>();
    static {
        IRREGULAR.put("ser", forms("soy", "eres", "es", "somos", "son", "fui", "era", "sido", "sea"));
        IRREGULAR.put("ir", forms("voy", "vas", "va", "vamos", "van", "fui", "ido", "vaya"));
        IRREGULAR.put("estar", forms("estoy", "estas", "esta", "estamos", "estan", "estado"));
        IRREGULAR.put("tener", forms("tengo", "tienes", "tiene", "tenemos", "tienen", "tenido"));
        IRREGULAR.put("hacer", forms("hago", "haces", "hace", "hacemos", "hacen", "hecho"));
        IRREGULAR.put("decir", forms("digo", "dices", "dice", "decimos", "dicen", "dicho"));
        IRREGULAR.put("ver", forms("veo", "ves", "ve", "vemos", "ven", "visto"));
        IRREGULAR.put("dar", forms("doy", "das", "da", "damos", "dan", "dado"));
        IRREGULAR.put("saber", forms("se", "sabes", "sabe", "sabemos", "saben", "sabido"));
        IRREGULAR.put("oír", forms("oigo", "oyes", "oye", "oimos", "oyen", "oido"));
        IRREGULAR.put("oir", forms("oigo", "oyes", "oye", "oimos", "oyen", "oido"));
        IRREGULAR.put("llover", forms("llueve", "llovio", "llovido"));
        IRREGULAR.put("nevar", forms("nieva", "nevo"));
        IRREGULAR.put("leer", forms("leo", "lees", "lee", "leemos", "leen", "leido"));
        IRREGULAR.put("creer", forms("creo", "crees", "cree", "creemos", "creen"));
        IRREGULAR.put("dormir", forms("duermo", "duermes", "duerme", "dormimos", "duermen"));
        IRREGULAR.put("jugar", forms("juego", "juegas", "juega", "jugamos", "juegan"));
        IRREGULAR.put("preferir", forms("prefiero", "prefieres", "prefiere", "preferimos"));
        IRREGULAR.put("sentir", forms("siento", "sientes", "siente", "sentimos"));
        IRREGULAR.put("sentirse", forms("me siento", "te sientes", "se siente", "siento"));
        IRREGULAR.put("pensar", forms("pienso", "piensas", "piensa", "pensamos"));
        IRREGULAR.put("empezar", forms("empiezo", "empiezas", "empieza", "empezamos"));
        IRREGULAR.put("entender", forms("entiendo", "entiendes", "entiende"));
        IRREGULAR.put("pedir", forms("pido", "pides", "pide", "pedimos"));
        IRREGULAR.put("seguir", forms("sigo", "sigues", "sigue", "seguimos"));
        IRREGULAR.put("conseguir", forms("consigo", "consigues", "consigue"));
        IRREGULAR.put("volver", forms("vuelvo", "vuelves", "vuelve", "volvemos"));
        IRREGULAR.put("encontrar", forms("encuentro", "encuentras", "encuentra"));
        IRREGULAR.put("probar", forms("pruebo", "pruebas", "prueba", "probamos"));
        IRREGULAR.put("probarse", forms("me pruebo", "prueba", "pruebo"));
        IRREGULAR.put("cerrar", forms("cierro", "cierras", "cierra", "cerramos"));
        IRREGULAR.put("freír", forms("frio", "fries", "frie", "freimos", "freir"));
        IRREGULAR.put("hervir", forms("hiervo", "hierve", "hervimos"));
        IRREGULAR.put("medir", forms("mido", "mides", "mide", "medimos"));
        IRREGULAR.put("repetir", forms("repito", "repites", "repite"));
        IRREGULAR.put("servir", forms("sirvo", "sirves", "sirve", "servimos"));
        IRREGULAR.put("reír", forms("rio", "ries", "rie"));
        IRREGULAR.put("haber", forms("he", "has", "ha", "hemos", "han", "habido"));
        IRREGULAR.put("poder", forms("puedo", "puedes", "puede", "podemos", "pueden"));
        IRREGULAR.put("querer", forms("quiero", "quieres", "quiere", "queremos"));
        IRREGULAR.put("poner", forms("pongo", "pones", "pone", "ponemos"));
        IRREGULAR.put("proponer", forms("propongo", "propones", "propone", "proponemos"));
        IRREGULAR.put("suponer", forms("supongo", "supones", "supone", "suponemos"));
        IRREGULAR.put("concluir", forms("concluyo", "concluyes", "concluye", "concluimos"));
        IRREGULAR.put("contradecir", forms("contradigo", "contradices", "contradice", "contradigas"));
        IRREGULAR.put("salir", forms("salgo", "sales", "sale", "salimos"));
        IRREGULAR.put("venir", forms("vengo", "vienes", "viene", "venimos"));
        IRREGULAR.put("conocer", forms("conozco", "conoces", "conoce"));
        IRREGULAR.put("traer", forms("traigo", "traes", "trae", "traemos"));
        IRREGULAR.put("oír", forms("oigo", "oye", "oyes"));
        IRREGULAR.put("caer", forms("caigo", "cae", "caes"));
        IRREGULAR.put("huir", forms("huyo", "huye"));
    }

    private static final java.util.regex.Pattern ENDING =
            java.util.regex.Pattern.compile("o|as|a|amos|ais|an|e|i|aste|aron|aba|ia|ando|ado|ido|ar|er|ir|es|en|io|ye|yo|ieron|emos|imos|iste|eria|ira|ieron|jeron");

    private static java.util.Set<String> forms(String... values) {
        return new java.util.HashSet<>(java.util.Arrays.asList(values));
    }

    private static boolean containsSequence(java.util.List<String> haystack, java.util.List<String> needle) {
        for (int start = 0; start + needle.size() <= haystack.size(); start++) {
            boolean same = true;
            for (int offset = 0; offset < needle.size(); offset++) {
                if (!haystack.get(start + offset).equals(needle.get(offset))) { same = false; break; }
            }
            if (same) return true;
        }
        return false;
    }

    /** Stem-changing variants a regular suffix rule cannot predict (e→ie, e→i, o→ue, u→ue). */
    private static java.util.List<String> stemVariants(String stem) {
        java.util.List<String> variants = new java.util.ArrayList<>();
        variants.add(stem);
        int e = stem.lastIndexOf('e');
        if (e >= 1) {
            variants.add(stem.substring(0, e) + "ie" + stem.substring(e + 1));
            variants.add(stem.substring(0, e) + "i" + stem.substring(e + 1));
        }
        int o = stem.lastIndexOf('o');
        if (o >= 1) variants.add(stem.substring(0, o) + "ue" + stem.substring(o + 1));
        if (stem.endsWith("g")) variants.add(stem.substring(0, stem.length() - 1) + "j");
        int u = stem.lastIndexOf('u');
        if (u >= 1) variants.add(stem.substring(0, u) + "ue" + stem.substring(u + 1));
        return variants;
    }

    private static boolean tokenMatches(String token, java.util.List<String> example, String pos) {
        for (String got : example) {
            if (got.equals(token)) return true;
            if (plural(got).equals(token) || plural(token).equals(got)) return true;
        }
        String base = token;
        // Reflexive infinitive: levantarse -> levantar, irme -> ir (never strip "te" from "tener").
        for (String suffix : new String[]{"se", "me", "te", "nos", "os"}) {
            if (base.endsWith(suffix) && base.length() >= suffix.length() + 2) {
                String candidate = base.substring(0, base.length() - suffix.length());
                if (candidate.endsWith("ar") || candidate.endsWith("er") || candidate.endsWith("ir")) {
                    base = candidate;
                    break;
                }
            }
        }
        boolean verbLike = (pos != null && pos.equals("verb"))
                || base.endsWith("ar") || base.endsWith("er") || base.endsWith("ir");
        if (verbLike && (base.length() >= 4 || IRREGULAR.containsKey(base))) {
            java.util.Set<String> known = IRREGULAR.get(base);
            if (known != null) {
                for (String got : example) if (known.contains(got)) return true;
                for (String form : known) {
                    for (String word : TextNormalizers.words(form)) if (example.contains(word)) return true;
                }
            }
            String stem = base.substring(0, base.length() - 2);
            for (String candidate : stemVariants(stem)) {
                if (candidate.length() < 2) continue;
                for (String got : example) {
                    if (got.startsWith(candidate) && ENDING.matcher(got.substring(candidate.length())).matches()) return true;
                }
            }
        }
        if ("adj".equals(pos) && token.length() > 3 && (token.endsWith("o") || token.endsWith("a"))) {
            String stem = token.substring(0, token.length() - 1);
            for (String got : example) if (got.equals(stem + "o") || got.equals(stem + "a")) return true;
        }
        return false;
    }

    static String plural(String word) {
        if (word.isEmpty()) return word;
        char last = word.charAt(word.length() - 1);
        if ("aeiou".indexOf(last) >= 0) return word + "s";
        if (last == 'z') return word.substring(0, word.length() - 1) + "ces";
        return word + "es";
    }
}
