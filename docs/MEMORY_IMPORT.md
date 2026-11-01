# 从其他系统移植记忆

大肥鱼的记忆不是数据库，是手机本地的一组纯文本文件。  
所以“移植记忆”不是导入一个专有备份，而是把来源数据整理成下面几类文件，再放回手机。

## 记忆落点

```text
/sdcard/DeepSeekGirl/
  persona.md                    她是谁（人设，可选）
  diary/YYYY-MM-DD.md           她自己的日记
  memory/
    facts.md                    长期事实
    self.md                     自我认知
    chat/YYYY-MM-DD.jsonl       每天的对话原文
    summaries/YYYY-MM-DD.md     每日回顾（可选）
```

其中真正长期生效的是：

- `memory/facts.md`：稳定事实、偏好、约定
- `memory/self.md`：她对自己的认知
- `diary/`：按天发生的事

对话原文只是原文归档；`recallMemory` 默认回看最近 **14 天对话、10 天日记**。更早的重要事情，应该写进 facts 或 diary，而不是只塞进旧聊天记录。

## 文件格式

### facts.md

一行一条，纯文本：

```markdown
- 2026-10-30 12:00 主人喜欢蓝色
- 2026-10-30 12:05 主人在杭州，做前端
```

不要写成长篇故事；事实越短，召回越准。

### self.md

自由 Markdown。写“她是谁、经历过什么、现在怎么看自己”。  
注意：每日回顾可能会改写这份文件，导入前先备份。

### diary/YYYY-MM-DD.md

```markdown
# 大肥鱼的日记 2026-10-30

[13:20] 今天把鱼缸脚本跑通了。
[22:40] 主人说下周可能会很忙。
```

### memory/chat/YYYY-MM-DD.jsonl

一行一个 JSON，UTF-8：

```json
{"ts":1793337600000,"role":"user","source":"import","text":"在干嘛？"}
{"ts":1793337660000,"role":"assistant","source":"import","text":"刚看完一集，准备写日记。"}
```

- `ts`：毫秒时间戳
- `role`：`user` 或 `assistant`
- `source`：来源标记，建议写来源名
- `text`：正文；原程序单条最多保留 2000 字

## 从不同来源怎么映射

| 来源 | 映射方式 |
|---|---|
| dsh-world `worlds/<id>/` | `room/我是谁.md` → `memory/self.md`；`diary/*.md` → `diary/`；`conversations/*.jsonl` → `memory/chat/`；`persona.md` → `persona.md`；`archive.sqlite` 需要先导出成对话 JSON |
| ChatGPT 导出 | `conversations.json` 的 message → chat JSONL；自定义指令/长期偏好 → facts；重要事件 → diary |
| Mem0 / Letta / Zep | 导出的 fact/message JSON → facts 或 chat JSONL；不要整库直接覆盖 |
| 角色卡 / SillyTavern | 人设字段 → `persona.md` / `self.md`；聊天记录 → chat JSONL |
| 纯文本日记 | 按日期拆成 `diary/YYYY-MM-DD.md`，保留 `[HH:MM]` 行 |

## 导入步骤

1. **先停掉 AutoX 里的 agent**，避免边写边导入。
2. 备份手机现有记忆：

   ```text
   /sdcard/DeepSeekGirl/
   ```

3. 在电脑上把来源数据转换成上面的格式。
4. 用文件管理器或 ADB 放回手机。
5. 启动 agent，问她一个只有旧记忆里才有答案的问题，确认召回。
6. 检查 `memory/facts.md` 有没有重复；有就手动去重。

## 注意

- 全部使用 **UTF-8**，尽量不带 BOM。
- 文件名日期用本地时区，例如 `2026-10-30`。
- 不要把整份聊天记录塞进 facts；facts 只放稳定结论。
- 同一份来源不要重复导入，否则 facts 会膨胀。
- 导入前备份；`self.md` 和 `diary` 可能被她后续改写。
- 这些文件是她的房间。导入的是记忆，不是把另一个人格硬塞进来；人设和记忆最好分开整理。
