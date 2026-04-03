# money 实现 TODO 文档（MVP 直接开工版）

## 0. 文档目标

本文档用于把产品想法直接收敛成可实现的 Android MVP 方案。目标不是解释产品理念，而是把实现过程拆成**明确步骤、明确数据结构、明确交互规则、明确验收标准**，使开发可以按顺序推进。

本文档默认技术路线如下：

- 平台：Android
- 语言：Kotlin
- UI：Jetpack Compose
- 架构：MVVM + Repository
- 本地存储：Room
- 设置存储：DataStore
- 导航：Navigation Compose
- 后台任务：WorkManager
- 图表：MPAndroidChart 或 Compose 原生 Canvas（MVP 推荐先用图表库）
- 序列化导出：kotlinx.serialization
- 日期时间：java.time

若后续技术栈变化，产品规则不变，仅实现方式调整。

---

# 1. MVP 定义

## 1.1 MVP 要解决的问题

用户必须能够完成以下任务：

1. 建立账户，并录入当前余额
2. 记录一笔入账
3. 记录一笔出账
4. 记录一笔转账
5. 查看首页总资产
6. 查看首页默认周期净流入/净流出
7. 查看各账户当前余额与分组
8. 查看全部历史记录
9. 用备注 / 账户 / 日期 / 金额筛选历史记录
10. 对任意账户执行“更新余额”
11. 在余额不一致时自动生成余额矫正记录
12. 对证券账户更新余额后自动计算本周期盈亏与收益率
13. 查看账户详情页
14. 编辑普通记录
15. 删除普通记录
16. 归档旧账户
17. 导出 JSON 备份

## 1.2 MVP 明确不做

以下能力全部排除在 MVP 之外：

- 自动同步银行/支付平台流水
- OCR/票据识别
- 消费分类系统
- 预算系统
- 理财建议
- 家庭共享
- 多端云同步
- Widget
- 系统通知提醒
- 深度图表分析
- 多币种自动汇率

---

# 2. 全局产品规则

## 2.1 账户分组

MVP 仅支持以下账户分组：

- 支付类
- 银行类
- 投资类

## 2.2 前台动作

前台只提供以下四类显式动作：

- 入账
- 出账
- 转账
- 更新余额

说明：

- 入账：外部资金进入个人资金系统
- 出账：资金流出个人资金系统
- 转账：账户之间移动
- 更新余额：确认该账户当前真实余额

## 2.3 首页统计口径

首页默认周期支持：

- 本周
- 本月

默认周期由设置项决定。

“本周”定义固定为：

- 周一 00:00:00 到周日 23:59:59.999

首页净流入/净流出统计规则：

- 只统计入账与出账
- 排除转账
- 排除余额更新
- 排除余额矫正

## 2.4 总资产口径

总资产 = 所有未归档账户的当前余额之和。

说明：

- 支付类账户计入
- 银行类账户计入
- 投资类账户计入
- 已归档账户默认不计入
- 已过期账户仍计入，但首页必须显示“含过期账户估值”提示（若存在）

## 2.5 记录时间规则

- 所有记录默认时间为当前时刻
- 用户允许修改为任意过去时间
- MVP 不允许未来时间

## 2.6 用途文本规则

- 入账/出账的用途字段可为空
- 保存前若为空，弹一次轻提示：`未填写用途，后续检索会变弱，仍要保存吗？`
- 用户确认后允许保存

## 2.7 余额更新规则

所有账户都支持“更新余额”。

更新余额时：

1. 用户输入该账户当前真实余额
2. 系统计算当前系统余额
3. 比较二者差值
4. 生成一条 BalanceUpdateRecord
5. 若差值不为 0，则额外生成一条 BalanceAdjustmentRecord
6. 当前账户余额以本次更新后的真实余额为准

## 2.8 投资账户额外规则

投资类账户在更新余额后，还必须生成一个周期统计结果：

- 本周期净转入
- 本周期净转出
- 本周期盈亏
- 本周期收益率

本周期盈亏计算公式固定为：

