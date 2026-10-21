# S3 实时输入开发状态

日期：2026-10-21。本页保留 0.2.0 实时输入基线和后续人工检查表；0.3.0 中文接入见 [中文运行时](chinese-runtime.md)。优先配置为 **new3ds_view，仅上屏**，真实 new3ds_view 验收现按用户要求暂缓。0.2.0 编译、固定图回归、构造 WGC 集成及主窗口启动证据见 [编译运行验证](live-validation.md)，真实投屏软件未验收。

## 源码交付

| 部分 | 实现及边界 |
| - | - |
| `native/capture/` | C++20 / C++/WinRT / D3D11 / JNI。枚举有标题的可见窗口，由用户选择 HWND/PID；WGC 捕获该窗口，回调仅更新最新 CPU 图像，不调用 Java。没有桌面截图或其他捕获后端 |
| `WindowCapture` | 同一工作线程拥有 JNI 句柄；按原生采集代次/帧 ID 复用未变化帧；图像为物理像素 ARGB。DLL 延迟加载，保留原有离线入口 |
| 健康与清理 | 每次观察检查 HWND/PID、最小化、窗口/客户区/DPI 变化、捕获项关闭及 D3D 设备错误；窗口或设备失效停止捕获。最小化停止帧池；恢复、尺寸及 DPI 变化重建 WGC 会话，清除旧图像与识别结果。窗口关闭后要求重新选择 |
| `ViewportResolver` | 只上屏配置；在完整捕获范围和两种客户区原点假设中，按 400k×240k、边缘/居中对齐生成有限候选。用既有目标框线和清浊点行结构检查，再连续 3 次、间隔至少 100ms 确认唯一候选；不查 OCR 答案、词典或台词。候选不唯一/无证据时暂停识别，不要求精确框选 |
| `LiveSession` | 一个后台线程串行处理最新帧、校准和既有 OCR。使用固定延迟 100ms 的观察循环，延迟不补时钟；健康静止会话可重用最新帧。帧到达和观察时间均取原生 QPC 纳秒，同一时钟域、分别保留。既有稳定计数及内容代次决定 OCR 请求 |
| 发布与选择位置 | 旧结果按会话、映射、区域及内容代次撤下；光标位置独立读取。切窗/停止/重校准立即变更 UI 选择代次，旧工作结果不再显示；文字和视图不积压排队 |
| `LiveApplication` | 简单 JavaFX 日文窗口：选窗、刷新、开始/停止、重新校准、GB 预览、正文/列表/退出文字、选择位置、未知字符及同形候选。高级详情保留 HWND/PID、采集代次、帧 ID、QPC 时间、物理尺寸和映射。无中文翻译 |
| 构建入口 | `pom.xml` 增加 JavaFX 21.0.8 Windows 依赖，版本改为 0.2.0；增加 `build-capture.ps1`、`run-live.ps1`。验收比较程序从 Maven 接收版本，避免将后续结果误标为 0.1.0 |

窗口客户区的两个原点分别相对于 DWM 扩展框和 Win32 窗口框计算，均为候选，不将任一坐标约定直接当成已验证的 WGC 对应关系。移动但几何不变时保留会话；客户区、窗口尺寸或 DPI 变化重新确认映射。已校准后无框地图/空白画面不视为捕获故障，目标消失仍撤下旧文字。

当前候选只涵盖仅上屏的边缘/居中对齐，任意工具栏偏移、上下屏同窗、左右排列及任意缩放不在本次适配声明内。遇到实际反例后再按证据补充，不静默回退为手动硬编码 `(0,0,3)`。本次没有测试 new3ds_view，也没有测试 cc3dsfs，均不能标记“支持后台捕获”。

## 检查证据

PowerShell/POM 静态检查、JNI 签名与状态枚举、物理坐标、行跨度、QPC 时钟及资源生命周期核对见源码；动态证据统一见 [0.2.0 编译运行验证](live-validation.md)。原图、字库及独立答案未修改。原 [0.1.0 验收报告](japanese-acceptance.md) 的逐图 JSON 另存为 `japanese-0.1.0.json`；最新 `japanese-all.json` 属于 0.2.0。

## 后续人工执行

