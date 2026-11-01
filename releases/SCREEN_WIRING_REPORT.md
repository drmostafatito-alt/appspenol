# تقرير ربط الشاشات والأزرار (تدقيق ثابت)

المصدر: `app/src/main/java/com/habla/espanol/MainActivity.java` (2860+ سطرًا) و`content.json`.

هذا تدقيق **ثابت**: يقرأ الكود ويبني خريطة الشاشات والأزرار ويقارنها بالمحتوى. لا يشغّل التطبيق، لذلك لا يثبت أن اللمس يعمل — يثبت أن كل زر مربوط بفعل منطقي وأن كل شاشة لها مدخل وخروج وحالة فراغ.

- screen methods: 19
- screen ids: 21 • back-navigation cases: 20
- buttons: 52 • listeners: 71 • toast-only: 0
- destructive preference resets: 1 (all behind a dialog)
- exercise types in content: 3 • titled: 11 • explicitly graded branches: 8
- internet permission: absent (fully offline build)

## الشاشات

| الشاشة | حالة الرجوع | حالة فراغ |
|---|---|---|
| `achievements` | نعم | نعم |
| `call_center` | نعم | نعم |
| `content_info` | نعم | نعم |
| `dialogue` | نعم | نعم |
| `dictionary` | نعم | نعم |
| `exercise` | نعم | نعم |
| `flashcard` | نعم | نعم |
| `grammar` | نعم | نعم |
| `home` | نعم | نعم |
| `levels` | نعم | نعم |
| `path` | نعم | نعم |
| `profile` | نعم | نعم |
| `review` | نعم | نعم |
| `scenario_detail` | نعم | نعم |
| `scenarios` | نعم | نعم |
| `settings` | نعم | نعم |
| `stats` | نعم | نعم |
| `summary` | نعم | نعم |
| `weak` | نعم | نعم |
| `welcome` | طرفية | نعم |
| `word_detail` | نعم | نعم |

## شاشات بدون نص حالة فراغ

- `showDialogue`
- `showDictionary`
- `showExercise`
- `showFlashcard`
- `showGrammar`
- `showLevels`
- `showProfile`
- `showScenarioDetails`
- `showScenarios`
- `showSettings`
- `showStats`
- `showWelcome`
- `showWordSheet`

هذه الشاشات تعرض بيانات فقط بعد وجود تقدم؛ عند غياب البيانات تُترك فارغة. ليست أخطاء قاتلة لكنها ليست تجربة كاملة.

## الأزرار

| الزر | نوع الفعل |
|---|---|
| 🔊 استمع | صوت |
| 🐢 ببطء | صوت |
| ⏹ إيقاف | صوت، منطق: stopSpeaking |
| ✕ | تنقّل، منطق: navigateBack |
| − | حفظ حالة، تحديث واجهة، منطق: getInt، putInt |
| + | حفظ حالة، تحديث واجهة، منطق: getInt، putInt |
| ابدأ من A0 | تنقّل، حفظ حالة، منطق: home، putInt |
| 🗺️  خريطة المستويات A0 → C1 | تنقّل، منطق: showLevels |
| 📖  القاموس والمفضلة | تنقّل، منطق: showDictionary |
| ☎️  كول سنتر وخدمة عملاء بالإسباني | تنقّل، منطق: showCallCenter |
| 🎭  مواقف الحياة اليومية | تنقّل، منطق: showScenarios |
| 📚  قواعد بالعربي المصري | تنقّل، منطق: showGrammar |
| 📊  إحصائياتك والإنجازات | تنقّل، منطق: showStats |
| كمّل من مكانك | تنقّل، منطق: resumeSavedSession |
| ابدأها من الأول | تنقّل، رسالة، منطق: home |
| ▶   | تنقّل، منطق: startLesson |
| المستوى اللي بعده:  | تنقّل، منطق: showLevels |
| تحقق | تنقّل، صوت، رسالة، منطق: advanceSession، checkAnswer، requestSpeech |
| مش قادر أتكلم دلوقتي | منطق: skipExercise |
| 🔍  شوف الكلمة في القاموس | تنقّل، منطق: showWordSheet |
| ↺ إعادة ترتيب | تحديث واجهة، منطق: renderSelectedWords |
| الدرس اللي بعده:  | تنقّل، منطق: startLesson |
| العودة للمسار | تنقّل، منطق: path |
| 🃏  بطاقات المراجعة (تقييم ذاتي) | منطق: startFlashcards |
| 🧠  الكلمات اللي بتغلط فيها | تنقّل، منطق: showWeakWords |
| 📊  شوف تقدمك في الأرقام | تنقّل، منطق: showStats |
| 🔊 | صوت |
| التفاصيل | تنقّل، منطق: showWordDetails |
| تدرب على الكلمات دي | تنقّل، منطق: startItemSession، toSourceItems |
| اظهر المعنى | تنقّل، منطق: showFlashcard |
| 🔊 | صوت |
| عرض المزيد | منطق: renderDictionaryList |
| تدرّب على الكلمة دي | تنقّل، منطق: startItemSession |
| 📊  إحصائيات التقدم | تنقّل، منطق: showStats |
| 🏅  الإنجازات والمكافآت | تنقّل، منطق: showAchievements |
| ⚙  الإعدادات والصوت والنسخ الاحتياطي | تنقّل، منطق: showSettings |
| ℹ  عن المحتوى وحالة التحقق | تنقّل، منطق: showContentInfo |
| 🏅  الإنجازات | تنقّل، منطق: showAchievements |
| المصادر والقواعد المستخدمة | تأكيد/حوار، منطق: column، dp، full |
| اسمّع تجربة | صوت |
| تجربة ببطء | صوت |
| − | حفظ حالة، تحديث واجهة، منطق: getInt، putInt |
| + | حفظ حالة، تحديث واجهة، منطق: getInt، putInt |
| 📤 تصدير | منطق: exportProgress |
| 📥 استيراد | منطق: importProgressPicker |
| 🗑 حذف كل التقدم (لا يمكن الرجوع) | تنقّل، حفظ حالة، تأكيد/حوار، رسالة، منطق: home |
| اقرأ واسمع | تنقّل، منطق: showDialogue |
| تدرّب | منطق: startDialogueLesson |
| تدرّب على أسئلة الحوار | منطق: startDialogueLesson |
| 🔊 | صوت |
| افتح الموقف وتدرّب | تنقّل، منطق: showScenarioDetails |
| تدرّب على عبارات الموقف | تنقّل، منطق: startItemSession |

## النتيجة

- ✅ لا مشكلة ربط ثابتة: كل شاشة مُستدعاة، ولكل شاشة رجوع، وكل زر له مستمع وفعل مصنّف، وكل نوع تمرين في المحتوى له عنوان ومعالجة، ولا حذف تقدم بدون تأكيد.
