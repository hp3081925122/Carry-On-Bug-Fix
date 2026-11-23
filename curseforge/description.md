**Carry On Bug Fix** is a bug-fix and quality-of-life addon for Carry On.

## Features

- **Carry riding entities as a group**: when you pick up a boat with a villager inside, a minecart with a mob, or a stacked tower of mobs, all passengers are carried, rendered, placed, stacked, and dropped on death together while still enforcing Carry On's pickup rules on every passenger.
- **Direct boat & minecart boarding**: while carrying a mob, passenger stack, or player, Shift + right-click a boat, chest boat, or rideable minecart (or a passenger already inside) to place the carried entity straight into an open seat. Respects vanilla seat capacity and size limits, prevents vehicle nesting, rolls back cleanly if mounting fails, and avoids accidentally seating the player.
- **Portable playing jukebox**: picking up a playing jukebox keeps the music playing without restarting, moves the 3D sound source smoothly with the carrier, emits note particles and jukebox game events, and hands the live sound channel seamlessly back to the block when placed down so ejecting the disc or breaking the jukebox stops it normally. Nearby or late-joining players resume accurately from the current playback position.
- **Blacklist with whitelist exceptions**: in Carry On's default blacklist mode, whitelist entries can act as exceptions to broad wildcard blacklists (for example, blacklisting `modid:*` while allowing specific crates or entities). Supports both Carry On's own whitelist/tags and dedicated exception lists with exact IDs, `*` wildcards, and `#tags`.
- **Carry On Extend compatibility**: when Carry On Extend is installed, throwing a carried group keeps all passengers seated on the vehicle.
- **Carry key fix**: fixes issues where the carry key could not be bound to a mouse button or failed to respond after rebinding.

## Config

Configuration is stored in `carry_on_bug_fix-common.toml` (or `carry_on_bug_fix-local.toml` on newer FML builds).

- **Passenger Stack (`passengerStack`)**
  - `enabled`: enable or disable group carrying and rendering (default `true`).
  - `maxDepth`: maximum riding stack depth including the bottom vehicle (`1-3`, default `3`).
  - `maxEntities`: maximum total entities in a carried group (`1-8`, default `8`).
- **Vehicle Boarding (`vehicleBoarding`)**
  - `enabled`: allow placing carried entities directly into boats or minecarts (default `true`).
  - `requireShift`: require sneaking when boarding into a vehicle (default `true`).
  - `allowBoats`: allow boarding into boats and chest boats (default `true`).
  - `allowMinecarts`: allow boarding into rideable minecarts (default `true`).
- **Portable Jukebox (`portableJukebox`)**
  - `enabled`: keep jukebox music playing while carried and resume seamlessly when placed (default `true`).
  - `emitParticles`: emit note particles and jukebox game events while carrying a playing jukebox (default `true`).
- **Blacklist Exceptions (`blacklistExceptions`)**
  - `enabled`: enable whitelist exceptions in Carry On's blacklist mode (default `true`).
  - `useCarryOnWhitelist`: treat Carry On's own `allowedBlocks`, `allowedEntities`, `allowedStacking`, and `#carryon:*_whitelist` tags as blacklist exceptions (default `true`).
  - `blocks` / `entities` / `stacking`: extra exception lists supporting exact IDs, `*` wildcards, and `#tags` (default `[]`).

## Requirements

- Carry On

## Source Code

- https://github.com/hp3081925122/Carry-On-Bug-Fix

---

**Carry On Bug Fix** 是 Carry On 的问题修复与体验增强附属模组。

## 功能

- **整组乘客搬运**：抱起装着村民的船、坐着生物的矿车，或者叠在一起的生物塔时，全部乘客都会一起抱起、手持渲染、放下、堆叠与死亡掉落，同时对每一位乘客继续执行 Carry On 的拾取规则校验。
- **抱着生物直接塞进船 / 矿车**：抱着单个生物、乘客叠罗汉或玩家时，潜行右键空船、运输船或普通矿车（也可直接右键船内已有乘客），即可直接让目标坐入空位。遵守原版座位数与体型限制、禁止载具套娃、上车失败自动回滚不吞怪不刷怪，且不会让玩家自己误坐进船/矿车，与“连船带乘客整组抱走”形成完整闭环。
- **便携移动唱片机（边走边放歌 + 放下无缝续播）**：抱起正在播放的唱片机时音乐不断点、不重头播放，声源随搬运者实时移动并周期产生音符粒子与游戏事件；放下后精准衔接当前进度并交接回原版方块播放表，退盘或挖掉唱片机均可正常停歌。联机场景下中途靠近或跨维度的玩家也会从当前精确时间点无缝续播。
- **黑名单 + 白名单豁免共存**：在 Carry On 默认黑名单模式下，允许通过白名单/豁免表放行被宽泛通配符（例如 `modid:*`）拉黑的指定方块、实体或堆叠目标。同时支持直接复用 Carry On 自带白名单与 `#carryon:*_whitelist` 标签，以及本模组独立豁免列表（支持精确 ID、`*` 通配符与 `#标签`）。
- **Carry On Extend 可选兼容**：安装 Carry On Extend 后，投掷一整组带有乘客的载具时仍会完整保留全部乘客。
- **修复搬运按键**：修复了搬运按键无法绑定鼠标按键，以及重新绑定后按键无响应的问题。

## 配置

配置文件位于 `carry_on_bug_fix-common.toml`（新版 FML 环境位于 `carry_on_bug_fix-local.toml`）。

- **整组乘客搬运（`passengerStack`）**
  - `enabled`：整组搬运与手持渲染开关（默认 `true`）。
  - `maxDepth`：一组最多允许的乘骑层数，包含最底层载具（`1-3`，默认 `3`）。
  - `maxEntities`：一组最多允许的实体总数（`1-8`，默认 `8`）。
- **直接塞进船 / 矿车（`vehicleBoarding`）**
  - `enabled`：是否允许抱着生物或玩家直接右键放入船或矿车（默认 `true`）。
  - `requireShift`：放入载具时是否要求按住潜行键（默认 `true`）。
  - `allowBoats`：是否允许放入普通船与运输船（默认 `true`）。
  - `allowMinecarts`：是否允许放入普通载客矿车（默认 `true`）。
- **便携移动唱片机（`portableJukebox`）**
  - `enabled`：是否启用抱起播放中唱片机边走边放歌与放下无缝续播（默认 `true`）。
  - `emitParticles`：携带播放中的唱片机时是否周期发射音符粒子并触发唱片机游戏事件（默认 `true`）。
- **黑名单白名单豁免共存（`blacklistExceptions`）**
  - `enabled`：是否在 Carry On 黑名单模式下启用白名单豁免放行（默认 `true`）。
  - `useCarryOnWhitelist`：是否直接将 Carry On 原自带的 `allowedBlocks`、`allowedEntities`、`allowedStacking` 与 `#carryon:*_whitelist` 标签作为黑名单例外（默认 `true`）。
  - `blocks` / `entities` / `stacking`：额外的方块、实体、堆叠豁免列表，支持精确 ID、`*` 通配符与 `#标签`（默认 `[]`）。

## 前置

- Carry On

## 源码

- https://github.com/hp3081925122/Carry-On-Bug-Fix
