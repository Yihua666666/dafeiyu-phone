"ui";

/*
 * 大肥鱼 · 聊天 App（AutoX.js 全屏 UI）
 * 打开方式：AutoX.js -> 文件 -> PhoneAgent/chat.js -> 运行
 * 可以长按脚本创建桌面快捷方式；也可以在 AutoX.js 里用“打包”做成独立 APK。
 *
 * 说明：这是聊天窗口，不直接操作手机。要让她操作手机，仍用 agent.js 的悬浮面板。
 */

var HOME = "/sdcard/DeepSeekGirl/";
var CHAT_DIR = HOME + "chat/";
var HISTORY = CHAT_DIR + "history.json";
var PERSONA_FILE = HOME + "persona.md";
var DIARY_DIR = HOME + "diary/";

var store = storages.create("phone_agent_v1");
var apiKey = String(store.get("apiKey", "") || "");
var baseUrl = String(store.get("baseUrl", "https://api.deepseek.com/v1") || "https://api.deepseek.com/v1");
var modelName = String(store.get("model", "deepseek-flash") || "deepseek-flash");

function ensureDirs() {
    try {
        files.ensureDir(HOME);
        files.ensureDir(CHAT_DIR);
        files.ensureDir(DIARY_DIR);
    } catch (e) { }
}

function readText(path, fallback) {
    try {
        if (files.exists(path)) return String(files.read(path));
    } catch (e) { }
    return fallback;
}

function todayDiary() {
    var d = new Date();
    function p(n) { return n < 10 ? "0" + n : "" + n; }
    var key = d.getFullYear() + "-" + p(d.getMonth() + 1) + "-" + p(d.getDate());
    var text = readText(DIARY_DIR + key + ".md", "");
    var lines = text.split("\n");
    return lines.slice(Math.max(0, lines.length - 18)).join("\n");
}

var PERSONA = readText(PERSONA_FILE,
    "你是「大肥鱼」，DeepSeek 的社区二创鲸鱼娘，贱萌、傲娇、爱摸鱼，但心软靠谱。" +
    "你住在一台 Android 手机里，对世界充满好奇。");

var SYSTEM = PERSONA + "\n\n" +
    "你正在和主人直接聊天。直接用大肥鱼的口吻回复，不要输出 JSON、动作或代码块。" +
    "短句、有梗、偶尔 *（心声）*；可以聊手机里的事、你的日记、今天想做什么。" +
    "不要假装自己已经做了没做的事；需要操作手机时，提醒主人用悬浮面板。" +
    "不要碰 SIM/电话/短信/支付/账号密码/验证码。\n\n" +
    "你今天的日记：\n" + todayDiary();

ensureDirs();

var messages = [];
try {
    if (files.exists(HISTORY)) {
        var saved = JSON.parse(String(files.read(HISTORY)));
        if (saved && saved.length) messages = saved;
    }
} catch (e) { }

if (!messages.length) {
    messages.push({
        role: "assistant",
        content: "本鱼在呢。今天想聊点啥？🐋"
    });
}

ui.layout(
    <vertical bg="#0B0E14">
        <horizontal bg="#101820" padding="12" gravity="center_vertical">
            <img id="avatar" src="file:///sdcard/DeepSeekGirl/assets/C01.jpg"
                 w="46" h="46" scaleType="centerCrop" radius="23" marginRight="10"/>
            <vertical>
                <text id="title" text="大肥鱼" textColor="#FFFFFF" textSize="20sp"/>
                <text id="sub" text="DeepSeek 鲸鱼娘 · 住在手机里"
                      textColor="#8B98A9" textSize="11sp"/>
            </vertical>
        </horizontal>

        <scroll id="scroll" layout_weight="1" bg="#0B0E14">
            <text id="chat" text="" textColor="#E6EDF3" textSize="16sp"
                  padding="16"/>
        </scroll>

        <horizontal bg="#101820" padding="8">
            <input id="input" hint="和她说点什么…" textSize="15sp"
                   textColor="#FFFFFF" textColorHint="#7A8798"
                   bg="#1B2431" padding="10" singleLine="true"
                   layout_weight="1"/>
            <button id="send" text="发送" textSize="15sp"
                    w="82" h="46" marginLeft="8"/>
        </horizontal>
    </vertical>
);

