package com.habla.espanol;

import java.util.ArrayList;
import java.util.List;

/**
 * Every role-play phrase shipped in {@link RealLife} must resolve to a real curriculum item,
 * otherwise the scenario screen silently degrades to "not in the curriculum yet".
 */
final class ScenarioTest {
    private ScenarioTest() {}

    static void run(String contentPath) throws Exception {
        Curriculum model = ContentTest.load(contentPath);
        int phrases = 0;
        List<String> missing = new ArrayList<>();
        for (String[] scenario : RealLife.SCENARIOS) {
            TestSupport.check(scenario.length >= 4, scenario[0] + " has phrases");
            TestSupport.check((scenario.length - 2) % 2 == 0, scenario[0] + " phrase/prompt pairs");
            for (int i = 2; i + 1 < scenario.length; i += 2) {
                phrases++;
                if (model.findBySpanish(scenario[i]) == null) missing.add(scenario[i]);
            }
        }
        TestSupport.check(phrases >= 40, "scenarios carry many phrases (" + phrases + ")");
        for (String phrase : missing) {
            TestSupport.check(false, "scenario phrase missing from curriculum: " + phrase);
        }
        System.out.println("  scenarios: " + RealLife.SCENARIOS.length + " موقف / " + phrases + " عبارة قابلة للتدرّب");
    }
}
