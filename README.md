# 宝可梦 G1 日版 3DS VC 实时 OCR 翻译

Windows 本地工具：直接获取 `new3ds_view` 或 `cc3dsfs` 投屏窗口中的日文，在独立 JavaFX 窗口显示有据可查的中文汉化文本。

处理流程：整数倍画面 → GB 160×144 → 固定文字区域 → 100ms 采样、连续 3 次相同 → 像素 OCR → 本地汉化匹配 → 对话或列表显示。

## 开发入口

- [Spec](pokemon_g1_vc_ocr_SPEC.md)：唯一产品需求、六类目标、技术边界与验收标准。
- [ExecPlan](execplan.md)：从 S0 开始的实施步骤、目录约定、交付物与完成条件。
- [AGENTS.md](AGENTS.md)：修改范围、简单实现、验证与动态测试授权规则。
- [素材说明](testpic/README.md) / [截图索引](testpic/manifest.csv)：原始截图、候选样例与证据边界。
- [日文文本档案](testpic/labels/japanese.md) / [标注说明](testpic/labels/README.md)：108 张逐图日文答案；当前要求目标区域原始日文识别 100%，中文标注及最终中文验收条件待定。

## 首版使用条件

- Windows 10 1903 或更新版本 / Windows 11，x64。
- 3DS 上屏，VC 日版《宝可梦 红、绿、蓝、皮卡丘》，原生 1:1 显示，投屏按点对点整数倍输出。
- 目标为主对话、战斗操作、战斗技能、背包道具、PC 道具和 PC 宝可梦列表六类区域。
- 要求窗口遮挡后仍可捕获；最小化时允许暂停。具体投屏软件和游戏版本的支持声明须有相应实机证据。
- 普通用户使用包含运行时和原生 DLL 的交付包，无需安装开发工具。

像素短时稳定只决定何时尝试 OCR；绘制中途停顿可能提前触发，后续变化后重新识别。未可靠识别、译文存在实际冲突或显示条件不足时保留日文/未知状态，不猜测译文。

## 项目组织

```text
AGENTS.md                     开发规则
pokemon_g1_vc_ocr_SPEC.md      Spec
execplan.md                   ExecPlan
spec.md                       规范导航
testpic/                      原始 PNG、素材说明与索引
```

Java 单应用代码位于 `src/main/java/`，像素字库及来源说明位于 `src/main/resources/`；`tools/` 只含开发工具。`native/capture/` 是新增的薄 Windows WGC/JNI 捕获模块。

## 日中映射语料（S4 建设中）

[CSV 资源与用法](src/main/resources/corpus/README.md) 已固定四版本日文及用户选定的仿日版中文来源，包含完整文本、名称词条、战斗组合模板、变量和显示控制关系。10,187 条记录已建立来源对应或明确组成/未引用状态；中文原始单元格保留不改。

已保存多页/滚动对白的中文语义分段与前文条件，并修复 23 条记录的来源漏接或错配。另有 9 类、23 条来源记录的语义差异已按用户确认处理，补译、订正及接受的汉化增省文均保留原汉化与决定。23,939 个可见行窗口中，14,441 个有直接中文范围，1,962 个还需已确认前文，7,388 个等待完整语义单元，6 个阻止已知语义错译；141 个通过组合处理，1 个源码明确未引用。0.3.0 已接入运行时，建设计数不代表运行时命中率或最终中文验收通过。静态资源见 [建库状态](reports/text-corpus.md)，当前实现与验证见 [中文运行时](reports/chinese-runtime.md)。

## 当前开发版本（0.3.0，中文运行时已接入，实机验收暂缓）

全部 108 张已按无需选择版本的新规则完成中文离线测试：79 张全单元就绪、6 张部分输出、18 张无中文、5 张无目标。7 类实现/字段缺口已按根因修复；原汉化把“研究化石”写成“研究宝可梦”的单元已阻止显示，具体订正待确认。11 张受影响图片的 23 个固定预期比较通过，就绪仍不等于全量中文语义验收通过。逐图结果和待处理项见 [全图中文验证](reports/chinese-images.md)，当前不能宣称全部图片中文正确。

沿用实时捕获、自动校准与像素 OCR，优先配置为 **new3ds_view，仅上屏**。中文/状态和原始日文分别显示，无需确认或选择游戏版本。匹配器联合全部语料；来源唯一或全部候选当前中文、变量及显示条件一致时发布保存的译文，并保留全部来源。实际冲突、未完成片段、缺少连续前文或变量时保留日文。数量、等级与选择位置保留；PC 昵称不按物种名强译。来源和已批准修订可在详情中查看。

