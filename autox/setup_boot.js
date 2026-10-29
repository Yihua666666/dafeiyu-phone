// 在 AutoX 自己的 IntentTaskDatabase 里登记一个 BOOT_COMPLETED 任务。
// 运行一次即可；之后 AutoX 的 StaticBroadcastReceiver 开机时会跑 agent.js。
try {
    var db = context.openOrCreateDatabase("IntentTaskDatabase.db", 0, null);
    db.execSQL("CREATE TABLE IF NOT EXISTS `IntentTask`(" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "`script_path` TEXT NOT NULL ON CONFLICT FAIL, " +
        "`action` TEXT, " +
        "`category` TEXT, " +
        "`data_type` TEXT, " +
        "`local` INT);");
    db.execSQL("DELETE FROM `IntentTask`");
    var cv = new android.content.ContentValues();
    cv.put("script_path", "/sdcard/PhoneAgent/agent.js");
    cv.put("action", "android.intent.action.BOOT_COMPLETED");
    cv.put("local", java.lang.Integer.valueOf(1));
    var id = db.insert("IntentTask", null, cv);
    db.close();
    files.write("/sdcard/PhoneAgent/boot_setup.txt", "id=" + id + "\n");
    toast("开机任务已登记 id=" + id);
} catch (e) {
    files.write("/sdcard/PhoneAgent/boot_setup.txt", "ERROR: " + e + "\n");
    toast("登记失败：" + e);
}
