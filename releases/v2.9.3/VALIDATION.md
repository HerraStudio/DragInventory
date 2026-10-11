# Drag Inventory v2.9.3 VALIDATION — 顶棚数学与出厂预设源码级锁定

日期：2026-10-08 · 基线：cb88de7 (v2.9.2) → 本版三段式提交 · 门禁：Gradle 9.2.1 / MDG 2.0.147 / NeoForge 21.1.251 / JDK Adoptium 21.0.12.1

## 一、竖条高度顶棚（cappedBarHeight）数学锁定

### 越顶证据链（修复前，源码级）
- v2.9.2 `SoundPrintWidget.drawCluster`：`hUp = max(0, heightBase × bell × asym[0] × scale)`，其中 `heightBase = maxHeight × type.heightBoost() × sizeScale × energy`、`scale = 1 + wobbleAmp × wobble`；
- 乘子上界（各常量源码锁定）：`heightBoost(GUNSHOT)=1.35`（SoundPrintType）、`asymmetryFactors ∈ [0.12, 1.75]`（SoundPrintEnvelope.asymmetryFactors：`1 + max(0,a)×0.75`，a ∈ [-1,1]）、`wobbleAmplitude ∈ [0.10, 0.26]` 且 `|wobble| ≤ 1`（SoundPrintOscillator）；
- 全叠加：`24 × 1.35 × 1.75 × 1.26 ≈ 71.5px`——max_height=24 配置渲染 ~3 倍越顶，实锤。

### 顶棚实现（修复后）
```java
static float cappedBarHeight(float rawBase, float cap, float wobbleAmp, float wobble) {
    if (cap <= 0.0F) return 0.0F;
    float amp = clamp(wobbleAmp, 0.0F, 1.0F);
    float ceiling = amp > 0.0F ? cap / (1.0F + amp) : cap;
    float clamped = clamp(rawBase, 0.0F, ceiling);
    float wobbled = clamped * (1.0F + amp * clamp(wobble, -1.0F, 1.0F));
    return clamp(wobbled, 0.0F, cap);
}
```
- 上界证明：`clamped ≤ cap/(1+amp)`，`wobbled ≤ clamped×(1+amp) ≤ cap`；输出再钳 `[0, cap]`——**任何 rawBase ≥ 0、amp ∈ [0,1]、wobble ∈ [-1,1] 组合恒 ≤ cap**（外层 clamp 兜底浮点误差）；
- 节律证明：rawBase ≥ ceiling 时 `h(+) = ceiling×(1+amp) = cap`，`h(-) = ceiling×(1-amp) = cap×(1-amp)/(1+amp)`——amp=0.10 时下探 0.818×cap、amp=0.26 时下探 0.587×cap，满能量条摆幅 18%~41%，**舞动可见**；
- 接线：`capSide = maxHeight × type.heightBoost() × sizeScale`（错位 0.82 等比）；`osc.enabled=false` 时 wobbleAmp=0 → 顶棚退化为精确 cap 钳制（用户主动关振动 = 静止语义，v2.9.2 不变）。

### 回归测试（SoundPrintEnvelopeTest +2，全套 250）
1. `cappedBarHeightNeverExceedsCapForAnyMultiplierStack`：cap=24×1.35、amp=0.26，raw ∈ [0, 3×cap+1] 步进 1.37 × wobble ∈ {-1..1} 步进 0.07 全参数扫描（~2300 断言）恒 `0 ≤ h ≤ cap+1e-4`；amp=0 时精确 cap；cap ≤ 0 归零；
2. `cappedBarHeightKeepsSmallBarsExactAndFullEnergyBarsDancing`：顶棚下小条原样通过（`raw×(1±amp)` 精确断言）；满能量条 `h(+) = cap`、`h(-) = cap×(1-amp)/(1+amp)` 精确断言 + 摆幅 ≥ cap×amp×0.5 可见性门槛。

