# 宝可梦 VC 初代日版实时 OCR 翻译

一个从零开发的 Windows 本地工具：识别 **3DS VC 日版《宝可梦 红、绿、蓝、皮卡丘》**投屏画面的日文，在独立窗口显示**当前可见内容**的中文翻译。技术目标为 **Java 8 / JavaFX 8 / Maven / Windows 10 x64**。

开发进度、素材核验状态和下一步统一见 [.agent/EXECPLAN.md](.agent/EXECPLAN.md)。

## 文档导航

| 文件 | 只负责什么 |
| --- | --- |
| [spec.md](spec.md) | 产品要求、录像验证方法、验收门槛（**做什么、怎样才算通过**） |
| [AGENTS.md](AGENTS.md) | Codex 的入口指令及不可违反的行为约束 |
| [.agent/PLANS.md](.agent/PLANS.md) | ExecPlan 的结构、更新和恢复约定（**怎样维护计划**） |
| [.agent/EXECPLAN.md](.agent/EXECPLAN.md) | M0–M7 具体步骤、唯一进度和下次恢复位置（**现在做什么**） |
| `README.md` | 项目介绍、首次启动方式；不记录开发进度 |

规则优先级：**spec 的产品范围与验收标准不可因计划调整而降低**；`AGENTS.md` 约束执行行为；`PLANS.md` 约定如何维护 `EXECPLAN.md`；进度只写入 `EXECPLAN.md`。

## 唯一的离线录像素材

离线动态测试**仅使用用户已有的 4 段 3DS 实机原始录像**，从 `testpic/video/` 只读引用，或原样放入 `data/raw/video/`（加入 `.gitignore`）。`testpic/pic/` 中的截图仅作静态辅助，不能替代连续帧测试。素材身份与验证要求见 `spec.md §3`。

开发开始后，首先核对四个原文件的实际路径、SHA-256、时间戳、画面、游戏版本与真实会话关系；不能凭文件名推断内容。由四段录像选择代表性的**连续帧片段**，保留片段内的全部原始帧、时间和异常状态。未选择的区间或缺失的场景/版本标为**未验证**。

测试用的 **Ground Truth（GT，独立核验的标准答案）**由当前编码代理直接看原图双遍核读，不引入其他模型。**先完成现有四录像全部场景的 GT，再一次性进行人工审阅**：各场景可核验的代表性完整事件完成标注、双遍和完整性检查，缺失或经核查仍不能可靠核验的范围附依据、单列待验证；不要求四视频全文标注，也不补录。试标或部分场景完成不单独停下来等批准；全部场景按 `spec.md §3.4` 收口后，统一提交原帧/标注对照、时间边界、完整覆盖矩阵和复核记录，才暂停并等待明确批准对应版本。批准前不冻结新增 GT、不开发或运行被测 OCR/翻译。扩充及实质修订合并受影响场景重审；后续中文 GT 在 M4 通过后的对应阶段另行统一审阅。用户负责审阅批准，无须日常逐帧标注。

## 使用 Codex 从零启动

1. 在当前项目根目录工作，保留 `.agent/` 文件夹。
2. 从 `testpic/video/` 核对现有 **4 段录像**；不必复制或重新提供已经可访问的文件。缺失时登记事实，不能编造验证结果。
3. 让 Codex 读取 `AGENTS.md` → `spec.md` → `.agent/PLANS.md` → `.agent/EXECPLAN.md`，从 **M0** 开始。
4. 每个阶段先产生可复现证据，再更新 `.agent/EXECPLAN.md`；只按该文件的“下一步”继续执行。

可复制的首次指令：

> 请阅读 AGENTS.md、spec.md、.agent/PLANS.md、.agent/EXECPLAN.md，从 M0 开始按 Spec + ExecPlan 模式开发。离线动态测试只使用我已有的 4 段实机录像；先核对原件和时间轴，再由当前代理连续完成所有场景可核验范围的 GT 与双遍检查，缺失或不能可靠核验项附证据、标待验证。全部场景按覆盖矩阵收口后一次性提交审阅材料并停止，不在试标或部分场景完成时等待批准。等我明确批准统一版本后再冻结 GT、继续后续开发。不得引入其他模型、继承旧进度或把未实现命令当作成功结果。

## 开发顺序与边界

**工程 → 录像索引/连续帧测试 → 全部场景 GT 生成及代理双遍核读 → 一次性用户审阅批准 → GT 冻结 → 日文 OCR → 独立 OCR 验收 → 中日语料 → 翻译/UI → 真实环境验收**。没有可用独立验收会话则 OCR 验收待验证，不能进入生产中文映射。不从中文译文反推 OCR；产品无需模型、3DS 内存访问或 ROM 修改。FFmpeg/FFprobe 可作为开发期录像解码工具。

四段录像可以覆盖的范围取决于其真实内容。最终的真实窗口后台/遮挡采集、实际 UI 延迟和长时间稳定性需另作运行测试，但**不要求为此再录新的视频**。

## 构建与工程检查

准备 Windows x64、带 JavaFX 8 的 JDK 8 和 Maven。`JAVA_HOME` 应指向完整 JDK；工程分别在 `app/` 和 `verification/` 中，产品不依赖验证模块。依赖版本在 `pom.xml` 固定，缓存只写入项目 `.cache/m2/`。

```powershell
mvn -s config/maven-settings.xml -Dmaven.repo.local=.cache/m2 test
java -cp app/target/classes io.github.g1ocr.Main --self-check
java -cp app/target/classes io.github.g1ocr.Main
```

第三条打开工程检查窗口；功能可用范围和验证证据以 ExecPlan 为准。已经下载依赖后，可在 Maven 命令中增加 `-o` 离线构建。工程与录像的检查资料存放在 `reports/`，不包含原媒体的分发许可。

## 原帧工具

在 `config/video-tools.json` 配置本机 FFmpeg/FFprobe 可执行文件，`config/input-videos.json` 固定四原件的只读路径。当前解码配置要求 H264、BT709、limited-range，保留源帧尺寸、方向和整数时间；其他配置必须单独审计，不能自动猜测。所有输出需使用新目录，避免覆盖旧证据。

```powershell
./tools/validate.ps1 index config/input-videos.json data/meta/m1-v1
./tools/validate.ps1 export data/meta/m1-v1 video-01 0 30 data/frames/example 1
./tools/validate.ps1 sheet data/frames/example reports/example-sheet.png 5 320
```

`index` 从头连续解码全部原帧；`export` 的区间是 `[startFrame,endFrameExclusive)`，末参数 `1` 保留每一帧，并重新核对 RGB 像素与无损 PNG 读回哈希。末参数大于 `1` 只用于浏览导航，不能作为评分素材。拼图是最近邻缩小的辅助入口，完整原帧仍在导出目录，时间来自账本。命令退出 `0` 仅代表工具检查完成，`3` 为无效输入或证据不足，`4` 为工具错误；均不能单独证明 OCR 或产品验收通过。

`inspect` 可对已验证的连续导出逐帧校验并生成有序区域拼图与像素变化扫描；扫描仅用于定位，不自动生成文字答案。`gt-check` 核对候选 GT 与 clip、原媒体和字格/时间的结构一致性，拒绝未知状态、缺口与错误来源，但不会冻结 GT 或授予审阅批准。审阅包的 `index.html` 可在本地浏览器打开，所有原帧、放大图与 JSON/JSONL 同目录提供；应用内浏览器是否支持本地 HTML 取决于其 URL 安全策略。
