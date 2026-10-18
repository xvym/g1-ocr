# spec|宝可梦 VC 初代日版实时 OCR 翻译

**本文件定义产品需求、验证方法和验收标准。**

## 1. 目标、技术边界和范围

开发 **Windows 10 x64 / Java 8 / JavaFX 8 / Maven** 本地工具：从投屏窗口识别 **3DS VC 日版《宝可梦 红、绿、蓝、皮卡丘》**的当前可见日文，在**独立窗口**按画面类型显示中文。

- **运行时输入**：真实投屏窗口；不读 3DS 内存，不改 ROM、存档、游戏或投屏进程，不覆盖游戏原画面。
- **离线动态测试输入**：**使用位于testpic目录下的实机录像和截图**作为测试输入；允许从实机录像中采集更多的截图作为测试用例。
- **字体匹配**：匹配实际日版像素字库；不引入额外 OCR、视觉或语言模型作产品组件或核验依赖。开发期可用 FFmpeg/FFprobe 读取视频；正常使用不依赖 Python 和测试答案。
- **必需场景**：主文本框、探索/对话、战斗中选项与技能选择、背包、PC中的操作选项与道具、宝可梦列表、商店购物操作与道具列表、宝可梦状态菜单；其中**主文本框、背包、技能、PC道具和宝可梦列表作为优先级最高的翻译目标**；图鉴翻译不作必需项。
- **画面变化**：整倍/非整倍缩放、清浊点、逐字加载、短提示、翻页、滚动、清屏、光标和菜单切换、黑帧/无效帧。场景分类未知时，明确的主文本框仍应识别。
- **窗口采集**：支持窗口前台、非前台及被其他窗口遮挡时的有效画面采集；不能把遮挡后的桌面截图冒充目标窗口。最小化/采集失败允许“无有效帧”，但必须清除过期译文。
- **文本库建设**：建设四版本共用文本库。**不要求用户自行选择游戏版本，不区分版本也理应能通过文本库完成匹配和翻译。**

## 2. 可参考资料
以下为开源社区的可信材料，可以用于进行游戏场景、文本加载机制的理解及中、日文本映射库建设。

| 内容 | 项目地址 |
| - | - |
| 日版反编译 | [pokegreen](https://github.com/Narishma-gb/pokegreen)、[pokeblue](https://github.com/Narishma-gb/pokeblue)、[pokeyellow-jp](https://github.com/Narishma-gb/pokeyellow-jp) |
| 国际版反编译 | [pret/pokered](https://github.com/pret/pokered)、[pret/pokeyellow](https://github.com/pret/pokeyellow) |
| 国际版汉化 | [pokeredCHS](https://github.com/TomJinW/pokeredCHS)、[pokeyellowCHS](https://github.com/TomJinW/pokeyellowCHS) |
| 日文原始语料库 | [poke-corpus](https://github.com/abcboy101/poke-corpus) |
