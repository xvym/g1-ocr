# 窗口采集与游戏区域定位

状态：已确认窗口遮挡不影响采集、自动定位优先、人工框选兜底，以及最小化暂停、恢复后重新定位。Q32 确认由 JNA 直接调用 Windows Graphics Capture，实际捕获与定位尚未实现。

## 窗口采集

[OBS 窗口采集说明](https://obsproject.com/kb/window-capture-sources) 明确：只采集选定窗口，即使有其他窗口挡在前面。这符合本项目需要的输入行为。

推荐通过 Windows Graphics Capture 绑定目标窗口。Win32 的 [CreateForWindow](https://learn.microsoft.com/en-us/windows/win32/api/windows.graphics.capture.interop/nf-windows-graphics-capture-interop-igraphicscaptureiteminterop-createforwindow) 可以按 HWND 建立采集对象，接口最低要求 Windows 10 1903；这是 API 要求，产品支持哪些 Windows 版本尚未确定。选择窗口后，处理采集帧内部的像素，无需跟随桌面坐标截图。

遮挡与最小化需要分开验收。[微软示例](https://github.com/microsoft/Windows.UI.Composition-Win32-Samples/blob/master/cpp/ScreenCaptureforHWND/README.md) 明确说明最小化窗口可枚举但不采集。已确认最小化时暂停，恢复后重新取帧与定位；窗口关闭时回到待选窗口状态。

首版输入范围已确认：游戏完整显示、宽高比正确、实际尺寸至少为原生 160 × 144，支持整数和非整数等比放大。非整数缩放后的严格点阵恢复需要实测验收。

## 尺寸与生命周期

窗口内容尺寸变化时，需要根据有效内容尺寸处理帧池。[微软采集流程](https://learn.microsoft.com/en-us/windows/apps/develop/media-authoring-processing/screen-capture) 说明旧帧池可能导致新内容截断或剩余纹理区域无定义。[OBS 实现](https://github.com/obsproject/obs-studio/blob/master/libobs-winrt/winrt-capture.cpp) 还核对实际纹理尺寸、客户区与最小化状态，并在需要时重建帧池。应同时验证内容范围与纹理范围，不能将旧纹理全部当作新画面。

游戏定位、裁剪和 OCR 均在采集纹理像素坐标系中进行。窗口移动不应改变这套坐标；尺寸、DPI 或窗口内部布局变化则触发核验或重新定位。

[GraphicsCaptureItem.Closed](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.graphicscaptureitem.closed) 用于处理窗口关闭或采集目标失效。目标软件也可能替换底层窗口，需要明确重新绑定流程。没有新帧、画面像素重复、窗口最小化应分别记录，不能因为游戏静止就判定断流。

## 自动游戏区域定位候选方案

自动定位在选定窗口的有效内容中完成，不扫描其他应用，也不把工具栏、侧栏和非游戏文字交给翻译。

1. 搜索候选矩形，以原生 160 × 144 网格和 10:9 比例为主要线索，容纳取整与非整数缩放误差。
2. 综合矩形边界、重复像素与图块网格、色阶、已知字体和文本框特征核验候选。比例或黑边单独不足以确定区域，游戏内部的大块黑色必须保留。
3. 将候选还原到原生网格，检查多个局部能否由同一空间变换解释；文字出现时再用严格字形命中提供额外证据。
4. 稳定后跟踪上一位置；窗口内容尺寸、DPI、布局或匹配条件变化时重新核验和搜索。过渡期暂停提交新识别结果。
5. 全黑转场、当前没有文本或暂时没有特征，不自动缩小裁剪范围；有效内容尺寸未变时保留上一几何候选但暂停不可靠的新结果。尺寸、DPI、窗口内部布局或核验证据冲突则使定位失效，推进代次，恢复特征后重新核验，不凭旧位置继续识别。
6. 没有可信候选或多个候选无法区分时提供人工框选。人工结果同样使用窗口内坐标，并在布局改变时重新核验。

这些是待验证算法设计，不能由静态样本推定定位成功率。

## 验证范围

定位测试包含把现有游戏样本嵌入带工具栏、侧栏和背景的窗口内容，改变位置、缩放和配色，并覆盖内部黑区、无文本画面、全黑转场及多候选情况。需要保存真实游戏矩形，测量定位误差与恢复时间。

实时采集另行验证部分遮挡、完全遮挡、窗口移动、缩放、跨 DPI 显示器、最小化恢复及关闭重开。静态 PNG 不能替代这些系统行为验证。用户不需要为本项目补充主机型号或投屏工具名称。
