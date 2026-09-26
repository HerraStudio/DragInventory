# HERRA 方位条 HUD（罗盘条）— 交付文档

> 模组：Drag Inventory（`draginventory`）· Minecraft 1.21.1 · NeoForge 21.1.251 · LDLib2 2.2.40
> 版本：1.4.9（commit ea2a681）· 风格参考：《三角洲行动》顶部方位条

---

## 1. 功能总览

- **顶部中央方位条**：细线条半透明、非像素风平滑字体（LDLib2 SDF 字体），中间高亮当前朝向（如 `206`），两侧滚动显示主方向文字（北/东北/东…）与角度刻度（60/120/150…），主方向大、角度数字小。
- **朝向逻辑**：读取玩家 yaw 映射到 `[0, 360)`，0 = 北、顺时针增大；显示范围、刻度密度、标签内容全部可配置。
- **动画**：临界阻尼弹簧（精确闭式解，无过冲无抖动），359°↔0° 跨越走最短角路径不反向甩动；停止转向后自然收尾；入场动画、透明度渐变、朝向吸附辅助（snap assist）与惯性倾斜（视觉装饰）均可开关。
- **标点联动**：只读接入现有战术标点体系，标点在方位条对应方位以与中心高亮色**保证不撞色**的颜色（`CompassPalette.distinctFrom` 自动色相旋转）提示；可指令/UI 开关。
- **4 套皮肤**：`minimal`（默认极简细线）/ `glass`（毛玻璃）/ `tactical`（战术）/ `neon`（霓虹），每套 × 5 组配色（默认/琥珀/绯红/翠青/冰蓝），颜色均可逐项覆盖。
- **图形化设置界面**：`/compass gui` 唤出 LDLib2 现代化 UI（TabView + 滑条 + 开关 + 颜色选择器），拖动即实时预览、停顿 500ms 自动落盘。
- **与现有 HUD 共存**：独立 GUI layer 注册（`RegisterGuiLayersEvent`），不触碰浩白的快捷栏/体力血条/枪械 HUD 任何代码；默认位置顶部中央，可用 offset 调整避让。
- **联动预留**：`CompassApi` 静态门面，供其他 HERRA 模组软依赖读取朝向/角速度、注册标点提供者、监听基数方位事件。

## 2. 新增文件清单（2684 行 Java + 50 行桥 + 语言文件）

### 2.1 `src/main/java/dev/draginventory/client/compass/`（核心包，21 文件）

| 文件 | 行数 | 职责 |
|---|---|---|
| `CompassWidget.java` | 413 | 核心渲染部件：罗盘条绘制、刻度/标签/标点布局、高亮、动画驱动 |
| `CompassSettingsScreen.java` | 417 | 图形化设置界面（TabView：样式/位置/刻度/动画/标点 5 页） |
| `CompassCommands.java` | 224 | `/herracompass` + `/compass` 指令系统（17 子指令） |
| `CompassConfig.java` | 216 | ModConfigSpec 配置 + 防抖落盘（500ms 静默统一写盘） |
| `NeonStyle.java` / `TacticalStyle.java` / `MinimalStyle.java` / `GlassStyle.java` | 137/127/121/113 | 4 套皮肤（背景/描边/刻度/辉光绘制策略） |
| `CompassStyle.java` | 133 | 皮肤抽象基类 + 通用布局度量（tickTopY/labelBaselineY/markerY） |
| `CompassPaint.java` | 132 | 静态绘制工具（细线、文字、辉光、半透明底） |
| `CompassHeading.java` | 126 | 朝向状态：yaw→[0,360) 映射、临界阻尼弹簧、最短角路径 floorMod |
| `CompassStyleContext.java` | 112 | 皮肤绘制上下文（尺寸/配色/配置快照） |
| `CompassPalette.java` | 93 | 5 组配色定义 + `distinctFrom` 撞色规避算法 |
| `CompassHub.java` | 89 | 运行时中枢：heading 单例、提供者注册、基数方位事件分发 |
| `CompassHud.java` | 64 | LDLib2 ModularHudLayer 适配层（布局缓存 + 入场动画门控） |
| `CompassApi.java` | 55 | 公开联动 API（静态门面，见 §6） |
| `CompassClientEvents.java` | 40 | 客户端事件：指令注册、tick 驱动、配置落盘 |
| `CompassHudRegistration.java` | 29 | `RegisterGuiLayersEvent` 独立 HUD 层注册 |
| `CompassMark.java` | 25 | 标点数据记录（类型/方位角/距离/颜色） |
| `CompassMarkerProvider.java` | 18 | 标点提供者 SPI 接口 |

