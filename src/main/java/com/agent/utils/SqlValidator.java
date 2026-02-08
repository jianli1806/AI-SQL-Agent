package com.agent.utils;



import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class SqlValidator {

    private static final Logger logger = LoggerFactory.getLogger(SqlValidator.class);

    @Value("${sql-agent.security.allow-dml:false}")
    private boolean allowDml;

    @Value("${sql-agent.security.allow-ddl:false}")
    private boolean allowDdl;

    @Value("#{'${sql-agent.security.dangerous-keywords}'.split(',')}")
    private List<String> dangerousKeywords;

    // 危险的SQL模式
    private static final List<Pattern> DANGEROUS_PATTERNS = Arrays.asList(
            Pattern.compile("--;.*", Pattern.CASE_INSENSITIVE), // SQL注释
            Pattern.compile(".*union.*select.*", Pattern.CASE_INSENSITIVE), // UNION注入
            Pattern.compile(".*or\\s+1\\s*=\\s*1.*", Pattern.CASE_INSENSITIVE), // 经典注入
            Pattern.compile(".*and\\s+1\\s*=\\s*1.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*exec\\s*\\(.*\\).*", Pattern.CASE_INSENSITIVE), // 动态执行
            Pattern.compile(".*xp_.*", Pattern.CASE_INSENSITIVE), // SQL Server扩展存储过程
            Pattern.compile(".*sp_.*", Pattern.CASE_INSENSITIVE) // SQL Server系统存储过程
    );

    // DML关键字
    private static final List<String> DML_KEYWORDS = Arrays.asList(
            "INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE"
    );

    // DDL关键字
    private static final List<String> DDL_KEYWORDS = Arrays.asList(
            "CREATE", "DROP", "ALTER", "TRUNCATE", "RENAME"
    );

    /**
     * 验证SQL安全性
     */
    public ValidationResult validateSql(String sql) {
        if (StringUtils.isBlank(sql)) {
            return ValidationResult.error("SQL不能为空");
        }

        String cleanSql = sql.trim();
        logger.debug("验证SQL安全性: {}", cleanSql);

        // 1. 检查SQL注入模式
        ValidationResult injectionResult = checkSqlInjection(cleanSql);
        if (!injectionResult.isValid()) {
            return injectionResult;
        }

        // 2. 检查危险关键字
        ValidationResult keywordResult = checkDangerousKeywords(cleanSql);
        if (!keywordResult.isValid()) {
            return keywordResult;
        }

        // 3. 检查DML/DDL权限
        ValidationResult permissionResult = checkPermissions(cleanSql);
        if (!permissionResult.isValid()) {
            return permissionResult;
        }

        // 4. 检查是否需要确认
        boolean needsConfirmation = needsConfirmation(cleanSql);

        logger.debug("SQL验证通过, 需要确认: {}", needsConfirmation);
        return ValidationResult.success(needsConfirmation);
    }

    /**
     * 检查SQL注入模式
     */
    private ValidationResult checkSqlInjection(String sql) {
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(sql).matches()) {
                logger.warn("检测到可能的SQL注入模式: {}", pattern.pattern());
                return ValidationResult.error("检测到可能的SQL注入模式，请检查SQL语句");
            }
        }
        return ValidationResult.success(false);
    }

    /**
     * 检查危险关键字
     */
    private ValidationResult checkDangerousKeywords(String sql) {
        String upperSql = sql.toUpperCase();

        for (String keyword : dangerousKeywords) {
            if (upperSql.contains(keyword.toUpperCase())) {
                logger.warn("检测到危险关键字: {}", keyword);
                return ValidationResult.error("包含危险关键字: " + keyword);
            }
        }
        return ValidationResult.success(false);
    }

    /**
     * 检查DML/DDL权限
     */
    private ValidationResult checkPermissions(String sql) {
        String upperSql = sql.toUpperCase().trim();

        // 检查DML权限
        if (!allowDml) {
            for (String keyword : DML_KEYWORDS) {
                if (upperSql.startsWith(keyword)) {
                    logger.warn("DML操作被禁用: {}", keyword);
                    return ValidationResult.error("DML操作已被禁用: " + keyword);
                }
            }
        }

        // 检查DDL权限
        if (!allowDdl) {
            for (String keyword : DDL_KEYWORDS) {
                if (upperSql.startsWith(keyword)) {
                    logger.warn("DDL操作被禁用: {}", keyword);
                    return ValidationResult.error("DDL操作已被禁用: " + keyword);
                }
            }
        }

        return ValidationResult.success(false);
    }

    /**
     * 判断是否需要人工确认
     */
    private boolean needsConfirmation(String sql) {
        String upperSql = sql.toUpperCase().trim();

        // DML操作需要确认
        for (String keyword : DML_KEYWORDS) {
            if (upperSql.startsWith(keyword)) {
                return true;
            }
        }

        // DDL操作需要确认
        for (String keyword : DDL_KEYWORDS) {
            if (upperSql.startsWith(keyword)) {
                return true;
            }
        }

        // 包含子查询的复杂查询需要确认
        if (upperSql.contains("DELETE") || upperSql.contains("UPDATE") ||
                upperSql.contains("INSERT")) {
            return true;
        }

        return false;
    }

    /**
     * 清理和标准化SQL
     */
    public String sanitizeSql(String sql) {
        if (StringUtils.isBlank(sql)) {
            return sql;
        }

        return sql.trim()
                .replaceAll("\\s+", " ") // 合并多个空格
                .replaceAll(";+$", ""); // 移除末尾分号
    }

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private boolean valid;
        private String errorMessage;
        private boolean needsConfirmation;

        private ValidationResult(boolean valid, String errorMessage, boolean needsConfirmation) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.needsConfirmation = needsConfirmation;
        }

        public static ValidationResult success(boolean needsConfirmation) {
            return new ValidationResult(true, null, needsConfirmation);
        }

        public static ValidationResult error(String errorMessage) {
            return new ValidationResult(false, errorMessage, false);
        }

        // Getters
        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isNeedsConfirmation() {
            return needsConfirmation;
        }
    }
}