// QueryPlannerService.java
package com.agent.service;

import com.agent.model.TableSchema;
import com.agent.utils.NaturalLanguageProcessor;
import com.agent.utils.NaturalLanguageProcessor.QueryIntent;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class QueryPlannerService {

    private static final Logger logger = LoggerFactory.getLogger(QueryPlannerService.class);

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private NaturalLanguageProcessor nlpProcessor;

    /**
     * 根据用户查询生成SQL
     */
    public SqlGenerationResult generateSql(String userQuery) {
        try {
            logger.debug("开始生成SQL, 用户查询: {}", userQuery);

            // 1. 获取所有表信息
            List<String> allTables = databaseService.showTables();
            List<TableSchema> schemas = new ArrayList<>();
            for (String tableName : allTables) {
                schemas.add(databaseService.describeTable(tableName));
            }

            // 2. 分析用户意图
            QueryIntent intent = nlpProcessor.analyzeIntent(userQuery, schemas);

            // 3. 生成SQL
            String sql = buildSql(intent, schemas);

            // 4. 生成解释说明
            String explanation = generateExplanation(intent, sql);

            logger.debug("SQL生成完成: {}", sql);
            return SqlGenerationResult.success(sql, explanation, intent);

        } catch (Exception e) {
            logger.error("SQL生成失败", e);
            return SqlGenerationResult.error("SQL生成失败: " + e.getMessage());
        }
    }

    /**
     * 构建SQL语句
     */
    private String buildSql(QueryIntent intent, List<TableSchema> allSchemas) {
        if (intent.getRelevantTables().isEmpty()) {
            throw new RuntimeException("未找到相关表，请检查查询内容");
        }

        // 获取相关表的Schema信息
        Map<String, TableSchema> tableSchemaMap = allSchemas.stream()
                .collect(Collectors.toMap(TableSchema::getTableName, schema -> schema));

        List<TableSchema> relevantSchemas = intent.getRelevantTables().stream()
                .map(tableSchemaMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (relevantSchemas.isEmpty()) {
            throw new RuntimeException("相关表信息获取失败");
        }

        StringBuilder sql = new StringBuilder();

        // 1. 构建SELECT子句
        buildSelectClause(sql, intent, relevantSchemas);

        // 2. 构建FROM子句
        buildFromClause(sql, intent, relevantSchemas);

        // 3. 构建JOIN子句
        buildJoinClause(sql, intent, relevantSchemas);

        // 4. 构建WHERE子句
        buildWhereClause(sql, intent, relevantSchemas);

        // 5. 构建GROUP BY子句
        buildGroupByClause(sql, intent, relevantSchemas);

        // 6. 构建ORDER BY子句
        buildOrderByClause(sql, intent, relevantSchemas);

        // 7. 构建LIMIT子句
        buildLimitClause(sql, intent);

        return sql.toString().trim();
    }

    /**
     * 构建SELECT子句
     */
    private void buildSelectClause(StringBuilder sql, QueryIntent intent, List<TableSchema> schemas) {
        sql.append("SELECT ");

        List<String> selectFields = new ArrayList<>();

        // 如果有聚合函数
        if (!intent.getAggregations().isEmpty()) {
            for (String aggregation : intent.getAggregations()) {
                switch (aggregation) {
                    case "COUNT":
                        selectFields.add("COUNT(*) as total_count");
                        break;
                    case "SUM":
                        // 查找数值字段进行求和
                        String numericField = findNumericField(schemas, Arrays.asList("amount", "price", "total", "value", "money"));
                        if (numericField != null) {
                            selectFields.add("SUM(" + numericField + ") as total_sum");
                        }
                        break;
                    case "AVG":
                        numericField = findNumericField(schemas, Arrays.asList("amount", "price", "total", "value", "score"));
                        if (numericField != null) {
                            selectFields.add("AVG(" + numericField + ") as average_value");
                        }
                        break;
                    case "MAX":
                        numericField = findNumericField(schemas, Arrays.asList("amount", "price", "total", "value", "score"));
                        if (numericField != null) {
                            selectFields.add("MAX(" + numericField + ") as max_value");
                        }
                        break;
                    case "MIN":
                        numericField = findNumericField(schemas, Arrays.asList("amount", "price", "total", "value", "score"));
                        if (numericField != null) {
                            selectFields.add("MIN(" + numericField + ") as min_value");
                        }
                        break;
                }
            }

            // 如果是分组查询，添加分组字段
            if (intent.getQueryTypes().contains("GROUP")) {
                String groupField = findGroupField(schemas);
                if (groupField != null) {
                    selectFields.add(0, groupField); // 添加到最前面
                }
            }
        } else {
            // 普通查询，选择主要字段
            selectFields.addAll(selectMainFields(schemas));
        }

        if (selectFields.isEmpty()) {
            selectFields.add("*");
        }

        sql.append(String.join(", ", selectFields));
    }

    /**
     * 构建FROM子句
     */
    private void buildFromClause(StringBuilder sql, QueryIntent intent, List<TableSchema> schemas) {
        sql.append(" FROM ").append(schemas.get(0).getTableName());
    }

    /**
     * 构建JOIN子句
     */
    private void buildJoinClause(StringBuilder sql, QueryIntent intent, List<TableSchema> schemas) {
        if (schemas.size() <= 1) {
            return;
        }

        TableSchema mainTable = schemas.get(0);

        for (int i = 1; i < schemas.size(); i++) {
            TableSchema joinTable = schemas.get(i);
            String joinCondition = findJoinCondition(mainTable, joinTable);

            if (joinCondition != null) {
                sql.append(" LEFT JOIN ").append(joinTable.getTableName())
                        .append(" ON ").append(joinCondition);
            }
        }
    }

    /**
     * 构建WHERE子句
     */
    private void buildWhereClause(StringBuilder sql, QueryIntent intent, List<TableSchema> schemas) {
        List<String> whereConditions = new ArrayList<>();

        // 添加时间范围条件
        if (StringUtils.isNotBlank(intent.getTimeRange())) {
            whereConditions.add(intent.getTimeRange());
        }

        // 添加其他条件
        for (String condition : intent.getConditions()) {
            if (condition.startsWith("条件: ") || condition.startsWith("范围: ")) {
                String actualCondition = condition.substring(condition.indexOf(": ") + 2);
                // 这里需要根据实际字段名调整条件
                String field = findRelevantField(schemas, actualCondition);
                if (field != null) {
                    whereConditions.add(field + " " + actualCondition);
                }
            }
        }

        if (!whereConditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereConditions));
        }
    }

    /**
     * 构建GROUP BY子句
     */
    private void buildGroupByClause(StringBuilder sql, QueryIntent intent, List<TableSchema> schemas) {
        if (intent.getQueryTypes().contains("GROUP") && !intent.getAggregations().isEmpty()) {
            String groupField = findGroupField(schemas);
            if (groupField != null) {
                sql.append(" GROUP BY ").append(groupField);
            }
        }
    }

    /**
     * 构建ORDER BY子句
     */
    private void buildOrderByClause(StringBuilder sql, QueryIntent intent, List<TableSchema> schemas) {
        if (StringUtils.isNotBlank(intent.getOrderDirection())) {
            String orderField = findOrderField(schemas, intent);
            if (orderField != null) {
                sql.append(" ORDER BY ").append(orderField).append(" ").append(intent.getOrderDirection());
            }
        }
    }

    /**
     * 构建LIMIT子句
     */
    private void buildLimitClause(StringBuilder sql, QueryIntent intent) {
        if (intent.getLimitCount() != null) {
            sql.append(" LIMIT ").append(intent.getLimitCount());
        }
    }

    // =================== 工具方法 ===================

    /**
     * 查找数值字段
     */
    private String findNumericField(List<TableSchema> schemas, List<String> preferredNames) {
        for (String preferred : preferredNames) {
            for (TableSchema schema : schemas) {
                for (TableSchema.ColumnInfo column : schema.getColumns()) {
                    if (column.getColumnName().toLowerCase().contains(preferred) &&
                            isNumericType(column.getDataType())) {
                        return schema.getTableName() + "." + column.getColumnName();
                    }
                }
            }
        }

        // 如果没找到首选字段，返回第一个数值字段
        for (TableSchema schema : schemas) {
            for (TableSchema.ColumnInfo column : schema.getColumns()) {
                if (isNumericType(column.getDataType())) {
                    return schema.getTableName() + "." + column.getColumnName();
                }
            }
        }

        return null;
    }

    /**
     * 判断是否为数值类型
     */
    private boolean isNumericType(String dataType) {
        String lowerType = dataType.toLowerCase();
        return lowerType.contains("int") || lowerType.contains("decimal") ||
                lowerType.contains("float") || lowerType.contains("double") ||
                lowerType.contains("numeric");
    }

    /**
     * 查找分组字段
     */
    private String findGroupField(List<TableSchema> schemas) {
        // 优先查找名称字段
        List<String> preferredNames = Arrays.asList("name", "title", "category", "type", "status");

        for (String preferred : preferredNames) {
            for (TableSchema schema : schemas) {
                for (TableSchema.ColumnInfo column : schema.getColumns()) {
                    if (column.getColumnName().toLowerCase().contains(preferred)) {
                        return schema.getTableName() + "." + column.getColumnName();
                    }
                }
            }
        }

        return null;
    }

    /**
     * 选择主要字段
     */
    private List<String> selectMainFields(List<TableSchema> schemas) {
        List<String> fields = new ArrayList<>();

        for (TableSchema schema : schemas) {
            // 添加主键字段
            for (String pk : schema.getPrimaryKeys()) {
                fields.add(schema.getTableName() + "." + pk);
            }

            // 添加重要字段
            for (TableSchema.ColumnInfo column : schema.getColumns()) {
                String columnName = column.getColumnName().toLowerCase();
                if (columnName.contains("name") || columnName.contains("title") ||
                        columnName.contains("description") || columnName.contains("status")) {
                    fields.add(schema.getTableName() + "." + column.getColumnName());
                }
            }
        }

        // 如果没有找到重要字段，选择前几个字段
        if (fields.isEmpty() && !schemas.isEmpty()) {
            TableSchema firstSchema = schemas.get(0);
            int maxFields = Math.min(5, firstSchema.getColumns().size());
            for (int i = 0; i < maxFields; i++) {
                fields.add(firstSchema.getTableName() + "." +
                        firstSchema.getColumns().get(i).getColumnName());
            }
        }

        return fields;
    }

    /**
     * 查找JOIN条件
     */
    private String findJoinCondition(TableSchema mainTable, TableSchema joinTable) {
        // 检查外键关系
        for (Map.Entry<String, String> fk : mainTable.getForeignKeys().entrySet()) {
            if (fk.getValue().startsWith(joinTable.getTableName() + ".")) {
                return mainTable.getTableName() + "." + fk.getKey() + " = " + fk.getValue();
            }
        }

        // 检查反向外键关系
        for (Map.Entry<String, String> fk : joinTable.getForeignKeys().entrySet()) {
            if (fk.getValue().startsWith(mainTable.getTableName() + ".")) {
                return joinTable.getTableName() + "." + fk.getKey() + " = " + fk.getValue();
            }
        }

        // 查找同名字段
        for (TableSchema.ColumnInfo mainCol : mainTable.getColumns()) {
            for (TableSchema.ColumnInfo joinCol : joinTable.getColumns()) {
                if (mainCol.getColumnName().equals(joinCol.getColumnName()) &&
                        (mainCol.getColumnName().toLowerCase().contains("id") ||
                                mainCol.getColumnName().toLowerCase().endsWith("_id"))) {
                    return mainTable.getTableName() + "." + mainCol.getColumnName() +
                            " = " + joinTable.getTableName() + "." + joinCol.getColumnName();
                }
            }
        }

        return null;
    }

    /**
     * 查找相关字段
     */
    private String findRelevantField(List<TableSchema> schemas, String condition) {
        // 简化实现，返回第一个数值字段
        return findNumericField(schemas, Arrays.asList("amount", "price", "total", "value"));
    }

    /**
     * 查找排序字段
     */
    private String findOrderField(List<TableSchema> schemas, QueryIntent intent) {
        // 如果有聚合函数，按聚合结果排序
        if (!intent.getAggregations().isEmpty()) {
            return findNumericField(schemas, Arrays.asList("amount", "price", "total", "value"));
        }

        // 否则按时间字段排序
        for (TableSchema schema : schemas) {
            for (TableSchema.ColumnInfo column : schema.getColumns()) {
                String columnName = column.getColumnName().toLowerCase();
                if (columnName.contains("time") || columnName.contains("date") ||
                        columnName.contains("created") || columnName.contains("updated")) {
                    return schema.getTableName() + "." + column.getColumnName();
                }
            }
        }

        return null;
    }

    /**
     * 生成解释说明
     */

    private String generateExplanation(QueryIntent intent, String sql) {
        StringBuilder explanation = new StringBuilder();

        explanation.append("根据您的查询\"").append(intent.getOriginalQuery()).append("\"，我理解您想要：\n");

        if (!intent.getAggregations().isEmpty()) {
            explanation.append("- 计算统计信息：").append(String.join(", ", intent.getAggregations())).append("\n");
        }

        if (!intent.getRelevantTables().isEmpty()) {
            explanation.append("- 从表中查询：").append(String.join(", ", intent.getRelevantTables())).append("\n");
        }

        if (intent.getTimeRange() != null) {
            explanation.append("- 时间范围限制\n");
        }

        if (intent.getLimitCount() != null) {
            explanation.append("- 限制返回").append(intent.getLimitCount()).append("条记录\n");
        }

        explanation.append("\n生成的SQL语句：\n").append(sql);

        return explanation.toString();
    }

    /**
     * SQL生成结果类
     */
    public static class SqlGenerationResult {
        private boolean success;
        private String generatedSql;
        private String explanation;
        private String errorMessage;
        private QueryIntent intent;

        private SqlGenerationResult(boolean success) {
            this.success = success;
        }

        public static SqlGenerationResult success(String sql, String explanation, QueryIntent intent) {
            SqlGenerationResult result = new SqlGenerationResult(true);
            result.generatedSql = sql;
            result.explanation = explanation;
            result.intent = intent;
            return result;
        }

        public static SqlGenerationResult error(String errorMessage) {
            SqlGenerationResult result = new SqlGenerationResult(false);
            result.errorMessage = errorMessage;
            return result;
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public String getGeneratedSql() {
            return generatedSql;
        }

        public String getExplanation() {
            return explanation;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public QueryIntent getIntent() {
            return intent;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public void setGeneratedSql(String generatedSql) {
            this.generatedSql = generatedSql;
        }

        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public void setIntent(QueryIntent intent) {
            this.intent = intent;
        }

        @Override
        public String toString() {
            return "SqlGenerationResult{" +
                    "success=" + success +
                    ", generatedSql='" + generatedSql + '\'' +
                    ", explanation='" + explanation + '\'' +
                    ", errorMessage='" + errorMessage + '\'' +
                    '}';
        }
    }

} // QueryPlannerService类结束