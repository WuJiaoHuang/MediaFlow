# Stage 4 反馈状态与授权设计

读取权限继承当前任务授权并集；反馈作者身份不保留任务读取权。媒体库 LIBRARY/RESTRICTED 均不能授予反馈或附件访问权。

|任务状态|创建|回复/处理/确认/重开|
|---|---|---|
|PENDING|禁止|禁止|
|PROCESSING|当前参与者、ADMIN|按动作授权|
|REVIEWING|当前审核人、ADMIN|按动作授权|
|COMPLETED|禁止|只读|

处理：当前执行人或 ADMIN；确认/重开：仍有任务授权的作者、当前审核人或 ADMIN。重开必须填写原因。CLOSED 先重开再回复。所有变更先锁任务再锁反馈，校验 version，同事务保存日志。

OPEN/REOPENED → RESOLVED → CLOSED；RESOLVED/CLOSED → REOPENED。PROCESSING 也允许确认，避免业务死锁。提交任务阻止 OPEN/REOPENED，ADMIN 例外必须说明原因并记录 SUBMIT_OVERRIDE。审批必须全部 CLOSED。退回任务不修改意见状态。

附件单独存表和接口，使用绑定 task/feedback/attachment 的 120 秒 HttpOnly Cookie；每次 Range、下载、续签均重新验证当前账号、角色、任务及父子关系。反馈引用的素材关系不可移除，历史关联和未解决数量不会隐失。归档素材不抹去已有反馈。

时间码仅允许关联音视频素材且非负；尚无可靠服务端时长，不声称验证上限。文件系统与 MySQL 无跨系统原子事务：回滚清理本次文件，进程崩溃仍可能产生孤立文件。

## 当前角色与动作矩阵

|主体|读取 / 附件 / 历史|提出意见|回复|标记已处理|确认 / 重开|
|---|---|---|---|---|---|
|ADMIN|全部任务|可写阶段按上述矩阵|可写阶段，非 CLOSED|可写阶段，OPEN / REOPENED|可写阶段，遵守反馈状态|
|当前 EDITOR 执行人|仅当前分配任务|PROCESSING|可写阶段，非 CLOSED|OPEN / REOPENED|仅自己提出且仍有任务授权的意见|
|当前 REVIEWER 审核人|仅当前指定任务|PROCESSING / REVIEWING|可写阶段，非 CLOSED|无执行人授权时不可|RESOLVED 确认；RESOLVED / CLOSED 带原因重开|
|旧参与者 / 无关用户|404，无作者或素材共享例外|404|404|404|404|

多角色读取取有效授权并集，动作仍按该具体动作所需身份。PENDING / COMPLETED 禁止所有主体写入。作者确认权也必须先通过当前任务读取授权。
