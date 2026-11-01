# Proguard rules for unblocker
-keepattributes *Annotation*
-dontwarn javax.annotation.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# WorkManager reflectively instantiates workers
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep Serializable models and data classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep Room Database & Entities
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomDatabase$Callback { *; }
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Insert *;
    @androidx.room.Query *;
    @androidx.room.Update *;
    @androidx.room.Delete *;
}

# Keep TensorFlow Lite & FlatBuffers
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.flatbuffers.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Keep Kotlinx Serialization & Coroutines
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
-dontwarn kotlinx.coroutines.**
