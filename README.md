<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/money-icon-dark.svg" />
    <img src="docs/assets/money-icon.svg" width="144" height="144" alt="Money 应用图标" />
  </picture>
</p>

<h1 align="center">Money</h1>

<p align="center">
  一个完全离线、基于 Kotlin 与 Jetpack Compose 的 Android 个人记账应用。
</p>

<p align="center">
  当前版本 <code>2.6.5</code>（versionCode <code>149</code>）
</p>

## ✨ 功能特性

- 极简主线：账户与明细两个主页面，文字操作替代装饰图标，不提供手动排序。
- 账户管理：新建只需名称与余额；余额归零后可关闭，历史保留且可重新开启。
- 四步记账：选择方式 → 选择账户 → 输入金额 → 时间、备注与确认，仅最后一步保存。
- 现金流与转账：记录入账、出账和账户间转账，金额始终以最小货币单位精确存储。
- 余额核对：支持零余额与负余额，历史对账差额保持为固定账本事件。
- 明细与搜索：保留关键字、账户和日期筛选，以及编辑、删除与撤销。
- 隐私与安全：支持生物识别应用锁、应用内金额隐藏和最近任务内容保护。
- JSON 备份：明文 backup v5，兼容 v1-v4，导入前生成安全快照并支持条件回滚。
- 桌面快捷方式：直接进入对应的分步记账流程，只跳过已明确的上下文。

旧版隐藏账户、投资账户语义及备份字段继续兼容。独立首页、批量对账、周期提醒和电脑连接不再提供入口；后台提醒与 LAN 服务已停用，已有账目不被删除。

## 🤖 Money Client Skill

[Money Client Skill](https://github.com/shihuaidexianyu/money-client-skill) 是旧版电脑连接功能配套的独立 Codex skill 与 Python MCP bridge。当前极简版已停用手机端 LAN 服务，不能使用这条连接路径；以下链接仅用于旧版本参考。

- 仓库：[shihuaidexianyu/money-client-skill](https://github.com/shihuaidexianyu/money-client-skill)
- 安装包：[v0.3.0 Release](https://github.com/shihuaidexianyu/money-client-skill/releases/tag/v0.3.0)

## 🧱 技术栈

- **语言**: Kotlin 2.2.20
- **UI**: Jetpack Compose BOM 2025.10.01 + Material 3
- **架构**: Clean Architecture（Domain / Data / UI）+ MVVM
- **数据库**: Room 2.8.0（SQLite），模式版本 21
- **设置存储**: Room portable settings + DataStore Preferences 1.1.7 device preferences
- **导航**: Navigation Compose 2.9.5
- **后台任务**: WorkManager 2.10.1（保留兼容实现，当前不调度提醒）
- **旧版 AI bridge**: LAN TCP 服务 + Python MCP stdio（当前已停用）
- **依赖注入**: 手动注入（`MoneyAppContainer`）
- **构建工具**: AGP 9.1.0 + Java 17
- **SDK 版本**: minSdk 31，targetSdk/compileSdk 36
- **包名**: `com.shihuaidexianyu.money`

## 🚀 构建与测试

```bash
# 调试构建
./gradlew assembleDebug

# 发布构建
./gradlew assembleRelease

# 运行全部单元测试
./gradlew test

# 运行 Debug Lint
./gradlew lintDebug
```

也可使用发布脚本进行版本号管理、测试、签名校验与打包：

```bash
# 仅打包并自动 bump 版本
.\scripts\build-release.ps1

# 先测试再打包、提交、推送
.\scripts\build-release.ps1 -RunTests -Commit -Push
```

发布包必须使用本地 `signing/keystore.properties` 配置的正式证书签名。备份文件是未加密 JSON，请只保存到可信位置。

## 📌 当前发布范围

`2.6.5` 聚焦账户、入账/出账、转账、对账、明细和备份。四步记账与最终确认保留；开户余额默认零，简化历史筛选，账户关闭后仍可查看流水并重新开启。旧账本和备份继续兼容，数据库模式版本保持 21。

## 🗂️ 目录结构

```text
app/src/main/java/com/shihuaidexianyu/money/
├── domain/          # 业务模型与接口、UseCase
├── data/            # Room 实体、DAO、仓库实现
├── ui/              # Compose 页面与各 feature ViewModel
├── navigation/      # 导航与 ViewModel 工厂
├── notification/    # WorkManager 通知投影与调度
├── lan/             # 临时局域网服务、协议、配对与请求路由
└── util/            # 工具方法与格式化逻辑
```

## 📄 协议

All rights reserved.
