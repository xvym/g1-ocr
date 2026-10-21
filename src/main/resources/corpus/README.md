# 日中映射语料

这是 S4 的本地 CSV 数据资源。0.3.0 的 `TextCorpus/TextResolver` 读取主记录、行窗口和槽位规则；JavaFX 在确定可发布片段后独立换行。

**中文语义分段已接入运行时，最终 A6 未验收。** 完整中文、当前可显示范围、需要前文和等待后文分别记录。`aligned` 表示来源已对齐，不表示每一对译文均通过语义验收。原 9 类差异的决定见 `tools/text_translation_revisions.csv`；建库时的语义问题快照为零。全图测试发现的固定词、数量、字段类型与战后前缀缺口已按来源修复；g1-106 的“研究化石/宝可梦”差异在保存的语义单元中标为 `source_semantic_mismatch`，所有相关窗口均阻止发布，不改原汉化。具体措辞订正仍待逐项确认，见 `reports/chinese-images-issues.csv`。

## 文件

| 文件 | 用途 |
| - | - |
| `records.csv` | 完整脚本文本、词条、组合模板及组成片段，保留重复来源 |
| `fragments.csv` | 按日文换行、清框、滚动取得的可见行窗口；关联主记录及顺序 |
| `segments.csv` | 已保存的日中语义单元、原文范围、合并行序、来源与核对状态 |
| `variables.csv` | 日中变量的槽位、源操作数、格式及文本位置 |
| `control_rules.csv` | 显示控制符、固定展开、名字、数字及列表字段规则 |
| `sources.csv` | 来源仓库、分支、固定提交、适用版本及分发状态 |
| `slot_rules.csv` | 按已核对脚本角色补充变量类型，用于区别招式、道具、昵称、物种、能力及盒号；不含译文 |

本目录 CSV 为带 BOM 的 UTF-8、LF，支持字段内真实换行；BOM 供 Excel 直接打开时识别编码。程序读取时须消费文件开头的 BOM，例如 Python 使用 `encoding='utf-8-sig'`，不将其计入第一列列名。应使用 CSV 解析器，不按物理文件行切分记录。嵌套来源、命令和事件保存在 CSV 字段中的 JSON 值里；不依赖第三方仓库出现在运行环境中。

