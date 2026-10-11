# VALIDATION — v2.9.1（私库增量：活振动模型）

## 构建门禁（私库工程：Gradle 9.2.1 + MDG 2.0.147 + NeoForge 21.1.251）
- compileJava：BUILD SUCCESSFUL（2m45s 含 NFRT 全管线；24 个既有弃用警告均为 EventBusSubscriber bus=Bus.MOD，compass 同款非本次引入）
- test：245/245 全绿，26 套件 0 失败 0 错误 0 跳过
- build：BUILD SUCCESSFUL → jar 534,924 B
- jar 内 neoforge.mods.toml version="2.9.1"（processResources ${modVersion} 单源注入验证）
- 语言文件 en/zh 320 键对称不变（本版仅改 linger 标签 1 行 ×2，键数不变）

## 声纹测试构成（8 套件 64 例）
- Tracker 重写 10 例（活能量回归）：呼吸不熄灭 / 响度定高度 / 尾速常数单调（枪声快于脚步）/ 无声收拢到零后才移除 / 走动续期 / 枪声抗续期 / 自声 / 过期 / 命中承载 / 弹簧收敛
- 既有七件套 54 例零改动：Oscillator 11 / Classifier 7 / Aggregator 6 / LangKeys 3 / CaptureSafety 14 / Envelope 7 / Simulator 6

## 根因实证链（v2.9.0 白条残留）
1. Widget heightBase 带 0.4 静态下限：能量衰减到 0 后竖条仍钉在约 40% 高度不动。
2. Tracker 为"满能量保持 linger 650ms → 线性淡出"模型：声停后整簇静止满高再线性衰减；叠加 1 = 静止白条驻留约 2 秒后跳没。
3. 本版修复：高度=实时响度直达（下限删除）+ live 指数衰减 + 基线随最响声源淡出 → 无声整体连续收拢到零，无残留无突变（10 例回归锁定）。

## 活能量数学（源码级，测试锁定）
- 事件到达：live = max(live, energy)——跳到最新样本响度（attack 语义）
- 指数收尾：tailSec = max(0.05, linger_ms/1000) × type.tailScale()（枪声 0.6）；live *= exp(-elapsedSec/tailSec)，elapsed 按源自身上次刷新时间戳计量（防新样本被整段间隔误衰减）
- 静默归零：live < 0.02（LIVE_SILENCE）→ 0
- 显示目标：target = live × distanceDecay(dist, radius, minDecay, distanceDecay)；display 经临界阻尼弹簧闭式解收敛
- 走动续期：live = max(live, motionEnergy(speed) × 0.9)（走动实体按连续声处理）
- 过期判据：live ≤ 0 && display < 0.02 && 静默 > tail + 200ms

## 新旧 jar 对账
- 本 jar（私库增量构建，SHA256 f131ad62…）vs 反编译重建工程 jar（535,045B，SHA256 0c4abe29…）：217 类清单完全一致；声纹 33 个 .class（18 主类编译产物）字节级 100% 一致——私库增量移植精确性实证；非声纹 174/184 差异为私库手写源码 vs 反编译重建源码的编译产物等价差异（语义一致性经 v2.9.0 对账验证）
- 交付原则：git tag v2.9.1 ↔ 源码 ↔ 构建产物闭环，Release 附件即本 jar

## 合并策略（增量落到私库基线 18572dc = v2.9.0）
- 前置验证：私库 v2.9.0 声纹 18 主类与反编译重建基线 diff -rq 文本级完全一致后才整文件落位（RC=0）
- soundprint 6 主类更新（Commands/Config/SettingsScreen/Tracker/Type/Widget，+194/-63）；TrackerTest 重写（10 例）；lang en/zh 各 1 行（linger 标签：声纹停留→收尾衰减 / Linger before fade→Tail decay）；build.gradle 仅版本行 2.9.0→2.9.1
- 其余一切不动：compass/map/herra 等手写模块、docs/、wiki/、gradle 工程与历史 releases 全部保留私库正统

## 复测要点（用户实机）
见 RELEASE_NOTES-v2.9.1.md 复测清单七项。
