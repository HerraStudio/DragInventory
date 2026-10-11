# Drag Inventory v2.9.5 VALIDATION — 用户预设固化/箭头方向/箭头落点源码级锁定

日期：2026-10-11 · 基线：a6c4f2d (v2.9.4) → 本版三段式提交 · 门禁：Gradle 9.2.1 / MDG 2.0.147 / NeoForge 21.1.251 / JDK Adoptium 21.0.12.1 · 沙箱第 7 次重置后冷构建重放

## 一、用户预设固化（六值 + v295_retuned 迁移）纪律锁定

### 修改（SoundPrintConfig + SoundPrintConfigEvents）
- 六项新出厂默认：`bar_count 14→21`、`bar_width 2→1`、`detect_own_gunshots true→false`、`osc_speed_hz 3.5→5.6`、`osc_amp_max_px 9→24`、`offset_y_px -174→-143`——与用户回传 toml 逐项核对一致，其余键零改动；
- `v295_retuned` 新标记 + `retuneV295Defaults()` 一次性迁移：**每项只在等于旧默认值时才写入**（bar_count==14 / bar_width==2 / own_gunshots==true / osc_speed==3.5 / amp_max==9 / offset_y==-174），写完落标记 flush——v2.8.0 shape_retuned / v2.9.4 v294_retuned 同款纪律。

### 迁移矩阵推演（全路径核对）
| 场景 | bar_count | 行为 |
|---|---|---|
| 用户本人文件（六值全已等于新默认） | 21 | 不触碰任何项，只落 v295_retuned=true |
| v2.9.4 默认配置升级 | 14 | 14→21（其余五项同理收敛） |
| 自定义 bar_count=18 升级 | 18 | 18≠14 → **保留 18**，其余等于旧默认的项仍收敛 |
| 全新安装（无文件） | 21（代码默认） | 迁移六项全不等于旧默认 → 只落标记，无多余写入 |
| v2.8.0 时代老文件（bar_count=14, shape_retuned=true） | 14 | shape/v294/v295 三迁移链式收敛，最终 21 |

- 浮点等值：osc_speed 旧默认 3.5 二进制精确（7/2），`== 3.5D` 判定无歧义（v294 的 0.22 判定同款先例）；
- 迁移只在 Loading 事件跑一次，Reloading（外部热重载）不重跑——标记落盘后永久闭锁。

## 二、箭头方向反转修复（arrowRowSpread）几何证明

### 根因几何（v2.9.2 缺陷）
行序自顶向下：`y = cy - h + i`，`t = (i+0.5)/rows ∈ (0,1)` 随 i 单调增（i=0 顶行）。
- 旧式 `spread = up ? 1-t : t`：`up=true` 顶行 t≈0 → spread≈1（**最宽在顶**）、底行收尖 → 形成 "v"（尖朝下）——**上箭头画成下尖**；
- 实测吻合：声源在下方（verticalOffset<0）→ else 分支 up=false → 顶窄底宽 → "^"（尖朝上）→ "下方位对、箭头朝上指"，与用户 v2.9.4 实测反馈逐字吻合。

### v2.9.5 修复几何（SoundPrintEnvelope.arrowRowSpread）
```java
static float arrowRowSpread(boolean up, float t) {
    float c = clamp(t, 0.0F, 1.0F);
    return up ? c : 1.0F - c;
}
```
- `up=true`：spread=t → 顶行 0（收尖）、底行 1（张臂）→ "^" 尖朝上 ✓；
- `up=false`：spread=1-t → 顶行 1（张臂）、底行 0（收尖）→ "v" 尖朝下 ✓；
- 与参考图 IMG2（下方声源 → 底部无杆 V 尖朝下）一致；
- 回归锁定：`arrowRowSpreadPointsUpAndDownCorrectly`（新增）——上箭头 t=0→0 / t=1→1、下箭头镜像、单调性、越界 t=±5 钳制 [0,1]。

## 三、箭头落点锚定（上方箭头消失根治）数学锁定