Excel 中也可使用“数据 → 从文本/CSV”，选择 `65001: Unicode (UTF-8)` 导入。编码处理依据见 [Microsoft：正确打开 UTF-8 CSV](https://support.microsoft.com/en-gb/excel/opening-csv-utf-8-files-correctly-in-excel)。

## 来源与建设方法

日文来自固定提交的 [pokegreen](https://github.com/Narishma-gb/pokegreen)、[pokeblue](https://github.com/Narishma-gb/pokeblue)、[pokeyellow-jp](https://github.com/Narishma-gb/pokeyellow-jp)。目标为 VC 使用的红/绿修订版 1、蓝版、皮卡丘修订版 3；红/绿共用源码文本，记录中的 `games=red;green` 明确表示两个版本。

中文按用户选定的仿日版来源，使用 [pokeredCHS 的 CHS_MOD_RGB](https://github.com/TomJinW/pokeredCHS/tree/CHS_MOD_RGB) 与 [pokeyellowCHS 的 CHS_SJP](https://github.com/TomJinW/pokeyellowCHS/tree/CHS_SJP)。构建入口 `_prepare.command` 指定的工作簿 F/G 列及 `data.xlsx` 的替换规则是中文依据；不使用过期 JSON 导出文件或自动匹配的 LGPE 列代替当前译文。

先依据上游英文 `text_far` 指针和完整英文文本建立桥接，再按源文件、脚本角色和变量结构区分同文候选。无法直接对应的重命名与版本差异，另存模型语义核对决定；不按文件或行的顺序机械配对。道具、技能、物种和训练家名称按常量标识及源词条关联。原始中文单元格保存在 `chinese_cells`，不改措辞、标点、简繁或数字字形。

用户确认的修订独立保存在 `tools/text_translation_revisions.csv`，以适用日文和原汉化全文 SHA-256 固定输入。当前 23 条来源记录包含补译、订正及接受的汉化增文或礼貌语省略。其 `chinese` 保存应用修订后的完整正文，汉化增文仍保留在正文中但不进入显示分段；`chinese_revision` 保存原汉化正文、`revised_ranges`、`excluded_ranges`、决定文件、方法及 `approval_status=user_approved`。两个范围字段均为 Unicode 码点的左闭右开区间：前者标识补译/订正位置，后者标识保留但不发布的增文。原 `chinese_source/chinese_cells/chinese_events` 仍指向原汉化，不把模型修订标为上游译文。其余记录该字段为空。

建库只转换实际显示需要的结构：解码源码 Unicode 转义、展开游戏缩写、移除不显示的字符串终止符、把变量记成具名槽位。`japanese` 保留游戏文字及逻辑行/页结构；`japanese_ocr` 另将日文全角空格、全角数字和省略号转换为现有 OCR 的书写形式，不改中文，不强选四组同形假名的语义候选。

模型核对不代表人工复核。大部分全段关系的证据是源指针、英文原文和构建数据；没有把全部日中句对标为已逐句语义复核。黄版交易后“进化了”的句子，复用所选 RGB 汉化库里已经存在的原句，并明确记录跨来源关系，避免沿用黄版汉化的泛泛称赞或自行改写中文。

## 主记录与匹配边界

- `id` 以来源、文件和脚本标签为主，不随内容摘要变化。相同日文的不同来源保持独立；来源顺序不能决定运行时选择。
- `kind=dialogue` 是源文本单元；`dialogue_template` 是已核对控制流组合；`component` 必须通过 `evidence.consumed_by` 使用，不能独立翻译。
- `mapping_status=aligned` 表示已确定来源对应；`component` 表示被组合模板覆盖；`source_unreferenced` 保留源码明确标记未引用的条目，不计为目标覆盖的已确认译文。
- `chinese_source`、`chinese_repo`、`chinese_label` 和 `chinese_cells` 用于追溯；它们不是运行时能读到的隐藏地图或脚本状态。
- 运行时联合全部来源的可见多行、区域和有效前文保留候选，不要求选择游戏版本。分别输出 `unique`（单候选）、`equivalent`（多候选当前结果一致）、`ambiguous` 和 `missing`；不覆盖 `rawOcr`。
- 每个候选独立检查保存的中文范围、当前变量及类型、前文要求和后文等待条件。全部候选的当前中文、变量和显示条件一致才可发布；存在实际差异则回显日文，不能忽略尚未就绪的候选。多来源等价保留全部记录与出处，各来源的连续前文分别跟踪，不要求后续全文相同。资源的 `games` 仅作溯源和同源词条/组合关系，不作为用户输入或过滤条件。

## 显示分段

`fragments.csv` 的 `ordinal` 与 `previous_ordinal` 关联所属源文本内部的行进度。`para` 清框，`cont` 滚动；并非固定“每两行分页”。这里只登记已提取的完整行窗口，没有枚举所有逐字前缀、昵称长度变化或跨源脚本的合法画面组合。

`segments.csv` 将完整源段按语义切分；日文起止行序从 1 开始、包含两端，空行不计序号。一个单元可以合并相邻多行，中文范围只截取所属主记录的 `chinese`。不会按中日行号配对、按长度均分或在运行时重新推断边界。`method` 区分模型语义决定、完整短源段合并和已核对单元的精确复用；`review_status` 明确模型核对并非人工复核。用户确认的模型补译与订正分别以 `method/review_status=user_approved_model_translation`、`user_approved_model_correction` 标识，共 12、11 个单元，仍须等待对应日文单元完成并满足前文条件。词条及尚不能独立拆分的两行短源段沿用已建立的全段对应，必须等整个单元显示完。

仅宝可梦中心的“久等了”使用单元状态 `accepted_omission` 和方法 `user_approved_localization_omission`，共 3 条来源记录。该单元不产生中文，首行等待后续恢复状态单元完成；后两行已完整可见时可用原译，不要求观察此前的礼貌语。汉化增文由修订元数据的 `excluded_ranges` 记录，不分配日文单元，也不生成窗口译文。

| `status` | 用法 |
| - | - |
| `whole_visible_unit` | 当前窗口包含该单元的全部日文，可取已保存的中文范围；仍须检查匹配和变量状态 |
| `semantic_visible` | 当前可见行中已有完整语义单元，只能显示保存的范围；同窗后续单元未完成时不得补出 |
| `semantic_context_required` | 单元起点已滚出当前窗口，必须确认同一源段从 `context_start_ordinal` 到当前的有效连续前文；首次中途进入时回显日文 |
| `wait_for_semantic_unit` | 当前只有未完成的语义单元，回显日文，等待 `waiting_for_ordinal`；这不是可显示中文的覆盖 |
| `source_translation_gap` | 这段日文缺少对应的所选汉化原文，回显日文 |
| `source_semantic_mismatch` | 已发现日中含义或数值矛盾，回显日文，不猜测或改写 |
| `use_composition` | 原始组成片段；通过完整组合模板处理 |
| `source_unreferenced` | 源码明确未引用的记录，不纳入运行时翻译候选 |

`chinese_start/chinese_end` 是关联主记录 `chinese` 的 Unicode 码点范围，左闭右开；不是字节位置或 Java UTF-16 下标。中文布局换行不应改变可显示的内容范围。

`semantic_segment_ordinals` 是当前译文关联的语义单元序号。`waiting_for_ordinal` 也可与已有中文范围同时出现：已经完成的部分可显示，未完成的部分继续等待。`context_start_ordinal` 仅许可使用已确认前文来匹配语义单元，不允许沿用此前画面的姓名或数值。

当前 10,187 条主记录、23,939 个窗口、15,700 个语义单元。其中 14,441 个窗口有无需滚动前文的中文范围，1,962 个需要已确认前文，7,388 个等待完整语义单元，6 个阻止语义错译，141 个通过组合处理，1 个源码明确未引用。这是资源结构计数，不是游戏画面可翻译率或语义正确率。接受的汉化增文保留在主库但不发布，礼貌语省略也不计为中文覆盖。

## 变量

`{{PLAYER}}`、`{{RIVAL}}`、`{{USER}}`、`{{TARGET}}` 是名字槽位。`{{RAMn}}`、`{{NUMBERn}}` 只表示从 OCR 绑定的可见文本/数字，源 RAM 地址用于离线追溯，不是内存读取接口。同一源地址在同一段中重复出现时复用槽位。

`variable_status=typed_unique` 表示唯一类型槽位对应，`model_semantic` 表示已保存语义绑定。`review/unresolved` 出现在不能独立使用的组成片段或未引用条目时，不应绕过组合关系。`requires_observed_PLAYER` 表示原中文增加了日文当前片段没有的玩家名；只能使用当前画面 OCR 确认的姓名，不采用此前画面的姓名、默认名或隐藏状态。缺少中文所需的玩家名不改变日文源文本的匹配状态；可以匹配成功，但中文变量不满足时仍回显日文，不删除或改写中文中的姓名槽位。

“送入盒子”已单独交换中文两个槽位的绑定：日文先盒号后昵称，中文先昵称后盒号；只改槽位关联，不改中文。徽章槽位还需按字段语义绑定：日文可能只有颜色词干，中文原词条包含“徽章”。`USER/TARGET` 的敌方前缀与昵称必须分开处理；未知昵称不因与物种名同文而自动翻译。

## 重建与检查

固定清单是项目根目录的 `tools/text_sources.csv`。按清单将各仓库放在被忽略的 `output/corpus-sources/<id>/` 并检出相应提交，然后使用 Python 3 与 openpyxl 执行：

```text
python tools/build_text_corpus.py
```

构建器检查提交、读取源码及工作簿、生成 CSV 和 `reports/text-corpus.json`，不编译 ROM、不调用模型服务、不读取截图答案、不执行应用或测试。模型对齐与变量决定分别保存在 `tools/text_alignment.csv`、`tools/text_variable_bindings.csv`。

`tools/text_translation_revisions.csv` 只应用用户确认的具体修订，原汉化或适用日文哈希变化时停止重建；该文件不是运行时翻译入口。`chinese` 为修订后的完整正文，`revised_ranges/excluded_ranges` 为 JSON 区间数组，`method` 为 `model_translation`、`model_correction`、`localization_addition` 或 `localization_omission`，`evidence` 说明对应决定；原汉化仍从固定上游重新提取。

分段决定保存在 `tools/text_segmentation.csv`：用来源记录 ID、日中全文 SHA-256 固定输入，`units` 为 `[日文结束行序,中文起点,中文终点,状态]` 数组，下一单元从上一单元结束后的日文行开始。文本源改变时必须重新核对，不能沿用旧下标。完全相同的日中源段复用同一决定，各版本与重复来源记录保持独立。两行短段仅在第一行与已核对单元、中文前缀都精确同文时复用边界（忽略日文排版空格和中文换行）；其余合并等待，不按字符长度推断。

`slot_rules.csv` 单独维护，不由建库器覆盖；26 条规则引用相同固定日版源码，按完整标签应用，不按模糊名称推断。`AIBattleUseItemText.RAM3` 已追溯到 `GetItemName`。交换物种缓冲区、固定道馆城市/馆主与昵称分别按实际生产者判定，不再把所有 `MonName` 当昵称。8 座道馆的城市和馆主词条从本地 `.CityName/.LeaderName` 与原汉化 `Townnames` 表对应；固定退出词来自 `StartMenuExitText`。

姓名长度依据 `constants/text_constants.asm` 的 `NAME_LENGTH=6`（含终止符）。已知名称、数字与词条槽位按类型匹配；尚无语义类型的 RAM 槽位只在固定正文锚点充分时按字面绑定，不据裸值识别来源。PC 昵称行保留可见等级，独立取消行使用固定词；数量允许原始 `×` 后的排版空格，保留原 OCR 和数量后缀。敌方前缀及 `#` 展开仍取自同源既有汉化，先截取保存范围再展开，不改变原始来源或码点下标。`UsedMove` 组合按保存的 `move_symbols/default_set` 筛选源语法模板。

`TrainerEnd.*` 的 1,043 条组合由 `trainer` 宏和 `SaveEndBattleTextPointers` 的实际目标生成，支持全局及局部标签；不是给任意对白添加说话者。前缀来自 `TrainerEndBattleText`，中文为原汉化的姓名与冒号，`SPEAKER` 独立绑定当前可见姓名。组合保留正文所有语义边界、等待及前文条件；固定前缀不允许提前显示正文，滚出画面的姓名不从历史补用。原始正文记录和两个来源均保留，生成器在已确认修订之后构造组合，目标缺失时停止建库。

运行时开发验证见 `reports/chinese-runtime.md/json`，开发预期位于 `src/test/resources/chinese-cases.json`，11 张受影响图片的 23 个单元另有 `chinese-regressions.json` 独立固定预期。当前建库计数见 `reports/text-corpus.json`；`reports/text-corpus-static.json` 保留早期建库快照，不代表当前资源哈希。全图执行与来源修复证据见 `reports/chinese-images.md`；最终中文 A6、真实时序、实机及当前界面交互仍未验收。上游文本及派生资源的再分发授权未确认；本地建库不等于 S6 已具备分发条件。
