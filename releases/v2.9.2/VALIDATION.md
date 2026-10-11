# v2.9.2 验证记录

## 交付范围（两项实测反馈）

1. 声纹常驻随机振动：有声源即每条独立随机收缩/拉长，幅度随响度，仅无声渐弱；
2. 声纹渲染现代风格：渐变/柔光/圆角收头，去像素化；几何布局零改动。

## 改动面（源码级）

| 文件 | 改动 |
|---|---|
| `SoundPrintOscillator.java` | 移除 `pulse()/PULSE_FLOOR/BAR_PHASE_STEP`；新增 `wobble()/wobbleAmplitude()/noise1D()/hash01()` 与常量 `WOBBLE_AMP_MIN=0.10/WOBBLE_AMP_MAX=0.26/WOBBLE_SLOW_CELL_SEC=0.16/WOBBLE_FAST_CELL_SEC=0.065/WOBBLE_SLOW_MIX=0.68`；`amplitude()/clusterPhase()/stackedAlpha()` 保留（箭头伸展/簇相位/叠加透明度） |
| `SoundPrintWidget.java` | drawCluster：`pulse→wobble` 乘法模型（`h = heightBase·bell·asym·(1+wobbleAmp·wobble)`），fillV 增加 tipUp 方向参数；fillV：1px 柔光外晕 + fillGradient 垂直渐变（基线侧实/尖端透 30%）+ 圆角或柔化收头；fillH：两端 12px 渐隐 + 1px 底部柔影；ampPx 保留仅用于箭头 reach |
| `SoundPrintConfig.java` | animation 段注释更新（oscillate 语义/osc_speed 节奏/osc_amp 箭头专用）；键与默认值零变化 |
| `lang/en_us.json` + `zh_cn.json` | 各 4 行：oscillate/osc_speed/osc_amp_min/osc_amp_max 标签（键数对称不变，LangKeys 套件通过） |
| `SoundPrintOscillatorTest.java` | -3 pulse 例 +6 wobble 例 = 14 例 |
| `build.gradle` / `README.md` | 版本 2.9.1→2.9.2；README changelog 增 v2.9.2 条目 |

## 抖动数学（源码级锁定）

- `wobble(t, speed, phase, i) = 0.68·noise1D(phase, i, 1/0.16, t·tempo) + 0.32·noise1D(phase·1.618+0.5, i+101, 1/0.065, t·tempo)`，`tempo = max(0.05, speed)/3.5`；
- `noise1D`：格点值 = `hash01(seed, bar, cell)·2-1`，格间 smoothstep 插值 —— 连续有界；
- `hash01`：floatToIntBits(seed) ^ bar·0x9E3779B1 rotate-13 → ×0x85EBCA6B → ^ cell·0xC2B2AE3D → 雪崩混合 → [0,1)；
- `wobbleAmplitude = 0.10 + 0.16·intensity`，`intensity = clamp(energy·distFactor·(1+(n-1)·stackGain), 0, 1)`；
- 边界：|wobble| ≤ 0.68+0.32 = 1（数学保证），条高缩放 ∈ [1-amp, 1+amp] ⊂ [0.74, 1.26]，最大条高 1.26×maxHeight×heightBoost 仍在 v2.9.1 布局预留带（maxHeight×1.35×1.8 + ampMax）内，Hud syncLayout 零改动。

## 门禁

- 环境：沙箱重置后冷构建（Adoptium JDK 21.0.12.1 落地 /home/z/my-project/tools/；NFRT 缓存重建 + patch_nfrt_xmx.py 补丁重放 4096→2600m——vineflower 子进程不吃 build.gradle 的 JavaExec maxHeapSize，硬编码堆必须字节补丁，与 Task 11/12 同因）；
- compileJava / test / build 三连 BUILD SUCCESSFUL（Gradle 9.2.1 --no-daemon，Xmx2G）；
- test：26 套件 248 用例，0 失败 0 错误 0 跳过（对 v2.9.1 的 245：Oscillator -3+6）；
- 既有 24 弃用警告全部为 EventBusSubscriber bus=Bus.MOD 历史项（v2.9.1 同款，非本版引入）；
- jar 536,141B；mods.toml version=2.9.2（project.version 单源注入）。

## 回归风险审查

- 声纹能量/收尾模型（Tracker/linger）零改动——v2.9.1 十例回归全绿即证；
- 几何布局（条数/条宽/间距/位置预设）零改动——位置预设与 v2.9.1 完全一致；
- 配置键面零增删，oscillate=false 语义退化为纯响度高度（静止），兼容老配置；
- osc_amp_min/max_px 语义收窄为箭头专用（README/配置注释/语言键三处同步说明），老值不改写不报错；
- 模拟器/设置预览与 HUD 共用 drawSoundprint 同一抖动函数，三路一致性由"纯函数"结构性保证。