本机现有运行文件已就绪，**双击项目根目录的 `run-live.cmd` 即可启动界面**。脚本自动进入项目目录，检查 Java、应用 JAR 和捕获 DLL；运行失败时保留控制台错误，便于排查。启动后选择 new3ds_view 窗口，点击“开始”，无需选择游戏版本。PowerShell 用户也可执行 `./run-live.ps1`。

0.3.0 的 Java 编译、中文开发场景和 108 张日文回归证据见 [中文运行时](reports/chinese-runtime.md)。原生 DLL 与 WGC 构造窗口的历史验证仍属于 0.2.0，见 [编译运行验证](reports/live-validation.md)。真实 new3ds_view 验收按用户要求暂缓；两种投屏软件、真实时序、当前 JavaFX 界面交互及端到端延迟均未验收。人工检查表见 [实时开发记录](reports/live-development.md)。需要重新构建时，本机已有工具链，可执行：

```powershell
./build.ps1
./build-capture.ps1 -UseLocalTools
./run-live.ps1
```

原生构建需要 VS 2022 Desktop C++ / MSVC v143、Windows SDK 10.0.26100.0 和 CMake 3.28+；Java 使用下述 JDK/Maven，新增 JavaFX 21.0.8 Windows 依赖。本机采用微软官方包在 `.tools/native/` 解压构建；已安装开发工具的环境使用不带 `-UseLocalTools` 的默认入口，该路线尚未验证。当前不是免开发环境安装的正式发行包，两种投屏软件的兼容性仍待人工验证。

## 离线入口与 0.1.0 验收基线

已提交的 0.1.0（`0107e12`）对全部 108 张固定截图通过原始 OCR 验收：202 行、2186 个字符 token，CER=0，全图正确率 100%。按用户确认，仅四组完全同形假名等价比较，并保留候选；严格 Unicode 结果另列为 95/108。详见 [历史验收报告](reports/japanese-acceptance.md)、[0.1.0 逐项结果](reports/japanese-0.1.0.json) 及 [布局证据](reports/layouts.md)。当前版本的回归证据见 [最新逐项结果](reports/japanese-all.json)。

开发工具链固定为 Amazon Corretto JDK `21.0.9.10.1`（Windows x64）、Maven `3.9.16`、JavaFX `21.0.8`。0.2.0 主窗口启动及 JNI/WGC 历史集成证据见编译运行报告；0.3.0 的构建与离线验证见中文运行时报告。离线运行不加载捕获 DLL，不需要 Python、网络或标注。

本机开发 JDK 放在被忽略的 `.tools/jdk21.0.9_10/`；其他环境设置 `JAVA_HOME` 指向 JDK 21，并让 `mvn` 可执行即可。固定 JDK [下载地址](https://corretto.aws/downloads/resources/21.0.9.10.1/amazon-corretto-21.0.9.10.1-windows-x64-jdk.zip)，ZIP SHA-256：`77226707f66b0c19a5baeb2c176ff5cc1c7d0f2a9f67f52354ce2901a53b9240`。正式自带运行时的用户包属于 S6，本次尚未交付。

```powershell
# 构建并运行固定图片集、必要几何/构造时序回归（须在获授权范围内使用）
./build.ps1

# 单张离线识别：输入 PNG、上屏原点 ox/oy、整数倍率 k、输出目录
& ./.tools/jdk21.0.9_10/bin/java.exe -jar target/g1-ocr-0.3.0.jar `
  'testpic/Screenshot 2026-10-20 21-22-46.png' 0 0 3 output/example
```

输出为 `gb.png`、原始 `crop.png` 和 `ocr.json`，包含原始日文、footer、选择位置、字形诊断，以及独立的 `translation` 匹配/显示状态、候选、中文与来源。`equivalent` 表示多个来源的当前结果一致，不能解释为只命中一个来源。离线入口只有上述五个参数，无游戏版本参数。`0 0 3` 仅适用于这批截图。单图不携带滚动前文，也不代表真实稳定性或实机验收。

`./build.ps1 -Group development` / `-Group holdout` 可按固定同源分组运行；默认 `all` 始终比较全部 108 张。构建缓存和输出写入 `.tools/m2/`、`target/`、`output/`，不改系统 Maven 配置。字库可用 `tools/import_font.py` 从固定上游重建；资源再分发条件仍待确认，见 [来源说明](src/main/resources/SOURCES.md)。
