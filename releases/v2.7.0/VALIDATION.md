# v2.7.0 验证记录（声纹 HUD + HERRA 统一设置入口）

## 需求与实现

**用户需求**：为方位条 / 地图 / 体力条 / 快捷栏 / 枪械 HUD / 口袋轮盘生态新增"声纹"HUD——①方位条下方、跟随其显隐；②20m 检测半径；③脚步声白 / 枪声红；④水平方位 + 垂直偏移联动；⑤同方位多声源去重聚合（本版用户最终决策：**默认聚合合并，可切并排错位**）；⑥距离衰减；⑦高级流畅动画（参考三角洲行动，4 张参考图经像素级分析定稿视觉语言）；**硬约束：不修改任何现有 UI 代码**，声纹为独立组件经 CompassApi / 只读配置协同。另新增统一设置入口：主指令 + 主 GUI + 模块注册 API，接入现有全部模块并可扩展。

**实现**（新包 client/soundprint 14 文件 + client/herra 6 文件，除下述接线点外零触碰现有代码）：

1. **检测引擎**：`SoundPrintEvents`（PlaySoundEvent）→ 类别预筛（NEUTRAL/HOSTILE/PLAYERS/BLOCKS）→ 相对声 / 循环声排除 → `SoundClassifier` 关键字分类（脚步优先于枪声判定，防 gunstep 双命中错归）→ IGNORE_OWN 距离阈值 0.9m 过滤自声 → 半径过滤 → `SoundSample` 快照入并发队列。`SoundPrintTracker`（纯 JVM）client tick 单消费者：同物理声源（同类型 + <1.6m）重听即刷新（连走脚步不抖动）；能量模型 = 归一化基准音量（类型基准：脚步 0.28 / 枪声 0.85）× 时间窗（HOLD 260ms + 线性 fade 1.25s/0.85s）× **当前**距离衰减（玩家移动实时更新，peak 不含衰减、target 每 tick 重算——修掉了首轮测试拦截的双乘 bug）；显示能量用临界阻尼弹簧闭式解（attack 18 / release 7 配置，帧率无关）；满员 16 源按"保留价值"（能量 × 静默时长折减）替换（修掉了首轮测试拦截的条件反转 bug）。
2. **渲染**：`SoundPrintWidget`（LDLib2 UIElement）+ `SoundPrintHud`（ModularHudLayer，registerAboveAll 独立层）。与方位条协同全为只读：**自建 `CompassHeading` 弹簧实例**，喂与 CompassWidget 相同的目标源（`player.getViewYRot(partialTick)`）与刚度（`CompassConfig.SMOOTHNESS`）——同算法同输入序列输出一致（初值差按临界阻尼指数收敛），避免 `CompassApi.getSmoothHeading()` 取整值的 1° 阶梯；水平映射复刻 `degreesToX` 同式（range 读 CompassConfig.RANGE）；布局 Taffy 绝对定位读 BAR_WIDTH/OFFSET_X/OFFSET_Y + 皮肤高度（40~72 clamp）+ SPACING；显隐复刻方位条判定（ENABLED + HIDE_WITH_DEBUG + F3 + 旁观 + hideGui）× FOLLOW_COMPASS。钟形包络 = 高斯（σ=(n-1)/2/1.6，尾部 <0.06 整条省略→紧凑声源退化为中柱）；垂直不对称 [1±0.75/±0.72]；箭头强度三段（<0.22 无 / 0.22~0.65 淡入 / >0.65 全显），V 形无杆箭头行填充自绘（同 CompassPaint.chevron 像素语言）；枪声 heightBoost 1.35 + 条宽 +1 间隙 -1；条顶 / 底 0.5px 微光同方位条 hline 柔边手法；视野边缘 smoothstep 渐隐；入场 180ms ease-out 淡入轻落。
3. **聚合双模式**（`SoundPrintAggregator` 纯函数）：排序 + 线性分组 + **首尾 wrap 合并**（359°/1° 物理同向）；merge = 能量 RMS 合成（sqrt(e1²+e2²) cap 1，比 max 明显比求和温和）+ 方位向量平均（atan2(sin,cos) 能量加权）+ 垂直偏移能量加权 + 枪声优先；stagger = 成员按能量降序交替对称错位（slot 0, +s, -s, +2s…），各自保留类型 / 颜色 / 垂直偏移；簇上限 16 按能量截断。
4. **配置**（`SoundPrintConfig`）：draginventory-soundprint-client.toml，六段 26 项全部带范围校验；journal + 500ms 防抖落盘 + 重载对账（与 CompassConfig 同一套代码纪律）；关键字表带缓存解析（配置变化才重新 parse）。`SoundPrintConfigEvents`（MOD 总线）三事件对账。
5. **设置 GUI**（`SoundPrintSettingsScreen`）：单文件自洽像素风（MenuTheme 三主题同色值跟随 CompassConfig.MENU_THEME），sealed Control 模型（Bool/Slider/Cycler/Button）五页签，合成声场预览（`renderStandalone` 与 HUD 同绘制路径，scale 合并配置缩放与预览盒适配），滚轮滚动 / 滑条拖拽 / 关闭动画 130ms。无拖拽定位（声纹几何硬绑定方位条，仅暴露垂直间距）。
6. **HERRA 统一入口**：`HerraModule` 接口（id/title/description/hasSettings/openSettings/isEnabled/toggleable/setEnabled）；`HerraModuleRegistry`（CopyOnWrite，同 id 先到先得）静态注册七模块——compass/map/soundprint 经 `XxxSettingsScreen.create()` 公开工厂接真实配置（三模块代码零改动），stamina/hotbar/weapon/wheel 为 SimpleModule 占位（NO_OP setEnabled，不可开关只展示）；`HerraApi` 公开注册 / 注销 / 快照 / openHub；`HerraCommands`（/herra 无参 GUI + list/modules/open/toggle，suggests 按能力过滤）；`HerraHubScreen` 列表 + 详情 + 状态点 + 操作按钮。
7. **接线点**（用户知情范围内）：`DragInventory.java` +1 行注册 SoundPrintConfig；build.gradle 版本号 2.6.1→2.7.0 + soundprintSmoke 源集开关 + 沙箱内存适配块；mods.toml 未动（项目惯例不随版更新）；语言文件 en/zh 各 +66 key（现 291，对称）；README 版本行 / changelog / jar 名 3 处。

