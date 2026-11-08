# 词间 · WordHalo

为 Samsung Galaxy Watch7 44mm 设计的六级单词应用。离线背词，表圈操作，分层触感，英美发音按需联网播放。

当前版本 **v0.20.0** · Wear OS 6 / API 36 · 480 × 480 圆屏。

## 安装

[下载 v0.20 APK](dist/WordHalo-Watch7-v0.20.apk) · [使用说明](docs/使用说明.md) · [SHA-256 校验](dist/SHA256SUMS.txt)

在 GitHub 文件页点击下载按钮保存 APK，再通过 Wear OS 工具箱无线调试安装。已有词间的用户直接覆盖安装，**不要先卸载**；本版包名和签名与之前版本一致，保留学习进度。

## 功能

- 词义选择与主动回忆自评；每组 5 / 10 / 20 词，到期复习优先。
- 分别调节答题、完成与表圈、翻卡的触感强度，支持关闭。
- 表圈移动选框、翻卡、滚动菜单、调整触感和切换统计页；答题须显式确认。
- 收藏词本、错词本、今日词、稳定乱序翻卡，以及七天学习概览。
- 83 张离线图片关联 339 个词，22 条近音助记，51 条构词、辨析及画面线索。
- 英式、美式读音手动点播；不预下载、不保存录音。离线时仍可背词与查看内置图片。

![Watch7 原生界面预览](docs/preview.png)

## 词库说明

当前内置 **1945 个 ECDICT 六级进阶词**，**尚未对齐百词斩「六级核心（过考版）」2551 词**。本项目不读取百词斩账号，也不包含从百词斩提取的图片。

v0.20 校正 68 个词的常用释义或词性，详见 [修改记录](VOCABULARY-REVIEW.json)。短释义和记忆线索用于辅助记忆，不替代完整词典。

## 构建与验证

原生 Java 11 + Android Views + SQLite，无第三方运行时依赖。已通过 31 项 Android API 36 运行测试、10018 条学习逻辑断言；未连接真实 Watch7 测试本版触感、表圈事件或蓝牙代理网络。

构建使用 JDK 17+、Python 3、Android SDK Platform 36 和 Build Tools 36.0.0，从仓库根目录执行：

```text
python tools/build.py --platform SDK/platforms/android-36 --tools SDK/build-tools/36.0.0 --jdk JDK --work build-local --key private-signing --out build-local/WordHalo.apk
```

路径请替换为本机实际位置。已有内置素材无需重新联网生成。脚本在指定目录没有密钥时会生成新密钥；新签名无法覆盖安装本仓库的成品 APK。用于本项目后续更新的原签名私钥仅在作者本机保留。

[开发说明](docs/DEVELOPMENT.md) · [验证记录](docs/validation.json)

## 数据与署名

学习记录、收藏保存在手表本机。读音和地区音标请求使用公开词典服务，服务可用性与网络会影响播放。

ECDICT 数据的 MIT 许可见 [ECDICT-LICENSE.txt](app/src/main/assets/ECDICT-LICENSE.txt)。照片各自保留原许可，来源、作者及修改说明见 [PHOTO-CREDITS.md](PHOTO-CREDITS.md)，应用内也可查看。源码和自编文字尚未单独指定开放许可。
