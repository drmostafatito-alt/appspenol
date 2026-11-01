# Spanish 1000 — حالة المشروع (8.2)

هذا الملف مرجع الحالة المحدَّث. التقارير المفصّلة: `releases/ACCEPTANCE_AUDIT_FINAL.md`.

## الخلاصة
- **المحتوى:** 3101 عنصرًا (2558 كلمة/تعبير + 543 جملة) في 106 وحدات و573 درسًا، 12 حوارًا، 22 قاعدة،
  11,681 تمرينًا مولّدًا + 96 مكتوبًا. **كل كلمة لها جملة مثال** (585 منها مؤلَّفة يدويًا في 8.2).
- **الاختبارات:** `run_java_tests.py` → **PASS — 339,754 فحصًا**، منها جلسة كاملة لكل درس من الـ573
  مع استعادة الحالة بعد «إعادة التشغيل». مدقق المحتوى 0 خطأ. تدقيق الربط وتدقيق APK بلا مشاكل.
- **البناء:** `releases/app-debug.apk` و`releases/app-release.apk` (864,088 بايت لكل منهما، v1+v2+v3).
- **الترقية:** 0 معرّف محذوف مقارنة بـ8.0 ⇒ لا هجرة ولا فقدان تقدم.
- **غير المُختبَر:** التشغيل على جهاز/محاكي (لا يوجد أي منهما هنا): الواجهة، الصوت، التعرّف على الكلام،
  الأداء، والتثبيت الفعلي.
- **الفجوات الصادقة:** 2558 كلمة من هدف 5000 (فجوة 2442)، 0 عنصر `verified` (لا مصدر للمعاني)،
  1072 مثالًا مولّدًا بقالب يحتاج مراجعة بشرية.

## التشغيل
```
python3 tools/build_content.py            # توليد content.json من المناهج
python3 tools/validate_content.py         # 0 أخطاء
python3 tools/content_gate.py             # سجل التحقق
python3 tools/run_java_tests.py           # اختبارات المنطق + تقرير جلسات الدروس
python3 tools/dedupe_curriculum.py        # تكرار داخل المستوى
python3 tools/audit_wiring.py --write releases/SCREEN_WIRING_REPORT.md
python3 tools/audit_apk.py --write releases/APK_AUDIT_REPORT.md
python3 tools/check_upgrade_compat.py --write releases/UPGRADE_COMPAT_REPORT.md
python3 tools/build_apk.py --variant debug|release
python3 tools/fetch_toolchain.py          # لو أدوات البناء غير موجودة (.tools غير محفوظ)
```
