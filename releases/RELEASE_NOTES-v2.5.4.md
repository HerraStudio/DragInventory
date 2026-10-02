# Drag Inventory 2.5.4 —— 启动自检日志（排障锚点）

本版不含任何功能 / 视觉 / 行为变更，针对 2.5.3 一次实机排障暴露的"可诊断性"缺口：

背景：玩家报告"装上模组后卡在红色加载界面"。分析日志定位为整合包内 GWO（Guns Workshop Origins）模组在资源加载路径上同步请求其内容服务器 `api.playgwo.com`，网络不可达时线程池被占满约 4 分钟，最终 NeoForge 的 Common Setup 阶段进入异常状态（creative 系模组报 "Key already registered"）。经在干净环境复现对照（GWO 在场+网络黑洞=复现卡死；GWO 移除=正常；GWO 在场+网络畅通=正常），确认与本模组无关——但排障过程中发现**本模组在整个 latest.log 里零输出**：无法判断 construct 是否执行、配置阶段是否推进，只能靠排除法，成本高。

改进——启动自检 banner：主类构造完成时输出一条确定性锚点日志：

```
[main/INFO] [DragInventory/]: Drag Inventory construct ok: version=2.5.4 (modid=draginventory, dist=CLIENT, java=21.0.x)
```

它出现即证明本模组 construct 已完成（含三个配置文件注册）。配合既有的 MapConfigEvents 一次性日志（migration / retune marker），未来用户日志可完整覆盖"construct → config → 常见故障区"三个阶段，无需再靠排除法。

适配说明：直接替换 mods 内旧版 JAR 即可，无配置迁移。

适用：Minecraft Java 1.21.1 + NeoForge 21.1.251 + LDLib2 2.2.40（或 2.2.41），Java 21。

附带排障结论（供遇到"卡红色加载界面"的玩家参考，详见 releases/v2.5.4/VALIDATION.md）：

1. 若日志出现 `Cannot connect to GWO content server https://api.playgwo.com`（TransientFailure）——为整合包内 GWO 模组的联网校验在等超时，属于网络对 `api.playgwo.com` 不可达；换网络环境（热点/代理）或稍后再试。
2. mods 文件夹建议清理同名双版本残留（本次案例中 `tactical-inventory` 1.0.14 与 1.0.9 两个 jar 并存）。
