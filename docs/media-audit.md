# 素材修改记录

media_change_log 是只追加的字段明细表。每条记录有 asset_id、operator_id、operator_name、asset_title、operation_type、field_name、old_value、new_value、old_label、new_label、created_at。操作人与名称来自 JwtAuthenticationFilter 查询得到的 CurrentUser，不使用客户端 operatorId。日志接口只有 GET，没有修改 / 删除接口。

CREATE 记录 title、description、category_id、file_id、status 的初始值；UPDATE 仅记录实际变化的 title / description / category_id；ARCHIVE 记录状态 ACTIVE → ARCHIVED。提交相同值不生成虚假 UPDATE，不改变版本号。标题先 trim 再比较；描述按原文比较并保留换行；分类 null 明确表示未分类。

编辑先 SELECT FOR UPDATE 锁素材行，以数据库旧状态为基准，校验当前身份是否 ADMIN 或素材创建者 EDITOR，再校验客户端 version。旧版本返回 409，防止同时编辑覆盖。记录 old / new 后更新素材并增加 version。日志和素材更新处于同一 Spring 事务；任何数据库失败一起回滚。上传的文件清理使用事务完成回调。测试通过模拟实际更新失败验证日志不残留，未用独立异步日志事务。

分类字段存原始 ID 的字符串及当时分类名快照。前端优先显示快照并同时展示 ID，保留准确追溯；后续分类改名不修改历史名称。操作人姓名和素材标题也保留记录时快照，避免关联记录更名后历史含义消失。普通列表显示实时分类名称，不与历史快照混淆。

/api/media/changes 支持 page / size / assetId / operatorId / from / to，时间范围为日志创建时间，包含两端。素材详情复用同一查询并绑定 assetId。ADMIN 可查所有正常和归档素材历史；普通用户仅可查 ACTIVE 素材历史，显式查询归档 ID 返回 403。当前正常素材全员可读的策略只是 Stage 2 临时边界。

审计明细与素材、操作人采用 RESTRICT 外键；分类 ID 作为历史值而非外键，所以合法删除无关联分类不会破坏审计。当前没有提供日志清理业务，测试仅清理本次生成的临时资源；正式保留周期、归档与合规要求后续另行设计。
