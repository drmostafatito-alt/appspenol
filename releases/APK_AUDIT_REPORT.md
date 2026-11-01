# تقرير تدقيق APK (بناء يدوي بدون Gradle)

الملف: `releases/app-release.apk` • الحجم: **864,088** بايت • SHA-256: `ed77e70e84cb8403d20b3ed283a9d62a5b4b1c28f4db8727c2c01498170f56af`

## ما تم فحصه فعليًا

- gradlew present: False • gradle on PATH: no • ANDROID_HOME: no
- package com.habla.espanol • versionCode 12 • versionName 8.2 • label Spanish 1000
- minSdk 23 • targetSdk 36
- launchable activity: com.habla.espanol.MainActivity
- permissions: RECORD_AUDIO, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED
- components: exported=true 0 (launcher only expected) • exported=false 0
- zip entries: 52 • dex files: 1 • stored entries: 2
- assets/content.json is byte-identical to the project asset (2,206,759 bytes)
- resources.arsc stored and 4-byte aligned
- classes.dex: dex 035 • 219,636 bytes • 2403 strings
- expected app classes present in dex (10)
- class file major version: 61 (61 = Java 17, dexed with --min-api 23)
- post-API-23 API usages: 3 • guarded: 3
- signature: v1, v2, v3 • CN=Spanish 1000, O=Personal use, L=Cairo, C=EG • SHA-256 898b10fc9c95860c…
- file: releases/app-release.apk • 864,088 bytes • SHA-256 ed77e70e84cb8403d20b3ed283a9d62a5b4b1c28f4db8727c2c01498170f56af

## Gradle

- لا يوجد Gradle ولا Android SDK في هذه البيئة (لا gradlew ولا gradle على PATH ولا ANDROID_HOME)، والشبكة متاحة فقط لـ github/npm/pypi؛ لذلك لا يمكن استعادة بناء Gradle هنا. البناء الحالي يستخدم aapt2 + ECJ + D8 + apksigner مباشرة، وهو نفس ما يفحصه هذا التقرير.

## المشاكل

- ✅ لا مشكلة بنيوية مكتشفة: البيان صحيح، الموارد والأصول سليمة، DEX سليم ببصمة وتحقق داخلي، الأصناف المطلوبة موجودة، ولا استخدام لواجهات أحدث من minSdk 23 بدون حماية.

## ما لا يثبته هذا التقرير

- لا يثبت أن التطبيق يُفتح على هاتف أو محاكي، ولا أن الواجهة ترسم، ولا أن الصوت يعمل. هذه البيئة لا تحتوي جهازًا ولا محاكيًا ولا `adb`.
- التحقق من التوقيع يثبت أن الحزمة موقّعة ولم تُعدّل، لا أن التوقيع معتمد للنشر.