构建条件：Windows x64；Corretto JDK 21.0.9.10.1、Maven 3.9.16；Visual Studio 2022 的 Desktop C++ / MSVC v143；Windows SDK **10.0.26100.0**（脚本固定选择）；CMake 3.28 及以上。CMake 使用 SDK 自带的 C++/WinRT 头文件，原生 DLL 静态链接 MSVC 运行库，本机 Release 构建及依赖检查已通过。JavaFX 由 Maven 获取，普通用户自带运行时的发行包留待 S6。

本机已解压官方工具包，使用 `./build-capture.ps1 -UseLocalTools`，详见验证报告。已安装 VS 的其他环境使用下列入口；构建/测试仍须在用户授权范围内执行：

```powershell
./build.ps1 -SkipTests
./build-capture.ps1
./run-live.ps1
```

`-SkipTests` 只表示跳过自动测试，不代表完成验收。启动后选择 new3ds_view 窗口，确认软件仅显示上屏、GB 原生 1:1、投屏整数倍放大，再按“开始”。显示一个目标对话框或列表，等待校准。缺 DLL/依赖时，修复构建环境后重启应用。手动启动 JAR 时从项目根目录执行 `java -jar target/g1-ocr-0.2.0.jar --live`；可通过 `-Dg1ocr.capture.dll=<绝对路径>` 指定 DLL。启动脚本将 JavaFX 缓存放在项目输出目录。

| 人工检查 | 通过依据 | 当前状态 |
| - | - | - |
| 构建与启动 | Java/C++ 构建成功；DLL/JNI 可加载；窗口能列出并启动/停止 | 构建、JNI、主窗口启动已验证；界面开始/停止待人工 |
| 像素与校准 | GB 预览范围、文字网格、清浊点正确；实际倍率/偏移对应；非整数缩放或歧义时暂停 | 未执行 |
| 实时日文 | 对话、操作、技能、列表、数值、footer、同形候选及未知字符按当前画面显示 | 未执行 |
| 静止与文字变化 | 静止不重复 OCR；逐字、翻页、滚动、A→B→A 后重新稳定；旧内容撤下 | 未执行 |
| 指针与箭头 | 指针独立更新；闪烁不改变正文稳定计数 | 未执行 |
| 遮挡/焦点 | 正常、部分/完全遮挡、非活动分别操作实机，必须看到操作产生的新图像/新文字；记录帧 ID 和到达时间 | 未执行 |
| 移动/缩放/DPI | 移动正常；尺寸、客户区和 DPI 变化清空并重新校准，不能沿用旧映射 | 未执行 |
| 最小化/恢复 | 暂停并清空；恢复等待新会话帧和重新校准 | 未执行 |
| 关闭/重启/切窗 | 停止旧目标，清空；重新选择有效窗口；旧请求不能覆盖当前内容 | 未执行 |
| 退出 | 停止采样、释放帧池/会话和 JNI 句柄；进程正常退出 | 未执行 |
| 离线回归 | 当前 0.2.0 对全部 108 张重新比较，另报当前实现哈希 | 已通过：108/108，2186/2186；见编译运行验证 |

记录投屏软件/系统/游戏版本、窗口布局、物理尺寸与 DPI、倍率、失败步骤和画面。QPC 到达时间是捕获回调观察到帧的时间，不是实机画面产生时间；当前没有测量 capture → UI 的实际呈现延迟，也没有端到端性能通过结论。

## 接口资料

实现依据为 [Microsoft HWND/WGC 示例](https://github.com/microsoft/Windows.UI.Composition-Win32-Samples/tree/master/cpp/ScreenCaptureforHWND) 与 [CreateForWindow 文档](https://learn.microsoft.com/en-us/windows/win32/api/windows.graphics.capture.interop/nf-windows-graphics-capture-interop-igraphicscaptureiteminterop-createforwindow)；D3D 互操作类型依据 [Microsoft SDK 头文件](https://github.com/microsoft/win32metadata/blob/main/generation/WinSDK/RecompiledIdlHeaders/um/windows.graphics.directx.direct3d11.interop.h)，JavaFX 依赖与普通入口启动方式参考 [OpenJFX 文档](https://openjfx.io/openjfx-docs/)。仅引用接口与生命周期，不将样例成功视为目标投屏软件的兼容性证据。
