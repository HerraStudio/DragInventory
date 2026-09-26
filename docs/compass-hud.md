# HERRA 方位条 HUD（罗盘条）— 交付文档

> 模组：Drag Inventory（`draginventory`）· Minecraft 1.21.1 · NeoForge 21.1.251 · LDLib2 2.2.40
> 版本：1.4.9（第四轮：美术分型 + 死亡标点/按键绑定等 5 项新功能）· 风格参考：《三角洲行动》顶部方位条

---

## 1. 功能总览

- **顶部中央方位条**：细线条半透明、非像素风平滑字体（LDLib2 SDF 字体），中间高亮当前朝向（如 `206`），两侧滚动显示主方向文字（北/东北/东…）与角度刻度（60/120/150…），主方向大、角度数字小。
- **朝向逻辑**：读取玩家 yaw 映射到 `[0, 360)`，0 = 北、顺时针增大；显示范围、刻度密度、标签内容全部可配置。
- **动画**：临界阻尼弹簧（精确闭式解，无过冲无抖动），359°↔0° 跨越走最短角路径不反向甩动；停止转向后自然收尾；入场动画、透明度渐变、朝向吸附辅助（snap assist）与惯性倾斜（视觉装饰）均可开关。
- **标点联动**：只读接入现有战术标点体系，标点在方位条对应方位以与中心高亮色**保证不撞色**的颜色（`CompassPalette.distinctFrom` 自动色相旋转）提示；视野外不远处（方位差在半视野～全视野之间）的标点自动吸附到条带对应边缘、压暗并附**方向箭头**提示转向；可指令/UI 开关。
- **标点形状分型**：敌对=菱形、位置=上三角、物资=空心方块、死亡=X 十字，不读字即可区分含义；可选文字标签（与距离合成一行）。
- **死亡标点**：死亡时自动在死亡位置标记红色 X（同维度显示，脉冲相位稳定），重生后沿方位条导航回到落包点，靠近 8 米内自动消失；可开关。
- **按键绑定**：“开关方位条”可到 选项→控制→Drag Inventory 绑定（默认未绑定，避免与 GWO 键位冲突）；动作栏反馈。
- **4 套皮肤**：`minimal`（默认极简细线）/ `glass`（毛玻璃真描边）/ `tactical`（战术）/ `neon`（霓虹），每套 × 6 组配色（极光/霜白/琥珀/绯红/紫罗兰/石板），颜色均可逐项覆盖。
- **入场动画**：淡入 + 中央优先错峰 + 上方 8px 滑落（ease-out，与淡入同步），仅在“不可见→可见”真实切换时播。
- **图形化设置界面**：`/compass gui` 唤出 LDLib2 现代化 UI（TabView + 滑条 + 开关 + 颜色选择器），拖动即实时预览、停顿 500ms 自动落盘。
- **与现有 HUD 共存**：独立 GUI layer 注册（`RegisterGuiLayersEvent`），不触碰浩白的快捷栏/体力血条/枪械 HUD 任何代码；默认位置顶部中央，可用 offset 调整避让。
- **联动预留**：`CompassApi` 静态门面，供其他 HERRA 模组软依赖读取朝向/角速度、注册标点提供者、监听基数方位事件。

## 2. 新增文件清单（约 3240 行 Java + 52 行桥 + 语言文件）

### 2.1 `src/main/java/dev/draginventory/client/compass/`（核心包，22 文件）

