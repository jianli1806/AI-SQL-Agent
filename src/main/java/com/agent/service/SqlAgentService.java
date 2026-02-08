package com.agent.service;

import com.agent.model.QueryRequest;
import com.agent.model.QueryResponse;
import com.agent.utils.SqlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SqlAgentService {

    private static final Logger logger = LoggerFactory.getLogger(SqlAgentService.class);

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private OllamaService ollamaService;

    @Autowired
    private SqlValidator sqlValidator;

    @Value("${agent.use-ai:true}")
    private boolean useAI;

    /**
     * 处理用户查询 - AI增强版
     */
    public QueryResponse processQuery(QueryRequest request) {
        try {
            logger.debug("开始处理查询: {}", request.getUserQuery());

            String sql;
            String explanation;

            if (useAI && ollamaService.isAvailable()) {
                // 使用AI生成SQL
                sql = generateSQLWithAI(request.getUserQuery());
                explanation = "通过AI模型理解您的查询: " + request.getUserQuery();
            } else {
                // 降级到规则匹配
                logger.warn("AI不可用，使用规则匹配模式");
                return processQueryWithRules(request);
            }

            // 验证SQL安全性
            SqlValidator.ValidationResult validation = sqlValidator.validateSql(sql);

            if (!validation.isValid()) {
                return QueryResponse.error(validation.getErrorMessage());
            }

            // 如果需要确认，返回确认请求
            if (validation.isNeedsConfirmation()) {
                return QueryResponse.requireConfirmation(sql, explanation);
            }

            // 执行SQL
            long startTime = System.currentTimeMillis();
            List<Map<String, Object>> data = databaseService.executeQuery(sql);
            long endTime = System.currentTimeMillis();

            // 返回结果
            QueryResponse response = QueryResponse.success(data, sql);
            response.setExplanation(explanation);
            response.setExecutionTimeMs(endTime - startTime);

            logger.debug("AI查询完成，耗时: {}ms", endTime - startTime);
            return response;

        } catch (Exception e) {
            logger.error("处理查询失败", e);
            return QueryResponse.error("处理查询失败: " + e.getMessage());
        }
    }

    /**
     * 使用AI生成SQL
     */
    private String generateSQLWithAI(String userQuery) {
        // 1. 获取数据库schema信息
        String schemaContext = buildSchemaContext();

        // 2. 调用AI生成SQL
        String generatedSQL = ollamaService.generateSQL(userQuery, schemaContext);

        // 3. 清理和验证生成的SQL
        generatedSQL = cleanGeneratedSQL(generatedSQL);

        return generatedSQL;
    }

    /**
     * 构建数据库schema上下文
     */
    private String buildSchemaContext() {
        try {
            List<String> tableNames = databaseService.showTables();
            StringBuilder schemaBuilder = new StringBuilder();

            for (String tableName : tableNames) {
                try {
                    List<Map<String, Object>> columns = databaseService.executeQuery("DESCRIBE " + tableName);
                    schemaBuilder.append("表名: ").append(tableName).append("\n");

                    for (Map<String, Object> column : columns) {
                        schemaBuilder.append("  ")
                                .append(column.get("Field"))
                                .append(" (")
                                .append(column.get("Type"))
                                .append(")");

                        if ("PRI".equals(column.get("Key"))) {
                            schemaBuilder.append(" [主键]");
                        }
                        if ("YES".equals(column.get("Null"))) {
                            schemaBuilder.append(" [可为空]");
                        }

                        schemaBuilder.append("\n");
                    }
                    schemaBuilder.append("\n");

                } catch (Exception e) {
                    logger.warn("获取表 {} 的结构失败: {}", tableName, e.getMessage());
                }
            }

            return schemaBuilder.toString();

        } catch (Exception e) {
            logger.error("构建schema上下文失败", e);
            return "数据库表: users(用户表), products(商品表), orders(订单表), order_items(订单详情表), sales_summary(销售统计表)";
        }
    }

    /**
     * 清理生成的SQL
     */
    private String cleanGeneratedSQL(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new RuntimeException("AI生成的SQL为空");
        }

        String cleaned = sql.trim()
                .replaceAll("```sql", "")
                .replaceAll("```", "")
                .replaceAll(";+$", "");  // 移除末尾分号

        // 确保有LIMIT子句
        if (cleaned.toUpperCase().contains("SELECT") && !cleaned.toUpperCase().contains("LIMIT")) {
            cleaned += " LIMIT 100";
        }

        return cleaned;
    }

    /**
     * 规则匹配模式（作为降级方案）
     */
    private QueryResponse processQueryWithRules(QueryRequest request) {
        // 这里可以保留原有的规则匹配逻辑作为降级方案
        if (request.getUserQuery().contains("用户")) {
            String sql = "SELECT * FROM users LIMIT 10";
            List<Map<String, Object>> data = databaseService.executeQuery(sql);
            return QueryResponse.success(data, sql);
        } else if (request.getUserQuery().contains("商品")) {
            String sql = "SELECT * FROM products LIMIT 10";
            List<Map<String, Object>> data = databaseService.executeQuery(sql);
            return QueryResponse.success(data, sql);
        } else {
            return QueryResponse.error("无法理解查询内容，请尝试更具体的描述");
        }
    }

    // 保留原有的其他方法...
    public QueryResponse getTables() {
        try {
            List<String> tables = databaseService.showTables();
            Map<String, Object> result = new HashMap<>();
            result.put("tables", tables);
            result.put("count", tables.size());

            QueryResponse response = new QueryResponse(true, "获取表列表成功");
            response.setData(Arrays.asList(result));
            return response;

        } catch (Exception e) {
            logger.error("获取表列表失败", e);
            return QueryResponse.error("获取表列表失败: " + e.getMessage());
        }
    }

    public QueryResponse getTableSchema(String tableName) {
        try {
            String sql = "DESCRIBE " + tableName;
            List<Map<String, Object>> data = databaseService.executeQuery(sql);

            QueryResponse response = QueryResponse.success(data, sql);
            response.setExplanation("表 " + tableName + " 的结构信息");
            return response;

        } catch (Exception e) {
            logger.error("获取表结构失败", e);
            return QueryResponse.error("获取表结构失败: " + e.getMessage());
        }
    }
}