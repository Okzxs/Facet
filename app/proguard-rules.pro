# Правила R8/ProGuard для Facet (release-сборка).
# Файл подключается в app/build.gradle.kts для buildType release.

# Сохраняем имена файлов и номера строк: без этого стектрейсы из релиза
# бесполезны даже с mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# NewPipe Extractor и Rhino
# ---------------------------------------------------------------------------
# NewPipe тянет за собой Rhino (org.mozilla.javascript), а тот ссылается на
# java.beans и javax.script, которых в Android нет. Без -dontwarn R8 валит
# сборку на missing classes. Сами классы Rhino не удаляем: экстрактор дергает
# их через reflection при разборе некоторых сервисов.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn com.grack.nanojson.**
-keep class org.mozilla.javascript.** { *; }
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class com.grack.nanojson.** { *; }

# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
# Сериализаторы генерируются в `**$$serializer`; R8 должен их видеть, иначе
# JSON-ответы источников перестают разбираться уже в релизе.
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.facet.music.**$$serializer { *; }
-keepclassmembers class com.facet.music.** {
    *** Companion;
}
-keepclasseswithmembers class com.facet.music.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
# Room генерирует реализации RoomDatabase и DAO в рантайме по именам классов.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Enum'ы хранятся в БД и SharedPreferences по имени константы. Обычная
# обфускация переименовала бы константы и valueOf() перестал бы находить
# сохранённые значения, поэтому имена констант фиксируем.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    *;
}

# ---------------------------------------------------------------------------
# OkHttp / Okio и их опциональные TLS-провайдеры
# ---------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------------------
# Media3, Coil, Hilt
# ---------------------------------------------------------------------------
-dontwarn androidx.media3.**
-dontwarn coil3.**
-dontwarn dagger.hilt.**

# Hilt генерирует код по аннотациям; аннотации должны пережить R8.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