### 根因数值例（v2.9.4 落点为何陷条）
取 max_height=30、脚步（boost=1）、单源、energy=0.5、近距离：
- `heightBase = 30×1×1×0.5×1 = 15`；旧 `reach = 15×0.92+3.5+amp×0.6 ≈ 17.3`（amp≈4）；
- 上方声源 asym[0] = 1+0.75×a（a=0.75 时）= **1.56**；中心条 `hUp ≈ capped(15×1×1.56×(1+wobble)) → 逼近 cap=30`（顶棚内）；
- 箭头区间 `[baselineY-reach-5.6, baselineY-reach-2] ≈ [22.9, 26.5]` vs 条堆 `[0, 30]`——**箭头整体落在白条堆内**，同色同调被吃掉。下方箭头偶见是因为强能量时 reach 反超条高，用户 amp=24 拉大 reach 后更常幸存——与"下方偶见、上方全无"的实测分布吻合。

### v2.9.5 落点公式（drawCluster）
```java
// 逐条追踪实际绘制（≥1px 才计入）：
if (hUp >= 1.0F)   { fillV(...); maxUp = Math.max(maxUp, hUp); }
if (hDown >= 1.0F) { fillV(...); maxDown = Math.max(maxDown, hDown); }
// 箭头锚定实际条高外侧：
up:   cy = baselineY - maxUp - 2.0F - stretch          // 宽底贴条堆顶上方 2px
down: cy = baselineY + maxDown + 7.1F + stretch        // = 1.5(下条偏移)+2.0(间隙)+3.6(箭头高)
stretch = ampPx × 0.6F                                  // osc_amp 伸展通道保留（用户 24px 上限生效）
```
- **不变量**：箭头最近边距条堆边缘恒 ≥2px（up：`baselineY-maxUp-2` 恰为条顶上方 2px；down：`cy-h = baselineY+1.5+maxDown+2` 恰为条底下方 2px）——对任意 energy/asym/wobble 组合成立；
- 伸展单调性：`amplitude()`（intensity=energy×dist×boost 插值 [min,max]）不减 → stretch 不减 → 越响越近箭头离条堆越远，与 v2.9.2 "伸展振幅"语义兼容；
- 双侧对称：up/down 公式镜像（up 条堆在基线上方、down 在下方），垂直交战双向可读。

### 显形阈值（arrowStrength）
- 死区 0.22→0.15：`(sourceY-eyeY)/4` 换算，显形起点 0.88 格→**0.6 格**，满强度 0.65（2.6 格）不变；
- 中段斜率 `(a-0.15)/0.5`，a∈(0.15,0.65) 线性淡入，与 0.05 门槛/0.95 系数叠加后无跳变（连续性：a=0.15+ 处 strength≈0+，arrowAlpha 门槛 0.02 平滑截断）；
- 回归：`arrowStrengthHasDeadZoneAndFullZone` 更新——死区断言 0.2→0.14（0.15 边界内），新增 0.2/0.16 浅高差淡入单调断言。

## 四、渲染路径与零改动面审查

- drawVArrow 仍走整像素 `g.fill`（箭头是 1px 点阵 V 形，无连续坐标需求），方向修复不动任何坐标数学，只换 spread 表达式来源（arrowRowSpread）——**渲染成本不变**（每箭头 ≤4 行 × 2 fill）；
- maxUp/maxDown 追踪为 O(barCount) 常数级，无新分配；簇循环内 `Math.max` 21 次上限（bar_count 上限 21）；
- 零改动面：能量模型（Tracker/Aggregator）/顶棚数学（cappedBarHeight）/亚像素渲染（fillV/fillQuadF/fillGradientF）/基线（fillH）/采集链/指令树/设置界面/lang 键/位置预设其余六值（preset/width/spacing/offset_x/opacity/scale）零触碰；
- 预览链路（settings preview previewHits 垂直偏移 -0.55/-0.2/+0.6）自动继承修复——设置预览中第三簇（+0.6 枪声）将显示正确的上指箭头，可作 gui 内快速验证。

## 五、门禁对账

- 冷构建重放：Adoptium 21 下载 + Gradle 9.2.1 + NFRT vineflower 补丁 4096→2600m（沙箱 4GB OOM 规避，第 4 次重置起的标准舞步）；
- 三连：`compileJava` ✓ / `test` ✓（**254 用例**：253 存量 + 1 新增 arrowRowSpread；arrowStrength 死区断言随 0.15 更新，无删除用例）/ `build` ✓；
- jar 内置 `neoforge.mods.toml` version=**2.9.5** 验证通过（`inputs.property('modVersion')` 缓存根治后首次新版本发布，复查通过）；
- 发布物料：releases/v2.9.5/ 五件套（jar / RELEASE_NOTES / VALIDATION / SHA256SUMS / project zip），download/ 权威 jar 同步。
