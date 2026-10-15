# EXECPLAN｜从零开发与恢复记录

> **状态：2026-10-15 对话扩充已统一提交 `gt-all-scenes-v2`，等待用户明确批准该版本/范围，后续开发暂停。** 原17项保持，新增20个对话事件/自然语段及原道馆路牌续页，共38条目、3361去重评分原帧、717状态、117页面机会；7636文件清单SHA `e51b7d9ebccae9e784f0b60a4a73a352b164e0d9807bd068afb2aad7c54b2dd3`。旧3151文件和已批准35帧不变；本轮要求不是批准。新增冻结0帧，M3/OCR/M5未启动。末尾记录完整证据、缺口和唯一下一步。

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

**工程初始版本**：基线提交 `284b0715fcd9ed1973cb4b775e9caa990c0e6ace`，新代码未提交。M0历史源文件清单 `reports/m0-20261014/source-snapshot-final.json`，SHA-256 `270d81a9a87fc2e529571d44f017de2881ac069da4536f79b1c945340bedd6e3`；初始构建身份见 `build-version-final.json`。**当前工具源版本**为 `reports/m2-all-scenes-final-check-v1/source-snapshot-v2.json`，SHA `bebfd3ed578a95364a146bea0d5f380e2705ff2ec606a968db5378246b529d36`，最新35项Java测试及统一审阅检查见最终记录。只有35帧已冻结副本位于 `data/gt/frozen/ocr-trial-v2/`；没有中文数据或被测 OCR 输出。

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

### 全场景 GT 统一收口清单（2026-10-15 流程调整后的恢复依据）

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

CLI 成功退出 0 仅表示对应工具检查完成；输入/证据不足为 3，工具错误为 4。冻结、回放、比较尚未实现，不能调用来宣称通过。

`inspect` 必须输入 step=1 的连续导出，重新校验每一原帧 RGB，按顺序展示全部帧及裁剪；像素差阈值仅导航，不自动给文字/状态答案。`gt-check` 核对原件哈希、账本身份、非空半开区间、每个评分帧恰一状态、时间、UTF-8/NFC、字格/源矩形和完整页机会；不会授予批准或冻结，也不证明图像语义。审阅入口生成器 `tools/build-gt-review.ps1` 的实际调用记录在下文，产物含完整 JSONL 和原帧，不是另一份进度账本。

### M2 以后计划 CLI（**尚未实现**）

```powershell
mvn test
python tools/validate.py clips --manifest data/meta/clips.json --check
python tools/validate.py gt-freeze --kind ocr --manifest data/meta/clips.json
python tools/validate.py replay --set development --stage ocr --realtime
python tools/validate.py compare --set acceptance --stage ocr
python tools/validate.py compare --set acceptance --stage translation
```

`python tools/validate.py` 只是 M2 以后的**预期接口示意**，将继续采用 Java 实现并更新本节，当前不可执行。产品本身仍使用 Java。缺四视频、未冻结 GT、未实现功能、无法独立评分等均不得返回“成功/通过”。离线逐帧比对**不能**替代真实 UI 显示测量。

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

**人工卡点优先**：gt-trial-v2 已批准且冻结；仅商店/PC子包请求已撤回。**现已正式提交全场景gt-all-scenes-v1，等待明确批准，暂停全部后续开发。** 仅响应审阅反馈或做该材料相关检查；获本版/范围明确批准、逐项只读SHA复检与GT完整性通过后才冻结新增范围。原一般“继续”及文档流程调整不是GT批准。实质修订保留旧版，重做受影响双遍并合批重审；新批准不得覆盖背包5359等待验证缺口。

## Artifacts and Notes

已经创建 `config/input-videos.json`、`data/meta/m1-v1/{media,frames}.jsonl`、原始/试标 clip 候选、覆盖矩阵及 `data/gt/candidates/ocr-trial-v1/` 和 `reports/gt-review/gt-trial-v2/`。`data/gt/ocr.jsonl`、中文GT、实际OCR输出/回放报告仍未建立。数据版本放新目录，不覆盖原件或旧证据；`data/frames/` 与 `data/raw/`、构建和依赖缓存均忽略版本控制。录屏原件、外部版权资源不打进发行包或公开仓库。JDK/JavaFX、Maven、Gson、JUnit、外部FFmpeg来源/许可登记 `resources/dependencies.json`，未来再分发许可和游戏资源审计仍待完成。

## Interfaces and Dependencies