`pnl = current_balance - previous_balance - net_transfer_in + net_transfer_out`

其中：

- current_balance：本次更新余额输入值
- previous_balance：上一次投资账户更新余额的输入值；若不存在，则取账户初始余额
- net_transfer_in：两次更新之间转入该投资账户的总和
- net_transfer_out：两次更新之间转出该投资账户的总和

本周期收益率 MVP 先采用：

`return_rate = pnl / max(previous_balance + net_transfer_in - net_transfer_out, 0.01)`

显示为百分比，保留两位小数。

---

# 3. 信息架构

一级页面固定为四个：

1. 首页
2. 历史
3. 账户
4. 设置

说明：

- 记账动作使用 FAB 或首页快捷按钮触发，不作为一级 tab
- 账户详情、记录编辑、筛选页、导出页均为二级页面

---

# 4. 数据模型

以下为 MVP 必须实现的数据实体。

## 4.1 AccountEntity

字段：

- id: Long
- name: String
- groupType: String (`payment` / `bank` / `investment`)
- initialBalance: Long
- createdAt: Long
- archivedAt: Long?
- isArchived: Boolean
- lastUsedAt: Long?
- lastBalanceUpdateAt: Long?
- displayOrder: Int

金额全部用 Long 存储“最小货币单位”，如分。

示例：

- 12.34 元 => 1234

规则：

- name 不允许为空
- name 在未归档账户中必须唯一
- initialBalance 可为 0
- archivedAt 在 isArchived=false 时必须为 null

## 4.2 CashFlowRecordEntity

用于入账/出账。

字段：

- id: Long
- accountId: Long
- direction: String (`inflow` / `outflow`)
- amount: Long
- purpose: String
- occurredAt: Long
- createdAt: Long
- updatedAt: Long
- isDeleted: Boolean

规则：

- amount 必须 > 0
- purpose 可为空字符串
- 删除采用软删除，MVP 允许后期做真正清理

## 4.3 TransferRecordEntity

字段：

- id: Long
- fromAccountId: Long
- toAccountId: Long
- amount: Long
- note: String
- occurredAt: Long
- createdAt: Long
- updatedAt: Long
- isDeleted: Boolean

规则：

- fromAccountId != toAccountId
- amount > 0
- note 可为空

## 4.4 BalanceUpdateRecordEntity

字段：

- id: Long
- accountId: Long
- actualBalance: Long
- systemBalanceBeforeUpdate: Long
- delta: Long
- occurredAt: Long
- createdAt: Long

说明：

- delta = actualBalance - systemBalanceBeforeUpdate
- 即使 delta = 0，也必须生成此记录

## 4.5 BalanceAdjustmentRecordEntity

字段：

- id: Long
- accountId: Long
- delta: Long
- sourceUpdateRecordId: Long
- occurredAt: Long
- createdAt: Long

规则：

- 仅当 delta != 0 时生成
- 与 BalanceUpdateRecord 一对一或零对一关联

## 4.6 InvestmentSettlementEntity

仅用于投资类账户在更新余额后的统计结果。

字段：

- id: Long
- accountId: Long
- balanceUpdateRecordId: Long
- previousBalance: Long
- currentBalance: Long
- netTransferIn: Long
- netTransferOut: Long
- pnl: Long
- returnRate: Double
- periodStartAt: Long
- periodEndAt: Long
- createdAt: Long

说明：

- 每次投资类账户更新余额都生成一条
- periodStartAt 取上一次 BalanceUpdateRecord.occurredAt；若无，则取账户 createdAt
- periodEndAt 取本次 BalanceUpdateRecord.occurredAt

## 4.7 AppSettings

存入 DataStore。

字段：

- homePeriod: String (`week` / `month`)
- weekStart: String (`monday`) MVP 固定但仍暴露设置
- currencySymbol: String
- amountDisplayStyle: String (`symbol_before` / `symbol_after`)
- showStaleMark: Boolean
- accountSortMode: String (`recent_used` / `manual` / `balance_desc`)

---

# 5. 余额计算规则

## 5.1 普通账户当前余额计算

