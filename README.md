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

像素短时稳定只决定何时尝试 OCR；绘制中途停顿可能提前触发，后续变化后重新识别。未可靠识别或未找到唯一汉化对应时保留日文/未知状态，不猜测译文。

## 项目组织

```text
AGENTS.md                     开发规则
pokemon_g1_vc_ocr_SPEC.md      Spec
execplan.md                   ExecPlan
spec.md                       规范导航
testpic/                      原始 PNG、素材说明与索引
```

当前实现为 Java 单应用的离线识别链，代码位于 `src/main/java/`，像素字库及来源说明位于 `src/main/resources/`；`tools/` 只含开发工具。Windows 捕获模块在 S3 按需增加。

## 当前日文离线版本

全部 108 张固定截图已通过原始 OCR 验收：202 行、2186 个字符 token，CER=0，全图正确率 100%。按用户确认，仅四组完全同形假名等价比较，并保留候选；严格 Unicode 结果另列为 95/108。详见 [验收报告](reports/japanese-acceptance.md)、[108 张逐项结果](reports/japanese-all.json) 及 [布局证据](reports/layouts.md)。中文翻译、实时捕获和 JavaFX 界面尚未接入。

开发工具链固定为 Amazon Corretto JDK `21.0.9.10.1`（Windows x64）、Maven `3.9.16`。JavaFX 选用 `21.0.8`，在 S4 实际接入时再增加依赖及验证；后续 JNI/WGC 构建条件在 S3 落实。离线运行仅需 JDK 21，不需要 Python、网络或标注。

本机开发 JDK 放在被忽略的 `.tools/jdk21.0.9_10/`；其他环境设置 `JAVA_HOME` 指向 JDK 21，并让 `mvn` 可执行即可。固定 JDK [下载地址](https://corretto.aws/downloads/resources/21.0.9.10.1/amazon-corretto-21.0.9.10.1-windows-x64-jdk.zip)，ZIP SHA-256：`77226707f66b0c19a5baeb2c176ff5cc1c7d0f2a9f67f52354ce2901a53b9240`。正式自带运行时的用户包属于 S6，本次尚未交付。

```powershell
# 构建并运行固定图片集、必要几何/构造时序回归（须在获授权范围内使用）
./build.ps1

# 单张离线识别：输入 PNG、上屏原点 ox/oy、整数倍率 k、输出目录
& ./.tools/jdk21.0.9_10/bin/java.exe -jar target/g1-ocr-0.1.0.jar `
  'testpic/Screenshot 2026-10-20 21-22-53.png' 0 0 3 output/example
```

输出为 `gb.png`、原始 `crop.png` 和 `ocr.json`，包含原始日文行、footer、选择位置、候选、字形距离、未知位置及实际网格。`0 0 3` 仅是本批截图的已核对配置，其他输入必须提供可靠映射。单图命令直接检查静态图像，不将一张 PNG 重复采样冒称真实稳定性验收。

`./build.ps1 -Group development` / `-Group holdout` 可按固定同源分组运行；默认 `all` 始终比较全部 108 张。构建缓存和输出写入 `.tools/m2/`、`target/`、`output/`，不改系统 Maven 配置。字库可用 `tools/import_font.py` 从固定上游重建；资源再分发条件仍待确认，见 [来源说明](src/main/resources/SOURCES.md)。
