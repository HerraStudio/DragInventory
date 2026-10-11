# Release Notes — Drag Inventory v2.9.0（声纹 HUD 六项实测反馈修复）

**适用版本**：Minecraft 1.21.1 · NeoForge 21.1.251+ · LDLib2 2.2.40+
**文件**：`drag-inventory-1.21.1-neoforge-2.9.0.jar`
**SHA-256**：`5feceb3fdcbfe225d40002fd22126188a178aee18cda35b7faf20a96b55232ec`

---

## 一、六项反馈逐条答复

### ① 测试模式并入 GUI + `/sp` 指令不存在 + 与原版指令冲突

**根因**：v2.8.0 里 `/sp` 这个根指令**只挂了一个"打开设置界面"的执行体，没有挂任何子命令**——`sim` 子命令实际只注册在 `/soundprint sim ...` 和 `/herrasoundprint sim ...` 之下，所以 `/sp sim on` 会提示"指令不存在"。（当初发布说明里写成 `/sp sim on` 是我的失误。）

**本版改动**：
- **彻底移除 `/sp` 根指令**（避免与任何其他模组冲突）。
- 保留两个等价入口：**`/soundprint`**（主指令）与 **`/herrasoundprint`**（HERRA 生态别名），**两者都带全部子命令**。
- 直接输入 `/soundprint`（不带参数）或 `/soundprint gui` → 打开声纹设置 GUI。
- GUI 的 **"测试"页签**就是模拟测试模式：模拟声源开关/类型（脚步/枪声）/方位滑条（0–359°）/距离滑条（2–20 m）/移动模式（static/orbit/random）+ 新增**声音捕获日志开关**（见②）。开着 GUI 就能实时预览所有改动效果。

### ② 自己开枪看不到红色声纹

**根因分析**：采集链路对枪声有三道隐形闸门，外部枪械模组（你用的 Guns Workshop Origins 2）的声音大概率被其中一道拦下：
1. **声音类目**：原实现只接受 NEUTRAL/HOSTILE/PLAYERS/BLOCKS 四类，枪械模组普遍用 **MASTER** 类目播放枪声 → 直接被丢弃；
2. **相对声（2D 声）**：`isRelative()` 的声音（很多枪械模组本地枪声的播法）被无条件跳过；
3. **关键词**：默认枪声关键词表（gun, shot, shoot…）不一定覆盖 GWO 的声音 id。

**本版改动**：
- **MASTER 类目对"枪声"放行**（脚步仍不放行，避免音乐/环境音误入）；
- **相对声锚定到玩家自身位置**再走原有管线（自己的枪声 → 投射到视线前方 4 m 处，条带正中红色声纹）；
- 新增 **`/soundprint debug capturelog on`**（或 GUI 测试页"声音捕获日志"开关）：开启后**每一个到达采集链的声音事件**都会以 `[SoundPrint-Capture]` 前缀写进 `logs/latest.log`，格式含 id/类目/是否相对声/音量/坐标/分类结果。
  **请这样用**：开启捕获日志 → 开一枪 → 翻 `latest.log` 搜 `SoundPrint-Capture` → 把枪声那几行发我（或自己比对关键词表）。若 id 与现有关键词不匹配，用 `/soundprint gunshot_keywords "现有词,新词"` 追加即可（GUI 与指令都能改）。
- 若开日志后枪声**完全没有**被记录，说明 GWO 用了绕过 SoundEngine 的自定义播放通道——把现象告诉我，我再加针对性钩子。

### ③ 声纹条没有上下振动 + 自己脚步声闪烁

**自己脚步闪烁根因**：走动续期（motion_persist）只扫描**其他实体**，唯独漏了本地玩家自己——你关掉"忽略自己的声音"测试自声时，自己的脚步只靠 linger(650ms) 硬撑，步与步之间偶尔回落。**本版**：`ignore_own` 关闭时，本地玩家也进入走动续期扫描（`collectMovers`），走路期间自己的脚步声纹恒亮、停步后按 linger 淡出，不再闪。