对任意账户，当前余额不从 initialBalance 永久推导，而从最近一次 BalanceUpdateRecord 开始推导。

若账户从未更新余额：

`current = initialBalance + inflow - outflow + transfer_in - transfer_out + adjustment_sum`

若账户已有至少一次更新余额：

`current = latest_actual_balance + inflow_after_update - outflow_after_update + transfer_in_after_update - transfer_out_after_update + adjustment_after_update_sum`

其中：

- latest_actual_balance = 最近一次 BalanceUpdateRecord.actualBalance
- adjustment_after_update_sum 通常为 0，因为 adjustment 应与 update 同时发生；保留该项是为了逻辑一致性

## 5.2 系统余额（更新前）计算

用户点击“更新余额”时，系统要先算出当前系统余额，用于展示差额。

算法：调用与当前余额相同的计算逻辑，但不纳入本次尚未保存的 update。

## 5.3 投资账户当前余额计算

投资类账户也使用同一套规则，不做特殊余额算法。

特殊之处仅在于：

- 更新余额后额外生成 settlement
- settlement 用于计算本周期盈亏/收益率

---

# 6. 页面与交互规格

## 6.1 首页 HomeScreen

### 6.1.1 页面结构

自上而下：

1. 顶部总资产卡片
2. 默认周期净流入/净流出双卡片
3. 账户列表（按分组）
4. 待更新提示条
5. 浮动操作按钮 FAB

### 6.1.2 总资产卡片

显示：

- 标题：`总资产`
- 总金额
- 若存在过期账户且 showStaleMark = true，则显示：`含 X 个过期账户估值`

点击行为：

- 无跳转，MVP 仅展示

### 6.1.3 周期卡片

显示：

- 左卡：净流入
- 右卡：净流出
- 标题根据设置显示“本周”或“本月”

点击行为：

- 点击标题右侧切换器时，进入一个简单弹窗让用户临时切换查看周期
- 不改变设置项，只影响当前会话显示

### 6.1.4 账户列表

按分组顺序显示：

1. 支付类
2. 银行类
3. 投资类

组标题固定。

每个账户卡片显示：

- 账户名称
- 当前余额
- 若为投资类且 showStaleMark = true：显示 `已过期 X 天` 或 `已更新`
- 若为非投资类且超出推荐更新周期：显示 `待更新`

卡片点击：

- 进入账户详情页

卡片长按（MVP 可选，不强制）：

- 打开操作底部菜单：入账 / 出账 / 转账 / 更新余额 / 编辑账户 / 归档

### 6.1.5 待更新提示条

显示条件：

- 至少有一个账户需要更新

显示内容：

- `2 个账户待更新`

点击：

- 进入账户页并应用筛选 `待更新`

### 6.1.6 FAB

点击展开四个动作：

- 入账
- 出账
- 转账
- 更新余额

规则：

- 点击入账/出账/更新余额：先弹出账户选择
- 点击转账：直接进入转账页

## 6.2 历史页 HistoryScreen

### 6.2.1 默认状态

显示全部记录，按 occurredAt 倒序。

### 6.2.2 记录类型展示规则

统一时间线中可出现：

- 入账记录
- 出账记录
- 转账记录
- 余额更新记录
- 余额矫正记录

显示文案建议：

- 入账：`入账 · 工资` / `入账`
- 出账：`出账 · 午饭` / `出账`
- 转账：`微信零钱 → 银行卡`
- 更新余额：`更新余额`
- 余额矫正：`余额矫正 +2.36`

### 6.2.3 顶部筛选栏

支持四类筛选：

- 关键词（匹配 purpose 与 transfer.note）
- 账户
- 日期范围
- 金额范围

筛选规则：

- 多条件 AND
- 空条件不参与
- 账户筛选对转账记录的判断：fromAccountId 或 toAccountId 任一匹配即算命中

### 6.2.4 点击记录

- 入账/出账：进入记录详情页，可编辑、删除
- 转账：进入转账详情页，可编辑、删除
- 余额更新：进入更新详情页，只读
- 余额矫正：进入矫正详情页，只读

