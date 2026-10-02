# v2.5.9 验证记录（修复玩家图标不显示）

## 问题与排查

**用户反馈**：v2.5.8 玩家位置图标在地图上不显示。

**排查过程（静态审计，全部对照 build/moddev 反编译源码 NeoForge 21.1.251）**：

1. 调用面：三处渲染入口（小地图 FactoryMinimapHud:203 / 大地图 FactoryMapScreen:160 / 设置预览 MapSettingsScreen:215）主图标均传 alpha=1f（无早退）；调用方无异常吞噬。
2. v2.5.8 纹理链路逐环节核对：
   - `TextureManager.register` → `loadTexture` → `load()` 立即执行（源码 62-74 行）✓
   - `TextureUtil.prepareImage(id, mip, w, h)` 逐层 `_texImage2D` 分配 ✓
   - `NativeImage.upload(level, x, y, autoClose)` 四参重载语义核对（第 4 参实为 autoClose，传 false 无害）✓
   - `AbstractTexture.setFilter(true, true)` → 9987 LINEAR_MIPMAP_LINEAR ✓
   - `RenderSystem.setShaderTexture(0, RL)` → `getTexture(RL)` → 注册表命中 → `shaderTextures[0] = id` ✓
   - `BufferUploader.drawWithShader` → `VertexBuffer._drawWithShader` → `setDefaultUniforms`（把 shaderTextures 灌入 samplerMap）→ `apply()`（绑定采样器）✓
   - 绘制代码与 vanilla `GuiGraphics.innerBlit`（position_tex 变体，586-597 行）逐行一致 ✓
3. 资源实证：PNG 像素级解析（自写 PNG 解码）——256×256 RGBA，15,145 不透明 #32CD32 像素 + 786 抗锯齿半透明边缘，alpha 0..255 正常，无"全透明"异常；release jar（c11defd0…）内 PNG 与源文件 cmp 一致、PlayerMarkerTexture.class 存在、FactoryMapUI.class 字节码与源码逐条对应。
4. 版本偏差排除：neoforge.mods.toml 锁定 `[21.1.251, 21.2)`，审计源码即用户运行版本。
5. 结论：**全链路静态审计无一处偏差，沙箱无显示环境无法复现实机运行时，根因未定位**——按工程决策放弃继续盲猜，整体替换为原版管线。

## 修复内容

- 删除 `PlayerMarkerTexture.java`（自研 AbstractTexture + MipmapGenerator + prepareImage/upload/setFilter 全部移除）与 `FactoryMapUI.ensurePlayerTexture()`。
- `FactoryMapUI.player()` 改用原版 `GuiGraphics.blit(PLAYER_MARKER_ID, -128, -128, 0, 0, 256, 256)`：纹理经 `TextureManager.getTexture` → SimpleTexture 自动加载（与原版全部 GUI 贴图同路径），pose 栈承担平移/旋转（180°+yaw）/缩放（15.5/256 × 呼吸 × 拉伸），残影 alpha 经 `RenderSystem.setShaderColor` 叠透后复位；深度/混合状态簿记与 v2.5.8 相同（进入 disableDepth+enableBlend、退出恢复）。
- 新增 `player_marker.png.mcmeta`（`{"texture":{"blur":true,"clamp":false}}`）——SimpleTexture 按 LINEAR 过滤加载（TextureMetadataSection 原版机制），边缘平滑如矢量。
- 失败态可见性：纹理缺失 → 原版回退紫黑棋盘（MissingTextureAtlasSprite），不存在静默不可见状态。

## 门禁记录

- 构建：`./gradlew -Dorg.gradle.jvmargs=-Xmx768m build`（JAVA_HOME=jdk-21.0.12.1+1，构建期停 dev server 腾内存）——BUILD SUCCESSFUL（增量 6s；compileJava 仅 1 条既有弃用 Note）。
- 测试：**180 用例 0 失败 0 错误 0 跳过**（179 + 1 新增 `playerMarkerMcmetaDeclaresLinearBlur`：mcmeta 存在 + texture 段 + blur:true 三断言；开发中修正一处正则缺陷：Java `"\s"` 是空格转义非正则空白类，改 `"\\s"`）。
- jar：`drag-inventory-1.21.1-neoforge-2.5.9.jar` 413,812 字节，SHA256 `d51510ffe69438ce7c1570c0fbefde4fd179d5d0a011eef3afcfdd7e0cb227e5`。
- jar 内复核：`PlayerMarkerTexture.class` 已删除 ✓；`player_marker.png`（5,134B，与源 cmp 一致）✓；`player_marker.png.mcmeta`（60B，内容正确）✓；`FactoryMapUI.class` 字节码含 `GuiGraphics.blit(ResourceLocation,IIIIII)V` 调用、无 setShaderTexture/Tesselator 残留 ✓；画布系数 0.060546875f = 15.5/256 ✓。
- 打包：pack-release.py VER=2.5.9 / EXPECT_ENTRIES=181（179 − PlayerMarkerTexture.java + mcmeta + RELEASE_NOTES-v2.5.9.md + v2.5.9/VALIDATION.md），断言一次命中。

## 经验教训

- **"审计全对但不工作"时的工程决策**：当自研代码与框架源码逐行核对无偏差、而问题只在不可复现的环境出现时，正确解法不是继续找"看不见的运行时差异"，而是把自研部分整体替换为框架原版路径——原版路径有亿级用户验证，且失败态可见（缺失纹理回退棋盘）。
- **MultiEdit 非原子坑**：本工具的 MultiEdit 按序应用、遇错即停但**不回滚已应用的编辑**——本次第一段（imports）已应用而第二段失败，留下不一致中间态，靠 grep 残留引用 + 后续编辑修复。教训：MultiEdit 失败后必须先核对文件当前状态再重试，不能按"原子失败"假设整体重放。
- **Java 正则细节**：字符串字面量 `"\s"`（Java 15+ 转义）≠ 正则空白类 `"\\s"`——测试断言用错会把"只匹配空格"当成"匹配所有空白"，恰好本例文件格式下结果相同，属侥幸命中。

## 遗留

- 实机复测项（用户执行）：小地图居中箭头常显；大地图玩家在当前层时显示；1x~4x 缩放与呼吸/拉伸下边缘平滑无锯齿；尾迹残影正常渐隐。
- mapSmoke 系列显示环境验证仍需用户实机执行（与本版无关，沿袭历史遗留）。
