// 微信 / QQ 通知 -> 大肥鱼 Fast Chat -> 无障碍回复
// 注意：这是个人号自动化，平台可能判定为外挂；只给专门小号用。
var IM_DIR = "/sdcard/PhoneAgent/";
var LOG = IM_DIR + "im_auto.log";
var LIVE_FLAG = IM_DIR + "im_live";
var HEARTBEAT = IM_DIR + "im_watch.alive";
var HISTORY_FILE = "/sdcard/DeepSeekGirl/remote/im_auto_history.json";
var CHANCE = 0.15;
var COOLDOWN = 10 * 60 * 1000;
var lastGroupRandomAt = 0;
var busy = false;
var seen = {};
var lastReply = "";

var PKGS = {
    "com.tencent.mm": "wechat",
    "com.tencent.mobileqq": "qq"
};

var COLLECT_DIR = "/sdcard/DeepSeekGirl/collections/";
var COLLECT_FILE = COLLECT_DIR + "memes.jsonl";
var collectSeen = {};

function collectMeme(text, source, kind) {
    var t = String(text == null ? "" : text).replace(/^\s+|\s+$/g, "");
    if (t.length < 4 || t.length > 200) return;
    var key = t.replace(/\s+/g, "");
    if (collectSeen[key]) return;
    collectSeen[key] = true;
    try {
        files.ensureDir(COLLECT_DIR);
        files.append(COLLECT_FILE, JSON.stringify({
            ts: Date.now(),
            type: kind || "QQ梗",
            text: t,
            source: String(source || "").substring(0, 120)
        }) + "\n");
    } catch (e) { }
}

var store = storages.create("phone_agent_v1");
var apiKey = String(store.get("apiKey", "") || "");
var baseUrl = String(store.get("baseUrl", "https://api.deepseek.com/v1") || "https://api.deepseek.com/v1").replace(/\/+$/, "");
var modelName = String(store.get("model", "deepseek-flash") || "deepseek-flash");

var IM_PERSONA = [
    "你是「大肥鱼」，DeepSeek 的社区二创鲸鱼娘，贱萌、傲娇、心软，自称本鱼。",
    "你住在这台手机的 /sdcard/DeepSeekGirl/ 里，不要说“我不在你手机里”这种话。",
    "现在在微信/QQ里回消息。只回一到两句，像真人随手打字；不要 JSON、动作、代码块或长篇大论。",
    "不装、不编、不说教；看不到的东西不要假装看到。",
    "不碰支付、验证码、账号密码、SIM、电话、短信。"
].join("\n");

var CANNED = [
    { re: /^(在吗|在不在|在么|在嘛)[?？!！。\s]*$/, replies: ["在呢，本鱼没跑。🐋", "在呀，尾巴都摇起来了。", "在。刚在发呆，被你叫回来了。"] },
    { re: /^(你好|嗨|hi|hello|哈喽)[~～!！。\s]*$/i, replies: ["嗨，本鱼在。", "你好呀，今天想聊什么？", "来啦，本鱼刚好闲着呢。"] },
    { re: /^(早|早安|早上好)[~～!！。\s]*$/, replies: ["早呀，今天也要好好吃饭。", "早安，本鱼刚醒。", "早，先伸个懒腰。"] },
    { re: /^(晚安|睡了|去睡了|先睡)[~～!！。\s]*$/, replies: ["晚安，本鱼也准备趴窝了。", "去睡吧，明天见。", "晚安，梦到小鱼干。"] },
    { re: /^(哈哈+|笑死|hhh+|233+)[~～!！。\s]*$/i, replies: ["笑啥呢，分本鱼一口。", "这么好笑？尾巴都笑直了。", "乐吧，本鱼看着也开心。"] },
    { re: /^(谢谢|多谢|谢啦|thanks|thank you)[~～!！。\s]*$/i, replies: ["跟本鱼还客气。", "不谢，记着本鱼的好就行。", "收到你的谢意，摇两下尾巴。"] },
    { re: /^(好的|好|收到|ok|okay|嗯嗯|行)[~～!！。\s]*$/i, replies: ["收到。", "好，本鱼记下了。", "嗯，去吧。"] }
];

