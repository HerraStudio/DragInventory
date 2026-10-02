# Drag Inventory 2.5.9 —— 修复玩家图标不显示（纹理管线回归原版）

本版一项交付：修复 v2.5.8 玩家导航箭头在实机地图上不显示的问题。图标内容、视觉大小、动效与 v2.5.8 设计完全一致，变的只有"画法"——从自研纹理管线彻底回归原版管线。

问题——图标在地图上不显示：

- 现象：v2.5.8 的小地图/大地图玩家箭头完全不渲染（非错位、非变形，是无任何可见输出）。
- 排查：对 v2.5.8 的自定义纹理链路（AbstractTexture 子类 + MipmapGenerator + prepareImage/upload/setFilter + Tesselator position_tex 四边形）逐环节比对 NeoForge 21.1.251 反编译源码（TextureManager.register/getTexture、RenderSystem.setShaderTexture、ShaderInstance.setDefaultUniforms/apply、BufferUploader/VertexBuffer、NativeImage._upload、MipmapGenerator、TextureMetadataSection），全链路静态审计无一处偏差；jar 产物复核（类/资源/字节码/SHA）亦无误。沙箱无显示环境不可复现实机运行时，根因未定位。

解法——彻底回归原版管线（消灭全部自研 GL 代码）：

- 删除 `PlayerMarkerTexture` 自研纹理类与 `ensurePlayerTexture` 懒注册；`FactoryMapUI.player()` 改用原版 `GuiGraphics.blit` 绘制——与原版 HUD 全部贴图（快捷栏同款）同一条加载与渲染路径（SimpleTexture 自动加载、setShaderTexture、position_tex 四边形全部由原版内部完成）。
- 线性过滤（无锯齿的保障）改由原版 `.mcmeta` 机制声明：纹理旁新增 `player_marker.png.mcmeta`（`blur: true`），SimpleTexture 加载时按 LINEAR 过滤上传——边缘平滑如矢量的观感不变。
- **不存在静默不可见的失败态**：纹理缺失时原版回退紫黑棋盘，游戏内一眼可辨；这是原版对所有 GUI 贴图的承诺，本版完整继承。
- 图标内容仍为用户《玩家位置图标.svg》原图（纯 #32CD32 平面单色，零再创作，PNG 字节未变）；动效全家桶保留（partialTick 插值移动、120ms 平滑转向、弹性拉伸、渐隐尾迹、静止呼吸、断流保护），残影 alpha 经 shaderColor 叠透。

其它：

- 视觉大小与 v2.5.7/v2.5.8 一致（形状高 ≈12.5px，画布换算系数 15.5/256 不变）。
- 资源守卫测试增补 mcmeta 断言（存在性 + `blur: true` 声明——缺失时纹理回退 NEAREST 最近邻采样，锯齿回归），全套 **180 用例 0 失败 0 错误**。
- 实机复测要点：小地图（居中箭头常显）与大地图（玩家位于当前楼层区域内时）均应显示绿色箭头；缩放 1x~4x 与呼吸/拉伸下边缘平滑。

适配说明：纯客户端变更，无配置改动。适用：Minecraft Java 1.21.1 + NeoForge 21.1.251 + LDLib2 2.2.40（或 2.2.41），Java 21。直接替换 mods 内旧版 JAR 即可。
