# v2.5.8 验证记录（玩家图标严格遵循 SVG 原图，纹理化）

## 变更清单

| # | 变更 | 文件 |
| --- | --- | --- |
| 1 | SVG 原图离线栅格化为 256×256 抗锯齿 PNG（cairosvg，形状/颜色/画布零修改，主色像素级校验 #32CD32） | assets/draginventory/textures/gui/player_marker.png（新） |
| 2 | mipmap 纹理类：alpha 感知 mip 链 + LINEAR/LINEAR_MIPMAP_LINEAR 过滤（upload 后统一 setFilter 的顺序约束见注释） | PlayerMarkerTexture.java（新） |
| 3 | player() 几何三角改纹理四边形（position_tex + QUADS），删除立体感三件套（暗半/中脊/软影）与 tri/withAlpha | FactoryMapUI.java |
| 4 | 懒注册：首次渲染时渲染线程注册纹理，幂等 | FactoryMapUI.java |
| 5 | 版本号 2.5.7 → 2.5.8 | build.gradle |
| 6 | 资源守卫测试（PNG 存在 / 256×256 / alpha / 主色 #32CD32，纯 JDK 不触 MC 类） | PlayerMarkerIconAssetTest.java（新） |
| 7 | 文档同步：主段/changelog/jar 名/版本行 | README.md / wiki/战术地图.md / docs/factory-tactical-map.md |

## 设计决策

1. **为什么纹理化而不是继续几何绘制**：MC GUI 光栅化无抗锯齿，几何斜边必然阶梯化；纹理离线抗锯齿 + mipmap 是唯一能在任意缩放下保持平滑的方案（用户"矢量图怎么放大都看不到锯齿"的预期）。mipmap 链（而非仅 LINEAR）解决图标持续移动时 bilinear-only 缩小的边缘闪烁（每帧采样不同 texel 邻域的时间性爬行）。
2. **CANVAS=15.5px 的换算**：SVG 画布 200×200 中形状（含同色描边 miter）实测占 206/256 像素高（栅格化产物 bbox 实测），画布 15.5px → 形状高 ≈12.5px，与 v2.5.7 视觉尺寸一致（用户"不要太大"口径不变）。
3. **MipmapGenerator 而非自写盒滤波**：官方实现带 alpha 感知混合（透明边缘颜色不发黑），且与图集管线同源。MAX_MIP=5（256→8px）覆盖 GUI scale 1 + 呼吸缩小的采样需求。
4. **零再创作边界**：绘制内容 = SVG 原图栅格化产物，不含任何明暗/描边/阴影的代码层叠加；动效（旋转/拉伸/缩放/透明）只作用于纹理四边形的 pose，不改变纹理内容本身。

## 门禁

- 构建：`JAVA_HOME=jdk-21.0.12.1+1 ./gradlew build -Dorg.gradle.jvmargs=-Xmx768m`，第一轮失败 1 处（`Resource` 非 AutoCloseable——1.21.1 SimpleTexture 同款写法修正，只 try-with-resources InputStream），第二轮 **BUILD SUCCESSFUL in 6s**。
- 测试：**179 用例（177 + 2 新）0 失败 0 错误**；新增 PlayerMarkerIconAssetTest 两例（几何/主色）全绿。
- 产物：jar 415,827B，SHA256 `c11defd06b0d9389acacbfc1561b4fd65eca8ba62aef2a667bda822b87751a5f`；jar 内复核 `PlayerMarkerTexture.class` + `assets/draginventory/textures/gui/player_marker.png`（5,134B）均在。

## 开发中修复

1. **误删 button() 方法**：按行号批量替换 player 段时定位逻辑（找 withAlpha 后第 2 个闭合花括号）把紧随其后的 `button()` 方法整段吞掉——靠推送前 `git diff` 全量过目抓住，恢复时又写错一处 lambda（`message -> message` 漏 `.get()`），再以 `git show HEAD:` 原文核对修正。教训：**按行号删改后必须 diff 核对边界方法**，恢复代码以 git 原文为准而非记忆。
2. **Resource 非 AutoCloseable**（上节）：1.21.1 的 `Resource` 不实现 AutoCloseable，try-with-resources 只能包 `resource.open()` 的 InputStream。

## 遗留

- 实机观感（纹理平滑度/视觉大小/动效）待用户实机复测；mipmap 深度与 CANVAS 为常量区一键微调项（PlayerMarkerTexture.CANVAS / MAX_MIP）。
- mapSmoke 显示环境验证需用户实机执行。
