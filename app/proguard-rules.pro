# WorkManager persists these class names in its database across app upgrades.
-keep class hu.safora.documentstudio.OcrWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class hu.safora.documentstudio.ReminderWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