### 2.2 包外新增/修改

| 文件 | 说明 |
|---|---|
| `client/CompassMarkerBridge.java`（50 行，新增） | 只读桥：从 TacticalMarker 体系取标点→换算方位角，每标点异常隔离，绝不写回 |
| `DragInventory.java`（修改） | 注册 compass 配置与客户端事件，其余不动 |
| `assets/draginventory/lang/en_us.json` + `zh_cn.json` | 各 87 个 `compass.*` 翻译键（含标点类型动态键），中英完整 |

**未触碰**：ReferenceHotbar、StatusBar、WeaponHudMotion、TacticalMarker、GWO 桥等现有代码（`CompassMarkerBridge` 仅只读引用）。

## 3. 指令系统（`/herracompass`，别名 `/compass`）

```
/compass                  # 无参 = 打开图形化设置界面（等同 gui）
/compass gui              # 打开设置界面
/compass toggle|on|off    # 开关方位条
/compass style <id>       # 皮肤：minimal | glass | tactical | neon
/compass palette <id>     # 配色：default | amber | crimson | jade | ice
/compass offset <x> <y>   # 位置偏移（px，±640）
/compass scale <v>        # 整体缩放 0.5~2.0
/compass opacity <v>      # 透明度 0.15~1.0
/compass width <px>       # 条宽 120~520
/compass range <deg>      # 显示视野范围 60~360
/compass smooth <v>       # 平滑系数（0=瞬时 1=极缓，默认 0.5）
/compass markers <on|off> # 标点联动开关
/compass test enemy|location|item|clear   # 测试标点（前方15°/正前/后方15°）
/compass reset            # 恢复默认配置
/compass info             # 查看当前状态/朝向/已注册提供者
```

## 4. 配置项（`config/draginventory-client.toml`，即时生效 + 防抖落盘）

- **基础**：`enabled`、`hide_with_debug_screen`（F3 隐藏）、`offset_x/y`、`width`、`scale`、`style`、`palette`、`opacity`
- **颜色**（可逐项覆盖配色）：`accent`（中心高亮）、`text`、`dim_text`、`tick`、`background`
- **动画**：`smoothness`、`snap_assist`（吸附辅助）、`snap_range`、`inertia_tilt`（惯性倾斜）、`tilt_intensity`、`entry_animation`
- **刻度**：`range`（显示范围）、`minor_step`（小刻度）、`number_step`（数字间隔）、`show_cardinals`（北南东西）、`show_intercardinals`（东北等）、`show_numbers`、`cjk_labels`（中文方位字）、`cardinal_scale`（主方向字号倍率）
- **标点**：`markers.enabled`、`markers.tactical`（战术标点桥开关）、`markers.show_distance`、`markers.pulse`（脉冲）、`markers.test_markers`（测试标点）

## 5. 测试方法

### 5.1 逻辑单测（无 MC 依赖，可随时重放）

```bash
bash scripts/test_compass_logic.sh   # 19/19：359↔0 三方向最短路径/弹簧收敛无过冲/glow 接近度/8 组撞色
```

### 5.2 游戏内测试清单（建议顺序）

