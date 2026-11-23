# Carry On Bug Fix — CurseForge 发布记录

- 项目：Carry On Bug Fix（附属模组，需前置 Carry On）
- CurseForge 项目 ID：`1789268`
- 项目 slug：`carry-on-bug-fix`
- 版本：`1.1.0`（三个分支同版本号）
- 首发时间：2026-11-07
- 1.1.0 上传时间：2026-11-10（1.20.1 / 1.21.1 / 26.1.2）、2026-11-23（26.2 / 26.3）

## 已上传文件

| 分支 | 文件名 | 文件 ID | 链接 | 状态 |
| --- | --- | --- | --- | --- |
| 1.20.1 Forge | `carry_on_bug_fix-1.20.1-forge-1.0.0.jar` | 9493460 | https://www.curseforge.com/minecraft/mc-mods/carry-on-bug-fix/files/9493460 | Approved |
| 1.21.1 NeoForge | `carry_on_bug_fix-1.21.1-neoforge-1.0.0.jar` | 9493461 | https://www.curseforge.com/minecraft/mc-mods/carry-on-bug-fix/files/9493461 | Approved |
| 26.1.2 NeoForge | `carry_on_bug_fix-26.1.2-neoforge-1.0.0.jar` | 9493463 | https://www.curseforge.com/minecraft/mc-mods/carry-on-bug-fix/files/9493463 | Approved |
| 1.20.1 Forge | `carry_on_bug_fix-1.20.1-forge-1.1.0.jar` | 9543281 | https://www.curseforge.com/minecraft/mc-mods/carry-on-bug-fix/files/9543281 | Approved |
| 1.21.1 NeoForge | `carry_on_bug_fix-1.21.1-neoforge-1.1.0.jar` | 9543282 | https://www.curseforge.com/minecraft/mc-mods/carry-on-bug-fix/files/9543282 | Approved |
| 26.1.2 NeoForge | `carry_on_bug_fix-26.1.2-neoforge-1.1.0.jar` | 9543283 | https://www.curseforge.com/minecraft/mc-mods/carry-on-bug-fix/files/9543283 | Approved |
| 26.2 NeoForge | `carry_on_bug_fix-26.2-neoforge-1.1.0.jar` | 9765663 | https://www.curseforge.com/minecraft/mc-mods/carry-on-bug-fix/files/9765663 | 已接受，等待审核 |
| 26.3 NeoForge | `carry_on_bug_fix-26.3-neoforge-1.1.0.jar` | 9765664 | https://www.curseforge.com/minecraft/mc-mods/carry-on-bug-fix/files/9765664 | 已接受，等待审核 |

发布类型均为 Release，标签 `1.20.1/Forge`、`1.21.1/NeoForge`、`26.1.2/NeoForge`、`26.2/NeoForge`、`26.3/NeoForge` + Client + Server。

## 备注

- 首次尝试用 `gameVersions`（数字 ID，如 9994 / 7498）被拒绝：`Invalid game version ID: 9994 belongs to an invalid dependency.` 改用 `gameVersionNames` 后三个文件均返回 200 与文件 ID。
- 上传被接受不等于审核通过；审核中的文件不会出现在公开文件列表里，因此公开接口暂时查不到这三个文件。
- 未附加 `relations`（Carry On 依赖关系）：公开接口无法解析 Carry On 的项目 ID，未凭记忆填写；如需在页面上显示“必需依赖”，可在作者后台手动关联或用 `update-file` 补 `relations`。
- 为避免游戏内模组列表描述乱码，三个分支都补了 `filteringCharset = 'UTF-8'`，并把 `gradle.properties` 里的中文 `mod_description` 改成 `\uXXXX` 转义（Gradle 按 ISO-8859-1 读取 properties 文件）。