| 文件 | 行数 | 职责 |
|---|---|---|
| `CompassWidget.java` | 583 | 核心渲染部件：罗盘条绘制、刻度/标签/标点布局、高亮、动画驱动、屏外标点边缘吸附+方向箭头、标点文字行合成、死亡标点接入、预览布局同步 |
| `CompassSettingsScreen.java` | 442 | 图形化设置界面（导航 + 滚动内容 8 页，实时预览） |
| `CompassCommands.java` | 254 | `/herracompass` + `/compass` 指令系统（18 子指令，含 copy） |
| `CompassConfig.java` | 223 | ModConfigSpec 配置 + 防抖落盘（500ms 静默统一写盘） |
| `CompassPaint.java` | 181 | 绘制原语：菱形/上三角/X十字/方块描边/双向箭头/圆角矩形/辉光线/渐变 |
| `CompassHeading.java` | 174 | 朝向状态：yaw→[0,360) 映射（亚度精度）、临界阻尼弹簧、基数方位事件边沿检测 |
| `CompassHub.java` | 174 | 运行时中枢：heading 单例、提供者注册、基数方位事件分发、世界切换检测、死亡标点状态机 |
| `CompassStyle.java` | 168 | 皮肤抽象基类 + 分型形状分派 + 边缘遮罩（背景层） |
| `NeonStyle.java` / `TacticalStyle.java` / `MinimalStyle.java` / `GlassStyle.java` | 141/134/125/125 | 4 套皮肤（背景/描边/刻度/辉光绘制策略，标点形状分型共用） |
| `CompassStyleContext.java` | 118 | 皮肤绘制上下文（尺寸/配色/配置快照：range/tilt/pulse 帧内缓存） |
| `CompassPalette.java` | 103 | 6 组配色定义 + `distinctFrom` 撞色规避算法 |
| `CompassHud.java` | 64 | LDLib2 ModularHudLayer 适配层（布局缓存 + 入场动画门控） |
| `CompassApi.java` | 61 | 公开联动 API（静态门面，见 §6） |
| `CompassClientEvents.java` | 59 | 客户端事件：指令注册、按键轮询、tick 驱动、配置落盘 |
| `CompassKeyBindings.java` | 35 | “开关方位条”按键注册（默认未绑定） |
| `CompassHudRegistration.java` | 29 | `RegisterGuiLayersEvent` 独立 HUD 层注册 |
| `CompassMark.java` | 27 | 标点数据记录（类型含 DEATH/方位/距离/颜色/标签/创建时间） |
| `CompassMarkerProvider.java` | 18 | 标点提供者 SPI 接口 |

### 2.2 包外新增/修改

| 文件 | 说明 |
|---|---|
| `client/CompassMarkerBridge.java`（50 行，新增） | 只读桥：从 TacticalMarker 体系取标点→换算方位角，每标点异常隔离，绝不写回 |
| `DragInventory.java`（修改） | 注册 compass 配置与客户端事件，其余不动 |
| `assets/draginventory/lang/en_us.json` + `zh_cn.json` | 各 95 个 `compass.*` 翻译键（含标点类型/死亡点/按键绑定键），中英完整 |

**未触碰**：ReferenceHotbar、StatusBar、WeaponHudMotion、TacticalMarker、GWO 桥等现有代码（`CompassMarkerBridge` 仅只读引用）。

## 3. 指令系统（`/herracompass`，别名 `/compass`）

```
/compass                  # 无参 = 打开图形化设置界面（等同 gui）
/compass gui              # 打开设置界面
/compass toggle|on|off    # 开关方位条
/compass style <id>       # 皮肤：minimal | glass | tactical | neon
/compass palette <id>     # 配色：aurora | frost | amber | crimson | violet | slate
/compass offset <x> <y>   # 位置偏移（px，±640）
/compass scale <v>        # 整体缩放 0.5~2.0
/compass opacity <v>      # 透明度 0.15~1.0
/compass width <px>       # 条宽 120~520
/compass range <deg>      # 显示视野范围 60~360
/compass smooth <v>       # 弹簧刚度 4~34（越大越跟手，默认 15）
/compass markers <on|off> # 标点联动开关
/compass test enemy|location|item|clear   # 测试标点（前方15°/正前/后方15°）
/compass reset            # 恢复默认配置
/compass copy             # 当前朝向+方位名复制到剪贴板（如 "206° 西南"）
/compass info             # 查看当前状态/朝向/已注册提供者
```

按键：选项→控制→Drag Inventory→“开关方位条”（默认未绑定）。

## 4. 配置项（`config/draginventory-client.toml`，即时生效 + 防抖落盘）

- **基础**：`enabled`、`hide_with_debug_screen`（F3 隐藏）、`offset_x/y`、`width`、`scale`、`style`、`palette`、`opacity`
- **颜色**（可逐项覆盖配色）：`accent`（中心高亮）、`text`、`dim_text`、`tick`、`background`
- **动画**：`smoothness`、`snap_assist`（吸附辅助）、`snap_range`、`inertia_tilt`（惯性倾斜）、`tilt_intensity`、`entry_animation`
- **刻度**：`range`（显示范围）、`minor_step`（小刻度）、`number_step`（数字间隔）、`show_cardinals`（北南东西）、`show_intercardinals`（东北等）、`show_numbers`、`cjk_labels`（中文方位字）、`cardinal_scale`（主方向字号倍率）
- **标点**：`markers.enabled`、`markers.tactical`（战术标点桥开关）、`markers.death`（死亡标点）、`markers.show_distance`、`markers.show_labels`（文字标签）、`markers.pulse`（脉冲）、`markers.test_markers`（测试标点）

