package com.habla.espanol;

/**
 * The mistake tutor: names the error, explains it in Egyptian Arabic, shows the
 * correct answer, adds one extra example and links the item to its grammar rule.
 */
final class Tutor {
    private Tutor() {}

    static final class Feedback {
        String headline = "";
        String explanationEgy = "";
        String explanationFusha = "";
        String correctAnswer = "";
        String exampleEs = "";
        String exampleEgy = "";
        String ruleTitle = "";
        String ruleEgy = "";
        boolean correct = false;

        String renderEgy() {
            StringBuilder out = new StringBuilder();
            if (!headline.isEmpty()) out.append(headline).append('\n');
            if (!explanationEgy.isEmpty()) out.append(explanationEgy).append('\n');
            if (!exampleEs.isEmpty()) {
                out.append("مثال تاني: ").append(exampleEs);
                if (!exampleEgy.isEmpty()) out.append("  —  ").append(exampleEgy);
                out.append('\n');
            }
            if (!ruleTitle.isEmpty()) out.append("القاعدة: ").append(ruleTitle).append(" — ").append(ruleEgy);
            return out.toString().trim();
        }
    }

    static Feedback correct(Curriculum model, Curriculum.Exercise exercise, Curriculum.Item item) {
        Feedback feedback = new Feedback();
        feedback.correct = true;
        feedback.headline = "صح كده! ✓";
        feedback.explanationEgy = exercise.explainEgy.isEmpty() ? describeItem(item) : exercise.explainEgy;
        feedback.explanationFusha = exercise.explainFusha;
        attachExample(feedback, item);
        attachRule(model, exercise, feedback);
        return feedback;
    }

    static Feedback wrong(Curriculum model, Curriculum.Exercise exercise, Curriculum.Item item, AnswerChecker.Verdict verdict) {
        Feedback feedback = new Feedback();
        feedback.correct = false;
        feedback.correctAnswer = verdict.expected;
        feedback.headline = "الإجابة الصح: " + (verdict.expected.isEmpty() ? "—" : verdict.expected);
        StringBuilder out = new StringBuilder();
        if (!verdict.diagnosis.isEmpty()) out.append(verdict.diagnosis);
        String explain = exercise.explainEgy.isEmpty() ? describeItem(item) : exercise.explainEgy;
        if (!explain.isEmpty()) {
            if (out.length() > 0) out.append('\n');
            out.append(explain);
        }
        out.append("\nهيرجع العنصر ده في المراجعة النهاردة عشان يثبت.");
        feedback.explanationEgy = out.toString();
        feedback.explanationFusha = exercise.explainFusha;
        attachExample(feedback, item);
        attachRule(model, exercise, feedback);
        return feedback;
    }

    private static void attachExample(Feedback feedback, Curriculum.Item item) {
        if (item == null || !item.hasExample()) return;
        feedback.exampleEs = item.exEs;
        feedback.exampleEgy = item.exEgy;
    }

    private static void attachRule(Curriculum model, Curriculum.Exercise exercise, Feedback feedback) {
        if (model == null || exercise.rule.isEmpty()) return;
        for (Curriculum.GrammarRule rule : model.grammar) {
            if (rule.id.equals(exercise.rule)) {
                feedback.ruleTitle = rule.titleAr;
                feedback.ruleEgy = rule.ruleEgy;
                return;
            }
        }
    }

    private static String describeItem(Curriculum.Item item) {
        if (item == null) return "";
        StringBuilder out = new StringBuilder();
        if (!item.es.isEmpty()) out.append(item.es).append(" = ").append(item.ar);
        if (item.hasEgyptian()) out.append(" (بالمصري: ").append(item.egy).append(")");
        if (!item.ipa.isEmpty()) out.append("\nالنطق: [").append(item.ipa).append(']');
        if (!item.noteEgy.isEmpty()) out.append('\n').append(item.noteEgy);
        return out.toString();
    }

    /** Short, per-mode tip shown above the check button. */
    static String hint(Curriculum.Exercise exercise) {
        switch (exercise.type) {
            case "choose": return "اقرأ المعنيين وركّز في الكلمة اللي قدامك.";
            case "listen": return "اسمعلها مرة عادية ومرة بطيئة قبل ما تختار.";
            case "order": return "في الإسباني الفعل بيتصرّف حسب الشخص، والترتيب هو المعنى.";
            case "fill": return "لو مش متأكد، شوف الجملة المثال اللي فاتت.";
            case "translate_es_ar": return "اكتب المعنى بالعربي، بالفصحى أو بالمصري — الاتنين مقبولين.";
            case "translate_ar_es": return "اكتب بالإسباني. الأكسنت مش شرط للتصحيح، بس حاول تكتبه.";
            case "match": return "وصّل كل كلمة إسبانية بمعناها.";
            case "comprehend": return "اقرأ الحوار كويس قبل ما تجاوب.";
            case "speak": return "اتكلم بوضوح. التقييم تقريبي ومش تحليل صوتي احترافي.";
            default: return "";
        }
    }
}
