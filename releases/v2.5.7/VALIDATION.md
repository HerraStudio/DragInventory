# v2.5.7 验证记录（导航箭头图标 · 动效优化 · 联动 API）

## 变更清单

| # | 变更 | 文件 |
| --- | --- | --- |
| 1 | 玩家图标依用户 SVG 换导航箭头（#32CD32 原色，12.5px，缩小约 22%） | FactoryMapUI.java |
| 2 | 三处渲染入口改喂 partialTick 插值坐标（消除 20Hz tick 台阶） | FactoryMinimapHud / FactoryMapScreen / MapSettingsScreen |
| 3 | 图标与尾迹亚像素 float 绘制（去 Mth.floor 取整台阶） | 同上 |
| 4 | 平滑时间常数 140→120ms（插值输入后更跟手） | PlayerMarkerFX.java |
| 5 | 逐标点 TTL（四参便捷构造保持玩家 60 秒口径） | TacticalMarker.java |
| 6 | 外部标点 API（稳定 ID + 独立 16 名额 + 自定义 TTL + 幂等更新） | TacticalMarkerManager.java / TacticalMarkerLogic.java |
| 7 | 标点事件监听器（四因，异常隔离，先收集后移除再触发） | TacticalMarkerListener.java（新）/ TacticalMarkerManager.java |
| 8 | 世界 HUD / 方位条改走合并视图（外部标点四处可见） | TacticalMarkerHud.java / CompassMarkerBridge.java |
| 9 | 计时扩展：加时/减时、截止时刻、Provider 自省、结束沿回调 | MapMatchTimer.java / MapClientEvents.java |
| 10 | POI 注销 | MapPoiProvider.java |
| 11 | 统一门面 TacticalMapApi | TacticalMapApi.java（新） |
| 12 | wiki《战术地图API》联动接口文档 + 三处交叉链接 | wiki/战术地图API.md（新）/ Home.md / 战术地图.md / README.md |

## 门禁

- 分三段小步提交（图标 c5dc75d → 动效 c7be6cb → API 68ed41e → 文档 261e9c2），每段独立过 compileJava + test 门禁后推送；
- 最终 `gradlew build`：BUILD SUCCESSFUL，全套 **177 用例 0 失败 0 错误**（168 + 新增 9：外部容量控制 4 + 计时扩展 5）；
- jar：`drag-inventory-1.21.1-neoforge-2.5.7.jar`，409,070 字节，SHA256 `2d56d41c3f110202005f3928539b855a3cc55517f16e031f0dfd6d265bd578eb`；
- jar 内复核：TacticalMapApi / TacticalMarkerListener（含 Cause）/ PlayerMarkerFX / FactoryMapUI 字节码均在；构造期 banner 模板串在（版本号运行时注入自 mods.toml=2.5.7）；
- 版本单一源核对：build.gradle 与 neoforge.mods.toml 均为 2.5.7；README（版本行/主段/changelog/jar 名三处）/ wiki（头部/快速上手/版本历史）全量 grep 无 2.5.6 残留（历史段落除外）。

## 开发中修复的问题

1. **编译错误（16-a）**：`worldToScreenX` 返回 double 直传 float 形参需显式转换（原代码靠 Mth.floor 返回 int 隐式拓宽掩盖）——补 `(float)` 转换。
2. **编译错误（16-b）**：1.21.1 Mojang 映射 `Entity.getYRot()` 无插值重载——本地玩家朝向本就鼠标驱动帧连续，改回直取 `getYRot()`（位置仍用 `getPosition(partial)` 插值，这才是 tick 量化的量）。
3. **测试期望写错（16-c）**：`externalEvictionPredicateCanExemptTheUpdatingKey` 初版对"豁免谓词"语义推演失误（predicate 排除更新键 → 计数不含它 → 容量内不逐出），期望了不会发生的逐出——重写为两枚符合真实语义的用例（新增不挤自己 + 原地更新不自逐出），实现本身无误。
4. **README changelog 编辑事故（16-a）**：MultiEdit 替换 2.5.6 段标题时旧正文变孤儿——立即补回标题行；同轮发现 MultiEdit 为顺序应用遇错即停（非原子），错误信息"未替换"仅指失败那条——此后每次编辑后核对文件实际状态。
5. **wiki 快速上手 jar 名遗漏**：v2.5.6 发布时 wiki《战术地图》快速上手段的 jar 名停在 2.5.5——本轮全量 grep 版本串时抓到并修复。

## 设计决策存档

- **外部标点独立池而非共用池**：世界 HUD 与方位条直接读 MARKERS 表；共用池会令 5 名额 FIFO 逻辑（重度单测覆盖）与外部名额互相干扰。独立池 + `allMarkers()` 合并视图 = 零回归风险，四个消费方一处不改语义地拿到全量。
- **事件先收集后移除再触发**：监听器回调内若同步调用放置 API 会并发修改正在遍历的表；collect-then-mutate-then-fire 模式根治。
- **结束沿判定 `>0 → 0` 严格相等**：停止（off）使 remaining 变 -1，若用 `<=0` 会把"隐藏"误判为"结束"。
- **addSeconds 在 Provider 模式无操作**：Provider 模式下时间归对局系统所有，模组侧平移 deadline 越俎代庖；文档明确"要加减时请在对局系统侧改，由 Provider 自然下发"。

## 遗留

- 实机动效观感（插值平滑度/120ms 手感/导航箭头尺寸）为参数化主观项：几何在 FactoryMapUI 常量区、动效参数在 PlayerMarkerFX 常量区，可一键微调；
- POI 渲染位已预留但图标样式随对局系统上线逐步点亮（当前 providers 恒空，零开销）；
- mapSmoke 显示环境验证需用户实机执行。
