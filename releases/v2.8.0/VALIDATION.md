# v2.8.0 验证记录

## 门禁

| 门禁 | 结果 | 备注 |
|------|------|------|
| compileJava | BUILD SUCCESSFUL | 16s，仅存量 deprecation 警告（EventBusSubscriber.bus，与 v2.7.x 相同） |
| test | BUILD SUCCESSFUL | 237 用例 0 失败（224 + 13 新增） |
| build | BUILD SUCCESSFUL | jar 518,442 B；sources 340,057 B |
| jar 元数据 | 通过 | META-INF/neoforge.mods.toml version="2.8.0"（processResources 单源注入，v2.7.1 修复项回归验证） |
| 语言键对称 | 通过 | en/zh 各 303 键（291 + 12 新增），JSON 解析校验通过 |

## 根因诊断证据链（缺陷③ 位置重合）

1. 现象：用户实测声纹与方位条完全重合（offset_y=0 顶部对齐预设下）。
2. 假设一（配置数学错）：SoundPrintHud 旧公式 offsetY = OFFSET_Y(0) + 皮肤高(58) +
   spacing(2) = 60，声纹应在 60px 处——数学正确，与现象矛盾，排除。
3. 假设二（绘制坐标系错）：LDLib2 绘制链若为绝对坐标而 Widget 用局部 Y，
   声纹画在 y≈0.5 → 与方位条重合，与现象完全一致。
4. 实证：vineflower 1.10.1（-Xmx2368m）反编译 LDLib2 2.2.40 all-jar 的
   UIElement / ModularHudLayer / GUIContext：
   - `UIElement.drawBackgroundTexture`：`guiContext.drawTexture(background,
     this.getPositionX(), this.getPositionY(), ...)` —— 绝对坐标实证；
   - `drawInBackgroundInternal` 全链无 pose 局部平移（仅 transform2D/zIndex 样式变换）；
   - `ModularHudLayer.render` → `mui.getWidget().render(...)`，与方位条同路径。
   - 对照组：CompassWidget 用 `CompassStyleContext(getPositionX(), getPositionY(), ...)`
     绝对几何绘制且实机位置正确 —— 坐标系结论成立。
   - 反证：设置界面预览调用方先 `pose.translate(0, py)` 再调 renderStandalone，
     局部约定在预览路径成立——解释了预览不穿帮、发布自检未命中。
5. 结论：缺陷③ = Widget 局部 Y（0.5f）× 绝对坐标系 = 画在屏幕顶端。修复为绝对坐标。

## 反射读方位条实时几何的安全性论证（缺陷③ 增强策略）

- 目标类 `CompassHudRegistration` / `CompassHud` / `CompassWidget` 均为本 mod 自有
  类（同 JAR、非混淆生产映射、类名编译期锁定），`setAccessible` 对未命名模块内
  同类加载器成员无模块封锁——Java 平台层面可行。
- 读取路径：`LAYER`(private static final) → `widget`(private, 首次 getModularUI 才建)
  → `getPositionY()/getSizeWidth()/getSizeHeight()`（Taffy 布局解析值，public final）。
- 防御：字段句柄静态缓存；任何 ReflectiveOperationException / RuntimeException 置
  `reflectionBroken` 哨兵永久回退配置数学；widget 为 null / 尺寸非正（未布局）同样回退。
- 每帧成本：两次 getDeclaredField 静态缓存后仅 2 次 Field.get + 4 次 getter 读取
  （LDLib2 内部 getPosition 有 FloatOptional 缓存），远低于一次配置读取。
- 零修改 compass 包代码的红线保持（反射只读，不写入、不改行为）。

## test 首轮失败分析（2 例，均为测试时间线问题非产品缺陷）

- `lingerWindowHoldsEnergyBetweenSteps` / `sampleRisesWithAttackThenDecaysAndExpires`
  失败原因：linger 260→650ms 使淡出窗口整体后移 ~390ms；release 弹簧闭式解
  （ω=7, dt=0.05）每 tick 收敛因子 = (1+ω·dt)·e^(-ω·dt) = 1.35×0.7047 = 0.951
  （时间常数 ~1s），旧循环时长不足以让 displayEnergy 降到断言阈值。
- 修正：延长两处循环（90→140 tick / 50→90 tick），并复核第 38 tick（静默 1900ms =
  linger 650 + fade 1250）目标归零的时间点与断言时刻的余量。
- 产品语义本身由新增断言直接锁定：linger 窗内能量保持（held ≥ established-0.02）、
  窗后显著回落（fading < established×0.6）、走动 4s 仍点亮、停走后淘汰。

## 新增回归清单（13 例）

SoundPrintTrackerTest（+5）：
1. lingerWindowHoldsEnergyBetweenSteps —— 防闪烁核心回归（450ms 步间隔内能量不回落）
2. motionTickKeepsWalkingSourceAliveAndGlides —— 走动续期 + 声纹滑移（4s 无声纯走动
   仍点亮，方位随行走者从 0° 滑到 ~72.6°，停走后淘汰）
3. motionTickCreatesSourceForUnheardWalker —— 超耳距走动者新建（速度 0.28 → 峰值 0.854）
4. motionTickDoesNotProlongGunshotPrint —— 枪声不被走动续期 + 枪手脚步语义并存
5. motionEnergyMapsSpeed —— 速度能量映射（0→0.35 / 0.1→0.53 / 0.28→0.854 / 上限 0.9）

SoundPrintSimulatorTest（+6）：
6. staticFootstepEmitsImmediatelyThenEvery420ms —— 开启即发、420ms 节奏、方位→坐标
   数学（正东 12m：x=eyeX+12, z=eyeZ）、脚步在地面（eyeY-1.62）
7. gunshotAtEyeLevelOnSlowerCadence —— 枪声耳高（无箭头语义）、2200ms 节奏
8. orbitAdvancesBearingAndCrossesNorth —— 18°/s 推进 + 350°→8.9° 跨北 wrap
9. randomWalkStaysBoundedAndMoves —— 确定性种子下 10s 随机游走有界且离开起点
10. disabledAndResetSemantics —— 关即停发、reset 清时钟保参数
11. settersClampAndWrap —— bearing wrap360 / distance 夹取 / null 安全缺省

SoundPrintCaptureSafetyTest（+2）：
12. projectAheadNormalizesArbitraryLengthView —— 未归一视线归一后投影
13. projectAheadZeroViewFallsBackToEye —— 零向量回退眼睛位置

## 沙箱环境攻防（本版新增经验）

- vineflower 1.10.1 CLI：`--outputdir` 选项在该版本不生效（"no sources given" /
  保存目录冲突），正确用法为**位置参数** `java -jar vf.jar subset.jar outDir`；
  直接传散 .class 文件会因 DirectoryResultSaver 与源文件同目录冲突失败，
  需先 `jar --create` 打包子集。
- daemonize.py 首参为 logfile，命令行重定向 stdout 会导致参数错位（本版误触发一次，
  产生名为 env 的垃圾文件后立即发现修正）。

## 发布物料

- drag-inventory-1.21.1-neoforge-2.8.0.jar（518,442 B，SHA256 见 SHA256SUMS.txt）
- draginventory-v2.8.0-project.zip（源码 + 发布物料，见 SHA256SUMS.txt）
- RELEASE_NOTES-v2.8.0.md / VALIDATION.md（本文件）