## 6.3 账户页 AccountsScreen

### 6.3.1 默认状态

展示所有未归档账户，按设置排序。

### 6.3.2 支持功能

- 新建账户
- 查看账户详情
- 进入待更新筛选模式
- 查看已归档账户

## 6.4 账户详情页 AccountDetailScreen

### 6.4.1 通用头部

显示：

- 账户名称
- 分组类型
- 当前余额
- 最近更新时间

按钮：

- 入账
- 出账
- 转账
- 更新余额

### 6.4.2 走势区域

所有账户都支持趋势图。

MVP 图表规则：

- x 轴：时间
- y 轴：账户余额
- 自动计算 min/max
- 在 min/max 上下各加 5% padding
- 若 max = min，则固定扩展一个小范围，避免图线贴边

数据源：

- initialBalance 作为第一个点
- 每条影响余额的事件后都可形成一条点位
- 为简化实现，MVP 可先只用 BalanceUpdateRecord + 当前余额构成趋势点

推荐实现：

- MVP 第一版：使用 BalanceUpdateRecord 序列 + 当前点
- 第二版再扩展为完整余额时序

### 6.4.3 投资账户额外区域

显示最近一次结算结果：

- 本周期盈亏
- 本周期收益率
- 本周期净转入
- 本周期净转出

### 6.4.4 记录列表

仅显示与当前账户相关的记录：

- 入账/出账：accountId 匹配
- 转账：fromAccountId 或 toAccountId 匹配
- 更新余额：accountId 匹配
- 余额矫正：accountId 匹配

排序：按 occurredAt 倒序

## 6.5 新建账户页 CreateAccountScreen

字段：

- 账户名称
- 分组类型（支付类 / 银行类 / 投资类）
- 当前余额（即 initialBalance）

保存规则：

- 所有字段必填
- 金额允许 0
- 名称去重

保存后行为：

- 创建账户
- 返回账户页
- 自动刷新首页

## 6.6 入账/出账页 RecordCashFlowScreen

### 6.6.1 进入方式

- 首页 FAB -> 入账/出账 -> 先选账户 -> 进入本页
- 账户详情页点击入账/出账 -> 直接进入本页并带上账户

### 6.6.2 字段

- account（只读展示，若从全局进入则已预选）
- direction（入账或出账）
- amount
- purpose
- occurredAt

### 6.6.3 保存规则

- amount 必填且 > 0
- purpose 可空，但弹提醒
- occurredAt <= now

保存后：

- 写入 CashFlowRecord
- 更新 account.lastUsedAt
- 回到来源页面

## 6.7 转账页 RecordTransferScreen

字段：

- fromAccount
- toAccount
- amount
- note
- occurredAt

规则：

- from != to
- amount > 0
- note 可空
- occurredAt <= now

保存后：

- 写入 TransferRecord
- 更新两个账户的 lastUsedAt

## 6.8 更新余额页 UpdateBalanceScreen

### 6.8.1 字段

- account
- currentSystemBalance（只读）
- actualBalance（用户输入）
- occurredAt

### 6.8.2 预览区域

输入 actualBalance 后，实时显示：

- 系统余额：xxx
- 实际余额：xxx
- 差额：+/-xxx

### 6.8.3 保存规则

- actualBalance 必填
- occurredAt <= now

保存流程：

1. 计算 systemBalanceBeforeUpdate
2. delta = actualBalance - systemBalanceBeforeUpdate
3. 写入 BalanceUpdateRecord
4. 若 delta != 0，写入 BalanceAdjustmentRecord
5. 若账户为投资类，生成 InvestmentSettlement
6. 更新 account.lastBalanceUpdateAt
7. 若 occurredAt > account.lastUsedAt，则同步更新 lastUsedAt
8. 跳转到结果页

## 6.9 更新结果页 BalanceUpdateResultScreen

普通账户显示：

- 更新前系统余额
- 本次确认余额
- 差额
- 是否已生成矫正

投资账户额外显示：

