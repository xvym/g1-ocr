# Java 直接调用系统采集接口

状态：Q32 已确认 Java 8 与 JNA 直接调用系统 WGC，不维护自有采集 DLL。下述接口路径有公开依据，但尚未编写或运行采集实现；端到端可行性必须通过首个原型关卡。

## 可以直接调用的依据

[JNA 官方说明](https://github.com/java-native-access/jna)明确支持用 Java 接口描述本机函数、结构体、指针与回调，无需项目编写自己的 JNI 或原生业务代码。WGC 通过 WinRT 与 COM ABI 暴露，不能只映射一个截图函数，但 C++ DLL 也不是先决条件。

JNA 已有 IUnknown、COM 虚表调用、引用计数和部分 Win32 接口。它没有可直接套用的完整 WGC 投影，项目仍须补齐实际用到的接口。开发应以微软接口定义、元数据和结构体布局为依据，逐项验证 IID、方法次序、调用约定和 x64 布局，不能猜测常量。

可复用的原始实现包括 [Unknown](https://github.com/java-native-access/jna/blob/master/contrib/platform/src/com/sun/jna/platform/win32/COM/Unknown.java) 和 [COMInvoker](https://github.com/java-native-access/jna/blob/master/contrib/platform/src/com/sun/jna/platform/win32/COM/COMInvoker.java)。已发布 JNA 5.19.1 的 [构建配置](https://github.com/java-native-access/jna/blob/5.19.1/common.xml)标明 Java 8 目标，可作为依赖候选；仍需使用目标 JDK 8 实际构建和运行验证，不能仅凭主分支文档宣布兼容。

## 最小调用链

| 环节 | 系统接口和职责 |
| --- | --- |
| 后台初始化 | RoInitialize、WindowsCreateString、RoGetActivationFactory，建立 WinRT 初始化和对象工厂 |
| 目标窗口 | IGraphicsCaptureItemInterop.CreateForWindow 按 HWND 创建采集目标 |
| 图形设备 | D3D11CreateDevice、IDXGIDevice 与 CreateDirect3D11DeviceFromDXGIDevice 建立 D3D11 和 WinRT 设备 |
| 帧池与会话 | CreateFreeThreaded、CreateCaptureSession 和 StartCapture |
| 主动取帧 | TryGetNextFrame，读取 ContentSize、Surface、SystemRelativeTime |
| 读取像素 | 通过 IDirect3DDxgiInterfaceAccess 获取纹理，复制到可读 staging 纹理，Map 后按 RowPitch 批量读取 BGRA |
| 生命周期 | 关闭每帧与会话、归还帧池、释放 COM 引用；尺寸变化时重建资源 |

[CreateForWindow](https://learn.microsoft.com/en-us/windows/win32/api/windows.graphics.capture.interop/nf-windows-graphics-capture-interop-igraphicscaptureiteminterop-createforwindow) 和 [D3D11 互操作](https://learn.microsoft.com/en-us/windows/win32/api/windows.graphics.directx.direct3d11.interop/nf-windows-graphics-directx-direct3d11-interop-createdirect3d11devicefromdxgidevice)是相关入口。上述表是待实现的职责划分，不是已验证的 Java 封装。

HSTRING 使用 WindowsCreateString 和对应释放函数管理，不能用 BSTR 或普通 WString 直接替代。接口 ABI 可对照微软 [windows-rs 生成源码](https://github.com/microsoft/windows-rs/blob/master/crates/libs/windows/src/Windows/Graphics/Capture/mod.rs)及官方接口定义核查；不依赖必须编译 C++ 才能访问的语言入口。

## 优先用主动拉帧降低首关复杂度

微软[采集流程](https://learn.microsoft.com/en-us/windows/apps/develop/media-authoring-processing/screen-capture)明确允许使用 TryGetNextFrame 手动获取帧；[CreateFreeThreaded](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.direct3d11captureframepool.createfreethreaded?view=winrt-26100)使帧池不依赖 DispatcherQueue。首关可在单个已初始化的后台线程轮询最新帧，以 Win32 窗口状态和 API 返回处理最小化与关闭，先不注册 FrameArrived 或 Closed 委托。

这一组合减少自实现 COM 事件对象的范围，但必须通过 Java 8 实测。UI 线程仅负责快照与预览显示；捕获、像素读取和资源释放在采集线程完成。

帧池缓冲区必须按时归还。[TryGetNextFrame](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.direct3d11captureframepool.trygetnextframe?view=winrt-26100) 取下一帧而非保证最新帧；捕获端有界清理旧帧，尽量只对选定帧做 CPU 读回，上层最新帧槽仍需有界。测量轮询间隔和实际帧龄，不能只看 Java 队列长度。

成功 Map 后，在 [Unmap](https://learn.microsoft.com/en-us/windows/win32/api/d3d11/nf-d3d11-id3d11devicecontext-unmap) 前按 RowPitch 完成逐行批量复制；由 Map 指针包装的 ByteBuffer 仍是原生视图。复制后只向上层提供 Java 自有副本及元数据，不保留已归还帧的纹理指针。帧的 [Close](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.direct3d11captureframe.close?view=winrt-26100) 与各 COM 引用的 Release 各有职责，异常路径也须执行；旧帧、尺寸变化和定位代次必须可识别。

## 自有 DLL 与第三方原生依赖

JNA 会加载其随附的 jnidispatch 原生组件，JavaFX 8 也包含原生库。因此直接调用方案免去的是项目自有 C++ 采集 DLL 的源代码与构建维护，不会移除 Windows 和第三方运行库。

自有 C++ DLL 曾是封装接口的候选，不是必要条件，当前不作为默认前提。直接 JNA 的维护内容变为 Java 层的 ABI 映射、线程和资源管理；不能把免去自有 DLL 等同于采集接口无需维护。

## 已找到的复用备选

[gst1-java-core](https://github.com/gstreamer-java/gst1-java-core)声明支持 Java 8，通过 JNA 调用 GStreamer。其 [d3d11screencapturesrc](https://gstreamer.freedesktop.org/documentation/d3d11/d3d11screencapturesrc.html)支持显式选择 WGC、按窗口句柄采集和输出 BGRA，Java AppSink 可以取得帧。

这条路径可避免项目手写整套 WGC 映射，但需要额外分发 GStreamer 原生组件。插件默认采集 API 是 DXGI，必须明确指定 WGC；不能直接复制该项目要求 Java 11+ 的 JavaFX 示例。尚未在本机 Java 8 端到端验证。

该方案仅为有来源依据的备选记录。当前先验证直接系统调用；如果发现具体阻碍，再依据失败证据比较引入第三方框架或其他封装，不默认增加多个采集后端。

## 验证完成条件

Java 8 下可从选定窗口持续读到正确 BGRA；遮挡不改变采集内容；移动、缩放和最小化恢复符合既定行为；反复启停后资源可释放；不积压旧帧；预览正确且能测量采集到显示的延迟。只有这项实测通过，才能认为本项目的直接绑定路径成立。