## 5. 测试方法

### 5.1 逻辑单测（无 MC 依赖，可随时重放）

```bash
bash scripts/test_compass_logic.sh   # 61/61：环绕/弹簧/glow/事件/撞色 + 亚度精度 + nearestCardinal 边界
```

### 5.2 游戏内测试清单（建议顺序）

1. **装包**：`drag-inventory-1.21.1-neoforge-1.4.9.jar` 放 mods 目录，与 LDLib2 2.2.40、现有 DragInventory 依赖模组同装；进入单人世界。
2. **基础显示**：出生后顶部中央应出现方位条；缓转头观察：中心数字平滑滚动、刻度向反方向流动、无跳变。
3. **边界专项**：朝北来回跨越 359°↔0°（`F3 + 左右转`），确认条**不反向甩动**、角度连续（358→359→0→1）。
4. **收尾专项**：急转后松手，弹簧应在 ~0.5s 内自然停在目标角度，无过冲回弹、无余振。
5. **标点联动**：`/compass test enemy` → 前方 15° 出现红色敌对菱形并**平滑脉冲**（观察 3 秒确认无随机抖动）；转头使标点移向边缘，接近边缘时辉光增强；继续转过头（标点在身侧）→ 标点应**吸附到条带边缘并压暗**，转到背后（方位差 > range）则消失；`/compass test clear` 清除。放置战术标点（现有体系）确认自动映射到方位条；`/compass markers off` 关闭后消失。
6. **撞色专项**：`/compass palette crimson`（绯红橙 accent）+ `/compass test enemy`（红标点）→ 标点应自动变蓝青色仍醒目。
7. **设置界面**：`/compass`（无参）唤出 GUI；逐页拖滑条/切开关/换皮肤配色，方位条**实时预览无闪烁**；预览条宽应跟随“条带宽度”滑条、高度跟随皮肤变化；拖动“模拟朝向”滑杆时演示标点应固定在世界方位上滑过（不粘条带中心）；关闭界面 500ms 后 TOML 落盘，重进世界配置保留。
8. **HUD 共存**：装上浩白的快捷栏/体力血条/枪械 HUD，确认互不遮挡；如有重叠调 `offset` 避让；`/compass off` 独立关闭方位条后其余 HUD 正常。
9. **F3**：打开调试屏方位条隐藏（可配），关闭后恢复。
10. **世界切换**：`/compass test enemy` 后退出世界重进（或穿下界门切维度），旧测试标点应自动清空、`/compass info` 朝向重新初始化。
11. **指令校验**：`/compass style xyz` 应报错并列出合法值，不再静默回退 minimal。
12. **联动冒烟**：`/compass info` 输出朝向/角速度/提供者列表（应含 tactical bridge）；朝向 315°~359° 时 info 的方位名应显示“北”（旧版本误显示“西”）。
13. **死亡标点**：`/kill` 自杀 → 方位条中心附近出现红色 X（带脉冲、标“死亡点”标签）；重生后沿 X 导航走近，距死亡点 8 米内 X 自动消失；切维度后 X 不显示（跨维度坐标无意义）。
14. **形状分型**：`/compass test enemy|location|item` 三种标点应分别显示菱形/上三角/空心方块（不读字即可区分）；设置界面预览同步分型。
15. **标签与距离**：设置界面“标点文字标签”开关 → 标点下方文字在“仅距离/标签+距离/仅标签”间切换，预览即时可见。
16. **屏外箭头**：把测试标点转到身侧（屏外吸附状态）→ 边缘标点旁应出现朝向屏外的小箭头（◄/►）。
17. **按键**：选项→控制→Drag Inventory→“开关方位条”绑定任意键 → 游戏内按该键开关方位条（动作栏提示），设置界面打开时按键不误触。
18. **copy**：`/compass copy` → 剪贴板应为 `"206° 西南"` 样式；未进世界时执行应报错不崩溃。

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
CompassApi.addCardinalListener(deg -> { ... });      // 进入 北0/东90/南180/西270 区域的边沿事件
```

基数方位事件为**边沿触发**（进入区域时发一次，基于玩家真实视角而非平滑值）：快速扫过正北也会触发；离开后重进同一方位会再次触发；区域半径由 `snap_range` 决定，关闭 `snap_assist` 时不触发。

所有方法在方位条禁用时不抛异常（返回安全默认值）。`CompassMarkerProvider` 只需实现 `void collectMarkers(Context ctx, Consumer<CompassMark> out)`（每帧渲染前调用，实现应轻量并自行缓存）。

## 7. 后续扩展方向

1. **3D 标点投影**：当前标点只有方位角参与；可加俯仰角做垂直偏移（头顶目标）与距离缩放。
2. **更多提供者**：小地图 waypoint、队伍系统队友朝向、战局目标点——都走 `registerMarkerProvider`，无需改本模组。
3. **皮肤/配色热插拔**：数据驱动皮肤（JSON 资源加载），玩家自导入。
4. **HUD 布局协调器**：多个 HERRA HUD 注册各自预留区，自动避让（当前为手动 offset）。
5. **F3 详情行**：在调试屏输出 heading/omega/标点数，方便其他模组调试联动。

## 8. 已知边界与说明

- 标点颜色由 `distinctFrom` 与 accent 做色相距离 ≥0.14 保证区分，极端自定义灰度组合下按亮度差规避。
- 配置写盘有 500ms 防抖 + 退出世界/关 UI 兜底 flush，极端断电仍可能丢最后一次微调。
- 屏外标点仅在方位差 ≤ range（默认 120°）内做边缘吸附，更远（近乎身后）的标点不显示，避免两侧边缘长期堆满图标。
- 死亡标点在死亡动画期间就生成（玩家就在死亡点上），但“靠近 8m 自动清除”仅对活着的玩家生效，不会生成即消失。
- 本沙箱构建需先打 NFRT 内存补丁（`scripts/fix_nfrt_memory_v3.py`，4GB 内存机器专用，与模组代码无关）。

## 9. 变更记录

### 第四轮（美术分型 + 5 项新功能 + 3 处自查修复）

**新功能**
1. **死亡标点**：轮询式状态机（不依赖客户端 LivingDeathEvent 时机，任意死亡路径可靠）：死亡自动记录位置/维度/时刻 → X 形红标（`distinctFrom` 保障与 accent 可区分、脉冲相位用死亡时刻不抖）→ 同维度显示 → 活着且靠近 8m 自动清除；世界切换/重生换维度自动失效；`markers.death` 可开关。
2. **按键绑定**：“开关方位条”KeyMapping（默认未绑定防 GWO 冲突），动作栏反馈，屏幕打开时不误触。
3. **标点文字标签**：`markers.show_labels`——标签与距离合成一行（“死亡点 42m”），预览标点带可翻译标签即时可见效果。
4. **`/compass copy`**：朝向+方位名复制剪贴板；未进世界报错不崩。
5. **屏外标点方向箭头**：边缘吸附时标点内侧画朝向屏外的小箭头（◄/►），画在内侧避免控件裁剪。

**美术完善**
- **标点形状分型**：敌=菱形、点=上三角、物=空心方块、死=X 十字、外部=菱形；四皮肤共用分型逻辑，各自保留辉光/外框/白芯气质。
- **入场滑落**：出现时从上方 8px ease-out 滑入，与淡入同步；布局热同步不重播（防滑条拖动闪烁）。
- **Glass 真描边**：整面 tint 改四边 1px 亮线（缩进圆角），更像玻璃受光边。
- **中心括号随吸附发光**：minimal/tactical 的 `[ ]` 括号向 accent 过渡。
- **遮罩时序重构**：边缘渐隐遮罩移至背景层与元素层之间（新 `drawEdgeMask`）——遮罩只压暗背景条带两端，不再二次压暗自带 edgeFade 的刻度/标点；高度同步从硬编码 38px 改为控件全高。

**自查修复（本轮发现并修复）**
1. 死亡标点“生成即清除”（P1）：死亡瞬间玩家就在死亡点，靠近清除会把刚生成的标点立即清掉——限制“活着才判定靠近清除”。
2. 屏外箭头被裁剪（P2）：箭头画在标点外侧可能超出控件边界——移到内侧、箭头尖仍朝屏外。
3. 遮罩压暗屏外标点（P2）：全高遮罩画在元素之上会把边缘吸附标点二次压暗——重构遮罩时序（见上）。
4. Tactical 标点文字越界（P3）：文字行下移后底部超出控件声明高度 56px——皮肤覆写 `widgetHeight()=60`。
5. 死代码清理：移除无调用方的 `CompassWidget.open()` 与重构后无引用的 `CompassPaint.glowDiamond()`。

**验证**：61/61 逻辑单测回归通过；compileJava + build 全绿（jar 192.6KB）；javap + 解包验证 jar 含全部新类/方法/语言键；中英 lang 同步 101/101 键。

### 第三轮（深度排查 + 优化 + 功能完善）

**Bug 修复**
1. **亚度精度**（P1）：`toHeading` 原四舍五入到整数度，弹簧目标被量化——慢转向时刻度/标签以 ~2px 步进跳变；改为保留浮点精度，子像素平滑。
2. **`/compass info` 方位名错误**（P1）：`Math.round(heading)/90*90` 整数除法把 315°~359° 全部归到"西"；统一改用 `CompassHeading.nearestCardinal()`（先浮点除再取整）。
3. **战术标点脉冲抖动**（P2）：桥接层每帧重建 `CompassMark` 时重置创建时间，脉冲相位随帧时长随机跳变；改用战术标点自身的创建时刻。
4. **测试标点跨世界残留**（P2）：新增 `CompassHub.syncWorld()` 每帧检测 `ClientLevel` 引用变化（退出/重连/切维度/重生），自动清空旧世界测试标点并复位实时朝向（API 在新世界首帧返回 -1 而非旧航向）。
5. **预览滑杆标点粘滞**（P2）：拖"模拟朝向"滑杆时演示标点跟随条带中心走（世界锚点被滑杆重置）；现仅摆动开启时重锚，滑杆转向时标点固定世界方位滑过条带，与实机行为一致。
6. **offset_y 滑条范围**（P2）：设置界面 -16~320 与配置/指令 ±640 不一致，超范围值回显被钳制；对齐为 ±640。
7. **未知 style/palette 静默回退**（P3）：`/compass style xyz` 原静默应用默认皮肤并报成功；现报错并列出全部合法值（新增中英语言键）。
8. **刻度候选不全**（P3）：minor_step 候选 {5,10,15}、number_step {15,30,45}，外部 TOML 写入其它合法值时 UI 无选中项；扩充为 5~30 / 15~90 全量候选。
9. **API 卫生**（P3）：`CompassApi.markerProviders()` 不再暴露内部可变列表，返回快照。

**性能优化**
- `currentPalette()` 零分配缓存：稳定状态下每帧仅 6 次配置读取 + 整数比较，省去 3 次 `distinctFrom`（6 次 RGBtoHSB）与 record 分配；配色或任一颜色覆盖变化（含外部热重载）自动重建。
- 渲染热路径去配置查询：`range`/惯性倾斜系数/脉冲开关缓存进 `CompassStyleContext`（原每刻度/每标点各查 1~2 次）；刻度循环的显示开关提升到循环外；标点循环复用皮肤实例。
- 逻辑单测从 33 扩到 **61 项**（+28：亚度精度 7 项、nearestCardinal 边界 21 项）。

**功能完善**
- **屏外标点边缘吸附**：方位差在 (range/2, range] 的标点钳制到条带对应边缘、透明度减半，提示"视野外不远处、朝该方向转"；配合原有边缘渐隐不突兀。
- **预览保真**：设置界面预览的条宽/高度实时跟随"条带宽度"配置与当前皮肤（原先固定 340×58）。
- `/compass test` 反馈带标点类型名（敌对标记/位置标记/物资标记）。

### 第二轮（提交者改写 + 5 处修复）

提交者身份统一为 kujojotarojojo（历史重写 + 强推）；独立四通道刻度渲染（minor_step=10 不再丢 NE/SE/SW/NW）、边沿触发基数方位事件（滞回）、预览标点色缓存键含 accent、Reset 后控件静默刷新、文档勘误。

### 第一轮（初版交付）

功能实现 + 深度审查 7 处修复（防抖落盘、入场动画门控、标点插值、桥异常隔离、撞色算法等）+ 交付文档。