- 上次结算余额
- 本次结算余额
- 本周期净转入
- 本周期净转出
- 本周期盈亏
- 本周期收益率

按钮：

- 完成
- 查看账户详情

## 6.10 记录编辑页

支持编辑类型：

- 入账/出账
- 转账

更新规则：

- 保存后重新计算相关账户余额
- 若修改时间影响投资账户结算区间，后续结算结果全部重算
- 弹提示：`此修改会影响当前余额与后续统计`

## 6.11 删除确认

删除以下记录必须二次确认：

- 入账/出账
- 转账

文案固定：

`删除后将重新计算相关账户余额与后续统计，确认删除？`

余额更新与余额矫正记录默认不允许直接删除。

MVP 规则：

- 删除 BalanceUpdateRecord：不支持
- 删除 BalanceAdjustmentRecord：不支持

若后续需要，只能通过“撤销更新余额”整体处理。

## 6.12 设置页

必须提供以下设置项：

- 首页默认周期（本周/本月）
- 一周起始日（默认周一，MVP 允许显示但先只支持周一）
- 货币单位
- 金额显示格式
- 是否显示过期账户标记
- 默认账户排序逻辑
- JSON 导出

---

# 7. 过期与待更新规则

所有账户都应有“推荐更新周期”概念。MVP 默认：

- 支付类：7 天
- 银行类：7 天
- 投资类：7 天

后续可开放到账户级自定义，MVP 先写死。

判断规则：

- 若从未更新余额，且创建时间距今 >= 7 天，则状态为待更新
- 若已更新余额，且 now - lastBalanceUpdateAt >= 7 天，则状态为待更新
- 超过 7 天即显示待更新，不区分严重级别

首页过期标记仅针对投资类账户显示“过期估值”文案；其他账户统一显示“待更新”。

---

# 8. Repository 与业务用例

必须拆分以下 UseCase：

## 8.1 CreateAccountUseCase

输入：账户名、分组、初始余额

输出：新账户 id

校验：

- 名称非空
- 名称唯一

## 8.2 CreateCashFlowRecordUseCase

输入：accountId, direction, amount, purpose, occurredAt

输出：recordId

副作用：更新 account.lastUsedAt

## 8.3 CreateTransferRecordUseCase

输入：fromAccountId, toAccountId, amount, note, occurredAt

输出：recordId

副作用：更新两个账户的 lastUsedAt

## 8.4 UpdateBalanceUseCase

输入：accountId, actualBalance, occurredAt

输出：result DTO

DTO 包含：

- systemBalanceBeforeUpdate
- actualBalance
- delta
- adjustmentCreated
- 若投资账户则包含 settlementSummary

## 8.5 CalculateCurrentBalanceUseCase

输入：accountId

输出：Long

## 8.6 RecalculateInvestmentSettlementsUseCase

触发条件：

- 删除/编辑与投资账户相关的转账记录
- 编辑/删除投资账户入账/出账（理论上不应有普通投资账户入/出账，MVP 允许但不推荐）

逻辑：

- 取该账户所有 BalanceUpdateRecord 按时间排序
- 逐区间重算 settlement

## 8.7 ExportJsonUseCase

导出内容：

- accounts
- cashFlowRecords
- transferRecords
- balanceUpdateRecords
- balanceAdjustmentRecords
- investmentSettlements
- settings
- exportedAt
- appVersion

---

# 9. 数据库 DAO 清单

必须至少实现以下 DAO：

- AccountDao
- CashFlowRecordDao
- TransferRecordDao
- BalanceUpdateRecordDao
- BalanceAdjustmentRecordDao
- InvestmentSettlementDao

每个 DAO 至少包含：

- insert
- update
- softDelete（对可删除记录）
- queryById
- queryAllActive
- queryByAccountId

AccountDao 额外需要：

- queryActiveAccounts()
- queryArchivedAccounts()
- updateLastUsedAt()
- updateLastBalanceUpdateAt()
- archiveAccount()

---

# 10. 状态管理

每个页面建立独立 ViewModel。

必须包含以下 ViewModel：