**振动系统（全新实现）**：
- 声纹条现在**持续上下振动**：每根竖条按正弦波呼吸（相邻竖条相位差 0.85 rad，形成涟漪而非整体刚性平移），簇相位由方位角决定，不同方向的簇不会同步抖动；
- **振幅随距离衰减**：近处振幅接近上限，远处衰减到下限（复用距离衰减曲线，`distance_decay`/`min_decay` 联动）；
- **同方位多声源叠加增强**：同一聚合窗内的声源越多 → 振幅越强（每多一个声源 +35% 振幅，可调）**且越不透明**（每多一个声源 +22% 不透明度，可调），都封顶不溢出；
- **最小/最大振幅独立可调**（px），另有振动频率（Hz）与总开关。

### ④ 声纹条位置（顶部遮挡）+ 可自定义方位

**根因**：v2.8.0 把声纹**基准线画在了控件顶边**（方位条正下方那条线），而竖条是从基准线**上下双向伸展**的 → 上半段全部伸进方位条区域，被方位条的贴图盖住（层序靠后）。看起来就是"贴在屏幕最顶上、上半截被挡住"。

**本版改动**：
- **基准线移到控件垂直中心**，控件自身高度按"最大条高×1.35×1.8 + 最大振幅 + 余量"双侧预留 → 竖条再高也永远不会伸进方位条；
- **位置预设系统**（`/soundprint position preset <id>` 或 GUI 常规页第一项）：
  | 预设 | 含义 |
  |---|---|
  | `below_compass`（默认） | 水平跟随方位条，条带顶端贴方位条视觉底边 + spacing 间距 |
  | `top_center` | 屏幕顶部水平居中（Y 可负向偏移上贴边） |
  | `center` | 屏幕垂直居中（基准线正对屏幕中心） |
  | `bottom_center` | 屏幕底部水平居中，悬停在快捷栏上方约 44px |
- **像素级微调**：`position offsetx` / `position offsety`（±240 px，Y 正值向下）、`position width`（60–480 px，仅非 below_compass 预设生效）、`position spacing`（0–20 px，仅 below_compass 生效）。所有数值都是**视觉像素**（已按缩放换算）。

### ⑤ 指令菜单全量自定义选项（带单位）

`/soundprint` 现在把**配置文件里每一项**都暴露为子命令，反馈与状态输出**全部带单位**：

```
/soundprint                       打开设置 GUI
/soundprint gui                   同上
/soundprint toggle | on | off     总开关
/soundprint status                全量设置转储（见下）
/soundprint reset                 恢复默认
/soundprint radius <4-64>               半径（m）
/soundprint footsteps|gunshots|ignore_own|own_gunshots|motion_persist|follow_compass
                                        …… on|off|toggle
/soundprint footstep_keywords "<csv>"   脚步关键词
/soundprint gunshot_keywords "<csv>"    枪声关键词
/soundprint mode merge|stagger          多声源模式
/soundprint merge_window <2-20>         聚合窗口（°）
/soundprint stagger_spacing <1-6>       错位间距（px）
/soundprint bar_count <5-21>            每簇竖条数
/soundprint bar_width <1-4>             竖条宽（px）
/soundprint bar_gap <0-3>               竖条间隙（px）
/soundprint max_height <8-56>           单侧最大高（px）
/soundprint baseline|arrows|vertical_offset|distance_decay|oscillate
                                        …… on|off|toggle
/soundprint color_footstep|color_gunshot <default|white|red|gold|cyan|lime|0xRRGGBB>
/soundprint opacity <15-100>            不透明度（%）
/soundprint scale <50-200>              整体缩放（%）
/soundprint min_decay <0-60>            远端保底（%）
/soundprint attack <4-40>               出现速度（弹簧刚度）
/soundprint release <1-20>              衰减速度
/soundprint linger <300-1500>           停留（ms）
/soundprint osc_speed <0.5-8>           振动频率（Hz）
/soundprint osc_amp_min <0-8>           最小振幅（px）
/soundprint osc_amp_max <0-24>          最大振幅（px）
/soundprint stack_amp_gain <0-100>      叠加振幅增益（%）
/soundprint stack_alpha_gain <0-100>    叠加不透明度增益（%）
/soundprint position preset <below_compass|top_center|center|bottom_center>
/soundprint position width <60-480>     条带宽（px）
/soundprint position offsetx <-240..240>（px）
/soundprint position offsety <-240..240>（px）
/soundprint position spacing <0-20>     与方位条间距（px）
/soundprint sim on|off|toggle|type|bearing|distance|mode   模拟声源
/soundprint debug capturelog on|off|toggle                  捕获日志
```
（`/herrasoundprint` 子树完全相同。）

