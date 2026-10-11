# VALIDATION — v2.9.0（私库正统基线合并版）

## 构建门禁（私库工程：Gradle 9.2.1 + MDG 2.0.147 + Zulu 21.0.12.1）
- compileJava：BUILD SUCCESSFUL（8s；24 个既有弃用警告均为 EventBusSubscriber bus=Bus.MOD，compass 同款非本次引入）
- test：242/242 全绿，26 套件 0 失败 0 错误 0 跳过
- build：BUILD SUCCESSFUL → jar 534,056 B
- jar 内 neoforge.mods.toml version="2.9.0"（processResources ${modVersion} 单源注入验证）
- 语言文件 en/zh 320 键对称，GUI/指令引用键全覆盖（LangKeysTest 三断言）

## 声纹测试构成（8 套件 61 例）
- v2.9.0 新五件套 34 例：Oscillator 11 / Tracker 7 / Classifier 7 / Aggregator 6 / LangKeys 3
- 私库保留三件套 27 例：CaptureSafety 14 / Envelope 7 / Simulator 6（jar 反编译重建时丢失的测试以私库原版保留；三类 public API 面与 v2.9.0 实现零差异，编译与全量运行验证兼容）

## 根因实证链
1. /sp 无子命令：v2.8.0 反编译实证 buildRoots() 中 sp 根仅有 executes(openSettings)，sim 子树只挂在 soundprint/herrasoundprint 两个根下。
2. 顶部遮挡：v2.8.0 drawBackgroundAdditional 以 getPositionY()+0.5 为基准线（控件顶边），竖条上伸段全部进入方位条贴图区域 → 层序靠后被盖。本版基准线=控件垂直中心，控件高度=2×(maxHeight×1.35×1.8+ampMax+3)。
3. 无振动：v2.8.0 drawCluster 的 hUp/hDown 只由 (heightBase, bell, asym) 决定，无任何时间项。本版加入 ampPx×bell×pulse(t) 振荡项，pulse 由 SoundPrintOscillator.pulse 生成。
4. 闪烁（自脚步）：collectMovers 排除 mc.player → 自声无走动续期。本版在 ignore_own=false 时追加本地玩家进 movers。

## 振动数学（测试锁定）
- amplitude = lerp(minPx, maxPx, clamp(energy × distFactor × (1+(n-1)×stackAmpGain)))
- pulse(t) = 0.35 + 0.65×sin(2π·speedHz·t + clusterPhase + i×0.85)，值域 [-0.30, 1.00]
- stackedAlpha = clamp(base + (n-1)×gain, 0, 1)
- 距离因子复用 distanceDecay(dist, radius, minDecay, distanceDecay)（√ 衰减曲线）

## 新旧 jar 对账
- 本 jar（私库 tag 树构建，SHA256 5feceb3fdcbfe225d40002fd22126188a178aee18cda35b7faf20a96b55232ec）vs 期初交付 jar（重建工程构建，SHA256 96bf9426…ee33）：217 类清单完全一致；声纹 66 类（18 主类含内部类）字节级 100% 一致；非声纹类差异为私库手写源码 vs 反编译重建源码的编译产物等价差异（语义一致性经此前对账验证）；本 jar 额外注入 LICENSE / TEMPLATE_LICENSE.txt（私库 build.gradle 惯例）
- 交付原则：git tag v2.9.0 ↔ 源码 ↔ 构建产物闭环，Release 附件即本 jar

## 合并策略（增量落到私库基线 7faca0f = v2.8.0）
- soundprint 18 主类整包更新（17 覆盖 + Oscillator 新增）；测试 5 个落位（3 更新 + LangKeysTest/OscillatorTest 新增，私库三测试保留）；lang en/zh 纯追加 17 键 ×2（键值零冲突，逐键 diff 验证）；build.gradle 仅版本行 2.8.0→2.9.0
- 其余一切不动：compass/map/herra/stamina/wheel 等全部手写模块、DragInventory.java 主类、mods.toml、docs/、wiki/、gradle 工程与历史 releases 全部保留私库正统

## 复测要点（用户实机）
见 RELEASE_NOTES-v2.9.0.md 第二节七项清单。
