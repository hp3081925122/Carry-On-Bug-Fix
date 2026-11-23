# Carry On Bug Fix — CurseForge 发布记录

- 项目：Carry On Bug Fix（附属模组，需前置 Carry On）
- CurseForge 项目 ID：`1789268`
- 项目 slug：`carry-on-bug-fix`
- 当前版本：`1.2.0`（五个分支同版本号）
- 首发时间：2026-11-07
- 1.1.0 上传时间：2026-11-10（1.20.1 / 1.21.1 / 26.1.2）、2026-11-23（26.2 / 26.3）
- 1.2.0 上传时间：2026-11-23（1.20.1 / 1.21.1 / 26.1.2 / 26.2 / 26.3）

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
| 1.20.1 Forge | `carry_on_bug_fix-1.20.1-forge-1.2.0.jar` | 9773236 | https://www.curseforge.com/minecraft/mc-mods/carry-on-bug-fix/files/9773236 | Approved |
| 1.21.1 NeoForge | `carry_on_bug_fix-1.21.1-neoforge-1.2.0.jar` | 9773237 | https://www.curseforge.com/minecraft/mc-mods/carry-on-bug-fix/files/9773237 | Approved |
| 26.1.2 NeoForge | `carry_on_bug_fix-26.1.2-neoforge-1.2.0.jar` | 9773238 | https://www.curseforge.com/minecraft/mc-mods/carry-on-bug-fix/files/9773238 | Approved |
| 26.2 NeoForge | `carry_on_bug_fix-26.2-neoforge-1.2.0.jar` | 9773239 | https://www.curseforge.com/minecraft/mc-mods/carry-on-bug-fix/files/9773239 | Approved |
| 26.3 NeoForge | `carry_on_bug_fix-26.3-neoforge-1.2.0.jar` | 9773240 | https://www.curseforge.com/minecraft/mc-mods/carry-on-bug-fix/files/9773240 | Approved |

发布类型均为 Release，标签分别对应：
- 1.20.1：`1.20.1` + `Forge` + `Client` + `Server` + `Java 17`
- 1.21.1：`1.21.1` + `NeoForge` + `Client` + `Server` + `Java 21`
- 26.1.2：`26.1.2` + `NeoForge` + `Client` + `Server` + `Java 25`
- 26.2：`26.2` + `NeoForge` + `Client` + `Server` + `Java 25`
- 26.3：`26.3` + `NeoForge` + `Client` + `Server` + `Java 25`

`1.2.0` 全部 5 个文件均已通过 Upload API 附带 `relations`：`carry-on`（`requiredDependency`）与 `carry-on-extend`（`optionalDependency`）。

## 备注

- `1.2.0` 正式 JAR 打包时已在 `build.gradle` 中排除 `org/hp/carryonbugfix/gametest/**` 及测试用 `empty.nbt` 结构；`1.20.1` 经 `clean jar`（触发 `reobfJar`）完整包含 `carry_on_bug_fix.refmap.json`（含 6 项原版 SRG 映射）。
- 上传被接受不等于审核通过；审核中的文件不会出现在公开文件列表里，请以作者后台文件页实际状态为准。
