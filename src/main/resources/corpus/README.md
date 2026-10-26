# 日中映射语料

这是 S4 的本地 CSV 数据资源。0.3.0 的 `TextCorpus/TextResolver` 读取主记录、行窗口和槽位规则；JavaFX 在确定可发布片段后独立换行。

**中文语义分段已接入运行时，最终 A6 未验收。** 当前窗口相交的已保存语义单元可以显示完整中文，不要求日中逐行同步；当前变量、字符匹配和候选一致性仍须满足。`aligned` 表示来源已对齐，不表示每一对译文均通过语义验收。原 9 类差异的决定见 `tools/text_translation_revisions.csv`。g1-106 的“研究化石/宝可梦”差异在保存的语义单元中标为 `source_semantic_mismatch`，所有相关窗口均阻止发布，不改原汉化。具体措辞订正仍待逐项确认，见 `reports/chinese-images-issues.csv`。

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

用户确认的修订独立保存在 `tools/text_translation_revisions.csv`，以适用日文和原汉化全文 SHA-256 固定输入。当前156条原始来源决定包括此前23条补译、订正及接受的增文/省译，以及同文译文统一的133条修订；后者覆盖用户确认的T001～T041，T042暂缓，55条训练家战后组合同步继承修订。其 `chinese` 保存应用修订后的完整正文，汉化增文仍保留在正文中但不进入显示分段；`chinese_revision` 保存原汉化正文、`revised_ranges`、`excluded_ranges`、决定文件、方法及 `approval_status=user_approved`。两个范围字段均为 Unicode 码点的左闭右开区间：前者标识补译/订正位置，后者标识保留但不发布的增文。原 `chinese_source/chinese_cells/chinese_events` 仍指向原汉化，不把模型修订标为上游译文。其余记录该字段为空。

建库只转换实际显示需要的结构：解码源码 Unicode 转义、展开游戏缩写、移除不显示的字符串终止符、把变量记成具名槽位。`japanese` 保留游戏文字及逻辑行/页结构；`japanese_ocr` 另将日文全角空格、全角数字和省略号转换为现有 OCR 的书写形式，不改中文，不强选四组同形假名的语义候选。

模型核对不代表人工复核。大部分全段关系的证据是源指针、英文原文和构建数据；没有把全部日中句对标为已逐句语义复核。黄版交易后“进化了”的句子，复用所选 RGB 汉化库里已经存在的原句，并明确记录跨来源关系，避免沿用黄版汉化的泛泛称赞或自行改写中文。

## 主记录与匹配边界

- `id` 以来源、文件和脚本标签为主，不随内容摘要变化。相同日文的不同来源保持独立；来源顺序不能决定运行时选择。
- `kind=dialogue` 是源文本单元；`dialogue_template` 是已核对控制流组合；`component` 必须通过 `evidence.consumed_by` 使用，不能独立翻译。
- `mapping_status=aligned` 表示已确定来源对应；`component` 表示被组合模板覆盖；`source_unreferenced` 保留源码明确标记未引用的条目，不计为目标覆盖的已确认译文。
- `chinese_source`、`chinese_repo`、`chinese_label` 和 `chinese_cells` 用于追溯；它们不是运行时能读到的隐藏地图或脚本状态。
- 运行时联合全部来源的可见多行、区域和有效前文保留候选，不要求选择游戏版本。分别输出 `unique`（单候选）、`equivalent`（多候选当前结果一致）、`ambiguous` 和 `missing`；不覆盖 `rawOcr`。
- 每个候选独立检查保存的中文范围、当前变量及类型。全部候选的当前中文、变量和显示条件一致才可发布；存在实际差异则回显日文，不能忽略尚未就绪的候选。多来源等价保留全部记录与出处，连续前文用于缩小候选，不要求观察到整个语义单元。资源的 `games` 仅作溯源和同源词条/组合关系，不作为用户输入或过滤条件。

## 显示分段

`fragments.csv` 的 `ordinal` 与 `previous_ordinal` 关联所属源文本内部的行进度。`para` 清框，`cont` 滚动；并非固定“每两行分页”。这里只登记已提取的完整行窗口，没有枚举所有逐字前缀、昵称长度变化或跨源脚本的合法画面组合。

