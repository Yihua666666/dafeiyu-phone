var ACTION = "android.intent.action.BOOT_COMPLETED";
var db = context.openOrCreateDatabase("IntentTaskDatabase.db", 0, null);
db.execSQL("CREATE TABLE IF NOT EXISTS `IntentTask`(" +
    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
    "`script_path` TEXT NOT NULL ON CONFLICT FAIL, " +
    "`action` TEXT, " +
    "`category` TEXT, " +
    "`data_type` TEXT, " +
    "`local` INT);");
db.execSQL("DELETE FROM `IntentTask` WHERE `action`='" + ACTION + "'");
db.close();
files.write("/sdcard/PhoneAgent/boot_switch.txt", "off\n");
toast("开机自启：关");