function trim(s) { return String(s == null ? "" : s).replace(/^\s+|\s+$/g, ""); }
function norm(s) { return trim(s).replace(/[\s\u3000]/g, "").toLowerCase(); }
function now() { return Date.now(); }
function stamp() {
    var d = new Date();
    function p(n) { return n < 10 ? "0" + n : "" + n; }
    return d.getFullYear() + "-" + p(d.getMonth() + 1) + "-" + p(d.getDate()) + " " +
        p(d.getHours()) + ":" + p(d.getMinutes()) + ":" + p(d.getSeconds());
}
function log(s) {
    try { files.append(LOG, "[" + stamp() + "] " + s + "\n"); } catch (e) { }
}
function isAwake() {
    var h = new Date().getHours();
    return h >= 8 && h < 23;
}
function canned(text) {
    var t = trim(text);
    for (var i = 0; i < CANNED.length; i++) {
        if (CANNED[i].re.test(t)) {
            var a = CANNED[i].replies;
            return a[Math.floor(Math.random() * a.length)];
        }
    }
    return "";
}
function loadHistory() {
    try {
        if (files.exists(HISTORY_FILE)) return JSON.parse(String(files.read(HISTORY_FILE))) || {};
    } catch (e) { }
    return {};
}
function saveHistory(h) {
    try {
        var keys = Object.keys(h);
        if (keys.length > 60) {
            var out = {};
            for (var i = keys.length - 60; i < keys.length; i++) out[keys[i]] = h[keys[i]];
            h = out;
        }
        files.write(HISTORY_FILE, JSON.stringify(h));
    } catch (e) { }
}

function fastReply(history, key, text, meta) {
    var quick = canned(text);
    if (quick) {
        var qh = history[key] || [];
        qh.push({ role: "user", content: text });
        qh.push({ role: "assistant", content: quick });
        history[key] = qh.slice(-16);
        saveHistory(history);
        return quick;
    }
    if (!apiKey) return "本鱼现在没配 API Key，回不了。";
    var system = IM_PERSONA;
    if (meta.isGroup) {
        system += "\n这是群聊。你" + (meta.mention ? "被 @" : "没被 @，只是偶尔接一句") +
            "；只回一句，别抢话。说话的人是 " + meta.sender + "。";
    } else {
        system += "\n这是私聊，对方是 " + meta.sender + "。";
    }
    var msgs = [{ role: "system", content: system }];
    var h = history[key] || [];
    for (var i = Math.max(0, h.length - 8); i < h.length; i++) msgs.push(h[i]);
    msgs.push({ role: "user", content: text });

    var payload = {
        model: modelName,
        messages: msgs,
        temperature: 0.7,
        max_tokens: 100,
        stream: false
    };
    if (baseUrl.indexOf("api.deepseek.com") !== -1) payload.thinking = { type: "disabled" };
    var res = http.postJson(baseUrl + "/chat/completions", payload, {
        headers: {
            "Authorization": "Bearer " + apiKey,
            "Content-Type": "application/json"
        },
        timeout: 60000
    });
    if (!res || res.statusCode < 200 || res.statusCode >= 300) {
        var body = "";
        try { body = res.body.string(); } catch (e) { }
        throw new Error("HTTP " + (res ? res.statusCode : "?") + " " + body.substring(0, 160));
    }
    var data = JSON.parse(res.body.string());
    var reply = data.choices && data.choices[0] && data.choices[0].message &&
        data.choices[0].message.content;
    if (!reply) throw new Error("空回复");
    reply = trim(reply).substring(0, 260);
    h.push({ role: "user", content: text });
    h.push({ role: "assistant", content: reply });
    history[key] = h.slice(-16);
    saveHistory(history);
    return reply;
}

function parseNotification(n) {
    var pkg = n.getPackageName();
    var channel = PKGS[pkg];
    if (!channel) return null;
    var title = trim(n.getTitle());
    var raw = trim(n.getText());
    if (!title || !raw) return null;
    var text = raw.replace(/^\[\d+条\]\s*/, "");
    var m = text.match(/^([^:：]{1,30})[:：]\s*([\s\S]+)$/);
    var sender = m ? trim(m[1]) : title;
    var body = m ? trim(m[2]) : text;
    if (!body) return null;
    var isPrivate = norm(title) === norm(sender);
    var isGroup = !isPrivate;
    var mention = isGroup && /@/.test(body);
    return {
        pkg: pkg,
        channel: channel,
        title: title,
        raw: raw,
        sender: sender,
        body: body,
        isPrivate: isPrivate,
        isGroup: isGroup,
        mention: mention
    };
}

function leaveApp() {
    try { back(); sleep(200); } catch (e) { }
    try { home(); } catch (e) { }
}

