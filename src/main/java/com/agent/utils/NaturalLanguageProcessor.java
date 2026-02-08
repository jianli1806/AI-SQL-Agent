// NaturalLanguageProcessor.java
package com.agent.utils;

import com.agent.model.TableSchema;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NaturalLanguageProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NaturalLanguageProcessor.class);

    // 查询意图模式
    private static final Map<String, Pattern> INTENT_PATTERNS = new HashMap<>();

    // 聚合函数模式
    private static final Map<String, String> AGGREGATION_PATTERNS = new HashMap<>();

    // 时间范围模式
    private static final Map<String, String> TIME_PATTERNS = new HashMap<>();

    // 排序模式
    private static final Map<String, String> ORDER_PATTERNS = new HashMap<>();

    static {
        // 查询意图模式
        INTENT_PATTERNS.put("SELECT", Pattern.compile("(查询|查找|显示|列出|获取|统计|分析)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("COUNT", Pattern.compile("(数量|个数|总数|计数|有多少)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("SUM", Pattern.compile("(总和|合计|总计|总额|汇总)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("AVG", Pattern.compile("(平均|均值|平均值)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("MAX", Pattern.compile("(最大|最高|最多|最大值)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("MIN", Pattern.compile("(最小|最低|最少|最小值)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("GROUP", Pattern.compile("(分组|按.*分组|每.*的)", Pattern.CASE_INSENSITIVE));

        // 聚合函数映射
        AGGREGATION_PATTERNS.put("数量|个数|总数|计数|有多少", "COUNT");
        AGGREGATION_PATTERNS.put("总和|合计|总计|总额|汇总", "SUM");
        AGGREGATION_PATTERNS.put("平均|均值|平均值", "AVG");
        AGGREGATION_PATTERNS.put("最大|最高|最多|最大值", "MAX");
        AGGREGATION_PATTERNS.put("最小|最低|最少|最小值", "MIN");

        // 时间范围模式
        TIME_PATTERNS.put("今天", "DATE(created_at) = CURDATE()");
        TIME_PATTERNS.put("昨天", "DATE(created_at) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)");
        TIME_PATTERNS.put("本周", "YEARWEEK(created_at) = YEARWEEK(NOW())");
        TIME_PATTERNS.put("上周", "YEARWEEK(created_at) = YEARWEEK(NOW()) - 1");
        TIME_PATTERNS.put("本月", "DATE_FORMAT(created_at, '%Y-%m') = DATE_FORMAT(NOW(), '%Y-%m')");
        TIME_PATTERNS.put("上月|上个月", "DATE_FORMAT(created_at, '%Y-%m') = DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 1 MONTH), '%Y-%m')");
        TIME_PATTERNS.put("今年", "YEAR(created_at) = YEAR(NOW())");
        TIME_PATTERNS.put("去年", "YEAR(created_at) = YEAR(NOW()) - 1");

        // 排序模式
        ORDER_PATTERNS.put("升序|从小到大|从低到高", "ASC");
        ORDER_PATTERNS.put("降序|从大到小|从高到低", "DESC");
        ORDER_PATTERNS.put("前.*名|前.*个|top", "DESC");
    }

    /**
     * 分析用户查询意图
     */
    public QueryIntent analyzeIntent(String userQuery, List<TableSchema> availableSchemas) {
        logger.debug("分析查询意图: {}", userQuery);

        QueryIntent intent = new QueryIntent();
        intent.setOriginalQuery(userQuery);

        // 1. 识别查询类型
        identifyQueryType(userQuery, intent);

        // 2. 识别相关表
        identifyRelevantTables(userQuery, availableSchemas, intent);

        // 3. 识别字段和条件
        identifyFieldsAndConditions(userQuery, intent);

        // 4. 识别聚合函数
        identifyAggregations(userQuery, intent);

        // 5. 识别时间范围
        identifyTimeRange(userQuery, intent);

        // 6. 识别排序和限制
        identifyOrderAndLimit(userQuery, intent);

        logger.debug("意图分析完成: {}", intent);
        return intent;
    }

    /**
     * 识别查询类型
     */
    private void identifyQueryType(String query, QueryIntent intent) {
        for (Map.Entry<String, Pattern> entry : INTENT_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(query).find()) {
                intent.getQueryTypes().add(entry.getKey());
            }
        }

        // 默认为SELECT查询
        if (intent.getQueryTypes().isEmpty()) {
            intent.getQueryTypes().add("SELECT");
        }
    }

    /**
     * 识别相关表
     */
    private void identifyRelevantTables(String query, List<TableSchema> schemas, QueryIntent intent) {
        Map<String, Double> tableRelevance = new HashMap<>();

        for (TableSchema schema : schemas) {
            double relevance = calculateTableRelevance(query, schema);
            if (relevance > 0) {
                tableRelevance.put(schema.getTableName(), relevance);
            }
        }

        // 按相关度排序，取最相关的表
        List<Map.Entry<String, Double>> sortedTables = new ArrayList<>(tableRelevance.entrySet());
        sortedTables.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        for (Map.Entry<String, Double> entry : sortedTables) {
            intent.getRelevantTables().add(entry.getKey());
            logger.debug("表 {} 相关度: {}", entry.getKey(), entry.getValue());
        }
    }

    /**
     * 计算表的相关度
     */
    private double calculateTableRelevance(String query, TableSchema schema) {
        double relevance = 0.0;
        String lowerQuery = query.toLowerCase();
        String tableName = schema.getTableName().toLowerCase();

        // 表名匹配
        if (lowerQuery.contains(tableName)) {
            relevance += 10.0;
        }

        // 表名部分匹配
        String[] tableParts = tableName.split("_");
        for (String part : tableParts) {
            if (lowerQuery.contains(part)) {
                relevance += 3.0;
            }
        }

        // 列名匹配
        for (TableSchema.ColumnInfo column : schema.getColumns()) {
            String columnName = column.getColumnName().toLowerCase();
            if (lowerQuery.contains(columnName)) {
                relevance += 2.0;
            }

            // 列名部分匹配
            String[] columnParts = columnName.split("_");
            for (String part : columnParts) {
                if (lowerQuery.contains(part)) {
                    relevance += 1.0;
                }
            }
        }

        return relevance;
    }

    /**
     * 识别字段和条件
     */
    private void identifyFieldsAndConditions(String query, QueryIntent intent) {
        // 提取数字
        Pattern numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");
        Matcher numberMatcher = numberPattern.matcher(query);
        while (numberMatcher.find()) {
            intent.getConditions().add("数字: " + numberMatcher.group());
        }

        // 提取比较条件
        Pattern comparePattern = Pattern.compile("(大于|小于|等于|超过|低于|不少于|不超过)\\s*(\\d+)");
        Matcher compareMatcher = comparePattern.matcher(query);
        while (compareMatcher.find()) {
            String operator = compareMatcher.group(1);
            String value = compareMatcher.group(2);
            String sqlOperator = convertToSqlOperator(operator);
            intent.getConditions().add(String.format("条件: %s %s", sqlOperator, value));
        }

        // 提取范围条件
        Pattern rangePattern = Pattern.compile("(\\d+)\\s*到\\s*(\\d+)");
        Matcher rangeMatcher = rangePattern.matcher(query);
        while (rangeMatcher.find()) {
            String start = rangeMatcher.group(1);
            String end = rangeMatcher.group(2);
            intent.getConditions().add(String.format("范围: BETWEEN %s AND %s", start, end));
        }
    }

    /**
     * 识别聚合函数
     */
    private void identifyAggregations(String query, QueryIntent intent) {
        for (Map.Entry<String, String> entry : AGGREGATION_PATTERNS.entrySet()) {
            Pattern pattern = Pattern.compile(entry.getKey());
            if (pattern.matcher(query).find()) {
                intent.getAggregations().add(entry.getValue());
            }
        }
    }

    /**
     * 识别时间范围
     */
    private void identifyTimeRange(String query, QueryIntent intent) {
        for (Map.Entry<String, String> entry : TIME_PATTERNS.entrySet()) {
            Pattern pattern = Pattern.compile(entry.getKey());
            if (pattern.matcher(query).find()) {
                intent.setTimeRange(entry.getValue());
                break; // 只取第一个匹配的时间范围
            }
        }
    }

    /**
     * 识别排序和限制
     */
    private void identifyOrderAndLimit(String query, QueryIntent intent) {
        // 识别排序
        for (Map.Entry<String, String> entry : ORDER_PATTERNS.entrySet()) {
            Pattern pattern = Pattern.compile(entry.getKey());
            if (pattern.matcher(query).find()) {
                intent.setOrderDirection(entry.getValue());
                break;
            }
        }

        // 识别限制数量
        Pattern limitPattern = Pattern.compile("前\\s*(\\d+)\\s*[名个条]");
        Matcher limitMatcher = limitPattern.matcher(query);
        if (limitMatcher.find()) {
            intent.setLimitCount(Integer.parseInt(limitMatcher.group(1)));
        }
    }

    /**
     * 转换中文比较操作符为SQL操作符
     */
    private String convertToSqlOperator(String chineseOperator) {
        switch (chineseOperator) {
            case "大于":
            case "超过":
                return ">";
            case "小于":
            case "低于":
                return "<";
            case "等于":
                return "=";
            case "不少于":
                return ">=";
            case "不超过":
                return "<=";
            default:
                return "=";
        }
    }

    /**
     * 查询意图类
     */
    public static class QueryIntent {
        private String originalQuery;
        private List<String> queryTypes = new ArrayList<>();
        private List<String> relevantTables = new ArrayList<>();
        private List<String> targetFields = new ArrayList<>();
        private List<String> conditions = new ArrayList<>();
        private List<String> aggregations = new ArrayList<>();
        private String timeRange;
        private String orderDirection;
        private Integer limitCount;

        // Getters and Setters
        public String getOriginalQuery() {
            return originalQuery;
        }

        public void setOriginalQuery(String originalQuery) {
            this.originalQuery = originalQuery;
        }

        public List<String> getQueryTypes() {
            return queryTypes;
        }

        public void setQueryTypes(List<String> queryTypes) {
            this.queryTypes = queryTypes;
        }

        public List<String> getRelevantTables() {
            return relevantTables;
        }

        public void setRelevantTables(List<String> relevantTables) {
            this.relevantTables = relevantTables;
        }

        public List<String> getTargetFields() {
            return targetFields;
        }

        public void setTargetFields(List<String> targetFields) {
            this.targetFields = targetFields;
        }

        public List<String> getConditions() {
            return conditions;
        }

        public void setConditions(List<String> conditions) {
            this.conditions = conditions;
        }

        public List<String> getAggregations() {
            return aggregations;
        }

        public void setAggregations(List<String> aggregations) {
            this.aggregations = aggregations;
        }

        public String getTimeRange() {
            return timeRange;
        }

        public void setTimeRange(String timeRange) {
            this.timeRange = timeRange;
        }

        public String getOrderDirection() {
            return orderDirection;
        }

        public void setOrderDirection(String orderDirection) {
            this.orderDirection = orderDirection;
        }

        public Integer getLimitCount() {
            return limitCount;
        }

        public void setLimitCount(Integer limitCount) {
            this.limitCount = limitCount;
        }

        @Override
        public String toString() {
            return "QueryIntent{" +
                    "originalQuery='" + originalQuery + '\'' +
                    ", queryTypes=" + queryTypes +
                    ", relevantTables=" + relevantTables +
                    ", aggregations=" + aggregations +
                    ", timeRange='" + timeRange + '\'' +
                    ", orderDirection='" + orderDirection + '\'' +
                    ", limitCount=" + limitCount +
                    '}';
        }
    }
}