package com.habla.espanol;

/** Entry point: `java com.habla.espanol.TestRunner <path-to-content.json>`. */
public final class TestRunner {
    public static void main(String[] args) throws Exception {
        String contentPath = args.length > 0 ? args[0] : "app/src/main/assets/content.json";
        Curriculum model = ContentTest.load(contentPath);
        System.out.println("Spanish 1000 — JVM test run");
        JsonTest.run();
        ContentTest.run(model);
        PlannerTest.run(model);
        GradingTest.run(model);
        ScenarioTest.run(contentPath);
        SchedulerTest.run();
        Curriculum parsedAgain = Curriculum.parse(new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(contentPath)), java.nio.charset.StandardCharsets.UTF_8));
        TestSupport.check(parsedAgain.totalItems() == model.totalItems(), "deterministic reload");
        System.out.println();
        if (TestSupport.failures.isEmpty()) {
            System.out.println("PASS — " + TestSupport.checks + " checks");
        } else {
            System.out.println("FAIL — " + TestSupport.failures.size() + " of " + TestSupport.checks + " checks failed:");
            int shown = 0;
            for (String failure : TestSupport.failures) {
                System.out.println("  - " + failure);
                if (++shown >= 40) break;
            }
            System.exit(1);
        }
    }
}