`segments.csv` 将完整源段按语义切分；日文起止行序从 1 开始、包含两端，空行不计序号。一个单元可以合并相邻多行，中文范围只截取所属主记录的 `chinese`。不会按中日行号配对、按长度均分或在运行时重新推断边界。`method` 区分模型语义决定、完整短源段合并和已核对单元的精确复用；`review_status` 明确模型核对并非人工复核。用户确认的模型补译与订正分别以 `method/review_status=user_approved_model_translation`、`user_approved_model_correction` 标识，共12、11个单元；同文统一采用 `user_approved_translation_unification`，含派生组合共188个单元。当前完整匹配的行窗口与单元相交时，就可取该单元的完整中文，不等最后一行或要求此前已观察到起点。其他尚未进入窗口的独立单元不发布，中文所需变量取自当前画面；PLAYER 可按已确认玩家名的会话规则复用。

仅宝可梦中心的“久等了”使用单元状态 `accepted_omission` 和方法 `user_approved_localization_omission`，共 3 条来源记录。该单元不产生中文，只有该行时保留日文；恢复状态单元进入窗口后可用原译。汉化增文由修订元数据的 `excluded_ranges` 记录，不分配日文单元，也不生成窗口译文。

| `status` | 用法 |
| - | - |
| `whole_visible_unit` | 短源段作为一个语义单元，与当前窗口相交即可取完整中文；仍须检查匹配和变量状态 |
| `semantic_visible` | 当前窗口相交单元的保存范围，可包含同一单元尚未展开或已滚出的内容 |
| `wait_for_semantic_unit` | 当前只有获准省译的礼貌语单元，没有可发布中文；保留日文 |
| `source_translation_gap` | 这段日文缺少对应的所选汉化原文，回显日文 |
| `source_semantic_mismatch` | 已发现日中含义或数值矛盾，回显日文，不猜测或改写 |
| `use_composition` | 原始组成片段；通过完整组合模板处理 |
| `source_unreferenced` | 源码明确未引用的记录，不纳入运行时翻译候选 |

`chinese_start/chinese_end` 是关联主记录 `chinese` 的 Unicode 码点范围，左闭右开；不是字节位置或 Java UTF-16 下标。中文布局换行不应改变可显示的内容范围。

`display_chinese/display_source` 保存经离线核对的共同可见内容及出处。既有74组重叠情形的693个来源窗口具备共同内容决定；另有25种离屏名字窗口模式的65条来源具备条件正文。未配置的窗口显示列为空，继续使用上述完整语义范围。原有中文切片、状态和语义索引保留，完整动作或限定内容进入窗口后使用各自完整译文。运行时仍要求来源完整匹配、已发布变量/类型一致和正常就绪条件，不根据字符公共前缀推断译文。候选诊断保留全部当前变量，只有实际发布内容使用的变量参与共同片段一致性比较。

离线决定在 `tools/text_fragment_displays.csv`，以完整日中源哈希、窗口序号及可见日文哈希固定范围；已有中文切片另记录码点坐标。原共同片段仅“欢迎光临！”和“哪只宝可梦……”使用已确认的新表达；离屏名字正文采用原汉化切片或去除独立称呼后拼接，N013另按当前日文的第二人称将玩家称呼适配为“你”，保留不借地图的动作及人物关系并标明模型来源和本次用户授权；新提出的18种共同措辞在 `reports/translation-overlap-proposals.csv` 待确认，尚未应用。构建器把决定写入运行时 `fragments.csv`，不读取测试图或答案，不在运行时重新分段或翻译。

`display_policy=common` 始终使用已核对的共同内容；`missing_rolled_name` 仅在原译文缺少当前名字、也不能按既有规则复用已确认PLAYER时使用独立正文。有可用姓名时优先原完整译文。该策略不复用USER、TARGET、RIVAL或宝可梦昵称，也不作用于当前语义单元中尚未显示的名字。离线数据的 `source_chinese_ranges` 可记录去除称呼后拼接的多个原文码点范围，构建器逐字核对；旧的单范围列继续支持。

`semantic_segment_ordinals` 是当前译文关联的语义单元序号。保留 CSV 列结构兼容性，当前生成器的 `waiting_for_ordinal/context_start_ordinal` 为空，不再以日文行进度限制已保存语义单元。玩家名可按下述会话规则复用，其他姓名和数值不沿用前文。