### ⑥ `/soundprint status` —— 你调优、我固化的工作流

**这就是为你"调到满意后报数值"设计的**：

1. 进游戏用 GUI（或指令）把各项调到满意；
2. 执行 `/soundprint status`——聊天栏会输出**全部 45 项设置的完整转储**（含单位，形如 `style.bar_count = 14`、`position.preset = center`、`animation.osc_amp_max_px = 12 px`）；
3. 把整段复制发我，我在下一版把这些数值**固化成默认预设**。

GUI 里所有滑条也都带单位显示（px / Hz / ms / m / % / °）。

---

## 二、实机复测清单

1. **指令**：`/soundprint` 直接回车开 GUI；`/soundprint sim on` 出模拟声源；确认 `/sp` 已不存在。
2. **测试页**：GUI → 测试 → 开模拟声源，切脚步/枪声，拖方位滑条——声纹应随滑条移动、**持续振动**（默认 3.5 Hz）。
3. **振动**：`sim distance` 从 2 m 拖到 20 m——振幅应明显由强变弱；`sim bearing` 不动时把 `osc_amp_max` 拉到 24 px 看上限效果。
4. **位置**：依次切四个预设（below_compass/top_center/center/bottom_center），确认条带位置正确、below_compass 不再被方位条遮挡；再微调 offsetx/offsety。
5. **自声脚步**：`/soundprint ignore_own off` 后自己走动——声纹恒亮不闪，停步 650ms 后淡出。
6. **自声枪声**：`/soundprint debug capturelog on` → 开一枪 → `logs/latest.log` 搜 `SoundPrint-Capture` → 把枪声行发我（若关键词不匹配，用 `gunshot_keywords` 追加后立即生效）。
7. **多声源叠加**：模拟声源（orbit 模式）+ 另找实体同方向走动，同方位簇应更实、振幅更强。

## 三、已知限制与说明

- **GWO 枪声 id 未知**：无法拿到 Guns Workshop Origins 2 的声音定义表，默认关键词未必命中——用②的捕获日志流程一次定位。
- **below_compass 水平跟随方位条**：方位条自身的 offsetX/缩放会被跟随换算；若你在游戏中改方位条位置，声纹会实时跟上。
- **非 below_compass 预设**下条带宽度用 `position width`，不再跟随方位条宽度。
- **本版源码说明**：开发沙箱在上个版本后重置，本版由 v2.8.0 发布 jar 反编译重建源码后开发（六项修复均经纯 JVM 回归验证），后增量合并回本仓库正统基线（声纹 18 类 + 语言键 +17×2 + 版本号），全套 242 用例全绿。正式 jar 由本仓库 tag 树构建，与期初交付 jar（SHA256 `96bf9426…ee33`）类清单完全一致、声纹 66 类字节级 100% 一致，差异仅构建工程（本 jar 额外注入 LICENSE/TEMPLATE_LICENSE.txt）与非声纹类编译产物的等价差异。

## 四、完整变更文件

```
src/main/java/dev/draginventory/client/soundprint/
  SoundPrintCommands.java      重写：双根全子树 + status 转储 + 调试子命令
  SoundPrintEvents.java        采集链重排：MASTER 枪声放行/相对声锚定/捕获日志/自己走动续期
  SoundPrintConfig.java        新增 10 项配置（振动 7 + 位置 4，见 [animation]/[position]）
  SoundPrintWidget.java        基准线居中 + 振动渲染（OscStyle）+ 叠加不透明度
  SoundPrintHud.java           位置预设引擎 + 屏幕锚点 + 振幅预留高度
  SoundPrintSettingsScreen.java GUI 新增 11 个控件（位置/振动/捕获日志）
  SoundPrintOscillator.java    新增：纯 JVM 振动数学（振幅映射/脉冲/叠加）
assets/draginventory/lang/{en_us,zh_cn}.json   +17 键（320 对称）
src/test/java/.../soundprint/  5 个测试类 34 例
```