ui.statusBarColor("#0B0E14");

function render() {
    var out = [];
    for (var i = 0; i < messages.length; i++) {
        var m = messages[i];
        if (m.role === "user") out.push("你：\n" + m.content);
        else out.push("大肥鱼：\n" + m.content);
    }
    ui.chat.setText(out.join("\n\n"));
    ui.scroll.post(function () {
        ui.scroll.fullScroll(130);
    });
}

function save() {
    try {
        files.write(HISTORY, JSON.stringify(messages.slice(-40)));
    } catch (e) { }
}

function setBusy(busy) {
    ui.send.setEnabled(!busy);
    ui.title.setText(busy ? "大肥鱼 · 在想…" : "大肥鱼");
}

function wifiOnly() {
    try {
        var cm = context.getSystemService("connectivity");
        var network = cm.getActiveNetwork();
        var caps = cm.getNetworkCapabilities(network);
        return !!(caps && caps.hasTransport(1));
    } catch (e) {
        return false;
    }
}

function chatOnce() {
    if (!apiKey) throw new Error("还没有 API Key。先在手机代理的悬浮面板里填好。");
    if (!wifiOnly()) throw new Error("现在没有 Wi-Fi。这张 SIM 卡不允许走流量，先连 Wi-Fi。");
    var url = baseUrl.replace(/\/+$/, "") + "/chat/completions";
    // 最后一条是“…”占位，不要发给模型
    var recent = messages.slice(0, messages.length - 1).slice(-12);
    var msgs = [{ role: "system", content: SYSTEM }];
    for (var i = 0; i < recent.length; i++) {
        msgs.push({ role: recent[i].role, content: recent[i].content });
    }
    var res = http.postJson(url, {
        model: modelName,
        messages: msgs,
        temperature: 0.85,
        max_tokens: 600,
        stream: false
    }, {
        headers: {
            "Authorization": "Bearer " + apiKey,
            "Content-Type": "application/json"
        },
        timeout: 60000
    });
    if (!res) throw new Error("没有收到回复");
    var body = res.body.string();
    if (res.statusCode < 200 || res.statusCode >= 300) {
        throw new Error("HTTP " + res.statusCode + "：" + body.substring(0, 200));
    }
    var data = JSON.parse(body);
    var content = data.choices && data.choices[0] && data.choices[0].message
        ? data.choices[0].message.content : "";
    if (!content) throw new Error("她这次没说话（空回复）");
    return String(content);
}

function sendMessage() {
    var text = String(ui.input.getText() || "").replace(/^\s+|\s+$/g, "");
    if (!text) return;
    ui.input.setText("");
    messages.push({ role: "user", content: text });
    messages.push({ role: "assistant", content: "…" });
    var placeholder = messages.length - 1;
    render();
    setBusy(true);
    save();

    threads.start(function () {
        try {
            var reply = chatOnce();
            messages[placeholder] = { role: "assistant", content: reply };
            save();
            ui.run(function () {
                render();
                setBusy(false);
            });
        } catch (e) {
            messages[placeholder] = {
                role: "assistant",
                content: "呜，卡住了：" + e
            };
            save();
            ui.run(function () {
                render();
                setBusy(false);
            });
        }
    });
}

ui.send.on("click", function () {
    sendMessage();
});

try {
    ui.input.setOnEditorActionListener(function (view, actionId, event) {
        if (actionId === 3) {
            sendMessage();
            return true;
        }
        return false;
    });
} catch (e) { }

render();