当前 10,205 条主记录、23,957 个窗口、15,718 个语义单元。当前状态计数见 `reports/text-corpus.json`；这些是资源结构计数，不是游戏画面可翻译率或语义正确率。接受的汉化增文保留在主库但不发布，礼貌语省略也不计为中文覆盖。

## 变量

姓名及昵称的完整绑定和逐字前缀共同检查日文命名字符，字符集固定为三份日文源码 `data/text/kana.asm` 和 `data/text/dakutens.asm` 的150个可输入字符（空格规范化为半角，浊音符用于变换假名，不单独输入）。任意自定义名字不能包含对白的“！”“？”或控制符；原始 OCR 不改写。默认物种名中的小假名、♂/♀和固定训练家名等例外，仅接受相应 `pokemon` / `trainer` 词条的完整值或前缀，不将这些字符放开为任意名字字符。原有五字长度及固定训练家名限制保留，合法昵称造成的真实前缀歧义仍须保留。

`{{PLAYER}}`、`{{RIVAL}}`、`{{USER}}`、`{{TARGET}}` 是名字槽位。`{{RAMn}}`、`{{NUMBERn}}` 只表示从 OCR 绑定的可见文本/数字，源 RAM 地址用于离线追溯，不是内存读取接口。同一源地址在同一段中重复出现时复用槽位。

`variable_status=typed_unique` 表示唯一类型槽位对应，`model_semantic` 表示已保存语义绑定。`review/unresolved` 出现在不能独立使用的组成片段或未引用条目时，不应绕过组合关系。`requires_observed_PLAYER` 表示原中文增加了日文当前片段没有的玩家名；可取当前 OCR 的 `PLAYER`，或同一识别会话中已确认的玩家名。只有全部有效候选完整匹配且将相同可见值绑定为 `PLAYER` 才保存；不从默认名、对手名或隐藏状态推断。普通翻页和框消失保留，识别会话/捕获/映射重置清除。候选 `variables` 保留当前可见值，`reusedPlayerName` 另记前文补入的姓名。缺少中文所需变量且没有经核对的独立正文决定时仍回显日文，不临时删除或改写中文姓名槽位。

“送入盒子”已单独交换中文两个槽位的绑定：日文先盒号后昵称，中文先昵称后盒号；只改槽位关联，不改中文。徽章槽位还需按字段语义绑定：日文可能只有颜色词干，中文原词条包含“徽章”。`USER/TARGET` 的敌方前缀与昵称必须分开处理；姓名与昵称的完整值查询全部独立词条，唯一中文或全部候选同译时替换，未命中或多译冲突时保留该变量原文；与物种、道具、招式同文也采用此规则。复用玩家名同样查库，原始 OCR 与变量绑定不变。

`WildMonAppearedText/HookedMonAttackedText/WildRanText/ItemUseBallText05/ItemUseBallText06/SafariZoneEatingText/SafariZoneAngryText.RAM1` 按脚本角色声明为 `pokemon`：固定日版的非联机 `LoadEnemyMonData` 用 `GetMonName` 将物种名写入 `wEnemyMonNick`。捕获成功和图鉴登记发生在加入队伍/取昵称之前，狩猎区提示同样使用野生物种。该缓冲区在其他流程可能存昵称，不能全局改为物种类型。联机逃跑、电脑取出、捕获后电脑传送及战斗 USER/TARGET 仍按昵称角色绑定，再执行姓名查库；幽灵出现的 `EnemyAppearedText` 也不套用野生物种规则。

预设玩家/对手名新增 18 条来源记录，覆盖 11 个不同日文名：按 `constants/player_constants.asm` 的角色/槽位与汉化 `data/player_names.asm` 菜单对应，从 `data.xlsx#Main` 提取中文。全部版本的独立词条联合查询，不因对白来源排除其他版本预设名；同文多译不任取第一条。例如 `ジャック` 同时命中预设名“杰克”和未使用训练家别名“杂耍”，`ゴースト` 同时命中“鬼斯通”和属性“幽灵”，均保留日文。

## 重建与检查

固定清单是项目根目录的 `tools/text_sources.csv`。按清单将各仓库放在被忽略的 `output/corpus-sources/<id>/` 并检出相应提交，然后使用 Python 3 与 openpyxl 执行：

