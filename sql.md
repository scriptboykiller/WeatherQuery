对，就在 sql-inventory.csv 里找到这个 searchByFilter 对应的原记录修改，不要新增重复记录。

你现在这个 CSV 看起来没有 resolvedSqlText 列，所以这样填：

字段	填写内容

sqlText	完整 SQL
normalizedSqlText	同一条完整 SQL，保持一行
parameterMode	POSITIONAL
parameterNames	subdomainName,ownerName,ownerPsid
parameterCount	3
isDynamicSql	FALSE
requiresManualReview	FALSE
manualReviewReason	清空
confidence	HIGH
notes	Manually resolved from dynamic StringBuilder using maximum-condition branch.


SQL 填这个：

SELECT * FROM DATA_DOMAIN_OWNERSHIP WHERE current_indicator = 1 AND LOWER(subdomain_name) LIKE ? AND LOWER(owner_name) = ? AND LOWER(owner_psid) = ? AND deprecated = 0

参数顺序必须是：

subdomainName,ownerName,ownerPsid

注意两点：

1. deprecated = 0 不是参数，因此 parameterCount 仍然是 3。


2. 修改前先备份原 CSV；Excel 保存时选择 CSV UTF-8，不要另存成普通 Excel 工作簿。



这样下一次运行 validation-explain 时，这条就不会再作为未解决的动态 SQL 跳过，而会进入真实 PostgreSQL 校验。
