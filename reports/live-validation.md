# 0.2.0 编译与运行验证

日期：2026-10-21。范围：用户授权编译运行验证；真实投屏软件验收仍留待人工。中文翻译未接入。

## 结果

| 检查 | 结果及证据 |
| - | - |
| Java 编译与打包 | `./build.ps1` 成功；默认 4 项测试通过，桌面 WGC 测试默认跳过 1 项；日志 `output/build-java-final.log` |
| 全部固定截图 | 当前 0.2.0：108/108，202 行，2186/2186 token，CER=0，未知字符 0；严格 Unicode 95/108、13 个同形假名差异。仅按已批准的四组同形假名等价，保留候选。逐图结果及当前源码哈希见 [japanese-all.json](japanese-all.json) |
| 原生编译与链接 | MSVC x64 / C++20 Release 成功，无编译警告；`output/build-native-local.log`。DLL 依赖检查只列 Windows 系统组件，没有动态 MSVC 运行库依赖 |
| WGC/JNI 集成 | 显式运行 `WgcSmokeTest`：1/1 通过。真实创建独立测试窗口，枚举 HWND/PID，打开 WGC，轮询 JNI 图像，自动校准、OCR，再关闭 JNI 会话；日志 `output/wgc-test.log` |
| 实际采集像素 | 测试窗口交替绘制 g1-002 对话和 g1-003 无目标地图；两幅经 WGC 捕获并还原的 160×144 图像均与原 PNG 还原图逐像素完全一致，来自不同帧。对话有识别输出，地图无目标文字输出 |
| 离线 JAR | 最终 JAR 对 g1-002 运行成功，生成 `output/verify-offline/{gb.png,crop.png,ocr.json}`；该入口不加载捕获 DLL |
| JavaFX 启动 | 最终 `./run-live.ps1` 启动成功；桌面窗口清单确认标题“G1 日文 OCR — 开发版”。窗口控制授权超时，未完成按钮、排版及正常退出的界面操作检查 |

固定截图不证明未见图片准确率；构造窗口不代表 new3ds_view 或 cc3dsfs 通过实机验收。0.1.0 历史逐图结果另存 [japanese-0.1.0.json](japanese-0.1.0.json)，没有覆盖写成当前版本证据。

## WGC 原始观察

系统版本为 Windows Build `22000.2538`，x64；测试窗口 DPI `144`（150%），客户区按物理像素绘制 1200×720。捕获尺寸 `1204×767`；自动映射为上屏起点 `(2,45)`、倍率 `3`，不是预设上屏原点。测试输入每 1500ms 切换一次，不模拟实机时序。

| 输入 | 原生帧 ID | 回调到达 QPC 纳秒 | 采样 QPC 纳秒 |
| - | - | - | - |
| g1-002 对话 | 31 | 252614711913600 | 252614728061700 |
| g1-003 地图 | 76 | 252615462701300 | 252615471443400 |

原始记录为 `output/wgc-smoke.txt`；两幅捕获图为 `output/wgc-input-0.png` 和 `output/wgc-input-1.png`。输出目录不纳入版本控制；可用保留的集成测试重建。这些时间只证明不同帧和同一时钟域，不作为 capture → UI 延迟测量。

## 工具链与复现

Java 使用 Corretto `21.0.9.10.1`、Maven `3.9.16`、JavaFX `21.0.8`。本机原生构建采用微软官方包解压到 `.tools/native/`：MSVC 编译器 `19.44.35229`（目录 `14.44.35207`）、Windows SDK NuGet `10.0.26100.9169`（头文件/工具目录 `10.0.26100.0`）、CMake `3.31.6-msvc6`、Ninja `1.12.1`。下载地址与 SHA-256 见 [工具链清单](native-toolchain.json)；VSIX 与官方 VS 清单哈希核对，NuGet 记录本地下载文件哈希。没有全局安装编译器或更改系统环境配置。

VSIX 中 `Contents/` 下的内容解压到 `.tools/native/vs/`；两个 SDK NuGet 分别解压到 `.tools/native/microsoft.windows.sdk.cpp/` 和 `.tools/native/microsoft.windows.sdk.cpp.x64/`。工具链未提交到仓库。

本机已准备工具链时：

```powershell
./build.ps1
./build-capture.ps1 -UseLocalTools
./run-live.ps1
```

`-UseLocalTools` 使用 `output/native-local/` 构建缓存，产物复制到启动入口使用的 `output/native/Release/g1capture.dll`。已安装 VS 2022 Desktop C++ / SDK / CMake 的环境可使用默认 `./build-capture.ps1`，但该生成器路线未在本机验证。

在有图形桌面、获动态测试授权后，显式运行桌面集成测试：

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk21.0.9_10').Path
mvn -B -s tools/maven-settings.xml '-Dmaven.repo.local=.tools/m2' `
  '-Dtest=WgcSmokeTest' '-Dg1ocr.wgc.smoke=true' test
```

本轮修正重复打包复用旧依赖 JAR 的问题：每次强制生成原始应用 JAR 再合并依赖，最终构建已不再报告重复依赖类。仍有依赖中同名许可证、清单及其他平台元数据资源的合并提示。JavaFX 非模块化启动仍有 `Unsupported JavaFX configuration` 警告，当前窗口启动成功；发行包属于 S6。

启动脚本将 JavaFX 原生库缓存放在 `output/javafx-cache/`，解决本次受限环境默认缓存路径不可写的问题。

最终产物：

| 文件 | 字节数 | SHA-256 |
| - | - | - |
| `target/g1-ocr-0.2.0.jar` | 11755346 | `59f4991d9074a257cdfed010ebe9ae0abc75a0da798b62d3b7371b1e1c90f792` |
| `output/native/Release/g1capture.dll` | 187904 | `6203934d8f9627f66c8c5b6f7da85bb796098eeeac79cbdb7b76d966c643e6e2` |

## 人工验收缺口

本轮未运行 new3ds_view 或 cc3dsfs；未验证其正常/遮挡/失焦/移动/缩放/DPI 切换/最小化恢复/关闭重启矩阵，也未验证真实逐字、翻页、滚动、指针和端到端延迟。JavaFX 按钮、文字排版和应用正常退出仍待人工检查。S3/S4 保留未验收状态，按 [人工步骤](live-development.md) 继续；没有增加中文翻译或正式发行包声明。
