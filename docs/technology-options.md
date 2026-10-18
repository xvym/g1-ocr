# Windows 桌面程序技术选型

状态：Q32 已确认 Java 8、JavaFX 8 与 JNA 直接调用系统 WGC，不维护自有采集 DLL；C# 已被否决，Python 建库已确认。采集链路尚未实测。核查日期为 2026-10-18，尚未创建应用工程。

## Java 8 与 JavaFX 8 主程序

推荐由 Java 8 承担游戏区域定位、网格归一化、严格字形查表、公共文本库匹配和区域更新器，JavaFX 8 承担独立译文面板、相对布局、中文阅读与采集预览。Maven 管理 Java 工程与依赖。

JavaFX 8 已有 [Pane](https://docs.oracle.com/javase/8/javafx/api/javafx/scene/layout/Pane.html)、[TextFlow](https://docs.oracle.com/javase/8/javafx/api/javafx/scene/text/TextFlow.html)、[WritableImage](https://docs.oracle.com/javase/8/javafx/api/javafx/scene/image/WritableImage.html) 和 [PixelWriter](https://docs.oracle.com/javase/8/javafx/api/javafx/scene/image/PixelWriter.html)，足以实现已确认的界面。一次 [Platform.runLater](https://docs.oracle.com/javase/8/javafx/api/javafx/application/Platform.html) 任务提交完整视图快照，长任务放在后台；逐区域反复排队不能代替批量提交。

实现必须使用真正的 Java 8 工具链验证：不依赖 Java 9 模块系统、较新 Java 语言语法或 JavaFX 13 的 PixelBuffer。使用 JavaFX 8 的批量像素更新与普通文字控件，避免逐像素跨原生边界调用。

## 已选采集边界

已选路线为：Java 8 与 JavaFX 8 → JNA 的 Java 接口映射 → Windows 自带的 WinRT、WGC 与 D3D11。

[JNA](https://github.com/java-native-access/jna) 支持直接调用本机动态库，无需编写自己的 JNI 或 C++ DLL。项目需要维护实际用到的 WinRT 与 D3D11 接口的 Java 映射及资源生命周期。JNA 现成的 COM 支持不等于现成完整 WGC 封装，缺失接口仍需补齐和验证。

首个验证建议使用 CreateFreeThreaded 与后台主动拉帧。微软[采集流程](https://learn.microsoft.com/en-us/windows/apps/develop/media-authoring-processing/screen-capture)明确允许 TryGetNextFrame 手动取帧，[CreateFreeThreaded](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.direct3d11captureframepool.createfreethreaded?view=winrt-26100)消除 DispatcherQueue 依赖。可先用单个后台线程完成初始化、拉帧、尺寸检查和释放，减少第一阶段的 COM 事件回调实现。

完整调用链、已有映射、需要补齐的部分与备选方案见 [Java 直接调用系统采集接口](capture-interop.md)。这是一条有接口依据的可行路径，尚未证明 Java 8 端到端实现已工作或已满足性能目标。

JNA 自身使用随库提供的 jnidispatch 原生组件，JavaFX 也有原生运行库。因此这里的目标是免维护自有 DLL，不是整包零原生依赖。

[Robot](https://docs.oracle.com/javase/8/docs/api/java/awt/Robot.html) 获取屏幕像素，不满足被遮挡窗口采集要求；[PrintWindow](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-printwindow) 依赖目标程序处理绘制消息，不能据此保证现代 GPU 窗口。默认保留已核查的 WGC 路线。

## 资源构建与交付

已确认 Python 承担开发阶段的工作表、反汇编文本和资源包构建，Java 程序仅消费构建完成的只读资源。用户不需要安装或运行 Python。

Java 8 并非所有发行物都包含 JavaFX。可采用有 JavaFX 8 的成套运行时，例如 [Azul ZuluFX 8](https://www.azul.com/blog/zulu-gets-graphical-with-openjfx/) 或 [Liberica 8 Full](https://docs.bell-sw.com/liberica-jdk/8u502b9/general/install-guide/)，实施时核实并固定 Windows x64 包、来源和版本。

推荐 Windows x64 目录或 ZIP 包：启动器、应用、构建好的文本库、配套 Java 8 与 JavaFX 8 运行时、JNA 及其标准原生组件。系统 WGC 和 D3D11 由 Windows 提供。启动器使用随附运行时，避免依赖用户的 PATH。Java 8 的 [自包含打包](https://docs.oracle.com/javase/8/docs/technotes/guides/deploy/self-contained-packaging.html) 可作参考，不把 jlink 或 jpackage 当作 JDK 8 工具。

## 当前开发环境与验证关卡

已只读确认 Oracle JDK/JRE 1.8.0_192 x64、javac 1.8.0_192、Maven 3.9.16 可用；JAVA_HOME 为 `D:\Code\Tool\jdk1.8.0_192`。该目录存在 jfxrt.jar、JavaFX 原生库和 javapackager。尚未运行 JavaFX 窗口验证图形表现。

系统为 Windows 11 21H2、Build 22000.2538、x64，CPU 为 i5-1135G7、8 个逻辑处理器，可见内存约 15.8 GiB。当前环境可以作为首个开发与性能基准，不是全部用户的最低配置声明。

第一采集关卡应优先验证 Java 8 → JNA → 系统 WGC → 最新帧 → JavaFX 预览，覆盖遮挡、移动、缩放和恢复。随后验证归一化、严格查表及视图提交的完整延迟。本机有 JavaFX 库不能替代这两项运行验证；自有 C++ 工具链不作为已选路线的前置条件。
