package com.habla.espanol;

import java.util.List;
import java.util.Map;

/** Minimal parser edge cases. */
final class JsonTest {
    private JsonTest() {}

    static void run() {
        Map<String, Object> object = Json.parseObject("{\"a\":1,\"b\":[true,false,null],\"c\":\"x\\u00e9y\",\"d\":{\"e\":-2.5}}");
        TestSupport.equals(1, ((Number) object.get("a")).intValue(), "integer parse");
        List<?> list = (List<?>) object.get("b");
        TestSupport.equals(3, list.size(), "array size");
        TestSupport.equals("xéy", object.get("c"), "unicode escape");
        TestSupport.equals(-2.5, ((Number) Json.map(object.get("d")).get("e")).doubleValue(), "negative decimal");
        TestSupport.equals("", Json.str(object, "missing"), "missing key becomes empty string");
        boolean failed = false;
        try {
            Json.parseObject("{\"a\": }");
        } catch (IllegalArgumentException expected) {
            failed = true;
        }
        TestSupport.check(failed, "malformed json is rejected");
        TestSupport.equals("como estas", TextNormalizers.spanishKey("¿Cómo estás?"), "spanish key");
        TestSupport.equals("اهلا", TextNormalizers.arabicKey("أَهْلاً"), "arabic key");
        TestSupport.check(TextNormalizers.sameWordOrder("Yo soy de Egipto", "yo soy de egipto"), "word order ignores case");
        TestSupport.check(!TextNormalizers.sameWordOrder("Yo soy de Egipto", "yo soy Egipto de"), "word order is strict");
        TestSupport.equals("Hola mundo", TextNormalizers.join(List.of("Hola", "", "mundo")), "join skips empty tokens");
    }
}