1. **装包**：`drag-inventory-1.21.1-neoforge-1.4.9.jar` 放 mods 目录，与 LDLib2 2.2.40、现有 DragInventory 依赖模组同装；进入单人世界。
2. **基础显示**：出生后顶部中央应出现方位条；缓转头观察：中心数字平滑滚动、刻度向反方向流动、无跳变。
3. **边界专项**：朝北来回跨越 359°↔0°（`F3 + 左右转`），确认条**不反向甩动**、角度连续（358→359→0→1）。
4. **收尾专项**：急转后松手，弹簧应在 ~0.5s 内自然停在目标角度，无过冲回弹、无余振。
5. **标点联动**：`/compass test enemy` → 前方 15° 出现红色敌对菱形并脉冲；转头使标点移向边缘，接近边缘时辉光增强；`/compass test clear` 清除。放置战术标点（现有体系）确认自动映射到方位条；`/compass markers off` 关闭后消失。
6. **撞色专项**：`/compass palette crimson`（绯红橙 accent）+ `/compass test enemy`（红标点）→ 标点应自动变蓝青色仍醒目。
7. **设置界面**：`/compass`（无参）唤出 GUI；逐页拖滑条/切开关/换皮肤配色，方位条**实时预览无闪烁**；关闭界面 500ms 后 TOML 落盘，重进世界配置保留。
8. **HUD 共存**：装上浩白的快捷栏/体力血条/枪械 HUD，确认互不遮挡；如有重叠调 `offset` 避让；`/compass off` 独立关闭方位条后其余 HUD 正常。
9. **F3**：打开调试屏方位条隐藏（可配），关闭后恢复。
10. **联动冒烟**：`/compass info` 输出朝向/角速度/提供者列表（应含 tactical bridge）。

### 5.3 构建复现

```bash
./gradlew compileJava -Dorg.gradle.jvmargs=-Xmx512m   # 低内存机需先跑 scripts/fix_nfrt_memory_v3.py
./gradlew build
```

## 6. 联动 API（供其他 HERRA 模组软依赖）

调用方**反射探测** `dev.draginventory.client.compass.CompassApi` 是否存在，无编译期硬耦合：

```java
float heading = CompassApi.getSmoothHeading();      // [0,360)，未初始化 -1
float omega   = CompassApi.getHeadingVelocity();    // 度/秒，右转为正
CompassApi.registerMarkerProvider(provider);         // 小地图/队友方位等外部标点源
CompassApi.addCardinalListener(deg -> { ... });      // 朝向吸附到 北0/东90/南180/西270 事件
```

所有方法在方位条禁用时不抛异常（返回安全默认值）。`CompassMarkerProvider` 只需实现 `List<CompassMark> marks()`，每帧调用需自行缓存。

## 7. 后续扩展方向

1. **3D 标点投影**：当前标点只有方位角参与；可加俯仰角做垂直偏移（头顶目标）与距离缩放。
2. **更多提供者**：小地图 waypoint、队伍系统队友朝向、战局目标点——都走 `registerMarkerProvider`，无需改本模组。
3. **皮肤/配色热插拔**：数据驱动皮肤（JSON 资源加载），玩家自导入。
4. **HUD 布局协调器**：多个 HERRA HUD 注册各自预留区，自动避让（当前为手动 offset）。
5. **按键绑定**：开关键 + 设置界面键（当前仅指令入口）。
6. **F3 详情行**：在调试屏输出 heading/omega/标点数，方便其他模组调试联动。

## 8. 已知边界与说明

- 标点颜色由 `distinctFrom` 与 accent 做色相距离 ≥0.14 保证区分，极端自定义灰度组合下按亮度差规避。
- 配置写盘有 500ms 防抖 + 退出世界/关 UI 兜底 flush，极端断电仍可能丢最后一次微调。
- 本沙箱构建需先打 NFRT 内存补丁（`scripts/fix_nfrt_memory_v3.py`，4GB 内存机器专用，与模组代码无关）。
