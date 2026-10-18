# 宝可梦初代日文与中文资源核查

核查日期：2026-10-18。公开项目提供了构建离线翻译资源的基础；日版原文与国际版汉化的全文对应关系尚未验证。以下是来源事实和工程推断，不是已经通过验收的实现能力。

## 日版原文与字形

| 一手项目 | 项目声明的目标 |
| --- | --- |
| [Narishma-gb/pokegreen](https://github.com/Narishma-gb/pokegreen) | 日版红、绿 v1.0 / v1.1，另列 VC patch 构建目标 |
| [Narishma-gb/pokeblue](https://github.com/Narishma-gb/pokeblue) | 日版蓝，另列 VC patch 构建目标 |
| [Narishma-gb/pokeyellow-jp](https://github.com/Narishma-gb/pokeyellow-jp) | 日版皮卡丘 v1.0–v1.3，另列 VC patch 构建目标 |

这些是反汇编项目，包含汇编代码与可读的游戏文本。这里没有实际构建 ROM 或校验哈希，因此项目声明的重建目标不能替代本项目的资源验证。

红绿项目的 [真新镇脚本](https://github.com/Narishma-gb/pokegreen/blob/master/scripts/PalletTown.asm) 直接包含日文、文本标签及玩家和劲敌姓名的占位符。[字符映射](https://github.com/Narishma-gb/pokegreen/blob/master/constants/charmap.asm) 列出假名、数字、标点、框线、特殊符号和控制码，并指向字体资源；[字体定义](https://github.com/Narishma-gb/pokegreen/blob/master/gfx/font.asm) 分别引用普通字体、战斗附加字体等资产。这为游戏专用字形识别提供了候选基础，准确率仍须用真实采集画面测量。

## 国际版简体中文资源

| 一手项目 | 已核实范围 |
| --- | --- |
| [TomJinW/pokeredCHS](https://github.com/TomJinW/pokeredCHS) | 美版红蓝简体中文工程；还提供仿日版红绿蓝，作者明确说明五种版本底层均为英文版 |
| [TomJinW/pokeyellowCHS](https://github.com/TomJinW/pokeyellowCHS) | 基于英文黄版的简体中文工程，包含仿日版皮卡丘版本 |

红蓝工程的 [src 目录](https://github.com/TomJinW/pokeredCHS/tree/CHS_Mod/src) 公开文本、工作表与抽取工具。已实际读取 [indoor.xlsx](https://github.com/TomJinW/pokeredCHS/blob/CHS_Mod/src/xlsx/indoor.xlsx)：A/B/C 为英文标签、宏和文本，E/F/G 为中文标签、宏和译文，I 列标题为 `Gen1:JPN`，J–M 为自动匹配参考列。实际中文保留变量和文本指令，换行数量可以与英文不同，应按标签块处理。

直接语义抽查了 `RedsHouse1F`、`BluesHouse` 和 `BikeShop` 三个工作表中的若干片段。I 列日文与中文在这些片段中整体相关，但没有独立标签和宏，不能按单行硬配，也不能认为全部标签块都已可信对齐。J–M 自动匹配列存在明显不相关的内容，不能直接作为正式日中对应；I 列同样需要与日版基准核验。

对该工作簿仅作结构统计：88 个工作表含 678 个英文标签块，中文标签均同名；676 块中文列含汉字，439 块日文参考列含假名。这些是标签与字符统计，不是语义准确率或完整覆盖率，未含汉字或假名也不自动代表缺失。

已有 [_extractText.py](https://github.com/TomJinW/pokeredCHS/blob/CHS_Mod/src/tools/_extractText.py) 和 [_importText.py](https://github.com/TomJinW/pokeredCHS/blob/CHS_Mod/src/tools/_importText.py) 在标签、宏、文本列之间进行提取与回写。部分 `src/text` 文件仍是英文，例如 [BikeShop.asm](https://github.com/TomJinW/pokeredCHS/blob/CHS_Mod/src/text/BikeShop.asm)，只遍历 ASM 会错过工作表中的中文。

现成 [_G1RB.json](https://github.com/TomJinW/pokeredCHS/blob/CHS_Mod/src/_G1RB.json) 是 `Sentence` 条目数组，包含 `text`、`note`、`state`、`tag`。当前文件末尾多余逗号使严格解析失败；仅在内存去掉该逗号后得到 2,446 条，状态均为 `Unmarked`。其中有完整 tag 重复且文本不同，tag 本身不能作为唯一键。

[_genJSON.py](https://github.com/TomJinW/pokeredCHS/blob/CHS_Mod/src/tools/_genJSON.py) 会跳过名称包含 `dex` 或 `data` 的工作簿，且省略部分无文本内容的结束指令。现成 JSON 适合辅助核对，不足以承担完整导入。

项目说明中的仿日版不等于日版底层。中文工程可作为译文来源候选，不能据此推定与日版所有原句、分页及场景逐项相同。

## poke corpus 与国际版桥接参考

已将 [pret/pokered](https://github.com/pret/pokered) 与 [abcboy101/poke-corpus](https://github.com/abcboy101/poke-corpus) 纳入参考资源。前者提供国际版英文文本与控制符定义；后者提供已有符号对应的语料，可减少日英候选配对工作。

`poke-corpus` 的 [corpus.json](https://github.com/abcboy101/poke-corpus/blob/main/src/res/corpus.json) 将一代分为 `RedBlue` 和 `Yellow`，语言包含日文假名、英文及部分欧洲语言，没有一代简体或繁体中文。[NOTES.md](https://github.com/abcboy101/poke-corpus/blob/main/NOTES.md) 说明早期世代语料按反汇编符号对齐，缺失语言条目以 `[NULL]` 保位。

实际 [日文](https://github.com/abcboy101/poke-corpus/blob/main/corpus/RedBlue/ja-Hrkt_msg.txt)、[英文](https://github.com/abcboy101/poke-corpus/blob/main/corpus/RedBlue/en_msg.txt) 和 [qid](https://github.com/abcboy101/poke-corpus/blob/main/corpus/RedBlue/qid_msg.txt) 是对应行关联的文本文件。`RedBlue` 的 qid 包含 `^R`、`^G`、`^B`、`^RG` 等变体，证明该合并语料保留了日版红绿蓝差异；黄版有独立资源。尚未审计四作全文和全部修订版的完整覆盖。

其中部分日版条目没有英文。例如 `rb.dex_text^RG.RhydonDexEntry` 的 [日文条目](https://github.com/abcboy101/poke-corpus/blob/main/corpus/RedBlue/ja-Hrkt_msg.txt#L3394) 有内容，而 [同位置英文](https://github.com/abcboy101/poke-corpus/blob/main/corpus/RedBlue/en_msg.txt#L3394) 为 `[NULL]`。因此英文桥接只能覆盖一部分候选，仍需其他来源与建库补译。

一代语料还保留换行、滚动、分页、结束标记和运行时变量，不能当作去除全部控制信息的纯句子库直接查表。符号、版本差异与日英候选对应适合作为辅助信息；日版原文和中文汉化基准仍分别来自已选资源。

## 四作共享识别的证据

对三份字符映射作逐条比较：红绿的 242 条声明在蓝、黄中均有相同编码；蓝、黄各 244 条声明一致，多出的 `<GA>` 和 `<DEXEND>` 属于文本展开控制符。[红绿映射](https://github.com/Narishma-gb/pokegreen/blob/master/constants/charmap.asm)、[蓝映射](https://github.com/Narishma-gb/pokeblue/blob/master/constants/charmap.asm)、[黄映射](https://github.com/Narishma-gb/pokeyellow-jp/blob/main/constants/charmap.asm)

三份文本渲染代码的浊点和半浊点合成、普通对白行距与基本坐标规则一致；蓝、黄增加少量控制符处理。[红绿渲染](https://github.com/Narishma-gb/pokegreen/blob/master/home/text.asm)、[蓝渲染](https://github.com/Narishma-gb/pokeblue/blob/master/home/text.asm)、[黄渲染](https://github.com/Narishma-gb/pokeyellow-jp/blob/main/home/text.asm) 这些证据支持共用识别核心和资源配置机制；字体 PNG 是否逐字形完全相同尚未校验。

真新镇招牌和部分 NPC 文本在四作中相同，具有公共译文复用的直接依据；大木阻止出城的事件则在皮卡丘版拆成不同的对白阶段。[红绿真新镇](https://github.com/Narishma-gb/pokegreen/blob/master/scripts/PalletTown.asm)、[蓝真新镇](https://github.com/Narishma-gb/pokeblue/blob/master/scripts/PalletTown.asm)、[黄真新镇](https://github.com/Narishma-gb/pokeyellow-jp/blob/main/scripts/PalletTown.asm) 因此共享内容可以合并，分页和事件差异仍需在公共库中表达；模板、来源及可见变体分层的设计见 [数据契约](data-model.md)。

## 对离线设计的影响

1. **文本脚本与可见页面不同。** [文本宏](https://github.com/Narishma-gb/pokegreen/blob/master/macros/scripts/text.asm) 包含换行、分页、滚动、运行时文本插入、数字插入和执行代码。一个脚本标签可能对应多个可见阶段；只提取引号内字符串不足以还原当前画面。
2. **同一像素字形可能对应不同字符。** 日版字符映射注明部分平假名与片假名共用字形，例如 `へ/ヘ` 和 `り/リ`。因此纯字形识别可能需要词表或文本候选帮助消歧，不能假定每个图块都唯一对应一个 Unicode 字符。
3. **名字与数值会变化。** 字符映射包含玩家、劲敌和战斗参与者占位符；[战斗文本](https://github.com/Narishma-gb/pokegreen/blob/master/engine/battle/common_text.asm) 使用运行时插入。仅对完整句子做精确查表不足以覆盖这些内容。
4. **跨版本标签不能直接当成映射。** 日版真新镇正文位于脚本中，国际版正文位于 [text/PalletTown.asm](https://github.com/pret/pokered/blob/master/text/PalletTown.asm)，标签与分页组织存在差异。场景结构和标签可用于生成候选配对，仍需核对句意与变量。

工程推断：可以自动读取中文工作表的标签块，以日版原文和 poke-corpus 对应为主，工作表 I 列日文作为候选参考，再核对宏、变量、场景与版本信息。无英文桥接内容单独建立对应或补译；大模型集中处理差异与异常队列，减少逐句配对。运行时采用已确认的“画面识别 → 日文候选或模板匹配 → 本地中文输出”方向。完整自动导入与端到端识别仍未验证，异常数量和最终覆盖率尚不能承诺。

## 采集样本与严格匹配的条件

本地 16 张 PNG 均为 645 × 580，但每张包含 1,214–2,277 种 RGB 颜色。[对白样本](../testpic/pic/dialogue_01_f02190.png) 的下方文字区域（x=24…619，y=420…559）含 830 种 RGB，文字边缘有多级中间色。对 4 × 4 网格相位的只读统计也未发现纯 4 倍最近邻复制应有的恒色块结构。

这说明当前文件不能直接当作原始字库位图的 RGB 整数倍复制。具体中间色来自缩放、滤镜还是压缩，单凭这些 PNG 尚不能确定；也尚未验证确定性采样和二值化能否恢复全部原始字形。严格识别方向保持不变，首先验证恢复后的字形是否能稳定精确命中。

多个假名共用位图是确定的字形等价关系，不等于视觉模糊。使用字形标识与库内相同字形序列建立对应，位图和别名的表示见 [数据契约](data-model.md)。字体 PNG 的逐字形一致性和当前样本未出现字符仍需核查。

## 下一步需要验证的事实

- 扩大中文工作表审计，核查剩余工作簿和黄版工程，测量自动标签对应、日文参考核验及变量检查后的异常数量。
- 六类目标场景中，能建立多少有证据的日中对应关系，以及多少需要补译或拆分。
- 多页与滚动文本如何限制中文输出，使其只覆盖当前可见内容，不提前显示后续语义。
- 静态提取不能直接展开的文本脚本、重复可见片段和动态名字如何表示。
- 自动定位和网格归一后，日版字形是否能在现有缩放、配色及中间色情况下稳定严格还原；浊点和附加字体需分别验证。

实际实施前需固定所用资源的提交版本并保存来源信息。资源核查仅在内存读取、解析和统计公开文本与工作表，没有执行作者脚本、克隆资源仓库、下载 ROM、编写产品提取器或实现识别流程。
