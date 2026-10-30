var ACTION = "android.intent.action.BOOT_COMPLETED";
var AGENT = "/sdcard/PhoneAgent/agent.js";
var db = context.openOrCreateDatabase("IntentTaskDatabase.db", 0, null);
db.execSQL("CREATE TABLE IF NOT EXISTS `IntentTask`(" +
    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
    "`script_path` TEXT NOT NULL ON CONFLICT FAIL, " +
    "`action` TEXT, " +
    "`category` TEXT, " +
    "`data_type` TEXT, " +
    "`local` INT);");
db.execSQL("DELETE FROM `IntentTask` WHERE `action`='" + ACTION + "'");
var cv = new android.content.ContentValues();
cv.put("script_path", AGENT);
cv.put("action", ACTION);
cv.put("local", java.lang.Integer.valueOf(1));
db.insert("IntentTask", null, cv);
db.close();
files.write("/sdcard/PhoneAgent/boot_switch.txt", "on\n");
toast("开机自启：开");