内部接口按 `spec.md §2`：`Capture` → `OCR` → `VisibleState` → `Translator` → `UI`。离线验证器和 GT 应与主程序隔离，校验器可读取元数据及答案，被测管线只能收到 RGB/时间/正常设置。开发辅助可用 FFmpeg/FFprobe，具体版本与许可待 M0/M1 审计；字库/语料来源提交固定后才能引用。没有引入新模型或模拟器的隐含前置条件。

## Progress

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
- [ ] **全场景统一人工卡点：等待gt-all-scenes-v2明确批准**：新增冻结0帧，暂停开发；尚未取得本版/范围明确批准。获批后先复检7636文件清单/GT，再实现并验证批准封套/冻结步骤，继续M2回放/比较基础；冻结/回放接口尚未实现，不冒称已通过。
- [ ] **M1**：四视频逐一核验、原始 PTS/RGB 索引、覆盖矩阵与连续 clip。
- [ ] **M2**：代理双遍核读、用户审阅批准、GT 冻结、会话隔离、真实时间轴回放比较。
- [ ] **M3**：日文像素 OCR、原始/修正结果、状态模型及开发回归。
- [ ] **M4**：OCR 独立验收（若素材不能隔离或 GT 不完整，单列“待验证”）。
- [ ] **M5**：生产中日语料与两类 ≥95% 覆盖核对。
- [ ] **M6**：翻译、变量绑定、独立 JavaFX UI 与失效管理。
- [ ] **M7**：独立中文、实际 UI/采集、延迟、两种 ≥2h 稳定性及总报告。

## Surprises & Discoveries

- **2026-10-14 真实滚动**：5296–5297 的上行文字仍有基础字形但浊点/半浊点被框裁掉；第一遍错误沿用完整字符，第二遍直接原图放大发现并更正为当帧ホ/シ。不能以历史或源码完整消息掩盖这两帧。
- **2026-10-14 四媒体时间**：实际是30、24、24、19帧/秒，各自保留不同 time_base；总66,770帧全部配对。source-01 UDTA容器解析重试警告保留；未发现帧解码/时间缺口。其余三媒体元数据 encoder=Lavf58.20.100，来源/独立会话不能从文件数量、名称或编码器推断。导航跨素材内容相似，也不证明独立会话。
- **2026-10-14 审阅入口预检**：内置浏览器安全策略拒绝 file 协议，停止浏览器尝试，没有改成间接浏览器/网络方式绕过。静态JS编译、78张图片路径及95文件哈希检查通过，真实浏览器显示仍待验证；局部原图与最近邻图已由代理直接查看。

- **2026-10-14 环境**：Oracle JDK `1.8.0_192-b12`（自带 `jfxrt.jar`）、Maven `3.9.16` 可用；系统 API 为 `10.0.22000.0`，Maven 报 Windows 10，因此指定 Windows 10 实机兼容性仍待验证；CPU 环境标识 Intel Family 6 Model 140、8 逻辑处理器，GPU/CIM 被拒绝。PATH 中 Python 为不可用的商店别名；离线工具改用 Java 8。已安装通用 FFmpeg/FFprobe `8.1.2-essentials_build-www.gyan.dev`，只调用二进制及读取许可，不读取该旧目录的代码/GT/账本；原输入没有变化。
- **2026-10-14 失败与修复**：初次 Maven 下载失败（网络权限，`maven-test.log`）；下载已获环境审批，从官方中央仓库固定下载，`maven-test-network.log`。验证器首次测试失败：工具路径在类初始化时读取，模块测试工作目录不同导致找不到配置（`maven-test-with-ledger.log`）。此问题修复 **1/3**：改在 CLI 入口加载工具配置，使纯像素/时间检查无配置依赖；目标测试、同类时间/hash 检查和全工程 9 项回归通过（`maven-test-final.log`）。

- **2026-10-14**：目录枚举已确认上述 4 个 MP4 和 16 个 PNG 存在；未解码、计算媒体哈希或核读画面，会话、内容、帧数和版本待验证。不得照搬旧结果。

## Decision Log

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

**唯一下一步（等待批准）**：收到用户明确批准gt-all-scenes-v2对应范围后，先按`reports/gt-review/gt-all-scenes-v2/bundle-manifest.json`逐文件重算SHA，并对四原件、GT/clip/layout/契约及原35帧封套复检；全部与上述清单身份相符后才建立批准记录/冻结封套并继续M2基础。批准前停止后续开发，不冻结新增GT或运行被测OCR。
