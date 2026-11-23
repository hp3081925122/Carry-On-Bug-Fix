# -*- coding: utf-8 -*-
"""生成 Carry On Bug Fix 三版本分支的 CurseForge 发布清单（只写清单，不联网）。"""
import hashlib
import json
from pathlib import Path

ROOT = Path(r"E:\模组\发cf的模组\Carry On Bug修复")
OUT = ROOT / "curseforge" / "release"

CHANGELOG_EN = """## Carry On Bug Fix 1.0.0

First release, for Minecraft 1.20.1 (Forge).

- Carries a vehicle and everything riding it as one group, so passengers are no longer left behind or lost.
- Clicking any passenger picks up the whole group from its bottom vehicle.
- Placing, stacking onto another entity, and the death drop restore the whole group with every passenger still riding.
- The carried entity is drawn with all of its passengers in first and third person.
- Carry On's pickup rules (owner check, blacklist, baby exception, hostile mob and size limits, script and pickup conditions) apply to every passenger; one refused passenger refuses the whole group.
- Groups containing a player or an entity that cannot be saved are refused instead of being deleted.
- Stacking limits count the total size of both groups.
- Config in `carry_on_bug_fix-common.toml`: `passengerStack.enabled`, `passengerStack.maxDepth` (1-3), `passengerStack.maxEntities` (1-8).

---

## Carry On Bug Fix 1.0.0（首次发布）

适用于 Minecraft 1.20.1（Forge）。

- 抱起一个有乘客的实体时，载具和上面骑乘的所有实体作为一整组一起搬走，乘客不会再被留在原地或直接丢失。
- 点击组里的任意乘客，都会从最底下的载具开始把整组抱起来。
- 放下、叠到其他实体上、死亡掉落时，整组都会还原，乘客仍然骑在原来的位置上。
- 第一人称和第三人称下，手里的实体都会连同全部乘客一起显示。
- Carry On 的拾取规则（主人检查、拾取黑名单、幼体例外、敌对生物与体型限制、脚本条件、拾取条件）对每个乘客生效；只要有一个乘客被拒绝，整组都抱不起来。
- 组里有玩家或无法保存的实体时直接拒绝拾取，不会把它们删掉。
- 堆叠限制会合计两组的总规模。
- 配置位于 `carry_on_bug_fix-common.toml`：`passengerStack.enabled`、`passengerStack.maxDepth`（1-3）、`passengerStack.maxEntities`（1-8）。
"""

CHANGELOG_NEOFORGE = CHANGELOG_EN.replace("1.20.1 (Forge)", "1.21.1 (NeoForge)").replace(
    "适用于 Minecraft 1.20.1（Forge）。", "适用于 Minecraft 1.21.1（NeoForge）。"
)
CHANGELOG_26 = CHANGELOG_EN.replace("1.20.1 (Forge)", "26.1.2 (NeoForge)").replace(
    "适用于 Minecraft 1.20.1（Forge）。", "适用于 Minecraft 26.1.2（NeoForge）。"
)

MC = {"1.20.1": 9994, "1.21.1": 12735, "26.1.2": 16085}
LOADER = {"forge": 7498, "neoforge": 10150}
JAVA = {"1.20.1": 8326, "1.21.1": 11135, "26.1.2": 14454}
CLIENT, SERVER = 9638, 9639

BRANCHES = [
    ("1.20.1", "Forge", "forge", "1.20.1-forge-carry-on-bug-fix", "carry_on_bug_fix-1.20.1-forge-1.0.0.jar", CHANGELOG_EN),
    ("1.21.1", "NeoForge", "neoforge", "1.21.1-neoforge-carry-on-bug-fix", "carry_on_bug_fix-1.21.1-neoforge-1.0.0.jar", CHANGELOG_NEOFORGE),
    ("26.1.2", "NeoForge", "neoforge", "26.1.2-neoforge-carry-on-bug-fix", "carry_on_bug_fix-26.1.2-neoforge-1.0.0.jar", CHANGELOG_26),
]

files = []
for mc, loader_name, loader_key, folder, jar_name, changelog in BRANCHES:
    jar = ROOT / folder / "build" / "libs" / jar_name
    if not jar.is_file():
        raise SystemExit(f"missing artifact: {jar}")
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    files.append(
        {
            "minecraft": mc,
            "artifact": str(jar),
            "sha256": digest,
            "metadata": {
                "changelog": changelog,
                "changelogType": "markdown",
                "displayName": f"{mc}-{loader_name}-Carry On Bug Fix-1.0.0",
                "releaseType": "release",
                "gameVersionNames": [mc, loader_name, "Client", "Server"],
            },
        }
    )

OUT.mkdir(parents=True, exist_ok=True)
manifest = OUT / "release-manifest.json"
manifest.write_text(json.dumps({"files": files}, ensure_ascii=False, indent=2), encoding="utf-8")
print("manifest:", manifest)
for f in files:
    print(f["minecraft"], f["metadata"]["displayName"], f["sha256"][:16], f["metadata"]["gameVersionNames"])
