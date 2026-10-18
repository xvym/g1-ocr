# EXECPLAN｜从零开发与恢复记录

> **最新恢复状态：2026-10-17 v22原规则最终严格整页7→14/116，新增7页、0整页回退；本轮持续处理目标已取得实测提升，M3/M4仍未通过。** 增益来自显式日文源码页面边界辅助，原始图像严格仍7/116，原始188/最终177文字错保持；最终失败原帧2584→2429。187测试/package、6目标/同类片段、全38片实际回放及6736次独立逐帧复核通过。473保留/471评分引用得到来源确认，转换新增完成误判0，3577原发布门控完全保持；GT、OcrScoring判分条件、文字/坐标/附标/符号不变。PC关闭第2候选完成；授权追加的联合网格试验产生回退已拒绝，原几何3次＋此次1次封口，3541原3/3保持。100页像素框、17页问号等剩余失败、GT语义资格false/独立会话/UI缺口仍在；中文不启动。入口`reports/m3-grid-refinement-v1/source-page-boundary-production-v22/delivery/index.html`，唯一下一步见末尾。

> **历史状态：2026-10-17 队伍HP/冒号单元生产修复及r14/v20全38片复核完成，日文OCR总体目标仍未完成。** 第1候选只改队伍HP标签单元细分，20评分原帧/115标签恢复，全部3577保留引用只有允许的结构/耗时变化，门控逐项不变；178测试/package、目标与三同类片段、全38片回归及不导入评分器的6736次独立复核通过。字段位置一致页114→115、前四维合取raw60→61/final61→62仅诊断；文字97/98、符号113、状态79、像素框数值16保持。原/新严格均7/116，原始188/86569错、最终177错、2584失败帧、空误报0/692保持，GT和旧评分/实际输出文件不改。7附标差异帧、17问号页、100源框页等仍在；3箭头差异出现GT相位疑点，不再直接认定为程序多/漏检。M3/M4未过、中文不启动，UI/独立会话/GT语义资格仍待验证。入口`reports/m3-grid-refinement-v1/packed-render-units-fix-v1/delivery/index.html`，唯一下一步见末尾。

> **2026-10-16 审阅访问修订（历史，该GT卡点随后已解除）：** 根据安卓相对路径图片无法访问的反馈，另增 `reports/gt-review/gt-all-scenes-v2-mobile-v1-r3/index.html` 手机离线入口；在页面中选择已下载的原审阅目录/仓库 ZIP 后按需读取，不依赖相对文件访问。原包7636文件逐项SHA不变，候选范围/契约/批准状态不变；ZIP及控件逻辑检查通过，安卓真机呈现仍待验证。当时仅响应审阅反馈；后续明确批准、冻结及M3开发见本计划历史记录。

## Purpose / Big Picture

从零开发 Windows 10 x64 / Java 8 / JavaFX 8 本地程序：识别 3DS VC 日版《宝可梦 红、绿、蓝、皮卡丘》投屏画面中的当前日文，在独立窗口展示对应中文。研发顺序为**录像验证体系 → 日文 OCR → OCR 验收 → 中日关联 → 翻译/UI → 真实环境验收**。

**离线动态测试只允许用户现有 4 段原始实机录像**；不要求重录/补录，不添加其他视频代替。以全帧可追溯、先独立核验 Ground Truth（GT）、后冻结并回放比较为核心，不引入额外视觉/OCR/语言模型。不继承旧工程、帧索引、答案或测试结果。

## Context and Orientation

初始项目只有五份开发文档、`.gitignore` 和 `testpic/` 素材目录。2026-10-14 用户要求“请根据当前项目文档指引进行开发”后，从零建立根 Maven 聚合工程、`app/` 应用模块和 `verification/` 离线工具模块；两者无运行依赖，验证工具不包含 OCR。四视频可访问；首次试标已有按批准版本冻结的 GT，见下文。下文历史初始描述保留其当时语义，实际实现以最新证据为准。

已确认的视频相对路径（SHA-256/解码身份已核验，版本及独立会话范围见 `data/meta/coverage-observations-v1.json`）：

- `testpic/video/2026-10-11 22-34-17.mp4`
- `testpic/video/4a6e01eb2ea34cdbfdba41e9b4db6a88.mp4`
- `testpic/video/5b34074da5f0cd90a956a4178d77b637.mp4`
- `testpic/video/cd87cd5a156ef5cf5d64a43a1e6600fd.mp4`

| 位置 | 用途 |
| --- | --- |
| `pom.xml` / `app/src/` / `verification/src/` | Java 8 聚合工程、应用和隔离验证器与测试 |
| `config/input-videos.json` | 将上述 **4 段现有录像**登记为只读输入，身份另行核验 |
| `data/meta/` | 来源、原始帧 PTS/RGB 哈希、clip 与会话账本 |
| `data/gt/` | 独立核验并冻结的日文 GT；中文 GT 在后续阶段创建 |
| `resources/` | 来源提交/许可与日版字库、日文脚本索引 |
| `tools/` | 开发期确定性视频验证工具（允许 FFmpeg/FFprobe） |
| `reports/` | 实际输出、验证结果、差异及异常证据 |

**工程初始版本**：基线提交 `284b0715fcd9ed1973cb4b775e9caa990c0e6ace`，初始开发时新代码未提交。M0历史源文件清单 `reports/m0-20261014/source-snapshot-final.json`，SHA-256 `270d81a9a87fc2e529571d44f017de2881ac069da4536f79b1c945340bedd6e3`；初始构建身份见 `build-version-final.json`。早期视口阶段的80测试/package、几何失败及源身份保留在`reports/m3-viewport-v1/final-check-v1/`和`attempt-4/report.json`，属于历史版本。**当前OCR构建身份**为`reports/m3-grid-refinement-v1/build-evidence-v22-final/{source-snapshot,class-identity}.json`，187项Java测试通过（116 app＋71 verification），265源身份/105编译类；后增报告/复核辅助工具各自绑定SHA，不能冒称全在该时间点快照内。JAR SHA`f0bff070daddd489eac8fc92934b6bf83eed8c3b8e4416222019884b1ed6165b`，73包内类与当前编译类逐SHA相同。当前正常辅助配置`.cache/japanese-source-page-boundaries-v2/catalog.json` SHA`f3b05ece3212c5d74740ef063f7d647e769d73a5dc908bbdc7f44ab750d1d7ce`，3814个固定源blob/8653个保守页面模板；不含中文。当前权威实际回放r14/v22，原规则raw7/final14；原始图像观察与最终来源解释分别保存，未改GT/原OcrScoring条件。v20/v21可变产物（含31个旧测试XML）均事前归档，不能据此宣称补回早前缺失v19 XML。**当前开发GT封套**为`data/gt/frozen/ocr-all-scenes-v2-observation-r14/freeze.json`，旧封套全部保留，语义完整性资格仍为false。日文管线已能回放，整体指标未过；中文未启动。最新完整哈希、命令与限制见末尾。

原视频优先只读引用 `testpic/video/`；如需迁移，可把**同样四个原文件**原样置于 `data/raw/video/` 并忽略版本控制。未核验内容前不得猜版本、帧率或场景；现有截图不得继承为 GT。

术语：**原媒体**=四段视频文件；**clip**=从原媒体选取的连续帧区间；**warmup**=重放真实前史但不评分；**scored range**=冻结后逐帧计分区间；**GT**=独立于主 OCR 的正确可见内容/边界；**page opportunity**=一次完整页面可见机会。未选片段、未知标注、会话无法隔离等记为**待验证**。

## Plan of Work

| 里程碑 | 前置与实施重点 | 交付与完成证据 |
| --- | --- | --- |
| **M0 工程/来源** | 全新 Java 8 工程；审计环境与技术依赖；登记四视频可访问位置 | Maven 项目可编译/自检；输入清单**实际指向 4 个原件**或明确缺失；报告版本/许可缺口 |
| **M1 原视频与连续帧** | 核验四录像哈希、解码、帧 PTS/time_base/RGB；确认真实会话和画面覆盖；独立预选片段 | 四媒体账本、逐帧索引、原帧复现、场景/转换覆盖矩阵与缺口；发现的解码失败不得隐去 |
| **M2 独立 GT/回放基础** | 当前代理连续完成**全部场景所选完整事件的全部评分原帧**双遍核读和结构检查；覆盖矩阵收口后一次性提交，获批后冻结 GT；分组隔离 | 全场景处理结论及缺口、统一版本批准证据、冻结版本、全帧/时间检查和时序回放/比较接口；部分场景完成不设卡点，未批准或缺证据不得冻结或进入 M3 |
| **M3 日文 OCR** | 日版字库与渲染规则审计；字格 OCR、阴影/浊点处理、状态机、诊断 | development 连续片段逐帧可运行；记录原始 OCR/日文辅助修正差异与失败族 |
| **M4 OCR 验收** | 未用于调参的独立会话范围可用、GT 已冻结且审计通过 | 原始字符 ≥99.5%、整页 ≥95%；最终字符/整页/位置 100%、空画面误报 0；独立会话缺失则**待验证** |
| **M5 生产中文数据** | **M4 通过之后**审计四版本日文消息、显示控制、变量角色与中文候选 | 唯一完整消息、可确定固定页面的核对中日映射覆盖**分别 ≥95%**，分母与未知项有依据 |
| **M6 翻译/UI** | 日文可见状态驱动匹配、变量/历史消歧与中文独立窗口 | 离线翻译组件、实际 UI、换页/清屏/换源旧译文失效；不提前显示后文 |
| **M7 最终验收** | 冻结独立中文 GT；四视频原速回放、真实投屏/遮挡和 UI 呈现观测 | 中文/整链正确率 100%、显示与消失 ≤200ms；原速回放及真实投屏**各 ≥2h**；所有未覆盖维度明示 |

**前置限制**：可以先开发不依赖 M4 的确定性辅助工具，但不能建设**生产中文映射**或宣布未通过的阶段完成。M7 的四版本/场景/缩放/真实后台采集必须各有证据；旧录像未覆盖的项目只能待验证，**不得要求补录作为完成当前文档计划的前置**。

## Concrete Steps

### M0（第一个实际动作）

1. 获得开发授权后，在当前项目创建最小 Maven/Java 8/JavaFX 8 工程，保留现有文档与素材；固定 JDK 发行版/版本、JavaFX 来源、Maven 版本及依赖许可，将 `data/raw/` 排除在版本控制之外。
2. 为上述 **4 个现有原始视频**建立 `config/input-videos.json` 路径登记；只读复核文件是否仍可访问，缺失则记录，不创建假文件。
3. **工程建立后**执行 Maven 测试，保存执行结果、JDK/Maven 版本、提交与报告。本轮已执行带固定 settings/项目缓存的 Maven test 与 package，实际命令及证据见下文；不据此宣称实机验收。
4. 在 M0 做基础可行性检查：登记目标投屏软件/版本、Windows 版本、CPU/GPU、DPI、窗口尺寸、缩放范围、源更新率与计划采集频率；比较候选窗口采集方式在前台、非前台、遮挡、最小化时的真实表现。验证 UI 实际呈现观测方法、观测分辨率及其与源时间的对齐，不能用提交日志代替。该检查不建设 OCR 或中文映射，也不替代 M7；设备或测量条件缺失时该子项待验证，继续无依赖工作。
5. 进入 M1，先查实输入，再处理 PTS/帧索引；**不要跳到 OCR 或中文**。

### M1–M2（先建设可靠基准）

1. 对四视频单独计算 SHA-256，用 FFprobe 保存流信息与逐帧**整数 PTS、time_base、duration**，用 FFmpeg 从头连续解码每个原视频并按显示顺序建立完整 RGB 帧索引。固定工具版本、解码器、像素格式和色彩/方向处理参数，保存实际命令；遵守 `spec.md §3.2`，不以播放器截图、抽帧、仅关键帧、去重或改帧率代替。关联帧编号、PTS 与解码像素，登记全部异常；缺失时间信息不静默猜测。`PTS × time_base` 才是来源时间。
2. 按 `spec.md §3.1` 的完整事件规则建立实际场景/转换覆盖矩阵：先浏览上下文，以像素/布局变化找候选，再逐帧核查边界附近，向两侧扩展至事件前状态和结束后状态。不能仅凭静止时长判断完成。登记事件类型、起止理由、关键帧、完整性状态、热身/评分/结束后上下文；截断或不确定候选保留待验证，不计完整事件通过。只对选段独立全文核读，不要求全视频全文标注。
3. 核实四文件是否属于相互独立的会话。优先以会话划分 development/regression/acceptance；若不能隔离，则记录“不具备独立验收集”，仍允许开发与回归。
4. 当前代理可先以开发集一个完整事件内部试标并验证格式，再连续处理全部场景；不再等待单个试标/片段/场景批准。采用连续可见状态区间覆盖每一原帧；第一遍原帧盲读，第二遍重提原帧并用**独立像素/布局扫描**排查遗漏，逐项处理一帧显示、浊点、数值、文字/时间边界和空帧。完成一段即保存候选/差异和结构证据，继续下一未完成项。**不能使用主 OCR/翻译输出作为预期**，不引入其他模型，不将代理复核称为人工核验。
5. 按下表逐项收口全部场景和显示变化：可核验所选范围全部保帧/双遍/差异解决/结构检查；确实缺失、截断或经核查仍不能核验的范围保留依据、尝试与影响，标“待验证”，不伪造为通过。不以困难或尚未处理为理由跳过已有可核验项。**全部场景收口后**才按 `spec.md §3.4` 生成一个统一审阅版本，在本计划登记覆盖矩阵/范围/哈希和“等待用户审阅”，提交并暂停后续工作；中间材料和进度不触发卡点。记录用户对统一具体版本的批准依据，再复核评分范围内**100% 原帧/时间/可见状态**，拒绝 `unknown/unreviewed/conflict`、空集、错误源哈希、缺帧、时间洞与泄漏，之后才冻结新增范围。冻结文件不可就地改写；保存依据、版本与 SHA-256。已批准35帧保持原字节，可在统一包引用原身份，不重复假定或申请其已生效批准。
6. 单 clip 与原会话顺序按原始 PTS 回放。测试驱动读取 GT 比较，**程序只看到正常图像/时间**；漏识别、迟到、提前译文和异常均保存前后原帧。失败素材不得从已经冻结的分母移除。
7. 按 `spec.md §4` 冻结评分结构、字符表示、坐标映射、区域配对和时间对齐，并记录代表性评分例子。评分器开发不得根据被测 OCR 成绩选择计分口径；文档例子不作实机验收证据。

### 全场景 GT 统一收口清单（2026-10-15原工作清单，当前批准/冻结/修订状态以末尾为准）

本表是唯一计划中的覆盖工作清单，不另建进度账本。候选包围范围不是已确认完整事件；后续执行须依据原图核边界、必要时扩展，保存前/全部过程/结束后每一原帧。完整场景按显示内容与转换代表性选择足量事件，不以固定帧数或片段配额收口，也不要求全文标注四个整视频。

| 场景/维度 | 现有范围/证据 | 当前结论与收口尚需工作 |
| --- | --- | --- |
| 主文本框 | 已批准video-02评分[5264,5299)，35帧/14状态/1页；首次冻结封套见下文 | 原批准/字节保持；可证明一页加载/滚动，不能代替其他文本框变化和未选时段 |
| 探索/对话 | video-01原[6000,6660)经原图核查是开场介绍，保留未评分；另按原图导航登记[9600,10200)600帧上下文，NPC对话评分[9838,9926)88帧/17状态/2页已双遍及结构检查 | 覆盖开窗、两页逐字加载、完整页、间隔清屏和最终清屏；2倍栅格，无OCR选片，未选对话未验证 |
| 战斗提示 | video-02[6480,7240)760上下文内结果评分[6916,6964)48帧/19状态/2页已双遍及检查；video-04技能完整回合104帧 | 倒下/经验976、己方攻击/敌方攻击/落空及消息失效已覆盖；结果事件不声称此前KO动画或整个战斗通过 |
| 技能选择/PP | video-04上下文500原帧；评分[17462,17566)104帧/36状态/7完整页面机会双遍及结构检查完成 | 已含技能光标、9/10→14/15及属性变化、完整一回合、整幅晃动偏移、消息加载/清屏；候选未批准/冻结，其他必需变化继续覆盖 |
| 背包 | video-04开启评分[5174,5208)34帧/9状态/4页；导航[5206,5358)152帧/47状态/16页，全部双遍及检查 | 开启/光标/滚动/单帧页/重复行/双光标/孤立浊点已处理；两重叠帧和同一初始页面去重。5359退出被精灵/背景遮住基础字形，首遍、独立二遍及再次原图核查仍不能可靠确定，整个退出事件保持待验证，原5358–5362附包；未以历史补字 |
| 宝可梦状态菜单 | video-02评分[462,564)102帧及video-03[3534,3663)129帧各5状态/2页；队伍[3661,3687)26帧/5状态/1页，全部双遍及检查 | 两种显示几何18空态/单帧新旧混排及六行名字/等级/HP/光标/部分清屏已处理；3661–3662空帧重叠去重。未选队伍操作不宣称通过 |
| PC | video-02连接[1379,1426)47帧/10状态/3页；层级/列表[1634,1754)120帧/32状态/11页；关闭[2248,2258)10帧/2状态/1页，全部双遍及检查 | 提示/光标/层级/列表进入/遮挡前缀/孤立标记/滚动重复行/退出及清除文字均已处理；选定操作没有实际转移道具，未选宝可梦盒操作/转移效果不作验收结论 |
| 商店 | video-03买入导航[820,864)44帧/5状态/3页；卖出进入[1044,1102)58帧/15状态/4页；拒售[1408,1440)32帧/15状态/2页；原退出[1439,1486)47帧/19状态/3页，全部双遍及检查 | 商品价格/金额22526円/短暂金额消失/层叠菜单/滚动/拒售否定/双向下箭头/清屏及退出已处理；1439重叠去重。选定场景没有成功交易，不伪造余额变化、数量确认或未选操作通过 |
| 非目标/黑帧/无效帧 | video-01评分[90,390)，300原帧/8状态双遍空文字GT及结构检查完成；其中55帧精确RGB全黑；其他场景空态/失败随原帧保留 | 明确non_target_only支持0字符/0完整页，仍拒绝未知/缺帧/错来源；没有虚构目标网格或完整页。候选未批准/冻结，其他失效类型继续核查，实际不存在的类型附来源记缺口 |
| 跨场景显示变化/变量 | spec §1清单及上述全部候选，已有单帧短页、浊点裁切与双轴非整数几何证据 | 逐项核对加载、短页、翻页、滚动、清屏、光标、菜单切换、阴影/清浊点/缩放、名字/数值/HP/PP/金额等实际覆盖；未知/缺失附依据，不能凭工具测试补齐 |
| 版本/会话及未选范围 | 四原件、完整账本、来源覆盖证据；独立会话和其余版本证据未证实 | 在统一包保留身份、未选区间与可证/不可证依据；待验证不等于独立验收通过，不要求补录。真实窗口/UI/长时稳定性仍属后续阶段 |

**统一提交条件**：上表每项都有可追溯处理结论；已有可可靠核验范围不得留在“尚未处理”，拟冻结范围无未知/冲突/缺帧。经核查仍不能核验的范围保存尝试、原图与影响并列待验证，不纳入拟冻结范围；审阅包必须同时呈现这些缺口。当前尚不满足，不能仅凭商店/PC候选完成再次触发卡点。统一包使用新审阅版本/目录和总清单，引用既有材料哈希，按原帧/页面机会去重；不覆盖旧包，不把新流程调整记为任何GT批准。

### M1–M2 帧保存与选片检查（索引/首次试标已实施，扩充继续）

- 四视频全部建立逐帧索引，不要求把四视频永久全部展开为 PNG。选定 clip（含热身、评分和结束后上下文）将每一原帧导出到 `data/frames/<media-id>/<clip-id>/` 的无损 PNG；原媒体只读保留。PNG 文件名使用源帧编号，时间读取索引，不能从文件排序或文件修改时间推断。
- 重新提取片段时使用相同解码配置；可从源开头连续解码，若优化为从更早关键帧开始，则必须解码至目标边界，并逐帧证明与完整索引的编号、PTS、尺寸、RGB 像素哈希一致，不能把最近关键帧当作指定起点。对 PNG 重新读取后比较 RGB 像素哈希，不将 PNG 文件哈希与 RGB 哈希混用。
- 检查导出帧数等于源半开区间长度，每帧映射无遗漏/重复；相同像素但不同源帧仍分别保留。失败时保存差异，不覆盖原媒体或旧证据；修复后重跑受影响片段与同类检查。
- 审阅页展示完整原帧、目标区域裁剪和最近邻放大，辅助图记录源帧与裁剪坐标，不能用插值增强后的图替代原始证据。按 `spec.md §3.4` 附选片边界依据、前后帧、截断候选及一致性报告，再进入已有人工卡点。
- 离线解码与运行时窗口采集分别验证；FFmpeg 离线成功不代表非前台/遮挡捕获通过，实时方案仍由 M0 实测确定。

### M3–M7（在可靠基准上开发）

- **M3**：日版来源字库 → 原始字格 OCR → 视觉处理（阴影/浊点/缩放）→ 当前可见状态；先在 development 上迭代并保存 regression。
- **M4**：独立会话验收（条件足够才判定）→ 原始字符/整页 → 最终整页/关键字段/空画面 → 时间和错误分析；将调参过的验收材料降为 regression。
- **M5**：M4 通过后，先冻结四版本来源提交、目标文件/消息范围、去重与排除规则，再建立消息与固定页面的分母；逐版本/场景报告目标量、审计量、未知量和映射覆盖。核对中文来源、分页及变量角色；未核对中文不计覆盖，不得事后缩小范围提高成绩。
- **M6**：精确/受约束模板匹配、场景/历史消歧、实时翻译及 JavaFX 独立窗口；未知时显示原文，失效时删除旧结果。
- **M7**：M4通过后的独立中文GT工作按全部应译场景完成双遍和覆盖收口，再单独一次性提交用户审阅；获批冻结后进行原时间轴验证、实际 UI 呈现/消失观测、真实投屏后台/遮挡、两种 2 小时稳定性；不为日文GT统一提交提前开展中文工作。

### 已实现的 M0/M1 CLI（2026-10-14）

```powershell
mvn -s config/maven-settings.xml -Dmaven.repo.local=.cache/m2 test
java -cp app/target/classes io.github.g1ocr.Main --self-check
./tools/validate.ps1 index config/input-videos.json data/meta/m1-v1
./tools/validate.ps1 export data/meta/m1-v1 video-01 0 30 data/frames/example-new-dir 1
./tools/validate.ps1 sheet data/frames/example-new-dir reports/example-new-sheet.png 5 320
./tools/validate.ps1 inspect data/frames/video-02/trial-pass2-v1 reports/example-new-inspection 300 324 360 108
./tools/validate.ps1 gt-check data/meta/m1-v1 data/meta/clip-trial-v2.json data/gt/candidates/ocr-trial-v1/ocr-candidate-v2.jsonl data/gt/candidates/ocr-trial-v1/layout-v2.json reports/example-new-integrity.json
```

首次构建需从中央仓库下载已固定依赖；缓存位于项目 `.cache/m2`。已缓存时加 `-o` 离线执行。`export` 的末参数 `1` 表示保留区间全部原帧；大于 1 只能生成导航辅助，报告明确标为不能评分。输出目录/图片必须不存在，不覆盖旧证据。`index` 固定 H264 软件单线程、BT709 limited → RGB24 full、无旋转/缩放尺寸/帧率重采样，RGB SHA-256 来自完整连续 `framehash`，与 FFprobe 全帧整数 PTS、duration、尺寸逐项配对；异常不补齐。`export` 从源开头重新解码，核对每帧 RGB 和 PNG 读回像素哈希。

CLI 成功退出 0 仅表示对应工具检查完成；输入/证据不足为 3，工具错误为 4。此处为M0/M1历史接口；2026-10-16新增批准冻结、传帧回放与内容比较的实际接口见本节后及末尾，不据此宣称OCR通过。

`inspect` 必须输入 step=1 的连续导出，重新校验每一原帧 RGB，按顺序展示全部帧及裁剪；像素差阈值仅导航，不自动给文字/状态答案。`gt-check` 核对原件哈希、账本身份、非空半开区间、每个评分帧恰一状态、时间、UTF-8/NFC、字格/源矩形和完整页机会；不会授予批准或冻结，也不证明图像语义。审阅入口生成器 `tools/build-gt-review.ps1` 的实际调用记录在下文，产物含完整 JSONL 和原帧，不是另一份进度账本。

### M2 以后历史计划 CLI（**下列Python接口仍未实现，实际采用Java/Node接口**）

```powershell
mvn test
python tools/validate.py clips --manifest data/meta/clips.json --check
python tools/validate.py gt-freeze --kind ocr --manifest data/meta/clips.json
python tools/validate.py replay --set development --stage ocr --realtime
python tools/validate.py compare --set acceptance --stage ocr
python tools/validate.py compare --set acceptance --stage translation
```

`python tools/validate.py` 只是 M2 以后的**预期接口示意**，将继续采用 Java 实现并更新本节，当前不可执行。产品本身仍使用 Java。缺四视频、未冻结 GT、未实现功能、无法独立评分等均不得返回“成功/通过”。离线逐帧比对**不能**替代真实 UI 显示测量。

**2026-10-16已实现接口**：`node tools/freeze-reviewed-gt.cjs <approval.json> <review-dir> <new-frozen-dir> <new-check-dir>`（先复核批准字节及结构；现增加跨片字段/顺序/状态检查）；`./tools/validate.ps1 replay-audit <frozen-dir> <item-id|all> <new-report-dir> <unpaced|realtime>`（只传RGB/尺寸/原PTS，当前sink不运行OCR）；`./tools/validate.ps1 compare-ocr <frozen-dir> <item-id|all> <actual-dir> <new-report-dir>`（读取`<item-id>.jsonl`，独立内容评分；没有独立UI观测时页面验收/时延保持待验证）。正式提交修订后，批准前不能调用它们继续研发或运行被测OCR。

**2026-10-16恢复后新增接口**：`node tools/compose-approved-gt.cjs <base-frozen-dir> <amendment-frozen-dir> <replacement-id> <new-collection-dir>`组合既有批准封套并检查原范围不缩减；`node tools/check-effective-gt.cjs <approved-collection-dir> <new-report-dir>`对有效38项重新逐片校验；上述replay/compare已支持有效集合。`./tools/validate.ps1 font-audit resources/japanese-font-sources-v2.json <new-report-dir> <new-.cache-bank-dir>`只审计固定来源图块/编码，不读GT/录像，不运行外部仓库脚本。`./tools/validate.ps1 viewport-check <approved-collection-dir> <item-id|all> <new-report-dir>`现已实现：逐原PTS提供RGB，关闭actual文件后才独立对照冻结网格，保存全部原帧引用/实际参数/逐帧差异；几何失败保存报告并退出3，不作OCR验收。完整OCR CLI仍未实现。

## Validation and Acceptance

以 `spec.md §4` 为**唯一数值验收与评分定义**，阶段表仅作定位，不另设标准。不可提前放宽或只看平均值。

每次报告至少含**四个原视频身份、选段/评分范围、帧/页面机会分母、真实会话隔离、GT 冻结哈希、构建/数据版本、实际输出与失败数、未选范围/版本/场景缺口**。真实窗口与实际 UI 延迟单独报告。无独立验收会话或核验能力时记录“待验证”，不删除原有失败机会也不补录新视频。

### 本轮实际输入与证据（2026-10-14）

| 媒体 | SHA-256 | 原帧数 / 原 time_base / 总时长 |
| --- | --- | --- |
| video-01 | `f53c237ad19a3367a4d04c4cbd890e07a7faf3038a9a91e776765e3769cbc572` | 29,346 / 1/30 / 978.200000 s |
| video-02 | `c83de914352e64d207c9ea37318f4503855d6ca7f3b31af6215a0d9b451cf207` | 7,964 / 1/12288 / 331.833333 s |
| video-03 | `9c8aee036f721a3da4d79255589f00f142148a0b2398972c924194ffbd3635b5` | 7,402 / 1/12288 / 308.416667 s |
| video-04 | `4a2fec5930e97d8ad8db4ed3343dac3fce7ecaadde2f9211519bddc2ece9ec30` | 22,058 / 1/19456 / 1160.947368 s |

合计 **66,770 原帧、2779.397368 秒**。实际命令 `./tools/validate.ps1 index config/input-videos.json data/meta/m1-v1`，工具配置 `config/video-tools.json`，每个 FFprobe/FFmpeg 完整参数另存在各媒体 `*.command.json`；版本和二进制哈希在 `decoder.json` 与 `*-version.txt`。完整 RGB 帧哈希及整数 PTS/duration 联合账本 `data/meta/m1-v1/frames.jsonl` SHA-256 `70e8af7485369a91531d13543d6e6dd4675d8edae3a3b5399a47f1b37c2ea75e`；媒体账本 SHA-256 `2458c4b931241d3fd6cea9617873e97f48b6ce94114c3db5b95eb4eb1b2d2798`。解码配对/时间异常帧 0；video-01 有 UDTA 容器元数据解析后重试警告，原始 stderr 保留，不宣称没有任何警告。

独立的 PowerShell JSONL 重读验证四媒体编号及整数时间连续、异常数组为空，报告 `reports/m0-20261014/independent-ledger-check.json`；不依赖 OCR、不是独立会话验收。全量索引不等于全视频文本已核验。

四媒体导航原帧均重新连续解码并逐帧核对 RGB/PNG 读回哈希，位置为 `data/frames/video-*/navigation*-v1/`；末参数步长 >1 仅用于导航。覆盖证据/未选范围/会话依据在 `data/meta/coverage-observations-v1.json`。导航可见必需场景，但只有下述一个页面形成候选 GT；未选时段、其他版本/场景、短于 200 ms 的完整页、影子与其他缩放、真实窗口全部待验证。video-01 的 HOME 图标显示日版红；另外三媒体没有确定版本证据。独立游戏会话尚不能证明；trial 仅为 development，regression/acceptance 均无冻结数据，不要求新录像。

### M2 首次试标审阅批次：gt-trial-v2（已批准，已冻结）

- 审阅入口：`reports/gt-review/gt-trial-v2/index.html`；候选版本 **trial-ocr-v2**；完整材料哈希清单 `bundle-manifest.json`。
- 包清单 SHA-256：`7a1699a7c2cc298ed37f81958f9c6057f880bb4ad239eda3f6fffa33754c5bb4`；入口 SHA-256：`2c2d5d9116baa48bc959d274a401250b61d802c81a04eb1be0dcf4ff92ca5126`；95 个包文件哈希全相符（`reports/m2-review-qa-v1/report.json`）。原片段 39 帧、39 原图和39 最近邻放大图全部可定位。浏览器 file 协议被安全策略拒绝，未绕过；仅入口 JS 语法与图片路径静态检查通过，实际浏览器显示/交互仍待验证。
- 拟冻结候选 GT：`data/gt/candidates/ocr-trial-v1/ocr-candidate-v2.jsonl`，SHA-256 `30d240232e03eaca07eeebe9502a56689e41bf223faa59c665c4e638a154de25`。clip `data/meta/clip-trial-v2.json` SHA-256 `a44fa579bf3699bca4b8edd654c2e1300507cd15002cc74bc34e7f8e314bd912`；坐标 `layout-v2.json` SHA-256 `f004f9fdb59681ef99f116593ad5a82b716380125487b7cf66b639ddfb00adf0`。
- 来源 video-02；clip **[5262,5301)**、热身 [5262,5264)、评分 **[5264,5299)**、结束后上下文 [5299,5301)。评分 PTS [2695168,2713088)、time_base=1/12288；原 clip 1.625 s、评分 35 帧约 1.458333 s、14 状态、**1 个完整页机会**。逐帧预期字符分母 488，状态等权预期字符分母 194；11 个空文字帧仍登记，不从统计移除。
- 起点之前可见无框场景；5268 出框、5275 首次字形、5286 首次完整两行与箭头；完整页 [5286,5296) 共10帧约416.667 ms；5296–5297 完整保留滚动、5298 滚动完成旧上行消失。5300 的下一页加载仅作为后文，不声称评分第二事件。候选上下文 [5100,5400) 的300原帧全部保留，5320 清屏未作为这批页面验收范围；不存在按 OCR 成绩选片（OCR 尚无实现）。
- 第一遍：盲读原帧及完整有序文本区域；记录 `pass1.jsonl`，哈希 `43938f47ec2fd1211108225586aa8f8171a9df3a0df1f594a45b27fc652a74a9`。第二遍：从原媒体重新连续解码39帧，再看每帧文本区域/完整游戏区域、相邻帧及难字3倍最近邻放大；记录 `pass2.jsonl`，哈希 `383310c6d863252d5ff036dc8397c44e9b78342e936eeab38219cc21c6f2a3a2`。两遍均未读取被测 OCR、翻译或候选答案；当前编码代理核读，不声称人工逐帧核验。
- 双遍差异：1 个状态/2 个原帧。第一遍错误地将滚动帧5296–5297读为带标记「ポ」「ジ」；第二遍原图确认标记已被文本框裁掉，实际基础字形为「ホ」「シ」，候选按当帧保留。原版本、差异、处理依据和放大图都保留；不是 OCR 驱动修订。14 状态边界无差异，评分文字/位置/时间未解决项 0；来源版本和会话独立性仍在外部缺口中单列。
- 结构检查实际命令：`./tools/validate.ps1 gt-check data/meta/m1-v1 data/meta/clip-trial-v2.json data/gt/candidates/ocr-trial-v1/ocr-candidate-v2.jsonl data/gt/candidates/ocr-trial-v1/layout-v2.json reports/m2-trial-candidate-integrity-v2.json`，35原帧恰一状态、时间及坐标一致；报告不授予人工批准。三份故意损坏的副本（未知状态、1帧缺口、错误源哈希）均退出3、没有伪成功报告，最小复现及日志保留 `reports/m2-validator-negative-v1/`，原候选不变。
- 审阅生成器实际调用：`./tools/build-gt-review.ps1 -ReviewDirectory reports/gt-review/gt-trial-v2 -Clip data/meta/clip-trial-v2.json -Candidate data/gt/candidates/ocr-trial-v1/ocr-candidate-v2.jsonl -Layout data/gt/candidates/ocr-trial-v1/layout-v2.json -Frames data/frames/video-02/trial-pass2-v1 -Integrity reports/m2-trial-candidate-integrity-v2.json`。包中包含完整材料与再提取命令；HTML上下文标签的措辞修正前版本另存 `reports/m2-review-qa-v1/index-before-context-label-fix.html`，候选 GT/范围没有变化。
- **批准依据（2026-10-14）：** 用户在前一条明确点名 gt-trial-v2 / video-02评分[5264,5299) 的审阅请求后回复原话：“批准。你是需要我审核完一个gt再接着采集下一个gt吗？” 此为当前这批材料的批准，不作为后续批次批准，也不声称用户逐帧核验。复检与冻结身份见下面记录。

**M2 评分契约候选** `data/meta/ocr-scoring-contract-v1.json`（审阅包内同字节副本），SHA-256 `2930e6b370616fe29402d724e890ee4314ad9f714f9b8cae377484137d934c6c`：UTF-8/NFC 预组合假名，按 Unicode 标量计字符；一个可见空字格为 U+0020，标点「！」为 U+FF01，不删除空格/大小假名/浊点。区域按角色、基字格起始行列及阅读顺序配对，拒绝重复键，不按识别文字找最佳区域；缺输出删除、多输出插入，最小编辑距离。原帧累计与状态内分别除帧数后的等权口径都报告；空帧误报独立报告。坐标半开矩形/网格与当前可见标记冻结在 layout/GT，不从 OCR 反定。滚动隐藏标记不借历史补出；时延需要独立实际 UI 观测，没有观测写待验证。契约含小假名/半浊点/数字/空预期4个说明例子，非实机验收。此时无评分器或实际输出；批准后的冻结必须保存契约哈希，先于 M3。


### 首次试标获批复检与冻结（2026-10-14 22:39，Asia/Shanghai）

- 重新核对审阅包清单 SHA-256 `7a1699a7c2cc298ed37f81958f9c6057f880bb4ad239eda3f6fffa33754c5bb4` 和95个文件，GT/clip/layout/契约/完整账本哈希均与提交一致；四原件重新计算SHA均未变化。报告 `reports/gt-approval-trial-v2-identity.json` SHA-256 `bfb416a4099e480b468df126b2d36baecc96b14ae1118b7b141518b899e670a0`。
- 按上次唯一下一步实际执行 `./tools/validate.ps1 gt-check data/meta/m1-v1 data/meta/clip-trial-v2.json data/gt/candidates/ocr-trial-v1/ocr-candidate-v2.jsonl data/gt/candidates/ocr-trial-v1/layout-v2.json reports/gt-approval-trial-v2-integrity.json`；35帧、14状态、1完整页面，未知/缺口0，GT SHA仍为 `30d240232e03eaca07eeebe9502a56689e41bf223faa59c665c4e638a154de25`。复检报告SHA `627f7d810a2cd070e62b5ae156c41212b900f89fe306bda32cfa355d17fc8bbd`；退出0只证明结构检查。
- 冻结目录 `data/gt/frozen/ocr-trial-v2/` 首次创建，逐一 Copy-Item 原GT、clip、layout和评分契约并核对源/副本字节哈希相同，不覆盖候选/审阅包。保留各原文件名及原candidate字段以对应批准字节，冻结权威由不可就地修订的 `freeze.json` 封套记录，不能仅凭payload中的旧候选字段否认/推断冻结状态。
- `freeze.json` SHA-256 **`6fec19a0b95ed402e1b35f79eed837d802009ad874501e85adf2c9fbb965cd54`**。GT SHA `30d240232e03eaca07eeebe9502a56689e41bf223faa59c665c4e638a154de25`、clip SHA `a44fa579bf3699bca4b8edd654c2e1300507cd15002cc74bc34e7f8e314bd912`、layout SHA `f004f9fdb59681ef99f116593ad5a82b716380125487b7cf66b639ddfb00adf0`、契约SHA `2930e6b370616fe29402d724e890ee4314ad9f714f9b8cae377484137d934c6c` 均保持批准版本。
- 冻结评分仍是 video-02 [5264,5299)，35帧/14状态/1完整页、逐帧字符分母488、状态等权字符分母194、development；未添加其他机会或把同一会话拆片当独立验收。没有运行OCR/翻译；M2整体尚未完成，M4会话独立性仍待验证。
- 后续工作按 spec §3.4 合批：可以连续选取、保帧并双遍核读多个场景，不按“每个页面/GT都问一次”执行；下一批扩充材料准备完成后统一提交并暂停。用户本次批准没有预先批准下一批，也不改变卡点。

### 下一批扩充预选与连续保帧（2026-10-14 22:48，尚未形成GT）

- 选样清单 `data/meta/expansion-candidates-v1.json` SHA-256 `256b4f4da68155a1233357ad8dff1620d27daa94e8fab91b1c41492ce00a53ce`：依据之前核查的四录像导航画面登记9个连续上下文候选，分别涉及非目标/黑屏、对话、状态/另一种显示布局、PC、商店/道具列表、背包、技能选择/PP和战斗提示，合计5690候选上下文帧。它们不是9批，也不是已经确认边界完整的9个事件；scoredRange均未建立，扩充评分/冻结帧数0。没有看OCR成绩或输出。
- 这些上下文都是事前导航包围范围，实际完整事件/热身/评分/后文边界须逐帧核查并必要时向外扩展；不能把候选端点当完整事件端点。保留旧候选、未知边界及缺口，不将候选文件计作GT。独立会话/版本仍待验证，未分配独立acceptance集。
- 已实际执行 `./tools/validate.ps1 export data/meta/m1-v1 video-03 800 1540 data/frames/video-03/expansion-context-f800-f1540-v1 1`，从原件起点连续重解码，**740原帧**逐帧RGB哈希及PNG读回均与完整账本一致，未删除重复、加载、空白或失败帧。全命令/原时间及帧哈希在导出目录 `decode.command.json` / `verification.json`，后者SHA-256 `b29266697fbd317e0b82c67b17c9ba87e66b0a130ca4d81693766c0316f1b7c5`。启动/完成日志 `reports/expansion-context-video03-shop-v1.log`，退出0仅为原帧复现检查。
- 尚未核读或双遍完成任何扩充GT，未提交下一审阅批次，当前无需用户再批准；完成多个片段的材料后合批提交，再触发§3.4停止点。
### 扩充保帧、商店第一遍与非整数几何检查（2026-10-14，未提交审阅）

- 完成商店740原帧 inspect：`reports/m2-expansion-shop-fullframe-inspection-v1/report.json`，37张按序全帧拼图及逐帧像素扫描；报告SHA `03d93809a9d32558f904bb7579d97af2ddc2bccbbff7e9a93d5629a0d8bc7a47`，扫描SHA `2fbe8fdeb4da7f84ab2d28ba81ede1cf5300511442641629e6c6b023d8f305c1`。只是逐帧身份/展示材料，不宣称740帧全部核读。
- 继续从原件开头连续导出并逐帧核对：video-02状态[360,680)320帧、PC[1320,2520)1200帧；video-01启动[90,390)300帧；video-04背包[5100,5650)550帧、技能[17150,17650)500帧。分别保存 `data/frames/video-*/expansion-context-f*-f*-v1/` 和 `reports/expansion-context-video*-*-v1.log`。这些候选无已批准GT，未运行OCR。
- 商店提出退出子事件[1439,1488)，评分[1439,1486)47帧，第一遍19状态保留提示加载、光标移动、完整提示只停1帧、感谢语加载与清屏；证据 `reports/m2-expansion-shop-exit-firstlook-v1/` / cursor-firstlook / grid-measurement；第一遍 `data/gt/candidates/ocr-expansion-v1/shop-exit/{manual-pass1.json,pass1.jsonl,layout-pass1.json}`，GT SHA `466fe2d57b33d0ce3bc2f8c0d8b80d6181b067a4e52c8b7c128aa37edb58a261`。第一遍不是双遍/冻结，前段遮挡列表仍待验证。
- 原图可见源03游戏区域[180,0,780,544]对应160×144，X/Y缩放分别15/4、34/9。新增离线 `GridProjection` 支持双轴有理数；首次floor结束边界会漏一行实际像素（原帧测量/最小复现 `reports/m2-expansion-build-v1/rational-end-boundary-repro.json`）。修复 **1/3**：起点floor、终点ceil，完整视口/光标/文字矩形及旧等比例布局共5项检查通过；全工程14测试通过 `maven-test-ceil-end.log`。旧第一遍layout保留，下一版明确修正，不篡改记录。
- 已冻结35帧GT在新检查器回归通过 `reports/m2-expansion-build-v1/approved-trial-regression-ceil-end.json`，批准字节不变。构建/结构检查均不是实机OCR验收。首次source-snapshot为修复前历史快照，后续须生成新快照。
- 第二遍独立原件重导出 shop [1439,1488)49帧 `data/frames/video-03/expansion-shop-exit-pass2-v1/` 已完成并逐帧RGB/PNG一致；尚未完成第二遍核读/差异解决，当前无下一审阅卡点。

### 第二批合批提交历史：商店退出与PC连接（2026-10-15 00:35左右；待审请求现已撤回）

**当前效力（2026-10-15用户调整后）**：以下保留当时提交、停止和证据记录；仅两场景的待审请求已由用户本轮流程要求撤回，不再是恢复工作的批准前置。包/GT/哈希全部保留原字节，未获批准也未冻结；其中“批准口令/停止点”是历史说明。已完成候选将在未来全场景统一版本中引用或按原图另版修订，不能拿旧包或流程调整代替新统一版本批准。

- **审阅入口** `reports/gt-review/gt-expansion-v1-r2/index.html`；审阅版本/批准口令 **gt-expansion-v1**，r2表示材料构建修复版，不改变候选GT语义或冒充第二次人工批准。批次清单 `data/meta/gt-expansion-review-v1.json` / 审阅包 `batch.json`。**240文件**清单 `bundle-manifest.json` SHA-256 **`57ff7f12e05afeff82b218dd7e1d88d664f7dcf15b3e9ad3904e444e533a6f66`**；包与注释未冻结、无该批用户批准。
- **商店** video-03完整[1439,1488)，评分[1439,1486)，后文[1486,1488)：47评分原帧/19状态/3完整机会，1868逐帧字符分母、715状态等权分母；完整旧光标提示[1450,1451)仅1帧/41.667ms，完整新光标提示[1451,1462)、感谢[1468,1482)。加载/空框/清屏/金額22526円/光标均保留。第二遍从独立源解码49帧逐帧直接看游戏区域并回看完整原图1450，两个解码49张PNG字节相同。注释 `shop-exit/ocr-candidate-v2.jsonl` SHA **`cb90c6276d9905227add176e828943ffd5874c5c8d5e15f14e9cfafe73f4db7f`**；clip SHA `5760d76623aa7d698bc3acf77999445b7d2651b80b56e703707140b7dc00e0f3`，layout SHA `aee32f21b67e47541a03c43ad863a12b4c79b05e871d27f7ce682f681826c366`。两遍文本/边界/光标相同，sourceRect终点改ceil的原图依据明确保留 `pass-differences.json`，旧floor版不覆盖。没有认证买卖交易或早期遮挡列表。
- **PC** video-02完整[1379,1430)，评分[1379,1426)，后文[1426,1430)：47评分帧/10状态/3完整机会，2348逐帧字符分母、511状态等权分母。完整菜单光标row2→row4在1387，1404空框，1405–1410逐字加载，1411–1421完整「じぶんの パソコンと つないだ！」及下箭头，1422清屏；保留4评分清屏帧及4后文帧，1430开始的下一提示不声称覆盖。第一遍全原图1386/1387/1410/1411和逐帧拼图；第二遍独立解码51原帧全部游戏区域及完整1411直接看图，51张PNG两遍字节相同。注释 `pc-connect/ocr-candidate-v1.jsonl` SHA **`3b9da087f8807b7ad136942afd3e5d293d6f669ea828ecbe8a685669450df0eb`**；clip SHA `0a6005006657f212aa5da50c202f7ee30305a36b141c7df095fbd4f816aaf863`，layout SHA `0e36b60bf215eb24584920fab5f12a0f5a252de991c27423259330935311a8db`。各字段/坐标/上方标记/光标/时间/文字两遍一致；不认证PC存取交易、更早遮挡菜单或后续提示。
- **合计范围** 新增两clip含后文100原帧/4.166667秒，94候选评分帧/3.916667秒、29状态/6完整机会；4216逐帧字符分母、1226状态等权分母。全部development，尚无独立acceptance。只有前批35帧已批准冻结，扩充冻结仍0帧；四原件总66770帧/2779.397368秒，不以这两片段声称四视频通过。未选区间按四来源列在batch清单；来源版本/会话仍按原图证据待验证。其余7类上下文候选或未核清的子事件保留，背包/战斗/技能选择/探索/启动/状态页组合HP/No.字形等尚未完成GT，不要求新录像。
- **实际命令与检查** `./tools/validate.ps1 gt-check data/meta/m1-v1 data/meta/expansion-shop-clip-v2.json data/gt/candidates/ocr-expansion-v1/shop-exit/ocr-candidate-v2.jsonl data/gt/candidates/ocr-expansion-v1/shop-exit/layout-v2.json reports/m2-expansion-shop-integrity-v2.json`；PC对应 `expansion-pc-clip-v1.json`、`pc-connect/ocr-candidate-v1.jsonl` / `layout-v1.json` / `reports/m2-expansion-pc-integrity-v1.json`。两结构检查0未知/缺口、各3完整机会；不是图像语义/OCR实机验收。独立PNG复提比对及逐机会时长 `reports/m2-expansion-double-read-check-v1.json`。四原件与账本2026-10-15 00:24重算身份全相同 `reports/m2-expansion-source-identity-v1.json`；首次冻结封套/GT SHA仍原值。
- **工具验证** 最新Java14测试/已批准35帧回归见上一节；本次三负向原数据由直接Java CLI各拒绝exit3，未生成成功报告 `reports/m2-expansion-build-v1/negative-ceil-end/report-direct.json` / `report.json`。外层PowerShell曾返回1，原stderr仍是对应拒绝；保留外层日志，不能混写其退出码为3。手工注释编码器仅把人工给定字段转换格式，不读取图片/生成答案/批准。PC初次JSON字符串比较因mark对象属性顺序不同误报差异，按明确字段/数组逐项比较一致，未修改GT。
- **材料构建失败保留与修复1/3**：`reports/gt-review/gt-expansion-v1/` 和 `reports/gt-expansion-v1-build.log` 保留失败的部分材料，没有审阅入口，不提交该目录。根因System.Drawing.Bitmap的Int64尺寸选择句柄重载；最小复现 `reports/m2-expansion-review-builder-repro-v1.json`。修复用明确Int32尺寸后 `./tools/build-gt-batch-review.ps1 -Batch data/meta/gt-expansion-review-v1.json -ReviewDirectory reports/gt-review/gt-expansion-v1-r2` 完成两类尺寸全部100张原图/100放大图。
- **审阅材料检查** `node tools/check-gt-review.cjs reports/gt-review/gt-expansion-v1-r2 reports/gt-expansion-v1-r2-static-check.json` 完成：240文件SHA/尺寸、100原PNG身份、94评分帧恰有一个GT、嵌入JSON相同、JS编译、全原帧/关键帧/前后状态控件在DOM桩执行；shop1450和PC1411放大图实际直接查看，源裁剪/倍数随各帧登记。真实浏览器呈现仍待验证，DOM桩不冒充浏览器/人工审阅；没有绕过此前file协议安全拒绝。最终源清单 `reports/m2-expansion-build-v1/source-snapshot-delivery-v1.json` SHA `b0c36e0bcb77e8ab3076a4160222b33b60a7fc10b7f73aeb54e8d44aeaccb2e2`，Java构建身份对应ceil-end版代码；历史快照保留。
- **停止点**：本轮提交以上具体版本/范围，请用户审阅并明确批准 `gt-expansion-v1`；批准前不再做任何后续开发/GT冻结/OCR或其他独立任务。此次代理双遍不声称用户或其他人逐帧核验。获批后先重核包清单与版本、原件和结构，再冻结批准字节，继续剩余M1/M2；不能跳过剩余覆盖或直接转M3/M5。

### 全场景执行恢复与背包首读（2026-10-15，尚未完成双遍或提交）

- 按新规则和用户“继续执行”恢复。四原件SHA只读重算仍与完整账本一致；实际执行上次唯一下一步 `./tools/validate.ps1 inspect data/frames/video-04/expansion-context-f5100-f5650-v1 reports/m2-all-scenes-backpack-inspection-v1 0 0 960 544`，550原帧RGB/PNG身份核对通过，全部有序展示与逐帧扫描；报告及日志同前缀。导航图只用于定位，不作全文GT。
- 继续保留从原件开头连续导出的三个剩余上下文：video-01[6000,6660)660帧，video-03[3240,3900)660帧，video-02[6480,7240)760帧，均导出/读回核对通过；日志 `reports/all-scenes-context-video-01-dialogue-v1.log`、`video-03-status-v1.log`、`video-02-battle-v1.log`。至此9个事前上下文5690帧均已保留（不代表9个完整事件或全部GT完成）。
- 背包首读已逐帧查看5200–5379原帧拼图，直接完整原图包括5190/5204/5205/5235/5241/5250/5251/5260/5265/5301/5304/5305/5306/5314/5359。原图确认列表重绘中有重复行、光标临时消失/两个光标同时出现，不能用相邻稳定页补写或去掉过渡。首读笔记 `data/gt/candidates/ocr-all-scenes-v1/backpack-navigation/manual-pass1.json`，还不是完成GT/双遍；源04区域[150,0,750,544]，与源03原点不同，X/Y比例15/4、34/9。
- 完整事件候选5200–5365覆盖背包打开/列表往返滚动/关闭，需第二遍与格式核清：底层首标签基础字被遮住，仅剩上方浊点，不得恢复整词；5359关闭重绘时玩家精灵遮住「やめる」末字，保持待验证，不能凭前帧补全。独立重提[5200,5366)已启动，日志 `reports/all-scenes-backpack-pass2-v1.log`；未完成前不称第二遍通过。确定性符号像素计数 `reports/m2-all-scenes-backpack-symbol-scan-v1.json` 仅定位光标/箭头变化，须代理实际看原图确认。
- 接下来需在M2表达裸露上方标记、组合小标签/非整数网格占位和纯非目标GT，并独立验证旧GT回归；这些是离线GT工具工作，不是产品OCR。所有未知状态仍不允许冻结；本轮不把任何原图难点改成已验证。

### 全场景继续执行：格式、背包导航与启动双遍（2026-10-15，未提交统一审阅）

- 九个事前上下文已全部连续导出，合计5690原帧。此次补齐video-01对话660帧、video-02战斗760帧、video-03状态660帧，日志`reports/all-scenes-context-video-01-dialogue-v1.log`、`...video-02-battle-v1.log`、`...video-03-status-v1.log`。逐帧RGB/PNG与总账本相符不等于已全文核读。
- 背包第一遍原图与第二遍独立重提166帧[5200,5366)均直接查看；全部152评分帧还查看原像素光标/箭头条带。候选`data/gt/candidates/ocr-all-scenes-v1/backpack-navigation/ocr-candidate-v2.jsonl` SHA `34ef0bd2d6220db5c454155b574ec7541d8ca692a1807d2ed91e0c173721334c`，clip`data/meta/all-scenes-backpack-navigation-clip-v2.json`，layout-v1；结构报告`reports/m2-all-scenes-backpack-integrity-v2.json`。152帧/47状态/16完整页面机会/28完整状态区间，字符按帧4248、独立可见浊点按帧152。单帧机会5300/5303/5305/5346/5348、重复行、暂缺/双光标、箭头闪烁均保留。页面身份显式去重，不将闪烁乘成新页面。
- 背包范围仅为完整列表导航动作，不能称整个背包通过。首读暂定边界与二读确认的差异如实登记`pass-differences-v2.json`，不声称两份正式GT完全独立无差异。5359退出时`やめる`最后基础字形被角色遮挡，经原图/放大仍不能可靠核清，完整开启/退出候选及上下文保留待验证，不按历史补字。后续统一包必须展示此缺口。
- 启动两遍各查看全部300顺序原帧，第二遍独立重提[90,390)；候选`data/gt/candidates/ocr-all-scenes-v1/startup-nontarget/ocr-candidate-v1.jsonl` SHA `96c6896c8aadd805ee8041a49c29908addd2075ea860e5cfa0a372f5e9aa26f4`，clip`data/meta/all-scenes-startup-nontarget-clip-v1.json`，layout-v1；报告`reports/m2-all-scenes-startup-integrity-v1.json`通过300帧/8状态/0字符/0页面。完整HOME启动→logo→空栅格→版权→暂停返回HOME过程无游戏目标日文文字；不是未知字GT。精确黑帧102–109、178–224共55帧，零RGB与账本核对`reports/m2-all-scenes-startup-exact-black-v1.json`。没有为纯非目标范围捏造游戏网格。
- `reports/m2-all-scenes-independent-extraction-v1.json`：启动300帧及背包166帧原PNG两次独立源重解码字节相同，差异0；两遍都逐帧符合原索引。只证明复现，不替代语义核验或用户批准。
- 必要M2工具新增：`GtGeometry`支持原像素矩形/紧凑组合标签、孤立浊点及多光标；`PageOpportunities`支持显式页面ID；纯非目标须显式范围、已知非目标类型、无目标字/光标/完整页，未知仍拒绝。`tools/encode-visible-gt.ps1`只编码人工提供的字面字段/状态/原账本时间，不读图猜字、不冻结。候选评分契约`data/meta/ocr-scoring-contract-all-scenes-v3.json`尚未批准；v1已冻结契约和旧候选全部保持。
- 最终27项Java测试通过：`reports/m2-all-scenes-format-v1/maven-test-pages-final.log`。最终类版本的35帧试标、47帧商店、47帧PC回归分别`{trial,shop,pc}-regression-final.json/log`均通过；`negative-final/report.json`三个未知/缺帧/错源反例实际CLI退出3且没有虚假成功报告。源清单`source-snapshot-final-v1.json`。测试只证明工具，未运行产品OCR/翻译，未宣称录像验收。
- 技能500帧首次逐帧inspect正在生成`reports/m2-all-scenes-skills-inspection-v1`，已查看部分，尚未建立该场景GT。所有新增候选均未批准/未冻结。代理双遍不称用户/人工逐帧核验；全场景收口前继续M1/M2，不触发部分场景卡点。

### 技能完整回合与状态双页候选（2026-10-15，未提交统一审阅）

- 技能完整回合：独立第二次源重解码112帧[17458,17570)，全部顺序原图双遍；评分[17462,17566)104帧/36状态/7页面。`data/gt/candidates/ocr-all-scenes-v1/skills-turn/ocr-candidate-v2.jsonl` SHA `20d92c8959a41aa5e67ca59e094da0e3300ae3283fd1fdf698815a4e16728d5c`，clip`data/meta/all-scenes-skills-turn-clip-v2.json`，layout-v1，报告`reports/m2-all-scenes-skills-turn-integrity-v2.json`。v1保留，v2仅补名字/招式的字符串类型及显示值，文本/时序/位置不变。含17481 PP/类型先变而光标仍旧的单帧，技能选择9/10冰→14/15普通，己方攻击、敌方突进/落空、返回指令菜单；按帧字符4832，HUD在清屏时仍应输出，未伪造数值敌方HP。
- 实机攻击使整幅栅格左右晃动：17503/17504逻辑X偏移+2，17508偏移+1，其余选帧0。原图直接确认，`reports/m2-all-scenes-skills-raw-shift-navigation-v1.json`仅确定性像素块定位，不识字。新增GT状态`renderOffsetLogicalPixels`在有理投影前施加，所有当前源矩形逐帧核验；页面身份只去除已核验源矩形差异，名义格/文本/字段/选择不变才能延续同页。裁切包络拒绝冒充完整字形。HP:占12×4逻辑像素，:L是单8×8组合标签，字面字符与占位分别登记；直接原图放大`reports/m2-all-scenes-skills-packed-labels-v1.png`。契约新候选`data/meta/ocr-scoring-contract-all-scenes-v4.json`，未批准，旧版保留。
- 状态完整显示/双页/退出：独立第二次源重解码108帧[460,568)，评分[462,564)102帧，全部双遍。`data/gt/candidates/ocr-all-scenes-v1/status-pages/ocr-candidate-v1.jsonl` SHA `cdfc263ad361fb0db955c3f9275f9bbfdac232b18340fd0e71a075a7799d61c9`；clip`data/meta/all-scenes-status-pages-clip-v1.json`，layout-v1，报告`reports/m2-all-scenes-status-pages-integrity-v1.json`通过5状态/2页面/按帧字符7711。入场462–468与退出553–563共18空态原帧全部评分；526一帧新经验面板/新第一技能行与旧其余能力/属性2/ID/主人混排全部登记。名字、HP/等级、131编号、122/123/103/139能力、属性、ID16785、主人レッド、经验157734/距离8079/目标等级51、四技能PP含`5/ 5`保留。No.有点，IDNo/无点，压缩字样几何按原图放大`reports/m2-all-scenes-status-packed-labels-v1.png`确认。前置子菜单与返回队伍列表只是边界上下文，本候选不声称覆盖整个队伍列表操作。
- 两事件完整原PNG独立复提112/108帧字节相同，差异0：`reports/m2-all-scenes-independent-extraction-v2.json`。最新工具32项Java测试通过`reports/m2-all-scenes-format-v1/maven-test-shake-final-v1.log`，旧35试标/47商店/47PC/152背包/300非目标回归通过`reports/m2-all-scenes-format-shake-v1/*-regression.json`；3个负向样本退出3且未生成成功报告`negative-report.json`。源版本`source-snapshot-v1.json`。仅证明M2工具及候选结构，不是被测OCR/独立验收。
- 对话660帧、战斗760帧、另一显示几何状态660帧的全部身份检查与按序原图材料已生成：`reports/m2-all-scenes-{dialogue,battle,status-alt}-inspection-v1`，各自日志`reports/all-scenes-*-inspection-v1.log`。当前只是生成材料和部分导航查看，不能称三个场景全部双遍完成。总计新增未冻结候选752评分帧，另有既有冻结35帧；尚未统一提交，继续其余场景及必需显示变化，不请求局部批准。

### 另一几何状态、战斗结果与探索对话（2026-10-15，未提交统一审阅）

- video-03状态双页评分[3534,3663)129帧/5状态/2页，独立重提135帧全部直接核读；GT `data/gt/candidates/ocr-all-scenes-v1/status-alt-pages/ocr-candidate-v1.jsonl` SHA `021fd43307990be1c54539071b9dd4f93761fb35929d14009bc194170e798f4e`，报告`reports/m2-all-scenes-status-alt-pages-integrity-v1.json`通过，按帧字符10314。ウツボット、编号071保留前导0、HP160/160、能力144/105/110/133、属性くさ/どく、ID44837、主人グリーン、经验117997/距离7129/目标51、四技能PP均来自两遍原图。3612单帧旧第一能力/属性1与新经验/后三技能混排保留，不用下一帧补写。
- video-02战斗结果评分[6916,6964)48帧/19状态/2页，独立重提55帧全部核读；GT `data/gt/candidates/ocr-all-scenes-v1/battle-result/ocr-candidate-v1.jsonl` SHA `8d2c1d32849df442e3c38294a3a93cd3e8d87fbbf1be1fcfc2514415b5020157`，报告`reports/m2-all-scenes-battle-result-integrity-v1.json`通过，按帧字符1464。てきの ポニータは たおれた！及ラプラスは おおめに / 976 けいけんちを もらった！全部加载和消失保留；消息清屏仍有己方HUD，6958起6评分帧HUD也消失。此前KO动画仅上下文，不声称完整战斗验收。
- video-01原候选6000–6660核查为开场介绍，纠正场景分类；不把错误标签当探索证据。依据原导航9900户外NPC画面，事前登记`data/meta/all-scenes-exploration-context-v1.json`并保留新上下文[9600,10200)600帧。评分[9838,9926)88帧/17状态/2页，独立重提94帧全部核读；GT `data/gt/candidates/ocr-all-scenes-v1/exploration-npc/ocr-candidate-v1.jsonl` SHA `d4f28d7c291633e2018531ed2b04d6bc0db9cd6dbbb15d6907fda80d2903cd56`，报告`reports/m2-all-scenes-exploration-npc-integrity-v1.json`通过，按帧字符1064。两倍原栅格，9840开窗/9850加载/9857第一页完整/9877清屏/9888第二页加载/9898完整/9922清屏，27空态帧全部保留；第一页面箭头持续，第二页无箭头。字面两页わたしも / ポケモンを そだててるの！与つよく なれば / ガードマンの かわりに なるしね不按历史恢复。
- 三次重提135/55/94帧与各首遍上下文PNG逐文件SHA一致，差异0：`reports/m2-all-scenes-independent-extraction-v3.json`。各候选目录含首读/二读/差异/布局；clip各`data/meta/all-scenes-<scene>-clip-v1.json`。均只是候选结构/复现检查，不是用户逐帧核验、批准、冻结或产品OCR验收。尚未全场景收口，继续队伍/PC/商店，不请求局部批准。

### 全场景候选最终收口与统一提交 gt-all-scenes-v1（2026-10-15，等待明确批准）

**本次处理结论**：全部必需场景及实际显示变化已有代表性完整事件的双遍候选/结构检查，或者有原帧与具体原因支持的待验证结论。矩阵与精确选片登记 `data/meta/all-scenes-review-v1.json`；包内 `summary.json`按四源逐段列出未评分、未选clip范围，`batch.json`列来源/边界/缺口。不是四录像全文GT，不宣称未选范围通过，也未以未处理/困难代替可核验场景。

- 新完成PC层级/列表120帧、PC关闭10帧、队伍26帧、商店买入44帧/卖出58帧/拒售32帧、背包开启34帧。各自候选在 `data/gt/candidates/ocr-all-scenes-v1/<scene>/`，包含首读、独立源重提二读、差异与布局。PC列表选用 `ocr-candidate-v3.jsonl`/`layout-v2.json`/clip-v3；其他本段新增选用GT-v1/layout-v1/clip-v2；旧版本全部保留。精确身份在最终17份 `reports/m2-all-scenes-final-check-v1/<scene>.json` 及包内清单，不另建进度账本。七段独立导出124/14/30/48/62/36/38原帧和首遍PNG逐文件相同，差异0：`reports/m2-all-scenes-independent-extraction-v4.json`。
- 第二遍修正PC1654–1658尚未出现问号、1673–1688列表箭头消失；原首读笔记保留，`pc-deposit-navigation/pass-differences-v1.json`解释依据。背包开启5181两条基础文字+后行孤立浊点、5204遮掉整行せってい、5205文字齐但无光标、5206光标出现全部保留。拒售的两个省略号是两个原字格U+2026，否定 `おつけするわけには まいりません`完整核读；列表箭头与消息箭头1428–1437同时可见，新增可选`cursor.additionalDownArrows`逐个检查原像素投影、拒绝重复几何；不将闪烁计成新页面。
- 当前未批准评分契约 `data/meta/ocr-scoring-contract-all-scenes-v5.json` SHA `619265a1f39869e950cc95e3a85def7b72d0d832b2b10ef9bbcca1f320d8414d`。既有冻结35帧仍用原契约-v1，本包只作参考。Java最新35测试（app4+verification31）全部通过：`reports/m2-all-scenes-format-v1/maven-test-two-arrows-v1.log`；其后只调整审阅脚本/候选资料，Java未改。最终17范围GT结构回归均通过；未知/缺帧/错源三个实际CLI反例均退出3、无成功报告：`reports/m2-all-scenes-final-check-v1/negative-report.json`。未开发或运行产品OCR/翻译。
- 汇总为16个未批准事件的1341评分帧引用，扣队伍2空帧、拒售1后文帧、背包开启2帧重叠后为 **1336新增候选独立评分原帧**；另有35已批准参考，总 **17事件、1371独立评分原帧、263连续状态区间、63完整页面机会**。页面原计数64，背包开启和导航共享同一初始列表页只算一次；所有变化原帧仍保留。评分57.049561秒；含热身/后文的去重1440原帧/60.071930秒，完整材料1461帧引用，四源总66770帧/2779.397368秒。跨片重叠的实际日文、格/原像素/标记和符号无冲突；旧注释`outlined_right`与新`outline_right`指同一原像素轮廓三角，包内`annotation-descriptor-equivalence-v1.json`说明，仅作同源统计去重，不归一化文字。
- **正式入口**：`reports/gt-review/gt-all-scenes-v1/index.html`。完整包3151文件（清单不含自身）、511672941字节；最终 `bundle-manifest.json` SHA **`f999098f9ec4401a55fb10641ad2a1a342d24447af4ce40267e5bb60669f3246`**。逐文件SHA、GT/clip/layout、17连续保帧身份、嵌入资料/PNG尺寸、全部1461帧查看控件/263状态跳转、边界按钮均检查通过，`static-check.json`；不是实际浏览器呈现。六种尺度/转换（黑帧外非目标、两倍对话、状态混排、技能偏移、PC部分绘制、已批准裁切滚动）的放大图每一像素与最近邻原图相符，差异0：包内`evidence/zoom-pixel-check-v1.json`；两张生成放大图也实际查看。没有绕过既有file协议安全拒绝，**实际浏览器呈现待验证**。
- 原四媒体只读SHA、66770帧账本、原35冻结封套及原240文件商店/PC子包身份均保持，`source-identity-v1.json`和`final-bundle-recheck-v1.json`。最终`git diff --check`通过（仅既有换行转换提示）；当前工具源快照 `source-snapshot-v2.json` SHA **`bebfd3ed578a95364a146bea0d5f380e2705ff2ec606a968db5378246b529d36`**，逐文件复核一致，未提交Git。M0真实窗口预检、其余版本/独立会话、实际UI/采集/延迟/长时稳定性仍待验证。候选全部development，acceptance为空，M4不通过则M5仍不可启动。
- 5359背包退出经首读/独立二读/再次原图仍被玩家精灵与重绘遮挡，**整个退出事件不拟冻结**；包内保存5358–5362原图、原因/检查/影响。其他候选得分范围无unknown/conflict。用户无需补录，批准不能将此缺口变为通过。用户只负责本版本/范围的审阅批准，不承担标注，也不称人工逐帧核验。
- 复现命令：`mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' test`；数据编码`tools/encode-visible-gt.ps1`仅转换手工原图笔记；结构检查`tools/validate.ps1 gt-check <ledger> <clip> <GT> <layout> <新报告>`。审阅构建为`tools/build-all-scenes-review.ps1`，仅接受不存在的新目录；当前包已固定不再重建覆盖。检查器为`node tools/check-all-scenes-review.cjs <review-dir>`（实际使用固定Node绝对路径），其会生成/更新派生清单，**已正式提交包不应再调用改写清单**；批准前复检只读遍历现有清单逐项SHA。放大复现为`tools/check-all-scenes-zoom.ps1`，拒绝覆盖既有证据。所有产物都是离线工具/GT证据，不是OCR回放通过。

**本段故障与修复累计**：PC笔记脚本缺闭括号在写笔记前解析拒绝，修复1/3；GT-v1遗漏孤立标记renderNote被校验器拒绝，新manual-v2/GT-v2补说明，后GT-v3仅修布局scope描述，旧失败版本保留，字段/边界不因OCR改变。审阅重叠比较把对象键顺序误当差异，比较器修复1/3改固定取标记类型/行列；同一三角的注释拼写差异经原图/投影核对另附身份说明。审阅UI假定旧GT均有光标数组，静态控件检查抛异常，兼容无光标/旧单对象修复1/3；全部17事件1461帧/263跳转复测通过。放大像素检查器首次C#编译缺Windows绘图接口程序集，修复1/3补实际引用，六类像素检查通过。失败日志 `review-static-v1/v2/v3.log`、`zoom-pixel-check-v1.log`保留，成功`review-static-final-v1.log`、`zoom-pixel-check-v2.log`及17结构报告。未把工具故障当成实机/OCR失败或删除任何评分帧。

**人工卡点现在生效**：正式提交 `gt-all-scenes-v1` 的3151文件清单及上述SHA，申请批准16候选事件/1336去重评分原帧与评分契约-v5；既有35帧及其契约-v1原批准保持。**停止全部后续开发、GT冻结与被测OCR/翻译执行**。等待期间只响应本包审阅反馈、必要修订和相关复检，不转做其他独立开发；本轮一般“继续”不代替本版明确批准。获批或实质修订后按spec §3.4恢复/合批重审。

## Idempotence and Recovery

原视频始终只读，工具再跑应保留相同帧顺序、整数 PTS 和像素哈希；若输入或解码器参数变化，生成新账本版本，旧账本保留供比较。GT 仅以新版本修订并记录差异、依据和受影响回归。首次执行失败不清理原件、不重置已经累计的问题次数、不把失败或待验证改成通过。

恢复按以下顺序：查看本文件最新“下一步” → 核对文件/证据和 hash → 执行最早有前置条件的未完成工作 → 运行最小相关验证与回归 → 更新日志。对同一问题默认最多 3 次有证据的修复尝试；达上限或外部输入缺失时停止该问题，继续无依赖工作，不能假设新材料会自动出现。

**人工卡点优先（当前效力）**：gt-trial-v2、gt-all-scenes-v2/契约v5及其商店单帧修订r2均已获明确批准，复检/另版冻结/有效38项组合完成，**当前无待审版本，继续M3开发**。旧含冲突封套只作历史证据，开发使用有效集合；不得删除冲突帧或按答案重命名actual来通过。将来新增或实质修订GT仍须合批重审；背包5359、独立会话/版本及真实环境缺口保持待验证，M4未通过不得启动M5。

## Artifacts and Notes

已经创建 `config/input-videos.json`、`data/meta/m1-v1/{media,frames}.jsonl`、原始/试标 clip 候选、覆盖矩阵及 `data/gt/candidates/ocr-trial-v1/` 和 `reports/gt-review/gt-trial-v2/`。`data/gt/ocr.jsonl`、中文GT、实际OCR输出/回放报告仍未建立。数据版本放新目录，不覆盖原件或旧证据；`data/frames/` 与 `data/raw/`、构建和依赖缓存均忽略版本控制。录屏原件、外部版权资源不打进发行包或公开仓库。JDK/JavaFX、Maven、Gson、JUnit、外部FFmpeg来源/许可登记 `resources/dependencies.json`，未来再分发许可和游戏资源审计仍待完成。

## Interfaces and Dependencies

内部接口按 `spec.md §2`：`Capture` → `OCR` → `VisibleState` → `Translator` → `UI`。离线验证器和 GT 应与主程序隔离，校验器可读取元数据及答案，被测管线只能收到 RGB/时间/正常设置。开发辅助可用 FFmpeg/FFprobe，具体版本与许可待 M0/M1 审计；字库/语料来源提交固定后才能引用。没有引入新模型或模拟器的隐含前置条件。

## Progress

- [x] **2026-10-17 日文源码页面边界第2/3候选完成生产与独立复核**：3814个固定源文件、8653个控制模板；187测试/package、6目标/同类和全38片实跑通过。473保留/471评分引用增加有来源的最终完成解释，转换新增完成误判0；原规则最终严格7→14，raw7保持，0整页回退。独立复核8645报告输入＋3856来源/策略输入、6736阶段评分、116页/715状态/553转换及2169链接通过。首版9153模板/目标结果、字符串结束语义收紧、旧配置适配失败及统计类调试行号核对全部保留；详见末尾。
- [x] **2026-10-17 v21 PC关闭全38片完成、追加联合网格实验拒绝**：PC第2候选仅1731可见kind及直接派生菜单/计时元数据变化，全部3577 ready/fire/invalidate/messageConfirmed保持、180测试/package、六同类及全38片通过；严格仍7。授权追加的固定8帧联合拟合全部38片/3577观测完成；2962评分引用可拟合，但乐观仅换坐标也使严格7→0、像素框16→0，原7正确页全部回退，因此拒绝接入，不再追加调参，原三次失败保持。此项失败与源码辅助新问题分开，不用重命名重置几何预算。

- [x] **2026-10-17 本轮原规则最终严格取得显著实测增益**：7→14/116（6.034%→12.069%，新增7页、无回退），最终失败原帧少155；增益为日文源码辅助状态解释，raw图像整页仍7。既有像素框等失败未豁免，整体OCR未达spec。PC第2候选、一次授权的失败联合网格试验及来源第2候选分别记录，不清零/追加几何预算。

- [x] **2026-10-17 紧凑HP/冒号输出修复，第1/3候选完成**：4270历史输入事前核验、103份生产源/类/JAR原件归档、三固定日版9个打印源码身份一致。5项目标测试及178项全测/package通过；20评分原帧/115标签与12热身/后文引用发生同一合法单元变化，其他输出/门控不变。三同类片段和全38片v20实跑已关闭，4561身份及6736逐帧/116页/553转换独立复核通过；字段位置一致114→115页、严格仍7/116。验证假设修正与31个临时JUnit XML替换如实记录，非生产候选重试。最终证据`packed-render-units-fix-v1/delivery`及`independent-check-v1.json`，旧几何/3541等3/3不重开。

- [x] **2026-10-17 独立版本分维度评分报告**：`score-contract-dimensions-v1.cjs`按事前协议对r14/v19全部116页/3361原帧/1316转换帧拆分文字、字段位置/附标/单元、符号、状态、像素框，保留原严格分数；旧箭头和对应完成状态名称仅在冻结v1契约SHA＋schema1旧扁平结构下兼容，1页10帧。32反例检查及不导入新评分器的6736逐帧独立重构、715状态/553转换机会/116页汇总、4263输入身份、4043链接与控件替身检查通过。最终入口`scoring-dimensions-v1-final`，raw60/final61逻辑合取仅诊断；无GT/生产/旧评分器改动、无新构建/回放或验收通过。

- [x] **2026-10-17 r14/v19坐标和计分口径只读审计**：全部116页/3368评分引用/3361原帧（含1316转换原帧）完整登记，15561次冻结投影检查及15532个目标包围范围像素检查完成；6736次raw/final逐帧分数独立重构、4211原输入SHA和116页归类复核通过。原严格7/116及字符成绩保持；最终53页仅源矩形不同（14无检出风险、39有风险），47页混合、9页其他。计分口径问题、旧描述兼容、漏附标/附标归属、紧凑字段渲染单元及未确定边缘风险分别保留。报告`coordinate-contract-v19-v1`与独立检查`coordinate-contract-v19-check-v1.json`；不是评分器修复或新验收通过。

- [x] 2026-10-17 v19生产OCR接入独立`outputReadiness`（ready/fire/菜单与消息资格/撤销与计时证据）；原visibleState.kind不改。176测试（108 app＋68 verification）通过、package通过，构建快照233源/100类；JAR68类与当前编译类逐项SHA相同。
- [x] v19全部38片重跑完成：3577判定与离线候选一致，全116页/1316转换、原20收益及短页复核通过；原OCR输出除耗时/新增资格字段外完全一致，原时间轴与严格评分保持；证据`completion-production-v19`。

- [x] 2026-10-17 消息历史候选（本门控路线累计第2候选）：v18全116页/1316转换原帧及3577保留引用通过离线条件，独立检查14308判定一致、18项性质通过，报告`completion-message-history-v2`；当时生产尚未接入/回放，现已由后续v19全回放完成。

- [x] **M3现有v18菜单/消息分开门控验证（2026-10-17，仅离线诊断）**：消息独立确认＋PC悬置父菜单门控将残句5→0、其他转换触发4→2；原新增20页全部保留且正确文字源时间≤200ms，无新增漏页/短页漏检，滚动42帧与清屏/空/非目标722帧无输出。17885次因果判定、586不可变输入SHA、全部116页/1316转换帧、13项规则性质由独立检查通过。14页新增等待和剩余2转换/2单帧漏页均保留；未改生产/GT/严格7/116，UI时延待验证，完整证据及唯一下一步见末尾。
- [x] **M3现有v18完成触发规则对比（2026-10-17，仅离线诊断）**：全部116页/3361评分原帧/1316非完整状态原帧，9个稳定窗口与原判定完成；主100ms补20个可触发页，最终正确文字命中78→98、≤200ms源时间触发76→98，新增残句0、PC开启过渡新增误触发1、两单帧短页仍漏。独立重构35770次判定、572输入哈希、全部页面/触发/分位数/转换覆盖和10项规则性质检查通过。原严格7/116及生产/GT保持；实际UI时延未测，未授予验收。完整定义、证据与下一步见末尾。
- [x] **M3现有v18全部116页错误归因（2026-10-17，仅诊断）**：116页完整表及3368评分引用明细已生成；独立重算每帧raw/final分数、交叉核对117原片页面别名→116去重机会/3361原帧、564输入哈希和96类身份，原完整aggregate保持。严格7/116；纯文字原始97/116、辅助后98/116。源矩形影响100页（最终53页仅此阻塞），完整消息误判加载影响31页（7页仅此阻塞）。本项不代表新算法、回放或验收通过；优先级/预算与下一步见末尾。
- [ ] **持续目标（2026-10-16用户明确要求）**：“请持续执行任务直到像素文字日文识别准确率达标”。继续M3至冻结开发范围的原始/最终OCR门槛有真实证据；不将字格诊断或开发回归冒称独立M4验收。当前无新GT卡点，中文仍不启动。原包围问题3/3记录保持，先做已定位帧网格/原始字形及区域独立工作。
- [x] **M3真实图像管线/首次完整评分子项（2026-10-16，整体仍失败）**：当前帧字格→源布局字段/区域→符号/可见状态→关闭actual后冻结比较，消费者不接收GT/片段标签。完整v1/v3均保留全部38项/3361原帧/86602字符/117页，原始字符由27.9128%到87.8802%；字格诊断另列，不混作通过。v3四媒体/GT/时间线/源类/JAR/120测试的独立证据复核通过，只证明失败报告可复核。后续125测试改动在v4全回归中，M3/M4未通过。
- [x] **M3网格相位与原始字格基础（2026-10-16，尚非整链通过）**：新增`RasterGridRefiner`保留双轴周期/相位候选，代表四片实跑仅两倍探索几何通过，非整数坐标仍失败；新增`PixelGlyphReader`固定日版字形Hamming候选、单格Otsu/对比度、同帧清浊点组合，保留共形假名、不借历史补字。`glyph-audit`原始字格诊断驱动已实现；92项Java测试通过。已批准35帧路牌共488预期普通字格字符全部精确读取，但有1537个未分类的框/箭头/背景候选，几何仍错，不能称OCR100%或达标。全38项字格诊断正在运行，后续结果写末尾。

- [x] **2026-10-14 文档检查与修订**：检查五份文档和素材目录；补充评分契约、M0 可行性检查、中文审计分母与稳定性趋势规则，校正路径和过时说明。仅文档检查，不代表任何开发里程碑通过。
- [x] **2026-10-14 流程确认**：用户确认代理双遍核读及独立会话门槛，并要求增加人工审阅卡点；已同步五份文档，未开始标注或开发。
- [x] **2026-10-14 采帧与选片文档补充**：明确 FFmpeg 连续解码、全视频索引/选段 PNG、重提取一致性，以及完整事件定义、各场景边界、截断处理和审阅证据；仅文档完成，未解码或选片。
- [ ] **M0**：最小工程、Windows/JavaFX 环境、四视频路径登记和初次构建证据。
- [x] **M0 工程子项（2026-10-14）**：Java 8/JavaFX 8/Maven 双模块从零建立；四原视频路径登记；9 项基础测试及 JavaFX linkage 自检通过。证据 `reports/m0-20261014/maven-test-final.log`、`app-tests/`、`verifier-tests/`、`bootstrap-self-check.log`、`environment.json`。本轮源快照 SHA-256 `7925a22aafb55f164496da314030e1c53e5aa9db8488d0f1ae9b4df95b793a04`，清单 `source-snapshot.json`；不等于 OCR/真实 UI 验收。
- [ ] **M0 真实预检待验证**：目标投屏程序/窗口、GPU、DPI、缩放、更新频率、前台/后台/遮挡/最小化采集，以及实际 UI 呈现观测与时间对齐均无实测证据。CIM 查询被当前环境拒绝；不据此要求补录，继续 M1。
- [x] **M1 工具已实现**：全量帧/PTS/RGB 账本、源开头重解码导出、PNG 像素核对、最近邻导航拼图；9 项基础测试不代表录像验收。实际四媒体索引完成后的帧数和哈希见下一项及本轮证据。
- [x] **M1 索引/复现子项（2026-10-14）**：四原件前后SHA一致、66,770帧完整索引、整数时间/RGB尺寸配对异常0、独立JSONL检查通过；四媒体导航原帧及300帧试标上下文、39帧第二遍全部重提取/无损读回相符。M1覆盖扩充、版本/会话证据仍未完成。
- [x] **M2 初次双遍核读与材料（2026-10-14）**：35评分帧、14状态、1完整页，双遍差异已依据原图处理；候选结构检查及3个负向检查通过，审阅包95文件哈希相符；入口浏览器显示待验证。**该批原提交时等待批准，现已获批/冻结；仍不标 M2 整体通过。**
- [x] **M2 首次试标获批冻结（2026-10-14）**：原话/范围和材料哈希登记；95文件及四原件复检一致；35帧结构复检通过；冻结GT/clip/layout/契约字节不变，封套 `data/gt/frozen/ocr-trial-v2/freeze.json`。只有首次试标冻结，不是OCR通过。
- [x] **M1/M2 扩充准备（2026-10-14）**：事前登记9个场景上下文候选（非完整事件认定），商店候选740原帧连续导出/读回哈希相符；扩充GT/评分范围尚未建立，不是新批次通过。
- [x] **M1/M2 部分场景双遍材料（2026-10-15）**：商店退出、PC光标及首连接确认两段94候选评分原帧/29状态/6完整机会，独立重解码与双遍核读/结构检查完成；240文件包及原证据保留。**其单独待审请求现已撤回，候选未批准/未冻结，将并入全场景统一版本，不是M2整体或OCR验收通过。**
- [x] **全场景统一审阅文档调整（2026-10-15）**：按用户原话修改AGENTS/spec/PLANS/README和本计划；取消试标/部分场景的单独卡点，列出全场景收口条件和恢复清单；本轮仅文档核对，不新增GT、不冻结、不运行OCR。
- [x] **M1/M2 全场景候选收口与统一提交（2026-10-15）**：17代表事件（16未批准+1既有批准参考），1336新增去重评分原帧，全部原图双遍及结构检查；5359退出等缺口附原图/原因，3151文件统一包`gt-all-scenes-v1`正式提交，哈希见最终记录。35工具测试、1461帧控件/263状态跳转及文件检查通过，只证明工具/材料。
- [x] **M1/M2 对话扩充与新版统一提交（2026-10-15）**：用户反馈后完成20个新增对话事件/语段及原路牌续页，21项原图双遍/结构/独立重提2116帧引用身份检查；与旧17项合为38项统一包gt-all-scenes-v2，原包不覆盖，结果详见末尾。
- [x] **审阅访问兼容修订（2026-10-16）**：新增可选择本地ZIP的手机离线入口；原包/GT/原帧/范围保持原SHA。真实1.382GB ZIP读取、38场景/3577帧控件/717状态跳转及损坏反例检查通过，仅为资料与逻辑检查；安卓实机显示待验证，未批准或冻结新增GT。
- [x] **全场景统一人工卡点解除及冻结（2026-10-16）**：用户先回复“gt审核通过，请按项目计划指引，继续执行”，复检期间再次明确“批准 gt-all-scenes-v2 的拟冻结候选范围及评分契约 v5”。原7636文件/四原件/保帧PNG身份及38项CLI结构复检全部一致；37项新增候选与契约v5原字节冻结，旧35帧/契约v1引用原封套。详见末尾本轮证据；批准不等于OCR验收。
- [x] **M2回放/内容评分基础（2026-10-16，工具已实现）**：53项Java测试通过；两种实现全38条目/3577帧引用/3507去重保留原帧的整数PTS、duration、RGB身份逐项相同；三个实时传帧片段无丢帧，非UI验收。评分器逐帧/状态等权/字段/位置/符号/缺输出及空态检查已实现，夹具不称OCR输出。
- [x] **M2跨片元数据缺陷发现与修订候选（2026-10-16）**：原全部7个重叠帧中1处冲突；保存原冻结字节、最小复现与失败，按原件两次重解码82帧/代理核读提出单帧元数据修订；有效38条目加候选的全部7处复检冲突0，仅是候选检查。
- [x] **修订统一人工卡点解除（2026-10-16）**：用户原话“批准gt-all-scenes-v2-shop-metadata-r2修订”；475文件/四原件/四商店事件复检一致，32帧修订事件另版冻结，其他三事件引用原批准；见`reports/gt-approval-shop-metadata-r2/`。不重复申请已生效批准。
- [x] **M2开发基准有效集合（2026-10-16）**：由原38项封套与获批修订组合，不改payload，38项GT重新逐片校验/全部3577原帧引用重放；全部7重叠精确角色/顺序/kind冲突0，3361评分帧/117页面分母保持。评分器复制夹具全范围通过，只证明工具；独立验收集合为空，M2独立会话维度/M4仍待验证。已具备M3开发前置。
- [x] **M3来源审计/候选基础子项（2026-10-16）**：三个固定日版提交，覆盖红绿/蓝/黄来源字库与打印路径；每份197实际装载图块/50组合规则，保留同形假名与profile歧义。`GlyphTemplate`/`PixelGlyphMatcher`基础类、验证侧`font-audit`实现，69项Java测试及package通过。原素材/字形掩码只留忽略的.cache；再发行许可未确认。没有把源图块自检当录像OCR成绩，M3整体未完成。
- [x] **M3视口工具实现子项（2026-10-16）**：`GameViewportLocator`纯像素候选定位、双轴像素周期估计/半开投影，独立`viewport-check`输出/原PTS回放/逐逻辑像素边界比较已实现；80项Java测试及package通过。全38项3577原帧引用实际运行；686/3061有冻结几何去重原帧完全吻合，55精确黑帧拒绝，整体几何验证失败。包围问题3/3后局部阻塞，完整原帧失败不删除；可以推进独立的已定位帧尺度/相位拟合问题。M3不通过。
- [ ] **M1**：四视频逐一核验、原始 PTS/RGB 索引、覆盖矩阵与连续 clip。
- [ ] **M2**：代理双遍核读、用户审阅批准、GT 冻结、会话隔离、真实时间轴回放比较。
- [ ] **M3**：日文像素 OCR、原始/修正结果、状态模型及开发回归。
- [ ] **M4**：OCR 独立验收（若素材不能隔离或 GT 不完整，单列“待验证”）。
- [ ] **M5**：生产中日语料与两类 ≥95% 覆盖核对。
- [ ] **M6**：翻译、变量绑定、独立 JavaFX UI 与失效管理。
- [ ] **M7**：独立中文、实际 UI/采集、延迟、两种 ≥2h 稳定性及总报告。

## Surprises & Discoveries

- **2026-10-17 分维度拆分后的实际阻塞**：final字段位置不一致27原帧，其中7帧附标、20帧紧凑渲染单元（覆盖2完整页、8转换帧）；符号兼容后仍20原帧不一致，涉及3完整页及6转换帧；状态兼容后仍1099原帧不一致，涉及37完整页及406转换帧。像素框数值差异2071原帧/100页，其中另有20帧目标缺失不能作纯边框比较。各维度可重叠。旧试标10帧既缺箭头style又使用complete_waiting，只有在冻结契约和旧结构均匹配时可解释为同一filled_down/message_complete；其余状态对照均保留，绝不全局把loading/menu/clear名称混同。详见末尾全量报告。

- **2026-10-17 坐标与计分口径审计**：spec §4及冻结v1/v5的rectangles均要求像素边框差异另报，但现有`OcrScoring.java:79`把sourceRect与gridRect/附标/渲染单元共同并入regionGeometry，符号也因源矩形差异失败；v5同时要求有理数投影、动画相位及可见附标包围范围，因此不能一律取消源坐标检查。15561次冻结投影与程序区域自身网格投影无矛盾，差异主要来自网格起点/尺度及附标范围。原图检查发现HP横条/相邻行会进入阈值支持区，像素风险不等于字符截断；7帧附标/文字差异不能按边框豁免。旧试标箭头缺style而实际为filled_down，解释原4个symbol_structure页中的1页；其余3页仍有真实符号存在差异。详见末尾审计证据。

- **2026-10-16 视口定位在建**：本轮用户“执行下一步”后按顺序完整重读spec/PLANS/本计划，保持已批准GT字节。先实现纯像素背景包围/轴向像素周期定位及独立`viewport-check`驱动；验证约定为全部38条目/3577保留原帧引用，按原PTS传入，对每个已冻结网格的评分帧比较全部逻辑像素边界（floor/ceil、含晃动偏移），不得仅比较外框。无冻结网格的非目标帧单列，不计几何成功；精确全黑独立核对拒绝结果。合成图测试只验证算法性质，不作实机验收。
- **2026-10-16 背景断连问题修复1/3**：首轮Java测试中有规律的深色笔画将浅色背景切为多个孤岛，原连通包围器未返回视口；失败`reports/m3-viewport-v1/maven-test-attempt-1.log`保留。补行列支持度作为附加候选，不改测试图；全工程73测试通过`maven-test-attempt-2.log`（当时尚未加入后续比较器测试）。正在全范围原帧运行几何检查，不能称定位通过。初次原帧候选误把围绕屏幕的机身边框选为更大视口，完整attempt-1输出继续保留，拟修复时增加内部单色调一致性约束；不按源ID/GT坐标裁剪。

- **2026-10-14 真实滚动**：5296–5297 的上行文字仍有基础字形但浊点/半浊点被框裁掉；第一遍错误沿用完整字符，第二遍直接原图放大发现并更正为当帧ホ/シ。不能以历史或源码完整消息掩盖这两帧。
- **2026-10-14 四媒体时间**：实际是30、24、24、19帧/秒，各自保留不同 time_base；总66,770帧全部配对。source-01 UDTA容器解析重试警告保留；未发现帧解码/时间缺口。其余三媒体元数据 encoder=Lavf58.20.100，来源/独立会话不能从文件数量、名称或编码器推断。导航跨素材内容相似，也不证明独立会话。
- **2026-10-14 审阅入口预检**：内置浏览器安全策略拒绝 file 协议，停止浏览器尝试，没有改成间接浏览器/网络方式绕过。静态JS编译、78张图片路径及95文件哈希检查通过，真实浏览器显示仍待验证；局部原图与最近邻图已由代理直接查看。

- **2026-10-14 环境**：Oracle JDK `1.8.0_192-b12`（自带 `jfxrt.jar`）、Maven `3.9.16` 可用；系统 API 为 `10.0.22000.0`，Maven 报 Windows 10，因此指定 Windows 10 实机兼容性仍待验证；CPU 环境标识 Intel Family 6 Model 140、8 逻辑处理器，GPU/CIM 被拒绝。PATH 中 Python 为不可用的商店别名；离线工具改用 Java 8。已安装通用 FFmpeg/FFprobe `8.1.2-essentials_build-www.gyan.dev`，只调用二进制及读取许可，不读取该旧目录的代码/GT/账本；原输入没有变化。
- **2026-10-14 失败与修复**：初次 Maven 下载失败（网络权限，`maven-test.log`）；下载已获环境审批，从官方中央仓库固定下载，`maven-test-network.log`。验证器首次测试失败：工具路径在类初始化时读取，模块测试工作目录不同导致找不到配置（`maven-test-with-ledger.log`）。此问题修复 **1/3**：改在 CLI 入口加载工具配置，使纯像素/时间检查无配置依赖；目标测试、同类时间/hash 检查和全工程 9 项回归通过（`maven-test-final.log`）。

- **2026-10-14**：目录枚举已确认上述 4 个 MP4 和 16 个 PNG 存在；未解码、计算媒体哈希或核读画面，会话、内容、帧数和版本待验证。不得照搬旧结果。

## Decision Log

- **2026-10-17 原标准下采用显式源码辅助最终状态，拒绝失败几何**：联合网格试验虽少590个坐标边差，却令全部原7严格页回退，故不采用。独立推进日文来源控制解释：配置只由三固定日版源构建，当前两行精确匹配且无更长页前缀歧义时，生成带源SHA/路径/行号的correctedVisibleState；raw visibleState、文字及发布门控不改。首版@停止符不足以证明页面停止，第二配置要求后继真实页面等待/结束，8653模板。OcrComparison最终输入适配读取此显式最终状态并核证据、禁止改符号；OcrScoring源/类字节未变。全38片及独立复算确认最终14/116，raw7/116，不能把它叫原始图像提分、放宽计分或产品验收通过。

- **2026-10-17 一次追加几何试验的明确授权**：当前用户要求“继续处理，直到严格成绩有显著提升”。由于AGENTS第6条默认同问题3次上限，本代理先继续不受影响的PC关闭问题，并提出一项限定追加：连续原帧联合拟合网格，GT/评分/全部失败帧不变。用户明确选择“允许追加一次，目标是提高原严格成绩”。据此允许一次固定算法实验，非无限追加/清零、非更改GT/原评分授权；采用最多8帧历史的源字形笔画重心及稳健仿射拟合，在GT评价前确定候选，不为未发生显著提升宣称完成。

- **2026-10-17 用户要求“修复问题”**：先执行上一唯一计划中已定位、可独立验证的队伍HP/冒号结构问题，生产候选第1/3一次达成，保留GT和原成绩。全38片后继续核查剩余问题：问号存在同一字形两种冻结编码、3箭头原图与冻结可见性记录有相位疑点，不能为了消除分数差异直接改运行时输出或GT。未解决项继续保留，下一步转向有独立当前/历史结构证据的PC关闭转换根因，不将本项通过冒称所有问题修复或M3/M4通过。

- **2026-10-17 用户授权实现独立分维度报告**：用户明确要求按计划使用现有v19覆盖116页及转换帧，处理旧箭头描述兼容，保留原成绩，不豁免漏字/附标/字段归属、不改GT并独立复核。本轮只新增隔离比较工具与报告，不修改生产和原Java评分器；兼容以原冻结v1契约SHA和旧schema/扁平结构为边界，不按帧号、片段或固定答案豁免。原像素框/名称/描述差异继续存储，逻辑合取与UI/物理边界资格分开。下一步转向已定位且不依赖旧3/3阻塞的紧凑字段输出问题，未在本轮执行生产修复。

- **2026-10-17 用户授权先审计坐标和计分口径**：用户原话“用现有v19结果把纯边框差异与漏字、截字、字段归属错误分开，覆盖全部116页，明确哪些该修算法、哪些该修评分器。保留原成绩，更新唯一执行计划”。本轮据此替代上一尾部剩余两次门控过渡根因分析，限定为现有结果的确定性审计和输出可见的原图诊断；没有新GT、新生产候选或重跑。先固定协议，再保留旧严格成绩、逐帧独立重构，并单列去除sourceRect的诊断视图，绝不将59/60页诊断数作为新成绩。下一步优先建立版本化评分维度分离，算法问题和边缘疑点不豁免，旧3/3阻塞不重开。

- **2026-10-17 用户授权菜单/消息门控全范围验证**：用户要求“用现有v18结果验证菜单完成与消息完成分开门控，减少误触发，保留新增20页的收益，并在全部116页及加载、滚动、清屏帧上复核，更新唯一执行计划”。本轮限定为已有输出的隔离实验，未接入生产。先固定消息独立确认、全部空心菜单阻断反例、来源支持的PC父/子选择门控三个对照，不改变100ms窗口。当前菜单选择不再直接认证新消息；PC道具父选择空心、尚无活动子选择时保留等待，主消息箭头可解锁。商店感谢语也有空心光标，因此拒绝全局空心阻断。选定规则减少误触发且保留20页，但14页多等41.667–125ms，不能仅凭P95/最大值不变忽略逐页代价；下一步先验证消息确认在纯菜单转换时保持，生产接入与实际UI仍待后续证据。旧几何/3541等耗尽预算不重开。
- **2026-10-17 用户授权文字稳定触发对比**：用户原话“按这个方向执行，用现有v18逐帧结果对比‘箭头判定’和‘箭头＋文字稳定判定’，覆盖全部116页及加载、滚动、清屏片段，统计残句误触发、短页漏检和输出延迟，并更新唯一执行计划”。据此替代上一尾部只做7页可辨识性审计的行动，直接在全38项已保存输出上做因果规则实验。稳定是可测的触发启发式，不应因不能绝对证明终止而排除；GT完整性与spec验收仍独立。先固定100ms主窗口及0/33/50/67/100/133/167/200/250ms敏感性范围；菜单/状态页共用现有规则，只给message_loading补稳定分支，避免把菜单无箭头算成无意义漏检。判定接口无GT、片段/帧号或未来输入；真实热身保留，不跨缺口积累历史。100ms结果有价值但仍有1个新增过渡错误、2个单帧短页未解，先做菜单/消息完成分离验证，不直接接入生产或继续盲调等待时间。旧几何/3541等3/3预算不变。
- **2026-10-17 用户授权现有v18页面归因与优先级调整**：用户要求“先用现有v18结果生成全部116个页面的错误归因表，明确主要问题和修复优先级，并更新唯一执行计划”。据此本轮限于确定性离线诊断、证据检查与本计划更新；不继续旧尾部PC1731单帧修复。保留严格原分数，纯文字统计仅作诊断；优先看页级共同阻塞及单独阻塞数量，不以状态修复帧数冒称整页收益。几何路线/3541稀疏标记3/3保持局部阻塞，不因归因结果自行重开或重置预算；下一可推进项转为无箭头完整页在线可辨识性审计。
- **2026-10-16 持续执行授权与原阻塞恢复**：用户在上一轮明确报告“包围提取3次修复后仍失败”之后要求“请持续执行任务直到像素文字日文识别准确率达标”。据此按该次持续推进授权继续处理原阻塞，不把它当作必须再问一遍确认；原三次记录/次数保留，下一次为累计4。本轮改用黑侧边界/全高显示结构，累计4后发现游戏内暗天花板会缩短信号包围，累计5补全与整帧高宽比一致的显示区域。不是将失败问题清零或改名规避。仍逐次保留证据；同方法再无进展时转独立工作。GT/评分/四视频/中文门槛没有放宽。

- **2026-10-16 安卓访问反馈**：用户反馈“安卓上的浏览器好像识别不了文件的相对路径”。当前入口确实直接引用相对图片/附件；尚无手机URI/浏览器日志，具体系统权限原因不能冒称已诊断。选择另建只读访问入口，通过用户主动选择ZIP取得文件内容，使用经原清单SHA核对的Blob显示图片/附件；不修改原包或降低浏览器文件权限，不新增网络请求，不将此反馈记作GT批准。允许范围限于本审阅材料相关修订及检查，M3–M7继续暂停。

- **2026-10-15 用户明确调整卡点**：原话“请修改文档说明和执行计划，一次性生成所有场景的gt后再进行人工卡点”。据此覆盖过去按少量场景合批即暂停的流程：撤回gt-expansion-v1仅商店/PC的待审请求，保留全部材料身份与未批准状态，已批准35帧不变；后续M1/M2连续处理全部场景，按spec §3.4收口后一次性正式提交。全部场景不意味着凭空补齐缺失素材或四视频全文标注；不得以难度跳过已有可核验项。日文与后续中文保持阶段前置，各自全场景统一审阅；流程调整不是GT批准。本轮用户仅请求文档修改，故不执行新的采帧/核读/开发。

- **2026-10-15 合批停止**：只提交原图已可靠完成双遍的商店退出与PC完整光标/确认页，不把候选740/1200整段或未核清组合/遮挡字形记作完成。挑选依据是原画面完整操作及可见边界，尚不存在被测OCR；未知部分和其原帧保留，不是在看成绩后删失败。两场景合批无需逐个页面问用户，提交后整批停止。

- **2026-10-14 本次批准与合批**：用户已批准当前 gt-trial-v2；复检一致后冻结这35评分帧。回应用户关于逐个审核的疑问：首次试标卡点已结束，后续按场景连续推进、合批提交；每个片段无须单独审批，整批提交后仍强制停止。

- **2026-10-14 首次试标**：选择video-02一页而非整条消息，保留该页加载、完整、滚动及替换后的可见状态。下一页5300加载只作后文并明示不评分；未扩大为第二完整事件。空态11帧、单帧加载态及2帧裁切滚动均在分母中。候选GT不冻结；用户批准后先重新检查身份与完整性，再冻结这一范围，然后按计划扩充并重新提交，不能直接跳M3。
- **2026-10-14 停止点**：提交 `gt-trial-v2` 的具体哈希/范围，等待用户明确批准。M0实际采集、其他场景GT、M3–M7均暂停，不用其他独立工作绕过卡点。

- **2026-10-14 开发授权及实施**：用户本轮明确要求按文档开发，解除此前“仅文档”范围。选择 Maven 双模块物理隔离应用与验证器，运行时不加载 Gson、GT 或评分标签。使用可用的 Java 8 构建离线工具，避免新增 Python 运行前提。M0 真实设备子项未满足不标通过；先继续独立的 M1，M3/M5 卡点不变。

- **2026-10-14 采帧与选片**：按用户要求将已讨论方式落入 spec 和本计划；完整事件以原画面过程与前后状态证明，静止时长和像素差异均不能单独判定。采帧工具只提供确定性像素与时间，不生成 GT 答案；截断范围保留待验证，人工卡点不变。
- **2026-10-14 用户确认**：允许当前编码代理直接看图双遍核读，不引入其他模型；确认连续状态区间标注和独立会话验收门槛。新增强制人工卡点：双遍后提交材料，用户明确批准该版本后才冻结 GT 并继续开发。用户本次确认的是流程，不是尚未生成的 GT。M4 未通过不得启动 M5。
- **2026-10-14 文档范围**：用户授权修改其余检查意见；只改文档，不实施 M0。评分契约与运行环境预检分别在 M2、M0 落实；验收数值不降低。
- **初始决定**：只继承用户**现有四段录像**作为离线动态素材；不补录、不扩大视频来源；现有录像覆盖以核验结果为准。
- **初始决定（按用户确认补充）**：采用代理原帧双遍核读 → 用户审阅批准 → GT 冻结 → 原 PTS 回放；不用其他模型，无法可靠核验的范围待验证。
- **初始决定**：计划及进度集中在本文件，结构由 `.agent/PLANS.md` 规定；所有里程碑初始未完成。

## Outcomes & Retrospective

**当前实际成果（2026-10-14）：最小Java工程及隔离验证工具构建通过；四原件66,770帧账本已建立；首次试标35评分帧/14状态/1完整页经代理双遍、用户批准、版本/结构复检后已冻结，评分契约同步冻结。** 没有运行OCR/中文映射，M2整体尚未完成；其他版本/会话/场景、真实采集与实际UI仍待验证。获批试标不等于OCR或产品通过。

**本轮扩充**：9个上下文候选中六段合计3610原帧已连续导出/核对；商店、PC内两完整子事件已完成94评分帧代理双遍和结构检查，100原帧（含后文）及240文件审阅包准备完成。其余范围/场景仍待验证，本批GT尚未获批/冻结。

**本轮验证**：四原件/账本和已批准试标SHA未变；两个新候选结构检查通过，100原PNG独立两次复提字节一致，三错误反例拒绝；240材料文件/嵌入数据及控件逻辑检查通过，真实浏览器呈现待验证。14项Java测试通过只证明工具/基础工程，未跑被测OCR。完整证据/命令/源版本及失败根因见第二批提交记录。

**最新流程与本轮实际改动（2026-10-15）**：只修改五份文档，统一为“全部场景GT生成/双遍/检查与缺口收口 → 一次性人工审阅 → 明确批准/复检 → 冻结新增GT”。撤回94帧部分场景的待审请求，不修改其GT、审阅包或哈希；尚无全场景统一包、GT批准或新增冻结。本轮仅检查文档一致性/差异，不把以前14项测试当作本轮重跑，不宣称补完任何场景。

**本次文档验证**：`git diff --check`通过，无空白错误（Git仅提示既有LF/CRLF转换）；核对五文件的试标/全部场景/统一提交/冻结/中文前置措辞一致，旧停止请求已标历史并撤回。只读重算确认原商店/PC候选SHA仍为`cb90c627…f4db7f` / `3b9da087…0df0eb`，旧包清单仍为`57ff7f12…3a6f66`，首次冻结封套仍为`6fec19a0…65cd54`，完整值见历史记录。已确认下一步背包verification文件存在、新报告目录不存在；未运行inspect、构建/测试或GT工具。

**当前实际成果（2026-10-15，覆盖前文历史“仅文档”描述）**：全场景GT候选及可复核缺口已统一提交`gt-all-scenes-v1`；3151文件SHA复检通过，35项工具测试通过，新增未批准1336独立评分原帧，既有35冻结保持。尚无产品OCR/中文映射/真实UI验收；M2整体、独立会话和其余阶段不标通过。

**本轮用户反馈与恢复（2026-10-15）**：用户原话“但我希望能尽可能多地再收集包含对话的事件，请你检查当前进度，继续收集GT”。这是要求扩充审阅材料，非批准旧版。核对原17事件：探索NPC只有一个两页事件，另有一页已批准主文本框参考；其余主要为菜单/战斗/非目标。保留旧包身份和批准35帧不变，扩大开场、室内/户外NPC、剧情/招呼和牌子等实际对话来源；不设固定事件配额，不以OCR成绩选样，不声称全部录像全文GT。按全部受影响场景完成后生成新的统一审阅版本。

**对话扩充执行过程（2026-10-15，最终统一提交见本节末尾）**：

- 依次完整重读spec/PLANS/本计划；四原件及旧3151文件只读SHA复核一致，`reports/m2-dialogue-expansion-v1/source-identity-v1.json`。旧包/35冻结封套不变，用户扩充反馈解除旧版等待但不批准或冻结任何新范围。
- 事前范围登记`data/meta/dialogue-expansion-contexts-v1.json`；五次源开头连续导出新上下文合计6650帧（v02城镇1550、v03中心1920及道路530、v01研究所1100及返回1550），全部RGB/PNG与全量账本相符；既有6000–6660开场660帧作为首读来源。原件未重编码。
- 开场五自然语段候选`data/gt/candidates/ocr-dialogue-expansion-v1/{intro-greeting,intro-oak,intro-creatures,intro-pets,intro-research}/`，首读全部逐帧裁剪，第二遍重新从原件解码[6056,6482)426帧并查看全部有序原图及滚动/淡出放大。首读误记数个单帧边界及箭头闪烁，第二遍原帧和像素变化扫描找出并修正；原笔记/逐项差异均保留，不声称两遍无差异。377评分帧/101状态/10完整页，五份`*-integrity-v1.json`结构通过，未批准/未冻结。
- `slice-verified-frames.ps1`只从已连续解码/逐帧RGB核验的父范围取连续子集，每PNG复制前后SHA相同，保留父核验SHA/源解码命令/实际切片来源，不伪称切片是新独立解码。原上下文及独立二遍导出均保留。
- 新离线展示/像素导航脚本不识字、不生成答案；阈值掩码仅定位变化，淡出文字仍以原图为准。新像素扫描器首次C#缺绘图接口程序集（与旧放大器同类，累计该程序集问题第二次修复，共2/3），改读取实际绘图接口/加密程序集引用后扫描成功。窄裁剪标签挤到相邻格，显示脚本改窄格只写帧号，保留原图和旧显示；不改GT。尚未跑新Java构建，Java未修改。
- 另外七事件（博物馆两段、道路NPC、商店NPC、劲敌、博士回复、包裹交付）现均完成源开头独立重解码、第二遍核读、差异处理及结构检查。新增609评分帧，合前述五段共986帧/27页面；博士回复13467滚动、13474/13477加载以及交付24316清屏等首读差异保留并修正。不是OCR修复或人工逐帧核验。
- 再完成室内NPC三段、中心NPC、路牌PP说明、护士欢迎/接收等待/返还三段，共8个对话事件/自然语段；另将原道馆路牌第二页作为第21个新增审阅条目。其评分25帧与旧批准35帧不重叠，原[5262,5328)历史全留作热身/评分/后文，不把同一道馆路牌计成独立来源。护士两个共享空框原帧1922/2088汇总去重；三护士块、五开场块、博物馆块及相邻劲敌/博士块均登记共同互动，不冒充独立会话。
- 全部新增21条目（20对话事件/语段+原路牌续页）首读/二读/差异/布局/编码GT位于同一候选目录，各自`reports/m2-dialogue-expansion-v1/<id>-integrity-v1.json`实际CLI检查通过；全部是candidate，不批准、不冻结。独立两次源开头重解码的每一选片PNG、RGB、PTS、duration/time_base逐项一致，报告`dialogue-independent-extraction-v1.json`；不是用切片冒充第二次解码。
- 新身份比较脚本首跑因Int32键查询使用JSON Int64循环变量误报4549，未生成成功报告；直接取两份原核验确认一致，声明循环变量[int]后21项全部通过（1/3）。包裹笔记单行PowerShell数组展开失败1/3，用一元逗号保留行数组并重跑通过。完整失败/根因/修复验证见`dialogue-tool-recovery-v1.json`，不改原笔记/原帧/验收标准。
- 重复大拼图预览出现黑图、截断和重复显示；1905两次原PNG SHA及view_image返回数据均相同，2043原图与拼图对应格88800像素差异0，重开原图确认。保留`dialogue-preview-check-v1.json`，不把预览异常改成空GT；显示内部原因未诊断。窄格原预览和重看记录均保留。
- 四原件和旧3151文件最终再核SHA一致，`source-identity-v2.json`；新统一元数据`data/meta/all-scenes-review-v2.json`保留旧17项并追加21项，版本gt-all-scenes-v2，覆盖缺口沿用真实证据、未选范围待最终按四源总账本重算。新包正在生成，尚未正式提交；Java/被测OCR/中文均未改动或运行。

**对话扩充最终结果与统一提交 gt-all-scenes-v2（2026-10-15，等待明确批准）**：

- 正式入口 `reports/gt-review/gt-all-scenes-v2/index.html`，元数据`data/meta/all-scenes-review-v2.json`；原17项原字节保留，新增20个对话事件/自然语段+原道馆路牌后续页，共38审阅条目（37未批准候选+1已批准参考）。开场五语段、博物馆问答/费用、劲敌与博士回复、包裹交付、室内外NPC、中心护士及路牌增加文字与显示方式；同一次互动拆块只为标注组织，不是独立会话或独立验收集。没有声称穷尽四视频全部对话。
- 本轮新增1992评分帧引用，扣护士两个共享空框帧后为 **1990新增去重评分原帧、454连续状态、54完整页面机会**。总包3368评分帧引用，扣7重叠后 **3361评分原帧=3326未批准候选+35原批准参考，717连续状态、117完整页面机会**；重复页合并，无跨片文字/几何冲突。评分134.916228秒；全部热身/评分/后文3577帧引用、3507去重原帧/140.863596秒；四源仍66770帧/2779.397368秒。每原帧、加载、短页、等待、滚动/裁切、空态、清屏及PTS均保留。四源未评分/未选clip补集逐段见包内`summary.json`。
- 本轮21份真实CLI完整性检查`reports/m2-dialogue-expansion-v1/<id>-integrity-v1.json`，输入GT/clip/layout SHA均在报告；命令由`prepare-dialogue-event.ps1 -Id <id>`调用`VideoTool gt-check data/meta/m1-v1 <clip> <gt> <layout> <report>`，数据版本为每候选v1/评分契约all-scenes-v5。旧批准参考继续原contract-v1。独立重解码全2116原帧引用的PNG/RGB/整数PTS一致；包内`evidence/dialogue-independent-extraction-v1.json`含两次真实FFmpeg源开头命令与每帧身份，不把结构检查称作语义或OCR验收。
- 统一构建命令`build-all-scenes-review.ps1 -Batch data/meta/all-scenes-review-v2.json -ReviewDirectory reports/gt-review/gt-all-scenes-v2`；`node tools/check-all-scenes-review.cjs reports/gt-review/gt-all-scenes-v2`实际检查全部3577帧查看控件、717状态跳转、边界按钮、嵌入资料/GT版本/PNG身份及重叠/统计，差异0：包内`static-check.json`及外部`unified-review-static-check-v1.log`。原六种显示/转换与新增21项各首完整页最近邻放大像素逐个比较差异0：包内`zoom-pixel-check-v1.json`、`dialogue-zoom-pixel-check-v1.json`；实际查看新护士菜单及裁切滚动放大图。**这只是静态/逻辑/像素检查，实际浏览器呈现仍待验证**，未绕过既有file协议拒绝。
- 最终包清单不含自身 **7636文件/1379046562字节**，`bundle-manifest.json` SHA **`e51b7d9ebccae9e784f0b60a4a73a352b164e0d9807bd068afb2aad7c54b2dd3`**。逐文件最终SHA、38份GT/clip/layout版本、当前源快照、四原件、旧3151文件和35冻结封套复检全部相同：`reports/m2-dialogue-expansion-v1/final-dialogue-bundle-recheck-v1.json`。Java源/构建文件与前次35测试构建快照逐个相同，本轮未重跑或冒称重跑Java测试；本轮实际跑的是上述资料检查。`git diff --check`通过（既有LF/CRLF提示）；没有运行被测OCR/生产中文映射。
- 旧版gt-all-scenes-v1审阅请求由本版统一替代，旧包及SHA不改；新增候选冻结0帧。背包退出5359、未证实版本/独立会话、真实窗口/UI/时延/长时稳定性与所有未选时段继续待验证；不要求补录，不把用户批准当OCR通过。**正式提交本版后按spec §3.4暂停后续开发，等待用户对gt-all-scenes-v2/候选范围及评分契约v5明确批准；M2整体与M3–M7未通过。**

**安卓离线访问修订与验证（2026-10-16，仅审阅反馈范围；r2为中间检查版，最终入口r3见后文）**：

- 原仓库已由用户要求推送至GitHub `main`，本轮起点 `bcc3ca0278be994232f2196ad674fc95eeaa49a3`，起始工作区干净。用户随后报告安卓本地相对图片路径问题；原入口只能直接引用相对图片，不能据此承诺完整目录在安卓必然可用。未取得手机URI或实际浏览器日志，因此系统层根因保持未核实。
- 另建访问入口 `reports/gt-review/gt-all-scenes-v2-mobile-v1-r2/index.html`（附README与source-identity.json）。用户在新页面选择本地完整审阅ZIP或含原包的仓库ZIP，无需解压或重新下载原资料；页面仅通过File/Blob读取所选文件，读取ZIP目录及当前所需文件，不整包载入内存。支持普通存储/DEFLATE ZIP，明确拒绝分卷、加密、ZIP64、损坏/越界记录；DEFLATE依赖浏览器DecompressionStream支持，缺失时给出更新浏览器/使用存储ZIP的提示。图片、附件按原包清单核SHA；无crypto.subtle的本地上下文使用经对照验证的SHA-256实现。快速换帧先隐藏旧图，过期异步结果丢弃，替换时回收Blob URL。
- 实现来源 `tools/mobile-review-assets.js` SHA `be4dfcf07aeada2c16f33d12ad75f605db6fa56f6f854f3296ae3f304aaaaa9c`，构建器 `tools/build-mobile-review.cjs` SHA `658396acee7c9969d4497c13f2584b1e2cefbc5d719b8a7671e72d7fda5b2e61`；实际命令 `node tools/build-mobile-review.cjs reports/gt-review/gt-all-scenes-v2 reports/gt-review/gt-all-scenes-v2-mobile-v1-r2`。生成前复核原7636文件/1379046562字节及清单，源review-data SHA仍为 `f25b80f58df9eba218f5864150989108a8ec6a5f706e53d094cf47e754e800b4`；新HTML SHA `696821da5f12f4e0ae44ac03aeb6dfbd1b3f4973f9113dc7363a5e310e8bf9fe`。早期派生入口mobile-v1保留，最终r2增加明确hidden样式及使用说明；不覆盖原gt-all-scenes-v2。
- 检查器 `tools/check-mobile-review.cjs` SHA `f5763a8bb8eb1c387b212137b929d428f6a2ad9cb34ad21dce63ddb7ca4fc7d7`，最终命令 `node tools/check-mobile-review.cjs reports/gt-review/gt-all-scenes-v2-mobile-v1-r2 reports/m2-mobile-review-v1-r3 .cache/mobile-review-v1 .cache/mobile-review-v1/repository-layout-stored.zip`。完整真实ZIP夹具由原7636文件加原清单构造，7637条目、1382256776字节、仓库式根前缀；夹具只在忽略的.cache下，不随Git提交。普通DEFLATE/存储、UTF-8文件名、SHA/CRC对照及截断/损坏/错误版本/等长度篡改/越界等拒绝检查通过；全部38场景、3577帧控件、717状态跳转与原数据相同，76个场景图片样本及GT/缺口附件按字节核对。最快切换先清旧图、Blob URL最多8个，记录的单次档案读取最大1332808字节。结束后原7636文件逐项SHA复查一致。证据 `reports/m2-mobile-review-v1-r3/check.json`，只读访问逻辑不属于OCR或产品验收。
- 首跑失败保留 `reports/m2-mobile-review-v1/failure.json`：测试包装器将合法Blob.slice(start)的省略end误算为NaN并误报无界读取，生产读取实际只取ZIP末65557字节；测试包装器补end=file.size，修复 **1/3**。保持原ZIP不重建，受影响检查及完整同类/回归在r2报告通过；最终入口在r3报告再次通过。失败证据、早期派生HTML、原包均未删除或覆写。
- 发布前核查Git暂存字节时发现：原包363个文本文件在Git索引中已被CRLF→LF转换，均以Git blob身份确认只存在换行差异；直接按原文件长度拒绝会使旧GitHub ZIP无法连接。这是离线访问兼容问题，修复 **1/3**：构建器仅对已证明匹配Git索引的换行副本登记长度/SHA/CRLF位置；入口先核对传输副本SHA，再精确还原原字节，最后按原审阅清单SHA核验后显示，不接受任意规范化文字或改GT。新增`.gitattributes`只对新mobile入口HTML设置-text，保留派生文件自身哈希，不改变原审阅文件。
- **最终发布入口为 `reports/gt-review/gt-all-scenes-v2-mobile-v1-r3/index.html`**，SHA `f4b4ca1c7c680e94a6c1be8517a7919e5df12ed398443112db76379ef1749b9a`，附`git-transport.json`列出上述363个已证明的传输副本及恢复信息。当前构建器SHA `c323588fa343bd08403c8eaac803b6b13aa202e536550be525aea2586cadccfa`、runtime SHA `413d4e62ecac711fd6808e62edee8915f7f702ea12fc2edea5d88996973b3acf`、检查器SHA `a79554a151541952b9ecbb40649f1796a75fcc7804b16df634080951b9a139c4`。实际构建 `node tools/build-mobile-review.cjs reports/gt-review/gt-all-scenes-v2 reports/gt-review/gt-all-scenes-v2-mobile-v1-r3`；检查 `node tools/check-mobile-review.cjs reports/gt-review/gt-all-scenes-v2-mobile-v1-r3 reports/m2-mobile-review-v1-r4 .cache/mobile-review-v1 .cache/mobile-review-v1/repository-layout-stored.zip`。r4报告中完整重跑前述10类检查；另构造正常ZIP更新字节流，363项用Git中的LF副本及普通DEFLATE，其余使用原ZIP文件数据，全部363项精确恢复到原文件SHA/字节，其他范围/控件与原7636文件复检通过。中间r2入口/报告保留，不再作为用户入口。
- 新入口是原审阅版本的访问附件，原GT/原帧/边界/会话/契约/缺口及批准对象不变，新增冻结0帧。没有运行Java构建、被测OCR、翻译或后续开发；没有绕过此前内置浏览器file协议拒绝。**实际浏览器及安卓真机呈现仍待验证**；不能将Node内的DOM桩控件检查称为安卓或真实UI通过。等待gt-all-scenes-v2明确批准的卡点保持。

**上轮恢复指令（现为历史）**：安卓入口r3的访问反馈已由本轮明确批准解除；其原文件与审批对象保持，不再要求重复批准gt-all-scenes-v2。当前唯一下一步见以下最新记录。

### gt-all-scenes-v2获批、M2基础实现与单帧元数据修订统一提交（2026-10-16）

**批准、身份复检和冻结**：先依次完整重读spec/PLANS/本计划，起始工作区干净。用户原话“gt审核通过，请按项目计划指引，继续执行”，执行批准复检期间又明确“批准 gt-all-scenes-v2 的拟冻结候选范围及评分契约 v5”；两条依据分别保存于`reports/gt-approval-all-scenes-v2/{approval,explicit-confirmation}.json`，不是用户逐帧核验。实际命令：

```powershell
node tools/freeze-reviewed-gt.cjs reports/gt-approval-all-scenes-v2/approval.json reports/gt-review/gt-all-scenes-v2 data/gt/frozen/ocr-all-scenes-v2 reports/gt-approval-all-scenes-v2/recheck-v1
```

原7636文件/1379046562字节、四原件及各自全量账本、38项当前GT/clip/layout/契约和3577原PNG引用SHA均与批准材料一致；38次真实Java `gt-check`重新执行，命令/日志/逐项报告保存在`recheck-v1/`。身份总报告SHA `3f41f2e126d518ab6f4721bb96cf96919352d7ee29c3d3109c69d7391780ce8e`。37个新增候选逐字节复制后创建冻结封套`data/gt/frozen/ocr-all-scenes-v2/freeze.json`，SHA **`cf6cffeeefb4b94b5c519193effaa72db4dac3195e32ee9481bfd47541b36bc6`**；原35帧引用旧封套和契约v1。合并范围仍为3361去重评分原帧/717原状态区间/117页面机会，未增删分母。payload中的历史candidate状态原样保留，冻结权威来自封套。**以下跨片缺陷是冻结后补齐评分工具时发现；这次冻结步骤未做精确字段/顺序跨片检查，不可据其逐片结构成功宣称M2完整性已通过。** 原封套与批准不被回写或伪装为修正后数据。

**M2实现与验证**：新增验证器`FrozenDataset`验证冻结payload/旧批准引用、`ReplayTimeline`逐原PTS传帧、`OcrScoring`和`OcrComparison`独立评分、`ComparisonAggregate`原帧/重叠页面去重。验证模块现在单向依赖app的不可变`SourceFrame`，app仍不依赖验证模块/Gson/GT；消费边界只含RGB24、宽高、整数PTS及有理time_base，clip/帧号/集合/GT/冻结几何留在驱动。实际OCR未实现/运行；当前传帧sink仅计数。

计分先于被测OCR固定实现：Unicode标量最小编辑距离保留大小假名、空格、浊点、数字；按角色+基字格起点+readingOrder配对，拒绝重复键、错误源PTS/重复帧和无依据修正；缺输出记删除并使空态也失败，额外文字记插入/空画面误报。字格占位、源矩形、renderUnits、上方标记、光标/多箭头/孤立浊点及kind分别检查。每帧与状态内除帧数后的等权口径均报告；完整页内容记录所有完整帧是否一致，**这不是UI时延/整页验收通过**。没有独立UI呈现观测时始终pending。重叠原帧最多取错误数、所有重复运行的内容/几何/状态均正确才算正确，不能择优掩盖失败；重叠的状态/页面用源帧连接去重，不合并再次出现的其他页面。

实际执行：`mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' test`，最终日志`reports/gt-approval-all-scenes-v2/maven-test-m2-final-v1.log`，**app4+verification49=53测试，失败/错误/跳过0**。`node tools/check-m2-foundation.cjs data/gt/frozen/ocr-all-scenes-v2 .cache/m2-foundation-fixtures-v3 reports/m2-foundation-fixtures-v3`完成全部38项3368评分帧引用的逐片评分器夹具、单帧1450漏输出、空态误报、重复区域/原帧、错PTS及无依据修正拒绝；其actual文件是明确标记的GT复制夹具，不是主程序自标注或实机OCR结果。全片合计被正确拒绝，因为下面的一帧元数据冲突仍存在；不以夹具的零编辑错误掩盖它。

`./tools/validate.ps1 replay-audit data/gt/frozen/ocr-all-scenes-v2 all reports/m2-replay-all-scenes-v2-unpaced-v2 unpaced`检查全部38条目、3577原帧引用/3507去重保留原帧、3368评分帧引用/3361去重评分原帧。逐片warmup/评分/后文全留，全部RGB、整数PTS、duration/time_base与最初实现`unpaced-v1`逐项一致，差异0；未重采样、跳帧或拉长短页。`all`按媒体/原起点排序，各片重置后使用本片真实warmup；**完整会话不中断的运行与两小时稳定性未实现/验收**。

三个实时传帧检查（均为相同CLI的`realtime`模式，无OCR/UI）：商店退出49原帧源时长2.041667s/墙钟2.045713s/最大传递迟到15.699ms，技能回合112原帧5.894737s/5.905125s/15.490ms，探索94原帧3.133333s/3.135943s/15.979ms，完整timeline/report各在`reports/m2-replay-{short-page-realtime-v2,skills-realtime-v1,exploration-realtime-v1}/`。**这些是输入传递的测量，不能作实际UI的≤200ms证据。** 首个商店实现逐像素三次digest.update并重复取像素，2.041667s源被处理成4.778104s、迟到2.777274s，失败`short-page-realtime-v1`保留。修复 **1/3**：批量读取RGB、一次散列、首帧解码准备后启动源时钟；字节序/原哈希测试、同类两尺度/不同PTS、全38条目原身份回归均通过，不删除任何帧或改动PTS。

**精确评分暴露的GT冲突与根因**：全7个重叠原帧只有`video-03:1439`冲突，`reports/gt-approval-all-scenes-v2/strict-overlap-metadata-v1.json`保存两份完整注释。`shop-rejection`最后状态[1439,1440)使用`list_removed_parent_remains`、菜单优先阅读序、menu_label/money_label/money；既有`shop-exit`首状态[1439,1441)使用`textbox_empty`、画面从上到下的顺序、shop_menu_choice/shop_money_label/money_amount。文字/字格/原矩形/数值完全相同；旧审阅包的`annotation-descriptor-equivalence-v1.json`明确只在源画面分母去重时忽略字段命名，不能当成评分时可按片段重命名actual的许可。旧逐片校验和去重签名没有查这些语义元数据，新精确合计在任何OCR实现前拒绝，日志`reports/m2-foundation-fixtures-v2/command-0.log`及旧夹具/逐帧报告全部保留。现在`tools/check-gt-overlaps.cjs`及冻结脚本将精确角色/顺序/kind/完整页冲突作为冻结前拒绝项；仅沿用原批准的同一轮廓三角拼写等价，不改实际文字或字段答案。

**修订尝试1/3（未批准/未冻结）**：从原video-03开头分别连续重解码[1406,1488)82原帧两次；新`data/frames/video-03/shop-metadata-pass{1,2}-v1/`各RGB/PNG/PTS与总账本一致，82 PNG两次SHA相同，命令及逐帧身份`reports/gt-approval-all-scenes-v2/shop-metadata-independent-extraction.json`。当前代理两遍查看全部82帧有序游戏区原图，第二遍另直接打开完整1439；笔记`shop-metadata-pass1.json/pass2.json`及拼图保留。1438列表仍在/底框清空，1439列表消失且底框空，1440保持，1441开始下一提示；不借OCR、模型或模拟器。此为代理对已双遍事件的元数据复核，不冒称用户逐帧核验。

`tools/prepare-shop-metadata-amendment.cjs`只在新的`data/gt/candidates/ocr-shop-metadata-v1/shop-rejection/ocr-candidate-v2.jsonl`中，将[1439,1440)元数据统一为已批准shop-exit同帧描述：kind、role/readingOrder、字段注释表示和光标描述结构。其他14状态逐对象相同；所有字面文字/数值、字形标记、源/格坐标、帧/PTS边界、完整页、评分/热身/后文均未改，精确差异`pass-differences-v2.json`。GT SHA **`2c44d8e75b2df9b2f10400a05ade9ed596e0f11202fa2fe7632b821d235e05b8`**，clip SHA **`59fb65440ddf09e0a5273bd0420cd811d65f0948dbc878f59939afb62016051c`**，layout/契约v5仍原字节。真实CLI `gt-check`重新通过32帧/15状态/2页，报告`reports/m2-shop-metadata-amendment-v1/shop-rejection-integrity.json`。原38项叠加该候选的严格检查`effective-overlap-check.json`：7重叠/冲突0；这里只检查候选，不把新字节写入旧封套。

**全受影响商店场景已合批，正式提交 `gt-all-scenes-v2-shop-metadata-r2`**：

- 完整入口 `reports/gt-review/gt-all-scenes-v2-shop-metadata-r2/index.html`，变更对照 `change-overview.html`内嵌1438/1439/1440完整原图，安卓无需相对图片路径即可看改动。元数据 `data/meta/shop-metadata-review-v2.json`；原四商店事件全留：1个修订候选32评分帧+3个原批准参考149帧（1439重叠一次），合计**180去重评分原帧、54状态、12页面机会、195热身/评分/后文原帧引用、192去重保留帧**。只有1帧的元数据有实质变化；其他34个原条目及契约v5/旧35帧批准保持，未要求重审未修改的全部对话。原R1和中间访问附件保留，不作当前请求。
- `build-all-scenes-review.ps1 -Batch data/meta/shop-metadata-review-v2.json -ReviewDirectory reports/gt-review/gt-all-scenes-v2-shop-metadata-r2`，随后`node tools/build-shop-metadata-overview.cjs <同目录>`及`node tools/check-all-scenes-review.cjs <同目录>`。195原帧/54状态控件、395原图/最近邻图PNG身份/尺寸、嵌入GT和重叠图文一致检查通过；summary/status明确候选，未运行被测管线。新包清单不含自身 **475文件/81426619字节**，SHA **`3f800da7ce58e00a3b0ca5e4d443beeb0629d3d87f40118f43c22100f452241e`**；变更对照HTML SHA `d5c987df7b925aefc8b0f37516bb17c228a7725050e197579c5bdc7b06cec348`。实际浏览器/安卓呈现仍待验证，不绕过先前file协议拒绝。
- 手机完整查看附件 `reports/gt-review/gt-all-scenes-v2-shop-metadata-r2-mobile-r2/index.html`，HTML SHA `70bb13330555b0a71d0ac3ae9d48ab101d090b784c17cae30f529a0193dfab4d`；选择本次小包ZIP `.cache/shop-metadata-review-zip-v1/repository-layout-stored.zip`（SHA `03a5bbd2be0cf7bd6497c83285f185230b6f8148b1d0cd869b82513fa41e5b9e`）。构建器/检查器改为使用包版本/真实统计数，不要求未提交本地资料已经进Git；不声称存在未登记的Git换行变体。新475文件/4事件/195帧/54状态/8图片样本及GT/缺口附件、损坏拒绝和有界ZIP读取检查通过，`mobile-check-r2/check.json`。同样完整回归原r3入口及1.382GB老ZIP，原38/3577/717、363个已证明换行副本精确恢复及原7636文件不变，`original-mobile-regression/check.json`。这只是ZIP与DOM桩逻辑，不称安卓实机通过。
- `.gitattributes`只增加本轮新冻结/候选/证据/修订包的`-text`，防止未来Git传输改变冻结哈希；不修改旧批准包或把既有CRLF差异恢复当成新GT。没有提交或推送Git。

**最终检查与版本**：`node tools/check-m2-delivery.cjs reports/m2-shop-metadata-amendment-v1/final-check-v1`重新核对原7636文件、新475文件、四原件/账本、全部冻结payload、两个传帧实现全3577帧身份、53测试和夹具的预期冲突拒绝。报告SHA **`6a6acad5299caddd6efe7e49d2d07d0e5228af602d0deed07d9f52ae0f318391`**；代码/工具源快照SHA **`8382b135c18888de4f9a6d8e67c730f49da129d8314dc7ee6624ac180d002aff`**，类/构建身份SHA **`f7e4a5c1b705be5bdfa620a230062533de8a50341f156372f8c6ee2d21e1569b`**，均在该目录。修订冻结0帧；M2整体因原精确元数据冲突未通过，M3/OCR/M5未启动。原四源仍66770帧/2779.397368s，原选范围仍3361评分帧/134.916228s，未选范围/版本/独立会话/背包遮挡/真实窗口/UI/时延/两小时稳定性全部维持待验证，不要求新录像。

**人工卡点现在生效**：本次正式提交上述商店场景修订包和清单SHA，申请仅批准`gt-all-scenes-v2-shop-metadata-r2`的单帧元数据修订，契约v5保持。按spec §3.4“拟冻结的扩充及实质修订也在其全部受影响场景处理完成后统一重新提交，旧批准不能覆盖新增或修订范围”；此处属于字段/状态的实质修订，不是重复索取原v2批准。现在暂停全部后续开发/GT冻结/被测OCR执行，仅响应本修订材料反馈及相关检查。

**上轮下一步（本轮已执行，历史）**：收到用户对`gt-all-scenes-v2-shop-metadata-r2`明确批准后，记录依据，复检/冻结32帧修订并与原38项组合。实际命令、身份与完成范围见以下最新记录；不再等待该版批准。

### 修订获批冻结、M2有效开发基准复检与M3首批实现（2026-10-16）

**恢复与批准依据**：本轮再次依次完整读取spec、PLANS、本计划全部内容；起始改动为上轮未提交成果，没有撤销用户改动。用户原话 **“批准gt-all-scenes-v2-shop-metadata-r2修订”**，日期2026-10-16/Asia/Shanghai；保存`reports/gt-approval-shop-metadata-r2/approval.json`，绑定原475文件清单SHA `3f800da7ce58e00a3b0ca5e4d443beeb0629d3d87f40118f43c22100f452241e`。这不是用户逐帧核验或OCR通过，也没有再次请求确认。

实际执行上轮恢复命令`node tools/freeze-reviewed-gt.cjs reports/gt-approval-shop-metadata-r2/approval.json reports/gt-review/gt-all-scenes-v2-shop-metadata-r2 data/gt/frozen/ocr-shop-metadata-r2 reports/gt-approval-shop-metadata-r2/recheck-v1`：475文件、四原件/各自全量账本、195原PNG引用身份、四商店事件GT/clip/layout/契约及严格字段/顺序/kind重叠检查全部一致。四次Java `gt-check`真实重跑，命令/日志/报告保留。仅新冻结shop-rejection修订32帧，其他149帧为原批准参考；封套 **`data/gt/frozen/ocr-shop-metadata-r2/freeze.json` SHA `141777e5b8c357239135450bdedd2adf43d9dd5ca35f1914f956a9f23422f016`**，批准复检身份报告SHA `1dc91de23260e241c359102f65018b3af4159351c678e2671705d58585fb5788`。原GT/包/封套/契约v1与v5不变，上一问题修订尝试仍累计1/3并已生效，未删除1439或改actual答案。

**M2有效集合与完整复检**：`node tools/compose-approved-gt.cjs data/gt/frozen/ocr-all-scenes-v2 data/gt/frozen/ocr-shop-metadata-r2 shop-rejection data/gt/frozen/ocr-all-scenes-v2-effective-r2`生成只引用批准封套的有效集合，不创造新的人工批准或改写任何payload。组合要求来源/原范围/GT状态边界/完整页/字面与图形几何/契约保持；只有批准的1439元数据变化，原34个其他场景条目及3个其他商店参考不改。有效封套SHA **`748087f7049d657ada68e634fda8b21dc37674257769bc559e79fff684dbb246`**。`FrozenDataset`加载时再次核对父封套、payload路径/哈希、原分母、范围、重复ID及全体重叠；帧消费者仍只获得`SourceFrame`。复核程序不能以候选GT路径代替批准来源。

执行`node tools/check-effective-gt.cjs data/gt/frozen/ocr-all-scenes-v2-effective-r2 reports/m2-effective-r2-integrity-v1`，全部38项重新逐片结构检查，未知/冲突/漏帧/时间缺口0，7处精确重叠均一致，报告SHA **`f58c5f6c219981d83638e2ce9bbddb5ba9aaab3c00b1a9c0ff0414ffa748e3de`**。执行`node tools/check-m2-foundation.cjs data/gt/frozen/ocr-all-scenes-v2-effective-r2 .cache/m2-effective-r2-fixtures-v1 reports/m2-effective-r2-fixtures-v1`，全范围评分器夹具及缺单帧短页、空态误报、重复区域/帧、错PTS、无修正依据等检查通过。**所有fixture实际值均显式复制GT用来检查评分器，不能称实机OCR输出/100%识别率。** GT自身分母：逐帧86602字符、状态等权15416字符、692空文字原帧；717原区间引用，重叠合并后712连续状态、117页面机会。字符/字段/位置/状态/符号评分规则先于被测OCR保持，不以成绩修改。

执行`./tools/validate.ps1 replay-audit data/gt/frozen/ocr-all-scenes-v2-effective-r2 all reports/m2-effective-r2-replay-v1 unpaced`；全部3577热身/评分/后文原帧引用（3507去重保留原帧）、3368评分引用（3361去重原帧）RGB/整数PTS/duration/time_base/计分标记/源偏移与上一无修订回放逐项相同，差异0。没有拉长短页或删帧；仍为逐片真实warmup、各片重置的传帧回放，无产品OCR消费。旧错误封套/失败最小复现和全部录像未选范围保持。`check-collection-rejections.cjs`另造4个损坏副本（父哈希错误、未知替换ID、少原条目、payload来源错配），全部在消费者收到帧前退出3且没有成功报告，`reports/m2-effective-r2-collection-negative-v1/report.json`。这些是明确的工具负向测试，不是实机失败素材。

**M2结论边界**：上述已批准/冻结的开发范围100%保帧/时间/可见状态覆盖与回放/比较基础通过，具备M3开发前置。四源仍66770原帧/2779.397368s，评分3361原帧/134.916228s，117页；没有增加独立来源。真实会话关系/未用调参的独立验收集合仍不能证实，M1/M2相关维度及M4继续待验证。按spec允许继续开发/回归，**不标独立验收或产品全部通过，不要求补录，M5不启动**。

**M3验证约定与首批实现**：先审计固定日版来源字库/编码/打印路径，再做任何完整录像OCR；不取旧工程算法、其他模型或中文答案。新增`GlyphTemplate`存8x8原字形及全部源别名；`PixelGlyphMatcher`按64位像素差返回所有候选和距离，精确保留并列/同形假名，不默认取第一项。没有源码全文索引纠错、变量替换或中文翻译。验证侧`FontReferenceAudit`只读固定来源PNG/ASM和Git对象，不读取GT/视频或执行参考仓库脚本；将源PNG与图块掩码留在忽略的.cache。完整视口、区域/行检测、源RGB阴影/阈值、清浊点合成输出、紧凑字段解释和可见状态管线仍待实现，不能称日文OCR已通过。

**固定来源与实读范围**（manifest `resources/japanese-font-sources-v2.json` SHA **`39e42b896e44dbd13819391c7a829db87a5b2cbb7172c321e5c29c2124db9da3`**）：

| 来源 | 固定提交 | 用途与边界 |
| --- | --- | --- |
| Narishma-gb/pokegreen | `953f41b34108621b2bf13c3b1e53abfc9c3e5aec` | 日版红/绿字库与渲染来源；不作为四录像版本/会话证明 |
| Narishma-gb/pokeblue | `69cd1814203dd24533c46af383d7cff5591aea90` | 日版蓝字库及控制差异 |
| Narishma-gb/pokeyellow-jp | `f282e72ae26232790fdb780aa5a5db7ec8ebf572` | 日版皮卡丘字库及控制差异 |

先通过公开仓库页面核实来源，再只读clone到`.cache/reference-sources/*-v1`并固定HEAD。初次3个Git克隆均被Windows Schannel报`SEC_E_NO_CREDENTIALS`拒绝，原日志保留；恢复尝试 **1/3**：逐命令`git -c http.sslBackend=openssl`成功，TLS证书核验未关闭，未改全局配置。只稀疏检出所需来源目录；不运行Makefile、工具脚本、不构建ROM、不使用模拟器。每源22个明确文件逐一与`git show <commit>:<file>`原字节比较，二进制精确相同；文本仅容许并登记已证明的CRLF工作副本差异。Git blob、仓库/工作副本SHA、固定URL、图块几何/灰度调色板和候选保留自检均在审计报告。来源图像与掩码不进app资源。

实际命令`./tools/validate.ps1 font-audit resources/japanese-font-sources-v2.json reports/m3-font-audit-v1/source-audit-v2 .cache/m3-japanese-glyph-banks-v2`，三份来源各 **197个实际装载图块/50个清浊音字面组合**。报告SHA **`7dbead23d979e368afde384493177d9d9c05fbbf9c39fcd82461ff5172f9c2a3`**。已确认的规则/直接证据及未实现部分在`reports/m3-font-audit-v1/rendering-findings.json`：标记在基字格上方一行，字节含义依字符串/命令上下文变化，同一图块码会被不同profile覆盖；`へ/ヘ`和`り/リ`共形，不能靠像素选唯一字符。普通128主字形掩码相同不代表全部图块相同：红绿与蓝/黄在textbox/battle额外库的4个边角图块（每profile的0x79/7b/7d/7e，共8条）不同；蓝/黄本次图块掩码相同。蓝/黄增加`GA 0x4a`和`DEXEND 0x5f`控制及不同声效分派；这些是日版源码规则，未用于补写GT。

**源图块装载长度问题，修复1/3**：首轮审计按完整PNG条带列出每源200块（保留`source-audit-v1`与`.cache/m3-japanese-glyph-banks-v1`），随后对照`StatusScreen`真实复制指令发现HUD2仅复制3块图像中的1块、HUD3仅复制3块中的2块。新增manifest-v2明确装载数，审计拒绝越过源图像的复制范围，并增加长度测试；重跑全部3源、原图身份/所有别名与清浊音算术、精确并列候选回归通过，每源修为197块。没有被测录像OCR成绩，更没有因此修改GT。v1是可追溯的来源范围假设，不作为当前字库。

**许可/构建边界**：三份完整固定Git树未找到LICENSE/COPYING/NOTICE命名文件，README给出pret来源结构鸣谢；不据此推定任何再发行权。`resources/dependencies.json`记录新增来源审计，仍保留M0依赖许可的原证据。所有外部字形/源码与派生像素只在忽略的.cache中；最终app JAR条目检查无PNG/1bpp/2bpp/ROM/视频/GT/验证器/Gson数据，不能把该开发包宣称为已完成可识别产品。没有获取中文候选库或建设生产中文映射。

**实际构建/回归/最终版本**：`mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' test`最终**app10+verification59=69测试，失败/错误/跳过0**，`reports/m3-font-audit-v1/maven-test-final-v1.log`；`mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' -DskipTests package`构建成功，`maven-package-final-v1.log`，此步未冒称再跑测试。`node tools/check-effective-stage-delivery.cjs reports/m3-font-audit-v1/final-check-v2`独立重读4原件/账本与批准payload、38项报告、3577回放身份/完整分母、字库本地银行哈希与来源版本差异、69测试XML、app JAR排除项，均通过。总报告SHA **`49173841e07752df0da0bae3e6bac1547cbbf048156029e1fe05ab21c0a0b21f`**；源快照SHA **`9415993c1dae8083e1aa23e7d2a40fb99572456f4dff7c7b6297e61806b38f5e`**，类/构建身份SHA **`76f7022bc8defbb87eec6e42e87f41cb71dfc00ebbd13452e1ed743b43ff05a9`**。新冻结/证据目录以.gitattributes保字节；未修改旧审阅包、原视频或旧冻结封套，未提交或推送Git。最终Git空白检查另存本轮日志。

**上轮下一步（本轮已执行，历史）**：批准已落实，无待审GT；M2已冻结开发范围可用，M3只完成上述来源/字形候选子项，整体仍未完成。下一步在`app/src/main/java/io/github/g1ocr/ocr/GameViewportLocator.java`实现仅接受`SourceFrame`像素/尺寸/时间和正常配置的160×144视口及像素网格定位；以有效冻结集合的两种轴向缩放、攻击晃动、空/黑帧连续原帧由独立驱动核对，输出新`reports/m3-viewport-v1/`报告，产品组件不得读layout/GT/clip标签。此处是上轮恢复位置；本轮真实实现/失败与当前下一步见以下最新记录。

### 视口候选实现、四次完整原帧运行与失败保留（2026-10-16）

**授权/输入与验证约定**：用户“执行下一步”；按顺序完整重读spec/PLANS/本计划并读根AGENTS，未继承旧工程代码。起始工作区为此前未提交成果，没有重置。使用唯一有效批准集合`ocr-all-scenes-v2-effective-r2`，封套SHA仍 **`748087f7049d657ada68e634fda8b21dc37674257769bc559e79fff684dbb246`**，基础/商店修订封套仍分别`cf6cffee…b36bc6` / `141777e5…22f016`（完整值见上轮记录）。全部38项、3577保留原帧引用/3507去重原帧、3368评分引用/3361去重评分原帧和117页保持。四源媒体/各完整账本再次重算SHA相同，仍66770原帧/2779.397368s，评分134.916228s，未选/未证明会话/版本和原覆盖缺口不变。

先约定纯像素定位消费边界、原PTS全帧传递、关闭实际输出后再独立比较。对有冻结网格的每个评分原帧，检查全部可见逻辑像素边界的起点floor/终点ceil，并应用该帧GT中的原晃动偏移；差1源像素也不记精确，不仅对外框。300个非目标评分帧没有GT网格断言，单列、不凑几何成功。空/清屏帧仍保留；其中无可观测网格的NO_GRID只表示本次严格几何检查未能定位，不凭此捏造文字OCR删除错误。整个工具不读/输出任何日文文字，**本轮没有字符准确率或整页OCR成绩**。

**实际实现**：`GameViewportLocator.java`仅接受`SourceFrame`和通用Config，输出信号/无网格/歧义/定位候选状态、调色板包围、双轴尺度、原点、梯度周期强度及半开投影。采用有限6个背景色种子、RGB/色度相容连通与行列支持、区域内单色一致性、X/Y独立像素梯度周期搜索；默认容差22、尺度1–16、输入不超过4096×2160、无文件读取/文本索引/GT依赖，不按源ID/帧号/场景硬编码。它是候选估计，不是经过验收的校准。尚无时间配准/整逻辑像素晃动恢复或完整文字OCR。

验证侧新增`ViewportCheck`及实际CLI：

```powershell
mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' test
./tools/validate.ps1 viewport-check data/gt/frozen/ocr-all-scenes-v2-effective-r2 all reports/m3-viewport-v1/attempt-4
mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' -DskipTests package
node tools/check-viewport-delivery.cjs reports/m3-viewport-v1/attempt-4 reports/m3-viewport-v1/final-check-v1
```

上述都是已执行命令；工具/测试输出目录不可覆盖，复现时改为不存在的新目录。每条目`*-actual.jsonl`保存每一原帧候选/参数/耗时，`*-timeline.jsonl`留原RGB/整数PTS/duration/time_base，`*-comparison.jsonl`逐帧关联冻结状态/严格差异、原图及前后原图路径；失败也全留。消费者只见RGB/尺寸/时间，驱动的源身份/clip/GT不跨入产品。按片使用真实warmup、全部评分及后文，unpaced运行；不称完整不中断会话或实际UI时延验证。

**失败、修复与预算**：`repair-evidence.json`绑定最小复现、改动和回归；四次完整运行的Java源副本/原输出/日志均保留，均因几何不一致退出3。

- 背景断连单独问题 **1/3**：最初仅连通背景，被深笔画分割的合成测试失败；增加行列支持，保留同一失败图性质测试，73测试通过后才跑原帧。首失败`maven-test-attempt-1.log`，恢复`maven-test-attempt-2.log`。
- 原帧包围提取问题，首`attempt-1`误把较大彩色机身当视口：**修复1/3**检查区域单色调；`attempt-2`已有457个评分引用精确、未定位1063，但暗色调被误拒。**修复2/3**允许暗阶保留色度偏移，`attempt-3`仍遗漏不同亮度的边缘。**修复3/3**将相容非黑色阶共同用于连通和行列支持，博士/PC/商店代表失败帧重新定位并完整回归到`attempt-4`。未改GT/选段/分母，未增加其他输入。剩余包围失败已到3/3，**该问题局部阻塞，不再继续调同类阈值**。
- 仍失败的包围最小复现：`video-03:1592`，原PTS815104/1⁄12288，RGB SHA `86a4629cca358699a0362bbf59ab9b21f1cedac2ec0ff3ad40eb07a0c01fc958`，`data/frames/video-03/dialogue-league-centre-npc-reviewed-v1/frame-001592.png`及1591/1593完整原图，actual返回空包围NO_GRID。整个142帧事件/其前后文全部仍在，不能称只有空帧失败。恢复如需复现可用现有CLI的`league-centre-npc`选择和新报告目录；未经额外授权不增加该同类问题的修复次数。
- 独立的像素尺度/原点/相位拟合问题尚未做有证据的修复（**0/3**）：已找到包围仍不等于确定标准网格。例`video-02:6916`候选原点301/109、Y尺度约2.270833与冻结几何不符，误差4；非整数场景会随可见边缘变化漂移。晃动3帧仍未恢复逻辑偏移，skills最大8像素。不得据这些输出反改GT。可转做该独立问题，不宣称已解决包围阻塞。
- 一次离线探针未创建javac输出目录，未运行成功；新目录后重跑，日志`monochrome-repair-samples-v1/v2.log`。这不是录像错误，也不冒称首条探针成功。

**最终真实几何结果（失败）**：`attempt-4/report.json` SHA **`6fcfc9c40469f0cb1866284bb4953d7be6f551aa4cd795a883779d1997e886fa`**。有冻结网格3068评分引用/3061去重原帧，精确686，未定位147引用；已定位仍错误的2235引用，最大边界误差14源像素。两倍组694引用中686精确/8无网格，其余三组921/1163/290引用均未全边界吻合（各自最大4/14/8）；不是OCR字符错误率。55个精确RGB全黑原帧全部返回NO_SIGNAL，未以非黑帧NO_SIGNAL代替精确黑帧分母。非目标GT不指定网格，不能声称这300帧已通过非目标文字零误报。

**构建与独立证据复核**：最终`maven-test-final-v2.log`为**app18+verification62=80测试，失败/错误/跳过0**，包含原评分/冻结/时间/字形回归、背景/多阶/机身/黑/空态性质测试和独立有理边界/偏移比较测试；合成输入仅单测。`maven-package-final-v2.log`的skipTests package成功，本步没有重跑测试。产品JAR SHA **`ec787befa40ab57e1b332becb2ef7ad843b91f549da4f4696d1102c55e1eab12`**，仍不含游戏图形/视频/GT/验证器/Gson，仍不是可交付的完整识别工具。

`check-viewport-delivery.cjs`用独立JS重新计算所有边界误差/统计，逐项比较全部3577条源时间/RGB身份与上一M2回放，差异0；重复帧结果一致，所有批准payload/四原件/账本不变。该检查退出0只说明失败报告可复核，报告明确`geometry_gate_failed`。总报告`final-check-v1/report.json` SHA **`b027fb98e7ce3d23f593d8510c4d0ec7963c9ed743c1d0fbd3a78afb60e9b34b`**，代码源快照SHA **`2cd3fb666692de75aa2f3583ee9a998d1f97c983e3b76774aea14c5574ebf2a3`**，类/构建身份SHA **`0f416d4361ba67407ecb598958b594990ac86e6d93956ae01526cb3722a02581`**。逐类失败例在`failure-examples.json`，全量仍在逐帧comparison。单次定位耗时P50/P95/P99/最大约37.675/59.481/71.039/163.702ms，仅算法观测，不是源传递或实际UI时延。本轮未触及GT、旧包、录像或中文映射，未提交/推送Git。

**当前结论与唯一下一步**：实现/构建/保帧验证已做，自动视口/网格整体未通过；包围提取问题3/3局部阻塞，GT无新卡点。下一步新建`app/src/main/java/io/github/g1ocr/ocr/RasterGridRefiner.java`（**计划文件/尚未实现**），在已由当前定位器提供的包围内，使用正常RGB像素和梯度轮廓实现受约束的双轴尺度及相位拟合，替代“最高周期峰后居中”的原点估计；不得读取GT/视频身份或按片段硬设尺度。前置为有效冻结/本轮source身份不变，包围提取阈值保持，像素尺度问题从0/3继续，保留未定位帧为失败。先以本报告`video-02:6916`、两倍已通过组和两种非整数几何作为开发/同类回归，再用现有`viewport-check ... all <新目录>`完整跑回同一38项，报告新`reports/m3-grid-refinement-v1/`；预期产物为新拟合器、候选/不确定性参数及全部原帧差异。验证先跑既有Maven test，任何改进不改GT或宣称独立验收；完整文字OCR及M4/M5仍未通过/未启动。独立会话、未选时段/版本、背包遮挡、真实窗口/UI/两小时稳定性继续待验证，不要求补录。

### 持续OCR开发：网格、字格诊断与首次完整评分（2026-10-16）

用户明确要求“请持续执行任务直到像素文字日文识别准确率达标”，已建立本线程持续目标。上段下一步现为历史：`RasterGridRefiner`已实现，保留双轴前三个周期/相位假设；四代表事件`initial-{battle-result,approved-trial,skills-turn,exploration-npc}`仅两倍探索严格几何通过，其他均保留失败。非整数视频中的实际采样尺度与冻结名义投影仍有差异，不按OCR结果改GT或评分。

新增`PixelGlyphReader`仅从当前RGB按8×8中心采样、局部Otsu和固定源字形Hamming距离产生全部并列候选；上行清浊点同帧组合，裁切/无格不补字。`KanaScriptResolver`只在当前行同一连续假名词段内，用距离不大于2的最近明确平/片假名区分同形り/リ、へ/ヘ；冲突/无锚仍保留歧义，不读取消息词库。`BoxTextDetector`使用实际边角/边线检测框，分离箭头/孤立标记，保留内部空格与未知格。`FrameGeometryEstimator`在原定位器失败时使用黑侧边和屏幕高宽比；包围修复累计4/5的授权/原因见Decision Log，原3次失败不清零。累计5修复游戏内暗天花板使信号包围短于全高的问题。仍无时间晃动配准，原严格几何失败保留。

实际命令`./tools/validate.ps1 glyph-audit data/gt/frozen/ocr-all-scenes-v2-effective-r2 all reports/m3-grid-refinement-v1/glyph-all-v1`完成全部3577保留引用。原字格诊断86602字符中82639普通等宽格可比较、3963紧凑/组合字段不支持；75911精确、2230歧义，普通格诊断91.8586%，报告SHA `134d8133c210b71c80765fce4b0483cdc66a6ed064d4ee2bbbcaa1a766d6a477`。未分类格185590包括边框/光标/背景，不能当正式插入错误或忽略为验收通过。增强命令`glyph-context-audit ... all reports/m3-grid-refinement-v1/glyph-context-all-v1`普通格精确78129/82639=94.5425%，3963不支持仍明列，报告SHA `7ab1dd8aa68ea28fd8d4156afc0aa4a3ef671e634aeee4e9c2f9e436173b4004`。两次均为raw_8x8_cell_diagnostic_not_contract_OCR_acceptance，不能与spec准确率混写。

当前实现`JapaneseOcrEngine`及`ocr-replay`已串起真实SourceFrame→网格→字格→主文本框→原始区域/可见状态。引擎只见RGB/尺寸/PTS/正常字库配置；驱动在引擎返回后才附源帧ID，关闭全部actual后独立调用未修改的冻结评分器。第一次完整命令`./tools/validate.ps1 ocr-replay data/gt/frozen/ocr-all-scenes-v2-effective-r2 all reports/m3-grid-refinement-v1/ocr-baseline-all-v1`已完成，完整分母3361原帧/86602字符/712连续状态/117页，编辑错误62429，逐帧原始准确率27.9127502829034%、状态等权26.75929889507739%，2665失败帧，692空文字帧误报0，5页内容全程精确。仅支持底部普通消息框，HUD/菜单/状态字段未实现的删除及错角色全部计入，没有缩分母。比较报告SHA `f0bfef64db1183bf7187e4fb8a0571b2b7f055cf4babe9d8ef1e7dc6ea0543e6`，根报告SHA `0a4f45f713dff9e277926c4389bd9d6112091a58fb420720304b48ea29ac5eb0`。不是整页/时延或独立验收通过。

局部原候选频率显示全角斜线/问号、源码⋯/·与预先冻结的显示Unicode约定存在编码差异；新增明确显示绑定（／→/、？→?、⋯→…、·→・）不删除字格、数字或空格，！保持。源StatusScreen_PrintPP与P图块装载明确0x72为P，`resources/glyph-display-bindings-v1.json`添加来源绑定，不改原银行。109项Java测试通过（app43+verification66，失败/错误/跳过0），日志`maven-test-source-display-v1.log`。针对性完整原片重跑`display-and-fullheight-{league-centre-npc,status-pages,shop-rejection,safari-warden-npc}`依次普通格精确2939/3008、6773/6776、2218/2223、1180/1308，退出0只说明诊断完成。源副本/身份在`initial-*`、`enhanced-*`、`ocr-baseline-*`中保留；新代码尚未package，旧80测试JAR不能代表新引擎。

为字段布局补审计`resources/japanese-ui-sources-v1.json`的11份菜单/状态/战斗/打印ASM，工具`tools/audit-ui-sources.cjs`只读3个固定Git对象并逐文件记录blob/SHA/URL，源文件副本只留.cache。首`ui-source-audit-v1`因隐式promisor fetch再次触发Schannel失败，日志/partial/failure保留，同Git后端问题累计修复2/3：逐命令openssl，不关TLS、不改全局。重跑`node tools/audit-ui-sources.cjs resources/japanese-ui-sources-v1.json reports/m3-grid-refinement-v1/ui-source-audit-v2 .cache/japanese-ui-sources-v2`完成3×11文件，报告SHA `b06ccc4a92c87338be2eb751e69ac1cce29204c7e81815fb329ff159a2d42175`。来源仅用于编码/字段布局，不用完整消息更改原始识别。

**当前唯一下一步**：实现当前像素驱动的状态页/HUD字段分类与区域工厂，按已审计StatusScreen/DrawPlayerHUDAndHPBar等布局、可见标签和字格产生动态名字/数值，不使用片段ID/GT文字/后帧补字；先验证字段边界/空格/标记的合成性质，再重跑状态/战斗代表原片及同38项完整OCR。紧凑HP/:L/IDNo/No.仍需额外来源灰阶/打印语义审计；不得把3963不支持字段排除正式评分。原视频/GT/契约/分母不变，M3/M4未通过，独立会话/真实窗口/UI等保持待验证，中文不启动。

**字段实现及代表原片复测（继续，同日）**：`SceneFieldReader`读取状态、队伍、战斗HUD及技能字段，`PackedGlyphReader`从已审计battle-extra的0x71+0x62下半部组合12×4 HP标签，仅匹配当前像素，不带固定名字/数值。`<LV>`/`<ID>`/№/．分别按源PrintLevel与StatusScreen打印语义显示为:L/ID/No/.。再次检查源PNG证明所涉及HP图块实际只有0/255两色，之前“也许灰阶导致缺笔”的假设被否定，没有添加猜测的阈值字库。新增UI manifest-v2及`ui-source-audit-v3`完整3×12源文件（增加home/pokemon.asm），没有执行外部源码。

`maven-test-scene-fields-v2.log`112测试通过后，用相同冻结集的`ocr-replay ... <id> reports/m3-grid-refinement-v1/ocr-fields-v1-<id>`连续复测5事件：状态页7711字符/354编辑错，另一状态10314/656错，战斗结果1464/0错，技能4832/343错，队伍2374/0错；后两项100%仅原始字符，位置/状态仍失败，完整页0。全部原帧仍保留。`fields-implementation-v1/`记录对应111源文件、78编译类及源副本身份。状态第二页经验值左侧有未覆盖精灵边缘，数值框改按当前帧首个实际数字定左端并保留之后内部空格/错误；不是按GT固定值裁剪。尚待下一轮验证。

新增`MenuFieldReader`按当前框/标签识别开始菜单、背包、PC父层/列表、商店父层/列表，读取残留父菜单前缀、商品/数量/价格/金额。菜单值来自像素，角色选择只来自布局。合并框检测的重复光标；被列表覆盖文本框右上角时，按仍完整可见的两侧/底边提取消息区域。49项app+66项verification=115测试通过（`maven-test-menu-fields-v3.log`），含当前HP模式/错误偏移/空白拒绝、动态字段/内部空格、真实布局层叠的合成性质测试。`menus-implementation-v1/`保存113源文件/81编译类身份。完整命令`./tools/validate.ps1 ocr-replay data/gt/frozen/ocr-all-scenes-v2-effective-r2 all reports/m3-grid-refinement-v1/ocr-menus-all-v1`正在运行，不提前登记通过。

已注意到冻结不同事件使用不同的role/readingOrder以及?／？显示约定；当前使用来源布局的一致输出，保留由此产生的失败，不按片段ID或GT词句重命名actual。需在完整差异后独立核查其是否为实质契约歧义，未修改或重新提交GT。当前下一步为等待这次全范围输出关闭后分析原始字符/字段差异，并实现不借历史文字的像素晃动配准；冻结几何与最终100%门槛仍保持。

**全范围菜单基线及继续修复（同日）**：`ocr-menus-all-v1`已完整结束，3361原帧/86602字符/712状态/117页，编辑错20059，逐帧76.83771737373271%、状态等权63.42102955042137%，7页内容精确，692空文字帧误报0。`tools/analyze-ocr-differences.cjs`另用JS Unicode标量最小编辑距离及原冻结role/位置/readingOrder匹配键逐帧重算，`ocr-menus-all-v1-text-analysis/`与Java全部编辑分母/分子完全相同；没有放宽评分。主要失败包括边框层叠未检出的背包/选择菜单、PC部分关闭、旧注释角色口径和少数原帧字形/GT疑点。

新增`RasterMotionTracker`只保存最近一张160×144灰度采样及有界边缘点，用当前像素在±3逻辑像素范围配准，置信不足/换源/时间倒退时回到当前帧基础定位，不存储历史文字。`ocr-registration-v1-skills-turn`实测恰在17503/17504为+2逻辑X、17508为+1，其余0；原报告保留。注册后外侧框角被固定显示孔裁掉时，仅在注册位移证据存在时允许以另一侧完整边及上下横线识别当前消息区域。修复普通菜单覆盖角/背包残留父边框/医护选择菜单（源YesNoChoicePokeCenter在13,6，普通YesNoChoice在14,7）；`resources/japanese-ui-sources-v3.json`及`ui-source-audit-v4`完整3×15固定源码文件留身份，源码副本只在.cache。

顶部捕获边缘问题修复累计**2/3**：video-03:3541首帧mark格含额外整横行，原样记录在`glyph-edge-v2-status-alt`，RGB/原图不变。第1次仅检测黑边，原片仍失败；独立原RGB探针显示边缘随淡入成为108–115灰度，而内侧约139，故第2次使用当前整行的均匀度及相邻内侧亮度差，把确证捕获边缘对应的8采样位标为不确定。`PixelGlyphMatcher`仍保留所有并列，在其余至少48观察像素上算距离，不填造缺失笔画；普通格和图内暗行不屏蔽。`Cell.knownPixelMask`/诊断hex保留审计，实际改善尚待本轮原片回归。

局部`ocr-edge-v2-{status-alt-pages,skills-turn,nurse-greeting,backpack-navigation}`逐帧字符率分别98.5554%、95.6954%、99.2447%、99.5527%；状态等权分别46.3717%、97.9180%、98.7868%、98.5874%，均非最终通过。独立JS诊断证实技能剩余208错全是战斗命令被重复当消息插入，已按当前字段布局避免第二次输出，待全回归。最新**app54+verification66=120测试**全部通过，`maven-test-capture-edge-v3.log`；`current-implementation-v3/`保存117源文件/84编译类及源副本身份。正在执行完整`ocr-replay ... all reports/m3-grid-refinement-v1/ocr-fields-and-motion-all-v3`，未提前标通过；当前新JAR仍未构建。

**冻结注释疑点，尚未修订/提交**：直接打开完整原PNG另查发现，video-03:3612上半页仍清楚可见旧HP160/160/じょうたい，而冻结该单帧填了新经验117997/7129で；video-02:526旧能力/属性2上方标记已被新面板清掉，但原GT仍保留ぼ/ぎ/プ；video-02:4502引号前的空格与实际字格位置疑似不符；video-01劲敌「あっ/あッ」也需原图重核。现仅是错误线索及第一次直接原图检查，不冒称新的盲双遍结论，不改GT，不让程序补错误字来符合GT。为独立复核正在从原四视频源头重解码这4个完整受影响事件到`data/frames/<source>/ocr-observation-recheck-<id>-v1/`，各自日志`observation-reextract-<id>-v1.log`。后续任何实质GT/评分契约修订须全部受影响场景合批，完整材料准备后再正式提交并暂停等待明确批准；当前没有提交新卡点，也没有新冻结。源矩形/非整数几何/状态/独立会话等未通过项仍保留。

**当前一条下一步**：等待`ocr-fields-and-motion-all-v3`完成并关闭全部actual，用`node tools/analyze-ocr-differences.cjs reports/m3-grid-refinement-v1/ocr-fields-and-motion-all-v3 reports/m3-grid-refinement-v1/ocr-fields-and-motion-all-v3-text-analysis`独立重算全范围失败；据此优先处理剩余可复现程序问题，并独立复核上述原图疑点，禁止按程序输出改GT。目标继续有效，未达标不宣称完成。

**v3完整结果与证据（同日）**：上述下一步已执行；3361原帧/86602字符的编辑错10496，逐帧**87.88018752453755%**、状态等权**78.7488892879853%**，117页中7页内容精确；2632帧仍有文字/几何/符号/状态失败，692空文字帧误报0。技能回合4832原始字符/0编辑错，但位置/状态未过，不能称该场景验收100%。独立JS全量重算与Java逐项/汇总完全相同，`ocr-fields-and-motion-all-v3-text-analysis`。比较报告SHA `8f8a6a1918f2323152cbf4b6b2c436d4de8e87871dd44d5f233efeffb87de27b`。

执行`mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' -DskipTests package`，`maven-package-v3.log`成功，未声称重跑测试。`build-evidence-v3/`保存对应118源文件/84编译类；源快照SHA `2d18c6fa2cfb18053b08bf01b086880f5adbb797a74d6ea785fd8c08fa465bcc`。`node tools/check-ocr-evidence.cjs reports/m3-grid-refinement-v1/ocr-fields-and-motion-all-v3 reports/m3-grid-refinement-v1/build-evidence-v3 reports/m3-grid-refinement-v1/ocr-fields-and-motion-all-v3-evidence`重新核对四原件/账本、批准父封套/全部payload、3577原帧时间/RGB身份与M2回放、分母/actual评分身份、120测试、源/类/JAR身份与产品不含游戏素材/GT/验证器/Gson，均一致；证据报告SHA `2eac389cdc5dc0c42e248ccb1f7bd6dcd8866823acb43955743e7d1dd27b036c`。退出0仅证明证据可复核，报告明确OCR门槛未通过。此包是后续修复前v3，不代表未来源码。

四受影响事件从原视频源头独立重解码全部372原帧，RGB/时间与总账本一致且PNG逐文件与原二遍材料相同；`annotation-observation-v1/report.json` SHA `4dd98a1506367db69c74e13b87854683c57773fd63c4ca779035b5826bf35c81`绑定4事件全帧身份、新解码命令及4张精确最近邻图。直接查看新提原图及放大图，状态3612上半旧HP与冻结新EXP冲突明确，526旧行标记消失、4502引号前空格、13385小ッ形状问题继续收口。该附件明确preliminary_original_frame_observations_not_new_GT_not_submitted_for_approval，不冒称全部受影响场景新盲双遍完成，不改GT/用户批准，不触发新人工卡点。

**全幅配准修复2/3**：v3的museum-fee由旧62编辑错回归为1626，最小复现3810及完整原帧/输出保留。根因是纹理很多的游戏内部背景移动被误判成整幅Y+1，文本框本身未动；历史错误采样会延续。增加当前文本框边线支持检查：注册后已知边角/横竖线至少36且比基础采样增加至少4才接受非零位移，否则保留当前基础网格、重置像素历史并记录被拒的proposal，不带旧文字。新增背景移动但消息框不动的合成性质测试，**app55+verification66=121测试**通过，`maven-test-global-motion-guard-v1.log`。`global-motion-guard-implementation-v1`保存该版身份，目标/同类回归`ocr-motion-guard-v1-{museum-fee,skills-turn,intro-creatures}`正在执行。原片真实验收/最终100%仍未过。

**当前唯一下一步**：待上述三片目标回归关闭后核对失败museum-fee恢复、三帧攻击位移仍正确及文字不回退，再处理PC部分闭合/残留标记等明确程序错误；若修复有效，再跑同38项全范围。任何注释修订另合批双遍/审阅；当前GT不变且无待审卡点，持续目标未完成。

**运动防护目标回归完成与后续局部实现（同日）**：`ocr-motion-guard-v1-museum-fee`恢复到旧62编辑错（不再1626），技能仍4832字符/0错、三帧配准保持，开场creatures仍5错；原失败v3报告不覆盖。新增列表按当前每行边界识别遮挡范围，避免未画到的父菜单也被截断；PC数量按源`home/list_menu.asm`单独打印的×和数字拆为quantity_label/item_quantity，背包原合并行保持。`ocr-partial-menu-v1-pc-deposit-navigation`编辑错由2174降到485，shop-sell-entry由1058降到1039，shop-buy-navigation仍0，backpack-navigation仍19。源码扩审`japanese-ui-sources-v4.json`/`ui-source-audit-v5`完整3×16文件，包含list_menu的金额/数量打印，固定提交不变。122项Java测试通过`maven-test-partial-menu-v2.log`，`partial-menu-implementation-v1`留版本身份。

3541顶部/稀疏标记问题累计**3/3，仍未解决，停止继续修该同一例**：第2次灰边不确定掩码已生效，`glyph-edge-v3-status-alt`实际knownMask=ffffffffffffff00，但剩余稀疏观测3800000000000000最近是空格距离3，未匹配真实浊点。第3次在当前已观察假名上方的稀疏非空残片尝试±0.5逻辑像素、1/4步进局部相位，仅接受零Hamming的源゛/゜且保留所有别名/参数/并列；没有借历史或名字补字。`maven-test-local-mark-phase-v1.log`123测试通过，但`ocr-local-mark-v1-{status-alt-pages,status-pages,backpack-navigation,pc-deposit-navigation}`分别仍149/3/19/485编辑错，没有实际改进，失败原帧保留。该局部能力只由合成薄标记错位性质测试证明，不能声称修好了实机难帧；后续不继续调整该例阈值/来源答案。

另处理独立滚动残片问题：Bill-data的4587–4588两条当前文本基线之间出现稀疏未决候选（观测ink79、[「,空格]并列），旧实现将其当第三条文字，改变后行readingOrder导致62编辑错。新增当前几何判定：仅在两条相隔一行的已见文本基线之间、至多2格且每格非唯一候选/观察墨点≤8时，保留为`diagnosticBoxes.unclassifiedFragments`（含原矩形/ink/已观察掩码/全部候选/理由），不把它冒称已识别文字或丢弃像素证据；普通单行未知字仍保留错误占位。`maven-test-scroll-fragments-v1.log`**app58+verification66=124测试**通过；当前`scroll-fragment-implementation-v1`留源/类身份，目标`ocr-fragment-v1-{bill-data-npc,nurse-greeting}`正在运行，尚不登记解决。

**当前唯一下一步**：核对上述滚动目标和同类医护回归后，用当前124测试版本跑同38项`ocr-replay ... all`至新目录，独立JS重算并汇总剩余技术错误/注释疑点。顶部稀疏标记3/3局部阻塞，转做其他问题；全幅运动仅2/3且新防护已通过目标回归。旧GT、四原件、分母和M4/M5门槛保持，未提交新GT审阅，持续目标仍未完成。

**v4完整回归及构建已完成（同日）**：滚动残片目标Bill-data由62编辑错降至0，医护仍15未回退。补充被列表遮挡但尚可见的父菜单光标/孤立标记，以及主消息箭头优先级/确定性符号顺序；合成父层可见基字与被遮基字的标记性质测试通过。`maven-test-overlay-symbols-v1.log`为**app59+verification66=125测试**，失败/错误/跳过0；`overlay-symbols-implementation-v1`保存版本。完整命令`./tools/validate.ps1 ocr-replay data/gt/frozen/ocr-all-scenes-v2-effective-r2 all reports/m3-grid-refinement-v1/ocr-regression-all-v4`全部结束；3361原帧/86602字符的编辑错**7100**，原始逐帧**91.80157502136209%**、状态等权**83.96424995223024%**；117页中7页内容精确，2625帧存在文字/几何/符号/状态失败，692空文字帧误报0。GT/分母没有变，仍不达标；字符零错的技能/战斗/队伍等不等于整页/独立验收通过。

`ocr-regression-all-v4-text-analysis`用独立JS重算全部原帧Unicode编辑错/区域键，逐片及7100汇总与Java相同。`classify-ocr-region-errors.cjs`另按完整gridRect查文字差异（仅诊断，显式忽略role/order且额外要求完整占位，所以有时比正式文本计分更严格），报告`ocr-regression-all-v4-region-causes`为1005剩余差异；**这个数及其差额不能用作识别准确率或授予字段通过**，正式7100/86602不改。`audit-frozen-observational-consistency.cjs`完全不读OCR，只比较原RGB哈希相同的冻结注释，`frozen-observational-consistency-v1`未发现物理文字/位置冲突；这不替代原图语义核读，也不否定已确认的切页单帧问题。

`maven-package-v4.log`的离线skipTests package成功（未重跑测试）。`build-evidence-v4/`保存120源文件/86编译类及源副本，产品JAR SHA **`1de0d83d0e256fdb783db8cfd2dce348569181f495878e706bef97648c7c3f70`**。`check-ocr-evidence.cjs ... ocr-regression-all-v4 ... build-evidence-v4 ... ocr-regression-all-v4-evidence`重读四原件/各账本/批准封套及payload、3577时间/RGB/actual身份、3361/86602/117分母、125测试/源类/JAR均一致；不含素材/GT/验证器/Gson。算法耗时P50/P95/P99/最大49.112/71.475/82.902/220.218ms，仅处理观测，不是实际UI延迟验收。Git空白检查通过（只提示原换行规则），未提交/推送。

**当前处理顺序**：保留当前M3代码和全部失败，先对已发现的原帧/标注与字段编码疑点做M2独立一致性复核；不得为追求达标把错误字形/字段硬塞进程序。先不生成新GT、改契约或改批准payload，不预设“程序对/GT错”。来源372帧初查只有观察附件，仍需对受影响状态和前后文重新直接核读及差异收口；角色/顺序/Unicode问题须结合源渲染与原冻结定义判断，不能仅靠“能提高分数”改GT或评分。若确需实质修订，按spec §3.4完成全部受影响场景的统一材料后一次提交、暂停等明确批准，当前尚无该新卡点。

**当前唯一下一步**：从有效集合登记的原范围重新连续解码League/PC/背包/医护/商店及旧试标等受影响事件到新`annotation-recheck-<id>-pass1-v1`目录，核对原RGB/PTS/PNG身份，随后仅看原图和源打印规则做独立复核。四视频、旧冻结38项/评分契约v1/v5全部保留；目标继续有效，原始/最终OCR尚未达标，独立会话/真实UI待验证，中文不启动。

**独立原图复核开始（同日，尚无新GT候选或卡点）**：上述10事件及商店买入/背包开启原范围重新逐源解码全部完成，每项exit0且原RGB/PTS/PNG读回一致；加原先4事件共16受影响事件的第一遍输入。新增`tools/RawRecheckSheets.java`是独立离线显示工具，仅读连续原PNG/verification与手工宽包围裁剪，不读GT、OCR输出/字库候选或生成答案；将6原帧按2×3排列，最近邻放大，另加帧号/PTS标签，每张保存后逐像素与原图核对0差异。源副本/哈希和原帧身份在每项`blind-recheck-sheets-pass1-v1/<id>/report.json`，尚未把生成材料冒称核读完成。

已直接连续查看status-alt-pages全部135原帧/23张表、3612细节和3541名字局部，第一遍原图笔记`blind-recheck-notes-pass1-v1/status-alt-pages.json`已保存。3612确实保留旧HP/状态上面板、旧攻击/属性1，下三技能已换；第二技能当帧是ねむりこな并有4个旧孤立标记（row11列1/3浊点、12半浊点、15浊点），不是把旧标记借给新ご。3613才完整经验/四技能新页。3541上方仍有短小未完整笔画，**不能因为程序漏读就直接把原ボ改成ホ**；笔记明确第二遍再核，暂不判原字面错误。此澄清覆盖此前仅据初查可能“首帧无浊点”的猜测，没有改GT。两遍均将只看原图/源字形规则；在新的原图盲读期间不再打开被测actual/评分差异作答案。

**上轮下一步（本轮已执行其status-pages首遍）**：查看18张原图表并记录首遍笔记；其余核读尚未完成，未提交新卡点。

**2026-10-16网络中断恢复及最新范围决定**：用户再次以持续目标明确要求“**不改GT或降低标准**”。本轮按顺序完整重读三文件，恢复原有效38项，不创建第二个目标/计划。直接看完status-pages的18张表/108原帧及完整526，首遍观察保存在`blind-recheck-notes-pass1-v1/status-pages.json`；没有查看被测actual作本遍答案，不冒称第二遍或新GT。526原图显示切页时旧基础字形尚在但其上标记已清；疑点只留证据，**不制作修订GT、改契约或申请放宽**。既有批准范围照常评分，已知疑点不能被程序硬编码迎合，也不能删除。

依最新用户范围，原M2复核作为未完成观察附件保留，不再用无限扩展GT修订阻止已授权M3程序修复；无正式审阅提交，无待审卡点。先处理与这些疑点无关的当前像素/字段程序错误，使用原四源/同冻结GT/同分母回归。全部原件身份和payload须在新回归证据中重核；独立会话、UI、真实环境及M4/M5前置保持。

**当前唯一下一步**：保存本遍观察后，检查v4剩余程序差异并选择一个不涉及GT疑点的最小复现，按当前图像与已审计源渲染规则修复，验证目标/同类/全38项回归；不改GT或评分器，顶部标记3/3问题保持局部阻塞。

**PC部分重绘漏检修复1/3（2026-10-16恢复轮）**：原video-02:1749完整原图和前后文显示PC主菜单只有顶边、左右上半边与前两行，旧程序要求完整底边而漏掉全部文字。`MenuFieldReader`增加当前顶边两角/至少11横线及每行两侧边支持，只读取连续已围住的行，未绘制行即停止；仍用正常PC菜单字段名，不按GT/片段重命名。`JapaneseOcrEngine`明确此时`menu_partial_redraw`，不宣称完整菜单；光标/孤立标记仍来自当前像素。

首测试`maven-test-partial-pc-parent-v1.log`保留失败：新增夹具用了未登记E/F字形且误把paint(0)当擦除（该helper只涂黑不清背景），未影响实机素材。夹具修复1/3：使用既有C/D并明确将被擦除单格RGB设白，性质/负向检查与原回归全部通过，`maven-test-partial-pc-parent-v2.log`为app61+verification66=**127测试**，失败/错误/跳过0。

目标`ocr-partial-parent-v1-pc-deposit-navigation`在1749已输出原图两行共19字及光标/残留标记，没有补第三行；但冻结该帧role为menu_label、正常PC主菜单输出为pc_menu_choice，正式编辑错由485**增加到504**，照实保留，不把物理文字改善称评分通过。同类`ocr-partial-parent-v1-pc-connect`2348字符/0编辑错未回退，几何/状态仍失败。版本`partial-pc-parent-implementation-v1`绑定122源文件/86类。完整`ocr-replay ... all reports/m3-grid-refinement-v1/ocr-regression-all-v5`运行中，不能提前标通过。

**固定GT约束的原图障碍（不修订、不触发卡点）**：从video-01源头分别连续重解码[6256,6262)两次，新`fixed-gt-observation-6258-pass{1,2}-v1`各6原帧RGB/PTS/PNG读回通过，两次PNG逐文件一致。两次直接看原6258时未打开被测输出；原图下行仅ポケット，冻结文字为ポケット モ。按原批准layout定位モ字格[336,352,352,368)，256像素的(R+2G+B)/4最小/最大/均值均**140**，无字形笔画；6259后帧才有后续字，不能借未来补前帧。`fixed-gt-observation-6258-v1/report.json` SHA **0195396c81f605f5691f0a15f74244dc17fc36e9bacafd95cddc27303ccd28ee**保留原GT状态/哈希、两次源提取/逐PNG身份和像素测量。只是该原帧的观察，不冒称全事件新双遍或新GT。用户要求不改GT，因此该失败继续在原分母；当前固定范围最终100%不能据此诚实授予，程序不能输出原图没有的モ来迎合测试。尚有可推进程序工作，不在首个恢复轮将持续目标标blocked/complete。

**当前唯一下一步**：等待v5全38项全部actual关闭后，独立JS重算同冻结评分，构建并用`check-ocr-evidence.cjs`重核四原件、批准payload、3577时间/RGB、原分母、127测试与源/类/JAR；保存包括新增19字段匹配错误的完整结果，再评估剩余独立程序问题。GT、契约及所有旧失败不变，中文不启动。

**v5回归证据已封口（2026-10-16）**：同38项全部actual关闭，独立`analyze-ocr-differences.cjs`重算7119/86602编辑错，与Java相同；原始逐帧91.77963557423616%、状态等权83.84100137931898%，7/117页内容精确，2625失败帧，692空文字帧误报0。比v4多19错误均来自1749新读出的两行与冻结role不匹配，不隐藏回退。comparison SHA `39f056fc25700f6bc1265903e7515d31551f12a609fbe8dd6a3586adbe389010`。`maven-package-v5.log`构建成功；`build-evidence-v5`保存122源/86类，JAR SHA `89540b398192f52f14c020687887b050d463f4840d6bc36b2ff7668a37f5a579`。`ocr-regression-all-v5-evidence/report.json` SHA `b49f1b179b7f5742f5b5ddf66954c7a4230553f74fe1cfd76401e9cf113ce3bc`重核四原件、旧批准payload、3577/3507/3368/3361保帧/评分数、86602/117、127测试和源/类/JAR一致。算法P95 74.427ms不代表实际UI验收。

**最新用户条件授权（覆盖上段“不改GT”的绝对限制）**：用户原话“如果确认冻结内容有误，可以在置信度较高的情况下调整冻结结论”。执行解释：允许对原图及确定性证据确认的错误进行版本化纠正，保留旧冻结和旧分数，不把这句话捏造成对尚未形成版本的逐项人工核验；不改验收阈值、不按OCR输出补答案。先完成受影响场景复核、明确差异及置信依据，未确认项保持待核验，不默默排除失败。该授权不涉及中文或独立验收。当前尚无新修订GT/冻结版本，旧r2仍是实际评分输入。

**技能菜单遮挡光标漏检修复1/3**：原17472图清楚有左列箭头，但PP框遮挡了技能框右下边界，旧完整闭框路径漏掉17472–17485的可见光标。根据已由当前像素识别的battle/move_name字段，仅检查技能列1的10/12/14/16行唯一箭头候选，与既有框符号去重，不读帧号/GT。17471技能菜单无箭头明确标menu_cursor_loading。新增整个图像管线性质测试覆盖无箭头、三种当前行位置及框外假箭头；`maven-test-battle-cursor-v1.log`为128测试通过。目标`ocr-battle-cursor-v1-skills-turn`4832字零错保持，17471状态及随后14原帧箭头格均已恢复；源像素坐标、其他状态失败仍在，未称整页通过。同类`ocr-battle-cursor-v1-battle-result`已完成，随后核对并全38项回归。

**当前唯一下一步**：确认battle-result同类无文字回退，保存本版源/类并运行旧r2全38项v6回归；完成后转入用户新授权下的受影响场景原图复核，先处理6258已确认的冻结错误，保持全部帧和旧证据。


### 条件授权纠错与v6复核（2026-10-16至17，旧版全部保留）

**v6完整结果**：battle-result同类1464字符零编辑错保持，随后同38项完整运行`ocr-regression-all-v6`完成。原r2上7119/86602编辑错、逐帧91.77963557423616%、状态等权83.84100137931898%，7/117页内容精确，2625失败帧，692空文字帧误报0；comparison SHA `bb4f6f9d19377cb94ef526355f0303f30e78a2c9d1c8f45cf2da5ec08cea3cf7`。独立JS全量重算一致。`maven-package-v6.log`成功；`build-evidence-v6`保存122源/86类；JAR SHA `d7cff184e78fe0632d413bb17e56b17440e4e83e0b502403a3ec83f4efa077a5`。`ocr-regression-all-v6-evidence/report.json`核对四原件、全部payload/3577时间与RGB/评分分母、128测试/源类JAR一致，仍明确未通过。技能遮挡光标修复累计1/3；无其他程序预算重置。

**完整原图复核**：intro-creatures两次从原视频独立解码[6246,6324)各78帧，13×2张最近邻原图表全部直接核读，另查6258/6291/6293/6299完整原图；两个状态事件新第二遍分别108/135帧，与已完成首遍逐PNG/时间/RGB一致，18/23张表全部直接核读，526/3612另查完整原帧。笔记和身份在`authorized-correction-<id>-notes-v1`及对应pass2-sheets目录。观察期间没有打开被测actual作为答案，但如实披露此前诊断知识；代理核读不冒称用户逐帧核验。最后一次大图工具响应截断后重新打开status-alt表018–022，未把截断响应算完成。

**证据确认的6帧差异**：6258下行只有ポケット，原多填空格与モ；6291–6292上行尚无が；6293下行尚无る。四个被指称字格测得单一亮度140、无笔画；候选同步缩短占位/去除不存在标记，字符按帧减少5。526旧能力行的ぼ/ぎ及属性2的プ上标记已被新行擦除，实际为ほ/き/フ；3612上面仍是HP160/160与じょうたい/ふつう，非下一帧经验，下面是ねむりこな及4个旧孤立标记；该帧字符减少4。仅修上述可见内容及其必要几何/字段顺序，不调整角色规范、阈值、版面映射、状态边界或页面机会。候选SHA分别`7cc5f3cc68e320b98afb3e87f717197073f2b20521a6c053fe3f7e8d6e61c9af`、`7a1d6134b9a0c6c11c4ffe8e863107ac117da99f9c8db27920ae05e62930f7a4`、`880f326ff0ae317b2d812eca981521915f9f69d84c4d9569c52eaf908f3404cf`。三份`authorized-correction-<id>-integrity-v1.json`实际Java检查分别74/102/129评分帧、22/5/5状态、各2页，字符1077/7711/10310。

**授权落地及结论降级**：用户原话“如果确认冻结内容有误，可以在置信度较高的情况下调整冻结结论”是对证据满足条件后纠正的明确授权，覆盖先前绝对不改GT及修订必须再次等待的限制；不捏造用户对新版本逐项审阅或对未知项批准。三个受影响事件完整合包`reports/gt-review/gt-observation-corrections-v1/index.html`，321全原帧/新旧全文/双遍/时间/结构检查/条件授权及所有缺口均可追溯。新封套`data/gt/frozen/ocr-all-scenes-v2-observation-r3/freeze.json` SHA **`dd6aa72aca1ddecfe2c6c0143a1def2458f7de5a8767cbf3ca04e9407b7f5141`**，只对6帧纠错给出新结论，其他旧注释引用原批准，既有失败不删除。包清单SHA **`515dbf84a7eeac1c0049df8bbc5057da0cd83a867ab0a2373511da94f581424f`**；构建命令`node tools/build-observation-correction-bundle.cjs`重核四原件和全部旧payload，三片Java结构检查再次执行，全7重叠一致/3361原帧保持。

3541名字上方残缺笔画仍不具备高置信改字依据，不把ボ改成ホ、也不删除该帧。新封套显式`allSemanticIntegrityPassed:false`：未改内容保持历史计分不等于新认证，其余已记录原图/字段/Unicode疑点待完成复核。因此本轮**同时撤回原“全基准语义完整性已确认”的现行结论**，历史记录仍保留。独立会话/真实UI/未选场景保持待验证，完整验收不能由本次纠错授予。新审阅入口只经脚本语法/原图身份检查，实际浏览器显示待验证。

**固定算法下的重计分**：`./tools/validate.ps1 compare-ocr data/gt/frozen/ocr-all-scenes-v2-observation-r3 all reports/m3-grid-refinement-v1/ocr-regression-all-v6/actual reports/m3-grid-refinement-v1/ocr-v6-observation-r3-comparison`复用已封口v6实际输出，非新跑算法。原38项/3361原帧/712连续状态/117页/692空帧保持；新GT86593字符、6963编辑错，逐帧91.95893432494544%、状态等权84.83858237691988%、7页内容精确、2621失败帧、空误报0。新`analyze-ocr-differences.cjs`仅增加显式冻结/比较路径参数及GT/actual哈希验证，不改评分算法；独立重算`ocr-v6-observation-r3-text-analysis`完全一致，同工具对旧r2重跑`ocr-v6-original-r2-analyzer-regression-v2`仍7119/86602。不得把156错误差额称算法改善或降低验收标准。

**辅助工具故障保留**：首原图表Java调用未正确引用-D参数，两次均未生成成功结果；补引号后成功。初像素探针将PowerShell PathInfo误传Bitmap构造器，失败输出保留；辅助修复1/3显式转绝对字符串并Stop-on-error后测量通过。intro首遍笔记で列12的文字记录误差由二遍明确列11纠正，原笔记不覆盖。没有以工具错误修改原帧。

**当前唯一下一步**：用独立检查器绑定新r3封套/321原图/所有新旧payload及同一v6 actual身份，确认仅6帧发生计分变化，然后处理一个不依赖GT疑点的独立程序问题（优先league-centre-npc滚动格定位），按原预算跑失败/同类与完整38项回归；其他标注疑点继续单列，不由程序补答案。中文不启动，持续目标保持active且未完成。
**r3交付独立复核完成（2026-10-17）**：`node tools/check-observation-correction-delivery.cjs reports/m3-grid-refinement-v1/observation-r3-delivery-check-v1`逐包文件SHA、全38项payload、原封套/范围、全部3368计分引用及actual SHA复核一致。恰好6帧GT对象变化、也恰好这6帧计分变化；其余帧结果逐对象相同，字符差额-9逐帧重算一致。报告SHA `4b6fd84c1a07f4fd4dac6f4bebeb3e82c1c522b0693efdea3dc58fca8b887657`。新增.gitattributes保新候选/封套/审阅包原字节；Git空白检查通过，仅原换行提示。没有提交/推送，未重跑或冒称重跑Java测试。

**滚动定位诊断转为注释边界复核**：打开完整原1630/1632/1692后，1630、1692文字确实仍在正常14/16行，1632才已上移；1692ド/が标记仍可见。该直接观察在读过实际差异之后，明确只是诊断首查，不冒称盲双遍。`league-scroll-boundary-observation-v1.json`保存依据；暂不改程序，避免把正确当前像素强行移到旧GT的提前滚动位置。新从原video03开头连续重解码[1590,1736)146帧，`authorized-correction-league-centre-npc-pass2-v1`RGB/时间/PNG读回通过；旧首遍`annotation-recheck-league-centre-npc-pass1-v1`保留。未形成该事件新GT，r3不变。

**当前唯一可执行下一步**：直接按序查看`reports/m3-grid-refinement-v1/blind-recheck-sheets-pass1-v1/league-centre-npc/sheet-000.png`至024并保存首遍全文/边界笔记，再查看新`authorized-correction-league-centre-npc-pass2-sheets-v1`的全部25张独立原图作第二遍；这两遍期间关闭actual/计分差异，完成后才比较原注释并决定是否按同一高置信条件授权纠错。新第二遍表命令已启动，先核对日志完成与像素一致性报告；本轮没有授予该场景或整体通过，目标active、中文未启动。
**联盟中心原图双遍与边界候选完成（2026-10-17）**：按序直接看完两套各25张原图表、各146原帧[1590,1736)，期间未打开actual/成绩/旧GT作答案；此前诊断知识已在笔记披露。首遍笔记`blind-recheck-notes-pass1-v1/league-centre-npc.json`完整记录文字/加载/滚动/清屏；第二遍`authorized-correction-league-centre-npc-notes-v1/pass2.json`纠正首遍1629箭头误记：1629仍有箭头，1630才清除。两遍原PNG逐146文件SHA相同。原图确认1630–1631正常14/16行，1632才上移13/15；1692正常14/16且ド/が标记仍在，1693才上移并擦除上标记。1662–1663真实滚动及其余事件文字/边界与旧注释相符。不是用户人工逐帧认证或独立会话验收。

`node tools/prepare-league-boundary-correction.cjs`生成新候选`data/gt/candidates/ocr-observation-corrections-v2/league-centre-npc/ocr-candidate-v2.jsonl`，SHA **05bb1f63e45c4efeb2ecea8fe7b03ac039aac593db613bb33036d7e78495c542**；旧GT SHA **78a4a387496fc7eae9f74432290d9d69169f1bcd86d5995828cce44658cb6b2d**保留。只对3原帧可见内容/位置/状态修正，另拆出真实滚动边界；31→33状态，142评分帧及146保留帧不变。箭头消失但完整内容尚未滚动的两区间按原explicit_id契约延续既有页面机会，4页不增加、不删除；不能为了保持旧状态分母隐瞒真实转换。未改角色、Unicode、layout、阈值或程序输出。

实际`./tools/validate.ps1 gt-check data/meta/m1-v1 data/gt/candidates/ocr-observation-corrections-v2/league-centre-npc/dialogue-league-centre-npc-clip-v1.json data/gt/candidates/ocr-observation-corrections-v2/league-centre-npc/ocr-candidate-v2.jsonl data/gt/candidates/ocr-observation-corrections-v2/league-centre-npc/layout-v1.json reports/m3-grid-refinement-v1/authorized-correction-league-centre-npc-integrity-v1.json`通过142帧/33状态/4页结构检查。候选尚未装入有效冻结封套，r3与现行91.958934%结果保持；未声称新算法改善、未重跑Java测试。Git空白检查通过（原换行提示）。

**当前唯一可执行下一步**：将上述完整146帧双遍、差异/结构检查与条件授权绑定为新的审阅附件和r4冻结封套，保留r3及所有旧报告；全38项逐帧验证仅1630/1631/1692可见状态改变，复用封口v6 actual重新计分并独立重算，明确新增2状态而原帧/4页机会不变。封套仍保留全基准语义未确认及3541/其他疑点待验证，不授予M4、不启动中文。完成本次证据绑定后再推进不依赖GT疑点的程序修复。

**r4边界纠错证据与全范围重计分完成（2026-10-17）**：上一轮完成双遍及候选，本轮实际推进版本绑定而非等待。`node tools/build-league-correction-bundle.cjs`重新核对四原视频SHA、r3封套及全38项payload、146双份原PNG/完整时间RGB记录、新候选及Java结构报告；新审阅入口`reports/gt-review/gt-observation-corrections-v2/index.html`包含所有146原帧和新旧注释。包SHA **d85000b748ba327db1e6c997557f202ecf46ab4850f173cd23a704036e77ddc5**；新有效开发参考`data/gt/frozen/ocr-all-scenes-v2-observation-r4/freeze.json` SHA **650b69280c331809c901f2c587e00774762173ece7d330ac597a3b40380729c9**。条件授权仅覆盖1630、1631、1692，继承r3的全部待验证限定，allSemanticIntegrityPassed/productAcceptance仍false。新入口只检查脚本语法，真实浏览器显示仍未验证。

实际`./tools/validate.ps1 compare-ocr data/gt/frozen/ocr-all-scenes-v2-observation-r4 all reports/m3-grid-refinement-v1/ocr-regression-all-v6/actual reports/m3-grid-refinement-v1/ocr-v6-observation-r4-comparison`完成；`node tools/analyze-ocr-differences.cjs reports/m3-grid-refinement-v1/ocr-regression-all-v6 reports/m3-grid-refinement-v1/ocr-v6-observation-r4-text-analysis data/gt/frozen/ocr-all-scenes-v2-observation-r4 reports/m3-grid-refinement-v1/ocr-v6-observation-r4-comparison/report.json`独立重算一致。**6807/86593编辑错、逐帧92.13908745510607%、状态等权85.19550363882948%、7/117页内容精确、2621失败帧、692空文字帧误报0**。714连续状态（较r3增加2），等权字符15462；原3361评分帧、3368引用、117页及86593逐帧字符均不变。仍为同v6实际输出再计分，非算法提升或新回放。

`node tools/check-league-correction-delivery.cjs reports/m3-grid-refinement-v1/observation-r4-delivery-check-v1`独立逐包hash、全38项payload/实际输出/3368逐帧结果检查通过。忽略纯区间ID变更后，恰好3帧可见GT变化、也恰好这3帧计分内容变化；其余原帧结果完全相同。报告SHA **65137f7d7486add48133e3f03f6128c01b43a6e980b7eab361c00d523247a31b**。旧r3/r2与所有分数保留，新增目录字节规则，未提交/推送，未冒称新Java测试。

**下一程序问题的实证**：r4上联盟中心文字已零错但几何仍失败。原1610 actual当前grid origin=(180.76988636414555,-0.27817627840251047)、scale=(3.7546875,3.7821180555555554)，产生上行sourceRect右端572而名义布局570、下行底端515而布局514；光标源矩形同样漂移。原包围[180,0,782,544]包含右侧压缩/阴影边缘，而原标定[180,0,780,544]；不能把文字正确称精确几何通过。当前代码取RasterGridRefiner最高梯度集中候选，没有以完整矩形渲染边线检验源投影；这不是GT边界问题，不再修改GT迎合候选。1699无箭头最终页仍message_loading，须另处理真实打印完成证据，不能以静止/标点猜完整。

**当前唯一可执行下一步**：保留原1610及相邻帧/actual为最小复现，检查`RasterGridRefiner`候选评分与已识别文本框四边的确定性像素约束，先建立不读GT的源投影诊断，再在允许预算内修复双轴尺度/相位候选选择；以联盟中心、非整数PC和两倍探索为目标/同类，跑完整38项r4回归。不得重开已耗尽的包围提取或3541稀疏标记阈值，不得把名义GT坐标硬编码进程序。M3/M4未过，独立会话/UI/真实环境待验证，中文不启动。

**几何误差诊断及未采用的离散模型（2026-10-17）**：本轮前一目标轮为progress（r4绑定/重计分完成），继续检查真实原帧。新增`tools/GridEdgeProbe.java`只消费PNG像素/当前定位器，不读GT、帧ID或文字答案；对原边缘梯度比较连续傅里叶相位与离散ceil边缘模型，遍历受包围约束尺度及相位，记录并列最小值，不接入产品。实际javac编译成功；java `GridEdgeProbe <原PNG> reports/m3-grid-refinement-v1/discrete-grid-probe-v1/<样例>.json`完成league1610/1611、PC1410、两倍探索9870四原帧，全部原输入仍来自既有四视频且与已验证PNG关联。

`node tools/check-grid-probe.cjs`独立按r4冻结布局比较全部逻辑边界的floor/ceil投影（只诊断，不改GT）。有效结果`discrete-grid-probe-v1/assessment-v2.json`：联盟1610/1611原算法各356不一致边界、最大2源像素，离散模型各284、最大1；PC1410原329/最大4，新612/最大6；探索9870原0/0，新612/最大5，X/Y分别152/151个同分解。**离散模型不能采用**：周期边缘无法确定整数逻辑格原点，直接把外包围当原点会包含捕获边框；ceil量化即使零代价也有大量等价解。不能按GT选并列答案或四舍五入到期望坐标。这个实验没有修改生产算法，因此不是正式回归改善；现行结果仍r4/v6的92.1391%，原完整128测试证据未变，本轮未重跑Java测试。

独立诊断器初版只支持分轴scale字段，遇到PC/探索的共轴schema生成NaN并JSON写null；保留`assessment.json`为**无效初版评估**，不得引用其PC/探索数字。辅助修复1/3明确支持共轴字段并拒绝非有限数，重跑四样例到`assessment-v2.json`全部有限，上述真实结果据v2。产品/GT无改动。此轮离散相位模型验证是几何问题的一次已证伪诊断尝试，不重置此前尝试预算，不重开包围提取和3541耗尽问题。

**当前唯一可执行下一步**：使用当前像素已识别的完整文本框四角与长边作为非周期锚点，在独立诊断工具中检验角点/边线的源像素投影和采样模型；保留多解，先对上述四原帧及其相邻帧验证可辨识性，只有能解释非整数误差且保持探索整数网格时才接入RasterGridRefiner候选选择并跑同r4全38项回归。禁止以GT坐标当运行时输入或把离散零代价等同精确定位。目标active，未达到M3/M4，中文不启动。

**边框采样假设诊断封口（2026-10-17）**：`tools/BorderProjectionProbe.java`仅用当前像素识别的框字形和已审计三版本银行构造边框模型，不读GT/文本答案。双线性与像素面积积分各运行联盟1610/1611、PC1410、探索9870；`node tools/check-border-projection-probe.cjs`将两套报告分别绑定原PNG/时间RGB、r4布局、银行和工具源SHA。独立assessment分别位于`border-projection-probe-v1/assessment.json`、`border-projection-area-v1/assessment.json`，原双线性源和新增面积源各自留存。两者图像残差降低但整数探索从0边界错退化到306/最大1像素；联盟分别356→398、356→439，PC329→328、329→331。采样每隔2像素也可能造成相位别名，未证明可辨识性。均不采用、不改GT/生产定位器。连同前轮离散ceil模型，本几何候选假设累计3/3诊断尝试，停止该路线并转独立任务；不重置包围提取或3541耗尽预算。没有把失败模型接入全量OCR或称精度提升。

**空开始菜单状态修复1/3（2026-10-17）**：直接看原video04第5176完整帧，右侧完整空菜单已出现；旧v6检测框[12,0,20,16]且无文字/符号，却在main==null分支输出no_textbox。仅对当前确认START布局、完整闭框且lines/symbols/fragments全空的无文字帧输出menu_empty，不将一般无框/部分框改名。增加图像管线测试，覆盖有字→清空及去掉底边后不再认证空菜单。首Maven调用漏固定settings导致离线仓库ID不符，在编译前失败，日志v1保留；改用已登记`mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' test`后v2成功，app63+verification66=129测试0失败。

实际目标`./tools/validate.ps1 ocr-replay data/gt/frozen/ocr-all-scenes-v2-observation-r4 backpack-opening reports/m3-grid-refinement-v1/ocr-empty-start-v1-backpack-opening`完成38保留帧/34评分帧，662字零编辑错保持，仍29失败帧/0页通过；`state-delta.json`对同v6逐帧原文字/几何对象验证完全不变，仅5176–5180状态变为menu_empty，其余状态相同。不能由单个状态修正宣称最终页通过。源/类快照`build-evidence-v7`已保存132源/86类。

**当前唯一可执行下一步**：等待已启动`ocr-regression-all-v7`全38项r4回放关闭（当前运行会话3660），然后独立重算并逐帧与v6对照文字/状态变更、保存v7构建及源类身份；如同类/全范围有回退保留并按空菜单问题1/3继续修复。正式全量成绩在完成前仍引用r4/v6的92.139087%、7/117页、空误报0/692，M3/M4未过，独立会话/UI/真实环境待验证，中文不启动。

**v7全量回归及独立封口完成（2026-10-17）**：上一目标轮为progress（空开始菜单修复及129测试/目标回放完成），本轮先直接轮询运行会话3660，确认仍活跃后继续等待，未重启；随后句柄返回exit0且38项actual/报告完整关闭。`node tools/analyze-ocr-differences.cjs reports/m3-grid-refinement-v1/ocr-regression-all-v7 reports/m3-grid-refinement-v1/ocr-v7-observation-r4-text-analysis data/gt/frozen/ocr-all-scenes-v2-observation-r4`独立重算仍6807/86593错误、逐帧92.13908745510607%、状态等权85.19550363882948%、7/117内容精确页、692空帧误报0；失败帧**2621→2616**。`check-empty-start-regression.cjs`对全部3577引用逐对象比较同v6的rawRegions、geometry、diagnosticBoxes、imageRegistration和源时间身份完全一致，唯一状态变化是backpack-opening的5176–5180从no_textbox→menu_empty，完整列表在v7/state-delta.json。没有以零文字错误或局部五帧通过冒称整页达标。

`maven-package-v7.log`实际构建成功，JAR SHA **bc386c8cb2df3617edf503d222098cdcac444341f321edb59e6c6dd5016d8d2f**。新增独立`tools/check-ocr-r4-evidence.cjs`保留旧r2检查器，适配r4的previousEffectiveFreeze/priorFreeze/审阅清单/identity结构及86593字符分母，并要求allSemanticIntegrityPassed/productAcceptance仍false。实际命令`node tools/check-ocr-r4-evidence.cjs reports/m3-grid-refinement-v1/ocr-regression-all-v7 reports/m3-grid-refinement-v1/build-evidence-v7 reports/m3-grid-refinement-v1/ocr-regression-all-v7-evidence`重读四原件/总账本、全部payload和3577引用时间RGB、3361评分原帧、129测试、源/类/JAR，全部身份一致。报告SHA **142632a1a06c9fce419450f3aabfb3f236e2b6c78b68207942464d31f410b745**。源快照早于新增两项独立检查器，因此不将新检查器声称为该编译快照源；state-delta另含自身SHA。产品算法只有上一轮菜单状态改动。空菜单问题累计1/3已通过完整回归；几何采样模型3/3停止不重开。

**未解决编码/字段契约清单**：新`frozen-contract-inventory-r4-v1.json`只读取并验证全部冻结GT SHA，列出57种角色及179个含半角/全角问号叹号的区域状态。此附件只是当前标注词汇分布，不以词汇不同证明GT错误、不改注释或评分。原严格成绩中shop-exit3736、shop-sell-entry1039、approved-trial976、PC相关954编辑错包含字段口径/顺序/标点差异；必须分别审查原图语义/字库绑定与历史冻结契约，不能用片段ID重命名输出。旧相同RGB一致性工具已做过，不重复把它当新语义证明。

**当前唯一可执行下一步**：针对问号显示编码作不读取OCR成绩的契约核查：逐版本查已审计字库/源码字符映射和`resources/glyph-display-bindings-v1.json`，与冻结契约textUnit及全部问号状态（`frozen-contract-inventory-r4-v1.json`）交叉核对；先形成可追溯的映射依据及全部受影响事件列表，再按条件授权规则决定需要哪些完整原图双遍，未确认前不改GT/程序显示绑定。不以缩小范围或放宽键匹配解决现有6807错误。现行v7/r4、M3/M4未过、UI/独立会话/真实环境待验证、中文不启动，目标active。

**问号契约核查完成，规范选择待答复（2026-10-17）**：上一目标轮为progress（v7完整回归/独立证据封口）。本轮`node tools/audit-question-encoding.cjs reports/m3-grid-refinement-v1/question-encoding-contract-r4-v1.json`只读GT/契约/已固定本地源码，不读被测actual或成绩。三版Git HEAD均等于来源清单提交，三份charmap分别266/268/268行均以U+FF1F映射tile E6，三银行对应相同位图`1818001873e77e00`。产品显示绑定另将其映射U+003F。冻结GT中pc-deposit-navigation/shop-sell-entry/shop-rejection/shop-exit共34区域状态168帧使用全角，bill-data-npc/bill-grandfather/museum-question共13状态105帧使用半角；原契约textUnit仅明确空格/叹号，没有明确问号宽度。**同一字形不能从像素推断两种Unicode规范哪个必须使用，不能将契约歧义冒称高置信字形错误。** 已用异步问题请用户选择统一「？」（建议，来源映射一致）或「?」（当前绑定一致）；答复前保留GT、显示绑定、评分器及全部问号失败。问题仅阻塞这一编码选择，不暂停独立原图复核；超时不当同意。

**劲敌对白完整原图复核与确定字形纠错候选（2026-10-17）**：初查原13382及字库后，关闭actual/旧GT，按顺序直接看完`blind-recheck-sheets-pass1-v1/lab-rival-demand`全部8表46原帧[13374,13420)，保存`authorized-correction-lab-rival-demand-notes-v1/pass1.json`。实际从video01开头再次连续解码同范围到`data/frames/video-01/authorized-correction-lab-rival-demand-pass2-v1`（export会话97438实际结束exit0），新`RawRecheckSheets`8表全部直接核读，`pass2.json`绑定46原PNG/RGB/PTS逐项相同。明确披露先前诊断接触，属于当前代理两遍核读，不冒称新独立会话或用户逐帧核验。初表调用classpath分号后误加空格导致Gson找不到、未生成输出；删空格后一次成功，不改原件。首遍13379的瞬态疑问在二遍确认グリ，其余逐字/空框/关闭边界一致，完整可见句为`グリーン『あッ！ ずるい！` / `じいさん！ おれにも くれよお！`，无下箭头。

**像素区分依据**：依据冻结整数布局，原上行第7格source[352,320,368,336]，直接计算每个2×2原像素的灰度均值，不导入任何产品/OCR代码。`cell-means.json`保留全部32可见帧64格值，`glyph-separation.json`对照已审计字库两种不同掩码：片假名ッ=`0608101515000000`，平假名っ=`060810100f000000`。32帧均“最暗背景−最亮笔画”对ッ为正且>100灰度，对っ为负，13382分别+113/-120.667；与直接观察的分离上笔画一致。不能根据邻字あ把确实可区分的ッ转成っ。此证据足以满足用户已给的高置信纠错条件，不依赖问号规范答复。

实际`node tools/prepare-rival-glyph-correction.cjs`产生`data/gt/candidates/ocr-observation-corrections-v3/lab-rival-demand/ocr-candidate-v2.jsonl`，SHA **84ac0c0130dce455c9171bfa6ce525bf2a40b0ba7b4092b81582852a4a4e04cb**；8区间/32帧仅あっ→あッ及新复核依据，全部原帧、字符数、状态边界、角色/顺序/坐标/光标/页面机会不变。旧GT和旧结果保留。实际gt-check输出`authorized-correction-lab-rival-demand-integrity-v1.json`通过42评分帧/13状态/1页结构检查。尚未装入有效冻结封套，当前r4/v7成绩不变，没有新算法/Java测试或验收通过声明。

**当前唯一可执行下一步**：将劲敌完整46帧双遍、字形分离证据、新旧注释及条件授权合为新的审阅附件与r5封套（继承r4所有待验证限定）；独立逐38项核查仅上述32帧可见文本变化、字符/帧/状态/页分母完全不变，复用封口v7 actual重计分并独立重算。问号编码仍等待用户规范选择，不混入本次确定字形纠错。中文不启动，目标active；空菜单1/3、几何模型3/3及其余累计预算不重置。

**r5劲敌字形纠错交付与同v7重计分完成（2026-10-17）**：上一目标轮为progress（问号契约审计、劲敌双遍/候选/完整性通过）。本轮`build-rival-correction-bundle.cjs`核对四原件、r4全部payload、候选SHA、46双份原帧和完整时间/RGB、42帧/13状态/1页结构报告；新审阅附件`reports/gt-review/gt-observation-corrections-v3/index.html`包含全部46原帧、新旧完整注释、双遍笔记及字形像素证据。新开发参考`data/gt/frozen/ocr-all-scenes-v2-observation-r5/freeze.json` SHA **7e122cc67f89da660936daa413d6f7d822d6a747998169724c92b5aa2f8702fc**，包清单SHA **2ded99bb7caab35e230a4a3cc5fc4569dc8251f87325281ae18b1432c64c2b2a**。仅13382–13413的っ→ッ按已有高置信条件授权落地；不冒称用户逐项审阅，继承全部待验证限定、allSemanticIntegrityPassed/productAcceptance=false。旧版保留，实际浏览器显示仍待验证。

辅助构建初次误选152帧的历史dialogue-lab-rival-pass2-v1作首遍输入，被46帧身份断言拒绝，尚未写入新包。辅助修复1/3改为首遍sheet报告真实引用的46帧ocr-observation-recheck-lab-rival-demand-v1，逐帧身份比较后成功，未放宽检查或修改任何原帧。

实际`./tools/validate.ps1 compare-ocr data/gt/frozen/ocr-all-scenes-v2-observation-r5 all reports/m3-grid-refinement-v1/ocr-regression-all-v7/actual reports/m3-grid-refinement-v1/ocr-v7-observation-r5-comparison`及独立JS重算完成：**6775/86593编辑错，逐帧92.17604194334414%、状态等权85.2472433878917%、7/117内容精确页、2605失败帧、空文字误报0/692**。仍是封口v7实际输出，未重跑算法，32错误减少来自观察标注纠错，不是算法提升。`node tools/check-rival-correction-delivery.cjs reports/m3-grid-refinement-v1/observation-r5-delivery-check-v1`逐38项/3368评分引用检查，仅32帧可见GT及其计分变化，其他结果逐对象相同；全部actual身份不变，3361原帧/86593字符/714状态/117页等分母不变。交付检查报告SHA **1d0e6fcffa3a04b24fba9294aff7fca865cac4de2eb279bedd54c5760e9823c4**。新目录字节规则已加，未提交/推送，没有声称本轮重跑Java测试。

**当前唯一可执行下一步**：继续不依赖问号选择的safari-warden-npc空格疑点原图复核：先读旧诊断定位与其连续保帧区间，然后关闭actual/GT，完整双遍原图核读该事件，检查“でも……「は」は”之间是否真有一个空格字格；仅在原像素与两遍证据确认后形成候选，不把语言习惯或现有识别值当答案。问号规范问题仍待用户答复；不能以等待该单项阻塞其他独立工作。r5/v7尚未达标，独立会话/UI/真实环境保持待验证，中文不启动；几何3/3、空菜单1/3及其余预算不重置。

**园长对白r6纠错与同v7复算完成（2026-10-17）**：完整直接核读首遍 blind-recheck-sheets-pass1-v1/safari-warden-npc 全14表83帧[4445,4528)，重新从video02连续解码后，直接核读 authorized-correction-safari-warden-npc-pass2-sheets-v1 全14表。两次83帧PNG/RGB/PTS一致；记录在 authorized-correction-safari-warden-npc-notes-v1 的 pass1.json、pass2.json、space-cell-evidence.json。披露此前诊断接触，核读期间关闭actual/GT；同代理，不是独立会话或用户逐帧核验。第二遍完成后沿用一致的span表示。

tools/measure-safari-space.ps1 不导入OCR，仅按冻结布局(300,108)、18源像素/格测量原PNG。26帧[4496,4522)第5格均有27–28个均值<80的引号笔画像素，第6格は、第7格闭引号；4496/4497第8格空，4498起第8格は，第9格始终空。最近邻6倍原图直接核看与双遍一致：原标注多了一整格空格。阈值仅描述像素，不修改识别/评分规则。prepare-safari-space-correction.cjs 生成候选 data/gt/candidates/ocr-observation-corrections-v4/safari-warden-npc/ocr-candidate-v2.jsonl，SHA 28e541e118f35e9467037d4c5f662ef0f33a4478019f53f8c6350cd4a6cf6aae；仅6区间/26帧删除该空格，上行右界grid-1/source-18，全部原帧、边界、页、状态、其他文字保持。gt-check报告 reports/m3-grid-refinement-v1/authorized-correction-safari-warden-npc-integrity-v1 实际通过79评分帧/23状态/2页面机会。

node tools/build-safari-correction-bundle.cjs 核对四原件哈希、r5全部payload、83双份原帧及候选，生成 reports/gt-review/gt-observation-corrections-v4/index.html，83原帧、新旧注释、双遍、像素依据全保留。包清单SHA 4f2194b20710d9eacc3df3dc41b10a5fad22a688bd6f3e749a1fc3c4b21a54e6；依用户高置信条件授权，r6封套 data/gt/frozen/ocr-all-scenes-v2-observation-r6/freeze.json SHA 603450afecb3fb7e45e1c916281cef77f6957c0382f9cd91cda15c109828de80。继承所有待验证限定、allSemanticIntegrityPassed/productAcceptance=false；脚本语法已检查，UI实际呈现待验证。

实际成功命令：./tools/validate.ps1 compare-ocr data/gt/frozen/ocr-all-scenes-v2-observation-r6 all reports/m3-grid-refinement-v1/ocr-regression-all-v7/actual reports/m3-grid-refinement-v1/ocr-v7-observation-r6-comparison-v2。报告SHA be9bf1f7d125bcdbd8391661337260105ed55b612e2403b0ce1356e1f38ecf63。analyze-ocr-differences.cjs 独立复算到 ocr-v7-observation-r6-text-analysis-v2 确认6749/86567编辑错、逐帧92.20372659327457%、状态等权85.28033626187769%、7/117页、2605失败帧、误报0/692。node tools/check-safari-correction-delivery.cjs reports/m3-grid-refinement-v1/observation-r6-delivery-check-v1 逐38项/3368评分引用检查通过：仅26帧可见GT/计分变化、全部v7 actual SHA相同，字符分母精确-26，原帧/页/状态数不变。交付报告SHA 45d135fe3309820fd3dbb5b6b4bc414c0de3eeda1b7846b9fd5c8dc3affc36a0。减少26错误是注释纠错，不是算法提升，没有重跑Java测试声明。

调用失败保留：首次compare误传运行根目录，拒绝缺actual文件但已建空comparison目录；第二次修正actual路径却复用该目录，被新目录保护拒绝，依赖分析亦缺报告。随后使用全新comparison-v2成功，再进行独立分析。一次报告读取缺-v2后缀失败后正确读取。首次计划写入的JS模板引号语法失败未写文件，改为PowerShell字面量写入。未放宽保护、删除失败帧或改变评分规则；调用恢复不重置算法预算。

**当前唯一可执行下一步**：读取 ocr-v7-observation-r6-text-analysis-v2/pc-exit.json 及原契约，定位pc-exit的450编辑错属于残留菜单还是字段顺序/角色问题；依据原帧核对通用区域状态推导根因，在既有预算内做通用修复并重跑失败、同类、回归，禁止按片段ID填答案或配合输出改GT。问号规范选择仍待用户答复，不阻塞此独立任务。r6/v7未达标，独立会话/UI/真实环境待验证，中文不启动；几何3/3、空菜单1/3及其他累计预算不重置；目标active。
**PC主菜单字段不一致定位（2026-10-17）**：上一目标轮为progress（r6证据封口与独立复算）。本轮读当前spec/PLANS及计划，完整计划单次输出被工具截断，不能声称该次输出包含所有历史段；另检索PC/预算历史。实际读取 r6-text-analysis-v2/pc-exit.json，五个评分帧2248–2252每帧90编辑错全由同样45字的menu_label删除+pc_menu_choice插入构成，无文字差异。直接看原2248与1379完整PNG，均同一个五行PC主菜单、同光标位置。产品MenuFieldReader对主菜单完整/部分框统一pc_menu_choice，不能按未来退出事件或clip ID改变名字；未改算法。

独立工具 tools/audit-pc-role-contract.cjs 不读取任何actual、错误数或被测识别器，仅核验r6冻结GT/clip/layout/contract SHA，按原标注物理文字/占位/源矩形/标记及顺序审计。报告 reports/m3-grid-refinement-v1/pc-role-contract-r6-v1.json SHA **47301343c2a9ed31effcb36c062d36e3033d038720aae837ce5127cbe7e539ad**：原时间更早的pc-connect全主菜单使用pc_menu_choice；pc-deposit-navigation1749–1753及pc-exit2248–2252共10帧/12区域状态使用menu_label，物理字段一致。子菜单的道具操作、遮挡前缀、数量等作为负向控制，不能按坐标或全局字符串重命名。此证据确认元数据不一致，不直接修GT，也不等于原图全事件复核完成。

RawRecheckSheets 对三个完整保帧事件生成新首遍辅助表，运行会话32212已返回exit0：reports/m3-grid-refinement-v1/pc-role-recheck-pass1-v1 下 pc-connect 51帧[1379,1430)/9表，pc-deposit-navigation124帧[1632,1756)/21表，pc-exit14帧[2246,2260)/3表，总189原帧。报告确认全部展示像素等于原图、保留PNG/RGB/PTS身份。**仅生成，尚未直接核读这些33表**；不把生成成功算双遍完成。未新建GT候选/冻结、未运行OCR或测试、成绩保持r6/v7。当前命名一致化方向是同一PC主菜单采用更早冻结的专门角色，但必须完成全受影响事件原图双遍及契约解释后再形成条件授权修订候选，不能以当前输出作为依据。

**当前唯一可执行下一步**：关闭GT/actual，依原时间先后直接核读 pc-role-recheck-pass1-v1/pc-connect 的9表、pc-deposit-navigation的21表、pc-exit的3表，记录完整文字/字段布局/转换与角色语义；再分别从同一video02原件重新连续解码上述完整范围做第二遍。任何字段/状态疑点保留，不按菜单名称猜历史。所有189保留原帧、177评分引用与原页面机会保持，实际页分母以绑定封套为准。问号规范仍待答复；独立会话/UI/真实环境待验证，中文不启动，修复预算不重置，目标active。
**PC字段复核首遍完成及独立二遍保帧（2026-10-17）**：本轮分段重新读spec/PLANS/计划，截断部分另补读。首遍33表189原帧已全部直接核读，笔记 reports/m3-grid-refinement-v1/pc-role-recheck-notes-v1/pass1.json SHA **bd36f1712b7d9da9934eddb307e11ad2c9122b608387eacebc907bd3699d2a37**。连接五行主菜单、四行道具子菜单、列表滚动/重复行/双光标/孤立标记、返回1749两行部分绘制及1750完整五行、退出2253消失均登记。此前诊断接触如实披露，核读期间不打开actual/GT，不声称用户逐帧认证。1749下部只露上方标记，不补写未显示的第三行。

实际从video02原件重新连续解码[1379,1430)、[1632,1756)、[2246,2260)，分别保存 data/frames/video-02/pc-role-recheck-<id>-pass2-v1；三个export均exit0（导航会话71786已关闭）。RawRecheckSheets生成 reports/m3-grid-refinement-v1/pc-role-recheck-pass2-v1/<id> 全9/21/3表，会话4694已exit0。逐189原PNG与首遍输入SHA完全一致、工具报告全部展示像素相符；extraction-check.json SHA **e2ce5078c58cb8a8d6215157e11b23c6c7b071e0a06bfd4cc2fa53e5e33931a2**绑定两次verification。**第二遍仅备好材料，尚未直接核读**，没有新候选/冻结/算法或测试通过声明，成绩仍r6/v7。

**当前唯一可执行下一步**：关闭GT/actual，按原时间直接核读 pc-role-recheck-pass2-v1 下 pc-connect 000–008、pc-deposit-navigation 000–020、pc-exit 000–002全33表，保存第二遍独立文字/转换笔记及与首遍差异；完成后才结合原契约和主/子菜单语义决定是否制作10帧字段归属修订，不能按当前输出选择答案。189保留帧/177评分引用/原页面范围不变。问号规范待用户答复，独立会话/UI/真实环境待验证，中文不启动；目标active，全部修复预算保持。

**PC主菜单r7纠错与同v7复算完成（2026-10-17）**：第二遍33表189原帧全部直接核读，先记录pass2.json（SHA 6cc6e748b7a4b4802e37fc8a46bdc454db3d378aaabfd0643844a64093ee78ed），再打开GT。pass-differences.json（468bea3fb750399c2709e455454d9439ec001676a67338a4a799a0d1d0ce7d9d）保留首遍1688帧光标误读：完整原PNG确认仅第三行实心光标，首遍笔记不覆盖；1724残留上方标记补记。这些疑点未混入本次角色修订。两遍同代理、此前诊断接触已披露，不冒称独立验收。extraction-check中的secondReadComplete=false是核读前历史快照，后续完成证据在pass2。

实际prepare-pc-role-correction.cjs仅改主菜单12区域状态角色menu_label→pc_menu_choice，涉及导航1749–1753与退出2248–2252共10原帧；采用更早原时间pc-connect冻结字段语义。恢复角色后逐对象与旧GT完全相同，文字/Unicode/坐标/顺序/边界/页/光标/评分规则均不变。候选data/gt/candidates/ocr-observation-corrections-v5下导航SHA bebe6893906bdae28249ab61ced31c69249bbcef7da626184867d43a3ff867c5，退出SHA b4c2cf523651bfcc590cce9f48319a7a4f3bf651dd71262b3593223b74c4d665；candidate-evidence.json SHA 2c6916b62bc16ed7237d798af1b1b80dac04e454b31ecefce14465f788d4e9ec。实际两项gt-check分别通过120帧/32状态/11页与10帧/2状态/1页，报告pc-role-navigation-integrity-v1.json SHA 96fce86d1c017df1da758801e0e8868d28c577ebcb56aa789cf8a9366f0841f5、pc-role-exit-integrity-v1.json SHA b95cdbb8781170c6a6d9d1c54cdacae15b9bcf7920d20962bc713e05e7c557b4。

build-pc-role-correction-bundle.cjs实际重新核四原件SHA、r6全部payload、189双份原PNG/verification及候选，重叠审计3361唯一评分原帧/7重复引用/0冲突通过。reports/gt-review/gt-observation-corrections-v5保留全部189原图、新旧注释、双遍与差异；bundle SHA 0568f195a5117bab9dbc4409f1436d0204e6334daabbb5a8b2d49171622e475a。依用户高置信条件授权生成r7封套，freeze SHA e57cb8b84ec8d4b0631b1a59377a90cb5157799a418d22696e459df99518cfe1；不是用户再次逐项审阅，allSemanticIntegrityPassed/productAcceptance=false，实际浏览器呈现待验证。

实际命令./tools/validate.ps1 compare-ocr data/gt/frozen/ocr-all-scenes-v2-observation-r7 all reports/m3-grid-refinement-v1/ocr-regression-all-v7/actual reports/m3-grid-refinement-v1/ocr-v7-observation-r7-comparison成功，report SHA 66cfe195bdb006d601df164090d076f032eb442d6193728fbb8f35e7827692de。analyze-ocr-differences.cjs独立复算确认5901/86567编辑错，逐帧93.18331465801056%、状态等权86.69079174842013%、7/117内容精确页、2605失败帧、误报0/692。check-pc-role-correction-delivery.cjs逐38项/3368评分引用检查通过，仅上述10帧GT/计分变化，全部actual SHA和所有分母保持；交付observation-r7-delivery-check-v1/report.json SHA 80feee1b4a39a958c3bf03f6d0cd33cd74a889264b39a0b138b2fb9cdb32eb7f。848编辑错减少来自字段元数据纠错，不是算法提升；本轮未改生产Java、未重跑Java测试或OCR回放。新目录字节规则已加，未提交/推送。一次Get-Content读取单行报告输出被截断，已以独立统计和交付检查提取精确结果。

**当前唯一可执行下一步**：读取r7逐帧报告与JapaneseOcrEngine的无目标状态分支，定位startup-nontarget的245帧状态错误（字符/误报均0）；以原片段和通用输入特征区分non_target与empty_target，先查历史预算再决定通用修复，禁止使用片段ID/帧号/未来画面判断。问号规范选择继续待答复，不阻塞该独立任务；r7/v7尚未达标，独立会话/UI/真实环境待验证，中文不启动，目标active，已有几何3/3等全部预算保持。

**非目标状态根因与全范围状态诊断完成（2026-10-17）**：上一目标轮为progress（r7角色纠错与独立复算），本轮继续实际诊断。重新读取spec/PLANS/本计划；长输出截断部分分段补读。生产app/src逐文件SHA仍与build-evidence-v7一致，没有修改定位器/引擎/GT/评分规则，也未重跑Maven或声明新回放。读取OcrReplay时首次路径误用verify目录，随后rg定位verification目录并正确读取；无文件改动。

直接查看startup原90、110、225、262、366全PNG，分别是HOME、极暗启动标志、空白游戏栅格、版权页、HOME暂停覆盖。原冻结non_target包含空白游戏栅格，因此“未找到字/网格→non_target”没有充分证据；同理不能把所有no_textbox直接改为empty_target，原trial与shop-exit的无框状态词汇另有不一致疑点。没有按片段或未来帧强行选择标签，现有245状态失败保持。

新增独立tools/RawSignalProbe.java（SHA f730bd6178dea6f5f2cb262b79a08f155deb8e9dde36203a83e5f3e84c923f31），只读原PNG RGB、不读OCR/GT；实际javac编译并测量all-scenes-startup-pass2-v1全部300帧。reports/m3-grid-refinement-v1/startup-original-signal-v1.json SHA b60d82083722b82aff4fa9c319669869fc0cf2d18de7669b8eccdad18170182b：精确RGB全黑55帧；另100、101、110、111、177最大亮度16/2/6/19/11，非零像素383000/222980/9056/10218/16807，均被GameViewportLocator的亮度>32像素数阈值判NO_SIGNAL。引擎将几何低信号直接叫black_frame的语义错误明确，但仅改成unknown/empty_target仍不能满足原non_target，不能声称修复验收。尚未提出/试跑生产修改，不消费或重置包围/几何耗尽预算。

实际node tools/audit-visible-state-errors.cjs data/gt/frozen/ocr-all-scenes-v2-observation-r7 reports/m3-grid-refinement-v1/ocr-regression-all-v7 reports/m3-grid-refinement-v1/ocr-v7-observation-r7-comparison reports/m3-grid-refinement-v1/visible-state-diagnosis-r7-v1 reports/m3-grid-refinement-v1/startup-original-signal-v1.json成功。逐38项验证GT/actual/评分绑定与时间关联，3368评分引用中1202状态名不一致（引用数，未冒称去重数）；全部逐帧明细保留mismatches.jsonl，报告SHA 236040a7077a1c95bbfaec1ecb442c1ff0057f57081c564f01e58b013899ebe8。非目标245分为146 empty_target、94 no_textbox、5 black_frame；另109 empty_target被输出no_textbox、654无箭头完整页被输出message_loading。报告只解释失败，不归一化计分标签或证明分类可行。来源verification与300原PNG SHA均绑定。

诊断结论：不能把“降低低信号阈值”或“所有空输出改一个标签”作为这245帧通用修复；必须先有输入可观察的场景证据与统一状态语义。该项尚有具体证据缺口，局部暂缓，目标不blocked；转向不依赖场景猜测的字段契约核对。当前r7/v7仍5901/86567编辑错、93.1833%逐帧/86.6908%等权、7/117页、2605失败帧、误报0/692。剩余原始编辑错主要为shop-exit3736、shop-sell-entry1039、approved-trial976；不能按成绩修改GT，只用其定位独立审计范围。

**当前唯一可执行下一步**：只读审计原approved-trial与同一道馆后续gym-sign-continuation的GT/契约/布局/已固定源打印规则，明确message行角色与dialogue、loading/complete_waiting与message_loading/message_complete是否具有任何可见语义差别，输出绑定原SHA的元数据审计和全部受影响范围；审计时不读actual作为答案。若无可见区别，仍须完成完整66原帧事件的新双遍证据后才按条件授权形成修订；未确认前不改GT/程序/评分。问号规范仍待答复，不阻塞独立工作。中文不启动，独立会话/UI/真实环境仍待验证；目标active，所有累计修复预算保持。


**道馆字段契约审计与完整双遍复核完成（2026-10-17）**：本轮按上一步只读审核全部38项冻结payload、原trial与continuation契约的10项相同评分规则及三个固定源码home/text.asm哈希。tools/audit-gym-dialogue-contract.cjs输出gym-dialogue-contract-r7-v1.json（SHA f7efcaebbf3a31f7eeeb94c50f9ae7342a1edd6ff3c3fe3bb2633c4aee391202），随后扩大相关上下文到PC/shop的v2（SHA 3766781bcc353fad647abb5f4252994886f503aa05084203a17aed110829077e）。5298/5299相邻原帧的同一行文字、位置、顺序、附标均相同，角色却从dialogue_line变为dialogue；不是整张PNG相同。全冻结中dialogue_line共有46区域状态，分布approved-trial、pc-connect、shop-exit三个事件，不能盲目全局改名；waiting与无箭头complete也不能直接混同。

原预定66帧材料实际含68帧[5262,5330)，额外5328/29仅上下文不扩大评分。首遍使用dialogue-sign-completion-pass2-v1，第二遍实际重新执行./tools/validate.ps1 export data/meta/m1-v1 video-02 5262 5330 data/frames/video-02/gym-dialogue-role-pass2-v1 1成功。RawRecheckSheets对两份全部68原PNG制作12页，当前编码代理顺序直接查看两遍全部页，核读期间未开GT或actual；先前元数据与首遍记忆已披露，不冒称独立会话/人工逐帧。两遍记录gym-dialogue-role-notes-v1/pass1.json SHA b6bd06629fda25da11840c2da3e6d5db30d570423f9416afedb1a9c51d503d84、pass2.json SHA f0e7f15ce8ec1e4b23b194d52cad187ba4a904e458e7b76e8507ceab2eab67b5；独立展开逐帧文字/箭头比较无差异，全部68 PNG字节及verification元数据一致，所有显示页哈希复检通过，extraction-and-pass-check.json SHA 6a7fc3975973beaff19d74d56c1db1e61d1d32434be5232e4188e4737906f7bd。确认5298/5299是同一普通对话连续行，5296/97滚动附标被裁、5286–95有等待箭头、5303–19无箭头、5320清框；并未据静止时长证明在线完成状态。

商店49原帧[1439,1488)材料已制作；首次shop-dialogue-role-pass1-sheets-v1误沿用video02裁剪框，查看000–002即发现上下/左侧文字被截，不作为完整核读证据，保留失败材料。直接检查1439全图后改用[176,0,612,544]完整游戏范围，重新生成shop-dialogue-role-pass1-sheets-v2共9页，全部显示像素与原图校验通过，尚未核读v2。一次verification单行输出截断，之后用完整JSON解析逐项比较68条而非依赖截断输出。本轮未改冻结/生产/评分、未重跑OCR或Maven，不消费或重置生产修复预算。有效版本仍r7/v7，5901/86567编辑错、93.1833%逐帧/86.6908%状态等权、7/117页、2605失败帧、误报0/692；未达标。

**当前唯一可执行下一步**：顺序核读shop-dialogue-role-pass1-sheets-v2全部9页，保存首遍记录后从video03重新导出[1439,1488)做第二遍；结合已验证PC连接完整51帧双遍证据，对三个dialogue_line上下文明确哪些角色确实错误，再合批生成仅字段角色修订候选并验证。未核实前不改GT；状态名继续独立保留。独立会话/UI/真实环境待验证，中文不启动，目标active。


**商店双遍完成、道馆r8角色修订交付（2026-10-17）**：上一目标轮为progress（道馆68帧双遍与契约审计）。本轮顺序重读spec/PLANS及计划分段，部分历史长输出遭截断并补读；不将截断输出当完整证据。商店shop-dialogue-role-pass1-sheets-v2全部9页49帧直接核读，首遍笔记SHA 34d226a3fdcf740e316e55bf5fb0522da3e0534dcdff0dbac8d9bba324b237d7；实际重新export video03 [1439,1488)到shop-dialogue-role-pass2-v1，exit0，完整9页第二遍直接核读后保存pass2 SHA 9c7aede8590db0731c4f03711df110b7a98829b31f88b1df38dbd8e7306708ba。两遍文字/光标/字段可见区间一致，49原PNG及完整verification元数据相同；extraction-and-pass-check SHA 276877a17df7cc501d660c167e013ae69441215bdd6e5a5e06b2c97984894ec0。核读期间未开GT/actual，既有诊断记忆披露，不冒称独立会话或人工逐帧。确认1450旧光标下完整单帧提示、1451光标移动、1462清消息、1464感谢加载、1468完整、1482所有菜单/金额/消息消失。

语义范围收敛：普通道馆5298/5299为同一行连续显示，role不同没有场景依据；PC连接主菜单提示有专门上下文，不因同名dialogue_line被全局改写。商店父菜单提示与sell-entry1044已有相同文字/物理格但角色与父字段顺序不同，且1439有已批准重叠修订，须独立合批审查其完整受影响范围；本轮不盲目把商店/PC都改为dialogue。

实际node tools/prepare-gym-role-correction.cjs仅生成approved-trial的19区域状态/24原帧[5275,5299)角色dialogue_line→dialogue，采用后续普通对话既有定义；候选SHA bcea58f995d75002d1421be6f92d7db331f52ff4eba5b4e231583ff968a91e79。恢复role后与旧GT逐对象完全一致，所有文字/Unicode/格与原矩形/顺序/状态/边界/页面/契约保持。实际gt-check通过35帧/14状态/1页结构。build-gym-role-correction-bundle.cjs重核四原件、r7全部payload、68双份PNG/verification、候选及完整性报告，7重复引用0冲突；新包gt-observation-corrections-v6全部68原图与新旧注释/笔记均保留。包SHA 0db7c511a53a783014e9faf138a08d7a8c134b7a2ddb158cbaa207d9a156849a。依用户高置信条件授权生成r8封套SHA e1b7d53332b3d3e6bd4d667072273d8fb80510b3df9a8f56aa98785acf5211f8，allSemanticIntegrityPassed/productAcceptance=false，UI实际呈现待验证，不冒称用户逐项批准。

实际compare-ocr r8 all ocr-regression-all-v7/actual → ocr-v7-observation-r8-comparison及独立analyze-ocr-differences完成：4925/86567编辑错、逐帧94.31076507214066%、状态等权89.20114371529383%、7/117内容精确页、2605失败帧、误报0/692。comparison SHA 3a0d2206cd96d119cf2cbed2ac97cdb9c9cb99b304526bcaba30fc7d6f2e52c4。check-gym-role-correction-delivery逐38项/3368评分引用核对仅24帧GT/计分变化、全部actual SHA不变、所有分母保持，报告SHA 5f852f0e4d0f225b27f1ddccfaecb4f37d5d519037f0f4ecce9cf0a37c089100。减少976错误来自字段标注纠错，不是算法提升。未改生产Java、未重跑算法/Java测试，没有重置修复预算；新增路径字节保护，未提交/推送。

**当前唯一可执行下一步**：以商店双遍笔记为依据，建立不读取actual的shop父菜单字段/阅读顺序契约审计，比较sell-entry1044–1068与exit1439–1482及rejection1439已批准重叠；明确父/子菜单遮挡的有意义差异，合批列出真正受影响字段/事件，再决定版本化修订或通用程序修复。问号编码仍待用户选择，不混入该角色工作；r8/v7未达标，独立会话/UI/真实环境待验证，中文不启动，目标active。

**商店字段审计与字符失败诊断（2026-10-17）**：实际运行 tools/audit-shop-field-contract.cjs，逐38项验证r8 payload身份，只读取GT/双遍材料、不读取actual或成绩作为审计答案。shop-field-contract-r8-v1.json SHA 136235f1de4dfe7b85cd6859ae279b2ce769242a163bd7a287e3c9e8812538e0：sell-entry1044与exit1450的7个物理字段（文字、格、原矩形、附标）全部相同，7项role/order均不同；rejection1439与exit1439的5项完全同约定。物理比较不含variable元数据，不声称金额变量规范相同。首次审计因markCells对象属性顺序导致JSON字符串比较断言失败，改用深对象比较后通过；未改GT。父/子菜单遮挡有可见意义，shop_prompt/shop_message不能全局替换；像素证明不一致，不能单独证明哪套任意角色命名更正确。本轮不生成商店候选或新冻结。

读取MenuFieldReader及其测试，发现SHOP_SELL_LIST统一返回shop_message而父菜单返回shop_prompt；这只定位潜在连续字段归属问题，未凭成绩改代码。另检查KanaScriptResolver，护士15错为ポケモンセンターへ→ポケモンセンターヘ，与最近明确片假名锚点选择共形字的规则吻合；不能以像素证明应选平假名，也没有加入场景答案或外部模型。

独立字符诊断：在读取失败记录后直接查看原video01 intro pass2的6408完整PNG，可见两个分离点，现GT为そして・而actual为そして・・。这次属于已接触答案的诊断，不算盲读、不确认冻结错误。tools/record-intro-punctuation-diagnostic.cjs绑定r8/GT/actual/比较报告与6408、6409原PNG，验证payload及actual SHA，输出intro-punctuation-diagnostic-r8-v1.json SHA 3114b94145391525464f1016c0c2cc020036c346d796c1359c7d8b7662b85c27。6409本轮只绑定身份，尚未直接看图。一条rg命令的Windows通配路径失败，未影响已解析的GT/actual证据。未修改生产、GT、评分或构建，未重跑回放/Java测试，累计修复预算均保持；r8/v7仍4925/86567错，94.31076507214066%逐帧、89.20114371529383%状态等权、7/117内容精确页，误报0/692，未达标。

**当前唯一可执行下一步**：关闭GT和actual，定位intro-research完整保留事件范围，核读其全部原帧并记录第一遍；从原录像重新导出同一范围完成第二遍，再对全部受影响状态核对标点出现边界，决定是否依条件授权作版本化修订。既有诊断接触必须披露；不能仅修6408/6409或凭OCR补点。商店角色约定和问号编码继续保留待定，独立会话/UI/真实环境待验证，中文不启动；目标active。

**开场标点完整双遍与候选完成（2026-10-17）**：上一目标轮为progress（商店契约审计和标点原图疑点改变下一行动）。本轮按序重读spec/PLANS/本计划，历史长输出部分被截断，已分段补读主要缺段；不将截断视为完整显示。有效r8封套只读SHA仍e1b7d53332b3d3e6bd4d667072273d8fb80510b3df9a8f56aa98785acf5211f8。

原intro-research完整保留[6402,6482)80帧、评分[6404,6480)76帧，前后文各2帧，保持原范围。RawRecheckSheets以原dialogue-intro-research-reviewed-v1生成intro-punctuation-pass1-sheets-v1，完整14表全部直接查看后保存intro-punctuation-notes-v1/pass1.json，SHA 0d692ca965bcd2b76b4ce3363145961952c7e8800cf0df80558a0023155f24ca。实际./tools/validate.ps1 export data/meta/m1-v1 video-01 6402 6482 data/frames/video-01/intro-punctuation-pass2-v1 1，会话8796确认exit0；新pass2-sheets-v1完整14表全部直接核读，二遍笔记SHA d9305138c5c71830f187bfa4303b6126a1ffe0e02a013cc9aa9f415bddcb3d9b。两遍核读期间关闭GT/actual，披露先前诊断接触及同代理记忆；二遍完成后沿用一致的span表示，不称独立会话或用户逐帧核验。全部80原PNG字节、原时间/RGB元数据一致，28展示表逐SHA复检，extraction-and-pass-check.json SHA 28369d2cf330fd0cad57f03455612f78aecd191d7d7ae0589cee3d540a1f7570。

原图确认6408/6409均已出现第二个点，6410才有第三点及下箭头。6429清文字、6438第二页起字、6453完整、6467开始淡出、6469箭头消失、6476全图空白；未凭静止判断在线完成。完成双遍再比较GT时发现笔记把6419误记为无箭头；重新直接打开6418/6419/6420完整原PNG，三帧均有箭头。另存notes-correction.json明确纠正代理笔记、原笔记不覆盖，不改正确GT或声称原笔记完全正确。此前extraction-and-pass-check只证明两遍记录彼此一致及原帧身份，并不替代语义核查。

实际node tools/prepare-intro-punctuation-correction.cjs逐76评分帧比较核读文字与旧GT，除6408/6409外均相同；光标按完整原图纠正后的观察亦全部与旧GT一致。仅生成data/gt/candidates/ocr-observation-corrections-v7/intro-research/ocr-candidate-punctuation-v1.jsonl，SHA 6aacd78b33e72d1743f2bc59a3bdda083089fd2b1cce974f6d9477ddb730955b：1状态/2帧そして・→そして・・，grid右界5→6，source右界320→336，更新该状态的复核依据并如实披露诊断接触。其他所有状态逐对象相同，范围/PTS/角色/顺序/光标/页面/规则不改；新字符分母预计+2，不能为保持旧分母漏掉实见点。

实际./tools/validate.ps1 gt-check data/meta/m1-v1 data/gt/candidates/ocr-observation-corrections-v7/intro-research/dialogue-intro-research-clip-v1.json data/gt/candidates/ocr-observation-corrections-v7/intro-research/ocr-candidate-punctuation-v1.jsonl data/gt/candidates/ocr-observation-corrections-v7/intro-research/layout-v1.json reports/m3-grid-refinement-v1/intro-punctuation-candidate-integrity-v1.json通过76评分帧/19状态/2页结构检查。候选尚未装入有效封套，未改生产/评分、未跑算法/Java测试、未重置修复预算；当前仍r8/v7的4925/86567错、94.31076507214066%逐帧/89.20114371529383%等权、7/117页、误报0/692。

**当前唯一可执行下一步**：将intro-punctuation-notes-v1的完整80帧双遍、笔记纠错、新旧注释和结构报告合为gt-observation-corrections-v7审阅附件，重核四原件/全部r8 payload后依条件授权生成r9封套；复用封口v7 actual复算并独立检查只6408/6409变化、字符分母+2、其他帧/状态/页分母及所有actual SHA保持。该纠错不是算法提升；独立会话/UI/真实环境继续待验证，中文不启动，目标active。


**开场标点r9交付与同输出复算完成（2026-10-17）**：实际 tools/build-intro-punctuation-correction-bundle.cjs 重核四原件、r8全部payload、完整80帧双遍材料和笔记纠错，生成gt-observation-corrections-v7（bundle SHA f056d91cb413f9b75e912d5d8f1669a08309ded76244a98a782eecba97183fc4）及r9封套（SHA c3a110eb72418d7939112ff8fe264f1b2fa4b78b8133fa711f3a7b7eecb37b66）。依用户高置信纠错授权，保留allSemanticIntegrityPassed/productAcceptance=false，不冒称逐项人工批准。

实际compare-ocr r9 all复用ocr-regression-all-v7/actual，再运行analyze-ocr-differences.cjs独立计数，4923/86569编辑错、94.31320680613152%逐帧、89.20831191457472%状态等权、7/117内容精确页、2603失败帧、误报0/692。check-intro-punctuation-correction-delivery.cjs逐38项3368评分引用验证：仅intro-research6408/6409的GT和评分变化，字符分母+2，714状态/117页/692空帧保持；所有actual SHA相同。交付检查observation-r9-delivery-check-v1/report.json SHA 9cd02cbd2e0d0bdff256a8a2639c460af8ba6537a793757dfec78fa05c93ff1f。改进来自GT纠错，不是算法提升。辅助文件首次生成因Windows python占位程序不可执行失败，随后Node生成并执行成功；未改生产代码或重置修复预算。

**当前唯一可执行下一步**：读取lab-oak-reply13438与oak-delivery24223的失败记录和程序候选细节，定位多出/漏掉单字符的像素或时序原因，先确认是否属于已有耗尽预算的问题；建立有原帧身份的最小复现，再决定通用程序修复。不得将actual当GT答案或硬编码场景文本。独立会话/UI/真实环境待验证，中文不启动，目标active。

**两处博士对话单字符失败诊断（2026-10-17）**：读取r9独立差异记录与v7相邻帧候选，13438多出感叹号、24223少一个の；两处几何均origin240,96/scale2，registration无偏移，未发现足以支持修改算法的证据。随后直接查看两张完整原PNG：13438实见感叹号，24223下行实见とどけも而后格空白。此查看发生在GT/actual暴露后，仅为诊断疑点，不能代替完整事件双遍或直接确认冻结错误。tools/record-oak-boundary-diagnostics.cjs核对两事件全部payload、r9封套/比较报告及actual SHA，保留完整期望/输出/原图身份，报告oak-boundary-diagnostics-r9-v1.json SHA 06714bfffb1529df0b1c003ea1ee062ecc217b200a0f5249f3e017afc3101f95。未修改生产或两事件GT，未运行OCR/Java测试，没有消耗或重置修复预算；当前r9/v7指标保持。

**当前唯一可执行下一步**：关闭GT/actual，顺序双遍核读lab-oak-reply完整[13429,13512)83帧及oak-delivery完整[24211,24325)114帧，第二遍从原视频重新导出；完整记录后再核对全部状态和附标边界，合批决定是否形成高置信版本化修订。既有诊断暴露继续披露，不冒称独立核验；独立会话/UI/真实环境待验证，中文不启动，目标active。

**博士对话完整双遍、r10纠错交付（2026-10-17）**：上一轮为progress（r9交付与两处原图诊断推动完整事件复核）。本轮依序完整阅读spec/PLANS/本计划，长输出截断已分段补齐。核读lab-oak-reply完整[13429,13512)83帧、oak-delivery完整[24211,24325)114帧；首遍33张原像素表全部直接查看，保存oak-boundary-notes-v1/{lab-oak-reply,oak-delivery}-pass1.json。首遍SHA分别df536a188d2a65dee13525deb45d86de195f6ca13fce645ebf5d6d81397d1e9a、bd3af2581cebe61a47c34f876b0a376470e7182565e86ae25ccce40eb49526f8。

实际执行 ./tools/validate.ps1 export data/meta/m1-v1 video-01 13429 13512 data/frames/video-01/oak-reply-boundary-pass2-v1 1 及对应24211 24325 oak-delivery-boundary-pass2-v1，两次确认exit0。RawRecheckSheets生成二遍14+19表并全部直接核读；tools/record-oak-boundary-second-read.cjs保存二遍观察，不从OCR推断。两遍GT/actual均关闭，披露既有诊断、历史记录及同代理首遍记忆，不称独立会话或人工逐帧验收。

tools/prepare-oak-boundary-correction.cjs复检197原PNG字节、RGB/PTS元数据、66展示表身份及两遍每帧文字/箭头一致；将完整187评分帧与现GT比较，并按观察核对全部行列、文字矩形和附标。唯一文字差异是13438漏记已出现的！、24223提前记入未出现的の。候选v8：lab-oak-reply SHA 59f91be59d9dc900d96c527c15e038a9d4c79f3a3914df259d8d03b201053efb，13438文字オーキド『まー→オーキド『まー！，grid右界8→9/source368→384；oak-delivery SHA bfce42c4eb6818fdfd15a4c49709713966e7a0436220fa888a024fea5581fd6a，24223とどけもの→とどけも，grid右界6→5/source336→320。只更新两状态必要文字、矩形及证据，其他所有状态逐对象保持；字符分母+1/-1净0，未改帧、状态、页或评分标准。分别gt-check通过79帧/18状态/2页、108帧/12状态/1页结构检查。无箭头静止页的在线完成判断没有从本次核读推定解决。

tools/build-oak-boundary-correction-bundle.cjs重核四录像原件和全部38项payload，保留197原帧、两遍笔记、新旧注释和结构报告，统一生成gt-observation-corrections-v8（bundle SHA 001e3d716befc9acef58c0827d04e16d1f78a41815e8e8ae2ac5d593e2544d24）及r10封套（SHA fd098d60c720dfad568475d6ee16ba71e5338e9faa22982c80cf5b6590b6ace2）。依用户高置信纠错授权，保留allSemanticIntegrityPassed/productAcceptance=false；审阅HTML生成但UI呈现待验证。

实际 ./tools/validate.ps1 compare-ocr data/gt/frozen/ocr-all-scenes-v2-observation-r10 all reports/m3-grid-refinement-v1/ocr-regression-all-v7/actual reports/m3-grid-refinement-v1/ocr-v7-observation-r10-comparison，并以analyze-ocr-differences.cjs独立复算：4921/86569编辑错、94.31551710196491%逐帧、89.22125103600838%状态等权、7/117内容精确页、2601失败帧、误报0/692。tools/check-oak-boundary-correction-delivery.cjs逐38项3368评分引用验证，仅lab-oak-reply13438/oak-delivery24223的GT和评分改变、所有actual SHA及分母保持，交付报告observation-r10-delivery-check-v1/report.json SHA 079cbd99452624cd38d14ef8f8e0425e735f3604c941577669a30b34c8b82ded。减少2错误来自GT纠错，不是算法提升。未改生产代码、未跑新OCR/Java测试、未重置任何修复预算；新增字节保护，未提交/推送。

**当前唯一可执行下一步**：基于既有shop-field-contract-r8-v1.json与完整商店双遍笔记，读取shop父菜单原始角色定义和首次批准契约，确定同像素7字段在sell-entry/exit间的role/order冲突应采用的统一契约，列出全部受影响状态后实现合批契约纠正或通用MenuFieldReader修复；不凭actual选择GT，不全局替换子菜单字段，不混入未决问号编码。r10/v7未达标，独立会话/UI/真实环境待验证，中文不启动，目标active。

**商店字段契约溯源与消息连续性修复v8（2026-10-17）**：本轮按顺序完整阅读spec/PLANS/ExecPlan。核查原始record-shop-first-v1、shop-exit候选及已明确批准的shop-metadata-r2：1439重叠帧曾统一到较早退出片段的shop_menu_choice/shop_money_label/money_amount及屏幕从上到下顺序；sell-entry父菜单则仍用menu_label/money_label/money和菜单优先顺序。既有shop-field-contract-r8-v1已证明同物理七字段冲突。后续父菜单应沿用已批准的较早约定，不能依actual选择；角色名称是标注契约，不能冒称像素直接证明其英文拼写。本轮没有修改任何GT或评分契约，父菜单合批纠正仍未完成。

为确认另一项可独立修复的运行时错误，重新核读卖出入口完整[1042,1104)62帧。首遍来自all-scenes-shop-sell-entry-pass2-v1，用RawRecheckSheets生成shop-sell-contract-pass1-sheets-v1的11表并全部直接查看；执行export data/meta/m1-v1 video-03 1042 1104 data/frames/video-03/shop-sell-contract-pass2-v1 1后，另生成二遍11表并全部查看。两遍记录shop-sell-contract-notes-v1/pass1.json SHA 17a8db673f3d5440ca2cc248fe5c7131a9c4bf172987c29e6dfdb502edf18ab9、pass2.json SHA b8a2ac43357d088a8141086f91ee821b78b1ce2f47929f790c6654f44bc144af。观察确认1063起的卖出问句在1068列表局部重绘、1069完整叠层和其后光标移动时一直保持；1063–1068金额暂清、1068仅下三商品行、1069金额恢复、1070/1092光标及箭头闪烁均登记。两遍没有打开被测输出；披露先前注释与契约诊断暴露、同代理记忆，不称独立会话或人工逐帧验收。问号笔记采用全角呈现，不作新的Unicode裁决。

根因：MenuFieldReader.messageRole只依当前布局将SHOP_PARENT的shop_prompt切换为SHOP_SELL_LIST的shop_message，即使底部消息没有换文。新增ShopMessageRoleTracker，仅对连续商店上下文中相同行位/文字签名保留前一消息角色；换文、空消息、非商店、无文本框或reset清除记忆。JapaneseOcrEngine在确定完整当前签名后再构造消息区域。没有片段名、帧号、固定问句或未来帧条件；没有填补文字、改变完成状态或改问号编码。这是该独立问题第1/3次修复，既有耗尽预算保持。

实际mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' test通过132项（app66/verifier66）；日志shop-message-continuity-tests-v1.log。新增3测试覆盖叠层连续性、换文/清空/非商店/reset边界、冷启动列表与行位变化。snapshot-ocr-implementation.cjs生成build-evidence-v8，165源文件/87编译类；本轮未重新package，旧JAR不得冒称v8。实际ocr-replay data/gt/frozen/ocr-all-scenes-v2-observation-r10 all reports/m3-grid-refinement-v1/ocr-regression-all-v8完成全部38项3577保留帧引用、3368评分引用。check-shop-message-continuity.cjs核实62原PNG/PTS元数据和两遍观察一致、22表身份；逐3577输出比较，只有sell-entry1068–1103共36区域role/id由shop_message变shop_prompt，其余字段文字、矩形、readingOrder、标记、几何、注册和visibleState逐对象相同。检查报告shop-message-continuity-regression-v1.json SHA ed66059fcb29eb6a23e036ea76070a7e3a1a79b2264aed92c2958703bb1ec39d。

实际analyze-ocr-differences.cjs独立复算与比较器一致：3935/86569编辑错（较v7减少986，来自严格字段匹配改善，非字形改善）、95.45449294782197%逐帧、90.34695460073633%状态等权、7/117内容精确页、2601失败帧、误报0/692。报告ocr-regression-all-v8/comparison/report.json与ocr-v8-observation-r10-text-analysis/report.json。r10封套SHA仍fd098d60c720dfad568475d6ee16ba71e5338e9faa22982c80cf5b6590b6ace2；没有删帧、改GT或放宽标准。M3/M4仍未通过，独立会话/UI/真实环境待验证，中文不启动。

**当前唯一可执行下一步**：在已完成shop-exit49帧与sell-entry62帧完整双遍证据上，枚举全部商店父菜单未遮挡状态，按明确批准的shop-metadata-r2较早角色/顺序约定准备合批元数据纠正；保留子菜单/遮挡区域定义及未决问号，核验1439重叠一致后再生成新封套并实现对应通用父菜单输出修复。先验证再记录，目标保持active。

**商店父菜单契约合批纠正r11与通用修复v9（2026-10-17，回放运行中）**：上一轮为progress，本轮按序完整读完spec/PLANS/ExecPlan。沿既有111原帧完整双遍证据与明确批准的shop-metadata-r2约定，prepare-shop-parent-correction.cjs仅调整sell-entry1044–1101共58评分帧/15状态的61处区域角色或顺序：完整父菜单24帧使用shop_money_label/shop_menu_choice/money_amount/dialogue_line和屏幕顺序；1068以后仅未换文问句保持dialogue_line，列表字段不改。英文角色是既有契约一致化选择，不冒称像素直接证明。无actual/评分参与选择；文字/Unicode、变量属性、矩形、标记、状态、页、时序不变，其他37项不变，问号仍待定。

实际gt-check通过58帧/15状态/4页结构检查；候选SHA 553c62dc97c6e2a0925d8b1625b5c6f67792e92c66337db60206a5b3befa975b。build-shop-parent-correction-bundle.cjs重核4原录像及全部payload，保留111原帧/两遍观察及4商店控制项；v9审阅bundle SHA 575968518de0676b7edeb261ef5b38c4d75afb2dea7759d3e591d140bf77a82d；r11 freeze SHA 715b7297c1bd4327c6a8a50a93ddee7fef2410b8b25c4f2d4c2f4ac8a54199dd。7重叠引用无冲突，3361唯一评分原帧。依用户高置信纠错授权版本化，allSemanticIntegrityPassed/productAcceptance=false，同代理历史暴露披露，UI/独立会话待验证。

实际compare-ocr r11 all ocr-regression-all-v8/actual ocr-v8-observation-r11-comparison；独立analyze-ocr-differences复算7139/86569错误，91.75339902274486%逐帧、84.35614137695423%状态等权、7/117页、2601失败帧、误报0/692。旧输出在统一契约下更差，不把GT纠错当算法提升。check-shop-parent-correction-delivery.cjs逐38项3368评分引用核验，只有58帧GT/评分变化且全部actual身份与各分母不变，可逆恢复旧注释；报告shop-parent-correction-delivery-v1.json SHA 3df2a29e7f3521c079c199db2d2516fbc347b1f555e6683f7c7b8dbe1a483059。

MenuFieldReader的完整SHOP_PARENT分支按已批准角色/屏幕顺序输出；列表/遮挡分支保持，底部消息用dialogue_line，沿v8连续性机制跨叠层保留。新增有金额/无金额两场景测试，动态金额37円而非录像金额，防止空金额被补全。实际离线Maven test通过133项（app67/verifier66），shop-parent-role-tests-v1.log；该独立问题第1/3次修复，不重置旧预算。build-evidence-v9记录168源文件/87编译类；未package，不将旧JAR称v9。实际ocr-replay r11 all ocr-regression-all-v9正在运行，会话81654；不得将启动或退出码代替准确率结论。

**当前唯一可执行下一步**：等待既有81654回放完成，独立复算v9并运行check-shop-parent-runtime.cjs核实全部3577保留帧只改商店角色/顺序，再记录最终指标与剩余问题。目标active，中文不启动。

**v9全量封口（同日）**：既有会话81654正常exit0，38项actual/比较报告齐备。独立analyze-ocr-differences.cjs复算 **211/86569编辑错，99.75626378957826%逐帧、99.58548730436576%状态等权**（64.07122736418512等权错/15457字符），首次在选定开发范围达到两项99.5%字符门槛；仍7/117内容精确页（5.9829%）、2601失败帧、空/非目标文字误报0/692，最终内容指标与raw一致，不满足整页95%及最终100%。不据此宣称M3/M4整体或独立验收通过。

比较报告ocr-regression-all-v9/comparison/report.json SHA 75022c919899d33ec6999bf86a7e7d69a630d9fad91243a441c33fb31661f455；独立文本分析ocr-v9-observation-r11-text-analysis/report.json。check-shop-parent-runtime.cjs逐全部3577保留帧匹配物理区域，证明除108帧487区域的role/id/readingOrder及区域排列外，所有glyphCandidates/pixelCandidateText/最终text、矩形、附标、光标/状态、配准、诊断框、源帧/PTS均完全一致；仅sell-entry/exit/rejection改变，买入及非商店保持。检查报告shop-parent-runtime-regression-v1.json SHA ced647370ce33d135b51d06a3f14decb47504a5af6830901096a6aed95370ef5。固定r11下由v8的7139错降至v9的211错，6928差额来自严格字段对齐恢复，不能称字形改善；与旧r10/v8的3935差额混合了契约和实现影响，不用作纯算法比较。

剩余按片段引用：PC106、sell53、rejection5、exit12、nurse15、status-alt1、backpack19（重叠去重后总211）。问号编码仍未决，不用按片段硬编码；3541稀疏附标预算3/3及几何已耗尽预算不重置。未改评分逻辑/阈值、未删帧、未建设中文、未提交/推送。r11有效资格仍有限；独立会话/真实UI/真实环境保持待验证。当前轮为progress（契约证据、通用实现、完整回归及独立复算完成）。

**当前唯一可执行下一步**：关闭GT/actual，对video-04背包导航完整[5200,5366)166原帧重新连续双遍核读（第二遍从四原件中的video-04重新导出），核对所有滚动、重复行与附标边界后再与冻结注释比较，决定是否存在可高置信版本化纠错；已有错误诊断暴露如实记录，不称独立验收，不只选择5347/5349等失败帧，不触碰已耗尽的3541算法修复预算。目标保持active。

**背包导航完整双遍复核（2026-10-17）**：上一轮为progress，本轮按顺序完整阅读spec/PLANS/ExecPlan。首遍直接核读既有annotation-recheck-backpack-navigation-pass1-v1的166原帧、blind-recheck-sheets-pass1-v1/backpack-navigation全部28表，并补看5301/5304/5306/5347/5349完整PNG。record-backpack-scroll-first-read.cjs保存pass1.json SHA 8768763dde0a439de709cf237ba599bcabb6ebdf9737b4051359642ddeb8cf89。实际执行 ./tools/validate.ps1 export data/meta/m1-v1 video-04 5200 5366 data/frames/video-04/backpack-scroll-pass2-v1 1，exit0且166帧RGB回读核验通过；RawRecheckSheets生成backpack-scroll-pass2-sheets-v1全部28表并逐张直接核读，record-backpack-scroll-second-read.cjs保存pass2.json SHA f7aa1df8f051264243f2f58e35fa3878fcf36fcb5e8d1f10586b9caa343993ea。两遍关闭GT/actual；既有诊断/计划暴露、同代理记忆明确披露，不称独立会话或人工逐帧验收。

双遍均观察到5301/5304/5306/5347/5349第二行基础文字已换、部分上方旧附标尚存；特别5347第一行仍为ポケモンのふえ，5349第一行仍为いいつりざお，不能用下一稳定帧倒填；5330同帧出现两光标。完整文字段、附标坐标、光标段、下箭头闪烁已登记。5359关闭过渡有遮挡，继续保留不确定说明，不扩入评分。以上为原帧观察，尚未与冻结GT逐项对照，不作冻结错误最终结论。

实际check-backpack-scroll-reads.cjs验证两遍166张PNG逐字节相同、RGB/PTS/尺寸等元数据逐对象相同、56展示表哈希一致，并核对两遍文字段/附标/光标/箭头记录一致；read-integrity.json SHA b014a8ecf7bef89db7e7f885c4bc0b05dcd68922171a21a183d53d5263ce3aca。记录位于reports/m3-grid-refinement-v1/backpack-scroll-notes-v1。未修改GT/生产实现、未运行新OCR/Java测试、未重置修复次数。当前仍r11/v9：211/86569编辑错、99.75626378957826%逐帧、99.58548730436576%状态等权、7/117内容精确页、2601失败帧、误报0/692；独立会话/UI/真实环境待验证，中文不启动。

**当前唯一可执行下一步**：在已保存完整双遍观察基础上，将背包导航全部152评分帧/47状态与现冻结注释逐项核对（文字、附标、矩形、光标、箭头及状态），枚举全部差异和5206–5207重叠一致性；只有证据清晰的差异才依用户高置信纠错授权形成合批版本化候选，不能依据OCR输出挑选修订。目标active。

**背包滚动r12纠错与同v9复算完成（2026-10-17）**：上一轮为progress（完整双遍与166帧身份验证）。本轮按序完整重读spec/PLANS/本计划，长输出截断的229–241行另补读。audit-backpack-scroll-observations.cjs对全部152评分帧/47状态比较双遍手工观察的文字、格/原矩形、附标、光标、下箭头、kind及完整状态，并核对5206–5207与backpack-opening重叠字段一致。对照发现双遍笔记本身有三处箭头误记，直接重看5214/5215/5216、5235/5236/5237、5323/5326完整原PNG，确认5215/5323有箭头、5236无箭头；数量乘号/数字列号也从误记16/18纠为15/17。另存notes-correction.json，原两遍笔记不覆盖，正确GT保持。原read-integrity只证明记录一致及原图身份，不冒称消除了核读错误。

完整比较frozen-comparison.json SHA d5757eb14566228f98f15cec3a8bed136d6d0858e331eeaae0de608bce122ec9只发现5301/5304/5306/5347/5349五帧文字/附标/必要矩形差异。5301第二行实为ポロのつりさお并有两旧孤立标记；5304第二行いいつりさお及两孤立浊点；5306第二行ホケモンのふえ及の上旧浊点；5347首行仍ポケモンのふえ、第二行ホロのつりさお及り上旧浊点；5349首行仍いいつりざお、第二行ビーヒーエイター × 1及イ上旧浊点。没有借后帧恢复稳定词。其余147帧所有被比较字段一致；全范围cursor/kind/completePage/页机会、时间、角色和阅读顺序保持。

prepare-backpack-scroll-correction.cjs生成候选data/gt/candidates/ocr-observation-corrections-v10/backpack-navigation/ocr-candidate-scroll-v1.jsonl，SHA 09399b146b0ab8cee1e07f566be4051032e8c307dd791e7cc012d1c864c2e50a，字符分母净0。实际gt-check通过152帧/47状态/16页结构，backpack-scroll-candidate-integrity-v1.json。build-backpack-scroll-correction-bundle.cjs重新核对四原录像、r11全部payload、166双份PNG/PTS/RGB和候选，生成完整166原帧/新旧注释/双遍/笔记纠正附件gt-observation-corrections-v10，bundle SHA 999386750d8b98a082c89d7683bbdf26c4a7b7ce2e67028527b55ae3771c5e2f。全7重叠引用无冲突、3361唯一评分帧保持。依用户高置信纠错授权生成r12 freeze SHA 3949812dd750e62fb8bd562465f0743b928c48310d73919f520db0cce8a7ea8a；allSemanticIntegrityPassed/productAcceptance=false，UI/独立会话待验证，旧版全部保留。

实际compare-ocr r12 all ocr-regression-all-v9/actual → ocr-v9-observation-r12-comparison，独立analyze-ocr-differences复算 **192/86569编辑错、99.77821159999538%逐帧、99.70840895798547%状态等权**；仍7/117内容精确页、2601失败帧、空误报0/692。check-backpack-scroll-correction-delivery.cjs逐38项/3368评分引用证明恰五帧GT及计分改变、全部actual SHA及所有分母保持，observation-r12-delivery-check-v1/report.json SHA d0f19e7ef06b497aec2a4c97cb2f49bc8ef2e5b5447946b3f0a581c0b789379c。减少19错仅来自GT纠错，不是算法提升。未改生产代码/评分规则、未重跑OCR/Java测试或重置修复预算。检查器生成首次shell引号错误导致Node解析失败、没有产物，改字面here-string后成功；原输出保留。新增路径字节保护，未提交/推送。

**当前唯一可执行下一步**：检查nurse-greeting剩余15个へ/ヘ共形歧义的原始候选、KanaScriptResolver和已审计日文打印来源，确定不按片段硬编码、可保留原始输出与修正理由的确定性日文辅助消歧方案；先形成来源与歧义边界证据，再实现并验证，不能修改正确GT或混入中文。问号规范仍待用户答复；几何模型/3541等已耗尽预算保持，独立会话/UI/真实环境待验证，目标active。

**同形假名来源审计与失败分拆（2026-10-17）**：上一目标轮为progress（r12纠错及同v9复算）；本轮按序完整读取spec/PLANS/本计划，170–310段输出截断处另补读229–253。只读复核r12封套SHA仍3949812dd750e62fb8bd562465f0743b928c48310d73919f520db0cce8a7ea8a，KanaScriptResolver与v9源快照SHA同为d04de2bc5234addd010371e889526e0d473a95a83853729e2371437f2b0e34e2。现有最近明确假名锚点规则对1841–1851将末字へ/ヘ选择为ヘ；不能凭同一图块证明应该选哪种Unicode。

实际 node tools/audit-kana-source-ambiguity.cjs reports/m3-grid-refinement-v1/kana-source-ambiguity-v1 完成三固定来源HEAD/Git对象与已有UI审计48文件逐SHA核对，另外读取各home/text.asm及constants/charmap.asm原Git字节（GIT_NO_LAZY_FETCH=1，不执行外部代码）。三版へ/ヘ均映射cd；#经PlacePOKe/PlacePOKeText展开为ポケモン；各欢迎语源码明确#センターへ。仅提取所有已审计文件中整条text/line/cont/para字面量、显式#展开和游戏空格显示，排除动态控制/变量/拼接，得到414来源条目/126不同字面量。该范围不是完整日文语料，不据其局部唯一性断言游戏全范围无冲突。

独立诊断遍历同v9全部38项3577保留帧/14402区域，逐actual SHA绑定；2352区域含同形候选。要求源码字面量与当前可见字符数完全一致、逐字都在当前候选内、全部匹配来源字面量一致、只准改变平片同形配对，不补未来/浊点/片段答案。结果仅护士1841–1851共11区域产生辅助候选，无目录内多字面冲突；没有写回actual或运行时。报告kana-source-ambiguity-v1/report.json SHA 6d0b140b157ca032c29381250c4ac399b0669278e5907cb218dbca89067efe0a，保存来源定位、全输入SHA、原始候选/既有锚点理由及建议变化。未知/冲突应保持原始结果，有限来源候选只能作为显式辅助解释，不得修改raw或据此判页面完成；未来生产资源只能来自已审计来源配置，不能加载本诊断的GT/片段/帧号。

完整错误分拆纠正此前“护士15错均为へ/ヘ”的不准确描述：实际11错为同形，另4错在1920/1921两帧的ここでは ポケモンの→ここては ホケモンの，当前候选只有て/ホ，不能借来源补浊点。failure-partition.json SHA 43ddee476b78539b6c7de06d09c0948c21487186f6329936540de40ab8004c53绑定独立分析/actual/完整区域。本轮未直接重看该两帧，尚不能判为GT错误或生产漏检。两次只读路径错误（错误source id、Windows通配路径）已通过实际manifest路径定位，不改素材。没有修改生产/GT/评分或运行新OCR/Maven，不消费或重置算法修复次数；当前仍r12/v9：192/86569错、99.77821159999538%逐帧/99.70840895798547%等权、7/117内容精确页、2601失败帧、误报0/692。独立会话/UI/真实环境待验证，中文不启动。

**当前唯一可执行下一步**：实现独立可选的JapaneseSourceAliasResolver及来源配置加载，将本次审计的整行、同形候选、全匹配一致限制编码为通用规则；原始Region及候选保持不可变，修正只进入correctedRegions并附来源/差异理由。先验证冲突、未知、变量、短行、缺浊点和换页不补字，再接入独立回放对同r12全38项检查raw完全不变及final变化；1920/1921四错单列待原图复核，不混入同形修复。问号选择仍待答复；目标active，所有既有耗尽预算保持。

**日文来源辅助同形消歧实现及全回放（2026-10-17，修复1/3）**：本轮按序完整读spec/PLANS/本计划后实现可选JapaneseSourceAliasResolver。它只接收当前行raw和glyphCandidates及显式来源条目，无GT、片段/帧号、时间、跨帧缓存；要求整行等长且逐字符兼容，全部匹配字面量一致，只允许当前候选内平片同形配对改变。未知、冲突、短行、缺浊点、变量、非假名标点歧义保持不变。此来源集并非完整游戏语料，唯一匹配只称显式辅助解释，不认证页面完成，不改raw或状态。Entry/Resolution保留来源版本/文件/行/SHA及逐字变化理由。

build-japanese-source-aliases.cjs独立核对固定三来源Git对象/HEAD、48已审计文件和renderer/charmap，从来源生成.cache/japanese-source-aliases-v1/catalog.json（414条，SHA 2eb7e1fd2a393230cb426b565e52c1e110f040d9ce35cf0bbd52c979800192e3）。该构建器无GT/actual/诊断报告答案输入，游戏文本留本地cache，不纳入JAR。SourceAliasConfiguration使用显式expected SHA验证外部配置，OcrReplay新增显式ocr-replay-assisted入口；默认回放不启用。辅助层在engine.process返回后操作序列化副本，只有实际变化才添加correctedRegions及非空correctionEvidence，保留rawRegion/候选/geometry/visibleState，评分器未改。

实际mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' test通过142项（app74/verifier68），0失败/错误/跳过，source-alias-tests-v3.log。新增7算法边界测试及2加载/副本完整性测试。首次调用漏项目settings，离线仓库ID不匹配而在编译前失败，source-alias-tests-v1.log保留；补settings后v2的140项通过，增加配置测试后v3通过。此环境调用失误不算算法修复尝试。build-evidence-v10保存回放开始时182源/92类；补测试及检查器后的build-evidence-v10-final保存184源/92类，逐类SHA相同，未打新JAR，旧包不能称v10。

实际tools/validate.ps1 ocr-replay-assisted data/gt/frozen/ocr-all-scenes-v2-observation-r12 all reports/m3-grid-refinement-v1/ocr-regression-all-v10 .cache/japanese-source-aliases-v1/catalog.json 2eb7e1fd2a393230cb426b565e52c1e110f040d9ce35cf0bbd52c979800192e3完成38项3577保留帧引用。check-source-alias-regression.cjs独立逐帧检查v9/v10除耗时及新增辅助字段外所有输出对象相同，所有原始区域/候选/注册/几何/状态不变，原始计分逐项相同；逐时间线PTS、duration、RGB SHA、scored、offset全部一致。仅nurse-greeting1841–1851共11区域ポケモンセンターヘ→ポケモンセンターへ，每条有来源和差异证据，原raw未变。source-alias-regression-v1/report.json SHA ccb657e0ad9b4e2d8169f615103f92e9c744551185fceaa074f567bf25247ea4。另source-alias-input-integrity-v1.json流式核对四原件及帧账本SHA、r12全部payload，封套仍3949812dd750e62fb8bd562465f0743b928c48310d73919f520db0cce8a7ea8a。

当前r12/v10：原始192/86569错、99.77821159999538%逐帧、99.70840895798547%等权不变；最终181/86569错、99.79091822707897%逐帧、99.7148785187023%等权。原始/最终均7/117内容精确页、2601失败帧、空误报0/692；3368评分引用/3361唯一帧、714状态、117页保持。已实现/构建通过/离线回放改善，不是独立验收通过；M3/M4完整门槛未过，独立会话/UI/真实环境待验证，中文不启动。未修改GT/标准、未引入其他模型、未提交/推送；其余累计修复预算不变。

**当前唯一可执行下一步**：对nurse-greeting1920/1921缺浊点四错执行原实机完整帧及相邻过渡的两遍核读（核读过程中隐藏actual/GT），先保存观察和帧身份，再与冻结注释及附标候选对照，判断真实短帧状态还是检测遗漏；不得用来源词句补字，若确认冻结错误仅按用户高置信授权版本化纠正。问号规范仍待用户答复；目标active。

**护士问候消标过渡完整复核（2026-10-17，本轮未消耗算法修复次数）**：依序恢复阅读spec/PLANS/本计划后，完成video-03完整保留事件[1823,1925)共102帧的两遍直接核读，每遍17张原像素联系表。第一遍使用annotation-recheck-nurse-greeting-pass1-v1，第二遍从原录像重新export至nurse-mark-pass2-v1；两次verification SHA均d89fbeff67a80a1372a5eabb79e97e739182bd11bff8a04b455b49ab4764c4fe。第二次export和RawRecheckSheets均退出0，所有102 PNG逐个SHA相同、34张联系表SHA及显示像素一致性检查通过。未读GT/actual期间先落盘pass1/pass2；已知历史诊断和同代理记忆明确披露，不称完全盲读、人工逐帧或独立会话验收。

观察：1920/1921的正文上行实际为「ここては ホケモンの」，row13 column3/6附标消失而基础字仍在；1920菜单及「ず」浊点仍在，1921菜单消失，1922正文清空。完整事件的加载、清屏、短页和光标均核读，1864第二行「たいり」、1865才出现小「ょ」；へ/ヘ同形仍仅按既有转写规范，不以像素证明Unicode。两遍结构观察一致。

新增tools/audit-nurse-mark-observations.cjs，实际node运行通过，先检两遍原件/表格/观察身份，再按观察生成所有98评分帧的文字、角色、顺序、格坐标、原像素区域、附标、菜单光标/下箭头并比对r12 payload。差异仅1920/1921上行文字、附标与包围区域；其余96评分帧这些字段一致。read-integrity.json SHA a8161ad8aa5a7ec5354e32b8ac50022d22ae17fa6f99472c134ddf92b700ee8c；frozen-comparison.json SHA a2a6e64e395eafa923be96da0d57d0b4b291228f9fcd5cc51bfa303b8ff46493，均位于reports/m3-grid-refinement-v1/nurse-mark-notes-v1。状态类别及completePage/pageOpportunity仍须依据消标过渡语义复核，不把字段一致等同全部状态正确。本轮没有修改冻结GT、算法、评分标准，也未重新运行OCR；r12/v10实测数值保持，M3/M4未通过、独立会话/UI/真实环境待验证、中文不启动，目标active。

**当前唯一可执行下一步**：依据已保存102帧双遍及98帧全字段对照，为nurse-greeting1920/1921消标过渡核查kind/completePage/pageOpportunity（保留全部帧与原机会依据，不为改善成绩删除页），在用户高置信纠错授权下制作版本化候选/差异包并完成完整性检查后再冻结；随后对同一v10构建重跑全部38项，单独报告GT纠错引起的分母/成绩变化。不得把该纠错记作OCR算法提升；问号选择仍未收到答复，其他耗尽修复预算保持。

**护士消标过渡r13候选、冻结及同输出复算（2026-10-17）**：上一轮为progress（完整102帧双遍及98评分帧原图比较完成）。本轮按序完整读spec/PLANS/本计划，120–280段中被截断的198–227段补读。依据双遍实际顺序1919附标仍在→1920正文附标清除→1921菜单清除→1922正文清空，1920/1921为部分擦除，不是新完整消息。prepare-nurse-mark-correction.cjs仅修这两帧上行「ここては ホケモンの」、附标空及对应原矩形；从1886–1921旧区间拆出1920，kind=message_clearing/completePage=false。原第4机会仅1921一帧，现撤回其错误完整页结论并在candidate-evidence.withdrawnPage保存ID/原范围/依据；不算通过，不删除或移出评分任何原帧。原第3页有效完整段截止1920。其余96帧可见对象完全相同；所有原时间/字符分母/角色/顺序/光标/评分标准保持。不是为了保持旧分母而将擦除残片继续认证为完整页。

候选data/gt/candidates/ocr-observation-corrections-v11/nurse-greeting/ocr-candidate-clearing-v1.jsonl SHA 84c6855eedd478398e36ee8673bcecd65ab027fcc0f0ca6d55e85bed6325a41e。实际gt-check通过98帧/23状态/3页（原22状态/4页），nurse-mark-candidate-integrity-v1.json。build-nurse-mark-correction-bundle.cjs重核四录像原件、r12所有payload、两遍102原PNG及记录身份，保留全部原图/新旧注释/笔记/逐帧对照；gt-observation-corrections-v11 bundle SHA 8128d639a61930dcf4697ff1e8df523511b7af51a084cde9041c852fd4a4322a。依用户高置信纠错授权生成r13 freeze SHA 83f59aad5b1af0d5b295bf70740309599ec5af065eb45367c6254af9e78f1ab2；allSemanticIntegrityPassed/productAcceptance=false，UI实际呈现待验证，旧版本及错误机会历史完整保留。7重叠引用无冲突，3361唯一原帧不变。

实际compare-ocr r13 all复用ocr-regression-all-v10/actual到ocr-v10-observation-r13-comparison，analyze-ocr-differences独立重算原始188/86569错（99.78283219166214%逐帧、99.72232854878935%等权）；最终177/86569错（99.79553881874574%逐帧、99.7287839099668%等权）。仍2601失败帧、误报0/692、7内容精确页；页分母117→116、唯一状态714→715、等权字符15457→15491。check-nurse-mark-correction-delivery.cjs全38项3368评分引用逐对象验证仅1920/1921的GT和评分变化，全部actual SHA不变；交付报告SHA f3c7cc96eff6a1c7d98680b0297153f4238b0415dc9bce752e92934592a37fca。减少4编辑错及分母变化来自标注纠错，不是算法提升；原整页标准和最终100%要求不变。

另核对当前92编译类及61app/verification源文件与build-evidence-v10-final全SHA相同后，实际启动ocr-replay-assisted r13 all → reports/m3-grid-refinement-v1/ocr-regression-r13-v10（来源配置SHA仍2eb7e1fd2a393230cb426b565e52c1e110f040d9ce35cf0bbd52c979800192e3）。会话74875真实运行中，日志nurse-mark-r13-replay-v1.log；新回放未完成前不得用上述复算成绩冒称新回放结果。新增check-nurse-mark-replay.cjs待完成后逐3577对象（仅忽略处理耗时）、时间/RGB及全量成绩与同构建旧输出复算比较。未改生产/评分、未重跑Maven或新package，累计预算保持，中文未启动，独立会话/UI/真实环境仍待验证。

**r13同v10全回放完成及独立封口（同日）**：会话74875正常exit0，全部38项actual关闭。node tools/check-nurse-mark-replay.cjs通过：3577保留帧输出逐对象（仅忽略processingNanos）与旧v10相同，原PTS/duration/RGB/scored/sourceOffset全部相同；92编译类和61生产/验证源SHA未变，完整aggregate与同v10输出r13复算逐字段一致。报告nurse-mark-r13-replay-check-v1.json SHA da2e007b8215284b32aae4595d85331c60869aea8da1e4154a9c98e506e68447。真实新回放原始188/86569错、99.78283219166214%逐帧/99.72232854878935%等权，最终177/86569错、99.79553881874574%逐帧/99.7287839099668%等权；原始/最终均7/116内容精确页、2601失败帧、误报0/692。四源66770帧/2779.397368秒、3361评分原帧/134.916228秒、3507唯一保留帧及未选范围不变。新增路径字节保护；git diff --check通过（只有既有换行转换提示）。本轮辅助读取三处猜错目录及一次不存在build-identity文件，均只读失败后用实际文件定位，无素材或构建变化。没有新Maven/JAR通过声明，未提交/推送。

**当前唯一可执行下一步**：以r13中1920/1921两帧为失败复现，检查JapaneseOcrEngine的previousSignature/complete分支，为“已显示正文保持同一基字格、当前只擦除附标”的清屏过渡设计通用有界状态跟踪；只允许当前像素及上一真实输入观察，不补文字、不读取GT/片段/帧号/未来画面。先验证换文、仅加载附标、滚动、换源/reset等反例，证据支持后按新问题1/3实现并运行护士目标、同类完整事件及全38项r13回归。保留当前GT/评分器和所有原帧；不重开几何模型3/3或3541标记3/3预算。问号规范仍待原问题答复，独立会话/UI/真实环境待验证、M3/M4未通过、中文不启动；目标active。

**正文附标擦除状态修复v11（2026-10-17，新问题1/3，完整回归运行中）**：上一轮为progress（r13纠错/同v10全回放证据完成）。本轮沿已读spec/PLANS及本计划继续，复核末尾权威状态与当前源文件。MessageEraseTracker只保存上一帧最多两行36字格的基础候选与当前附标类型、完整/擦除标志及输入尺寸/原时间。此前完整正文的基字候选/格位完全相同而附标仅消失时，输出message_clearing；相同残留继续保持，添加/改变附标、换字、移动行、滚动、空框、reset、时间倒退/单位变化/尺寸变化丢弃旧证据。不存消息词库/片段/帧号、不使用未来帧、不补文字。JapaneseOcrEngine在当前正文状态分支接入，其他场景resetTextState时同步清除。新增5项像素/状态测试含端到端合成图，不能当实机验收。

首次message-erase-tests-v1.log有1测试失败：夹具首帧沿用time_base1/1，后帧1/30，实际正确触发重置保护；夹具修复1/3统一首帧1/30，未改产品时间保护或降低断言。实际mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' test重跑v2通过147项（app79/verifier68），0失败/错误/跳过。build-evidence-v11保存191源/93编译类；未package，旧JAR不代表v11。

实际ocr-replay-assisted r13 nurse-greeting → ocr-message-erase-v1-nurse-greeting完整102保留帧/98评分帧完成。独立逐对象比较仅1920/1921从message_loading→message_clearing，除耗时外文字/候选/区域/几何/光标/附标/辅助解释全部相同；原始11/1986错、最终0/1986错不变，82失败帧仍由其他项导致，0/3整页通过。message-erase-target-evidence-v1.json保存最小复现原PNG/新旧输出/测试日志SHA，不称最终整页通过。

随后实际启动相同r13全38项ocr-replay-assisted到ocr-regression-all-v11，会话21940真实活跃，message-erase-all-v11.log记录进度。新增check-message-erase-regression.cjs待所有输出关闭后核对3577对象仅状态变化、原PTS/RGB一致及所有冻结payload/类身份。当前未给全量通过结论，GT/评分器/来源字库不变，几何3/3及3541标记3/3不重开；M3/M4未过、独立会话/UI/真实环境待验证，中文不启动。

**v11全范围回归与状态差异复核完成（同日）**：会话21940正常exit0。check-message-erase-regression.cjs逐全部38项/3577保留引用证明仅护士1920/1921的kind从message_loading→message_clearing，其余所有输出字段（除耗时）与r13/v10逐对象相同；原时间/RGB/评分标记、全部冻结payload及93类身份一致。message-erase-regression-v1.json SHA f6ce2e0c72cc66b6babb0c86aee29df45bc96d6f437120f84ebc0ea2dbed209b。全部原始/最终aggregate与v10相同：188/86569及177/86569错，99.7828%/99.7955%逐帧；状态等权99.7223%/99.7288%；7/116内容精确页、2601失败帧、误报0/692。两帧状态修复没有消除其几何失败，不能称整页率改善。独立analyze-ocr-differences至ocr-v11-observation-r13-text-analysis再次复算188错误一致。当前修复1/3通过目标/全范围回归，不追加尝试。

实际audit-visible-state-errors.cjs重新绑定r13/v11当前输出及评分，报告visible-state-diagnosis-r13-v11/report.json SHA 8bcc0ebdbe42bb72106775249951c1bcc3e5f9d613e470d43d7600f19e9a860d：3368评分引用中1200状态名差异（不是去重帧数），护士两帧错误消除；无箭头完成页仍652，非目标245、empty_target/no_textbox109及其他契约/转换问题保留。另19帧message_fading仍输出message_complete，属于尚未实现的淡出显示状态，非几何或3541稀疏标记修复预算。诊断附件不归一化评分、不证明可行性或通过。生产中文仍未启动，独立会话/UI/真实环境待验证；所有旧失败/GT/原视频保持，未提交/推送。

**当前唯一可执行下一步**：从visible-state-diagnosis-r13-v11/mismatches.jsonl定位全部19个message_fading原帧，并由冻结batch映射回四录像内完整开场事件及相邻原帧；先读取原PNG、确定性测量整幅亮暗变化和当前文字对比度，核查“淡出但仍可见”能否仅凭当前/上一真实输入与正常字格确定。只有像素证据支持后才实现通用淡出状态，保留文字直到当帧确实消失，按新问题1/3运行目标/同类/全38项r13回归；不能以帧号或静止时长猜状态，也不重开已耗尽的几何/3541预算。问号规范仍待原问题答复，目标active。

**淡出像素诊断与通用状态修复v12（2026-10-17，新问题累计2/3，全回归运行中）**：上一轮为progress（v11清屏实现/全回归）。从当前r13/v11诊断定位全部19个message_fading错误：intro-oak6208–6216、intro-research6466–6475。直接查看原6207/6208/6216/6466全PNG，不把此诊断称新盲双遍。RawFadeProbe不读OCR/GT，仅测量两个完整保留事件54+80原帧的游戏区域原RGB、亮度直方图/逐帧变化；v2另分别统计上2/3与下1/3。所有134 PNG SHA及帧序号与原verification身份复核，message-fade-original-evidence-v1.json SHA 6886d8e306762d3a380139b3c34053d706fd5e1a571e1ab1293f007c983953d7。

实测暗笔画约19→67→108→背景140，明显淡出从6209/6467起；6208仅6个亮度像素变化且均不超过4，6466游戏区域RGB与6465完全相同。原冻结提前一帧的message_fading标签留疑点，不能由未来帧反推当前；本轮不修改GT，也未声称完成受影响事件新双遍。两帧继续原标准计失败。

新增MessageFadeTracker：仅保存上一输入160×144亮度采样、当前文本签名及有限状态；在上下两区同时发生大部分原暗笔画变亮、亮背景稳定、无明显新增暗像素的对比度收缩时记录淡出，阶梯调色板的稳定平台保持；恢复对比度、换字、滚动/清屏、无消息、reset、时间倒退/单位/尺寸变化重置。文字仍由当前像素识别，不补字、不按时间长短/帧号/场景答案判断。引擎只在当前非滚动非擦除正文分支接入，GT/评分器/字库不变。

首次算法尝试1/3用5%低分位，151测试通过但实际两完整事件目标复测oak0/8可观察淡出被发现、research仅6467–6471的5帧，原失败输出ocr-message-fade-v1-*及build-evidence-v12保留。原像素分区测量证明上部稀疏人物占比不足5%，低分位落入背景（oak6208上p05=134/p01=19；6209上p05=140/p01=67），导致漏检和第二阶段丢失。修复2/3改1%低尾并保留双区同向变化/80%暗笔画约束，增加稀疏前景反例。实际Maven固定离线命令message-fade-tests-v2.log通过152项（app84/verifier68），0失败；build-evidence-v12-r2保存195源/94类，未新package。测试与实机结果分别记录，不用合成图授予验收。

第二次完整目标ocr-message-fade-v2-intro-oak检出6209–6216八帧、intro-research检出6467–6475九帧；目标失败帧分别10→2、10→1，文字零错保持，两起点疑点仍失败。随后启动ocr-replay-assisted r13 all到ocr-regression-all-v12，会话26653真实活跃，message-fade-all-v12.log记录进度。check-message-fade-regression.cjs已准备，待actual全部关闭才验证全3577引用仅17个状态变化、文字/几何/候选/源时间/RGB与v11相同，不能提前声称全量成功。旧几何与3541耗尽预算不重开，生产中文未启动，独立会话/UI/真实环境待验证，目标active。

**v12淡出状态全回归封口（同日）**：运行会话26653正常exit0。check-message-fade-regression.cjs逐38项3577保留引用确认仅上述17帧message_complete→message_fading，其他输出除处理耗时外逐对象相同；所有raw/corrected文字、候选、字段、几何、光标/附标与原PTS/duration/RGB/scored/sourceOffset一致。全部r13 payload与94类身份未变。报告message-fade-regression-v1.json SHA 894f78d7547b820664e150ab62b07026b106eeb00395512127ac519c90290479。原始/最终失败帧2601→2584，其他aggregate完全不变；独立analyze-ocr-differences到ocr-v12-observation-r13-text-analysis再次复算188/86569原始错误一致。当前r13/v12原始逐帧99.78283219166214%、等权99.72232854878935%，最终177/86569错、逐帧99.79553881874574%、等权99.7287839099668%，7/116内容精确页、误报0/692。不是独立验收或完整达标；淡出算法累计2/3已在既有范围通过本次回归，不继续消耗次数迎合两帧未证实标签。

message-fade-input-integrity-v1.json实际重算四原视频SHA与r13封套SHA相同，原件只读。原134帧测量、首算法失败、两次目标回放、152测试与源类快照全部保留；GT/评分标准/范围未改，未新打JAR、未提交/推送。6208/6466疑点留原分母，不能凭一次诊断直接修订；中文未启动，独立会话/UI/真实环境待验证。

**当前唯一可执行下一步**：从visible-state-diagnosis-r13-v11保留的背包menu_redraw_or_cursor_transition错误（v12仅改淡出，其余仍同）定位全部19帧，并结合r13/v12当前光标、当前列表边界和前后原PNG核查缺光标/双光标是否有统一可观察依据。先形成不改GT的最小复现/完整受影响帧清单，再实现有界菜单转换状态（区分初次出现与已出现后暂缺，不用帧号/固定文本/未来输入），按新问题1/3验证背包目标、PC/商店同类及全38项。重复文字本身不自动证明滚动；原几何与3541耗尽预算保持，问号规范仍待原问题答复，目标active。

**背包转换状态修复v13（2026-10-17，新问题1/3，全回归运行中）**：沿已读spec/PLANS/唯一计划继续，重新核对当前r13/v12证据与背包完整166原帧双遍记录。19个导航状态错误分为15个暂缺光标、1个同帧双光标、3个单光标但列表内旧孤立附标；重复文字本身不作重绘依据。直接看过5205/5241/5301/5330完整原PNG；backpack-transition-diagnosis-v1.json绑定19个失败及各前后原PNG SHA、当前输出身份，SHA f9d5621b89168d63f360bcecfec3218b01c0a842c941f8125f8c02b3186216da。此次为已有双遍基础上的实现诊断，不称新的盲读或独立验收。

新增BackpackTransitionTracker，仅当当前识别为BACKPACK且列表闭合边界存在时处理。首次缺光标标submenu_cursor_loading，当前历史已出现选择后暂缺或同帧双光标标menu_redraw_or_cursor_transition；列表内部孤立附标同样提示重绘，父菜单上露出附标不触发。只保存有限选择标志、输入时间与尺寸；换布局/缺闭合边界/reset/时间倒退或单位/尺寸改变清除历史；不读取GT/片段/帧号/未来内容，不修改当前文字。引擎在字段和当前符号提取后调用；未触及已耗尽的几何或3541预算。

新增4项合成流程测试，包括初始与后续缺光标、重复行/父附标反例、双光标/列表残留、reset/时间不连续和缺边界。首轮测试4失败是夹具只叠画子框而没有擦去被遮挡父框竖边，保留backpack-transition-tests-v1.log；修夹具为真实覆盖语义，生产规则不变，v2固定离线Maven命令通过156项（app88/verifier68），0失败。build-evidence-v13保存198源/95编译类，未新打JAR。

实际ocr-replay-assisted r13分别运行backpack-opening和backpack-navigation；38+166全部保留帧已关闭并逐对象比较，只有5205（两片段引用）初始状态及导航19状态改变，除耗时外全部文字/候选/字段/矩形/光标/附标/配准不变，backpack-transition-target-evidence-v1.json。导航文字仍0/4248错、但152失败帧/0整页由其他错误保持，不能把状态修复冒称整页通过。随后实际启动全38项ocr-regression-all-v13，会话88172，日志backpack-transition-all-v13.log。check-backpack-transition-regression.cjs准备验证全3577引用仅21处状态变化及冻结payload/类/时间线身份；回放未完成前不作全量结论。GT与标准不改，问号规范仍未获答复，独立会话/UI/真实环境待验证，中文不启动。

**当前唯一可执行下一步**：等待既有88172回放完成，再运行node tools/check-backpack-transition-regression.cjs及独立文本分析，核查全38项回归和当前状态差异后更新权威指标；不得重复启动同目录回放，目标保持active。


**v13背包状态全回归封口（同日）**：既有88172正常exit0，全部38项actual关闭。check-backpack-transition-regression.cjs逐3577保留引用确认仅5205初始光标加载（两片段引用）及19导航帧状态改变，其余输出除处理耗时完全相同；全部原PTS/duration/RGB/scored/sourceOffset、r13 payload、95类SHA一致。报告backpack-transition-regression-v1.json SHA 3f97c51fb4b8c1048c77ad0a33b048359f55a90e61b1e66ca9884f1e192b016b。原始及最终aggregate与v12逐字段相同：188/86569及177/86569错，逐帧99.78283219166214%/99.79553881874574%，等权99.72232854878935%/99.7287839099668%，7/116内容精确页、2584失败帧、误报0/692。状态修复未消除这些帧的位置等失败，不宣称整页改善。独立ocr-v13-observation-r13-text-analysis再次复算188错误一致。

重新生成visible-state-diagnosis-r13-v13，3368评分引用中1163状态名称差异（比v11少37：17淡出+20背包评分帧），报告SHA ea10e6157097077c2666c4313e0a2538259cedba2fc850ef4e877ffe2aa7a871。backpack-transition-input-integrity-v1.json重新流式核对4原录像SHA和r13封套一致。运行中曾把approved-trial误认为最后一项，后明确纠正；未在此时作完成结论。新增实现问题累计1/3、测试夹具失败及修正均保留；没有GT/标准变化，没有新JAR/提交/推送，中文未启动。独立会话/UI/真实环境待验证，M3/M4整体未过，目标active。

**当前唯一可执行下一步**：依据visible-state-diagnosis-r13-v13定位全部submenu_partial_redraw三帧（PC1659、商店1068、背包5204）和其完整事件上下文，先用原PNG与当前边框/可见字段证据核查逐行绘制的统一特征，再决定是否将当前部分列表边界转为通用状态识别。不得根据片段/帧号/具体文字判状态；GT及评分不改，旧几何/3541耗尽预算保持；先原帧诊断、目标/同类验证，再全回归。

**子菜单部分绘制诊断与v14修复（2026-10-17，新问题1/3，全回归运行中）**：上一目标轮为progress（v13实现与38项回归完成），本轮沿已读spec/PLANS/计划继续并核对末尾当前证据。直接查看PC1658–1660、商店1067–1069、背包5203–5205九张完整原PNG：三次都是子菜单下边框先可见、上部仍为旧背景；PC/商店同时出现下三行道具，背包5204只有下边框。纠正一般“从上向下绘制”的假设，本次只依据可见边界不推断内部复制过程。submenu-partial-diagnosis-v1.json保存九原PNG SHA及三旧actual身份，不称新盲读或GT重审，GT未改。

MenuFieldReader.partialChildOutline在当前已识别START/BACKPACK/PC_ITEMS/SHOP_BUY/SHOP_SELL_LIST布局中，要求子菜单两个下角及全部14格下横边可识别、两个上角尚未同时出现，才输出submenu_partial_redraw。未知布局、断裂下边、已有上角不触发；没有帧号、时间等待、文本答案或未来输入。字段提取保持，特别保留背包尚未被覆盖的父菜单文字。新增3项测试覆盖无菜单反例/缺边/完整框及端到端父字段保留。

submenu-partial-tests-v1有一个合成测试失败：测试绘制函数只加黑像素，子框底边未擦去父竖边，和原像素覆盖不同；修夹具先清该行再画，生产规则不变。v2实际固定离线Maven test通过159项（app91/verifier68）；两日志均保留。build-evidence-v14保存199源/95类，未新打JAR。

三目标实际ocr-replay-assisted r13分别完成PC124保留帧、商店62帧、背包38帧。submenu-partial-target-evidence-v1.json逐对象验证仅1659/1068/5204三帧kind由menu_complete→submenu_partial_redraw；除耗时外文字、候选、字段、几何、符号、配准完全相同。随后启动全38项ocr-regression-all-v14，会话96795，submenu-partial-all-v14.log记录。check-submenu-partial-regression.cjs准备全3577引用检查，预期另含backpack-navigation热身5204同一原帧；不得将目标完成冒称全回归完成。GT/标准不变，其他累计预算保持，问号规范仍待原问题答复，独立会话/UI/真实环境待验证，中文不启动。

**当前唯一可执行下一步**：等待既有96795回放完成，运行node tools/check-submenu-partial-regression.cjs及独立文本分析并核对全部38项输出后更新结论；不重复启动同目录回放。目标active。


**v14全范围回归完成（同日）**：会话96795正常exit0，38项actual关闭；check-submenu-partial-regression.cjs核对3577保留引用仅PC1659、商店1068、背包5204（两引用）状态改变，其余输出除processingNanos逐对象相同。全部时间线、r13 payload和95编译类SHA一致，报告submenu-partial-regression-v1.json SHA 61893c954850bcf5b894182b3b613d153e33f56de40815eb5762e5b2d3932367。独立ocr-v14-observation-r13-text-analysis复算188/86569原始错误一致。全部aggregate与v13相同：原始逐帧99.78283219166214%/等权99.72232854878935%，最终177错、逐帧99.79553881874574%/等权99.7287839099668%，7/116内容精确页、2584失败帧、误报0/692。三帧状态修正未消除其他位置等失败，不宣称整页或独立验收通过。已实现/测试通过/回放通过（本次改动范围）；M3/M4整体未过。

等待回放期间另直接查看背包5180/5181/5182原PNG：空菜单→两行标签及第三行两个孤立浊点、已有光标→完整菜单。start-menu-mark-loading-diagnosis-v1.json绑定原图和v13输出；不是新盲双遍。独立扫描v13全部38项输出中35个含main_menu_label引用，仅5181出现此类父框内部孤立附标，start-menu-mark-loading-scan-v1.json SHA 93819d771696698f26d9c895119c710d77b212b27aa7f187033e4a7b7a994763。此为下项诊断，尚未修改对应生产状态或GT，未宣称解决。未改标准/冻结、未新打JAR/提交/推送，中文不启动；独立会话/UI/真实环境待验证，目标active。

**当前唯一可执行下一步**：依据已保存主菜单5181原帧及全38项孤立附标扫描，实现仅在完整START父框内部、基础格尚未形成可组合文字时的menu_partial_redraw状态；保留当帧字段/附标/光标，增加完整菜单及子菜单遮挡父附标反例，运行背包目标及全38项r13回归。不得按具体词/帧号判断，不补未来内容，旧耗尽预算保持。

**主菜单附标先出现状态修复v15（2026-10-17，新问题1/3，全回归运行中）**：上一轮为progress（v14实现/全回归），本轮沿已读spec/PLANS/唯一计划继续，复核末尾和start-menu-mark-loading原帧证据。MenuFieldReader.startLabelsDrawing仅在当前START布局、完整父框及无子列表遮挡条件下检查父框标签区域：孤立清浊点的下一行基础格仍为空时输出menu_partial_redraw。基础字出现后由当前像素正常读取，不补字、不改候选/字段/附标/光标，不根据词句或帧号判状态。已有子列表不触发，框外标记不触发，空菜单沿原分支处理。

新增2项合成端到端测试，覆盖已有光标仍未写完、基础假名到来并组合、父框外标记不触发；既有子菜单遮挡附标反例保留。实际固定离线Maven test一次通过161项（app93/verifier68），start-menu-label-tests-v1.log，build-evidence-v15保存200源/95类，未package。真实backpack-opening目标38保留帧完成；start-menu-label-target-evidence-v1.json核对仅5181由menu_complete→menu_partial_redraw，其余除耗时全对象相同，并重新核对5180–5182三原PNG SHA。没有GT/标准变化。恢复读取的一次Windows通配路径rg调用失败，无文件变化，后续使用明确文件路径。

随后启动r13全38项ocr-regression-all-v15，会话35309，日志start-menu-label-all-v15.log。check-start-menu-label-regression.cjs待全部actual关闭后验证3577引用/时间线/冻结payload/95类身份及完整aggregate。当前不能声称全范围完成。等待期间只读检查其他闭合列表缺子光标：PC1660/1697/1707/1714/1715/1724，商店买859与卖1069；首开加载和后续移动/滚动需区分，不能把所有缺光标都叫加载。此诊断尚未写对应生产实现。独立会话/UI/真实环境待验证，中文不启动，旧耗尽预算保持，目标active。

**当前唯一可执行下一步**：等待35309现有回放完成，运行node tools/check-start-menu-label-regression.cjs与独立文本分析确认全量变化范围，再更新状态；不重启同目录回放。


**v15全范围回归完成（同日）**：35309正常exit0，全部38项actual关闭。check-start-menu-label-regression.cjs逐3577保留引用验证只有backpack-opening5181状态改变，其他输出除处理耗时完全一致；全部原时间/RGB/评分身份、r13 payload和95类SHA一致。start-menu-label-regression-v1.json SHA 66785e1d525922dd9024d8936b7fc55653c0ed11837610eb77a8efcab118e7d9。独立ocr-v15-observation-r13-text-analysis复算188/86569原始错一致。完整aggregate仍与v14相同：原始99.78283219166214%逐帧/99.72232854878935%等权，最终177错、99.79553881874574%逐帧/99.7287839099668%等权；7/116内容精确页、2584失败帧、误报0/692。该帧尚有位置等失败，因此状态修复不等于该帧全通过或整页改善。M3/M4整体未过，独立会话/UI/真实环境待验证，中文不启动。

等待期间直接查看PC1697/1707/1714/1715/1724及商店859原PNG，并结合之前已查看的PC1660/商店1069保存8原图SHA、12条相关转换错误至list-selection-state-diagnosis-v1.json。逐帧GT核对确认PC1714/1715为cursor_transition，不把汇总不同类别示例混当该帧标签。缺子光标的原因包括首次出现、移动、局部滚动；有光标仍可能出现双光标或重复行，因此下一项须覆盖完整转换，不能只按有无光标推定完成。本轮未改GT/标准、未package/提交/推送；新问题修复1/3，旧耗尽预算保持，目标active。

**当前唯一可执行下一步**：以list-selection-state-diagnosis-v1.json为索引，补看PC1673/1688/1706/1736和各转换邻帧、商店859及1069邻帧，并检查当前候选/列表行/子父光标。基于完整证据设计共享列表选择状态跟踪，区分初次缺光标、移动/双光标及当前可见局部滚动；不读取GT/帧号/固定答案/未来内容。先目标与同类验证，再全38项r13回归，不修改正确GT或重开耗尽预算。

**共享列表选择/滚动状态v16（2026-10-17，新问题1/3，全回归运行中）**：上一轮为progress（v15实现与全回归），本轮沿已读spec/PLANS/唯一计划继续。直接查看PC1673/1688/1706/1736及1672/1674/1687/1689/1705/1735/1737邻帧，商店858/860/1070，与已读859/1068/1069共同确认。list-transition-original-observations-v2.json保存新增原图身份及既有完整PC双遍差异记录SHA。1673原帧仍有首行子光标、仅下箭头消失；1688仅第三行子光标。当前冻结分别删光标/写双光标，与原图有疑点，且旧pc-role-recheck-notes-v1/pass-differences已记录1688首遍误读。本轮不改GT、不迎合疑似错误标签，两帧继续失败；1736已回父菜单，另列不混为子列表状态。

扩展既有BackpackTransitionTracker至当前闭合PC道具/商店买卖列表，保留背包既有行为。按当前子光标列计数，分开首次无子光标与既有选择后暂缺；父空心光标不计作子选择。当前列表内孤立附标表示部分重绘；当前基字格行从上一真实输入的相邻行复制而产生新的重复行时标scroll_duplicate_row，不因自然重复行或冷启动重复判滚动。只保存前一帧四行候选签名及菜单/时间/尺寸，切布局/reset/失去闭合框丢弃历史，不读GT/未来/固定词。正常选择返回无额外覆盖，避免覆盖并发消息清屏/加载；这是编码复核中加入的保护，未用评分反推。

新增2项序列测试覆盖初次/后续缺光标、双光标、复制重复、冷启动重复、残留附标与恢复。首轮两个夹具报Invalid glyph identity：合成tile编号超过255；修正夹具编号后，固定离线Maven test v2通过163项（app95/verifier68），日志v1/v2保留。build-evidence-v16保存201源/95类，未package。实际PC124、买入48、卖入62完整目标回放完成；list-transition-target-evidence-v1.json逐对象核查只有PC1660/1697/1706/1707/1714/1715/1724、买859、卖1069共9帧kind改变，其余除耗时完全相同；可见文字/字段/几何/符号/候选均不变。

随后启动全38项ocr-regression-all-v16，会话70542，list-transition-all-v16.log记录。check-list-transition-regression.cjs待全部输出关闭后检查3577引用、时间线、r13 payload/95类身份与完整aggregate。未修改GT/标准或重开已耗尽预算，独立会话/UI/真实环境待验证，中文不启动，目标active。

**当前唯一可执行下一步**：等待既有70542回放完成，运行node tools/check-list-transition-regression.cjs及独立文本分析，检查全部38项输出后更新指标与PC光标疑点的后续复核范围，不重启同目录回放。


**v16全范围回归完成（同日）**：70542正常exit0，全部38项actual关闭。check-list-transition-regression.cjs逐3577保留引用核对只改变上述9帧kind，其余除处理耗时完全相同，全部原时间/RGB/scored/sourceOffset、r13 payload、95类身份一致。list-transition-regression-v1.json SHA 40b67284bdc5e764c72eaf294441b599d3de312b519c48413988186fc52181dc。独立ocr-v16-observation-r13-text-analysis复算188/86569原始错误一致。完整aggregate仍与v15相同：原始逐帧99.78283219166214%/等权99.72232854878935%，最终177错、逐帧99.79553881874574%/等权99.7287839099668%，7/116内容精确页、2584失败帧、误报0/692。状态修复不消除其他位置等失败，未取得整页提升或独立验收。list-transition-input-integrity-v1.json重新流式核对四原录像及r13封套SHA一致；git diff --check通过，仅既有换行提示。

本次新问题累计1/3完成目标/全回归；当前仅前一帧新复制重复判定已覆盖单帧实机机会，多帧相同重复平台的状态保持尚无对应录像证据，不宣称新增覆盖。未改GT/标准、未package/提交/推送，独立会话/UI/真实环境待验证，中文不启动，目标active。

**当前唯一可执行下一步**：关闭GT/actual，针对PC导航完整[1632,1756)124原帧重新双遍核读所有父/子光标、下箭头及状态边界，第二遍从原录像video-02重新导出；先独立记录观察、核验原帧身份及完整性，再与r13逐帧对照。已有诊断暴露如实披露，不称独立会话或人工逐帧核验；只在确认高置信错误后按用户授权合批版本化更正，保留旧版、全部评分帧和失败机会。不能仅挑1673/1688，不能为了匹配程序改GT；旧耗尽预算保持。

**PC完整事件光标复核首遍与重新取帧（2026-10-17）**：针对既定[1632,1756)全部124原帧，关闭GT/actual后逐张查看pc-role-recheck-pass1-v1/pc-deposit-navigation全部21张原帧表；000–017在上下文压缩前读取，018–020因批次输出截断重新逐张打开确认，未把截断图计作已读。pc-cursor-notes-v1/pass1.json保存25段无缝光标/下箭头观察，SHA 6334cceeeae46a50a480005580185becd0f75b745195a1fbb7d3ff0cc72d6f19，绑定21张表、原取帧验证身份。记录包含正常选择、无光标、残留/重复行、退出与空帧，不仅两个疑点。历史诊断暴露如实披露，不称无记忆盲读、独立会话或人工逐帧验证。首遍看到1673首行光标仍在、1688仅第三行光标，尚不能仅凭首遍修订。

实际执行 ./tools/validate.ps1 export data/meta/m1-v1 video-02 1632 1756 data/frames/video-02/pc-cursor-pass2-v1 1，正常完成124连续原帧；RawRecheckSheets生成reports/m3-grid-refinement-v1/pc-cursor-pass2-v1全部21张表（crop296,104,368,336，最近邻2倍），逐显示像素验证通过。pc-cursor-notes-v1/extraction-check.json逐124帧比较两次原PTS/时长/RGB及PNG SHA一致，表SHA复算一致，报告SHA a9f73277cf4b0eb341a78e5417093bd224cb2c7722196c38a08f5077093b8d5d。第二遍图像尚未核读，不能宣称双遍完成。恢复时一次合并输出spec/PLANS/全计划被截断，不声称该次完整读取；PLANS随后完整补读，其余后续须分段读取避免截断。

本次未改生产代码/冻结/标准，没有新OCR回放，权威指标仍r13/v16。原始99.78283219166214%，最终99.79553881874574%，7/116内容精确页、2584失败帧、误报0/692，整体目标未达成。独立会话/UI/真实环境待验证，中文不启动，目标active。

**当前唯一可执行下一步**：不打开pass1/GT/actual，依次查看reports/m3-grid-refinement-v1/pc-cursor-pass2-v1/sheet-000.png至sheet-020.png全部124原帧，独立保存pass2.json；之后再比两遍并从原图解决差异，最后才与r13逐帧核对、按用户授权合批版本化更正高置信错误。当前无运行进程，不重复导出或覆盖首遍记录。

**PC光标完整双遍与候选修订（2026-10-17）**：上一轮为progress（首遍与124帧重新导出已验证）。本轮连续查看新导出的pc-cursor-pass2-v1/sheet-000至020，全部124帧先于重新打开首遍/GT记录观察；pass2.json SHA 109baf47588636cc5f130799e735cd60d97997eb1372e18fd154559800251591。历史诊断/首遍记忆暴露如实披露，不称独立会话或人工逐帧。随后逐帧对比两遍父/子光标及下箭头，124帧无差异，pass-comparison.json保留；文字重绘/退出边界另以观察记录核查。

prepare-pc-cursor-correction.cjs实际运行成功，复算r13封套/4payload/两遍原帧表身份；全部120评分帧对照、4上下文帧保留，恰有1673和1688两个光标差异，118帧一致。1673首行子光标仍在，仅下箭头隐藏；1688只有第三行子光标，第二行已消失。两帧均已具完整当前菜单选择，应为menu_complete，不能因箭头闪烁判缺选择。frozen-comparison.json与candidate-evidence.json保留全差异及前后状态。

生成候选data/gt/candidates/ocr-observation-corrections-v12/pc-deposit-navigation/ocr-candidate-cursor-v1.jsonl SHA 545612ea9a8133b80a5efafb95c02ff0aa2365e8e7665c2b00a53271525b67f0：仅两帧光标、状态、完整性和证据更正；全部regions文字/几何不变，32区间和11个原页面机会不变。pc-list-A-first结束1673→1674，pc-list-A-third开始1689→1688，属于原机会边界纠正，不新增/删掉失败机会。其他118评分帧对象完全相同。此为候选，尚未改冻结、尚未更新评分契约或运行OCR；未宣称新分数。用户高置信纠错授权沿用，无额外模型、中文不启动。

**当前唯一可执行下一步**：读取PC既有评分契约及tools/build-nurse-mark-correction-bundle.cjs等版本化修订工具，按本次candidate-evidence两项已证实边界变化构建完整v12修订包（包含全部受影响PC事件/原帧/双遍/差异/旧版及缺口），同步契约后执行完整性检查；通过后依据用户已给高置信纠错授权另存r14冻结，再对同一v16 actual重评分并做完整回归身份验证。当前有效基线仍r13/v16：原始188/86569错、最终177错、7/116页、2584失败帧、误报0/692；独立会话/UI/真实环境待验证，目标active，无运行进程。

**PC光标修订r14冻结与同输出重评分（2026-10-17）**：上一轮为progress（全部124帧双遍与两帧候选）。读取既有评分契约确认pageOpportunityIdentity已允许箭头闪烁前后共用ID，边界由GT提供，没有需改的逐页数字契约；因此完整保留评分契约及标准。实际gt-check通过120评分帧/32状态/11页面机会，报告pc-cursor-candidate-integrity-v1.json。build-pc-cursor-correction-bundle.cjs重算四原视频SHA、r13全部payload、两遍笔记/表/124重新导出PNG身份，并逐状态确认仅1673/1688已证实光标错误变化，所有regions不变。

依用户高置信纠错授权生成完整gt-observation-corrections-v12修订包，包含124原PNG、新旧全文、两遍、差异、完整性、全部覆盖缺口及旧版引用；bundle SHA 200839777b5f0e51308abc905ec4a79923f2ba0e7d3d50aa158eb5d64b6ffcd8。r14 freeze SHA eac7f15d33ed07d8df6d27cb9133da42582716ec48f77b29dee0172cf5b01dfe，allSemanticIntegrityPassed/productAcceptance仍false，审阅页实际UI呈现待验证。全38项重叠检查7引用无冲突，3361唯一评分帧不变。完整状态区间数增加2，但连续GT区间32、PC页面11和全局页面116均不变；原机会边界变化保留明细，不删失败机会。

实际compare-ocr r14 all对同一ocr-regression-all-v16/actual重评分到ocr-v16-observation-r14-comparison。独立文本分析复算188/86569错误一致。check-pc-cursor-correction-delivery.cjs核验38actual SHA均与旧报告相同、全部payload/封套/修订包文件身份一致、3368评分引用/原PTS保持，只有PC1673和1688评分明细变化；完整aggregate逐字段不变。报告pc-cursor-correction-delivery-v1/report.json SHA 91386a39e35b02f2126c539a0e31dc3ca98a8b8fdab0ba720352444d83c10dad。权威r14/同v16输出：原始逐帧99.78283219166214%、等权99.72232854878935%；最终177错、逐帧99.79553881874574%、等权99.7287839099668%；7/116内容精确页、2584失败帧、误报0/692。纠错不等于实现改善或独立验收，两帧其他失败保留。

随后实际启动全38项r14新回放到ocr-regression-all-v16-r14，会话99200，pc-cursor-r14-replay.log记录；尚未完成，不称新全回归通过。生产源码/构建未改，仍build-evidence-v16的201源/95类、163测试基线；没有新JAR/提交/推送。独立会话/UI/真实环境待验证，中文不启动。

**当前唯一可执行下一步**：轮询既有99200直到终止；全部actual关闭后，逐3577保留引用比较新回放与v16旧输出（仅忽略processingNanos），核对95类及时间线/原RGB，比较新完整aggregate与同输出r14重评分，并生成r14/v16当前状态错误索引。不得重复启动回放；目标active。

**r14全回放封口与菜单加载根因定位（2026-10-17）**：上一轮为progress（r14冻结、同输出评分及实际启动回放）。本轮轮询既有99200确认运行，后正常exit0。check-pc-cursor-r14-replay.cjs实际逐38项3577保留引用验证新ocr-regression-all-v16-r14与旧v16除processingNanos全对象一致；全部原PTS/duration/RGB/scored/sourceOffset一致，r14全部payload及95编译类SHA不变。每帧评分与同输出r14重评分完全一致，完整aggregate相同。pc-cursor-r14-replay-check-v1.json SHA ff79ab872eff92ba1d0135e6730443ddbd82bd2ffe8e07e974d64dc8d55f3b03。独立ocr-v16-r14-replay-text-analysis再次复算188/86569原始错误一致。当前权威r14/v16新回放原始99.78283219166214%、最终177错/99.79553881874574%，7/116内容精确页、2584失败帧、误报0/692；不称整体达标。

生成visible-state-diagnosis-r14-v16，绑定同v16输出/r14重评分，3368评分引用中1148状态名称不符，报告SHA 911998e550050c0d08b8ba51d985329e910d25a422231f7ee5eb39d21a5c113d。相对v13索引1163减少15（v14三、v15一、v16九、本次GT纠错二），不是新增15全帧通过。主要652个无箭头完整消息、空/非目标标签等仍待解决；旧耗尽预算不重置。

代码诊断发现JapaneseOcrEngine.java第185行在已算message_loading/complete之后，若存在任意菜单光标就覆盖为menu_complete，未区分空心父光标/等待中的子光标。parent-cursor-message-loading-diagnosis-v1.json保留全部21误判帧及当前输出身份：PC1650–1653、商店卖出1058–1062、拒收1414–1425。核查全部列出的当前光标均为outline_right（拒收父/子均空心）。PC完整原事件刚完成双遍，其他两商店事件需在已有原帧/双遍基础上补看转换及邻帧。诊断未改生产/GT，不称新盲读。一次误用PixelOcrEngine.java不存在路径已纠正为JapaneseOcrEngine.java，无文件变化。

**当前唯一可执行下一步**：依据parent-cursor-message-loading-diagnosis-v1.json，补看商店1057–1063、1413–1427原帧和既有整事件双遍证据，检查全部只有空心光标的菜单状态；验证以当前filled_right选择作为菜单完成覆盖条件能否保留并发消息加载/完成。不能按文字/帧号/未来状态判定，尤其保留子列表初始缺光标及滚动规则；先合成正反例、PC/卖出/拒收/背包同类目标，再全38项r14回归。生产尝试尚未开始，不占修复预算；当前无运行进程，目标active，独立会话/UI/真实环境待验证，中文不启动。

**空心选择与消息加载v17（2026-10-17，问题累计2/3，全回归运行中）**：上一轮为progress（r14全回放封口及21帧根因诊断）。直接查看商店卖出1054–1065连续原帧表、拒收1413/1414/1426/1427完整原PNG，并读取既有卖出完整62帧复核记录、PC124帧双遍；hollow-cursor-original-evidence-v1.json绑定原图/记录SHA。原图确认实心选择在提交后变空心、消息继续逐字绘制；拒收父子光标皆空心，列表下箭头不能冒充消息等待箭头。未做新GT修订，不称新盲读。

第1次实现将JapaneseOcrEngine菜单完成覆盖限制为filled_right，新增端到端测试覆盖空心/实心、消息有/无等待箭头，164测试通过。首轮四目标回放保留在ocr-hollow-cursor-v1-*，发现商店1056回归：旧提示完整保留，只变空心，被判加载。hollow-cursor-target-evidence-v1.json记录全部变化，失败保留，不能将其算通过。

第2次实现增加一个menuPromptComplete标志，只在当前实心菜单选择建立；同一当前消息签名连续未变时短暂空心仍保留菜单完成，文字变化/清空/滚动/无菜单或reset丢弃。其用途只区分提交选择后的旧提示与正在绘制的新提示，不补文字、不用固定帧/词句/GT/未来输入。新增序列断言：实心完整→空心旧提示→新增字加载→reset后加载。两次测试日志均保存，v2固定离线Maven test通过164项（app96/verifier68），build-evidence-v17-r2保存206源/95类，首尝试build-evidence-v17仍保留。未新打JAR。

第2次PC124、卖出62、拒收36、背包166完整目标回放均完成。check-hollow-cursor-targets.cjs逐对象验证只有44帧kind变化且原光标均为空心，除耗时外全部文字/候选/字段/矩形/符号/配准保持，背包无状态变化，1056明确保持menu_complete。hollow-cursor-target-evidence-v2.json SHA bdcef1cebb9c452fc977ae224c0a6a3e2f9bcaefd0ad82a457a60fbb80109712。无等待箭头的完整消息仍有未解状态，不把44变化全称正确或全帧通过。

随后实际启动r14全38项ocr-regression-all-v17，hollow-cursor-all-v17.log记录，回放尚未完成。有效完整指标仍r14/v16：188/86569原始错误、177最终错、7/116页、2584失败帧、误报0/692。GT/标准不变，旧耗尽预算保持，独立会话/UI/真实环境待验证，中文不启动，目标active。

**当前唯一可执行下一步**：轮询本轮启动的v17会话直到结束（工具返回会话号见本轮记录），不重复启动；关闭全部actual后比较38项3577引用，仅允许空心光标关联的kind变化且核对所有时间线/r14 payload/95类身份，检查相对v16是否新增状态失败、复算字符和整页指标。若出现新回归保存根因后按本问题剩余第3次预算修复，不放宽评分或改GT。

本轮v17全回放已确认会话号为25064；下一轮应轮询该既有句柄。

**v17全回归完成（2026-10-17）**：上一轮为progress（空心光标规则第2次修复、164测试及4目标、全回放启动）。本轮轮询既有25064正常exit0，38项输出全部关闭。逐3577保留引用比较v16-r14与v17，只有72个kind变化、所有这些帧的原菜单光标均为空心；除processingNanos外全部其他输出逐对象相同。全时间线PTS/duration/RGB/scored/sourceOffset一致，r14全部payload和build-evidence-v17-r2的95类SHA不变。逐3368评分引用检查31个状态错误被修正、0新增状态错误。hollow-cursor-regression-v1.json SHA 0a418573cbfd02c5eea4152cbcb2d813c2c130c0687f9612dc235b24e862e374。72变化分布PC加载9、卖出加载10、拒收加载15/完成10、退出加载28；不把所有变化均称原冻结标签已正确。

独立ocr-v17-r14-text-analysis复算188/86569原始错误一致。hollow-cursor-input-integrity-v1.json重新核对四原视频SHA/r14封套及完整aggregate深比较相同。当前r14/v17原始逐帧99.78283219166214%、等权99.72232854878935%，最终177错/99.79553881874574%、等权99.7287839099668%；7/116内容精确页、2584失败帧、误报0/692。状态改善未消除同帧位置等失败，因此不称整页改善或独立验收通过。当前问题累计2/3，首次1056回归证据保留，不再无据消耗第3次。

visible-state-diagnosis-r14-v17报告1117状态名称差异，SHA bf669c8a95707ae7c79bc8d527ab0b18e6e2d95801fdbd6c45913731b05dc051。补看商店1066–1071完整原帧表，与前轮1054–1065及既有62帧双遍共同确认：1063–1068金额暂时清空而标签/框仍存在，1068子框部分绘制，1069金额恢复但子光标未出现。money-clear-diagnosis-v1.json绑定1062–1069当前字段及3原帧表。此为下项诊断，尚未改该状态实现或GT。

**当前唯一可执行下一步**：用money-clear-diagnosis-v1.json和当前字段/钱框像素设计有界金额清空状态：只在已见有效金额后、当前同一商店钱框及标签仍在而金额格确实为空时标money_temporarily_cleared；金额恢复/缺框/换布局/reset时清历史，子列表绘制/初始光标加载保持优先。不凭固定金额/帧号判断，不恢复旧数值。先明确当前钱框完整轮廓与金额空格条件并写金额存在/消失/恢复及未知字形反例，再目标/同类/全38项r14验证，作为新问题1/3。当前无运行进程，目标active，GT与标准不变，独立会话/UI/真实环境待验证，中文不启动。

**商店金额暂时清空v18（2026-10-17，新问题1/3，全回归运行中）**：上一轮为progress（v17全回归及下一项原帧诊断）。基于money-clear-diagnosis-v1与已直接查看1054–1071原帧/完整62帧既有双遍，新增ShopMoneyTransitionTracker。只在当前SHOP_PARENT、标签和钱框完整（除标签外的上边、左右边、下边及四角）条件下观察7格金额；曾见当前数字+円后，所有金额格valid/blank才标money_temporarily_cleared。保存的只是已见标志，不保存或补出历史金额。未知/多候选、缺边/标签、换布局/reset/时间倒退/尺寸或时间单位改变清历史；金额恢复立即退出清空。判断在子列表选择和部分绘制之前，后者保持优先，1068不误覆盖。

新增2项序列测试：冷启动空不判、金额存在→消失/持续空→新值恢复/reset；未知墨迹、缺底角、切子列表、时间倒退反例。固定离线Maven test通过166项（app98/verifier68），money-clear-tests-v1.log。build-evidence-v18保存209源/96类，未package。实际卖出62、买入48、拒收36目标回放完成，逐对象比较只有卖出1063–1067五帧message_loading→money_temporarily_cleared；1068子列表部分绘制、1069初始子光标加载保持，买入/拒收不变。全部文字/字段/几何/候选/符号/配准除耗时完全相同，money-clear-target-evidence-v1.json保留身份及差异。

实际启动全38项ocr-regression-all-v18，会话2879，money-clear-all-v18.log记录。check-money-clear-regression.cjs已准备全3577引用、原时间线、r14 payload/96类身份、确切五帧变化及aggregate验证；尚未运行，不宣称全回归完成。GT/标准未改，未新JAR/提交/推送；有效全量仍r14/v17原始188/86569错、最终177错、7/116内容精确页、2584失败帧、误报0/692。新问题1/3，旧预算保持，独立会话/UI/真实环境待验证，中文不启动。

**当前唯一可执行下一步**：轮询既有2879直到结束，随后运行node tools/check-money-clear-regression.cjs及独立文本分析，核对38项全部输出并更新有效指标；不重复启动同目录回放。目标active。

**v18全回归封口（2026-10-17）**：上一轮为progress（金额清空实现、166测试/三目标及全回放启动）。本轮轮询既有2879，先确认仍运行，后正常exit0；未重启。check-money-clear-regression.cjs实际验证38项3577保留引用，只改变卖出1063–1067五帧kind，其他输出除processingNanos全对象一致；原PTS/duration/RGB/scored/sourceOffset、r14全部payload与96编译类身份一致。money-clear-regression-v1.json SHA d60136e0ccca014adcbe92c261c489d2a61e51b066b5b448186e027a42ed2402。独立ocr-v18-r14-text-analysis再次复算188/86569原始错误一致。完整aggregate与v17完全相同：原始逐帧99.78283219166214%、等权99.72232854878935%，最终177错/99.79553881874574%、等权99.7287839099668%；7/116内容精确页、2584失败帧、误报0/692。新问题1/3已在本次目标/全范围回归验证；状态改善未解决位置等错误，整体尚未通过。

另核查PC1730–1732当前字段与检测框，并绑定此前完整124帧双遍及对应三原PNG，保存submenu-closing-diagnosis-v1.json。1730子列表完整，1731子框上沿/首行仍在而下部父标签恢复，1732子框完全消失；1731仍被判menu_complete。当前BackpackTransitionTracker失去完整闭框即清历史，无法区分刚开始画子框与正在关闭子框。本轮尚未改该实现/GT，打印检测框一次被输出截断，完整诊断文件已保存，不以截断输出作完整阅读证明。独立会话/UI/真实环境仍待验证，中文未启动，未package/提交/推送。

**当前唯一可执行下一步**：以submenu-closing-diagnosis-v1.json和PC已完成整事件双遍为依据，补核当前子框上沿/两侧残留与父菜单恢复的像素格，设计只有先前完整子框已见、当前只剩上部时触发submenu_partial_closing的有限历史判定。必须覆盖冷启动/初次打开不触发、完整框保持、完全消失/reset清历史，并验证PC、背包、商店同类及全38项r14回归；不用固定帧号/道具名/未来输入，正确GT不改。当前无运行进程，目标active，旧预算不重置。

### 现有v18全部116页错误归因与优先级收口（2026-10-17）

**本轮范围与完成状态**：用户明确要求先用现有v18生成全部116页归因并更新唯一计划。本轮按序完整重读spec/PLANS及本计划全部1066行历史，长文件按范围读取，无截断段作为完成依据。上段PC1731修复下一步现为历史，由本轮页级分析替代。实现的是隔离的确定性离线诊断工具与报告，不是新OCR能力；未运行被测OCR/翻译、未修改app/verification评分器、GT/边界/原帧或阈值，未创建第二份计划/进度账本。未进行新的原图双遍核读，不将报告分析冒称原图语义核验、用户审阅或独立验收。

**输入、版本与命令**：既有`ocr-regression-all-v18`全部38项actual/时间线/Java逐帧比较；r14冻结封套SHA仍`eac7f15d33ed07d8df6d27cb9133da42582716ec48f77b29dee0172cf5b01dfe`；构建身份沿`build-evidence-v18`，96编译类及快照内当前app/verification源逐SHA保持。四原视频实际SHA及四全量原帧账本重新只读核对，身份/66,770原帧/2779.397368秒均保持；完整路径、哈希、time_base、来源版本/会话与全部未选范围见报告`sources/frozenCoverageAndGaps/limitations`。3361评分原帧/134.916228秒、3577保留引用/3507去重保留原帧保持。实际执行：

```powershell
node --check tools/attribute-ocr-pages.cjs
node tools/attribute-ocr-pages.cjs data/gt/frozen/ocr-all-scenes-v2-observation-r14 reports/m3-grid-refinement-v1/ocr-regression-all-v18 reports/m3-grid-refinement-v1/page-attribution-v18-r14-v3
node --check tools/check-page-attribution.cjs
node tools/check-page-attribution.cjs reports/m3-grid-refinement-v1/page-attribution-v18-r14-v3 reports/m3-grid-refinement-v1/page-attribution-v18-r14-check-v1.json
```

生成/交叉检查实际日志为同名`page-attribution-v18-r14-v3.log`和`page-attribution-v18-r14-check-v1.log`。所有输出要求新目录/文件，不覆盖旧数据；复现时使用不存在的新路径。生成器逐3368评分引用独立重算raw/final的Unicode编辑操作、严格区域键、几何/符号/状态及完整差异标签，并与原Java保存分数逐对象相同，再复算原完整aggregate；区域匹配仍按role+字格起点+readingOrder，不按文字找最佳配对。完整页使用原completePage/pageOpportunityId，原117片内页面别名经同源重叠合并为116机会；同一原帧的重复输出仍必须全部正确。

**完整诊断附件**（不是进度账本）：`reports/m3-grid-refinement-v1/page-attribution-v18-r14-v3/index.html`是可直接打开的116行完整表，按录像/原帧排列，可筛选；每页包含原/最终严格与纯文字结果、完整状态原帧/PTS区间、全部差异家族、代表失败原帧/GT/actual链接以及完整逐帧身份。`pages.jsonl`为116条完整页面数据，`frames.jsonl`为3368评分引用全量诊断（含加载、滚动、清屏、空帧等不属于完整页的范围），`report.json`包含全体交集、各维度条件收益、全部输入SHA和覆盖缺口。页面完整帧共2045去重原帧；其余1316评分原帧单独保留，809失败帧未被页面统计吞掉。纯文字诊断仍用原严格字段键，不能替代spec整页/位置/状态或UI时延验收。

**验证证据**：生成器前后输入SHA一致；独立检查器不导入生成器，从原Java逐帧分数和冻结页面声明重新交叉核对全部页面/区间/重叠，116页无缺漏，严格raw/final均仍7页，原始字符188/86569错、等权99.72232854878935%，最终177错、等权99.7287839099668%，2584失败原帧、误报0/692全部保持。独立检查另对2565条“只差源矩形”的原/最终引用确认非源矩形内容完全一致，核564输入哈希、HTML116行/489本地链接和全部/失败/通过/无匹配过滤逻辑；实际浏览器呈现仍待验证，DOM桩不称真实UI。未重跑或冒称重跑166项Java测试、Maven构建或原时间轴OCR回放。报告只证明既有失败可归因和复核。

- 最终报告SHA：`0a1e66fccc381fb0928d715c74a9dd0116a5988047f3c01470b1914d5a368360`。
- 附件清单`manifest.json` SHA：`2f190e839230a37159c34f530e37886e7c71fdfc0da5cd4b90a741219966ac95`。
- 独立检查`page-attribution-v18-r14-check-v1.json` SHA：`fa49460fdd3608a4f8c38109ab3a6da831d472f81a8b6e0d67925c222e67eb74`。
- 生成器SHA：`dddaf69df86b1ee8d85a63018a96668feee870080be4697b4f0b639854d2b38b`；检查器SHA：`8cd515a2bd5db9c4f1afd879a29dc36a31a4504ba31cc5bb7d9cf434dea23428`。

**主要发现与修复优先级**：原始纯文字全对97/116，辅助后98/116；严格7/116并不表示其余109页均读错日文。以下家族可以重叠，不能将受影响页相加；“单独阻塞”指这一家族是当前该页唯一剩余差异，不代表可行性已证明或已通过。

| 影响优先级 / 家族 | 原始/最终受影响页 | 原始/最终单独阻塞页 | 结论与执行限制 |
| --- | --- | --- | --- |
| P0 源像素矩形/投影 | 100 / 100 | 52 / 53 | 绝大部分几何差异仅sourceRect；符号结构相同但源矩形不同也归此家族。影响最大，但既有离散/双线性/面积采样路线3/3保持局部阻塞，不能自行再调参数或改名重置预算。差异本身不证明每项GT/像素投影哪方正确 |
| P1 完整消息判加载 | 31 / 31 | 7 / 7 | 全654个相关完整原帧。现已完成全部116页因果触发对比：100ms使本家族可触发11→30/31，但仍有菜单过渡误触发与单帧短页，见末尾；稳定仅为开发启发式，不是GT证明或验收通过 |
| P2 问号编码 | 17 / 17 | 0 / 0 | 全评分范围168字符差异，完整页内150帧；已完成来源审计，但规范选择仍待用户答复，仅阻塞此项。不能偷偷归一化评分或把编码统一当字形改善 |
| P3 符号结构 | 4 / 4 | 0 / 0 | 仅真正的箭头/光标结构差异；源矩形偏差已经分开。与这些页其余阻塞合并处理，避免再按单帧补丁推进 |
| P3 其他状态 | 7 / 7 | 2 / 2 | 两个单独阻塞页是已记录6208/6466提前淡出标签疑点，仍需证据收口，不能强改算法迎合；其余是等待/菜单/加载标签等差异 |
| P4 紧凑渲染单位 | 1 / 1 | 0 / 0 | 同时有源矩形错误，需独立核查序列化/字段语义 |
| P4 其他文字/附标 | 1 / 1 | 0 / 0 | 完整页中的3541稀疏标记；原3/3预算保持局部阻塞。加载/过渡中其余文字错误仍全部保留 |

当前严格字段键不一致页0、缺输出页0，不继续无依据扩展字段契约返工。按最终输出，失败页的最大交集为53页仅源矩形、22页源矩形+完整判定、15页源矩形+问号编码、7页仅完整判定；其余12页交集逐项见报告，合计109失败页无重复。若真实修复所有源矩形而其余不变，严格页条件上限7→60；仅真实解决无箭头完整判定则7→14；二者均解决时上限89/116，仍不达标。原始对应源矩形上限59、联合88。以上是条件收益分析，不是删除错误类别后的新成绩或修改验收门槛。

**工具失败与恢复**：首生成器因Java BigDecimal把部分字格10写成`1E+1`、JS差异标签写`10`，在全分数对象核对时正确拒绝，未产出假成功；补兼容标签表示后v2/v3全量通过，兼容问题修复1/3。独立检查器初次静态语法检查发现链接断言多一个右括号，执行前拒绝；语法修复1/3后全检查通过。最小复现/日志与验证记录`page-attribution-tool-recovery-v1.json`，v1失败日志及v2中间数据均保留。没有触碰或重置生产问题的累计预算。

**当前唯一可执行下一步**：保持r14/v18输入身份与上述报告不变，读取`report.json`的`summary.final.familyImpacts`中`complete_loading`全部31页及`pages.jsonl`证据，结合`JapaneseOcrEngine.java`当前完成判定和已固定日文打印控制做一次**在线可辨识性审计**。先用仅该项阻塞的7页（video-01首次完整帧6187/6299/6344/9898/13393/13479/24227）及其完整真实前史，分别列出当前/历史观测能确定终止、仍与较长文本前缀不可区分、证据不足的情况，再覆盖全部31页/654原帧；不能读未来帧作为运行时条件、借GT/片段答案或凭静止猜完整。预期产物`reports/m3-grid-refinement-v1/no-arrow-completion-feasibility-v1.json`为**计划附件/尚未实现**，应绑定源与原输出SHA、正反例、可行与不可行边界及不占/占用何项原预算；证据不足则记录具体局部阻塞。该下一步是可行性审计，不提前承诺实现修复或整体达标。独立会话、GT剩余语义资格、UI/真实环境继续待验证，M3/M4未过，中文不启动，本轮无新GT待审卡点。

### 现有v18箭头与文字稳定触发全范围对比（2026-10-17）

**范围与当前效力**：用户明确授权现有输出对比及唯一计划更新。按顺序重读spec、PLANS、本计划全部1110行；首次长输出截断，随后按段补读，包括250–269缺段。上一尾部“先7页在线可辨识性审计/尚未实现no-arrow-completion-feasibility-v1.json”现为历史，改由本节全范围对比完成，未生成第二份计划。新实现仅是隔离的Node诊断策略、对比器和检查器，没有修改app/verification、GT、原帧、既有actual或严格评分器，没有运行新OCR/翻译或新Java构建，不将旧166测试冒称本轮重跑。没有新增GT待审卡点；原几何候选模型3/3、3541稀疏标记3/3以及其他累计预算保持。

**输入与身份**：以`page-attribution-v18-r14-v3`已验证116页/3368评分引用为索引，重新核对其全部输入及附件清单；r14封套SHA仍`eac7f15d33ed07d8df6d27cb9133da42582716ec48f77b29dee0172cf5b01dfe`。四原媒体及四全量账本、r14全部payload、v18全部actual/原PTS时间线/原Java评分、96编译类与生产/验证源身份均一致，572个不可变输入SHA前后保持。四源仍66,770帧/2779.397368秒；38项3577保留引用/3507保留原帧，3368评分引用/3361评分原帧/134.916228秒。全部116机会按既有来源/重叠去重，不以新规则重建页面答案。未选范围、版本/独立会话、GT剩余语义资格和真实环境缺口完整继承。

**先定义再执行的诊断契约**：`tools/completion-trigger-protocol.json`固定主窗口100ms，以及0/33/50/67/100/133/167/200/250ms敏感性范围。0ms也须至少第二个真实观察，不等于首帧立即确认。“箭头判定”基线保留v18现有`message_complete/message_fading/menu_complete/status_page_complete`输出资格，菜单/状态页不是依赖下箭头；消息沿用现有箭头及同内容历史锁存。扩展只在`message_loading`允许稳定触发。策略接口只接收原整数PTS/time_base、rawRegions、当前kind、菜单光标及孤立附标，无GT、来源/片段/帧号/未来输入；全策略判定完成后才关联冻结GT评分。按真实warmup逐片重置，未跨缺口攒静止时间，不在后文或片尾补发过期短页。

稳定签名比较全部原始文字、role/order/gridRect、markCells、logicalRect/renderUnits、菜单选择和孤立附标。等长换字/数字/清浊点/行位置/选择变化均重新计时；不让漂移sourceRect与闪烁下箭头重置文字计时。这是候选触发规则，既有坐标/编码/符号严格计错不变。非消息加载态不获稳定分支；未知字形不由静止变成已识别。待输出候选在签名变化或失去资格时失效，未建设实际UI/队列。

残句误触发定义为在冻结`loading/message_loading`且非完整帧发出新候选；其他非完整转换的新候选单列，不能将它们藏入正确命中。可触发页=完整有效原帧内至少有一次ready，重复引用必须全都ready；文字正确页另要求原严格角色/字格起点/顺序匹配下零编辑错误。它们都不是原严格整页成绩。短页为完整原帧总时长<200ms；两份延迟分别统计首次ready、首次文字正确ready的PTS减首次完整PTS，漏检另列，不填0；按最近秩报告P50/P95/P99/最大。只测源时间触发下限，未包含识别计算、捕获交付、排队、翻译与实际UI呈现，不能授予≤200ms验收。

**主对比结果**（全部116页，最终文字正确口径）：

| 诊断项 | 现有v18判定 | 增加100ms文字稳定 |
| --- | --- | --- |
| 有效期内可触发页 | 94/116 | 114/116 |
| 原始文字正确命中页 | 77/116 | 97/116 |
| 辅助后文字正确命中页 | 78/116 | 98/116 |
| 辅助后文字正确且源时间触发≤200ms | 76/116 | 98/116 |
| 加载残句新触发 | 5次 | 5次，新增0 |
| 其他非完整转换新触发 | 3次 | 4次，新增1 |
| 11个短页未触发 / 未正确命中文字 | 2 / 4 | 2 / 4 |
| 文字正确触发延迟P50/P95/P99/最大 | 0/52.632/2066.667/2066.667ms | 0/125/125/125ms |

100ms新增20个可触发页且均有正确文字命中，没有新增页面漏检。原归因31个complete_loading受影响页中，基线有效期内能触发11页（说明“至少一帧判加载”不等于整次完全漏检），100ms能触发30页且30页正确文字均≤200ms；余一页是video-02:3541单帧。原先6299完整页等到6318才被箭头确认，延迟633.333ms；24227页等到24289，延迟2066.667ms；100ms分别在6302、24230触发。新增完整页面的等待约100–125ms，实际呈现时间仍未知。

**全部失败与取舍**：

- 5次加载残句触发均继承自PC连接1405/1406/1407/1408/1410：文字仍增长，但v18输出menu_complete。不能只给message_loading加稳定分支就消除这类菜单/消息混用。两规则均在6个加载原帧保持ready，5次是按内容变化去重的新触发。
- 100ms新增PC导航video-02:1657的`submenu_opening`误触发，1657–1658有2个过渡原帧ready、只发1次新候选。原3次其他转换触发为PC1731部分关闭、商店1056开启、技能17481详细字段先变；全部原帧/输出均保留，不冒称已解决。
- 两个未触发短页是博物馆询问video-02:3541和商店退出video-03:1450，各只41.667ms；两种规则均漏，0ms/第二次观察也不能挽救仅一帧机会，不能靠延长显示或片尾补发算通过。另外两个短页video-02:1634、video-03:1044有触发，但原问号编码使文字不全对，不能归咎于等待。
- 0/33ms均产生125次残句触发；50ms为11次，67ms为9次，均未比100ms增加页面命中。133ms仍5次残句、4次其他转换、114可触发/98正确文字，等待更长。167/200ms减少PC1657这次新增触发，但17页仅触发等待就>200ms；250ms有22页>200ms。因此100ms是当前有价值的开发候选，不是已达标方案，不继续单纯延长等待逃避过渡问题。

**加载、滚动、清屏全部覆盖**：2045完整原帧之外的1316非完整原帧全部独立保留：加载类514（含479消息加载、30旧loading、5光标加载），滚动类42，清屏/空/非目标722，其他菜单/光标转换38。主100ms与基线在全部42滚动帧、722清屏/空/非目标帧没有ready/新触发；这仅为离线规则结果，不是实际旧译文清除时延证据。新增过渡误触发未从总计中移走。

**实现、命令与检查**：

```powershell
node tools/check-completion-policy-properties.cjs reports/m3-grid-refinement-v1/completion-policy-properties-v1.json
node tools/compare-completion-triggers.cjs reports/m3-grid-refinement-v1/page-attribution-v18-r14-v3 reports/m3-grid-refinement-v1/completion-trigger-v18-r14-v2
node tools/check-completion-triggers.cjs reports/m3-grid-refinement-v1/completion-trigger-v18-r14-v2 reports/m3-grid-refinement-v1/completion-trigger-v18-r14-check-v2.json
```

上述均已实际执行；重跑使用不存在的新路径。对比日志`completion-trigger-v18-r14-v2.log`。v1首轮结果及check-v1保留，v2仅补充增量误触发/漏页定位和中文报告展示，没有变更策略或首轮结果，不是新算法尝试。主策略SHA`78468159f746032c13a2ca67539f90f60432dcca4a6ea312434ca610d203ad83`，固定协议SHA`c4dcfcadab2556a297f65dbd801c28a9e1dae28a70b28763e20bd3273ec99ec8`，对比器SHA`fc1cb5c3cfb0a2deb8f112bf121b5a2f346e521b574f5e2488b1de6b8e88ad30`。

独立检查器不导入策略/生成器，以原actual重新构造签名、向后确定同内容连续区间及整数时间阈值，全部35770次判定逐字段相同；再从冻结GT/原逐帧评分独立核对116页、11短页、1496触发引用、全部首次时刻/文字分数/分位数/转换分母与增量错误。572输入哈希、HTML116行/130本地链接、全部/短页/漏页/无匹配筛选在DOM桩通过，实际浏览器呈现仍待验证。10项独立规则性质检查通过，含阈值前不发、增长/等长替换/行/附标/光标重置、sourceRect噪声、原完成快速路径、滚动/清屏/未知反例、时钟重置、单帧不补发、第二观察及不同未来后缀下前缀输出相同；这些合成性质不冒称新实机验收。本轮未发生运行失败，没有消耗或重置生产修复次数。

**交付附件与身份**：`completion-trigger-v18-r14-v2/index.html`为用户入口；report.json是完整汇总/协议/输入身份，pages.jsonl为116页，frames.jsonl为3368评分引用，decisions.jsonl保留3577真实输入含热身/后文的全部判定，events.jsonl保留全部变体触发及原帧/GT定位。它们是诊断证据附件，不是第二份进度账本。

- 报告SHA：`28f1e1849146f08a52723198bbf0d78f9ae5636a82ec1f869cec5441aa3e7b0c`；manifest SHA：`25989202dd88a8a9cbea4ca10cf3e972d394a52fb7c075d9fd0a696d90f2ca08`。
- 独立check-v2 SHA：`13b8aea718958305850d4646dfcd456a8c10fa1b28d9735df32fbec9531ff827`；检查器SHA：`56e4a8fe622880be1deace62fae3e5776fdb46449718b92499da14a9b40e6c00`。
- 规则性质报告SHA：`b496e765b2700b5668b065f019f095dce41338a6e66ac458c35854b6ed7df4c0`。

**原成绩保持与结论**：原始188/86569错、99.78283219166214%逐帧/99.72232854878935%等权；最终177错、99.79553881874574%逐帧/99.7287839099668%等权；严格7/116、2584失败原帧、误报0/692完全保持。可触发114或文字正确98都不能代替这个严格成绩，未修复100页源矩形问题。独立会话、GT剩余语义资格、真实采集/UI/两小时稳定性继续待验证；M3/M4未过、中文不启动，未提交/推送。

**当前唯一可执行下一步**：保持本轮r14/v18与100ms策略身份，针对新增PC1657开启过渡及继承PC1405–1410加载残句，在既有完整PC连接/导航真实前史中检查当前消息行增长、父/子光标和边框观测，验证“菜单选择完成”与“消息可输出”分开门控的通用候选规则；先形成`reports/m3-grid-refinement-v1/completion-menu-gating-v1/`（计划附件/尚未实现）的全部正反例及因果判定，再以同116页/1316转换帧检查能否减少这6次误触发且保留新增20页。不得按帧号/词句/GT或未来状态门控，不盲调等待、不先改生产；证据不足就记录具体不可区分范围。两单帧短页保留为未解的即时确认问题，不拉长原时间、不因本步无改善而算通过；旧耗尽预算和所有标准保持。

### 现有v18菜单完成与消息完成分开门控（2026-10-17）

**本轮范围与验证状态**：按用户明确授权和上一唯一恢复动作，完成现有v18上的菜单/消息门控实验、全部116页及加载/滚动/清屏复核，并更新本计划。此前已在本上下文完整读取的历史与本轮重读的spec/PLANS、计划最新结果/恢复位置一起用于约束；无新的GT卡点。实现仅在tools隔离诊断层，没有修改app/verification、原GT/契约、原帧、原actual或原评分，没有运行新OCR/翻译或Java构建。新门控第1次有证据的候选实验；全部空心光标规则是事先登记的负向对照，不是采用后再删除失败。v2只扩充逐页延迟证据/修正展示基线，策略和首轮结果完全相同；v1与其源/报告均保留。生产修复预算未消耗或重置，原几何模型3/3、3541稀疏标记3/3及其余限制保持。

**输入/来源身份**：先用原独立检查器复核上一`completion-trigger-v18-r14-v2`（before-menu-v1检查），然后重新核对586个不可变输入：上一完整报告/清单与原572输入、原四媒体/账本、r14全部payload、v18全部actual/时间线/原Java分数、96编译类与生产/验证源，以及本轮协议/策略/对比器/PC来源证据。全部前后SHA一致。四源仍66770原帧/2779.397368秒；38项3577保留引用/3507保留原帧，3368评分引用/3361评分原帧/134.916228秒，2045完整原帧和1316非完整原帧，116原页面机会/11短页不变。r14 freeze SHA仍`eac7f15d33ed07d8df6d27cb9133da42582716ec48f77b29dee0172cf5b01dfe`。旧未选范围、会话/版本与其他缺口全部继承，不新增来源或独立验收。

PC控制语义只读既有固定来源`ui-source-audit-v5`的三份`engine/menus/players_pc.asm`并逐SHA绑定；红绿/蓝SHA为`6a64f64c61a47ccbf686db59690085b207858d752e66b6b9d22e3bc4be06fa83`，黄为`131ba7b9b834b4895d07e8379876b4bd6420dae96d3c84f2da0d7437719e9558`，固定提交沿原来源清单。代码显示父菜单输入后画空心光标，再分派存取/丢弃；打印提示后进入列表；空内容/失败等另有带等待箭头提示。此来源仅支持当前PC道具父/子选择语义，未执行外部代码、未加载固定词句答案，不能声称未出现PC操作或游戏全范围已验证。

**预先固定的三种门控与两个旧基线**：协议`tools/completion-menu-gate-protocol.json`在评分前建立；旧箭头、旧100ms基线必须与上一实验逐判定/逐汇总完全相同。

1. 消息独立确认：菜单实心光标不再直接认证新消息；只凭当前主消息框箭头、原真实消息完成/淡出状态，或当前消息字段稳定100ms确认。消息计时只含消息角色/行位/文字/附标等，菜单选择变化本身不改变消息文字签名。菜单无消息页面保留原立即路径；滚动/清屏/局部重绘等不获得输出资格。
2. 全部空心菜单阻断反例：一律拦只有空心选择的混合画面，用来暴露商店感谢语被误挡的损失；不作为选定规则。
3. 选定PC门控：当前已识别`pc_prompt`且父菜单列1为空心选择、右侧无活动子选择时，稳定消息仍等待；当前主消息箭头可解锁。只用正常角色/当前父子光标/当前主框符号，不读取GT、来源/片段/帧号、固定日文或未来状态；列表下箭头不能冒充主消息箭头。商店空心感谢语保留稳定输出。

策略在关联GT之前连续执行，真实warmup/评分/后文均保留，按原PTS计时并在原片边界重置。输出去重仍使用上一全raw签名，未靠忽略光标/字段变化隐藏误触发。原始/最终文字、几何、符号与严格分数完全保持；本实验只比较候选可输出时刻，不能把新ready标志回写GT或替代逐帧完成状态。

**全范围结果**（均为候选触发，不是UI或严格整页验收）：

| 规则 | 残句新触发 | 其他非完整转换新触发 | 可触发页 | 最终文字正确页 | 原20页收益保留 | 短页未触发 |
| --- | --- | --- | --- | --- | --- | --- |
| 原100ms | 5 | 4 | 114/116 | 98/116 | 20/20 | 2/11 |
| 消息独立确认 | 0 | 3 | 114/116 | 98/116 | 20/20 | 2/11 |
| 全部空心菜单阻断反例 | 0 | 1 | 113/116 | 97/116 | 19/20 | 2/11 |
| 消息独立确认＋PC门控 | 0 | 2 | 114/116 | 98/116 | 20/20 | 2/11 |

选定规则的原始正确文字仍97/116，辅助后98/116；此前新增20页逐页均保留正确文字且源时间触发≤200ms，没有新增可触发页/文字正确页/短页漏检。原100ms的5个PC连接残句1405/1406/1407/1408/1410全部不再发布；PC1657开启过渡也被拦住；PC1731部分关闭不再发布。剩余两次为商店1056开启、技能17481详细字段先变，完整保留，不称所有误触发已消除。全部空心阻断丢失的收益页正是商店感谢video-03:f1468，负向证据验证必须区分当前菜单语义，不能靠全局空心规则换取低误报。

1316非完整原帧全部复核：加载514帧的ready由6→0、新触发5→0；其他菜单转换38帧ready5→2、新触发4→2；全部42滚动帧和722清屏/空/非目标帧仍无ready或新触发。评分原帧和失败均未删除、移到热身或因不利结果重新选样。

**延迟与未解项**：选定规则正确文字触发P50/P95/P99/最大仍0/125/125/125ms，与原100ms全局分位数相同，正确文字源时间≤200ms仍98页。但逐页有14页比原100ms多等41.667–125ms，1页更早；完整列表、原因和有效期剩余时间在`report.comparison.readinessDelayChanges`。PC/商店子光标转换被当作整帧不合格时会清除消息确认，恢复后重新等待，是后续应验证的额外等待来源，不能在未验证前直接放开。

特别是商店买入video-03:f860短页，旧规则860立即ready，新规则863才ready（125ms），仅剩41.667ms原有效显示期可供后续处理/呈现。源时间上仍命中不等于实际UI能赶上；报告明确列出此余量，不能用全局最大值125ms掩盖短页。原漏检两个单帧页video-02:3541、video-03:1450均41.667ms，仍未解决；另两个短页有触发但原问号编码使文字不正确，仍单列。未测捕获/计算/队列/翻译/实际UI，不授予≤200ms或短页实际显示验收。

**实现与实际命令**：

```powershell
node tools/check-completion-triggers.cjs reports/m3-grid-refinement-v1/completion-trigger-v18-r14-v2 reports/m3-grid-refinement-v1/completion-trigger-v18-r14-check-before-menu-v1.json
node tools/check-completion-menu-properties.cjs reports/m3-grid-refinement-v1/completion-menu-properties-v1.json
node tools/compare-completion-menu-gates-v2.cjs reports/m3-grid-refinement-v1/page-attribution-v18-r14-v3 reports/m3-grid-refinement-v1/completion-menu-gating-v2
node tools/check-completion-menu-gates-v2.cjs reports/m3-grid-refinement-v1/completion-menu-gating-v2 reports/m3-grid-refinement-v1/completion-menu-gating-check-v2.json
```

复现必须改为不存在的新输出路径，工具拒绝覆盖；对比日志同输出目录名`.log`。策略`completion-menu-gate-policy.cjs` SHA`ee787cdd27ca3f8da4c7e14c50d575dbc271d1f9b59c9e5ba5fac0bca6ddec1f`，协议SHA`92b432bf68b344f3e1d073f3b7e6ac6652a55a0fffab335ef702178711b57e42`，最终对比器SHA`21a840316166d6b58b48da71f2573f5ce6c1eb052c1f6c3407ab9d768f97fbeb`。v1报告和检查通过后，v2另存对比器/检查器及报告以增加延迟证据，并把逐页表的比较列明确为旧100ms；没有覆盖v1源或改变策略，没有发生运行失败。

独立检查器不导入策略/对比器，以原actual重新构造完整签名和消息签名、真实历史、整数时间、确认/父子选择与发布历史，17885次判定逐字段相同；再按冻结GT与旧严格分数核对全部116页/11短页、604触发引用、分位数、全转换分母、增量失败、原20页名单及逐页等待/剩余有效期。两条旧基线3577×2判定和全部原统计与上一实验完全一致；8项预设实验条件全真，只授予本诊断任务达成，不授予产品验收。586输入SHA前后保持，HTML116行/123本地链接及全部/短页/漏页/无匹配筛选在DOM桩检查通过；实际浏览器显示仍未宣称验证。

13项性质检查通过，覆盖实心父选择不得认证增长消息、菜单无消息立即路径、消息阈值/去重、PC悬置/子选择解锁、主/列表箭头区别、商店空心反例、等长换字/附标/行变化、纯选择变化不重置文字计时、未知字、清屏/滚动/部分绘制、时钟重置、单帧不补发、未来后缀及额外GT/身份属性不影响已有前缀。它们是合成性质，不冒称新的实机原图核验或独立验收。

**交付与哈希**：用户入口`reports/m3-grid-refinement-v1/completion-menu-gating-v2/index.html`；report.json含完整汇总/规则/来源与输入身份/逐页等待代价，pages.jsonl含116页、frames.jsonl含3368评分引用、decisions.jsonl含3577保留引用的全部规则判定、events.jsonl保留全部触发及原帧定位。全部为诊断证据附件，不另建进度账本。

- report SHA：`7d8db053d58a7b77460eb959c55fceda2aa6c9bcf15ba0944515eb59a5b98d24`；manifest SHA：`b3592f3df1c6124992156002dd56df0c9a1b8a8f23d13e88f3d7384cb0370da5`。
- 独立check-v2 SHA：`8c20ec0acafd1cfb2d9236f4ca012a90bfdf91d55fea0d74d3279ab36bb49123`；检查器SHA：`adc0e808f9ad6cc4d92a9a7d3d35d7862f883e3d3e50b294daa04bfec1909382`。
- 性质报告SHA：`10d684a929d282beb5184325c68090593a86ff5eb72500412194efcf7f1734bb`。

**原验收结论保持**：权威r14/v18原始188/86569错、99.78283219166214%逐帧/99.72232854878935%等权，最终177错、99.79553881874574%逐帧/99.7287839099668%等权，严格7/116、2584失败帧、空误报0/692均不变。100页源矩形等其他失败未解；独立会话、GT剩余语义资格、真实窗口/UI/两小时稳定性仍待验证，M3/M4未过、中文不启动。本轮没有新Maven/JAR、提交或推送。

**当前唯一可执行下一步**：固定本轮r14/v18与选定门控为对照，在隔离诊断层验证“当前消息签名完全未变时，纯菜单/子光标转换只暂缓发布、保留已有消息确认”的候选，重点核对video-03:f860等待125ms/仅余41.667ms的短页及全部14个变慢页；文字/行位/附标变化、滚动、清屏、时钟/reset仍必须丢弃旧确认，PC悬置门控保持。输出`reports/m3-grid-refinement-v1/completion-message-history-v1/`（计划附件/尚未实现），在同116页/1316转换帧证明能否减少新增等待，同时要求残句0、其他转换不超过2、20页收益和全部短页不回退；失败保留并按本门控候选累计第2次处理，不先改生产/GT/原标准，也不重开耗尽预算。


### 2026-10-17 消息未变确认保留：离线验证已过，进入授权生产接入

用户明确要求验证通过后接入生产并重跑全部38片，本轮无新增批准卡点。固定r14/v18、100ms和原116页/1316转换帧，新增`tools/completion-message-history-policy.cjs`，不覆盖既有策略。消息签名按消息内相对顺序（不受菜单字段数量影响）、角色/文字/行格/附标/渲染单元及消息区域孤立标记判等；全候选去重签名仍包含全部原字段和光标。只在明确菜单绘制/列表滚动/光标/金额转换时保留已经确认的未变消息，不在转换中确认新消息或发布。消息滚动、清屏、文字/位置/附标变化、无消息、重置或时钟倒退/变化撤销确认；PC悬置门控不变。

最终离线报告`reports/m3-grid-refinement-v1/completion-message-history-v2/index.html`，report SHA`f862fc12a172c07ea11e490c81ba23d6f690eb2e034c588bf3216e93b382da92`；独立检查`completion-message-history-check-v1.json`，594输入前后SHA一致、14308判定一致、116页面/11短页/481事件引用/1316转换复核。18项性质`completion-message-history-properties-v1.json`通过。v1保留首次数据；v2仅修正比较标签并补全剩余9页真实历史证据，策略未改，不计新修复尝试。

结果：残句0、其他转换2，114页可触发、97原始/98最终文字正确、20收益均正确且源时间≤200ms，短页仍2未触发/4文字未命中，无回退。原14额外等待中的PC1698/1708/1716/1725和商店860均125→0ms；另museum3542为125→83.333ms。其余9页保留等待：5页在片首真实热身尚未满100ms（PC1634、商店820/1044/1408、队伍3664）；PC1661问号刚出现、商店1070首次新消息未安全确认；PC1737及商店1451清屏后新消息不能继承旧确认。源时间分位仍0/125/125/125ms；关键商店860有效期余量41.667→166.667ms，不冒称UI实测。

实际命令：`node tools/compare-completion-message-history-v2.cjs reports/m3-grid-refinement-v1/page-attribution-v18-r14-v3 reports/m3-grid-refinement-v1/completion-message-history-v2`；`node tools/check-completion-message-history.cjs reports/m3-grid-refinement-v1/completion-message-history-v2 reports/m3-grid-refinement-v1/completion-message-history-check-v1.json`；`node tools/check-completion-message-history-properties.cjs reports/m3-grid-refinement-v1/completion-message-history-properties-v1.json`。原menu-gating-v2也在修改生产前重检通过，证据`completion-menu-gating-check-before-history-v1.json`。

**当前唯一可执行下一步（正在执行）**：将本候选作为独立输出资格/事件字段接入`JapaneseOcrEngine`，不篡改可见kind、文字或严格评分；完成Java验证、保存v19源码/类身份，运行`./tools/validate.ps1 ocr-replay-assisted data/gt/frozen/ocr-all-scenes-v2-observation-r14 all reports/m3-grid-refinement-v1/ocr-regression-all-v19 .cache/japanese-source-aliases-v1/catalog.json 2eb7e1fd2a393230cb426b565e52c1e110f040d9ce35cf0bbd52c979800192e3`，逐帧比较生产新字段与离线候选，同时证明除处理耗时/新增字段外原输出及GT/媒体不变，复核全116页/转换阈值后更新本计划。旧报告绑定的当前Java/类路径将在合法新构建后变化，不能再称594旧路径仍现值相同；旧v18源码和类快照保留，须逐项记录新旧差异。


2026-10-17 生产接入中间验证：`OutputReadinessTracker`已在`JapaneseOcrEngine`每帧最终分类之后执行，返回正常图像/源PTS的候选输出资格，独立保存于`Result.outputReadiness`；无GT、片段身份、固定消息或未来输入。原消息kind、raw/辅助文字、坐标、符号均未刻意修改，是否逐帧保持由全回放再核对。消息计时用整数有理数与BigInteger避免阈值溢出；时钟倒退/变化、显式reset和输入尺寸变化清除历史。Java新增10测试并扩充真实引擎合成场景断言，176全通过；测试不是实机验收。最终日志`build-v19-final-test.log`，package日志`build-v19-package.log`，快照`build-evidence-v19`233源/100类；`package-v19-identity.json`证明JAR68类与当前编译结果逐SHA相同，JAR SHA`9fb76c7c0cb208ace8b5962ea3826adee2e5d761048ce0849da5c33434a0f746`。

**当前唯一可执行下一步（正在执行）**：等待已启动的`ocr-regression-all-v19`完成38片（日志`ocr-regression-all-v19.log`），随后运行`node tools/check-completion-production-v19.cjs reports/m3-grid-refinement-v1/ocr-regression-all-v19 reports/m3-grid-refinement-v1/build-evidence-v19 reports/m3-grid-refinement-v1/completion-production-v19`验证所有3577观测与离线候选一致、原OCR/时间轴/分数不变及594历史输入仅允许预期源码/类身份变化；若失败保留真实差异，不覆盖旧输出，按本路线累计预算处理，再更新本计划。

### 2026-10-17 v19生产门控与全部38片重跑完成

**范围与决定**：用户已明确授权离线条件通过后接入生产并重跑38片。本轮先完成离线独立检查，再修改生产，固定四录像、r14封套、原38片及全部热身/评分/后文；未改GT、评分器、选样、短页定义或原标准。当前权威OCR回放为r14/v19，v18保留为对照。M3/M4及中文阶段边界保持。

**实现**：`OutputReadinessTracker`从当前raw字段、主消息框/光标和源PTS计算资格，集成于`JapaneseOcrEngine`最终可见分类之后，保存为`Result.outputReadiness`。提供ready/fire、invalidate、menuReady/messageReady、messageConfirmed、PC悬置及计时证据；不将ready回写kind美化严格评分。只跨明确菜单/列表转换保留已确认且未变的消息，转换中不发布、不确认新消息；消息文字/角色/行位/附标/渲染结构变化、清屏/消息滚动、缺失、reset、时钟倒退/变化或输入尺寸变化撤销。消息内顺序与菜单字段数量分开，全候选去重仍包含所有菜单/选择。不从历史补当前文字，不读GT、身份、固定消息或未来输入。100ms阈值用整数有理数/BigInteger，避免溢出。

| 检查 | v19实际结果 | 结论范围 |
| --- | --- | --- |
| 全部38片实际图像重跑 | 3577保留引用/3507原帧；3368评分引用/3361评分原帧；116页及1316非完整帧全保留 | 开发/回归范围 |
| 生产与离线一致 | 3577判定逐字段一致；浮点显示年龄仅1e-8ms数值容差，阈值精确比较 | 门控接入正确，非UI验收 |
| 残句 / 其他转换误触发 | 0 / 2 | 达到本轮0和≤2条件，两次失败保留 |
| 可触发 / 原始文字正确 / 最终文字正确 | 114 / 97 / 98，分母116 | 不是严格精确页 |
| 原新增20页 | 20/20最终文字正确，源PTS等待≤200ms | 逐页名单保持 |
| 短页 | 11页，仍2未触发、4最终文字未命中 | 无新增漏页、无页面变慢 |
| 原14额外等待 | PC1698/1708/1716/1725及商店860共5页125→0ms；9页保持等待 | 4个PC页原问号错误保留，提前ready不是文字修复 |
| 其他改善 | museum3542为125→83.333ms | 另计，不算原14页 |
| 关键短页video-03:f860 | 首个完整帧ready/fire；有效期余量41.667→166.667ms；该帧算法耗时50.5602ms | 不等于实际呈现延迟 |
| 原OCR / 时间轴 / 严格评分 | 除processingNanos和新增outputReadiness外3577结果完全一致；原PTS/RGB哈希/评分标识和逐帧评分保持 | 原分数不变 |
| Java / 构建 / 包 | 176测试（108 app＋68 verification）全过，package通过；233源/100类，JAR68类逐SHA匹配当前编译类 | 不替代实机/独立验收 |

剩余两次为商店video-03:1056 `submenu_opening`、技能video-04:17481 `menu_detail_before_cursor_transition`。514加载帧仍0ready/0fire；42滚动帧及722清屏/空/非目标帧仍0ready/0fire；其他38转换帧仍2ready/2fire。两个单帧短页video-02:3541、video-03:1450仍未触发；问号短页等原失败保留。9页未改善的真实历史见离线报告`unimprovedDelayEvidence`，没有继承新消息或虚构片前历史。

正确文字源PTS等待P50/P95/P99/最大仍0/125/125/125ms。当前算法全段耗时P50/P95/P99/最大50.8995/76.4335/88.6942/265.8147ms，对照49.4972/73.6753/85.0299/265.3543ms；不是控制速度实验，不据此声称无性能回退，也不将“源等待＋算法耗时”当UI≤200ms验收。捕获/排队/翻译/实际呈现仍未测。

**实际命令**（重跑使用新输出路径，拒绝覆盖）：

```powershell
mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' test
mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' '-DskipTests' package
node tools/snapshot-ocr-implementation.cjs reports/m3-grid-refinement-v1/build-evidence-v19
./tools/validate.ps1 ocr-replay-assisted data/gt/frozen/ocr-all-scenes-v2-observation-r14 all reports/m3-grid-refinement-v1/ocr-regression-all-v19 .cache/japanese-source-aliases-v1/catalog.json 2eb7e1fd2a393230cb426b565e52c1e110f040d9ce35cf0bbd52c979800192e3
node tools/check-completion-production-v19.cjs reports/m3-grid-refinement-v1/ocr-regression-all-v19 reports/m3-grid-refinement-v1/build-evidence-v19 reports/m3-grid-refinement-v1/completion-production-v19
```

完整176测试已在test步骤通过，package的skipTests仅免重复，二者不混称。日志`build-v19-final-test.log`、`build-v19-package.log`、`ocr-regression-all-v19.log`保留。检查器不导入策略，比较生产判定与已独立重构结果，按原评分重算页命中/误触发，核对原actual、全部时间轴、逐帧分数和总分。594历史输入只有预期4路径变化：引擎源码/测试及Engine.class/Engine$Result.class，其余590路径（含媒体、GT、原输出、策略与评分器）SHA保持。变化源码与保存的v18副本核对，新源/类绑定v19快照；旧类只有此前已核验身份，不捏造现存v18类字节可运行。

**交付与身份**：入口`reports/m3-grid-refinement-v1/completion-production-v19-v2/index.html`链接全116页/转换原图、生产核对和全38严格评分；manifest/HTML/本地链接检查通过，打开请求已交给应用，未将queued冒称实际渲染核验。

- 生产report SHA：`7cb05ce47638653f3eec001d11c40db0338c31dc841b23a179f2c67a8eb6d7de`；manifest SHA：`88ac2676126264862580332112eb5e6cc2a256a97eaf14cea7c97bf4bc4d45de`。
- 检查器SHA：`ede5dc5a1f366641eb2b647051ddca8f709a9d979ec456cd7e84b7176050411f`；源快照SHA：`87d4257f310d95048991fe81e9e2e417cd7d29de9fc1888322a62792e9553aef`；类身份SHA：`9953f24540b10bb7e09dded2938d80dfebe866f1633ddfb0149ebdc109eebad0`。
- JAR SHA：`9fb76c7c0cb208ace8b5962ea3826adee2e5d761048ce0849da5c33434a0f746`；`package-v19-identity.json`证明全部68包内类与编译类匹配。
- 离线v2 report SHA：`f862fc12a172c07ea11e490c81ba23d6f690eb2e034c588bf3216e93b382da92`；独立检查SHA：`109fa82e87cb9f3ca075870764a0fce832ff7485e1a580dddf04672cb1852e5b`；18性质报告SHA：`f009244328d86a4c4dfc2d13f4cf53b84c2677265c8564b109aed4b0e8b987cd`。

**修复累计与恢复**：本门控路线第2候选达到预设目标后接入生产；无隐藏失败重试，未执行第3候选。首次Java全测通过后补充签名长度边界和尺寸重置健壮性，再次全测通过，最终包/回放使用后一版本；不是按成绩调参重试。离线v1/v2策略相同，只修正比较标签并补解释证据。最后写入本段时一次文档脚本因引号解析失败，未写入文件，随后直接修正文档；不计为算法修复。此前几何路线及video-03:3541孤立标记3/3阻塞保持，不清零、不重开。无提交/推送。

**总体状态**：r14/v19原始188/86569错、99.78283219166214%逐帧/99.72232854878935%状态等权；最终177错、99.79553881874574%逐帧/99.7287839099668%状态等权；严格7/116、2584失败帧、空误报0/692。100页源矩形等失败未解。门控条件通过不等于严格OCR、独立会话、GT剩余语义资格、未选范围、实际UI/两小时稳定性验收通过；M3/M4未过，中文不启动。

**最终报告说明修订（不改算法/数据）**：v1生产report继承了离线实验的“saved-v18、未做像素重跑”等限制文字，容易与本轮实际38片重跑混淆。保留v1原件，运行`node tools/finalize-completion-production-v19.cjs reports/m3-grid-refinement-v1/completion-production-v19 reports/m3-grid-refinement-v1/completion-production-v19-v2`生成最终入口；把旧限制单列为offlineExperimentLimitations，生产limitations改为真实全38片重跑及UI/独立验收的剩余边界。逐项核对原manifest/全部输入身份，除说明字段外JSON完全一致，pages/index字节相同。最终report SHA`49063859a1817fa82d3a4ed0c792c0a84af6a43c0153070b56f69c325ea25f86`，manifest SHA`74dca1efac5a2e35fbf47e51e92d7b4ebb89f763a1c6fe367f092fccbfa27c78`；此是报告措辞修订，不计第3算法候选或新构建，v19源/类/JAR不变。

**上一轮下一步（已由下述用户审计要求替代，未执行）**：以固定r14/v19实际输出为基准，只读提取商店video-03:1056和技能video-04:17481两次剩余过渡误触发的前后原帧、当前消息/菜单字段/光标签名及状态变化，建立最小复现并核对是否存在共同的当前帧拒绝证据；附件计划路径`reports/m3-grid-refinement-v1/completion-transition-root-cause-v1/`（尚未实现，不是新进度账本）。先核根因，不提前放宽100ms或静默去重；若提出第3候选，须先固定残句0、其他≤2、20收益、短页及全116页/1316转换不回退条件，按累计预算验证，不动GT/原标准，不重开耗尽的几何路线。

### 2026-10-17 r14/v19全部116页坐标与计分口径审计完成

**授权与范围**：按用户最新要求，保留现有成绩，先审计spec、冻结契约、实际评分实现与已有v19输出；不做新的生产算法或评分器修复。输入为r14封套、全部38片v19 actual/逐帧评分/aggregate、原116页归因、v19源/类/JAR身份、原始帧与四媒体。`tools/coordinate-contract-audit-protocol.json`先固定精确区域键、原分数重构、独立投影及像素风险方法，后执行工具；无文本模糊匹配、问号归一化、GT改写或按成绩选帧。

**覆盖与身份**：3577保留引用、3368评分引用/3361去重评分原帧，116完整页机会、2045完整状态原帧、1316非完整原帧全部登记（514加载、42滚动、722清屏/空/非目标、38其他）。每张评分PNG读回RGB并匹配冻结账本，全部4211原输入SHA在准备、最终生成和独立检查时一致，包括四视频、GT、实际输出、旧报告、评分实现、v19源码/类/JAR。29个无法严格成对的符号目标仍保留结构差异，不塞进成对像素统计或删除。现有四视频仍为66770原帧/2779.397368s素材池，本次只覆盖既有冻结38片，不另扩样；实际评分时长仍134.916228s，未选时段、独立会话/版本/真实环境缺口保持待验证。

**计分契约结论**：

- `spec.md:132`及冻结v1/v5的`rectangles`明确：位置以区域、字格占位和字段归属一致判定，像素边框差异另报。现有`verification/src/main/java/io/github/g1ocr/verification/OcrScoring.java:79`把`sourceRect`和`gridRect/markCells/logicalRect/renderUnits`共同判入`regionGeometry`，第83行对符号对象做包括源矩形在内的相等比较；这会让仅像素框数值不同也阻塞严格整页，需版本化拆分。
- 冻结v5同时要求X/Y有理数独立投影、左上floor/右下ceil、动画偏移先于投影、真实可见附标包围范围；因此不能取消全部源坐标检查，不能给所有100页坐标差异豁免。15561次冻结区域/符号投影复核一致；当前输出的区域也都符合其自身估计网格的投影，未发现floor/ceil反用。主要是网格起点/尺度估计不同，个别还有漏附标引起的包围范围变化。顶部差异最大30像素，不能用全局±2像素容差覆盖。
- 区域仍严格按role、基字格起始行列、readingOrder匹配；全占位、附标、紧凑逻辑像素/渲染单元、符号和状态分别核对。字段归属错误与附标挂错字分别报告，不能混为边框问题；不以文字相同反推正确配对。
- 原完整页严格分数仍是全部完整状态帧的合取；加载/滚动/清屏等每一帧仍评分。实际UI呈现时延没有观测证据，本次没有改动或通过该项。

**116页最终结果互斥归类**（raw另见report，逐页有完整帧/原图、原分数、预期/实际矩形与逻辑证据、修复归属）：

| 页类别 | 页数 | 本轮判定 |
| --- | ---: | --- |
| 原严格正确 | 7 | 原成绩保持，不增加验收范围 |
| 仅源像素框数值不同，未检出前景边缘风险 | 14 | 文字/逻辑/字段/附标/符号/状态均一致；优先评分器维度拆分，未检出风险不是物理安全证明 |
| 仅源像素框数值不同，仍有边缘/核心/相邻前景提示 | 39 | 逻辑上一致，物理包围范围仍待确定；不能当39页已截字，也不能全当无害 |
| 源像素框差异与其他问题并存 | 47 | 分别保留算法/契约问题和源边界证据 |
| 没有源框差异，只有其他问题 | 9 | 不归咎于边框 |

共100页存在源框差异，最终53页仅此一项；raw仅此项52页。仅排除`sourceRect`而保持其他检查的raw/final诊断为59/60页，它只解释共同阻塞，不是新整页成绩、及时输出或独立验收。原始188/86569错、99.78283219166214%逐帧/99.72232854878935%状态等权；最终177错、99.79553881874574%逐帧/99.7287839099668%状态等权；严格7/116、2584失败帧、空误报0/692全部原样保留。

**漏字、截字与字段归属的证据边界**：原始188和最终177编辑错误在本范围内均为替换，整字删除/插入0；严格配对的字段缺失/额外字段/逻辑占位不一致均0。这不表示每个像素框都正确，也不表示漏浊点不算错。最终7个原帧保留字面/附标差异：video-02的1659（ポ→ホ）、1697/1706（ざ→さ）、1724（ひで→ぴて，附标种类/位置错）、1731（ぐ/す→く/ず，附标挂错字）；video-03的3541（ウツボット→ウツホット）和1068（ポ→ホ）。其中3541是1个完整页，其余6帧是转换；它们不能因取消源框相等而消失。3541姓名框预期top0、实际top29并漏上方浊点，属于内容/附标问题；原稀疏标记路线3/3阻塞保持。没有据此改冻结GT或新授予语义资格。

**物理边界检查**：隔离Java8/ImageIO工具逐张读取3361原PNG，检查15532个严格配对目标范围。用冻结基字格及明确附标支持区、固定亮度/Otsu/少数极性与核心对比度规则，记录实际框外候选前景及新增边缘内相邻候选前景；它不识别字形。发生坐标变化的目标引用中，11049无前景遗漏提示、1685核心在框外、1450边缘前景在框外，另1426含新增边缘相邻核心提示（交叉统计，不能相加当页数）。全部116页首个完整帧概览、66组代表风险与12组文字/附标差异共15张联系表均由当前代理直接看图；检查输出已可见，**不是新增盲读GT、人工逐帧核验或3361帧全部人工重读**。video-02:6916的HP框虽有209个核心候选在实际框外，原图可见其上方HP横条而数字200/206完整；相邻行笔画也会干扰支持区。保留风险而不把阈值数直接宣称截字，原图观察和每例处置存`agent-image-review.json`。

**修复归属与优先级**（页影响可重叠，不用相加凑分母）：

| 优先级/归属 | 具体问题与范围 | 应采取的动作 |
| --- | --- | --- |
| P0 评分器 | 100页源框差异被混入位置/整页；53页仅此阻塞 | 新版本分列文字、字段/字格/附标/单元、符号、状态、源投影/包围范围；保留legacyStrict及全部差异，不能改旧成绩或直接认可60页 |
| P0 评分契约兼容 | video-02:f5286旧试标下箭头未写style，实际filled_down，逻辑可见位置一致；同范围complete_waiting与message_complete命名不同 | 按冻结版本明确兼容表示，先证据核对，禁止全局删除style或将所有kind自动等价；GT原字节不改 |
| P1 算法/可见附标 | 上述7帧漏附标或附标归属差异，涉及1完整页/6转换 | 保留失败及原图最小复现，按现有预算处理；3541原路线仍3/3，不借本审计重启 |
| P1 算法输出结构/符号/编码 | video-03:f3664的HP与冒号被并成单个HP:单元（文字/总逻辑范围相同、单元细分不同，20原帧/115区域引用）；3页非旧版符号存在差异；17页问号编码差异 | 修结构输出、可见符号及明确字形编码，不能忽略renderUnits或在评分时归一化问号。原4个symbol_structure页中仅1页为上述旧版表示问题 |
| P1 待定位的坐标/包围范围 | 全部页中75页有边缘风险提示，含53个仅源框阻塞页中的39页；并非75页已截字 | 保留每帧/每目标像素与逻辑证据。进一步算法工作先证明目标笔画归属和可辨识性，不把HP条或相邻行当漏字，不重开耗尽几何路线 |
| 后续 状态算法/契约 | 原38个状态影响页中1页是旧版命名需审计，其余37页保留状态问题 | 不将outputReadiness回写kind，不因本次拆分位置评分消除状态失败；原两次门控过渡误触发与短页漏页保持 |

**工具与实际命令**（仅隔离审计工具；输出路径拒绝覆盖，重跑应另取v2路径）：

```powershell
node tools/audit-coordinate-contract.cjs reports/m3-grid-refinement-v1/coordinate-contract-v19-v1
javac -encoding UTF-8 -cp .cache/m2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar -d .cache/coordinate-contract-audit-v1/classes tools/CoordinateInkProbe.java tools/CoordinateAuditSheets.java
java -Xmx512m '-Dfile.encoding=UTF-8' -cp '.cache/coordinate-contract-audit-v1/classes;.cache/m2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar' CoordinateInkProbe reports/m3-grid-refinement-v1/coordinate-contract-v19-v1/pixel-jobs.jsonl reports/m3-grid-refinement-v1/coordinate-contract-v19-v1/pixel-probe.jsonl
node tools/prepare-coordinate-review.cjs reports/m3-grid-refinement-v1/coordinate-contract-v19-v1
java '-Dfile.encoding=UTF-8' -cp '.cache/coordinate-contract-audit-v1/classes;.cache/m2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar' CoordinateAuditSheets reports/m3-grid-refinement-v1/coordinate-contract-v19-v1/review-cases.json reports/m3-grid-refinement-v1/coordinate-contract-v19-v1/review-sheets
node tools/finalize-coordinate-contract-audit.cjs reports/m3-grid-refinement-v1/coordinate-contract-v19-v1
node tools/check-coordinate-contract-audit.cjs reports/m3-grid-refinement-v1/coordinate-contract-v19-v1 reports/m3-grid-refinement-v1/coordinate-contract-v19-check-v1.json
```

两Java工具实际分次编译至上述隔离目录。首次像素检查启动时PowerShell误解析未加引号的`-Dfile.encoding=UTF-8`，JVM在读取输入前退出；`pixel-probe.log`保留，没有生成结果。加单引号后一次完成全部3361帧，实际日志`pixel-probe-v2.log`；不是算法候选/调参尝试。独立Node检查器不导入生成器/被测OCR，重构6736次raw/final逐帧检查、3368引用/3361原帧、116页归类、7附标帧、1旧箭头描述页，复核15532像素目标关联、4211原输入/10辅助身份及manifest，2190本地链接和筛选控件DOM替身检查通过。真实浏览器渲染未作为已验收证据。未执行Maven、生产构建或新OCR回放。

**报告身份与恢复**：入口`reports/m3-grid-refinement-v1/coordinate-contract-v19-v1/index.html`，全表`pages.jsonl`、逐帧`frames.jsonl`、像素`pixel-probe.jsonl`、原图复核`agent-image-review.json`均在同目录。原v19/v18/旧评分报告、GT及失败证据全部保留。

- report SHA：`312a1256c60c8480f5beb3a8bd796d8392faf5b265055a2d0d093dbeae070125`；manifest SHA：`29ea77be3a7d8f99344e9e64c0e3788fe4a7ab743565883e054b62ebb268b9f5`。
- 协议SHA：`d8c28354e83a37151a8e0f657a81cbd9a43dec48f48a65838df790e4d1940f27`；原图诊断记录SHA：`32d55da491e468fbbc6202dd9d91a34a4ca5ef0593bf6d2b992a0c49eafca03a`。
- 独立检查结果`coordinate-contract-v19-check-v1.json` SHA：`39b67915e7a7c66dc531024ac1372eee1ee1cfd45acb73cbf61aeaf20fab0485`；检查器SHA：`67d21f40eb61014445a4294b31494656973d0c05eda44ac9232a722e35c7099b`。
- r14封套SHA仍`eac7f15d33ed07d8df6d27cb9133da42582716ec48f77b29dee0172cf5b01dfe`；v19源快照/类身份及JAR仍为上一节已绑定身份，176测试/38片回放是此前生产证据，不冒称本轮新跑。

本轮完成的是审计与独立证据复核，不是评分器修复、物理坐标全部正确或M3/M4通过。GT `allSemanticIntegrityPassed:false`、独立会话、真实UI/窗口/长时稳定性、未选录像范围仍待验证；中文不启动。旧几何及3541路线3/3阻塞、门控第2候选通过记录均不变，不清零、不新增生产尝试。无提交/推送。

**上一轮下一步（已由下述分维度报告完成）**：在不改旧评分器/GT/原成绩的前提下，实现一个**独立版本的分维度评分报告**。上述计划接口现已实现；其原命令、检查和范围保持可追溯，不将当时尚未实现的状态冒称提前完成。

### 2026-10-17 独立分维度报告、旧描述兼容与全范围复核完成

**本轮范围**：用户已明确授权执行上一下一步。完整重读spec/PLANS并恢复唯一计划的现有r14/v19证据，先写`tools/scoring-dimensions-protocol-v1.json`，后实现独立`tools/score-contract-dimensions-v1.cjs`；不修改`OcrScoring.java`、生产OCR、GT、选片/评分范围或原输出，不运行新OCR/构建。原坐标审计及所有旧版本报告保持。全范围为3577保留引用、3368评分引用/3361去重评分原帧、2045完整状态原帧/1316非完整原帧、116完整页机会、715连续状态，其中553个非完整连续状态作为转换机会报告。553不是新增页面或独立事件会话数。

**固定接口与计分方法**：每个raw/final帧保存`legacyStrict`原评分，文字、字段位置、符号、状态、像素边框各自结果/差异，以及仅供诊断的`logicalAgreement`。文字仍按Unicode标量最小编辑距离、不删空格/问号/附标/小假名；严格role＋起始字格行列＋readingOrder配对，不按文字找最佳字段，错角色/行位/阅读顺序导致缺失与额外字段并相应记删除/插入。字段位置维度完整检查gridRect、markCells、logicalRect、renderUnits；不会因为字面相同而取消附标或单元错误。符号比较全部光标/下箭头/孤立附标的存在、种类/样式、逻辑位置和次序；源矩形另列，描述原始相等结果也保留。状态保存原kind相等和限定兼容结果。像素边框保存全部源矩形差值、无法配对目标及旧原图像素风险；目标缺失记为不可比较，不能称为边框偏移。数值相等不表示物理包围范围/实际UI合格。

同一原帧只计一次，重叠回放的每个维度取全部一致的合取，字符错误取最大值；冻结连续状态与页面机会通过重叠原帧连接去重，保持原715状态/116页，不能合并重新出现的不同机会。每页维度一致必须覆盖全部完整状态帧；所有转换帧仍逐帧计分，并按553连续转换机会另汇总。`frames.jsonl`保留所有3368引用，`states.jsonl`保留715状态，`transitions.jsonl`保留553转换机会；HTML完整列116页及全部转换机会/每个原帧链接，支持范围、维度和文本筛选。

**旧箭头/状态描述兼容的限定**：只有契约SHA`2930e6b370616fe29402d724e890ee4314ad9f714f9b8cae377484137d934c6c`、预期schemaVersion1、cursor.downArrowVisible=true、旧扁平gridRect/sourceRect有效、无嵌套downArrow且未指定style时，才在派生比较副本中把预期箭头解释为filled_down；实际箭头缺失、样式错误、位置不同、额外符号仍失败。不会改GT字节或省略实际style。对应旧结构若同时为completePage、kind=complete_waiting且预期非空字段均为dialogue，实际为message_complete并确有下箭头，才另报完成状态名称等价；其他kind一律原值比较。兼容条件不含片段ID、原帧号、固定文字答案或OCR分数。实际仅approved-trial的video-02:f5286页、5286–5295共10原帧触发两项兼容；原符号描述/状态名不一致各10帧仍可查。load/menu/clear及其他旧名称没有扩大等价，状态原始名称一致页78、兼容后79，符号描述一致页112、兼容后113。

**全116页与全部转换帧的实测结果**（维度是相互独立的诊断，失败可重叠）：

| 维度 | raw一致页 /116 | final一致页 /116 | final全部原帧不一致 /3361 | final转换帧不一致 /1316 | final转换机会不一致 /553 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 原严格legacyStrict | 7 | 7 | 2584 | 809 | 393 |
| 文字 | 97 | 98 | 169 | 18 | 13 |
| 字段位置/附标/渲染单元 | 114 | 114 | 27 | 8 | 8 |
| 符号（限定旧描述兼容） | 113 | 113 | 20 | 6 | 6 |
| 状态（限定旧名称兼容） | 79 | 79 | 1099 | 406 | 76 |
| 像素框数值/目标可比较 | 16 | 16 | 2071 | 453 | 355 |
| 前四维合取，仅诊断 | 60 | 61 | 1288 | 424 | 89 |

源矩形数值不同仍100页/2071原帧，此外其中3页/20原帧还有不能成对比较的符号目标，分别报告而非直接称纯边框。原像素审计15561投影、15532目标包围范围及14无检出风险/39仍有风险的53仅源框页证据沿用原SHA；没有用阈值风险判定截字或用无风险授予通过。逻辑合取从上一只去sourceRect诊断的raw59/final60变为raw60/final61，**唯一新增页是旧试标描述兼容，不是OCR文字或算法进步**；仍不是正式整页成绩、物理边界合格或及时UI输出。

**原成绩保持**：原始188/86569错，逐帧99.78283219166214%、状态等权99.72232854878935%；最终177错，逐帧99.79553881874574%、状态等权99.7287839099668%；原严格7/116、2584失败原帧、空误报0/692及715连续状态不变。所有v19 actual（含outputReadiness）、源/类/JAR、原时间/RGB/GT/旧评分报告SHA保持，因此此前门控0残句/2其他转换、20页收益和短页结果没有被改动。本轮不把此前176测试或38片实际回放说成新跑。

**没有被豁免的真实失败**：7附标差异原帧（video-02:1659/1697/1706/1724/1731、video-03:3541/1068）全部仍字段位置与逻辑合取失败；video-03:f3541完整页与f3664紧凑HP/冒号细分仍失败，另6附标帧仍在转换分母。紧凑单元差异为20原帧，与7附标帧合计27位置不一致原帧；完整状态19帧、转换8帧。非旧版符号差异仍video-02:f6940、video-03:f826、video-04:f17557三页；17个问号编码页文字仍失败。原图HP条反例video-02:6916仍文字/字段位置一致而像素框不同、有风险提示，不能被工具改判为漏字。

**验证与实际命令**：

```powershell
node tools/check-scoring-dimension-properties-v1.cjs reports/m3-grid-refinement-v1/scoring-dimensions-v1-properties.json
node tools/score-contract-dimensions-v1.cjs reports/m3-grid-refinement-v1/scoring-dimensions-v1 reports/m3-grid-refinement-v1/scoring-dimensions-v1-properties.json
node tools/check-scoring-dimensions-v1.cjs reports/m3-grid-refinement-v1/scoring-dimensions-v1 reports/m3-grid-refinement-v1/scoring-dimensions-v1-check.json
node tools/finalize-scoring-dimensions-v1.cjs reports/m3-grid-refinement-v1/scoring-dimensions-v1 reports/m3-grid-refinement-v1/scoring-dimensions-v1-final
node tools/check-scoring-dimensions-v1.cjs reports/m3-grid-refinement-v1/scoring-dimensions-v1-final reports/m3-grid-refinement-v1/scoring-dimensions-v1-final-check.json
```

32个克隆构造反例覆盖整字删除/插入、字面漏附标、字面相同而附标遗漏/挂错字、字段缺失/错role/readingOrder/起点/占位、紧凑单元合并、问号/小假名/空格、空态误报、箭头存在/位置/样式/多符号、契约/结构不符时拒绝兼容、旧结构下真实字段/附标错误不豁免。反例是工具检查，不作为录像GT或实机验收。独立检查器不导入新评分器、反例运行器或生产OCR，直接读取冻结GT及v19 actual，以另一套比较/编辑距离/图遍历连通分量重构全部6736次raw/final维度、原聚合和状态等权、715状态/116页/553转换；也独立复算32反例和上述原帧锚点。生成报告4255输入、最终版4263输入全部SHA一致，4043本地链接和全部范围/维度/无匹配筛选的DOM替身检查通过；真实浏览器呈现不作为已核验证据。

初版报告沿用了坐标审计的历史限制说明，其中“未改评分器”容易混淆新隔离比较版本与旧Java实现；因此保留初版不覆盖，使用finalize工具将原限制单列`priorAuditLimitations`、当前限制明确为新增独立报告但旧Java评分器未变。所有计分/兼容结果及frames/pages/states/transitions/index字节保持，只有说明/溯源组织变化；最终版再次通过同独立检查。这是报告资格措辞整理，不是生产候选、阈值调参或算法修复重试。本轮全部执行首次通过，没有生产修复尝试。

**交付与身份**：最终入口`reports/m3-grid-refinement-v1/scoring-dimensions-v1-final/index.html`，同目录`report.json/frames.jsonl/pages.jsonl/states.jsonl/transitions.jsonl/manifest.json`可恢复全部统计和差异；原生成目录及两次独立检查均保留。四媒体仍原66770帧/2779.397368s，评分范围134.916228s，四原件SHA见上述报告inputs；未选时段、缺失版本/会话/实际窗口维度仍待验证。

- 原生成report SHA：`5e33744622fdc49c4c2fa8b6fcb59f82af348bdaad86bb845851f1150c66aeda`；最终report SHA：`e1be69ffc2e5e6c96fa81672dcc5e7d184dce8f90d3929f3b37c4a37ffc674a8`。
- 最终manifest SHA：`d79ae64a8b03ca2afda7bbce158b5850cab87fe1aa737e84065ffa6bbde699e4`；最终独立检查SHA：`739a6f7941eaa6b06f32e6b7c5b2b55abba71195fc77b2ef21fe0fe7b54d5917`。
- 独立检查器SHA：`7afc7a8d3443a0170ac87b4a8d248e1679486232c0fc08223ea5cf8bb5347b07`；32反例报告SHA：`db8b8d13ae59e6912a22ad2da6da2b53d32441c998f370335dfec961d47930de`；协议与生成器/辅助工具SHA均绑定于report.inputs。

**总体与预算**：本轮完成的是隔离报告实现和全范围独立复核；M3/M4仍未通过，GT语义资格false，独立会话、实际UI时延、真实窗口/两小时稳定性仍待验证，中文不启动。不增加冻结GT或触发新GT审阅，不要求补录。原几何和3541稀疏标记3/3局部阻塞不重开；门控第2候选及其他历史修复次数不变。无提交/推送。

**上一轮下一步（已由下述v20生产修复完成）**：固定全部20原帧的紧凑单元失败集，核源打印依据、最小修复及目标/同类/全38片回归；当前完成状态以本节后续证据为准，不将当时尚未实现的接口冒称既有结果。

### 2026-10-17 队伍HP/冒号单元第1候选修复及全38片v20复核完成

**范围与根因**：用户要求“修复问题”，本轮先执行上一唯一计划的明确生产问题。只改`SceneFieldReader`队伍分支的HP单元拆分参数false→true；`JapaneseOcrEngine.packed`已有HP8×4逻辑像素及冒号4×4逻辑像素的结构输出，战斗HUD已经调用该分支，状态详情保持其既有组合单元表示。三固定日版来源的`party_menu.asm→DrawHP2→DrawHPBar`调用和相邻0x71/0x62图块证据均核对到既定提交与Git对象，共9个源码身份一致。原队伍source画面及完整固定失败集保留，没有按文字/帧号给生产特殊答案。

**事前约束与恢复基线**：`tools/prepare-packed-render-units-fix.cjs`在生产修改前核验4270历史输入SHA，将2份待改生产/测试源、100个编译类和旧JAR共103份原字节归档至`packed-render-units-fix-v1/baseline/`。`preparation.json`先固定候选1/3、20评分原帧/115标签、仅允许单元结构变化、文字/总逻辑及源框/角色/顺序/附标/符号/kind/门控不变的验证条件。`failure-cases.jsonl`完整列video-03:3663–3682，115处原HP:单元与冻结HP、冒号两单元差异；GT/原输出/旧评分不改。原图直接看图是输出可见的开发诊断，不称新盲读GT或人工逐帧验收。

**实现与验证**：生产源与归档源逐字比较（统一换行后）只有上述单参数变化。新增动态队伍行/等级/数值的单元边界测试，以及只有HP图块而缺当前冒号像素时不生成完整标签的反例；既有状态测试补查其组合单元仍保留。5项目标测试、178项全工程测试（110 app＋68 verification）零失败/错误/跳过；随后package通过，skipTests仅免重复全测。JAR全部68包内类逐SHA匹配当前编译类，100个生产/验证编译类中仅`SceneFieldReader.class`改变。无其他生产阈值、字库、门控或评分器变更。

先实跑完整party-list的30个保留原帧，20评分原帧/115标签修复，除处理耗时和批准的单元结构外逐对象相同；再实跑status-pages108引用、status-alt-pages135引用、battle-result55引用。状态片段包括真实队伍菜单热身/后文，因此它们另有12个非评分引用/72标签发生同一结构变化；其他输出逐项相同。首次把同类片段假定为“完全不变”的比较在status-pages:460失败，最小复现与范围修正保留`similar-comparison-scope-v1.json`；没有删除热身帧或重试生产候选。

全部38片v20实际图像回放已退出0，3577保留引用/3507去重保留原帧、3368评分引用/3361评分原帧、116页、715连续状态及553转换机会保留。共有32保留引用（28原帧）/187标签结构改变，其中20评分原帧/115标签、12热身/后文引用/72标签；两个评分阶段共230次目标标签单元比较。最终阶段未显式存correctedRegions时按既有契约复用rawRegions，不把派生评分阶段误算成重复存储。全部3577对象除耗时及这项结构外相同，源PTS/time_base/duration/RGB/评分标识逐项相同，**全部3577个outputReadiness判定相同**；因此原残句0/其他转换2、20页收益、短页及等待时间无回退。算法耗时不是实际UI时延，本轮仍无UI验收。

| 维度/结果 | v19 | v20 | 判定范围 |
| --- | ---: | ---: | --- |
| 原严格页 /116 | 7 | 7 | 保留原口径及新版本实测，仍失败 |
| raw/final文字一致页 /116 | 97 / 98 | 97 / 98 | 文字未变 |
| 字段位置/附标/单元一致页 /116 | 114 | 115 | 队伍页单元阻塞消除，3541附标页保留 |
| 字段位置不一致原帧 /3361 | 27 | 7 | 20个单元失败原帧修复，7附标帧仍失败 |
| 字段位置不一致转换原帧 /1316 | 8 | 6 | 2个转换单元问题修复，其余不变 |
| 符号/状态/像素框一致页 /116 | 113 / 79 / 16 | 113 / 79 / 16 | 含原限定旧描述兼容，未新增豁免 |
| raw/final前四维合取 /116 | 60 / 61 | 61 / 62 | **仅诊断，非整页或UI通过** |

原始188/86569错、逐帧99.78283219166214%/状态等权99.72232854878935%；最终177错、逐帧99.79553881874574%/状态等权99.7287839099668%；2584失败原帧、空误报0/692全部保持。新Java严格aggregate与v19逐字段完全相同，原报告未覆盖。四原件仍为Context列出的固定文件，SHA逐项绑定于新report.inputs，66770原帧/2779.397368s素材池、134.916228s评分范围不扩样，未选时段和缺失版本/独立会话仍待验证。

**独立复核与失败保留**：`check-packed-render-units-fix.cjs`证明全部保留输出只发生批准结构变化，使用冻结分维度v1比较并直接核字段结构。第二实现`verify-packed-render-units-delivery.cjs`不导入生产OCR、评分器或生成器，直接从旧/新actual、冻结GT、旧页面引用及新报告重建字段/附标/单元与全部聚合，独立证明6736次raw/final检查、116页/715状态/553转换及全部门控保持。最终4561输入身份、2167本地链接和116行页表检查通过。真实浏览器呈现仍不作已验证证据，打开请求可交给应用。

核对历史绑定时还发现两项验证假设问题，保留并修正，生产代码始终同一第1候选：一是授权重跑Maven会替换31个target下临时JUnit XML，不能把它们当不可变评分输入；二是最终区域可复用raw，187个存储变化不能误断言为374。失败输出`delivery-check-v2.log`、通过输出`delivery-check-v3.log`与`validation-history-v1.json`保留。**旧31个JUnit XML只保留原哈希，原字节没有事前归档，不能宣称其仍现存**；此前176测试日志/报告、生产源/类/JAR档案与全部GT/OCR成绩原件保持，本轮178项新XML已核对。4270历史路径中4235个SHA未变，4个生产路径原字节有档，31个属于新的测试执行记录。验证辅助脚本修订前的原字节另存`validation-helper-before-junit-fix.cjs`，匹配build-evidence-v20-final中的该辅助文件；生产源码与编译类在全回放前后完全一致。另有临时统计命令及精确补丁匹配失败在写结果前退出，未产生生产改动，不计算法尝试。没有隐藏生产回放失败或重复同目录覆盖。

**实际命令**（输出均用新目录；恢复不要重复覆盖）：

```powershell
node tools/prepare-packed-render-units-fix.cjs reports/m3-grid-refinement-v1/packed-render-units-fix-v1
mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' '-Dtest=SceneFieldReaderTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' test
mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' '-DskipTests' package
./tools/validate.ps1 ocr-replay-assisted data/gt/frozen/ocr-all-scenes-v2-observation-r14 party-list reports/m3-grid-refinement-v1/packed-render-units-fix-v1/target-party .cache/japanese-source-aliases-v1/catalog.json 2eb7e1fd2a393230cb426b565e52c1e110f040d9ce35cf0bbd52c979800192e3
# 同样的接口另跑status-pages、status-alt-pages、battle-result，输出similar-<id>；日志同目录。
node tools/check-packed-similar-scope.cjs reports/m3-grid-refinement-v1/packed-render-units-fix-v1
node tools/snapshot-ocr-implementation.cjs reports/m3-grid-refinement-v1/build-evidence-v20-final
./tools/check-jar-class-identity.ps1 -Jar app/target/g1-ocr-app-0.1.0-SNAPSHOT.jar -Classes app/target/classes -Output reports/m3-grid-refinement-v1/packed-render-units-fix-v1/package-classes.json
./tools/validate.ps1 ocr-replay-assisted data/gt/frozen/ocr-all-scenes-v2-observation-r14 all reports/m3-grid-refinement-v1/ocr-regression-all-v20 .cache/japanese-source-aliases-v1/catalog.json 2eb7e1fd2a393230cb426b565e52c1e110f040d9ce35cf0bbd52c979800192e3
node tools/check-packed-render-units-fix.cjs reports/m3-grid-refinement-v1/packed-render-units-fix-v1 reports/m3-grid-refinement-v1/ocr-regression-all-v20 reports/m3-grid-refinement-v1/build-evidence-v20-final
node tools/verify-packed-render-units-delivery.cjs reports/m3-grid-refinement-v1/packed-render-units-fix-v1 reports/m3-grid-refinement-v1/ocr-regression-all-v20 reports/m3-grid-refinement-v1/packed-render-units-fix-v1/independent-check-v1.json
```

日志`target-test.log/full-test.log/package.log/target-party.log/similar-*.log/all38-v20.log`保留。源身份初稿`build-evidence-v20`保留；回放绑定`build-evidence-v20-final`的250条源身份/100类，同一生产类构建，没有为了辅助脚本修订重建或多跑OCR。

**剩余问题的新证据与限制**：

- 当前r14同一问号字形的标注同时出现U+FF1F与U+003F（分别34、13个状态区域出现记录，不是原帧或页面数量），记录`question-contract-current-v1.json`；旧来源审计也确认运行时统一映射ASCII而冻结拼写并不统一。不能按片段改字、全局替换后隐藏另一侧回退、在评分器归一化或改GT来凑分。17页问号差异保留，待一致的显式表示契约。
- 直接查看原PNG后，video-02:6950画面可见下三角而冻结记隐藏；video-03:846及video-04:17560目标区域看起来为空而冻结记可见，程序分别输出可见/隐藏/隐藏。`symbol-phase-diagnostic-v1.json`保留三个原图SHA、预期/实际及观察。**此前把这3页直接表述为算法多报/漏报过早**；当前只是输出可见的诊断，尚非整事件双遍独立GT复核，不能直接据此改标注，也不能反向把程序改成与原画面不符。三页评分失败完整保留，重新归为“需复核的可见性/相位疑点”。
- 7附标差异帧、100页源像素框、状态分类及真实环境/UI缺口仍未解决；原几何和3541稀疏标记3/3局部阻塞保持，不借本次修复重开。PC1731部分关闭状态的已有独立诊断仍可推进，尚未执行生产候选。

**交付身份**：入口`reports/m3-grid-refinement-v1/packed-render-units-fix-v1/delivery/index.html`；同目录report/frames/pages/states/transitions/manifest保存全116页及所有转换。新权威开发OCR实际回放为r14/v20，v19及全部旧分数保留。

- report SHA：`8eec6fa03ceb43de42bdfe735c6e980db052aea99624c5fe89a0e8cf9519e408`；manifest SHA：`2236b3052006718becb4d45731cbe9db3dc6490e20316f2ac353ee09e3402e36`。
- 独立检查SHA：`891cc430c02f701654436fa5e0f1311920f270d25cbc2e28fafa4fcc781bf666`；独立检查器SHA：`d5b278ff8cf4363b5480541af8dc075cdf87ae8cb600f38b3095576bbe2fcc9d`。
- v20源快照SHA：`88a1e6b1db5c3d1e4e9443574bd2b8a593980e60280846dc3cee3b5936ffc62f`；类身份SHA：`93aa593074b530ff04488f938ff76abe4c603389767954a7ce9afba29c83f527`；应用JAR SHA：`201405ba95e6256b3b78f39b49864634d63d9133d5dbefd875d800c0fe6ec500`。
- 当前问号诊断SHA：`c53ad03ecbff3ba0063cdecb8e6e52e3de36728a6a5341a03c53c68c2827df74`；箭头相位诊断SHA：`dfe84fa5245c2f279b9e76b407def4cd5d73b32fc6c2c770bdff2019353b7443`。

本问题第1/3候选通过，无第2生产候选；并未修复所有列出的问题。没有新GT冻结/修订或审阅卡点，没有更改旧评分器/标准；M3/M4未通过，GT语义资格false、独立会话/实际UI/真实窗口/稳定性/未选范围仍待验证，中文不启动。当前无运行进程，无提交/推送。

**当前唯一可执行下一步**：读取已有`submenu-closing-diagnosis-v1.json`、v20的PC完整导航actual与原图1729–1733，核对1730完整子框→1731仅上部残留且父标签恢复→1732完全消失的当前像素及真实历史；在不改GT/文字的前提下，设计`BackpackTransitionTracker`对“先见完整子框、现仅上部残留”的有限关闭状态，修复其失去完整闭框即清历史的问题。先把冷启动/初次绘制、完整框保持、完全消失/reset/逆时钟/布局变化等反例与已有MissingListBoundary测试区别写清，保存新`pc-submenu-closing-root-cause-v20-v1.json`（计划附件/尚未实现），再实现第1个有证据的生产候选，目标PC、背包/商店同类及全部38片回归。原文字/附标/字段、符号、20页收益/短页和全部转换不回退；不改GT、不按固定帧/菜单答案判断、不重开耗尽路线，若证据不成立则保留局部未解决项。


### 2026-10-17 持续修复：PC关闭、一次追加网格失败、源码边界v22严格增益完成

**范围与授权**：按用户“继续处理，直到严格成绩有显著提升”持续推进；随后用户明确答复“允许追加一次，目标是提高原严格成绩”，仅追加一次连续帧联合网格试验，未无限重开旧预算。spec/PLANS按顺序读取，已读历史计划及当前恢复位置连续使用；没有新GT、审阅卡点、其他代理/模型或新视频。四原媒体SHA由最终8645身份核对再次验证；素材池66770原帧/2779.397368s，既有38片134.916228s评分范围保持；未选/缺失范围继续待验证。

**先保全再修改**：`strict-improvement-v21/preparation.json`核验4568旧输入，保存381份可变源/类/JAR/测试产物（含31 XML）；`source-page-boundary-production-v22/preparation.json`再保存385份v21产物。没有重复覆盖历史实际输出或旧分数。以前v19未事前保存的31 XML字节仍缺失，本次归档不改变该历史限制。

**PC关闭第2/3候选**：正常真实前史1730为完整子框，1731上部子框仍在、父框下部已恢复、子选择消失，1732子框全消失。第1候选因误要求底角消失而未命中；原图/pc-cells确定底角仍留，原源/类/目标输出保留。第2候选要求过去真实完整选择与当前恢复父框/上部子框/下部子侧消失，不用文字答案或帧号。180测试、6同类及全38片v21只改变1731 kind与直接派生menuReady/计时元数据，所有3577 ready/fire/invalidate/messageConfirmed相同。既有MissingListBoundary、冷启动/reset/逆时钟/换布局/全消失反例保持。初版合成夹具未擦除子框覆盖的父边，失败XML/源码/日志与修正保留，不能把它写成实机失败或成功。

**追加网格试验封口，不接生产**：`JointGridProbe.java`只接正常原PNG、当前估计及固定日版源字形，最多8个真实先前/当前输入；唯一字形暗笔画重心、固定稳健仿射拟合，无GT/片段身份/未来输入。五完整事件先测后扩到全部38片3577引用；2962评分引用可拟合。在仍保留原文字/状态的乐观“只换坐标”投影下，坐标边差33273→32683，但严格7→0、像素框16→0，全部原7页回退。`strict-improvement-v21/joint-assessment/`保存全帧/页/状态及全部新旧差异；这是被拒绝的诊断，不是新OCR实跑分数、GT错误证明或生产退化。旧三次＋此次一次均保留，本路线不再调参；3541原3/3不重开。

**独立来源问题与最终输出**：使用三固定提交的正常反汇编源；首次Git懒取因本机Schannel凭证失败，单命令OpenSSL读取/稀疏补齐后成功，提交不变。3814个ASM文件逐Git blob/SHA校验，无中文、GT或现有OCR输入参与索引构建。只支持literal text/line/cont/para/done/prompt与有明确后继控制的字符串结束；动态RAM、复杂控制/条件块及重复变量等保守弃权，不能宣称全游戏源覆盖。首版9153模板已有473/471边界命中、4目标成功；源码审计发现@仅结束PlaceString，第二版等后继真实页面控制再索引，8653模板而同样473/471结果，两个版本及源证据都保留。来源第2/3候选采用，无第三候选。

生产`JapanesePageBoundaryResolver`仅对当前message_loading、无菜单/额外字段、[1,14]/[1,16]两行dialogue做精确源匹配；PLAYER/RIVAL只接受1–5个字母/数字的有界值，不补看不见的字，不按当前片段选答案；存在更长页前缀则弃权，保留所有一致来源。它生成显式correctedVisibleState与stateCorrectionEvidence（配置SHA、源提交/路径/行号/控制），raw状态/文字、原最终文字和输出门控完全保持。原最终比较原先只有correctedRegions输入，本轮增加对称的最终状态输入选择并严校证据，**OcrScoring.java及其编译类原字节完全相同**，没有放宽字符/字段/附标/源像素/符号/状态相等条件。未借分维度诊断81当严格成绩。

**实际验证与成绩**：全187项Java测试（116＋71）及package通过，73包内类与编译类相同。6目标/同类为intro-oak、intro-creatures、intro-pets、exploration-npc、lab-rival-demand、oak-delivery；第二配置与全38片实际输出除耗时外完全相同。全38片3577保留引用、3368评分引用/3361原帧、116完整页/2045完整原帧、1316转换原帧、715连续GT状态/553非完整机会全部保持，只有473保留/471评分引用增加有源的最终状态；转换新增完成误判0。原始/原最终文字、所有坐标/附标/字段/符号完全相同，3577原门控逐条相同，因此0残句/2其他转换、20页收益、11短页及其漏页行为保持。没有借它授予真实UI≤200ms验收，原processingNanos也不含来源辅助开销。

| 原规则指标 | v20/v21 | v22实跑 | 说明 |
| --- | --- | --- | --- |
| raw严格整页/116 | 7 | 7 | 图像观察不因源码解释改写 |
| final严格整页/116 | 7 | 14 | 真正消费有证据最终状态，新增7、损失0 |
| final严格失败原帧/3361 | 2584 | 2429 | 少155个严格失败原帧 |
| raw/final编辑错误/86569 | 188 / 177 | 188 / 177 | 字符分数保持 |
| raw/final文字全对页 | 97 / 98 | 97 / 98 | 未以来源补字 |
| 字段位置/符号/像素框一致页 | 115 / 113 / 16 | 115 / 113 / 16 | 100页源框等差异仍计错 |
| final状态一致页 | 79 | 98 | 包括来源解释；raw仍79 |
| raw/final四维合取 | 61 / 62 | 61 / 81 | 仅诊断，不能替代14严格页 |
| 空误报/692 | 0 | 0 | 全空/非目标原帧保持 |

新增严格页固定为video-01:f6187、f6299、f6344、f9898、f13393、f13479、f24227；全页每个完整原帧均通过原规则，没有只取首次ready/最佳帧。原始逐帧99.78283219166214%/等权99.72232854878935%、最终逐帧99.79553881874574%/等权99.7287839099668%保持；严格最终率6.034482758620689%→12.068965517241379%，不能把此双倍增益称作达到≥95%或100%的spec门槛。

**独立检查**：第二实现`verify-source-boundary-policy-v2.cjs`不导入策略/生产代码，以独立正则语言、源blob及全部真实控制行复核3814文件/8653模板/3577决策，3856输入身份通过。`verify-source-boundary-delivery-v22.cjs`不导入OCR/评分器/生成器，证明所有原像素观察保持，再按原严格字符/几何/符号/状态合取重建6736阶段评分与116页/715状态/553转换、全部源解释与重复原帧合取；8645报告输入、3856来源输入及2169本地链接检查通过。`independent-delivery-check.json`是最终复核状态，primary report生成时保留的pending独立检查标签不覆盖后续该独立结论。实际浏览器呈现仍待验证；打开请求返回queued，不冒称已肉眼渲染。

**失败与修正**：第一轮全测试暴露旧无边界配置的单位夹具缺少新几何字段；增加无页面目录即不进入边界适配的兼容分支，原失败XML/适配源码/日志保留，随后187全测通过。交付检查初次拒绝OcrComparison$Totals.class变化；旧/新javap -c -p逐字相同，-v仅因插入方法导致调试LineNumberTable整体+8行，原/新类均已归档；仅放行这一确证变化，保留失败日志、旧检查器及反汇编证据。此为验证身份规则补全，不是评分宽免/生产修复重试。完整历史`source-page-boundary-production-v22/validation-history.json` SHA`37e255646a67d2ea5aa15f522fc6097b656991e86cd14d0fec1c3555023d3b17`。

**实际命令与附件**（均已运行，输出已有，不重复覆盖）：

```powershell
node tools/prepare-strict-improvement-v21.cjs reports/m3-grid-refinement-v1/strict-improvement-v21
node tools/diagnose-strict-improvement-v21.cjs reports/m3-grid-refinement-v1/strict-improvement-v21
# v21目标/6同类/全38片使用原辅助配置，日志及实际输出均在strict-improvement-v21及ocr-regression-all-v21。
node tools/check-pc-closing-v21.cjs reports/m3-grid-refinement-v1/strict-improvement-v21
# JointGridProbe由javac编译到隔离probe-classes，run-joint-grid-probe.ps1两分片完整测量38片。
node tools/assess-joint-grid-v21.cjs reports/m3-grid-refinement-v1/strict-improvement-v21
node tools/build-source-page-boundaries-v2.cjs .cache/japanese-source-page-boundaries-v2
node tools/compare-source-page-boundaries-v1.cjs reports/m3-grid-refinement-v1/source-page-boundary-v2 .cache/japanese-source-page-boundaries-v2/catalog.json
node tools/verify-source-boundary-policy-v2.cjs .cache/japanese-source-page-boundaries-v2/catalog.json reports/m3-grid-refinement-v1/source-page-boundary-v2/independent-policy-check.json
mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' test
mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' '-DskipTests' package
node tools/snapshot-ocr-implementation.cjs reports/m3-grid-refinement-v1/build-evidence-v22-final
./tools/validate.ps1 ocr-replay-assisted data/gt/frozen/ocr-all-scenes-v2-observation-r14 all reports/m3-grid-refinement-v1/ocr-regression-all-v22 .cache/japanese-source-page-boundaries-v2/catalog.json f3b05ece3212c5d74740ef063f7d647e769d73a5dc908bbdc7f44ab750d1d7ce
node tools/check-source-boundary-production-v22.cjs reports/m3-grid-refinement-v1/source-page-boundary-production-v22 reports/m3-grid-refinement-v1/ocr-regression-all-v22 reports/m3-grid-refinement-v1/build-evidence-v22-final
node tools/verify-source-boundary-delivery-v22.cjs reports/m3-grid-refinement-v1/source-page-boundary-production-v22 reports/m3-grid-refinement-v1/source-page-boundary-production-v22/independent-delivery-check.json
```

全部目标/测试/package/全量日志保持，Maven第一次失败和交付第一次拒绝也不删除。最终入口`source-page-boundary-production-v22/delivery/index.html`覆盖全116页/全部完整原帧链接、全评分帧、553转换及旧报告。report SHA`e6a85316bdea098d11e3ca490a323c7cab9882df27123a8e45657b140c6ed801`，manifest SHA`af0dbca548f58f0b458b58b0ca0466561642329ec81c14416dcd345914b290d5`，独立交付检查SHA`bcb1bb55f66fed130b1d7d88d3a22e901b3877194ceebbadd5d7029111c2d572`，独立检查器SHA`c18d78acd15723754c04059bc8a3ab67dd90e2d0fcc09c6aa109fce5ecffbee4`；源快照SHA`05c67bcf83f228816a68feabc2c19b0f1e161eeabd12ae108e315ed7f27598a1`，类身份SHA`bfed90271100e45d27baa9b9386be6a23410d0afe9bb289cdedddd993926dd8f`，JAR SHA`f0bff070daddd489eac8fc92934b6bf83eed8c3b8e4416222019884b1ed6165b`。当前无运行进程，无提交/推送。

**剩余与阶段边界**：100源框页、17问号页、7附标差异帧（3541已耗尽路线）、3箭头/2淡出相位疑点及未支持动态来源控制等仍保留；不改GT、不迎合疑点标签。GT语义资格仍false，独立会话/未选范围/实际窗口采集/真实UI/两小时稳定性均待验证。M3/M4未过、中文不启动。

**当前唯一可执行下一步**：依据spec§3.3及已完成坐标审计的明确位置口径，为原评分器建立并行、版本化v2补丁（计划文件`verification/src/main/java/io/github/g1ocr/verification/OcrScoringV2.java`，尚未实现）：纯sourceRect数值差单列，文字、严格role/order/字格/附标/紧凑单元、符号逻辑及状态仍全部判定，不豁免真实漏字/截字/附标挂错/字段归属；先建立这些真实反例及纯边框正例，再对现有v22全部116页/转换重计分并由第二实现复算。原v1评分器、GT和r14/v22 raw7/final14成绩永久并列保留，必须把新版本差异明确归为评分器按spec修复，不能冒称又一次OCR算法提升或产品通过；不再尝试未获追加预算的几何/3541路线。
