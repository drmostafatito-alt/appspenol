package com.habla.espanol;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Spanish 1000 — single-activity Arabic (Egyptian) course app.
 *
 * Screens: welcome, home, level map, unit/lesson path, lesson session, lesson
 * result, daily review, flashcards, dictionary, word details, statistics,
 * achievements, call-centre practice, real-life scenarios, grammar, settings.
 *
 * The curriculum itself lives in assets/content.json (see tools/build_content.py);
 * grading, spaced repetition, XP rules and the mistake tutor are pure Java classes
 * that are unit-tested on the JVM (tools/java_tests).
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int INK = 0xff263238, MUTED = 0xff687a76, GREEN = 0xff218746, DARK_GREEN = 0xff176b39,
            BLUE = 0xff386a80, SKY = 0xffe8f3f4, PALE = 0xffe8f8eb, LINE = 0xffd9e6db, RED = 0xffaa3644,
            RED_BG = 0xffffefef, BG = 0xfff7f9f5, GOLD = 0xffffc857, CORAL = 0xffff9558, LAVENDER = 0xfff3f4e8;
    private static final int MIC_PERMISSION = 17, NOTIFY_PERMISSION = 18, EXPORT_REQUEST = 101, IMPORT_REQUEST = 102;
    private static final int MAX_BACKUP_BYTES = 4 * 1024 * 1024;

    private final ArrayList<Track> tracks = new ArrayList<>();
    private final ArrayList<Session> queue = new ArrayList<>();
    private final Set<String> mistakes = new HashSet<>(), skippedItems = new HashSet<>(), srsGraded = new HashSet<>();
    private final LinkedHashMap<String, Boolean> firstOutcome = new LinkedHashMap<>();
    private final ArrayList<String> selectedWords = new ArrayList<>();
    private final ArrayList<Button> selectedChips = new ArrayList<>();
    private final ArrayList<Item> sessionItems = new ArrayList<>();
    private final Locale spanish = new Locale("es", "ES");

    private Curriculum curriculum;
    private SharedPreferences prefs;
    private PrefStore store;
    private Typeface cairoRegular, cairoBold, nunitoRegular, nunitoBold;
    private TextToSpeech tts;
    /** Empty when the Spanish voice works; otherwise a clear Egyptian-Arabic reason shown in the UI. */
    private String ttsProblem = "";
    private SpeechRecognizer recognizer;
    private LinearLayout root, body, answers, footer;
    private WordFlow bank, selectedBank;
    private MatchCards matchCards;
    private ScrollView scroll;
    private TextView feedback;
    private EditText input;
    private Button action;
    private String screen = "home";
    private int trackIndex = 0, cursor = 0, correct = 0, attempted = 0, firstCorrect = 0, firstAttempted = 0,
            speechSkipped = 0, listeningSkipped = 0, ttsFailures = 0;
    private boolean ttsReady = false, answered = false, pendingSpeech = false, reviewMode = false;
    private Integer selectedChoice = null;
    private String selectedWord = null;
    private long lessonStarted = 0;
    /** Durable snapshot of the running lesson so a restart can resume it. */
    private LessonSession savedSession;
    private Lesson activeLesson;
    private Curriculum.Dialogue activeDialogue;
    private String pendingExport = null;
    private int dicLimit = 120;
    private String dicLevel = "", dicQuery = "";
    private boolean dicFavorites = false;
    private EditText dicSearch;

    // ---------- model ----------

    private static final class Item {
        String id = "", es = "", ar = "", egy = "", ipa = "", pos = "", topic = "", noteEgy = "", verification = "";
        String exEs = "", exFusha = "", exEgy = "", level = "", unitTitle = "";
        String source = "", sourceStatus = "", exampleSource = "";
        int freq = 0;
        int freqRank = 0;
        boolean hasEgy() { return !egy.isEmpty(); }
        boolean hasExample() { return !exEs.isEmpty(); }
        String egyOrFusha() { return hasEgy() ? egy : ar; }
    }

    private static final class Lesson {
        String id = "", title = "", titleEs = "", goalEgy = "", dialogueId = "";
        Curriculum.Lesson ref;
        ArrayList<Item> items = new ArrayList<>();
    }

    private static final class Unit {
        String id = "", title = "", titleEs = "", goalEgy = "", kind = "";
        ArrayList<Lesson> lessons = new ArrayList<>();
        ArrayList<Item> items = new ArrayList<>();
    }

    private static final class Track {
        String id = "", title = "", titleEs = "", goalEgy = "", descEgy = "";
        ArrayList<Unit> units = new ArrayList<>();
        ArrayList<Lesson> lessons = new ArrayList<>();
        ArrayList<Item> items = new ArrayList<>();
    }

    private static final class Session {
        Curriculum.Exercise ex;
        Curriculum.Item item;
        boolean retry = false;
        String dialogueId = "";
        Session(Curriculum.Exercise ex, Curriculum.Item item, boolean retry, String dialogueId) {
            this.ex = ex; this.item = item; this.retry = retry; this.dialogueId = dialogueId;
        }
    }

    /** Adapter so the pure-Java review scheduler can read/write SharedPreferences. */
    private final class PrefStore implements ReviewScheduler.Store {
        public int getInt(String key, int fallback) { return prefs.getInt(key, fallback); }
        public void putInt(String key, int value) { prefs.edit().putInt(key, value).apply(); }
        public long getLong(String key, long fallback) { return prefs.getLong(key, fallback); }
        public void putLong(String key, long value) { prefs.edit().putLong(key, value).apply(); }
        public float getFloat(String key, float fallback) { return prefs.getFloat(key, fallback); }
        public void putFloat(String key, float value) { prefs.edit().putFloat(key, value).apply(); }
        public String getString(String key, String fallback) { return prefs.getString(key, fallback); }
        public void putString(String key, String value) { prefs.edit().putString(key, value).apply(); }
    }

    // ---------- lifecycle ----------

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::navigateBack);
        }
        prefs = getSharedPreferences("habla_progress", MODE_PRIVATE);
        store = new PrefStore();
        cairoRegular = Typeface.createFromAsset(getAssets(), "fonts/cairo-regular.ttf");
        cairoBold = Typeface.createFromAsset(getAssets(), "fonts/cairo-bold.ttf");
        nunitoRegular = Typeface.createFromAsset(getAssets(), "fonts/nunito-regular.ttf");
        nunitoBold = Typeface.createFromAsset(getAssets(), "fonts/nunito-bold.ttf");
        try {
            loadContent();
        } catch (Exception e) {
            throw new IllegalStateException("تعذر تحميل المحتوى: " + e.getMessage(), e);
        }
        trackIndex = prefs.getInt("last_track", 0);
        if (trackIndex < 0 || trackIndex >= tracks.size()) trackIndex = 0;
        if (prefs.getBoolean("reminders_enabled", false)) ReminderReceiver.scheduleNext(this);
        tts = new TextToSpeech(this, this);
        if (!prefs.getBoolean("onboarding_done", false) && prefs.getInt("xp", 0) == 0) showWelcome(); else home();
    }

    private String readAsset(String path) throws Exception {
        InputStream in = getAssets().open(path);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) bytes.write(buffer, 0, read);
        in.close();
        return bytes.toString("UTF-8");
    }

    private void loadContent() throws Exception {
        curriculum = Curriculum.parse(readAsset("content.json"));
        tracks.clear();
        for (Curriculum.Level level : curriculum.levels) {
            Track track = new Track();
            track.id = level.id;
            track.title = level.title;
            track.titleEs = level.titleEs;
            track.goalEgy = level.goalEgy;
            track.descEgy = level.descEgy;
            for (Curriculum.Unit unit : level.units) {
                Unit local = new Unit();
                local.id = unit.id;
                local.title = unit.title;
                local.titleEs = unit.titleEs;
                local.goalEgy = unit.goalEgy;
                for (Curriculum.Item source : unit.items) {
                    Item item = copyItem(source, unit.title);
                    local.items.add(item);
                    track.items.add(item);
                }
                for (Curriculum.Lesson lessonSource : unit.lessons) {
                    Lesson lesson = new Lesson();
                    lesson.id = lessonSource.id;
                    lesson.title = lessonSource.title;
                    lesson.titleEs = lessonSource.titleEs;
                    lesson.goalEgy = lessonSource.goalEgy;
                    lesson.ref = lessonSource;
                    if ("dialogue".equals(unit.kind)) lesson.dialogueId = lessonSource.id.replace("_lesson", "");
                    for (String itemId : lessonSource.itemIds) {
                        Item item = findItemBySource(itemId);
                        if (item != null) lesson.items.add(item);
                    }
                    if (!lessonSource.itemIds.isEmpty() && lesson.items.isEmpty()) continue;
                    local.lessons.add(lesson);
                    track.lessons.add(lesson);
                }
                track.units.add(local);
            }
            tracks.add(track);
        }
    }

    private Item copyItem(Curriculum.Item source, String unitTitle) {
        Item item = new Item();
        item.id = source.id;
        item.es = source.es;
        item.ar = source.ar;
        item.egy = source.egy;
        item.ipa = source.ipa;
        item.pos = source.pos;
        item.topic = source.topic;
        item.noteEgy = source.noteEgy;
        item.exEs = source.exEs;
        item.exFusha = source.exFusha;
        item.exEgy = source.exEgy;
        item.verification = source.verification;
        item.source = source.source;
        item.sourceStatus = source.sourceStatus;
        item.exampleSource = source.exampleSource;
        item.freqRank = source.freqRank;
        item.level = source.level;
        item.unitTitle = unitTitle;
        item.freq = source.freq;
        return item;
    }

    private Track trackFor(String id) {
        for (Track track : tracks) if (track.id.equals(id)) return track;
        return null;
    }

    private Unit unitFor(Track track, String id) {
        for (Unit unit : track.units) if (unit.id.equals(id)) return unit;
        return null;
    }

    private Item findItemBySource(String id) {
        for (Track track : tracks) for (Item item : track.items) if (item.id.equals(id)) return item;
        return null;
    }

    private Item findItemBySpanish(String spanishWord) {
        Curriculum.Item source = curriculum.findBySpanish(spanishWord);
        return source == null ? null : findItemBySource(source.id);
    }

    private Track currentTrack() {
        if (trackIndex < 0 || trackIndex >= tracks.size()) trackIndex = 0;
        return tracks.get(trackIndex);
    }

    // ---------- audio ----------

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            int available = tts.isLanguageAvailable(spanish);
            if (available == TextToSpeech.LANG_MISSING_DATA) {
                ttsProblem = "محرك الصوت موجود لكن حزمة اللغة الإسبانية غير منزّلة. نزّلها من إعدادات تحويل النص إلى كلام.";
            } else if (available == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsProblem = "محرك الصوت على الجهاز لا يدعم الإسبانية. جرّب محركًا آخر من إعدادات النظام.";
            } else if (available >= TextToSpeech.LANG_AVAILABLE
                    && tts.setLanguage(spanish) >= TextToSpeech.LANG_AVAILABLE) {
                ttsReady = true;
                ttsProblem = "";
                applyRate(false);
            } else {
                ttsProblem = "تعذر تفعيل الصوت الإسباني على هذا الجهاز.";
            }
        } else if (status != TextToSpeech.SUCCESS) {
            ttsProblem = "محرك تحويل النص إلى كلام غير متاح على الجهاز.";
        }
        if (!ttsReady) ttsFailures++;
    }

    private void applyRate(boolean slow) {
        if (tts == null) return;
        float saved = prefs.getFloat("tts_rate", 0.92f);
        tts.setSpeechRate(slow ? Math.max(0.45f, saved * 0.7f) : saved);
        tts.setPitch(1.03f);
    }

    /** Speaks Spanish text. Only ever uses the device TextToSpeech engine (never claims human audio). */
    private void speak(String value, boolean slow) {
        if (value == null || value.trim().isEmpty()) return;
        if (!ttsReady) {
            toast(ttsProblem.isEmpty()
                    ? "الصوت الإسباني غير متاح على الجهاز. نزّل حزمة الصوت الإسباني من إعدادات تحويل النص إلى كلام، ثم أعد فتح التطبيق."
                    : ttsProblem);
            return;
        }
        String phrase = cleanForSpeech(value);
        tts.stop();
        applyRate(slow);
        if (tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "habla") != TextToSpeech.SUCCESS) {
            toast("تعذر تشغيل الصوت.");
        }
    }

    private String cleanForSpeech(String value) {
        return value.replaceAll("\\([^)]*\\)", "")
                .replace("…", "").replace("...", "")
                .replace("/", " ").replace("____", " ")
                .trim();
    }

    private void stopSpeaking() {
        if (tts != null) tts.stop();
    }

    /** Where the example sentence comes from — never presented as hand-verified content. */
    private String exampleSourceLabel(String kind) {
        switch (kind) {
            case "curriculum": return "جملة من المنهج نفسه";
            case "pattern": return "مولّد بقالب جملة آمن (يحتاج مراجعة بشرية)";
            case "phrase_self": return "التعبير نفسه";
            default: return kind;
        }
    }

    /** Honest provenance line: attested spelling/frequency vs. no external source at all. */
    private String sourceLabel(Item item) {
        if ("spelling_attested".equals(item.sourceStatus)) {
            String rank = item.freqRank > 0 ? " (رتبة تكرار " + item.freqRank + ")" : "";
            return "المصدر: قائمة تكرار OpenSubtitles 2018" + rank
                    + " — تثبت التهجئة والتكرار فقط، لا المعنى. حالة العنصر: فحص آلي.";
        }
        return "المصدر: لا يوجد إسناد خارجي لهذا العنصر. حالة العنصر: فحص آلي، وليس verified.";
    }

    private String audioStatus() {
        if (ttsReady) return "الصوت جاهز — محرك الجهاز بصوت es-ES (إسباني أوروبي).";
        if (!ttsProblem.isEmpty()) return "الصوت غير متاح — " + ttsProblem;
        return "الصوت غير متاح: الجهاز لا يحتوي محرك TTS إسباني. التمارين الكتابية والقراءة تعمل عادي.";
    }

    private void audioControls(LinearLayout box, String value) {
        LinearLayout row = new LinearLayout(this);
        Button normal = button("🔊 استمع", SKY, BLUE);
        Button slow = button("🐢 ببطء", Color.WHITE, BLUE);
        Button stop = button("⏹ إيقاف", Color.WHITE, MUTED);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1);
        first.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1);
        second.setMargins(0, 0, dp(6), 0);
        row.addView(normal, first);
        row.addView(slow, second);
        row.addView(stop, new LinearLayout.LayoutParams(0, -2, 1));
        normal.setOnClickListener(v -> speak(value, false));
        slow.setOnClickListener(v -> speak(value, true));
        stop.setOnClickListener(v -> stopSpeaking());
        normal.setContentDescription("تشغيل النطق الإسباني");
        slow.setContentDescription("تشغيل النطق ببطء");
        stop.setContentDescription("إيقاف الصوت");
        full(box, row);
    }

    private void requestSpeech(final String expected) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("خدمة التعرف على الكلام غير متاحة على الجهاز. يمكنك تخطي تمرين التحدث.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingSpeech = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION);
            return;
        }
        listen(expected);
    }

    private void listen(final String expected) {
        stopRecognizer();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) { hint("اتكلم دلوقتي بالإسباني..."); }
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float rms) {}
            public void onBufferReceived(byte[] buffer) {}
            public void onEndOfSpeech() { hint("جاري فهم كلامك..."); }
            public void onError(int error) {
                if (!screen.equals("exercise") || answered) return;
                hint("الصوت مش واضح أو الخدمة غير متاحة. جرّب تاني أو تخطَّ التمرين.");
            }
            public void onResults(Bundle results) {
                if (!screen.equals("exercise") || answered) return;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches == null || matches.isEmpty()) { hint("ما سمعتش الكلام بوضوح، حاول تاني."); return; }
                boolean ok = false;
                for (String candidate : matches) if (AnswerChecker.speechMatches(expected, candidate)) { ok = true; break; }
                gradeCurrent(ok, "سمعت: " + matches.get(0));
            }
            public void onPartialResults(Bundle partial) {}
            public void onEvent(int eventType, Bundle params) {}
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        recognizer.startListening(intent);
    }

    private void stopRecognizer() {
        if (recognizer != null) {
            try { recognizer.cancel(); recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == MIC_PERMISSION && pendingSpeech) {
            pendingSpeech = false;
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED && screen.equals("exercise") && !answered) {
                Session session = currentSession();
                if (session != null) listen(session.ex.audio.isEmpty() ? session.ex.answers.isEmpty() ? "" : session.ex.answers.get(0) : session.ex.audio);
            } else {
                toast("إذن الميكروفون مطلوب للتحدث؛ يمكنك التخطي.");
            }
        }
        if (requestCode == NOTIFY_PERMISSION) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
                ReminderReceiver.scheduleNext(this);
                toast("تم تفعيل التذكير اليومي.");
            } else {
                toast("إذن الإشعارات مطلوب ليصلك التذكير.");
            }
            showSettings();
        }
    }

    // ---------- view helpers ----------

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean dark() {
        return prefs != null && prefs.getBoolean("dark_mode", false);
    }

    private int surface(int color) {
        if (!dark()) return color;
        if (color == Color.WHITE) return 0xff25332d;
        if (color == BG) return 0xff121d18;
        if (color == PALE) return 0xff203e2e;
        if (color == SKY) return 0xff263750;
        if (color == LAVENDER) return 0xff333247;
        if (color == RED_BG) return 0xff482b30;
        if (color == LINE) return 0xff42554a;
        return color;
    }

    private int foreground(int color) {
        if (!dark()) return color;
        if (color == INK) return 0xffeff5ef;
        if (color == MUTED) return 0xffb5c6b9;
        if (color == DARK_GREEN || color == GREEN) return 0xff8ee3a1;
        if (color == BLUE) return 0xffa9c7ff;
        if (color == RED) return 0xffffa8ac;
        return color;
    }

    private GradientDrawable bg(int color, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(surface(color));
        shape.setCornerRadius(dp(radius));
        if (stroke != 0) shape.setStroke(dp(2), surface(stroke));
        return shape;
    }

    private boolean isArabicText(String value) {
        return value == null || value.isEmpty() || value.matches("(?s).*[\\u0600-\\u06ff\\u0750-\\u077f].*");
    }

    private Typeface fontFor(String value, boolean bold) {
        return isArabicText(value) ? (bold ? cairoBold : cairoRegular) : (bold ? nunitoBold : nunitoRegular);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(foreground(color));
        view.setTypeface(fontFor(value, bold));
        view.setLineSpacing(dp(4), 1f);
        return view;
    }

    private TextView ltr(String value, int size, int color, boolean bold) {
        TextView view = text(value, size, color, bold);
        view.setTextDirection(View.TEXT_DIRECTION_LTR);
        view.setGravity(Gravity.START);
        view.setTypeface(bold ? nunitoBold : nunitoRegular);
        return view;
    }

    private Button button(String label, int color, int fgColor) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(foreground(fgColor));
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setTypeface(fontFor(label, true));
        button.setMinHeight(dp(54));
        button.setPadding(dp(14), dp(9), dp(14), dp(9));
        GradientDrawable shape = bg(color, color == Color.WHITE ? LINE : 0, 18);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(dark() ? 0x33ffffff : 0x22000000), shape, null));
        return button;
    }

    private void full(LinearLayout box, View view) {
        box.addView(view, new LinearLayout.LayoutParams(-1, -2));
    }

    private void space(LinearLayout box, int height) {
        box.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout layout = column();
        layout.setPadding(dp(20), dp(20), dp(20), dp(20));
        layout.setBackground(bg(Color.WHITE, LINE, 24));
        return layout;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private ProgressBar progress(int value, int max) {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(Math.max(1, max));
        bar.setProgress(Math.max(0, Math.min(value, max)));
        bar.setProgressTintList(ColorStateList.valueOf(dark() ? 0xff58cc72 : GREEN));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(surface(LINE)));
        bar.setContentDescription("التقدم: " + value + " من " + max);
        return bar;
    }

    private void setup(String title, boolean back, boolean fixedFooter) {
        root = column();
        root.setBackgroundColor(surface(BG));
        getWindow().setStatusBarColor(surface(BG));
        getWindow().setNavigationBarColor(surface(BG));
        getWindow().getDecorView().setSystemUiVisibility(dark() ? 0 :
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));
        root.setPadding(dp(20), dp(12), dp(20), dp(12));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(20), dp(12) + insets.getSystemWindowInsetTop(), dp(20), dp(12) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        if (back) {
            Button close = button("✕", Color.WHITE, MUTED);
            close.setTextSize(18);
            close.setContentDescription("رجوع");
            bar.addView(close, new LinearLayout.LayoutParams(dp(50), dp(48)));
            close.setOnClickListener(v -> navigateBack());
        }
        TextView heading = text(title, 18, INK, true);
        heading.setGravity(Gravity.CENTER);
        bar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        full(root, bar);
        space(root, 12);
        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body = column();
        scroll.addView(body);
        if (fixedFooter) {
            footer = column();
            space(root, 8);
            full(root, footer);
        } else {
            footer = null;
        }
        if (screen.equals("home") || screen.equals("path") || screen.equals("review")
                || screen.equals("dictionary") || screen.equals("profile")) addNavigation();
    }

    private void addNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        nav.setPadding(0, dp(8), 0, 0);
        String[] labels = {"الرئيسية", "التعلم", "المراجعة", "القاموس", "حسابي"};
        String[] screens = {"home", "path", "review", "dictionary", "profile"};
        String[] icons = {"⌂", "◈", "↻", "▤", "◎"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            boolean active = screen.equals(screens[i]);
            TextView item = text(icons[i] + "\n" + labels[i], 12, active ? DARK_GREEN : MUTED, active);
            item.setGravity(Gravity.CENTER);
            item.setMinHeight(dp(58));
            item.setBackground(bg(active ? PALE : BG, 0, 14));
            item.setContentDescription(labels[i] + (active ? "، القسم الحالي" : ""));
            item.setOnClickListener(v -> {
                if (tab == 0) home();
                else if (tab == 1) path();
                else if (tab == 2) showReview();
                else if (tab == 3) showDictionary();
                else showProfile();
            });
            nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        full(root, nav);
    }

    private void mascot(LinearLayout box, String caption) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        CharacterArt.CharacterView buddy = new CharacterArt.CharacterView(this,
                CharacterArt.Cast.SOL, CharacterArt.Mood.WELCOME, dark(), false);
        row.addView(buddy, new LinearLayout.LayoutParams(dp(76), dp(76)));
        TextView bubble = text(caption, 15, INK, true);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setBackground(bg(Color.WHITE, LINE, 18));
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(0, -2, 1);
        bubbleParams.setMargins(dp(8), 0, 0, 0);
        row.addView(bubble, bubbleParams);
        full(box, row);
    }

    private void sectionTitle(String title, String subtitle) {
        full(body, text(title, 22, INK, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            space(body, 4);
            full(body, text(subtitle, 14, MUTED, false));
        }
        space(body, 12);
    }

    private void hint(String message) {
        if (feedback == null) { toast(message); return; }
        feedback.setVisibility(View.VISIBLE);
        feedback.setTextColor(foreground(BLUE));
        feedback.setBackground(bg(SKY, 0, 14));
        feedback.setText(message);
    }

    // ---------- progress helpers ----------

    private Set<String> doneLessons() {
        Set<String> raw = prefs.getStringSet("done_lessons", null);
        return raw == null ? new HashSet<>() : new HashSet<>(raw);
    }

    private boolean lessonDone(String lessonId) {
        return doneLessons().contains(lessonId);
    }

    private void markLessonDone(String lessonId) {
        Set<String> done = doneLessons();
        if (!done.add(lessonId)) return;
        prefs.edit().putStringSet("done_lessons", done).apply();
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            int count = 0;
            for (Lesson lesson : track.lessons) if (done.contains(lesson.id)) count++;
            prefs.edit().putInt("complete_" + i, count).apply();
        }
    }

    private int doneLessons(Track track) {
        Set<String> done = doneLessons();
        int count = 0;
        for (Lesson lesson : track.lessons) if (done.contains(lesson.id)) count++;
        return count;
    }

    private int percent(int part, int whole) {
        return whole <= 0 ? 0 : Math.max(0, Math.min(100, part * 100 / whole));
    }

    private String percentage(int part, int whole) {
        return percent(part, whole) + "٪";
    }

    private boolean unlockAll() {
        return prefs.getBoolean("unlock_all", false);
    }

    private boolean levelUnlocked(int index) {
        if (index <= 0 || unlockAll()) return true;
        Track previous = tracks.get(index - 1);
        return percent(doneLessons(previous), Math.max(1, previous.lessons.size())) >= 80;
    }

    private Lesson nextLesson(Track track) {
        Set<String> done = doneLessons();
        for (Lesson lesson : track.lessons) if (!done.contains(lesson.id)) return lesson;
        return null;
    }

    private boolean lessonUnlocked(Track track, Lesson lesson) {
        if (unlockAll()) return true;
        if (lessonDone(lesson.id)) return true;
        Lesson next = nextLesson(track);
        return next != null && next.id.equals(lesson.id);
    }

    private int learnedItemCount(Track track) {
        Set<String> done = doneLessons();
        Set<String> ids = new LinkedHashSet<>();
        for (Lesson lesson : track.lessons) {
            if (!done.contains(lesson.id)) continue;
            for (Item item : lesson.items) ids.add(item.id);
        }
        return ids.size();
    }

    private List<String> allItemIds() {
        return new ArrayList<>(curriculum.itemsById.keySet());
    }

    private int dueCount() {
        return ReviewScheduler.dueCount(store, allItemIds(), System.currentTimeMillis());
    }

    private int masteredCount() {
        return ReviewScheduler.masteredCount(store, allItemIds());
    }

    private int weakCount() {
        int count = 0;
        for (String id : allItemIds()) if (prefs.getInt("misses_" + id, 0) > 0) count++;
        return count;
    }

    private String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private int dailyLessonsToday() {
        return todayKey().equals(prefs.getString("daily_date", "")) ? prefs.getInt("daily_lessons", 0) : 0;
    }

    private int xpLevel() {
        return XpPolicy.levelFromXp(prefs.getInt("xp", 0));
    }

    // ---------- welcome ----------

    private void showWelcome() {
        screen = "welcome";
        setup("أهلاً بك في Spanish 1000", false, false);
        mascot(body, "هنتعلم إسباني من الصفر، بالعربي المصري، خطوة كل يوم.");
        space(body, 16);
        LinearLayout intro = card();
        intro.setBackground(bg(PALE, 0, 24));
        full(intro, text("إيه اللي هتلاقيه هنا؟", 22, INK, true));
        space(intro, 8);
        String[] points = {
                "منهج مرتب من A0 (تأسيس) لحد C1 (متقدم).",
                "لكل كلمة: المعنى بالفصحى والمصري، النطق، ومثال في جملة.",
                "تمارين: اختيار، استماع، ترتيب، إكمال، ترجمة، نطق، وفهم حوارات.",
                "مدرس أخطاء يشرح الغلط بالمصري ويرجّع الكلمة في المراجعة.",
                "كل حاجة شغالة أوفلاين، وتقدمك محفوظ على جهازك.",
        };
        for (String point : points) {
            full(intro, text("◆  " + point, 15, INK, false));
            space(intro, 5);
        }
        full(body, intro);
        space(body, 16);
        LinearLayout setupCard = card();
        full(setupCard, text("هدفك اليومي", 18, INK, true));
        space(setupCard, 6);
        final TextView goalValue = text(prefs.getInt("daily_goal", 1) + " درس يوميًا", 17, GREEN, true);
        goalValue.setGravity(Gravity.CENTER);
        full(setupCard, goalValue);
        space(setupCard, 8);
        LinearLayout goalRow = new LinearLayout(this);
        Button less = button("−", Color.WHITE, BLUE);
        Button more = button("+", Color.WHITE, BLUE);
        goalRow.addView(less, new LinearLayout.LayoutParams(0, dp(50), 1));
        goalRow.addView(more, new LinearLayout.LayoutParams(0, dp(50), 1));
        full(setupCard, goalRow);
        less.setOnClickListener(v -> {
            prefs.edit().putInt("daily_goal", Math.max(1, prefs.getInt("daily_goal", 1) - 1)).apply();
            goalValue.setText(prefs.getInt("daily_goal", 1) + " درس يوميًا");
        });
        more.setOnClickListener(v -> {
            prefs.edit().putInt("daily_goal", Math.min(10, prefs.getInt("daily_goal", 1) + 1)).apply();
            goalValue.setText(prefs.getInt("daily_goal", 1) + " درس يوميًا");
        });
        full(body, setupCard);
        space(body, 16);
        Button start = button("ابدأ من A0", GREEN, Color.WHITE);
        start.setOnClickListener(v -> {
            prefs.edit().putBoolean("onboarding_done", true).apply();
            trackIndex = 0;
            prefs.edit().putInt("last_track", 0).apply();
            home();
        });
        full(body, start);
        space(body, 8);
        full(body, text("التطبيق مش بيدّعي إنه بيضمن الطلاقة. الهدف: أساس قوي + تمرين مستمر. "
                + "عدد عناصر المحتوى الحالي: " + curriculum.summaryLine(), 13, MUTED, false));
    }

    // ---------- home ----------

    private void home() {
        screen = "home";
        setup("Spanish 1000", false, false);
        Track track = currentTrack();
        Set<String> done = doneLessons();
        int levelDone = doneLessons(track);
        Lesson next = nextLesson(track);

        LinearLayout welcome = new LinearLayout(this);
        welcome.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout greeting = column();
        full(greeting, text("¡Hola! أهلًا بيك", 25, INK, true));
        space(greeting, 4);
        full(greeting, text(prefs.getInt("streak", 0) > 0 ? "كمّل سلسلتك 🔥" : "كل يوم خطوة صغيرة", 15, MUTED, false));
        welcome.addView(greeting, new LinearLayout.LayoutParams(0, -2, 1));
        CharacterArt.CharacterView buddy = new CharacterArt.CharacterView(this,
                CharacterArt.Cast.SOL, CharacterArt.Mood.HAPPY, dark(), false);
        welcome.addView(buddy, new LinearLayout.LayoutParams(dp(72), dp(72)));
        full(body, welcome);
        space(body, 14);

        LinearLayout indicators = new LinearLayout(this);
        TextView streak = text("سلسلتك " + prefs.getInt("streak", 0) + " يوم", 14, DARK_GREEN, true);
        TextView xp = text(prefs.getInt("xp", 0) + " XP · مستوى " + xpLevel(), 14, DARK_GREEN, true);
        streak.setGravity(Gravity.CENTER);
        xp.setGravity(Gravity.CENTER);
        streak.setPadding(dp(8), dp(12), dp(8), dp(12));
        xp.setPadding(dp(8), dp(12), dp(8), dp(12));
        streak.setBackground(bg(PALE, 0, 16));
        xp.setBackground(bg(LAVENDER, 0, 16));
        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(0, -2, 1);
        indicatorParams.setMargins(0, 0, dp(8), 0);
        indicators.addView(streak, indicatorParams);
        indicators.addView(xp, new LinearLayout.LayoutParams(0, -2, 1));
        full(body, indicators);
        space(body, 18);

        resumeSessionCard(body);

        LinearLayout continuation = card();
        continuation.setBackground(bg(PALE, 0, 24));
        full(continuation, text(track.title + "  •  " + track.titleEs, 14, DARK_GREEN, true));
        space(continuation, 6);
        full(continuation, text(next == null ? "خلصت كل دروس المستوى — راجع أو كمّل للمستوى اللي بعده" :
                next.title, 20, INK, true));
        space(continuation, 6);
        full(continuation, text(levelDone + " من " + track.lessons.size() + " درس مكتمل  •  "
                + learnedItemCount(track) + " عنصر من " + track.items.size(), 14, MUTED, false));
        space(continuation, 8);
        full(continuation, progress(levelDone, track.lessons.size()));
        space(continuation, 14);
        Button resume = button(next == null ? "استعرض المستويات" : (levelDone == 0 ? "ابدأ أول درس" : "كمّل الدرس التالي"),
                GREEN, Color.WHITE);
        resume.setOnClickListener(v -> {
            if (next == null) showLevels();
            else startLesson(next, false);
        });
        full(continuation, resume);
        full(body, continuation);
        space(body, 14);

        dailyGoalCard(body);
        space(body, 14);

        LinearLayout reviewPanel = card();
        full(reviewPanel, text("المراجعة المتباعدة", 18, INK, true));
        space(reviewPanel, 5);
        full(reviewPanel, text(dueCount() > 0 ? dueCount() + " عنصر مستحق النهاردة" : "مفيش عناصر مستحقة دلوقتي",
                14, MUTED, false));
        space(reviewPanel, 5);
        full(reviewPanel, text(masteredCount() + " عنصر متقن  •  " + weakCount() + " محتاج تدريب", 13, MUTED, false));
        space(reviewPanel, 10);
        Button reviewNow = button(dueCount() > 0 ? "ابدأ المراجعة" : "افتح صفحة المراجعة", Color.WHITE, BLUE);
        reviewNow.setOnClickListener(v -> showReview());
        full(reviewPanel, reviewNow);
        full(body, reviewPanel);
        space(body, 18);

        sectionTitle("اختار قسمك", null);
        Button levels = button("🗺️  خريطة المستويات A0 → C1", Color.WHITE, GREEN);
        levels.setOnClickListener(v -> showLevels());
        full(body, levels);
        space(body, 8);
        Button dictionary = button("📖  القاموس والمفضلة", Color.WHITE, BLUE);
        dictionary.setOnClickListener(v -> showDictionary());
        full(body, dictionary);
        space(body, 8);
        Button callCenter = button("☎️  كول سنتر وخدمة عملاء بالإسباني", Color.WHITE, BLUE);
        callCenter.setOnClickListener(v -> showCallCenter());
        full(body, callCenter);
        space(body, 8);
        Button scenarios = button("🎭  مواقف الحياة اليومية", Color.WHITE, GREEN);
        scenarios.setOnClickListener(v -> showScenarios());
        full(body, scenarios);
        space(body, 8);
        Button grammar = button("📚  قواعد بالعربي المصري", Color.WHITE, BLUE);
        grammar.setOnClickListener(v -> showGrammar());
        full(body, grammar);
        space(body, 8);
        Button stats = button("📊  إحصائياتك والإنجازات", Color.WHITE, BLUE);
        stats.setOnClickListener(v -> showStats());
        full(body, stats);
        space(body, 14);
        full(body, text("الصوت بيستخدم محرك TTS في الجهاز بصوت es-ES. التعرف على الكلام بيستخدم خدمة الجهاز، "
                + "وتقييم النطق تقريبي وليس تحليلًا صوتيًا.", 12, MUTED, false));
    }

    /** "Continue where you stopped" — backed by the durable {@link LessonSession}. */
    private void resumeSessionCard(LinearLayout box) {
        LessonSession pending = LessonSession.load(store);
        if (pending == null || pending.cursor <= 0 || pending.finished()) return;
        LinearLayout card = card();
        card.setBackground(bg(SKY, 0, 22));
        full(card, text("فيه درس وقفت في نصه 🕘", 17, BLUE, true));
        space(card, 5);
        String title = pending.lessonId;
        Lesson lesson = findLessonById(pending.lessonId);
        if (lesson != null && !lesson.title.isEmpty()) title = lesson.title;
        full(card, text(title, 16, INK, true));
        space(card, 4);
        full(card, text("وقفت عند تمرين " + Math.min(pending.cursor + 1, pending.total) + " من " + pending.total
                + " — إجابات صح من أول مرة: " + pending.firstCorrect + " من " + pending.firstAttempted, 13, MUTED, false));
        space(card, 10);
        LinearLayout row = new LinearLayout(this);
        Button resume = button("كمّل من مكانك", GREEN, Color.WHITE);
        Button restart = button("ابدأها من الأول", Color.WHITE, BLUE);
        resume.setOnClickListener(v -> resumeSavedSession());
        restart.setOnClickListener(v -> {
            LessonSession.clear(store);
            toast("اتلغت الجلسة المحفوظة. الدرس هيتحسب من الأول بدون نقاط إضافية.");
            home();
        });
        row.addView(resume, new LinearLayout.LayoutParams(0, dp(48), 1.2f));
        row.addView(restart, new LinearLayout.LayoutParams(0, dp(48), 1f));
        full(card, row);
        full(box, card);
        space(box, 14);
    }

    private Lesson findLessonById(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Track track : tracks) for (Lesson lesson : track.lessons) if (lesson.id.equals(id)) return lesson;
        return null;
    }

    private void resumeSavedSession() {
        LessonSession state = LessonSession.load(store);
        if (state == null) { toast("مفيش جلسة محفوظة."); home(); return; }
        Lesson lesson = findLessonById(state.lessonId);
        if (lesson == null || lesson.ref == null) {
            if (state.reviewMode && !state.items().isEmpty()) {
                List<Curriculum.Item> items = new ArrayList<>();
                for (String id : state.items()) {
                    Curriculum.Item item = resolveItem(id);
                    if (item != null) items.add(item);
                }
                if (!items.isEmpty()) {
                    startItemSession(items, true);
                    cursor = Math.min(state.cursor, queue.size());
                    firstCorrect = state.firstCorrect;
                    firstAttempted = state.firstAttempted;
                    attempted = state.attempted;
                    correct = state.correct;
                    showExercise();
                    toast("رجعت لجلسة المراجعة من تمرين " + (cursor + 1) + ".");
                    return;
                }
            }
            LessonSession.clear(store);
            toast("الدرس المحفوظ مش موجود في المحتوى الحالي؛ اتحذفت الجلسة بأمان.");
            home();
            return;
        }
        List<Curriculum.Exercise> plan = LessonPlanner.plan(curriculum, lesson.ref);
        if (plan.isEmpty()) {
            LessonSession.clear(store);
            toast("الدرس المحفوظ مفيش فيه تمارين؛ اتحذفت الجلسة بأمان.");
            home();
            return;
        }
        activeLesson = lesson;
        activeDialogue = lesson.dialogueId.isEmpty() ? null : curriculum.dialogue(lesson.dialogueId);
        beginSession(plan, state.reviewMode, lesson.dialogueId, state);
        toast("رجعت للدرس من تمرين " + (Math.min(state.cursor, queue.size() - 1) + 1) + " من " + queue.size() + ".");
    }

    private void dailyGoalCard(LinearLayout box) {
        int goal = Math.max(1, prefs.getInt("daily_goal", 1));
        int done = dailyLessonsToday();
        LinearLayout daily = card();
        daily.setBackground(bg(LAVENDER, 0, 20));
        full(daily, text("🎯  هدفك اليومي", 17, INK, true));
        space(daily, 6);
        int percent = Math.min(100, done * 100 / goal);
        DesignSystem.RingProgressView ring = new DesignSystem.RingProgressView(this, percent,
                done + "/" + goal, "درس اليوم", foreground(GREEN), surface(LINE), foreground(INK),
                foreground(MUTED), cairoBold, nunitoRegular);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(ring, new LinearLayout.LayoutParams(dp(92), dp(92)));
        LinearLayout copy = column();
        full(copy, text(done >= goal ? "وصلت لهدفك النهاردة 🎉" : "باقي " + (goal - done) + " درس لهدفك",
                16, INK, true));
        space(copy, 4);
        full(copy, text("السلسلة: " + prefs.getInt("streak", 0) + " يوم", 13, MUTED, false));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
        copyParams.setMargins(dp(12), 0, 0, 0);
        row.addView(copy, copyParams);
        full(daily, row);
        full(box, daily);
    }

    // ---------- level map ----------

    private void showLevels() {
        screen = "levels";
        setup("خريطة المستويات", true, false);
        full(body, text("المنهج متدرّج: كل مستوى بيفتح لما تخلص ٨٠٪ من اللي قبله. الأربع مستويات تغطي من الصفر لحد المتقدم.",
                14, MUTED, false));
        space(body, 12);
        for (int index = 0; index < tracks.size(); index++) {
            final int levelIndex = index;
            Track track = tracks.get(index);
            boolean unlocked = levelUnlocked(index);
            boolean current = index == trackIndex;
            int done = doneLessons(track);
            LinearLayout box = card();
            box.setBackground(bg(current ? PALE : Color.WHITE, LINE, 24));
            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView badge = text(track.id, 20, unlocked ? Color.WHITE : MUTED, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(bg(unlocked ? (current ? GREEN : BLUE) : LINE, 0, 18));
            head.addView(badge, new LinearLayout.LayoutParams(dp(58), dp(58)));
            LinearLayout copy = column();
            full(copy, text(track.title, 19, INK, true));
            space(copy, 3);
            full(copy, text(track.titleEs + "  •  " + track.units.size() + " وحدات  •  "
                    + track.lessons.size() + " درس", 13, MUTED, false));
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
            copyParams.setMargins(dp(12), 0, 0, 0);
            head.addView(copy, copyParams);
            full(box, head);
            space(box, 8);
            full(box, text(track.descEgy, 13, MUTED, false));
            space(box, 8);
            full(box, progress(done, track.lessons.size()));
            space(box, 5);
            full(box, text(done + "/" + track.lessons.size() + " درس  •  " + percentage(done, track.lessons.size()),
                    13, unlocked ? DARK_GREEN : MUTED, true));
            space(box, 12);
            Button go = button(unlocked ? (done == 0 ? "ابدأ المستوى" : "افتح المستوى")
                    : "مقفول — كمّل " + percentage(80, 100) + " من " + tracks.get(index - 1).id, unlocked ? GREEN : LINE,
                    unlocked ? Color.WHITE : MUTED);
            go.setOnClickListener(v -> {
                if (!unlocked) {
                    toast("خلص ٨٠٪ من مستوى " + tracks.get(levelIndex - 1).id + " الأول. تقدر تلغي القفل من الإعدادات.");
                    return;
                }
                trackIndex = levelIndex;
                prefs.edit().putInt("last_track", levelIndex).apply();
                path();
            });
            full(box, go);
            full(body, box);
            space(body, 12);
        }
        Button unlock = button(unlockAll() ? "🔓 إلغاء قفل المستويات مفعّل" : "🔒 ترتيب المنهج مفعّل (اضغط للتغيير)",
                Color.WHITE, BLUE);
        unlock.setOnClickListener(v -> {
            prefs.edit().putBoolean("unlock_all", !unlockAll()).apply();
            showLevels();
        });
        full(body, unlock);
    }

    // ---------- path ----------

    private void path() {
        screen = "path";
        Track track = currentTrack();
        setup(track.title, true, false);
        int done = doneLessons(track);
        LinearLayout banner = card();
        banner.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{DARK_GREEN, GREEN}));
        banner.getBackground().setAlpha(255);
        full(banner, text(track.titleEs + " ✨", 22, Color.WHITE, true));
        space(banner, 6);
        full(banner, text(track.goalEgy, 14, Color.WHITE, false));
        space(banner, 8);
        ProgressBar bannerProgress = progress(done, track.lessons.size());
        bannerProgress.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        full(banner, bannerProgress);
        space(banner, 6);
        full(banner, text(done + " من " + track.lessons.size() + " درس  •  " + percentage(done, track.lessons.size()),
                14, Color.WHITE, true));
        full(body, banner);
        space(body, 14);
        Lesson next = nextLesson(track);
        if (next != null) {
            Button start = button("▶  " + next.title, GREEN, Color.WHITE);
            start.setOnClickListener(v -> startLesson(next, false));
            full(body, start);
            space(body, 10);
        }
        Set<String> doneIds = doneLessons();
        for (Unit unit : track.units) {
            LinearLayout unitCard = card();
            unitCard.setBackground(bg(PALE, 0, 20));
            full(unitCard, text(unit.title, 18, INK, true));
            space(unitCard, 3);
            full(unitCard, text(unit.titleEs, 13, MUTED, false));
            space(unitCard, 4);
            full(unitCard, text(unit.goalEgy, 13, MUTED, false));
            int unitDone = 0;
            for (Lesson lesson : unit.lessons) if (doneIds.contains(lesson.id)) unitDone++;
            space(unitCard, 8);
            full(unitCard, progress(unitDone, Math.max(1, unit.lessons.size())));
            full(body, unitCard);
            space(body, 8);
            for (Lesson lesson : unit.lessons) {
                boolean finished = doneIds.contains(lesson.id);
                boolean unlocked = lessonUnlocked(track, lesson);
                boolean current = next != null && next.id.equals(lesson.id);
                LinearLayout row = card();
                row.setBackground(bg(finished ? PALE : Color.WHITE, LINE, 18));
                LinearLayout head = new LinearLayout(this);
                head.setGravity(Gravity.CENTER_VERTICAL);
                TextView state = text(finished ? "✓" : current ? "▶" : unlocked ? "◻" : "🔒", 18,
                        finished ? Color.WHITE : unlocked ? Color.WHITE : MUTED, true);
                state.setGravity(Gravity.CENTER);
                state.setBackground(bg(finished ? DARK_GREEN : current ? GREEN : unlocked ? BLUE : LINE, 0, 14));
                head.addView(state, new LinearLayout.LayoutParams(dp(42), dp(42)));
                LinearLayout copy = column();
                full(copy, text(lesson.title, 16, INK, true));
                space(copy, 3);
                full(copy, text(lesson.dialogueId.isEmpty()
                        ? lesson.items.size() + " عنصر"
                        : "حوار وفهم مسموع", 12, MUTED, false));
                LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
                copyParams.setMargins(dp(10), 0, 0, 0);
                head.addView(copy, copyParams);
                full(row, head);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                row.setOnClickListener(v -> {
                    if (unlocked) startLesson(lesson, finished);
                    else toast("أكمل الدرس اللي قبله الأول.");
                });
                full(body, row);
                space(body, 6);
            }
            space(body, 10);
        }
        if (done == track.lessons.size() && trackIndex + 1 < tracks.size()) {
            Button nextLevel = button("المستوى اللي بعده: " + tracks.get(trackIndex + 1).id, BLUE, Color.WHITE);
            nextLevel.setOnClickListener(v -> showLevels());
            full(body, nextLevel);
        }
    }

    // ---------- lesson session ----------

    private Curriculum.Item resolveItem(String id) {
        return id == null || id.isEmpty() ? null : curriculum.item(id);
    }

    private Session currentSession() {
        return cursor >= 0 && cursor < queue.size() ? queue.get(cursor) : null;
    }

    private void startLesson(Lesson lesson, boolean asReview) {
        if (lesson == null) { toast("مفيش درس متاح."); return; }
        activeLesson = lesson;
        activeDialogue = lesson.dialogueId.isEmpty() ? null : curriculum.dialogue(lesson.dialogueId);
        List<Curriculum.Exercise> plan = lesson.ref == null
                ? new ArrayList<>() : LessonPlanner.plan(curriculum, lesson.ref);
        beginSession(plan, asReview, lesson.dialogueId);
    }

    private void startItemSession(List<Curriculum.Item> items, boolean asReview) {
        if (items.isEmpty()) { toast("مفيش عناصر للمراجعة دلوقتي."); return; }
        Curriculum.Lesson synthetic = new Curriculum.Lesson();
        synthetic.id = "review_" + todayKey() + "_" + items.size();
        synthetic.title = "مراجعة سريعة";
        synthetic.titleEs = "Repaso";
        synthetic.goalEgy = "مراجعة العناصر المستحقة والكلمات المتعثرة.";
        for (Curriculum.Item item : items) synthetic.itemIds.add(item.id);
        activeLesson = new Lesson();
        activeLesson.id = synthetic.id;
        activeLesson.title = synthetic.title;
        activeLesson.ref = synthetic;
        for (Curriculum.Item item : items) {
            Item local = findItemBySource(item.id);
            if (local != null) activeLesson.items.add(local);
        }
        activeDialogue = null;
        beginSession(LessonPlanner.plan(curriculum, synthetic), asReview, "");
    }

    private void beginSession(List<Curriculum.Exercise> plan, boolean asReview, String dialogueId) {
        beginSession(plan, asReview, dialogueId, null);
    }

    private void beginSession(List<Curriculum.Exercise> plan, boolean asReview, String dialogueId, LessonSession restore) {
        queue.clear();
        mistakes.clear();
        skippedItems.clear();
        srsGraded.clear();
        firstOutcome.clear();
        sessionItems.clear();
        for (Curriculum.Exercise exercise : plan) {
            queue.add(new Session(exercise, resolveItem(exercise.itemId), false, dialogueId));
            Curriculum.Item item = resolveItem(exercise.itemId);
            if (item != null && !containsItem(sessionItems, item.id)) sessionItems.add(findItemBySource(item.id));
        }
        if (sessionItems.isEmpty() && activeLesson != null) {
            for (Item studied : activeLesson.items) {
                if (studied != null && !containsItem(sessionItems, studied.id)) sessionItems.add(studied);
            }
        }
        sessionItems.removeAll(Collections.singleton(null));
        cursor = 0;
        correct = 0;
        attempted = 0;
        firstCorrect = 0;
        firstAttempted = 0;
        speechSkipped = 0;
        listeningSkipped = 0;
        reviewMode = asReview;
        lessonStarted = System.currentTimeMillis();
        if (queue.isEmpty()) { toast("الدرس مفيش فيه تمارين."); path(); return; }
        savedSession = LessonSession.begin(activeLesson == null ? "" : activeLesson.id, dialogueId, queue.size(),
                asReview, lessonStarted);
        java.util.List<String> plannedIds = new ArrayList<>();
        for (Curriculum.Exercise planned : plan) {
            if (!planned.itemId.isEmpty() && !plannedIds.contains(planned.itemId)) plannedIds.add(planned.itemId);
        }
        savedSession.setItems(plannedIds);
        if (restore != null) {
            cursor = Math.min(restore.cursor, queue.size());
            firstCorrect = restore.firstCorrect;
            firstAttempted = restore.firstAttempted;
            attempted = restore.attempted;
            correct = restore.correct;
            lessonStarted = restore.startedAt > 0 ? restore.startedAt : lessonStarted;
            savedSession = restore;
        }
        LessonSession.save(store, savedSession);
        showExercise();
    }

    private boolean containsItem(List<Item> items, String id) {
        for (Item item : items) if (item.id.equals(id)) return true;
        return false;
    }

    private String typeTitle(String type) {
        switch (type) {
            case "intro": return "كلمة جديدة ✨";
            case "choose": return "اختار المعنى الصحيح";
            case "listen": return "اسمع واختار";
            case "order": return "رتّب الجملة";
            case "fill": return "كمّل الجملة";
            case "translate_es_ar": return "ترجم للعربي";
            case "translate_ar_es": return "اكتب بالإسباني";
            case "speak": return "قولها بصوتك 🎙";
            case "match": return "وصّل الكلمات 🃏";
            case "comprehend": return "فهم الحوار";
            case "final": return "اختبار ختامي";
            default: return "تمرين";
        }
    }

    private void showExercise() {
        stopRecognizer();
        if (cursor >= queue.size()) { finishLesson(); return; }
        screen = "exercise";
        answered = false;
        selectedChoice = null;
        selectedWord = null;
        selectedWords.clear();
        selectedChips.clear();
        input = null;
        action = null;
        feedback = null;
        Session session = queue.get(cursor);
        Curriculum.Exercise ex = session.ex;
        Curriculum.Item item = session.item;

        setup((reviewMode ? "مراجعة • " : "سؤال ") + (cursor + 1) + " / " + queue.size(), true, true);
        full(body, progress(cursor, queue.size()));
        space(body, 12);
        full(body, text(session.retry ? "مراجعة خطأ سابق ↻" : typeTitle(ex.type), 21, INK, true));
        if (Tutor.hint(ex) != null && !Tutor.hint(ex).isEmpty()) {
            space(body, 4);
            full(body, text(Tutor.hint(ex), 13, MUTED, false));
        }
        space(body, 12);

        LinearLayout prompt = card();
        prompt.setBackground(bg(ex.type.equals("intro") ? PALE : ex.type.equals("listen") ? SKY : Color.WHITE, 0, 24));
        if (activeDialogue != null && (ex.type.equals("comprehend") || ex.type.equals("listen"))) {
            renderDialogueLines(prompt, activeDialogue);
            space(prompt, 10);
        }
        if (ex.type.equals("intro")) {
            renderIntro(prompt, ex, item);
        } else if (ex.type.equals("listen")) {
            full(prompt, text(ex.promptEgy, 16, INK, true));
            space(prompt, 10);
            audioControls(prompt, ex.audio);
        } else if (ex.type.equals("order") || ex.type.equals("fill")) {
            full(prompt, text(ex.promptEgy, 18, INK, true));
            if (item != null && item.hasExample() && ex.type.equals("order")) {
                space(prompt, 6);
                full(prompt, text("المعنى: " + (item.egy.isEmpty() ? item.ar : item.egy), 14, MUTED, false));
            }
        } else if (ex.type.equals("translate_es_ar")) {
            full(prompt, text(ex.promptEgy, 14, MUTED, false));
            space(prompt, 6);
            full(prompt, ltr(ex.audio.isEmpty() ? ex.promptEgy : ex.audio, 22, INK, true));
            space(prompt, 8);
            audioControls(prompt, ex.audio);
        } else if (ex.type.equals("translate_ar_es")) {
            full(prompt, text(ex.promptEgy, 20, INK, true));
            if (item != null && !item.exEs.isEmpty()) {
                space(prompt, 6);
                full(prompt, ltr(item.exEs, 15, MUTED, false));
                if (!item.exEgy.isEmpty()) full(prompt, text("مثال: " + item.exEgy, 13, MUTED, false));
            }
        } else if (ex.type.equals("speak")) {
            full(prompt, text(ex.promptEgy, 18, INK, true));
            space(prompt, 8);
            full(prompt, ltr(ex.audio, 22, INK, true));
            space(prompt, 8);
            audioControls(prompt, ex.audio);
            space(prompt, 6);
            full(prompt, text("التقييم تقريبي بالتعرف على الكلام — مش تحليل صوتي احترافي للنطق.", 12, MUTED, false));
        } else if (ex.type.equals("match")) {
            full(prompt, text(ex.promptEgy, 17, INK, true));
        } else {
            full(prompt, text(ex.promptEgy, 19, INK, true));
            if (!ex.promptFusha.isEmpty() && !ex.promptFusha.equals(ex.promptEgy)) {
                space(prompt, 5);
                full(prompt, text("بالفصحى: " + ex.promptFusha, 13, MUTED, false));
            }
        }
        full(body, prompt);
        space(body, 14);

        answers = column();
        full(body, answers);
        renderAnswers(ex, item);

        feedback = text("", 15, INK, true);
        feedback.setVisibility(View.GONE);
        feedback.setPadding(dp(14), dp(12), dp(14), dp(12));
        full(footer, feedback);
        space(footer, 8);
        action = button("تحقق", GREEN, Color.WHITE);
        if (ex.type.equals("intro")) action.setText("فهمت، كمّل");
        if (ex.type.equals("speak")) action.setText("جرّب الميكروفون");
        if (ex.type.equals("match")) action.setText("كمّل الأزواج");
        action.setEnabled(ex.type.equals("intro") || ex.type.equals("speak") || ex.type.equals("match"));
        full(footer, action);
        action.setOnClickListener(v -> {
            if (answered) { advanceSession(); showExercise(); return; }
            if (ex.type.equals("intro")) { advanceSession(); showExercise(); return; }
            if (ex.type.equals("speak")) { requestSpeech(speakTarget(ex)); return; }
            if (ex.type.equals("match")) { hint("طابق كل الأزواج الأول."); return; }
            checkAnswer(session);
        });
        if (input != null) watchInput(input);
    }

    /** Moves to the next exercise and persists the position immediately. */
    private void advanceSession() {
        cursor++;
        if (savedSession != null) {
            savedSession.cursor = cursor;
            LessonSession.save(store, savedSession);
        }
    }

    private String speakTarget(Curriculum.Exercise ex) {
        if (!ex.audio.isEmpty()) return ex.audio;
        if (!ex.answers.isEmpty()) return ex.answers.get(0);
        return "";
    }

    private void renderIntro(LinearLayout box, Curriculum.Exercise ex, Curriculum.Item item) {
        String spanishText = !ex.audio.isEmpty() ? ex.audio : (item != null ? item.es : "");
        full(box, ltr(spanishText, 26, INK, true));
        if (item != null && !item.ipa.isEmpty()) {
            space(box, 5);
            full(box, ltr("[" + item.ipa + "]", 15, BLUE, false));
        }
        space(box, 10);
        if (item != null) {
            full(box, text("الفصحى: " + item.ar, 17, INK, true));
            if (!item.egy.isEmpty()) {
                space(box, 5);
                TextView egy = text("بالمصري: " + item.egy, 17, DARK_GREEN, true);
                egy.setBackground(bg(PALE, 0, 12));
                egy.setPadding(dp(10), dp(8), dp(10), dp(8));
                full(box, egy);
            }
            if (!item.noteEgy.isEmpty()) {
                space(box, 6);
                full(box, text(item.noteEgy, 13, MUTED, false));
            }
            if (!item.exEs.isEmpty()) {
                space(box, 8);
                full(box, ltr("مثال: " + item.exEs, 15, INK, false));
                full(box, text("بالمصري: " + item.exEgy, 13, MUTED, false));
            }
        }
        space(box, 12);
        audioControls(box, spanishText);
    }

    private void renderAnswers(Curriculum.Exercise ex, Curriculum.Item item) {
        switch (ex.type) {
            case "intro":
                full(answers, text("اقرأ الكلمة واسمعها، وبعدها هتتدرب عليها.", 14, MUTED, false));
                break;
            case "choose":
            case "fill":
            case "listen":
            case "comprehend":
            case "final":
                renderChoices(ex);
                break;
            case "order":
                renderOrder(ex);
                break;
            case "translate_es_ar":
                input = makeInput("اكتب المعنى بالعربي (فصحى أو مصري)", true);
                full(answers, input);
                break;
            case "translate_ar_es":
                input = makeInput("اكتب الجملة بالإسباني", false);
                full(answers, input);
                break;
            case "speak":
                full(answers, text("اضغط «جرّب الميكروفون» واتكلم بصوت واضح.", 14, MUTED, false));
                Button skip = button("مش قادر أتكلم دلوقتي", Color.WHITE, MUTED);
                skip.setOnClickListener(v -> skipExercise(true));
                full(answers, skip);
                break;
            case "match":
                renderMatch(ex);
                break;
            default:
                full(answers, text("نوع تمرين غير معروف.", 14, RED, false));
        }
        if (item != null && !ex.type.equals("intro")) {
            space(answers, 10);
            Button lookup = button("🔍  شوف الكلمة في القاموس", Color.WHITE, BLUE);
            lookup.setOnClickListener(v -> { Item local = findItemBySource(item.id); if (local != null) showWordSheet(local); });
            full(answers, lookup);
        }
    }

    private EditText makeInput(String hintText, boolean arabic) {
        EditText field = new EditText(this);
        field.setTypeface(arabic ? cairoRegular : nunitoRegular);
        field.setSingleLine(false);
        field.setTextSize(19);
        field.setTextColor(foreground(INK));
        field.setHintTextColor(foreground(MUTED));
        field.setHint(hintText);
        field.setTextDirection(arabic ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        field.setPadding(dp(15), dp(12), dp(15), dp(12));
        field.setBackground(bg(Color.WHITE, LINE, 16));
        return field;
    }

    private void renderChoices(Curriculum.Exercise ex) {
        for (int i = 0; i < ex.options.size(); i++) {
            final int index = i;
            String label = ex.options.get(i);
            Button choice = button(label, Color.WHITE, INK);
            choice.setMinHeight(dp(56));
            choice.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            if (!isArabicText(label)) choice.setTextDirection(View.TEXT_DIRECTION_LTR);
            choice.setOnClickListener(v -> {
                if (answered) return;
                selectedChoice = index;
                for (int child = 0; child < answers.getChildCount(); child++) {
                    View view = answers.getChildAt(child);
                    if (view instanceof Button) view.setBackground(bg(Color.WHITE, LINE, 16));
                }
                choice.setBackground(bg(SKY, BLUE, 16));
                if (action != null) action.setEnabled(true);
            });
            full(answers, choice);
            space(answers, 7);
        }
    }

    private void renderOrder(Curriculum.Exercise ex) {
        full(answers, text("إجابتك — اضغط كلمة لإزالتها", 13, MUTED, false));
        space(answers, 5);
        selectedBank = new WordFlow(this);
        selectedBank.setMinimumHeight(dp(55));
        selectedBank.setPadding(dp(5), dp(5), dp(5), dp(5));
        selectedBank.setBackground(bg(Color.WHITE, LINE, 14));
        full(answers, selectedBank);
        space(answers, 10);
        full(answers, text("اختار الكلمات بالترتيب الصح", 13, MUTED, false));
        space(answers, 5);
        bank = new WordFlow(this);
        full(answers, bank);
        for (String word : ex.options) {
            Button chip = button(word, Color.WHITE, BLUE);
            chip.setMinHeight(dp(46));
            chip.setTextDirection(View.TEXT_DIRECTION_LTR);
            bank.addView(chip);
            chip.setOnClickListener(v -> {
                if (answered || !chip.isEnabled()) return;
                selectedWords.add(word);
                selectedChips.add(chip);
                chip.setEnabled(false);
                renderSelectedWords();
            });
        }
        space(answers, 8);
        Button reset = button("↺ إعادة ترتيب", Color.WHITE, MUTED);
        reset.setOnClickListener(v -> {
            if (answered) return;
            for (Button chip : selectedChips) chip.setEnabled(true);
            selectedWords.clear();
            selectedChips.clear();
            renderSelectedWords();
        });
        full(answers, reset);
    }

    private void renderSelectedWords() {
        if (action != null && !answered) action.setEnabled(!selectedWords.isEmpty());
        if (selectedBank == null) return;
        selectedBank.removeAllViews();
        for (int i = 0; i < selectedWords.size(); i++) {
            final int index = i;
            Button chosen = button(selectedWords.get(i), SKY, BLUE);
            chosen.setMinHeight(dp(46));
            chosen.setTextDirection(View.TEXT_DIRECTION_LTR);
            selectedBank.addView(chosen);
            chosen.setOnClickListener(v -> {
                if (answered) return;
                selectedChips.remove(index).setEnabled(true);
                selectedWords.remove(index);
                renderSelectedWords();
            });
        }
    }

    private void renderMatch(Curriculum.Exercise ex) {
        ArrayList<String[]> pairs = new ArrayList<>(ex.pairs);
        if (pairs.size() < 2) {
            full(answers, text("مش كفاية أزواج للمطابقة.", 14, MUTED, false));
            return;
        }
        matchCards = new MatchCards(this, pairs, moves -> {
            if (answered) return;
            gradeCurrent(true, "طابقت " + pairs.size() + " أزواج في " + moves + " محاولة.");
        });
        full(answers, matchCards);
    }

    private void watchInput(EditText field) {
        field.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (action != null && !answered) action.setEnabled(!s.toString().trim().isEmpty());
            }
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void checkAnswer(Session session) {
        Curriculum.Exercise ex = session.ex;
        AnswerChecker.Answer answer;
        if (ex.type.equals("choose") || ex.type.equals("fill") || ex.type.equals("listen")
                || ex.type.equals("comprehend") || ex.type.equals("final")) {
            if (selectedChoice == null) { toast("اختار إجابة الأول."); return; }
            answer = AnswerChecker.Answer.choice(selectedChoice);
        } else if (ex.type.equals("order")) {
            if (selectedWords.isEmpty()) { toast("اضغط الكلمات بالترتيب الأول."); return; }
            answer = AnswerChecker.Answer.tokens(selectedWords);
        } else if (ex.type.equals("translate_es_ar") || ex.type.equals("translate_ar_es")) {
            if (input == null) { toast("اكتب إجابتك الأول."); return; }
            String value = input.getText().toString();
            if (value.trim().isEmpty()) { toast("اكتب إجابتك الأول."); return; }
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
            answer = AnswerChecker.Answer.text(value);
        } else {
            return;
        }
        AnswerChecker.Verdict verdict = AnswerChecker.check(ex, answer, session.item);
        gradeCurrent(verdict.correct, verdict.diagnosis + (verdict.correct ? "" : "\n" + verdict.expected));
    }

    private void gradeCurrent(boolean correctAnswer, String extra) {
        if (answered) return;
        Session session = currentSession();
        if (session == null) return;
        answered = true;
        answered = true;
        stopRecognizer();
        hideKeyboard();
        attempted++;
        if (correctAnswer) correct++;
        if (!session.retry) {
            firstAttempted++;
            if (correctAnswer) firstCorrect++;
            firstOutcome.putIfAbsent(session.ex.itemId, correctAnswer);
        } else if (!firstOutcome.containsKey(session.ex.itemId)) {
            firstOutcome.put(session.ex.itemId, correctAnswer);
        }
        if (savedSession != null) {
            savedSession.record(correctAnswer, session.retry, session.ex.itemId, session.ex.type);
            LessonSession.save(store, savedSession);
        }
        if (!correctAnswer) {
            mistakes.add(session.ex.itemId + ":" + session.ex.type);
            if (session.ex.itemId != null && !session.ex.itemId.isEmpty()) {
                prefs.edit().putInt("misses_" + session.ex.itemId,
                        prefs.getInt("misses_" + session.ex.itemId, 0) + 1).apply();
                // Enqueue the item for spaced review right away (once per session).
                if (srsGraded.add(session.ex.itemId)) {
                    ReviewScheduler.grade(store, session.ex.itemId, ReviewScheduler.AGAIN, System.currentTimeMillis());
                }
            }
            queue.add(new Session(session.ex, session.item, true, session.dialogueId));
        }
        renderFeedback(session, correctAnswer, extra);
    }

    private void renderFeedback(Session session, boolean correctAnswer, String extra) {
        Curriculum.Exercise ex = session.ex;
        Tutor.Feedback review = correctAnswer
                ? Tutor.correct(curriculum, ex, session.item)
                : Tutor.wrong(curriculum, ex, session.item, verdictFor(ex, extra));
        if (feedback == null) return;
        feedback.setVisibility(View.VISIBLE);
        feedback.setTextColor(foreground(correctAnswer ? DARK_GREEN : RED));
        feedback.setBackground(bg(correctAnswer ? PALE : RED_BG, 0, 14));
        StringBuilder text = new StringBuilder();
        if (correctAnswer) {
            text.append("إجابة صحيحة ✓");
            String correctLabel = AnswerChecker.expectedLabel(ex);
            if (!correctLabel.isEmpty()) text.append("  —  ").append(correctLabel);
        } else {
            text.append(review.headline.isEmpty() ? "الإجابة مش صح" : review.headline);
        }
        if (review.explanationEgy != null && !review.explanationEgy.isEmpty()) text.append('\n').append(review.explanationEgy);
        if (!review.ruleTitle.isEmpty()) text.append("\n📚 قاعدة: ").append(review.ruleTitle).append(" — ").append(review.ruleEgy);
        feedback.setText(text.toString());
        feedback.setAlpha(0f);
        feedback.animate().alpha(1f).setDuration(160).start();
        disableAnswers();
        if (action != null) {
            action.setEnabled(true);
            action.setText("كمّل");
        }
        String speakable = AnswerChecker.expectedLabel(ex);
        if (!speakable.isEmpty() && !isArabicText(speakable)) speak(speakable, false);
    }

    private AnswerChecker.Verdict verdictFor(Curriculum.Exercise ex, String extra) {
        String expected = AnswerChecker.expectedLabel(ex);
        String diagnosis = extra == null ? "" : extra;
        return new AnswerChecker.Verdict(false, expected, diagnosis);
    }

    private void skipExercise(boolean speech) {
        if (answered) return;
        stopRecognizer();
        Session session = currentSession();
        if (session == null) return;
        if (speech) speechSkipped++; else listeningSkipped++;
        skippedItems.add(session.ex.itemId);
        answered = true;
        if (feedback != null) {
            feedback.setVisibility(View.VISIBLE);
            feedback.setTextColor(foreground(MUTED));
            feedback.setBackground(bg(SKY, 0, 14));
            feedback.setText("تم التخطي من غير تقييم. العنصر هيرجع في المراجعة، ومش هيتحسب صح أو غلط.");
        }
        if (action != null) { action.setEnabled(true); action.setText("كمّل"); }
        disableAnswers();
    }

    private void disableAnswers() {
        for (int i = 0; i < answers.getChildCount(); i++) {
            View view = answers.getChildAt(i);
            view.setEnabled(false);
            if (view instanceof MatchCards) {
                MatchCards cards = (MatchCards) view;
                for (int j = 0; j < cards.getChildCount(); j++) cards.getChildAt(j).setEnabled(false);
            }
        }
        if (input != null) input.setEnabled(false);
    }

    private void hideKeyboard() {
        if (input == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private void finishLesson() {
        stopRecognizer();
        stopSpeaking();
        screen = "summary";
        LessonSession.clear(store);
        savedSession = null;
        long now = System.currentTimeMillis();
        SharedPreferences.Editor edit = prefs.edit();
        int earned = 0;
        String title;
        if (reviewMode) {
            for (Item item : sessionItems) earned += XpPolicy.reviewReward(store, item.id, todayKey());
            title = "خلصت المراجعة";
        } else if (activeLesson != null) {
            earned = XpPolicy.lessonReward(store, activeLesson.id, firstCorrect);
            if (activeLesson.dialogueId.isEmpty()) markLessonDone(activeLesson.id);
            else {
                Set<String> dialogues = new HashSet<>(prefs.getStringSet("done_dialogues", new HashSet<>()));
                dialogues.add(activeLesson.dialogueId);
                edit.putStringSet("done_dialogues", dialogues);
            }
            title = "خلصت الدرس";
        } else {
            title = "خلصت التمرين";
        }
        // spaced repetition outcome per item
        for (Map.Entry<String, Boolean> entry : firstOutcome.entrySet()) {
            if (srsGraded.contains(entry.getKey())) continue;
            int quality = entry.getValue() ? ReviewScheduler.GOOD : ReviewScheduler.AGAIN;
            ReviewScheduler.grade(store, entry.getKey(), quality, now);
        }
        // Items that the lesson teaches but no exercise carried (handwritten dialogue lessons)
        // still enter the spaced-repetition queue once the lesson is finished.
        for (Item studied : sessionItems) {
            if (!firstOutcome.containsKey(studied.id) && !srsGraded.contains(studied.id)) {
                ReviewScheduler.grade(store, studied.id, ReviewScheduler.GOOD, now);
            }
        }
        for (String skipped : skippedItems) {
            if (!firstOutcome.containsKey(skipped) && !srsGraded.contains(skipped))
                ReviewScheduler.grade(store, skipped, ReviewScheduler.HARD, now);
        }
        int xp = prefs.getInt("xp", 0) + earned;
        edit.putInt("xp", xp);
        edit.putInt("total_first_correct", prefs.getInt("total_first_correct", 0) + firstCorrect);
        edit.putInt("total_first_attempted", prefs.getInt("total_first_attempted", 0) + firstAttempted);
        String day = todayKey();
        edit.putInt("acc_" + day + "_c", prefs.getInt("acc_" + day + "_c", 0) + firstCorrect);
        edit.putInt("acc_" + day + "_a", prefs.getInt("acc_" + day + "_a", 0) + firstAttempted);
        int daily = day.equals(prefs.getString("daily_date", "")) ? prefs.getInt("daily_lessons", 0) : 0;
        edit.putString("daily_date", day).putInt("daily_lessons", daily + 1);
        Calendar previousDay = Calendar.getInstance();
        previousDay.setTimeInMillis(now);
        previousDay.add(Calendar.DAY_OF_YEAR, -1);
        String yesterday = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(previousDay.getTime());
        int streak = XpPolicy.nextStreak(prefs.getInt("streak", 0), prefs.getString("last_day", ""), day, yesterday);
        edit.putInt("streak", streak).putString("last_day", day);
        edit.apply();

        setup(title, false, false);
        mascot(body, earned > 0 ? "تمام! خلصت وربحت نقاط." : "مراجعة كويسة. النقاط بتتحسب مرة واحدة بس.");
        space(body, 14);
        LinearLayout summary = card();
        summary.setBackground(bg(PALE, 0, 24));
        full(summary, text(activeLesson == null ? "نتيجة الجلسة" : activeLesson.title, 20, INK, true));
        space(summary, 8);
        full(summary, text("✦  " + earned + " نقطة" + (reviewMode ? " (مكافأة مراجعة يومية)" : ""), 17, GREEN, true));
        space(summary, 6);
        full(summary, text("إجابات صحيحة من أول محاولة: " + firstCorrect + " من " + firstAttempted, 15, INK, false));
        space(summary, 6);
        full(summary, text("الدقة: " + (firstAttempted == 0 ? "—" : percentage(firstCorrect, firstAttempted)), 15, INK, false));
        long seconds = Math.max(1, (now - lessonStarted) / 1000);
        full(summary, text("الوقت: " + (seconds / 60) + " دقيقة و" + (seconds % 60) + " ثانية", 14, MUTED, false));
        if (speechSkipped > 0 || listeningSkipped > 0) {
            full(summary, text("تم تخطي " + speechSkipped + " نطق و" + listeningSkipped + " استماع — لم تُحسب في الدقة.",
                    13, MUTED, false));
        }
        full(summary, progress(firstCorrect, Math.max(1, firstAttempted)));
        full(body, summary);
        space(body, 12);

        if (!sessionItems.isEmpty()) {
            LinearLayout studied = card();
            full(studied, text("العناصر اللي اشتغلت عليها", 17, INK, true));
            space(studied, 6);
            for (Item item : sessionItems) {
                LinearLayout row = column();
                full(row, ltr(item.es, 16, INK, true));
                full(row, text("فصحى: " + item.ar, 13, MUTED, false));
                if (item.hasEgy()) full(row, text("مصري: " + item.egy, 13, DARK_GREEN, false));
                space(studied, 6);
                full(studied, row);
            }
            full(body, studied);
            space(body, 12);
        }
        LinearLayout nextCard = card();
        full(nextCard, text("المراجعة المتباعدة: " + dueCount() + " عنصر مستحق دلوقتي", 15, BLUE, true));
        space(nextCard, 8);
        Lesson next = activeLesson == null ? null : nextLesson(currentTrack());
        if (!reviewMode && next != null && !next.id.equals(activeLesson.id)) {
            Button nextButton = button("الدرس اللي بعده: " + next.title, GREEN, Color.WHITE);
            nextButton.setOnClickListener(v -> startLesson(next, false));
            full(nextCard, nextButton);
            space(nextCard, 8);
        }
        Button repeat = button(reviewMode ? "الرئيسية" : "كرر الدرس (بدون نقاط)", Color.WHITE, BLUE);
        repeat.setOnClickListener(v -> {
            if (reviewMode) home();
            else if (activeLesson != null) startLesson(activeLesson, true);
            else path();
        });
        full(nextCard, repeat);
        space(nextCard, 8);
        Button backToPath = button("العودة للمسار", Color.WHITE, BLUE);
        backToPath.setOnClickListener(v -> path());
        full(nextCard, backToPath);
        full(body, nextCard);
    }

    // ---------- review ----------

    private void showReview() {
        screen = "review";
        setup("المراجعة", false, false);
        List<String> ids = allItemIds();
        int due = ReviewScheduler.dueCount(store, ids, System.currentTimeMillis());
        int mastered = ReviewScheduler.masteredCount(store, ids);
        LinearLayout hero = card();
        hero.setBackground(bg(PALE, 0, 24));
        full(hero, text("المراجعة المتباعدة", 22, INK, true));
        space(hero, 6);
        full(hero, text("الخوارزمية بترجّع كل كلمة قبل ما تنساها: العناصر الصعبة بترجع بدري، والسهلة بتتباعد.",
                14, MUTED, false));
        space(hero, 10);
        full(hero, text(due + " عنصر مستحق دلوقتي  •  " + mastered + " عنصر متقن", 16, DARK_GREEN, true));
        space(hero, 6);
        full(hero, text(weakCount() + " كلمة عندك فيها أخطاء مسجّلة", 14, MUTED, false));
        space(hero, 10);
        full(hero, progress(mastered, Math.max(1, ids.size())));
        space(hero, 12);
        Button start = button(due > 0 ? "ابدأ مراجعة المستحق" : "مفيش مستحق — جرّب مراجعة الكلمات المتعثرة", GREEN, Color.WHITE);
        start.setOnClickListener(v -> startReview());
        full(hero, start);
        full(body, hero);
        space(body, 12);

        Button flashcards = button("🃏  بطاقات المراجعة (تقييم ذاتي)", Color.WHITE, BLUE);
        flashcards.setOnClickListener(v -> startFlashcards());
        full(body, flashcards);
        space(body, 8);
        Button weak = button("🧠  الكلمات اللي بتغلط فيها", Color.WHITE, BLUE);
        weak.setOnClickListener(v -> showWeakWords());
        full(body, weak);
        space(body, 8);
        Button stats = button("📊  شوف تقدمك في الأرقام", Color.WHITE, BLUE);
        stats.setOnClickListener(v -> showStats());
        full(body, stats);
        space(body, 14);
        full(body, text("نظام المراجعة: SM-2 معدّل بأربع درجات (تاني / صعب / كويس / سهل)، مع حفظ عدد المحاولات "
                + "ومستوى الإتقان وموعد المراجعة القادم على الجهاز.", 12, MUTED, false));
    }

    private void startReview() {
        long now = System.currentTimeMillis();
        List<String> due = ReviewScheduler.dueItems(store, allItemIds(), now, 10);
        List<Curriculum.Item> items = new ArrayList<>();
        for (String id : due) {
            Curriculum.Item item = curriculum.item(id);
            if (item != null) items.add(item);
        }
        if (items.isEmpty()) {
            ArrayList<Item> weak = weakItems(10);
            if (!weak.isEmpty()) {
                startItemSession(toSourceItems(weak), true);
                return;
            }
            toast("مفيش عناصر مستحقة. كمّل درس جديد وهنرجعلك بالمراجعة بعدين.");
            return;
        }
        startItemSession(items, true);
    }

    private List<Curriculum.Item> toSourceItems(List<Item> items) {
        List<Curriculum.Item> out = new ArrayList<>();
        for (Item item : items) {
            Curriculum.Item source = curriculum.item(item.id);
            if (source != null) out.add(source);
        }
        return out;
    }

    private ArrayList<Item> weakItems(int limit) {
        ArrayList<Item> weak = new ArrayList<>();
        for (Track track : tracks) for (Item item : track.items) {
            if (prefs.getInt("misses_" + item.id, 0) > 0) weak.add(item);
        }
        Collections.sort(weak, (a, b) -> Integer.compare(prefs.getInt("misses_" + b.id, 0), prefs.getInt("misses_" + a.id, 0)));
        while (weak.size() > limit) weak.remove(weak.size() - 1);
        return weak;
    }

    private void showWeakWords() {
        screen = "weak";
        setup("كلمات تحتاج تدريب", true, false);
        ArrayList<Item> weak = weakItems(60);
        if (weak.isEmpty()) {
            full(body, text("مفيش كلمات متعثرة حاليًا. أي كلمة تغلط فيها هتظهر هنا لوحدها.", 16, GREEN, true));
            return;
        }
        full(body, text("مرتبة حسب عدد مرات الخطأ. اضغط أي كلمة تسمعها أو تشوف تفاصيلها.", 14, MUTED, false));
        space(body, 10);
        for (Item item : weak) {
            LinearLayout box = card();
            full(box, ltr(item.es, 17, INK, true));
            full(box, text("فصحى: " + item.ar, 13, MUTED, false));
            if (item.hasEgy()) full(box, text("مصري: " + item.egy, 13, DARK_GREEN, false));
            full(box, text("أخطاء مسجلة: " + prefs.getInt("misses_" + item.id, 0) + "  •  إتقان: "
                    + ReviewScheduler.masteryLevel(store, item.id) + "/5", 12, MUTED, false));
            LinearLayout row = new LinearLayout(this);
            Button say = button("🔊", SKY, BLUE);
            say.setOnClickListener(v -> speak(item.es, false));
            Button details = button("التفاصيل", Color.WHITE, BLUE);
            details.setOnClickListener(v -> showWordDetails(item));
            row.addView(say, new LinearLayout.LayoutParams(dp(56), dp(50)));
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, dp(50), 1);
            detailParams.setMargins(dp(8), 0, 0, 0);
            row.addView(details, detailParams);
            space(box, 8);
            full(box, row);
            full(body, box);
            space(body, 10);
        }
        Button practice = button("تدرب على الكلمات دي", GREEN, Color.WHITE);
        practice.setOnClickListener(v -> startItemSession(toSourceItems(weakItems(8)), true));
        full(body, practice);
    }

    private void startFlashcards() {
        ArrayList<Item> due = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Track track : tracks) for (Item item : track.items) {
            long when = prefs.getLong("due_" + item.id, 0);
            if ((when > 0 && when <= now) || prefs.getInt("misses_" + item.id, 0) > 0) due.add(item);
        }
        if (due.isEmpty()) {
            for (Track track : tracks) due.addAll(track.items);
            Collections.shuffle(due);
            while (due.size() > 8) due.remove(due.size() - 1);
            if (due.isEmpty()) { toast("مفيش كروت متاحة."); return; }
        }
        Collections.sort(due, (a, b) -> Long.compare(prefs.getLong("due_" + a.id, 0), prefs.getLong("due_" + b.id, 0)));
        while (due.size() > 10) due.remove(due.size() - 1);
        showFlashcard(due, 0, false);
    }

    private void showFlashcard(ArrayList<Item> cards, int index, boolean revealed) {
        if (index >= cards.size()) {
            showReview();
            toast("خلصت كروت المراجعة.");
            return;
        }
        screen = "flashcard";
        Item item = cards.get(index);
        setup("بطاقة " + (index + 1) + " / " + cards.size(), true, false);
        full(body, progress(index, cards.size()));
        space(body, 16);
        LinearLayout box = card();
        full(box, ltr(item.es, 26, INK, true));
        if (!item.ipa.isEmpty()) full(box, ltr("[" + item.ipa + "]", 14, BLUE, false));
        space(box, 10);
        audioControls(box, item.es);
        space(box, 12);
        if (revealed) {
            full(box, text("فصحى: " + item.ar, 18, INK, true));
            if (item.hasEgy()) full(box, text("مصري: " + item.egy, 18, DARK_GREEN, true));
            if (item.hasExample()) {
                space(box, 8);
                full(box, ltr(item.exEs, 14, MUTED, false));
            }
            space(box, 12);
            full(box, text("افتكرتها إزاي؟", 15, MUTED, false));
            space(box, 6);
            String[] labels = {"لم أتذكر", "صعبة", "كويسة", "سهلة"};
            int[] qualities = {ReviewScheduler.AGAIN, ReviewScheduler.HARD, ReviewScheduler.GOOD, ReviewScheduler.EASY};
            for (int i = 0; i < labels.length; i++) {
                final int quality = qualities[i];
                Button rate = button(labels[i], i == 3 ? GREEN : Color.WHITE, i == 3 ? Color.WHITE : BLUE);
                rate.setOnClickListener(v -> {
                    ReviewScheduler.grade(store, item.id, quality, System.currentTimeMillis());
                    int xp = XpPolicy.reviewReward(store, item.id, todayKey());
                    if (xp > 0) prefs.edit().putInt("xp", prefs.getInt("xp", 0) + xp).apply();
                    showFlashcard(cards, index + 1, false);
                });
                full(box, rate);
                space(box, 6);
            }
        } else {
            Button reveal = button("اظهر المعنى", GREEN, Color.WHITE);
            reveal.setOnClickListener(v -> showFlashcard(cards, index, true));
            full(box, reveal);
        }
        full(body, box);
    }

    // ---------- dictionary ----------

    private void showDictionary() {
        screen = "dictionary";
        dicLimit = 120;
        setup("القاموس", true, false);
        LinearLayout head = card();
        head.setBackground(bg(PALE, 0, 24));
        full(head, text("كل كلمات المنهج في مكان واحد", 20, INK, true));
        space(head, 4);
        full(head, text("ابحث بالإسباني أو بالعربي (فصحى ومصري)، اسمع النطق، واحفظ اللي يعجبك في المفضلة.",
                13, MUTED, false));
        space(head, 10);
        dicSearch = makeInput("اكتب مثل: agua أو ماء", false);
        dicSearch.setSingleLine(true);
        dicSearch.setText(dicQuery);
        dicSearch.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                dicQuery = s.toString().trim();
                dicLimit = 120;
                renderDictionaryList();
            }
        });
        full(head, dicSearch);
        space(head, 10);
        LinearLayout chips = new LinearLayout(this);
        chips.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        ArrayList<Button> chipButtons = new ArrayList<>();
        String[] labels = new String[tracks.size() + 2];
        labels[0] = "الكل";
        labels[1] = "⭐ المفضلة";
        for (int i = 0; i < tracks.size(); i++) labels[i + 2] = tracks.get(i).id;
        for (int i = 0; i < labels.length; i++) {
            final int mode = i;
            Button chip = button(labels[i], Color.WHITE, INK);
            chip.setTextSize(13);
            chip.setMinHeight(dp(38));
            chip.setPadding(dp(12), dp(4), dp(12), dp(4));
            chip.setOnClickListener(v -> {
                dicFavorites = mode == 1;
                dicLevel = mode >= 2 ? tracks.get(mode - 2).id : "";
                dicLimit = 120;
                for (int b = 0; b < chipButtons.size(); b++) chipButtons.get(b).setBackground(bg(b == mode ? SKY : Color.WHITE, b == mode ? BLUE : LINE, 14));
                renderDictionaryList();
            });
            chipButtons.add(chip);
            chips.addView(chip, new LinearLayout.LayoutParams(-2, -2));
            View gap = new View(this);
            chips.addView(gap, new LinearLayout.LayoutParams(dp(6), 1));
        }
        chipScroll.addView(chips);
        full(head, chipScroll);
        full(body, head);
        space(body, 10);
        dicListContainer = column();
        full(body, dicListContainer);
        renderDictionaryList();
    }

    private LinearLayout dicListContainer;

    private Set<String> favorites() {
        Object value = prefs.getAll().get("dictionary_favorites");
        if (value instanceof Set) return new HashSet<>((Set<String>) value);
        return new HashSet<>();
    }

    private void saveFavorites(Set<String> favorites) {
        prefs.edit().putStringSet("dictionary_favorites", new HashSet<>(favorites)).apply();
    }

    private void renderDictionaryList() {
        if (dicListContainer == null) return;
        dicListContainer.removeAllViews();
        Set<String> favorites = favorites();
        List<Curriculum.Item> results = curriculum.search(dicQuery, dicLevel, "", dicFavorites, favorites, 4000);
        int shown = Math.min(results.size(), dicLimit);
        full(dicListContainer, text(results.size() + " نتيجة" + (shown < results.size() ? " — بنعرض " + shown : ""),
                13, MUTED, true));
        space(dicListContainer, 8);
        for (int i = 0; i < shown; i++) {
            Curriculum.Item source = results.get(i);
            Item item = findItemBySource(source.id);
            if (item == null) continue;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(bg(Color.WHITE, LINE, 18));
            LinearLayout copy = column();
            TextView spanishText = ltr(item.es, 17, INK, true);
            full(copy, spanishText);
            full(copy, text("فصحى: " + item.ar, 13, MUTED, false));
            if (item.hasEgy()) full(copy, text("مصري: " + item.egy, 13, DARK_GREEN, false));
            TextView meta = text(item.level + "  •  " + item.pos + (item.ipa.isEmpty() ? "" : "  •  [" + item.ipa + "]"),
                    11, MUTED, false);
            full(copy, meta);
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            copy.setOnClickListener(v -> showWordDetails(item));
            Button star = button(favorites.contains(item.id) ? "★" : "☆", Color.WHITE,
                    favorites.contains(item.id) ? GOLD : MUTED);
            star.setTextSize(19);
            star.setPadding(0, 0, 0, 0);
            star.setContentDescription(favorites.contains(item.id) ? "إزالة من المفضلة" : "أضف للمفضلة");
            star.setOnClickListener(v -> {
                Set<String> now = favorites();
                if (!now.remove(item.id)) now.add(item.id);
                saveFavorites(now);
                renderDictionaryList();
            });
            row.addView(star, new LinearLayout.LayoutParams(dp(46), dp(46)));
            Button say = button("🔊", SKY, BLUE);
            say.setTextSize(15);
            say.setPadding(0, 0, 0, 0);
            say.setOnClickListener(v -> speak(item.es, false));
            row.addView(say, new LinearLayout.LayoutParams(dp(46), dp(46)));
            full(dicListContainer, row);
            space(dicListContainer, 6);
        }
        if (shown < results.size()) {
            Button more = button("عرض المزيد", Color.WHITE, BLUE);
            more.setOnClickListener(v -> { dicLimit += 120; renderDictionaryList(); });
            full(dicListContainer, more);
        }
        if (results.isEmpty()) full(dicListContainer, text("مفيش نتائج مطابقة. جرّب كلمة تانية.", 14, MUTED, false));
    }

    private void showWordDetails(Item item) {
        screen = "word_detail";
        setup("تفاصيل الكلمة", true, false);
        LinearLayout box = card();
        box.setBackground(bg(PALE, 0, 24));
        full(box, ltr(item.es, 26, INK, true));
        if (!item.ipa.isEmpty()) full(box, ltr("[" + item.ipa + "]", 15, BLUE, false));
        space(box, 8);
        full(box, text("الفصحى: " + item.ar, 18, INK, true));
        if (item.hasEgy()) full(box, text("المصري: " + item.egy, 18, DARK_GREEN, true));
        if (!item.noteEgy.isEmpty()) {
            space(box, 6);
            full(box, text(item.noteEgy, 13, MUTED, false));
        }
        if (item.hasExample()) {
            space(box, 10);
            full(box, text("مثال في جملة", 15, INK, true));
            full(box, ltr(item.exEs, 16, INK, false));
            full(box, text("فصحى: " + item.exFusha, 13, MUTED, false));
            full(box, text("مصري: " + item.exEgy, 13, DARK_GREEN, false));
            audioControls(box, item.exEs);
        } else {
            space(box, 10);
            audioControls(box, item.es);
        }
        space(box, 10);
        ReviewScheduler.State state = ReviewScheduler.read(store, item.id);
        full(box, text("مستوى الإتقان: " + state.mastery + "/5  •  محاولات: " + state.attempts
                + "  •  صحيحة: " + state.correct, 13, MUTED, false));
        if (state.dueAt > 0) {
            full(box, text("المراجعة الجاية: "
                    + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(state.dueAt)),
                    13, MUTED, false));
        }
        space(box, 10);
        Button favorite = button(favorites().contains(item.id) ? "★ إزالة من المفضلة" : "☆ أضف للمفضلة",
                Color.WHITE, favorites().contains(item.id) ? GOLD : BLUE);
        favorite.setOnClickListener(v -> {
            Set<String> now = favorites();
            boolean added = now.add(item.id);
            if (!added) now.remove(item.id);
            saveFavorites(now);
            showWordDetails(item);
        });
        full(box, favorite);
        space(box, 8);
        Button practice = button("تدرّب على الكلمة دي", GREEN, Color.WHITE);
        practice.setOnClickListener(v -> {
            Curriculum.Item source = curriculum.item(item.id);
            if (source != null) startItemSession(Collections.singletonList(source), true);
        });
        full(box, practice);
        full(body, box);
        space(body, 10);
        full(body, text("حالة التحقق: " + item.verification
                + "\nالمحتوى مفحوص آليًا للتهجئة والترقيم والتكرار، ولسه محتاج مراجعة لغوية بشرية عشان يتحول لـ verified.",
                12, MUTED, false));
    }

    private void showWordSheet(Item item) {
        LinearLayout panel = column();
        panel.setPadding(dp(16), dp(8), dp(16), dp(8));
        full(panel, ltr(item.es, 22, INK, true));
        if (!item.ipa.isEmpty()) full(panel, ltr("[" + item.ipa + "]", 14, BLUE, false));
        full(panel, text("فصحى: " + item.ar, 15, INK, false));
        if (item.hasEgy()) full(panel, text("مصري: " + item.egy, 15, DARK_GREEN, false));
        if (item.hasExample()) {
            space(panel, 6);
            full(panel, ltr(item.exEs, 14, MUTED, false));
            full(panel, text("مصري: " + item.exEgy, 12, MUTED, false));
            if (!item.exampleSource.isEmpty()) {
                full(panel, text("مصدر المثال: " + exampleSourceLabel(item.exampleSource), 11, MUTED, false));
            }
        }
        full(panel, text(sourceLabel(item), 11, MUTED, false));
        space(panel, 8);
        audioControls(panel, item.es);
        new android.app.AlertDialog.Builder(this).setTitle("القاموس السريع").setView(panel)
                .setPositiveButton("رجوع للتمرين", null).show();
    }

    // ---------- profile / stats / achievements ----------

    private void showProfile() {
        screen = "profile";
        setup("حسابي", false, false);
        LinearLayout box = card();
        box.setBackground(bg(PALE, 0, 24));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        StudyBuddy buddy = new StudyBuddy(this);
        head.addView(buddy, new LinearLayout.LayoutParams(dp(68), dp(68)));
        LinearLayout copy = column();
        full(copy, text("رحلتك في الإسباني", 21, INK, true));
        space(copy, 3);
        full(copy, text("كل تقدمك محفوظ على الجهاز وبيشتغل أوفلاين", 13, MUTED, false));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
        copyParams.setMargins(dp(12), 0, 0, 0);
        head.addView(copy, copyParams);
        full(box, head);
        space(box, 8);
        full(box, text(prefs.getInt("xp", 0) + " نقطة  •  " + prefs.getInt("streak", 0) + " يوم متتالي  •  مستوى "
                + xpLevel() + " (" + XpPolicy.levelTitle(xpLevel()) + ")", 15, DARK_GREEN, true));
        space(box, 6);
        int doneAll = 0;
        for (Track track : tracks) doneAll += doneLessons(track);
        full(box, text(doneAll + " من " + curriculum.totalLessons() + " درس مكتمل  •  "
                + masteredCount() + " عنصر متقن  •  " + favorites().size() + " في المفضلة", 13, MUTED, false));
        full(body, box);
        space(body, 12);
        Button stats = button("📊  إحصائيات التقدم", Color.WHITE, BLUE);
        stats.setOnClickListener(v -> showStats());
        full(body, stats);
        space(body, 8);
        Button achievements = button("🏅  الإنجازات والمكافآت", Color.WHITE, BLUE);
        achievements.setOnClickListener(v -> showAchievements());
        full(body, achievements);
        space(body, 8);
        Button settings = button("⚙  الإعدادات والصوت والنسخ الاحتياطي", Color.WHITE, BLUE);
        settings.setOnClickListener(v -> showSettings());
        full(body, settings);
        space(body, 8);
        Button content = button("ℹ  عن المحتوى وحالة التحقق", Color.WHITE, BLUE);
        content.setOnClickListener(v -> showContentInfo());
        full(body, content);
        space(body, 12);
        stickerShelf(body);
    }

    private void stickerShelf(LinearLayout box) {
        int lessons = 0;
        for (Track track : tracks) lessons += doneLessons(track);
        LinearLayout cardBox = card();
        full(cardBox, text("ملصقات رحلتك 🏅", 17, INK, true));
        space(cardBox, 8);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels = {"أول درس", "١٠ دروس", "سلسلة ٧ أيام"};
        String[] icons = {"🌱", "🗺️", "🔥"};
        boolean[] unlocked = {lessons >= 1, lessons >= 10, prefs.getInt("streak", 0) >= 7};
        for (int i = 0; i < labels.length; i++) {
            LinearLayout badge = column();
            badge.setGravity(Gravity.CENTER);
            TextView icon = text(unlocked[i] ? icons[i] : "🔒", 24, unlocked[i] ? GOLD : MUTED, true);
            icon.setGravity(Gravity.CENTER);
            badge.addView(icon, new LinearLayout.LayoutParams(-1, dp(40)));
            TextView caption = text(labels[i], 11, unlocked[i] ? INK : MUTED, unlocked[i]);
            caption.setGravity(Gravity.CENTER);
            badge.addView(caption);
            row.addView(badge, new LinearLayout.LayoutParams(0, -2, 1));
        }
        full(cardBox, row);
        full(box, cardBox);
    }

    private void showStats() {
        screen = "stats";
        setup("إحصائياتك", true, false);
        int streak = prefs.getInt("streak", 0), xp = prefs.getInt("xp", 0);
        int firstC = prefs.getInt("total_first_correct", 0), firstA = prefs.getInt("total_first_attempted", 0);
        int lessonsDone = 0;
        for (Track track : tracks) lessonsDone += doneLessons(track);
        LinearLayout hero = card();
        hero.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{DARK_GREEN, GREEN}));
        full(hero, text("🔥  " + streak + " يوم متتالي", 22, Color.WHITE, true));
        space(hero, 6);
        full(hero, text("✦  " + xp + " نقطة  •  " + lessonsDone + " درس مكتمل  •  مستوى " + xpLevel(),
                14, Color.rgb(221, 245, 234), true));
        full(body, hero);
        space(body, 12);

        LinearLayout accuracy = card();
        full(accuracy, text("دقة الإجابات", 17, INK, true));
        space(accuracy, 6);
        full(accuracy, accuracyRing(firstC, firstA));
        full(body, accuracy);
        space(body, 12);

        LinearLayout vocab = card();
        full(vocab, text("حصيلتك", 17, INK, true));
        space(vocab, 6);
        full(vocab, text(masteredCount() + " عنصر متقن (مستوى إتقان ٣ من ٥ أو أكتر)", 14, INK, false));
        full(vocab, text(dueCount() + " عنصر مستحق للمراجعة الآن", 14, INK, false));
        full(vocab, text(weakCount() + " عنصر فيه أخطاء مسجلة", 14, INK, false));
        int[] mastery = new int[6];
        for (String id : allItemIds()) mastery[Math.max(0, Math.min(5, prefs.getInt("mastery_" + id, 0)))]++;
        for (int level = 0; level <= 5; level++) {
            if (mastery[level] == 0) continue;
            full(vocab, text("مستوى إتقان " + level + ": " + mastery[level] + " عنصر", 13, MUTED, false));
            full(vocab, progress(mastery[level], Math.max(1, allItemIds().size())));
            space(vocab, 4);
        }
        full(body, vocab);
        space(body, 12);

        LinearLayout perLevel = card();
        full(perLevel, text("التقدم حسب المستوى", 17, INK, true));
        space(perLevel, 6);
        for (Track track : tracks) {
            int done = doneLessons(track);
            full(perLevel, text(track.id + " — " + track.title.replace(track.id + " — ", ""), 15, INK, true));
            full(perLevel, text(done + "/" + track.lessons.size() + " درس  •  " + learnedItemCount(track)
                    + "/" + track.items.size() + " عنصر", 12, MUTED, false));
            full(perLevel, progress(done, Math.max(1, track.lessons.size())));
            space(perLevel, 8);
        }
        full(body, perLevel);
        space(body, 12);

        LinearLayout week = card();
        full(week, text("آخر ٧ أيام", 17, INK, true));
        space(week, 8);
        String[] labels = new String[7];
        int[] values = new int[7];
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar day = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            String key = format.format(day.getTime());
            labels[6 - i] = key.substring(5);
            values[6 - i] = prefs.getInt("acc_" + key + "_a", 0);
            day.add(Calendar.DAY_OF_YEAR, -1);
        }
        DesignSystem.BarChartView chart = new DesignSystem.BarChartView(this, labels, values,
                foreground(GREEN), surface(LINE), foreground(INK), foreground(MUTED), cairoRegular);
        week.addView(chart, new LinearLayout.LayoutParams(-1, dp(140)));
        full(body, week);
        space(body, 12);
        Button achievements = button("🏅  الإنجازات", Color.WHITE, BLUE);
        achievements.setOnClickListener(v -> showAchievements());
        full(body, achievements);
    }

    private LinearLayout accuracyRing(int correctCount, int attempts) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int percent = attempts <= 0 ? 0 : Math.min(100, correctCount * 100 / attempts);
        DesignSystem.RingProgressView ring = new DesignSystem.RingProgressView(this, percent,
                attempts == 0 ? "—" : percent + "٪", "دقة أول محاولة", foreground(GREEN), surface(LINE),
                foreground(INK), foreground(MUTED), cairoBold, nunitoRegular);
        row.addView(ring, new LinearLayout.LayoutParams(dp(88), dp(88)));
        LinearLayout copy = column();
        full(copy, text("إجابات صحيحة من أول محاولة", 14, INK, true));
        space(copy, 4);
        full(copy, text(correctCount + " من " + attempts, 13, MUTED, false));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
        copyParams.setMargins(dp(12), 0, 0, 0);
        row.addView(copy, copyParams);
        return row;
    }

    private void showAchievements() {
        screen = "achievements";
        setup("الإنجازات", true, false);
        int lessons = 0;
        for (Track track : tracks) lessons += doneLessons(track);
        int xp = prefs.getInt("xp", 0);
        int streak = prefs.getInt("streak", 0);
        int mastered = masteredCount();
        int dialogues = prefs.getStringSet("done_dialogues", new HashSet<>()).size();
        String[][] badges = {
                {"🌱", "البداية", "أكمل أول درس", String.valueOf(lessons), "1"},
                {"🗺️", "مستكشف", "١٠ دروس مكتملة", String.valueOf(lessons), "10"},
                {"🏛️", "مثابر", "٥٠ درس مكتمل", String.valueOf(lessons), "50"},
                {"🔥", "سلسلة أسبوع", "٧ أيام متتالية", String.valueOf(streak), "7"},
                {"⭐", "جامع النقاط", "٥٠٠ نقطة", String.valueOf(xp), "500"},
                {"🧠", "حافظ", "٥٠ عنصر متقن", String.valueOf(mastered), "50"},
                {"☎️", "محترف الكول سنتر", "حوار خدمة عملاء واحد", String.valueOf(dialogues), "1"},
                {"🎙️", "متحدث", "١٠٠ عنصر متقن", String.valueOf(mastered), "100"},
        };
        for (String[] badge : badges) {
            int have = parseIntSafe(badge[3]);
            int need = parseIntSafe(badge[4]);
            boolean unlocked = have >= need;
            LinearLayout box = card();
            box.setBackground(bg(unlocked ? PALE : Color.WHITE, LINE, 20));
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView icon = text(unlocked ? badge[0] : "🔒", 26, unlocked ? GOLD : MUTED, true);
            icon.setGravity(Gravity.CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));
            LinearLayout copy = column();
            full(copy, text(badge[1], 17, INK, true));
            full(copy, text(badge[2], 13, MUTED, false));
            full(copy, text(unlocked ? "تم ✓" : Math.min(have, need) + " / " + need, 12,
                    unlocked ? DARK_GREEN : MUTED, true));
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
            copyParams.setMargins(dp(10), 0, 0, 0);
            row.addView(copy, copyParams);
            full(box, row);
            full(body, box);
            space(body, 8);
        }
        full(body, text("المكافآت بتتحسب من نشاطك الفعلي؛ مفيش نقاط مضاعفة لإعادة نفس الدرس.", 12, MUTED, false));
    }

    private int parseIntSafe(String value) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return 0; }
    }

    private void showContentInfo() {
        screen = "content_info";
        setup("عن المحتوى", true, false);
        LinearLayout box = card();
        full(box, text("Spanish 1000 — محتوى المنهج", 19, INK, true));
        space(box, 6);
        full(box, text(curriculum.summaryLine(), 15, INK, true));
        space(box, 4);
        full(box, text("تاريخ توليد المحتوى: " + curriculum.generated, 13, MUTED, false));
        for (Track track : tracks) {
            full(box, text(track.id + ": " + track.items.size() + " عنصر • " + track.lessons.size()
                    + " درس • " + track.units.size() + " وحدة", 13, MUTED, false));
        }
        space(box, 8);
        full(box, text("حالات التحقق المستخدمة: pending (مسودة) • machine_checked (فحص آلي) • "
                + "source_checked (مصدر مستقل) • sound_verified (تحقق نطق) • verified (مراجعة نهائية).", 12, MUTED, false));
        space(box, 6);
        full(box, text("كل عناصر النسخة الحالية حالتها machine_checked: اتفحصت آليًا من ناحية التهجئة والترقيم "
                + "والتكرار ووجود ترجمة فصحى ومصرية، لكن مفيش عنصر verified بمراجعة بشرية أو مصدر مستقل لكل مدخل.", 12, MUTED, false));
        full(body, box);
        space(body, 10);
        Button sources = button("المصادر والقواعد المستخدمة", Color.WHITE, BLUE);
        sources.setOnClickListener(v -> {
            LinearLayout panel = column();
            panel.setPadding(dp(16), dp(8), dp(16), dp(8));
            String[] lines = {
                    "• قواعد النطق والتهجئة: قواعد الأكاديمية الملكية الإسبانية (RAE) للأصوات والترقيم وعلامتي ¿ ¡.",
                    "• تحويل الحروف لرموز النطق (IPA) بيتم آليًا بقواعد RAE المعروفة، وموسوم machine_checked مش sound_verified.",
                    "• الترجمات العربية (فصحى ومصرية) مكتوبة يدويًا داخل المشروع، ولسه محتاجة مراجعة لغوية مستقلة.",
                    "• الصوت: محرك TTS في الجهاز بصوت es-ES. مفيش تسجيلات بشرية ولا ادعاء بوجودها.",
                    "• التعرف على الكلام: خدمة الجهاز فقط للتحدث، والتقييم تقريبي.",
            };
            for (String line : lines) {
                full(panel, text(line, 14, INK, false));
                space(panel, 6);
            }
            new android.app.AlertDialog.Builder(this).setTitle("المصادر").setView(panel)
                    .setPositiveButton("تمام", null).show();
        });
        full(body, sources);
    }

    // ---------- settings ----------

    private void showSettings() {
        screen = "settings";
        setup("الإعدادات", true, false);
        LinearLayout audio = card();
        full(audio, text("🔊 الصوت", 18, INK, true));
        space(audio, 6);
        full(audio, text(audioStatus(), 14, ttsReady ? DARK_GREEN : RED, true));
        space(audio, 6);
        full(audio, text("التشغيل بيستخدم TTS الجهاز. الإيقاف قبل كل جملة يمنع تداخل الأصوات، والسرعة العادية 0.92× والبطيئة حوالي 0.64×.",
                12, MUTED, false));
        space(audio, 8);
        full(audio, text("السرعة", 15, INK, true));
        final TextView speedValue = text(String.format(Locale.US, "%.2f×", prefs.getFloat("tts_rate", 0.92f)), 15, GREEN, true);
        full(audio, speedValue);
        android.widget.SeekBar seek = new android.widget.SeekBar(this);
        seek.setMax(20);
        seek.setProgress(Math.round((prefs.getFloat("tts_rate", 0.92f) - 0.5f) * 20));
        seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar bar, int value, boolean fromUser) {
                float rate = 0.5f + value * 0.05f;
                speedValue.setText(String.format(Locale.US, "%.2f×", rate));
                if (fromUser) prefs.edit().putFloat("tts_rate", rate).apply();
            }
            public void onStartTrackingTouch(android.widget.SeekBar bar) {}
            public void onStopTrackingTouch(android.widget.SeekBar bar) {}
        });
        full(audio, seek);
        space(audio, 8);
        LinearLayout audioRow = new LinearLayout(this);
        Button test = button("اسمّع تجربة", SKY, BLUE);
        Button testSlow = button("تجربة ببطء", Color.WHITE, BLUE);
        test.setOnClickListener(v -> speak("Hola, ¿cómo estás? Bienvenido a Spanish 1000.", false));
        testSlow.setOnClickListener(v -> speak("Hola, ¿cómo estás? Bienvenido a Spanish 1000.", true));
        audioRow.addView(test, new LinearLayout.LayoutParams(0, dp(50), 1));
        LinearLayout.LayoutParams slowParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        slowParams.setMargins(dp(8), 0, 0, 0);
        audioRow.addView(testSlow, slowParams);
        full(audio, audioRow);
        space(audio, 8);
        full(audio, text("لو الصوت مش شغال: نزّل حزمة «Spanish (Spain)» من إعدادات تحويل النص إلى كلام في الهاتف. "
                + "المحتوى والتمارين الكتابية بتعمل عادي بدون صوت.", 12, MUTED, false));
        full(body, audio);
        space(body, 12);

        LinearLayout learning = card();
        full(learning, text("🎯 التعلم", 18, INK, true));
        space(learning, 8);
        int goal = Math.max(1, prefs.getInt("daily_goal", 1));
        final TextView goalValue = text(goal + " درس يوميًا", 15, GREEN, true);
        full(learning, goalValue);
        LinearLayout goalRow = new LinearLayout(this);
        Button less = button("−", Color.WHITE, BLUE);
        Button more = button("+", Color.WHITE, BLUE);
        less.setOnClickListener(v -> {
            prefs.edit().putInt("daily_goal", Math.max(1, prefs.getInt("daily_goal", 1) - 1)).apply();
            goalValue.setText(prefs.getInt("daily_goal", 1) + " درس يوميًا");
        });
        more.setOnClickListener(v -> {
            prefs.edit().putInt("daily_goal", Math.min(10, prefs.getInt("daily_goal", 1) + 1)).apply();
            goalValue.setText(prefs.getInt("daily_goal", 1) + " درس يوميًا");
        });
        goalRow.addView(less, new LinearLayout.LayoutParams(0, dp(48), 1));
        goalRow.addView(more, new LinearLayout.LayoutParams(0, dp(48), 1));
        full(learning, goalRow);
        space(learning, 8);
        Button theme = button(dark() ? "🌙 الوضع الداكن مفعّل" : "☀️ الوضع الفاتح مفعّل", Color.WHITE, BLUE);
        theme.setOnClickListener(v -> {
            prefs.edit().putBoolean("dark_mode", !dark()).apply();
            showSettings();
        });
        full(learning, theme);
        space(learning, 8);
        Button unlock = button(unlockAll() ? "🔓 إلغاء قفل المستويات مفعّل" : "🔒 ترتيب المستويات مقفول",
                Color.WHITE, BLUE);
        unlock.setOnClickListener(v -> {
            prefs.edit().putBoolean("unlock_all", !unlockAll()).apply();
            showSettings();
        });
        full(learning, unlock);
        full(body, learning);
        space(body, 12);

        LinearLayout reminders = card();
        full(reminders, text("🔔 التذكير اليومي", 18, INK, true));
        space(reminders, 6);
        boolean on = prefs.getBoolean("reminders_enabled", false);
        Button toggle = button(on ? "المذكّرات مفعّلة — اضغط للإيقاف" : "المذكّرات موقوفة — اضغط للتفعيل",
                on ? PALE : Color.WHITE, on ? DARK_GREEN : BLUE);
        toggle.setOnClickListener(v -> {
            if (prefs.getBoolean("reminders_enabled", false)) {
                prefs.edit().putBoolean("reminders_enabled", false).apply();
                ReminderReceiver.cancel(this);
                toast("أوقفنا التذكير.");
                showSettings();
                return;
            }
            prefs.edit().putBoolean("reminders_enabled", true).apply();
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFY_PERMISSION);
                return;
            }
            ReminderReceiver.scheduleNext(this);
            toast("هنذكّرك لو ماكملتش هدفك.");
            showSettings();
        });
        full(reminders, toggle);
        full(body, reminders);
        space(body, 12);

        LinearLayout backup = card();
        full(backup, text("💾 النسخ الاحتياطي", 18, INK, true));
        space(backup, 6);
        full(backup, text("صدّر تقدمك كملف JSON قبل حذف التطبيق أو تغيير الهاتف.", 13, MUTED, false));
        space(backup, 8);
        LinearLayout backupRow = new LinearLayout(this);
        Button export = button("📤 تصدير", GREEN, Color.WHITE);
        Button importButton = button("📥 استيراد", Color.WHITE, BLUE);
        export.setOnClickListener(v -> exportProgress());
        importButton.setOnClickListener(v -> importProgressPicker());
        backupRow.addView(export, new LinearLayout.LayoutParams(0, dp(50), 1));
        backupRow.addView(importButton, new LinearLayout.LayoutParams(0, dp(50), 1));
        full(backup, backupRow);
        space(backup, 8);
        Button reset = button("🗑 حذف كل التقدم (لا يمكن الرجوع)", Color.WHITE, RED);
        reset.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("حذف التقدم؟")
                .setMessage("هيتم حذف النقاط والمراجعة وكل الدروس المكتملة من هذا الجهاز. المحتوى نفسه مش هيتأثر.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("احذف", (dialog, which) -> {
                    prefs.edit().clear().apply();
                    toast("تم حذف التقدم.");
                    home();
                }).show());
        full(backup, reset);
        full(body, backup);
    }

    private String encodePref(Object value) {
        if (value instanceof Boolean) return "b:" + value;
        if (value instanceof Integer) return "i:" + value;
        if (value instanceof Long) return "l:" + value;
        if (value instanceof Float) return "f:" + value;
        if (value instanceof Set) return "set:" + new JSONArray((Set<?>) value).toString();
        return "s:" + value;
    }

    private String progressSnapshot() throws Exception {
        JSONObject data = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) data.put(entry.getKey(), encodePref(entry.getValue()));
        return data.toString();
    }

    private void exportProgress() {
        try {
            pendingExport = progressSnapshot();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "habla-backup.json");
            startActivityForResult(intent, EXPORT_REQUEST);
        } catch (Exception e) {
            toast("تعذر تجهيز النسخة الاحتياطية.");
        }
    }

    private void importProgressPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, IMPORT_REQUEST);
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXPORT_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (pendingExport == null) pendingExport = progressSnapshot();
                out.write(pendingExport.getBytes("UTF-8"));
                toast("تم تصدير تقدمك.");
            } catch (Exception e) {
                toast("تعذر كتابة الملف.");
            }
            pendingExport = null;
        } else if (requestCode == IMPORT_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (bytes.size() > MAX_BACKUP_BYTES - read) throw new IllegalArgumentException("backup too large");
                    bytes.write(buffer, 0, read);
                }
                JSONObject parsed = new JSONObject(bytes.toString("UTF-8"));
                Map<String, Object> decoded = new LinkedHashMap<>();
                Iterator<String> keys = parsed.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object raw = parsed.get(key);
                    if (!(raw instanceof String)) throw new IllegalArgumentException("invalid value");
                    String encoded = (String) raw;
                    Object value;
                    if (encoded.startsWith("b:")) value = Boolean.valueOf(encoded.substring(2));
                    else if (encoded.startsWith("i:")) value = Integer.valueOf(encoded.substring(2));
                    else if (encoded.startsWith("l:")) value = Long.valueOf(encoded.substring(2));
                    else if (encoded.startsWith("f:")) value = Float.parseFloat(encoded.substring(2));
                    else if (encoded.startsWith("set:")) {
                        JSONArray array = new JSONArray(encoded.substring(4));
                        Set<String> values = new HashSet<>();
                        for (int i = 0; i < array.length(); i++) values.add(array.getString(i));
                        value = values;
                    } else if (encoded.startsWith("s:")) value = encoded.substring(2);
                    else throw new IllegalArgumentException("invalid type");
                    if (!LearningLogic.validPreferenceType(key, value)) throw new IllegalArgumentException("bad key " + key);
                    decoded.put(key, value);
                }
                SharedPreferences.Editor edit = prefs.edit();
                for (Map.Entry<String, Object> entry : decoded.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                    else if (value instanceof Integer) edit.putInt(key, (Integer) value);
                    else if (value instanceof Long) edit.putLong(key, (Long) value);
                    else if (value instanceof Float) edit.putFloat(key, (Float) value);
                    else if (value instanceof Set) edit.putStringSet(key, (Set<String>) value);
                    else edit.putString(key, (String) value);
                }
                if (!edit.commit()) throw new IllegalStateException("could not save");
                toast("تمت استعادة التقدم (" + decoded.size() + " مفتاح).");
                home();
            } catch (Exception e) {
                toast("الملف مش نسخة احتياطية صالحة من التطبيق.");
            }
        }
    }

    // ---------- call centre & dialogues ----------

    private void showCallCenter() {
        screen = "call_center";
        setup("كول سنتر وخدمة عملاء", true, false);
        LinearLayout head = card();
        head.setBackground(bg(SKY, 0, 24));
        full(head, text("☎️  اتصل وتكلم بالإسباني", 20, INK, true));
        space(head, 6);
        full(head, text("محادثات خدمة عملاء حقيقية: بنك، تليفون، إنترنت، حجز، وشكوى. "
                + "كل حوار فيه جمل بصوت إسباني وأسئلة فهم.", 13, MUTED, false));
        full(body, head);
        space(body, 12);
        List<Curriculum.Dialogue> dialogues = curriculum.dialoguesInSection("call_center");
        if (dialogues.isEmpty()) {
            full(body, text("مفيش حوارات كول سنتر في النسخة الحالية من المحتوى.", 14, MUTED, false));
            return;
        }
        Set<String> done = prefs.getStringSet("done_dialogues", new HashSet<>());
        for (Curriculum.Dialogue dialogue : dialogues) {
            LinearLayout box = card();
            full(box, text(dialogue.titleAr.isEmpty() ? dialogue.titleEgy : dialogue.titleAr, 17, INK, true));
            space(box, 4);
            full(box, ltr(dialogue.titleEs, 13, BLUE, false));
            space(box, 4);
            full(box, text(dialogue.contextEgy, 13, MUTED, false));
            space(box, 6);
            full(box, text(dialogue.level + "  •  " + dialogue.lines.size() + " جملة  •  "
                    + dialogue.questions.size() + " سؤال فهم" + (done.contains(dialogue.id) ? "  •  ✓ تم" : ""),
                    12, MUTED, false));
            space(box, 8);
            LinearLayout row = new LinearLayout(this);
            Button open = button("اقرأ واسمع", Color.WHITE, BLUE);
            Button practice = button("تدرّب", GREEN, Color.WHITE);
            open.setOnClickListener(v -> showDialogue(dialogue));
            practice.setOnClickListener(v -> startDialogueLesson(dialogue));
            row.addView(open, new LinearLayout.LayoutParams(0, dp(50), 1));
            LinearLayout.LayoutParams practiceParams = new LinearLayout.LayoutParams(0, dp(50), 1);
            practiceParams.setMargins(dp(8), 0, 0, 0);
            row.addView(practice, practiceParams);
            full(box, row);
            full(body, box);
            space(body, 10);
        }
        full(body, text("ملاحظة: النطق بيستخدم TTS الجهاز. مفيش تسجيلات بشرية هنا، والتقييم مش تحليل صوتي احترافي.",
                12, MUTED, false));
    }

    private void showDialogue(Curriculum.Dialogue dialogue) {
        screen = "dialogue";
        setup(dialogue.titleEgy.isEmpty() ? dialogue.titleAr : dialogue.titleEgy, true, false);
        LinearLayout intro = card();
        intro.setBackground(bg(SKY, 0, 20));
        full(intro, text(dialogue.contextEgy, 14, INK, false));
        if (!dialogue.contextFusha.isEmpty()) {
            space(intro, 4);
            full(intro, text("بالفصحى: " + dialogue.contextFusha, 12, MUTED, false));
        }
        full(body, intro);
        space(body, 12);
        for (Curriculum.Line line : dialogue.lines) {
            LinearLayout bubble = card();
            boolean customer = line.who.contains("عميل") || line.who.toLowerCase(Locale.ROOT).contains("cliente");
            bubble.setBackground(bg(customer ? PALE : SKY, 0, 20));
            full(bubble, text(line.who, 12, customer ? DARK_GREEN : BLUE, true));
            space(bubble, 4);
            full(bubble, ltr(line.es, 18, INK, true));
            full(bubble, text("فصحى: " + line.ar, 13, MUTED, false));
            full(bubble, text("مصري: " + line.egy, 13, DARK_GREEN, false));
            space(bubble, 6);
            audioControls(bubble, line.es);
            full(body, bubble);
            space(body, 8);
        }
        Button practice = button("تدرّب على أسئلة الحوار", GREEN, Color.WHITE);
        practice.setOnClickListener(v -> startDialogueLesson(dialogue));
        full(body, practice);
    }

    private void renderDialogueLines(LinearLayout box, Curriculum.Dialogue dialogue) {
        full(box, text(dialogue.titleEgy.isEmpty() ? dialogue.titleAr : dialogue.titleEgy, 15, INK, true));
        space(box, 6);
        for (int i = 0; i < dialogue.lines.size(); i++) {
            Curriculum.Line line = dialogue.lines.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView who = text(line.who + ":", 12, BLUE, true);
            row.addView(who);
            TextView phrase = ltr(" " + line.es, 14, INK, false);
            row.addView(phrase, new LinearLayout.LayoutParams(0, -2, 1));
            Button say = button("🔊", Color.WHITE, BLUE);
            say.setTextSize(12);
            say.setPadding(0, 0, 0, 0);
            say.setOnClickListener(v -> speak(line.es, false));
            row.addView(say, new LinearLayout.LayoutParams(dp(40), dp(38)));
            full(box, row);
            if (i < dialogue.lines.size() - 1) space(box, 4);
        }
    }

    private void startDialogueLesson(Curriculum.Dialogue dialogue) {
        Curriculum.Lesson lesson = curriculum.lessonsById.get(dialogue.id + "_lesson");
        if (lesson == null) {
            toast("مفيش درس مرتبط بالحوار ده.");
            return;
        }
        activeLesson = new Lesson();
        activeLesson.id = lesson.id;
        activeLesson.title = lesson.title;
        activeLesson.ref = lesson;
        activeLesson.dialogueId = dialogue.id;
        activeDialogue = dialogue;
        beginSession(LessonPlanner.plan(curriculum, lesson), false, dialogue.id);
    }

    // ---------- real life scenarios ----------

    private void showScenarios() {
        screen = "scenarios";
        setup("مواقف الحياة اليومية", true, false);
        full(body, text("مواقف من إسبانيا الحقيقية: كل موقف فيه عباراتك بالترتيب، مع النطق والمعنى بالفصحى والمصري.",
                14, MUTED, false));
        space(body, 12);
        for (int i = 0; i < RealLife.SCENARIOS.length; i++) {
            final int index = i;
            String[] scenario = RealLife.SCENARIOS[index];
            LinearLayout box = card();
            full(box, text(scenario[0], 18, INK, true));
            space(box, 4);
            full(box, text(scenario[1], 13, MUTED, false));
            space(box, 8);
            Button open = button("افتح الموقف وتدرّب", GREEN, Color.WHITE);
            open.setOnClickListener(v -> showScenarioDetails(index));
            full(box, open);
            full(body, box);
            space(body, 10);
        }
    }

    private void showScenarioDetails(int index) {
        String[] scenario = RealLife.SCENARIOS[index];
        screen = "scenario_detail";
        setup(scenario[0], true, false);
        full(body, text(scenario[1], 14, MUTED, false));
        space(body, 10);
        ArrayList<Curriculum.Item> practiceItems = new ArrayList<>();
        for (int i = 2; i + 1 < scenario.length; i += 2) {
            String phrase = scenario[i];
            String prompt = scenario[i + 1];
            Curriculum.Item source = curriculum.findBySpanish(phrase);
            LinearLayout bubble = card();
            bubble.setBackground(bg(SKY, 0, 20));
            full(bubble, text(prompt, 13, MUTED, false));
            space(bubble, 5);
            full(bubble, ltr(phrase, 19, INK, true));
            if (source != null) {
                full(bubble, text("فصحى: " + source.ar, 13, INK, false));
                if (!source.egy.isEmpty()) full(bubble, text("مصري: " + source.egy, 13, DARK_GREEN, false));
                practiceItems.add(source);
            } else {
                full(bubble, text("العبارة دي مش في المنهج المحمّل بعد — تقدر تسمعها بس.", 12, MUTED, false));
            }
            space(bubble, 6);
            audioControls(bubble, phrase);
            full(body, bubble);
            space(body, 8);
        }
        if (!practiceItems.isEmpty()) {
            Button practice = button("تدرّب على عبارات الموقف", GREEN, Color.WHITE);
            practice.setOnClickListener(v -> startItemSession(practiceItems, true));
            full(body, practice);
        }
    }

    // ---------- grammar ----------

    private void showGrammar() {
        screen = "grammar";
        setup("قواعد بالعربي المصري", true, false);
        full(body, text("قواعد مختصرة مربوطة بالجمل اللي بتتمرن عليها، مش حفظ نظري.", 14, MUTED, false));
        space(body, 12);
        if (!curriculum.grammar.isEmpty()) {
            for (Curriculum.GrammarRule rule : curriculum.grammar) {
                LinearLayout box = card();
                full(box, text(rule.titleAr, 18, DARK_GREEN, true));
                space(box, 4);
                full(box, text(rule.level, 12, MUTED, true));
                space(box, 6);
                full(box, text(rule.ruleEgy, 14, INK, false));
                if (!rule.ruleFusha.isEmpty()) {
                    space(box, 4);
                    full(box, text("بالفصحى: " + rule.ruleFusha, 12, MUTED, false));
                }
                for (String[] example : rule.examples) {
                    space(box, 6);
                    full(box, ltr(example[0], 15, INK, true));
                    full(box, text("فصحى: " + example[1], 12, MUTED, false));
                    if (example.length > 2 && !example[2].isEmpty()) {
                        full(box, text("مصري: " + example[2], 12, DARK_GREEN, false));
                    }
                }
                full(body, box);
                space(body, 10);
            }
        }
        for (String[] tip : GrammarTips.TIPS) {
            LinearLayout box = card();
            full(box, text(tip[0], 17, BLUE, true));
            space(box, 5);
            full(box, text(tip[1], 14, INK, false));
            full(body, box);
            space(body, 10);
        }
    }

    // ---------- navigation ----------

    @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (Build.VERSION.SDK_INT < 33 && keyCode == android.view.KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            navigateBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void navigateBack() {
        stopRecognizer();
        stopSpeaking();
        switch (screen) {
            case "home": finish(); break;
            case "exercise": {
                toast("خرجت من الدرس من غير حفظ نتيجته.");
                if (reviewMode) home(); else path();
                break;
            }
            case "summary": home(); break;
            case "levels": home(); break;
            case "path": home(); break;
            case "review": home(); break;
            case "flashcard": showReview(); break;
            case "weak": showReview(); break;
            case "dictionary": home(); break;
            case "word_detail": showDictionary(); break;
            case "stats": showProfile(); break;
            case "achievements": showProfile(); break;
            case "content_info": showProfile(); break;
            case "settings": showProfile(); break;
            case "call_center": home(); break;
            case "dialogue": showCallCenter(); break;
            case "scenarios": home(); break;
            case "scenario_detail": showScenarios(); break;
            case "grammar": home(); break;
            case "profile": home(); break;
            default: home();
        }
    }

    @Override protected void onStop() {
        stopRecognizer();
        stopSpeaking();
        super.onStop();
    }

    @Override protected void onDestroy() {
        stopRecognizer();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }
}
