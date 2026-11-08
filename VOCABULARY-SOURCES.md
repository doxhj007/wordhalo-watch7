# 词书来源与口径

WordHalo 0.22 内置 6 本彼此独立的词书。应用一次只启用一本；每本书各自保存成员、顺序、词数和学习进度显示。安装包里的 `words.tsv` 会将重合词去重以节省手表空间，`book-orders.tsv` 则保留每本书的独立成员和原有顺序。

| 等级 | 词书 | 应用内词头数 | 版本/口径 | 成员与顺序来源 |
|---|---|---:|---|---|
| 四级 | 星火《四级词汇必背乱序版》 | 2261 | 2014 年 3 月第 1 版；原数据 2296 项，应用按拼写去重 | DictionaryData |
| 四级 | 新东方《四级词汇正序版》 | 4447 | 2013 年 11 月第 1 版；原数据 4450 项，应用按拼写去重 | DictionaryData |
| 四级 | 《四级词汇闪过》 | 5821 | 公开整理词序 | English Vocabulary Vault |
| 六级 | 星火《六级巧记速记乱序版》 | 2999 | 2009 年 7 月第 1 版；原数据 3018 项，应用按拼写去重 | DictionaryData |
| 六级 | 新东方《六级词汇正序版（红宝书）》 | 2744 | 2013 年 8 月第 1 版；原数据 2747 项，应用按拼写去重 | DictionaryData |
| 六级 | 《六级词汇闪过乱序版》 | 5325 | 公开整理词序 | English Vocabulary Vault |

同一个单词出现在多本书时，个人记忆阶段、错词状态和下次复习时间会沿用；书内的总数、已学数和后续新词顺序仍按当前词书计算。切换词书不改变正在进行的一组，从下一组开始生效。

## 数据源

- [DictionaryData](https://github.com/LinXueyuanStdio/DictionaryData)：书目、词书成员和顺序。上游仓库声明 Apache-2.0。
- [English Vocabulary Vault](https://github.com/lilinji/English)：两本“闪过”公开整理表。上游说明这些资料仅供个人、非商业学习研究使用；WordHalo 为免费个人学习项目。
- [ECDICT](https://github.com/skywind3000/ECDICT)：短释义和基础音标，MIT License。应用会压缩释义以适应手表小屏，因此不等同于原书的完整释义。

生成时使用的源文件 SHA-256、每本书的首尾词和准确数量记录在 [`vocabulary-info.json`](app/src/main/assets/vocabulary-info.json)。构建脚本是 [`prepare_branded_books.py`](tools/prepare_branded_books.py)。

## 百词斩版本

用户当前使用的百词斩“六级核心（过考版）”显示 2551 词。项目目前没有取得可核验的 2551 词完整清单和顺序，所以 0.22 没有用 ECDICT 高频词截取 2551 个后冒充该版本。数据结构已支持继续增加独立词书；取得该版本的合法准确清单后，可以新增为第 7 本书，而不会与现有词书混合。

星火、新东方、闪过、百词斩等名称及相关书名归各自权利人所有；这里用来识别学习者选择的具体词表，不表示官方合作或背书。