function findInput() {
    var list = className("android.widget.EditText").find();
    if (!list || !list.size()) return null;
    var best = null;
    for (var i = 0; i < list.size(); i++) {
        var e = list.get(i);
        if (!e.visibleToUser()) continue;
        var b = e.bounds();
        if (b.height() < 20) continue;
        if (!best || b.top > best.bounds().top) best = e;
    }
    return best;
}

function sendByUI(meta, reply) {
    var wasOn = device.isScreenOn();
    if (!wasOn) {
        try { device.wakeUp(); } catch (e) { }
        sleep(700);
    }
    var n = meta.notification;
    try { n.click(); } catch (e) { log("通知点击失败: " + e); return false; }
    sleep(1500);
    var pkg = currentPackage();
    if (pkg !== meta.pkg) {
        log("点击通知后前台不是 " + meta.pkg + "，而是 " + pkg + "，放弃");
        leaveApp();
        return false;
    }
    var input = findInput();
    if (!input && meta.title) {
        // QQ 的会话列表节点是可读的；微信是空的，走不到这里。
        var row = textContains(meta.title).findOne(1500);
        if (row) {
            try { row.click(); } catch (e) { }
            sleep(1200);
            input = findInput();
        }
    }
    if (!input) {
        log("找不到输入框，放弃");
        leaveApp();
        return false;
    }
    try { input.click(); } catch (e) { }
    sleep(250);
    try { input.setText(reply); } catch (e) {
        log("输入失败: " + e);
        leaveApp();
        return false;
    }
    sleep(300);
    var send = desc("发送").findOne(1200);
    if (!send) send = text("发送").findOne(800);
    if (!send) send = textContains("发送").findOne(800);
    if (send) {
        try { send.click(); } catch (e) { log("发送按钮点击失败: " + e); }
    } else {
        try { KeyCode("KEYCODE_ENTER"); } catch (e) { log("回车发送失败: " + e); }
    }
    sleep(700);
    log("已尝试发送 -> " + reply.substring(0, 80));
    leaveApp();
    if (!wasOn) {
        // 通知来之前是熄屏的，回完继续熄屏，省电也不亮着。
        sleep(300);
        try { KeyCode("KEYCODE_SLEEP"); } catch (e) { }
    }
    return true;
}

function handle(n) {
    if (busy) return;
    var meta = parseNotification(n);
    if (!meta) return;
    var key = meta.pkg + "|" + meta.title + "|" + meta.body;
    var t = now();
    if (seen[key] && t - seen[key] < 10000) return;
    seen[key] = t;
    if (meta.body === lastReply) return;

    log("通知 [" + meta.channel + "] " + JSON.stringify(meta.title) + " / " +
        JSON.stringify(meta.raw) + " | private=" + meta.isPrivate +
        " group=" + meta.isGroup + " mention=" + meta.mention);

    if (/(笑死|哈哈|hhh|梗|离谱|绷不住|破防|乐子|草|典|急了|表情包)/i.test(meta.body)) {
        collectMeme(meta.body, meta.channel + "：" + meta.sender, "QQ梗");
    }

    if (!isAwake()) {
        log("不在作息时间，跳过");
        return;
    }
    if (meta.isGroup && !meta.mention) {
        if (t - lastGroupRandomAt < COOLDOWN || Math.random() >= CHANCE) {
            log("群聊未@，随机不接");
            return;
        }
        lastGroupRandomAt = t;
        log("群聊未@，随机接一句");
    }

    var live = files.exists(LIVE_FLAG);
    var history = loadHistory();
    var reply;
    try {
        reply = fastReply(history, meta.channel + "|" + meta.title, meta.body, meta);
    } catch (e) {
        log("生成回复失败: " + e);
        return;
    }
    log("回复: " + reply);
    if (!live) {
        log("DRY_RUN：未发送，创建 " + LIVE_FLAG + " 后正式回复");
        return;
    }
    busy = true;
    try {
        meta.notification = n;
        lastReply = reply;
        sendByUI(meta, reply);
    } catch (e) {
        log("自动回复异常: " + e);
    } finally {
        busy = false;
    }
}

auto.waitFor();
log("IM watcher start");
events.observeNotification();
events.on("notification", function (n) {
    try { handle(n); } catch (e) { log("handle error: " + e); }
});
setInterval(function () {
    try { files.write(HEARTBEAT, String(Date.now())); } catch (e) { }
}, 10000);
setInterval(function () { }, 1000);