- HomeViewModel
- HistoryViewModel
- AccountsViewModel
- AccountDetailViewModel
- CreateAccountViewModel
- RecordCashFlowViewModel
- RecordTransferViewModel
- UpdateBalanceViewModel
- SettingsViewModel

状态流统一用 StateFlow。

单次事件统一用 SharedFlow 或 Channel。

---

# 11. 实现顺序（严格按阶段推进）

## 阶段 1：项目骨架

### TODO

- [ ] 创建 Android 项目
- [ ] 配置 Compose
- [ ] 引入 Room / DataStore / Navigation / Serialization / WorkManager 依赖
- [ ] 搭建基础包结构
- [ ] 建立主题、颜色、Typography
- [ ] 建立基础导航框架

### 包结构建议

- data/
  - db/
  - dao/
  - entity/
  - repository/
- domain/
  - model/
  - usecase/
- ui/
  - home/
  - history/
  - accounts/
  - settings/
  - common/
- navigation/
- util/

### 验收标准

- App 能正常启动
- 底部四个 tab 框架可切换
- 主题稳定

## 阶段 2：数据层

### TODO

- [ ] 建立全部 Room Entity
- [ ] 建立 DAO
- [ ] 建立 Database
- [ ] 写 Repository 接口与实现
- [ ] 建立金额格式化工具
- [ ] 建立时间范围计算工具（本周/本月）

### 验收标准

- 可插入/读取账户
- 可插入/读取记录
- 单元测试通过基础 CRUD

## 阶段 3：账户系统

### TODO

- [ ] 实现新建账户页
- [ ] 实现账户列表页
- [ ] 实现归档账户
- [ ] 实现账户详情页基础头部
- [ ] 实现账户排序逻辑

### 验收标准

- 可新增账户
- 首页与账户页能显示账户
- 可归档并查看已归档账户

## 阶段 4：入账/出账/转账

### TODO

- [ ] 实现账户选择弹窗
- [ ] 实现入账页
- [ ] 实现出账页
- [ ] 实现转账页
- [ ] 实现 purpose 空值提示
- [ ] 保存后刷新首页/历史/详情

### 验收标准

- 三类记录都能保存
- 账户余额可正确计算
- 历史页能显示三类记录

## 阶段 5：首页统计

### TODO

- [ ] 实现总资产计算
- [ ] 实现默认周期净流入/净流出计算
- [ ] 实现首页账户分组列表
- [ ] 实现待更新提示条
- [ ] 实现 FAB 四动作

### 验收标准

- 首页数据正确
- 转账不污染净流入/净流出
- 过期账户提示可显示

## 阶段 6：更新余额与余额矫正

### TODO

- [ ] 实现 UpdateBalanceUseCase
- [ ] 实现更新余额页
- [ ] 实现差额预览
- [ ] 实现 BalanceUpdateRecord 写入
- [ ] 实现 BalanceAdjustmentRecord 自动生成
- [ ] 实现更新结果页
- [ ] 更新 lastBalanceUpdateAt

### 验收标准

- 所有账户可更新余额
- 差额正确计算
- 不一致时自动生成矫正记录
- 当前余额更新正确

## 阶段 7：投资账户结算

### TODO

- [ ] 实现 InvestmentSettlement 生成逻辑
- [ ] 在更新余额后对投资账户自动生成 settlement
- [ ] 实现结算结果展示区域
- [ ] 实现账户详情页中的投资统计卡
- [ ] 实现相关记录变动后的 settlement 重算

### 验收标准

- 证券账户更新余额后自动算出 pnl 与 returnRate
- 转账影响能正确反映到 pnl
- 删除旧转账后能重算后续 settlement

## 阶段 8：历史检索与编辑

### TODO

- [ ] 实现历史页筛选器
- [ ] 实现关键词/账户/日期/金额四类筛选
- [ ] 实现入账/出账编辑
- [ ] 实现转账编辑
- [ ] 实现记录删除
- [ ] 删除/编辑后重算余额与统计

### 验收标准

- 所有筛选条件有效
- 编辑/删除行为能影响余额
- 提示文案齐全