## 二、出厂预设七值（SoundPrintConfig 静态块源码锁定）

| 配置键 | 默认值（v2.9.3 源码） | 用户预设 |
|---|---|---|
| style.opacity | `defineInRange("opacity", 1.0, 0.15, 1.0)` | 1.00 ✓ |
| position.spacing | `defineInRange("spacing", 0, 0, 20)` | 0 px ✓ |
| style.scale | `defineInRange("scale", 1.0, 0.5, 2.0)` | 1.00 ✓ |
| position.preset | `define("preset", "bottom_center")` | Bottom center ✓ |
| position.offset_x_px | `defineInRange("offset_x_px", 0, -240, 240)` | +0 px ✓ |
| position.offset_y_px | `defineInRange("offset_y_px", -174, -240, 240)` | -174 px ✓ |
| position.width_px | `defineInRange("width_px", 380, 60, 480)` | 380 px ✓ |

- 恢复默认路径：`SoundPrintConfig.resetToDefaults()` 遍历 ALL 45 项 `resetOne(value) → value.set(value.getDefault())`——`getDefault()` 即上表 spec 默认；GUI 页脚按钮与 `/soundprint reset` 均调此函数（v2.9.0 起既有路径，本版零改动）；
- 迁移冲突排除：`SoundPrintConfigEvents.retuneShapeDefaults` 仅在 Loading 事件且 SHAPE_RETUNED=false 时改 BAR_COUNT/BAR_GAP（恢复默认后这两项已=14/3，迁移空转），**不触碰位置组**——恢复默认后无任何路径覆写七值；
- 老配置兼容：默认值变更只影响 spec default，已有 toml 值由 NeoForge 保留（无 Loading 迁移，无强制覆写）。

## 三、门禁记录

| 阶段 | 结果 |
|---|---|
| compileJava | BUILD SUCCESSFUL（增量；冷构建 NFRT 补丁 4096→2600m 重放于预热期） |
| test | 26 套件 250 用例 0 失败 0 错误 0 跳过（XML 报告逐套解析） |
| build | BUILD SUCCESSFUL；jar 536,506B |
| jar 内置版本 | `unzip -p … META-INF/neoforge.mods.toml` → `version="2.9.3"` ✓ |
| jar SHA256 | `5d140a3694f14966fe128edbb53791c3a7ee6df72125bf8052321a35f2b273ff` |

## 四、构建缓存陷阱修复（processResources）

- 现象：版本 2.9.2→2.9.3 bump 后 `build` 输出 jar 内 `neoforge.mods.toml` 仍为 `version="2.9.2"`（processResources UP-TO-DATE 误判，`org.gradle.caching=true` 下缓存命中旧产物）；
- 根因：`filesMatching('META-INF/neoforge.mods.toml'){ expand 'modVersion': … }` 的注入映射不参与该任务缓存键；
- 修复：`inputs.property('modVersion', project.version.toString())` 显式声明输入——缓存键随版本变更；
- 验证：`cleanProcessResources processResources build` 重跑后 jar 内 `version="2.9.3"` ✓；v2.9.2 及之前冷构建无此问题（每次全量执行）。

## 五、回归风险审查（零改动面）

- SoundPrintTracker（活能量模型）/ SoundPrintOscillator（振动数学）/ SoundPrintEvents + CaptureRules（采集链）/ SoundPrintAggregator（分簇）/ SoundPrintHud（布局与位置预设引擎）/ SoundPrintCommands / SoundPrintSettingsScreen（GUI 控件绑定）/ 语言键：**全部零改动**（`git diff v2.9.2..HEAD --stat` 核对：仅 Config 默认值与注释、Envelope 新函数、Widget 条高计算、build.gradle 两处、README、测试 +2）；
- 视觉不变量：布局预留带公式与基线位置零改动 → 用户 offset_y=-174 等位置微调落点与 v2.9.1/v2.9.2 完全一致；渐变/柔光/圆角/箭头渲染路径零改动。