```text
python tools/build_text_corpus.py
```

构建器检查提交、读取源码及工作簿、生成 CSV 和 `reports/text-corpus.json`，不编译 ROM、不调用模型服务、不读取截图答案、不执行应用或测试。模型对齐与变量决定分别保存在 `tools/text_alignment.csv`、`tools/text_variable_bindings.csv`。

`tools/text_translation_revisions.csv` 只应用用户确认的具体修订，原汉化或适用日文哈希变化时停止重建；该文件不是运行时翻译入口。`chinese` 为修订后的完整正文，`revised_ranges/excluded_ranges` 为 JSON 区间数组，`method` 为 `model_translation`、`model_correction`、`localization_addition`、`localization_omission` 或 `translation_unification`，`evidence` 说明对应决定；原汉化仍从固定上游重新提取。同文统一仅改变批准的中文语义范围，保留日文边界、变量和各来源的其他后文，详情见 `reports/translation-merge-review.md` 与 `reports/translation-merge-applied.json`。

分段决定保存在 `tools/text_segmentation.csv`：用来源记录 ID、日中全文 SHA-256 固定输入，`units` 为 `[日文结束行序,中文起点,中文终点,状态]` 数组，下一单元从上一单元结束后的日文行开始。文本源改变时必须重新核对，不能沿用旧下标。完全相同的日中源段复用同一决定，各版本与重复来源记录保持独立。两行短段仅在第一行与已核对单元、中文前缀都精确同文时复用边界（忽略日文排版空格和中文换行）；其余合并为一个单元，不按字符长度推断。

`slot_rules.csv` 单独维护，不由建库器覆盖；73 条规则引用相同固定日版源码，按完整标签应用，不按模糊名称推断。`AIBattleUseItemText.RAM3` 已追溯到 `GetItemName`。交换物种缓冲区、固定道馆城市/馆主与昵称分别按实际生产者判定，不再把所有 `MonName` 当昵称。8 座道馆的城市和馆主词条从本地 `.CityName/.LeaderName` 与原汉化 `Townnames` 表对应；固定退出词来自 `StartMenuExitText`。

姓名长度依据 `constants/text_constants.asm` 的 `NAME_LENGTH=6`（含终止符）。已知名称、数字与词条槽位按类型匹配；尚无语义类型的 RAM 槽位只在固定正文锚点充分时按字面绑定，不据裸值识别来源。PC 昵称行分离名字与等级，名字查库且保留可见等级，独立取消行使用固定词；数量允许原始 `×` 后的排版空格，保留原 OCR 和数量后缀。敌方前缀及 `#` 展开仍取自同源既有汉化，先截取保存范围再展开，不改变原始来源或码点下标。`UsedMove` 组合按保存的 `move_symbols/default_set` 筛选源语法模板。

`TrainerEnd.*` 的 1,043 条组合由 `trainer` 宏和 `SaveEndBattleTextPointers` 的实际目标生成，支持全局及局部标签；不是给任意对白添加说话者。前缀来自 `TrainerEndBattleText`，中文为原汉化的姓名与冒号，`SPEAKER` 独立绑定当前可见姓名。组合保留正文所有语义边界，采用相同的窗口相交单元显示规则；滚出画面的姓名不从历史补用。原始正文记录和两个来源均保留，生成器在已确认修订之后构造组合，目标缺失时停止建库。

运行时开发验证见 `reports/chinese-runtime.md/json`，开发预期位于 `src/test/resources/chinese-cases.json`，13 张受影响图片的 29 个单元另有 `chinese-regressions.json` 独立固定预期。当前建库计数见 `reports/text-corpus.json`；`reports/text-corpus-static.json` 保留早期建库快照，不代表当前资源哈希。全图执行与来源修复证据见 `reports/chinese-images.md`；最终中文 A6、真实时序、实机及当前界面交互仍未验收。上游文本及派生资源的再分发授权未确认；本地建库不等于 S6 已具备分发条件。

商店购买列表复用道具词条，运行时从整行尾部拆出数字与「円」，只翻译名称并保留价格；名称与价格可直接相邻。没有将金额、操作菜单或购买数量专框加入翻译词库。