## 阶段 9：趋势图

### TODO

- [ ] 为账户详情页接入折线图组件
- [ ] 建立图表数据转换器
- [ ] 实现自适应 y 轴范围
- [ ] 投资账户展示结算点
- [ ] 普通账户展示更新余额点（MVP 先这样做）

### 验收标准

- 图表可正常显示
- 数值范围合理，不贴边
- 空数据有占位态

## 阶段 10：设置与导出

### TODO

- [ ] 实现设置页全部字段
- [ ] DataStore 持久化
- [ ] 实现 JSON 导出 DTO
- [ ] 实现文件写入
- [ ] 实现分享/保存导出文件

### 验收标准

- 设置项修改后生效
- JSON 导出内容完整
- 可在系统文件管理器中看到导出文件

## 阶段 11：测试与收尾

### TODO

- [ ] 单元测试：余额计算
- [ ] 单元测试：净流入/净流出统计
- [ ] 单元测试：投资 settlement
- [ ] 单元测试：更新余额生成 adjustment
- [ ] UI 测试：首页/记录/更新余额关键链路
- [ ] 修复空状态、加载态、错误态
- [ ] 添加首次启动引导

### 验收标准

- 关键业务逻辑全通过
- 关键页面无明显崩溃点
- 首次启动到完成一条完整链路无阻塞

---

# 12. 首次启动引导

MVP 必须有一个极简 onboarding。

步骤固定为：

1. 欢迎页
2. 创建第一个账户
3. 返回首页
4. 提示用户可使用 FAB 添加记录或更新余额

若用户尚无账户：

- 首页显示空状态
- 主按钮：`添加第一个账户`

---

# 13. UI 细节规则

## 13.1 金额输入

- 使用数字键盘
- 支持小数点输入
- 内部实时转最小单位
- 不允许负号

## 13.2 金额显示

- 默认保留两位小数
- 货币符号按设置显示前置/后置
- 负值显示 `-¥12.34`

## 13.3 空状态

首页无账户：

- 文案：`还没有账户，先添加一个吧`

历史为空：

- 文案：`还没有记录`

账户详情无记录：

- 文案：`这个账户还没有任何记录`

## 13.4 错误文案

必须使用明确文案，不用技术提示：

- `金额不能为空`
- `金额必须大于 0`
- `请选择不同的转出和转入账户`
- `账户名称不能为空`
- `已存在同名账户`
- `时间不能晚于当前时间`

---

# 14. 关键算法伪代码

## 14.1 计算账户当前余额

```kotlin
fun calculateCurrentBalance(accountId: Long): Long {
    val latestUpdate = balanceUpdateDao.getLatestForAccount(accountId)
    val anchorBalance: Long
    val anchorTime: Long

    if (latestUpdate != null) {
        anchorBalance = latestUpdate.actualBalance
        anchorTime = latestUpdate.occurredAt
    } else {
        val account = accountDao.getById(accountId)
        anchorBalance = account.initialBalance
        anchorTime = account.createdAt
    }

    val inflow = cashFlowDao.sumInflowAfter(accountId, anchorTime)
    val outflow = cashFlowDao.sumOutflowAfter(accountId, anchorTime)
    val transferIn = transferDao.sumTransferInAfter(accountId, anchorTime)
    val transferOut = transferDao.sumTransferOutAfter(accountId, anchorTime)
    val adjustment = adjustmentDao.sumAdjustmentAfter(accountId, anchorTime)

    return anchorBalance + inflow - outflow + transferIn - transferOut + adjustment
}
```

## 14.2 更新余额

