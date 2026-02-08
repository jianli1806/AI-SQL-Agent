package com.agent.service;



import com.agent.model.TableSchema;
import com.agent.model.TableSchema.ColumnInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Service
public class DatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 获取数据库中所有表名
     */
    public List<String> showTables() {
        try {
            logger.debug("获取所有表名");
            String sql = "SHOW TABLES";
            List<String> tables = jdbcTemplate.queryForList(sql, String.class);
            logger.debug("找到 {} 个表: {}", tables.size(), tables);
            return tables;
        } catch (Exception e) {
            logger.error("获取表列表失败", e);
            throw new RuntimeException("获取表列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取指定表的详细结构信息
     */
    public TableSchema describeTable(String tableName) {
        try {
            logger.debug("获取表 {} 的结构信息", tableName);

            TableSchema schema = new TableSchema();
            schema.setTableName(tableName);

            // 获取列信息
            List<ColumnInfo> columns = getTableColumns(tableName);
            schema.setColumns(columns);

            // 获取主键信息
            List<String> primaryKeys = getTablePrimaryKeys(tableName);
            schema.setPrimaryKeys(primaryKeys);

            // 获取外键信息
            Map<String, String> foreignKeys = getTableForeignKeys(tableName);
            schema.setForeignKeys(foreignKeys);

            logger.debug("表 {} 包含 {} 列, {} 个主键, {} 个外键",
                    tableName, columns.size(), primaryKeys.size(), foreignKeys.size());

            return schema;
        } catch (Exception e) {
            logger.error("获取表 {} 结构失败", tableName, e);
            throw new RuntimeException("获取表结构失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行查询SQL并返回结果
     */
    public List<Map<String, Object>> executeQuery(String sql) {
        try {
            logger.debug("执行查询SQL: {}", sql);

            // 添加LIMIT限制（如果SQL中没有LIMIT子句）
            String limitedSql = addLimitIfNeeded(sql, 1000);

            long startTime = System.currentTimeMillis();
            List<Map<String, Object>> result = jdbcTemplate.queryForList(limitedSql);
            long endTime = System.currentTimeMillis();

            logger.debug("查询完成，返回 {} 行数据，耗时 {}ms", result.size(), (endTime - startTime));
            return result;

        } catch (Exception e) {
            logger.error("执行查询失败: {}", sql, e);
            throw new RuntimeException("执行查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行EXPLAIN分析查询性能
     */
    public List<Map<String, Object>> explainQuery(String sql) {
        try {
            logger.debug("执行EXPLAIN分析: {}", sql);
            String explainSql = "EXPLAIN " + sql;
            return jdbcTemplate.queryForList(explainSql);
        } catch (Exception e) {
            logger.error("执行EXPLAIN失败: {}", sql, e);
            throw new RuntimeException("执行EXPLAIN失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证SQL语法（通过EXPLAIN实现）
     */
    public boolean validateSqlSyntax(String sql) {
        try {
            explainQuery(sql);
            return true;
        } catch (Exception e) {
            logger.debug("SQL语法验证失败: {}", e.getMessage());
            return false;
        }
    }

    // =================== 私有方法 ===================

    /**
     * 获取表的列信息
     */
    private List<ColumnInfo> getTableColumns(String tableName) {
        String sql = "SELECT " +
                "COLUMN_NAME, " +
                "DATA_TYPE, " +
                "IS_NULLABLE, " +
                "COLUMN_DEFAULT, " +
                "COLUMN_COMMENT " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION";

        return jdbcTemplate.query(sql, new Object[]{tableName}, (rs, rowNum) -> {
            ColumnInfo column = new ColumnInfo();
            column.setColumnName(rs.getString("COLUMN_NAME"));
            column.setDataType(rs.getString("DATA_TYPE"));
            column.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
            column.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
            column.setComment(rs.getString("COLUMN_COMMENT"));
            return column;
        });
    }

    /**
     * 获取表的主键信息
     */
    private List<String> getTablePrimaryKeys(String tableName) {
        String sql = "SELECT COLUMN_NAME " +
                "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                "WHERE TABLE_SCHEMA = DATABASE() " +
                "AND TABLE_NAME = ? " +
                "AND CONSTRAINT_NAME = 'PRIMARY' " +
                "ORDER BY ORDINAL_POSITION";

        return jdbcTemplate.queryForList(sql, new Object[]{tableName}, String.class);
    }

    /**
     * 获取表的外键信息
     */
    private Map<String, String> getTableForeignKeys(String tableName) {
        String sql = "SELECT " +
                "COLUMN_NAME, " +
                "REFERENCED_TABLE_NAME, " +
                "REFERENCED_COLUMN_NAME " +
                "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                "WHERE TABLE_SCHEMA = DATABASE() " +
                "AND TABLE_NAME = ? " +
                "AND REFERENCED_TABLE_NAME IS NOT NULL";

        Map<String, String> foreignKeys = new HashMap<>();

        jdbcTemplate.query(sql, new Object[]{tableName}, (rs) -> {
            String columnName = rs.getString("COLUMN_NAME");
            String refTable = rs.getString("REFERENCED_TABLE_NAME");
            String refColumn = rs.getString("REFERENCED_COLUMN_NAME");
            foreignKeys.put(columnName, refTable + "." + refColumn);
        });

        return foreignKeys;
    }

    /**
     * 为SQL添加LIMIT限制（如果没有的话）
     */
    private String addLimitIfNeeded(String sql, int maxRows) {
        String upperSql = sql.toUpperCase().trim();

        // 如果已经有LIMIT子句，直接返回
        if (upperSql.contains("LIMIT")) {
            return sql;
        }

        // 只对SELECT语句添加LIMIT
        if (upperSql.startsWith("SELECT")) {
            return sql + " LIMIT " + maxRows;
        }

        return sql;
    }
}