## 门禁记录

- **构建环境适配（本版特例，决策记录见下）**：沙箱 4GB 物理，NFRT 硬编码给 vineflower 4G 堆直接 OOM（output.jar 0 字节，dmesg 确认 OOM killer）；字节码 patch NeoFormRuntime ExternalJavaToolAction（sipush 4096→2600）+ gradle daemon 512m 后 decompile 2m47s 成功（部分方法堆内 OOM 由 vineflower 方法级 stub 容错，recompile 编译 5364 文件全过 = 产物有效）。系统 JDK 为 headless（无 ct.sym，javac 报 "release version 21 not supported"）——切换 JAVA_HOME 到 gradle 工具链已缓存的完整 Adoptium 21.0.12.1（同版本号）后全链正常。
- 构建：`./gradlew compileJava` / `test` / `build` 三连 **BUILD SUCCESSFUL**（768m daemon）；仅 EventBusSubscriber 弃用警告（历史存量）。
- 测试：**212 用例 0 失败 0 错误 0 跳过**（181 原有 + 31 新增：SoundClassifierTest 6 / SoundPrintEnvelopeTest 7 / SoundPrintAggregatorTest 10 / SoundPrintTrackerTest 9——聚合双模式、跨北合并、RMS 合成、生命周期、refire、距离重算、容量替换、迟到样本、clear 全覆盖；首轮 5 失败全部为**测试拦截的真实 bug**：peak 双重距离衰减 / 满员替换条件反转 / weightedBearing 线性插值错误期望 ×1 / 时间线过短 ×1）。
- jar：`drag-inventory-1.21.1-neoforge-2.7.0.jar` 501,422 B（SHA256 见 SHA256SUMS.txt）。
- 语言键：en_us.json 与 zh_cn.json 各 225→291 key，逐 key 对称（脚本合并，未动现有键）。
- smoke：src/soundprintSmoke/java（`-PsoundprintSmoke` 属性源集，发布 jar 不含）——配置加载 / 注册表七模块 / 配置文件落盘三重自检后输出 SOUNDPRINT_SMOKE_PASS 退出。**沙箱无显示环境，实机 smoke 未在本轮执行**（构建链三连已覆盖编译与单测层；smoke 类随源码交付，实机一条命令可验）。

## 决策记录

- **⑤ 多声源默认模式**：用户拍板"默认聚合合并（merge），可切换并排错位（stagger）"——聚合合并能量叠加更符合"同方向多威胁 = 更大威胁"的战术直觉；并排错位保留参考图 3 的多簇观感供偏好选择。
- **弹簧实例而非读 CompassApi**：`getSmoothHeading()` 返回取整值（1° 阶梯，427px/120° 下 ≈3.5px 跳动）；自建同参弹簧与方位条逐像素同步，且完全零侵入（CompassHeading 为公开类，直接复用其算法）。
- **距离衰减在 target 层每 tick 重算**（peak 只存基准音量）：玩家移动时声纹高度实时变化；首轮实现在建源时也乘衰减导致双乘（能量平方级塌陷），被单测拦截后修正。
- **自声过滤用距离阈值而非实体判定**：EntityBoundSoundInstance 在 1.21.1 无公开实体取值（编译期发现），0.9m 阈值兜底覆盖本地实体声与本地方块声；骑乘 / 贴身同伴声会被误伤，代价远小于"自己走一步原地炸一下"。
- **占位模块不可开关**：体力条 / 快捷栏 / 枪械 HUD / 轮盘无配置系统，伪造开关等于伪造功能；只读展示 + 描述，待其获得配置系统时一行替换。
- **发布 jar 不含 smoke**：声纹 smoke 走 `-PsoundprintSmoke` 源集开关（项目既有模式），与 StartupSmoke 等一致。

## 实机复测要点

- 进世界附近走动：对应方位白色声纹浮现（自己脚步不显示）；停下后约 2.5 秒绵柔退场。
- 枪声 / 爆炸：红色高声纹瞬间冲起、快速消散；枪声比脚步明显更高更密实。
- 楼上 / 楼下声源：上下两排不对称 + 无杆 V 形箭头指上 / 指下；水平声源无箭头。
- 同方位多声源默认合成一簇（多脚步 = 一簇更高）；`/herrasoundprint mode stagger` 后各自错开。
- `/herra`：主界面七模块列表；open compass / map / soundprint 三项直达设置；toggle 生效；`/herra list` 文本态一致。
- 方位条拖动 / 拉宽 / 换皮肤：声纹实时跟随；`/com off` 后声纹一并消失（follow_compass 默认开）。
- 外部枪械模组声音不识别时：配置 `detection.gunshot_keywords` 追加其声音 id 关键字（逗号分隔），无需重启（热重载生效）。