```kotlin
fun updateBalance(accountId: Long, actualBalance: Long, occurredAt: Long): UpdateBalanceResult {
    val systemBalance = calculateBalanceAt(accountId, occurredAt)
    val delta = actualBalance - systemBalance

    val updateId = balanceUpdateDao.insert(
        BalanceUpdateRecordEntity(
            accountId = accountId,
            actualBalance = actualBalance,
            systemBalanceBeforeUpdate = systemBalance,
            delta = delta,
            occurredAt = occurredAt,
            createdAt = now()
        )
    )

    var adjustmentCreated = false
    if (delta != 0L) {
        adjustmentDao.insert(
            BalanceAdjustmentRecordEntity(
                accountId = accountId,
                delta = delta,
                sourceUpdateRecordId = updateId,
                occurredAt = occurredAt,
                createdAt = now()
            )
        )
        adjustmentCreated = true
    }

    if (account.groupType == "investment") {
        generateSettlement(accountId, updateId, actualBalance, occurredAt)
    }

    accountDao.updateLastBalanceUpdateAt(accountId, occurredAt)

    return UpdateBalanceResult(...)
}
```

## 14.3 生成投资结算

```kotlin
fun generateSettlement(accountId: Long, updateId: Long, currentBalance: Long, occurredAt: Long) {
    val prevUpdate = balanceUpdateDao.getPreviousForAccount(accountId, occurredAt)
    val previousBalance = prevUpdate?.actualBalance ?: accountDao.getById(accountId).initialBalance
    val periodStart = prevUpdate?.occurredAt ?: accountDao.getById(accountId).createdAt

    val netIn = transferDao.sumTransferInBetween(accountId, periodStart, occurredAt)
    val netOut = transferDao.sumTransferOutBetween(accountId, periodStart, occurredAt)
    val pnl = currentBalance - previousBalance - netIn + netOut
    val denominator = max(previousBalance + netIn - netOut, 1L)
    val rate = pnl.toDouble() / denominator.toDouble()

    settlementDao.insert(
        InvestmentSettlementEntity(
            accountId = accountId,
            balanceUpdateRecordId = updateId,
            previousBalance = previousBalance,
            currentBalance = currentBalance,
            netTransferIn = netIn,
            netTransferOut = netOut,
            pnl = pnl,
            returnRate = rate,
            periodStartAt = periodStart,
            periodEndAt = occurredAt,
            createdAt = now()
        )
    )
}
```

---

# 15. 测试用例清单

至少实现以下单元测试。

## 15.1 余额计算

- [ ] 无 update 时，初始余额 + 入账 - 出账正确
- [ ] 含转账时余额正确
- [ ] 含 adjustment 时余额正确
- [ ] 有最近 update 时，从 update 锚点继续推导正确

## 15.2 首页统计

- [ ] 入账计入净流入
- [ ] 出账计入净流出
- [ ] 转账不计入
- [ ] adjustment 不计入

## 15.3 更新余额

- [ ] systemBalance 与 actualBalance 一致时，不生成 adjustment
- [ ] 不一致时，生成 adjustment
- [ ] 更新后 currentBalance 等于 actualBalance

## 15.4 投资结算

- [ ] 无转账时 pnl 正确
- [ ] 有净转入时 pnl 正确
- [ ] 有净转出时 pnl 正确
- [ ] 多次 update 时区间划分正确

## 15.5 编辑/删除

- [ ] 删除一条出账后余额重算正确
- [ ] 编辑一条转账后投资 settlement 能重算

---

# 16. 最终开发节奏建议

若一个人开发，按以下节奏推进：

## 第 1 周

- 项目骨架
- Room / DataStore
- 账户系统

## 第 2 周

- 入账/出账/转账
- 首页余额与历史页

## 第 3 周

- 更新余额
- 余额矫正
- 投资结算

## 第 4 周

- 历史筛选
- 编辑/删除
- 趋势图
- 设置与 JSON 导出
- 测试与修复

---

# 17. 当前最重要的实现原则

整个项目开发过程中，始终遵守下面五条：

1. 前台动作一定要少，禁止偷塞分类系统
2. 所有统计口径保持一致，尤其是转账与矫正不得污染净流入/净流出
3. 余额更新一定要留痕，不允许“偷偷改余额”
4. 投资账户收益必须和资金转入转出分开
5. 用户看到的所有数字，都必须能从记录追溯出来

当这五条始终成立时，这个 app 就会是一套稳定、可信、可长期使用的个人资金工具。

