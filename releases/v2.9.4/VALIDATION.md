# Drag Inventory v2.9.4 VALIDATION — 振幅重调/亚像素渲染/叠加增益源码级锁定

日期：2026-10-08 · 基线：4d1c70f (v2.9.3) → 本版三段式提交 · 门禁：Gradle 9.2.1 / MDG 2.0.147 / NeoForge 21.1.251 / JDK Adoptium 21.0.12.1

## 一、振动幅度重调（WOBBLE_AMP_MIN/MAX）数学锁定

### 修改（SoundPrintOscillator 源码）
```java
static final float WOBBLE_AMP_MIN = 0.18F;  // 0.10F -> 0.18F
static final float WOBBLE_AMP_MAX = 0.42F;  // 0.26F -> 0.42F
```

### 与 v2.9.3 顶棚的相容性证明
- 顶棚数学（cappedBarHeight，v2.9.3 已锁）与幅度解耦：`ceiling = cap/(1+amp)`、`wobbled ≤ ceiling×(1+amp) = cap`——**对任意 amp ∈ [0,1] 恒成立**，amp 从 0.26 提到 0.42 不影响"永不越顶"上界；
- 满能量舞动区间：`h(+) = cap`、`h(-) = cap×(1-amp)/(1+amp)`；amp=0.42 → 下探 `0.58/1.42 = 0.408×cap`（v2.9.3 amp=0.26 为 0.587×cap）——峰谷比 1.0/0.408 ≈ **2.45 倍**（v2.9.3 为 1.70 倍），节律显著增强；
- 抖动关闭路径不变：`osc.enabled=false` 时 wobbleAmp=0 → 顶棚退化为精确 cap 钳制。

### 回归（OscillatorTest.wobbleAmplitudeBoundsAreTheV294RetunedValues 新增）
- `WOBBLE_AMP_MIN == 0.18`、`WOBBLE_AMP_MAX == 0.42` 精确断言（幅度重调防回退锁定）；
- `floor > 0.15`（静止律动可见性门槛）、`peak < 0.5`（保持条高比例语义，顶棚数学前提）；
- 既有 `wobbleAmplitudeScalesWithIntensityAndKeepsBounds`（符号常量断言）自动跟随新值通过。

## 二、亚像素渲染（fillQuadF/fillGradientF）源码级锁定

### 修改（SoundPrintWidget）
- 新原语：`fillQuadF`/`fillGradientF` = `pushPose → translate(x,y) → scale(w,h) → fill/fillGradient(0,0,1,1) → popPose`——顶点经 pose 矩阵映射到 `(x,y)-(x+w,y+h)` 连续小数坐标，**不再 Math.round 吸附整像素**；
- fillV 重写：两层柔光（外层 ±1.5px @ 9%、内层 ±0.5px @ 22%）+ 渐变主体（尖端 50%）+ 半透明盖行（62%）+ 短条 75% 平滑过渡；`h < 0.5` 或 `w ≤ 0` 跳过（退化守卫）；
- GuiGraphics.fill/fillGradient 逐顶点乘 pose 矩阵（CPU 端矩阵乘），单位方形 (0,0)-(1,1) 经 scale(w,h) 后即目标矩形——数学恒等，无渲染管线假设。

### 回归面审查
- 纯渲染层改动，不触几何/能量/聚合/配置键面；调用点仅 fillV 内部重构，drawCluster→fillV 签名不变；
- 渐变方向语义与 v2.9.2 一致（tipUp：尖端在上从 tip→base；tipDown：base→tip），fillGradient(0,0,1,1) 在缩放空间内插值跨全高。

## 三、叠加高度增益（stackHeightBoost）数学锁定

### 实现
```java
static final float STACK_HEIGHT_GAIN = 0.18F;
static final int STACK_HEIGHT_CAP_SOURCES = 3;
public static float stackHeightBoost(int sourceCount) {
    int extra = Math.min(Math.max(sourceCount - 1, 0), STACK_HEIGHT_CAP_SOURCES);
    return 1.0F + extra * STACK_HEIGHT_GAIN;
}
```
- 值域：`sourceCount ≤ 1 → 1.0`；2→1.18、3→1.36、4→1.54；**≥4 恒 1.54（封顶）**；负数/零容错归 1.0；
- 接线：`heightBase` 与 `capSide` 同乘 stackBoost（Widget.drawCluster）——顶棚随叠加等比放大，**叠加簇永不越 `max_height × 类型加成 × 1.54`**；
- HUD 预留带复核：`Hud.halfBand = max_height×1.35×1.8 + ampMax + 3`，max_height=30 时半带 ≈ 82.6px；叠加枪声满高 `30×1.35×1.54 ≈ 62.4px × 垂直不对称 1.75 ≈ 顶棚按侧钳 ≤ 62.4px` < 82.6px——**预留带足够，无遮挡风险**。

### 回归（OscillatorTest +2）
- `stackHeightBoostGrowsPerSourceAndCaps`：1/2/3/4/7/64 源精确断言（1.0/1.18/1.36/1.54/1.54/1.54）；
- `stackHeightBoostToleratesBadCounts`：0 与 -3 源 → 1.0（防御性）。

## 四、默认值与一次性迁移锁定

### spec 源码值
- `max_height`：`defineInRange("max_height", 30, 8, 56)`（默认 24→30）；
- `stack_alpha_gain`：`defineInRange("stack_alpha_gain", 0.30, 0.0, 1.0)`（默认 0.22→0.30）；
- 新标记 `v294_retuned`（general 段，默认 false，不进设置 UI）。

### 迁移（SoundPrintConfigEvents.retuneV294Defaults，v2.8.0 retune 纪律）
- 仅在 Loading 事件且 `v294_retuned=false` 时执行一次：`max_height == 24 → set 30`；`stack_alpha_gain == 0.22 → set 0.30`；写标记 + flush；
- **只动仍是旧默认值的项**——玩家自定值（如手动调过 max_height=40）永远保留；迁移后标记落盘，此后手动改回 24 也不再被碰；
- resetToDefaults 走 spec getDefault——"恢复默认"即得 30/0.30 新值，无需迁移。

## 五、透明度层次与回归零改动面

- drawCluster 两处系数：`(0.4+0.6×energy) → (0.55+0.45×energy)`、`(0.45+0.55×bell) → (0.62+0.38×bell)`；drawSoundprint 基线 `0.65 → 0.80`——纯常量替换，无控制流变化；满能量端点（1.0）不变，能量可读性保留；
- 零改动面清单：能量模型（Tracker）/聚合（Aggregator）/采集（CaptureRules/Classifier）/模拟器（Simulator）/指令（Commands）/设置界面（SettingsScreen）/lang 键/位置预设七值——**v2.9.3 固化的出厂位置预设与顶棚数学零触碰**。

## 六、门禁与产物对账

- 沙箱第 6 次重置后冷构建重放：Adoptium 21 下载 → NFRT vineflower 补丁 4096→2600 → compileJava/test/build 三连 BUILD SUCCESSFUL；
- **253 用例 0 失败**（250 + 3 新增；JUnit XML 报告复核：新增两测试类方法名均命中、tests=253 failures=0 errors=0）；
- jar 537,455B；`unzip -p META-INF/neoforge.mods.toml` 实测 `version="2.9.4"`（v2.9.3 缓存陷阱复查：inputs.property('modVersion') 修复持续有效）。
