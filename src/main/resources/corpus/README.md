# 日中映射语料

这是 S4 的本地 CSV 数据资源，供后续 TextResolver / TranslationLayout 使用。当前 Java 程序尚未读取这些文件。

**已保存中文语义分段，运行时尚未接入。** 完整中文、当前可显示范围、需要前文和等待后文分别记录。`aligned` 表示来源已对齐，不表示每一对译文均通过语义验收；已发现的源段省略、增文和矛盾见 `reports/text-segmentation-issues.csv`。

## 文件

| 文件 | 用途 |
| - | - |
| `records.csv` | 完整脚本文本、词条、组合模板及组成片段，保留重复来源 |
| `fragments.csv` | 按日文换行、清框、滚动取得的可见行窗口；关联主记录及顺序 |
| `segments.csv` | 已保存的日中语义单元、原文范围、合并行序、来源与核对状态 |
| `variables.csv` | 日中变量的槽位、源操作数、格式及文本位置 |
| `control_rules.csv` | 显示控制符、固定展开、名字、数字及列表字段规则 |
| `sources.csv` | 来源仓库、分支、固定提交、适用版本及分发状态 |

本目录 CSV 为带 BOM 的 UTF-8、LF，支持字段内真实换行；BOM 供 Excel 直接打开时识别编码。程序读取时须消费文件开头的 BOM，例如 Python 使用 `encoding='utf-8-sig'`，不将其计入第一列列名。应使用 CSV 解析器，不按物理文件行切分记录。嵌套来源、命令和事件保存在 CSV 字段中的 JSON 值里；不依赖第三方仓库出现在运行环境中。

Excel 中也可使用“数据 → 从文本/CSV”，选择 `65001: Unicode (UTF-8)` 导入。编码处理依据见 [Microsoft：正确打开 UTF-8 CSV](https://support.microsoft.com/en-gb/excel/opening-csv-utf-8-files-correctly-in-excel)。

## 来源与建设方法

日文来自固定提交的 [pokegreen](https://github.com/Narishma-gb/pokegreen)、[pokeblue](https://github.com/Narishma-gb/pokeblue)、[pokeyellow-jp](https://github.com/Narishma-gb/pokeyellow-jp)。目标为 VC 使用的红/绿修订版 1、蓝版、皮卡丘修订版 3；红/绿共用源码文本，记录中的 `games=red;green` 明确表示两个版本。

中文按用户选定的仿日版来源，使用 [pokeredCHS 的 CHS_MOD_RGB](https://github.com/TomJinW/pokeredCHS/tree/CHS_MOD_RGB) 与 [pokeyellowCHS 的 CHS_SJP](https://github.com/TomJinW/pokeyellowCHS/tree/CHS_SJP)。构建入口 `_prepare.command` 指定的工作簿 F/G 列及 `data.xlsx` 的替换规则是中文依据；不使用过期 JSON 导出文件或自动匹配的 LGPE 列代替当前译文。

先依据上游英文 `text_far` 指针和完整英文文本建立桥接，再按源文件、脚本角色和变量结构区分同文候选。无法直接对应的重命名与版本差异，另存模型语义核对决定；不按文件或行的顺序机械配对。道具、技能、物种和训练家名称按常量标识及源词条关联。原始中文单元格保存在 `chinese_cells`，不改措辞、标点、简繁或数字字形。

建库只转换实际显示需要的结构：解码源码 Unicode 转义、展开游戏缩写、移除不显示的字符串终止符、把变量记成具名槽位。`japanese` 保留游戏文字及逻辑行/页结构；`japanese_ocr` 另将日文全角空格、全角数字和省略号转换为现有 OCR 的书写形式，不改中文，不强选四组同形假名的语义候选。

模型核对不代表人工复核。大部分全段关系的证据是源指针、英文原文和构建数据；没有把全部日中句对标为已逐句语义复核。黄版交易后“进化了”的句子，复用所选 RGB 汉化库里已经存在的原句，并明确记录跨来源关系，避免沿用黄版汉化的泛泛称赞或自行改写中文。

## 主记录与匹配边界

- `id` 以来源、文件和脚本标签为主，不随内容摘要变化。相同日文的不同来源保持独立；来源顺序不能决定运行时选择。
- `kind=dialogue` 是源文本单元；`dialogue_template` 是已核对控制流组合；`component` 必须通过 `evidence.consumed_by` 使用，不能独立翻译。
- `mapping_status=aligned` 表示已确定来源对应；`component` 表示被组合模板覆盖；`source_unreferenced` 保留源码明确标记未引用的条目，不计为目标覆盖的已确认译文。
- `chinese_source`、`chinese_repo`、`chinese_label` 和 `chinese_cells` 用于追溯；它们不是运行时能读到的隐藏地图或脚本状态。
- 运行时应联合可见多行、版本、区域和有效前文保留全部候选，另外输出唯一、歧义、缺失状态；不覆盖 `rawOcr`。

## 显示分段

`fragments.csv` 的 `ordinal` 与 `previous_ordinal` 关联所属源文本内部的行进度。`para` 清框，`cont` 滚动；并非固定“每两行分页”。这里只登记已提取的完整行窗口，没有枚举所有逐字前缀、昵称长度变化或跨源脚本的合法画面组合。

`segments.csv` 将完整源段按语义切分；日文起止行序从 1 开始、包含两端，空行不计序号。一个单元可以合并相邻多行，中文范围只截取所属主记录的原文。不会按中日行号配对、按长度均分或在运行时重新推断边界。`method` 区分模型语义决定、完整短源段合并和已核对单元的精确复用；`review_status` 明确模型核对并非人工复核。词条及尚不能独立拆分的两行短源段沿用已建立的全段对应，必须等整个单元显示完。

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

当前 22,245 个窗口中，12,999 个具有无需滚动前文的中文范围，1,914 个额外需要已确认前文，7,134 个必须等待语义单元完成，59 个因源段省略或矛盾回显日文，138 个通过组合处理，1 个明确未引用。这些是资源状态，不是游戏画面可翻译率或运行时验收结果。来源差异涉及 23 条记录，另有汉化新增而当前日文没有的内容被保留在主库但不发布。

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

分段决定保存在 `tools/text_segmentation.csv`：用来源记录 ID、日中全文 SHA-256 固定输入，`units` 为 `[日文结束行序,中文起点,中文终点,状态]` 数组，下一单元从上一单元结束后的日文行开始。文本源改变时必须重新核对，不能沿用旧下标。完全相同的日中源段复用同一决定，各版本与重复来源记录保持独立。两行短段仅在第一行与已核对单元、中文前缀都精确同文时复用边界（忽略日文排版空格和中文换行）；其余合并等待，不按字符长度推断。

静态数据检查和剩余工作见项目根目录的 `reports/text-corpus.md`、`reports/text-corpus-static.json`。尚未执行词库运行时、中文逐图、时序或实机验收。上游文本及派生资源的再分发授权未确认；本地建库不等于 S6 已具备分发条件。
