package com.agent.model;


import java.util.List;
import java.util.Map;

public class QueryResponse {
    private boolean success;
    private String message;
    private String generatedSql;
    private List<Map<String, Object>> data;
    private String explanation;
    private boolean requiresConfirmation;
    private long executionTimeMs;

    public QueryResponse() {
    }

    public QueryResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    // 静态工厂方法
    public static QueryResponse success(List<Map<String, Object>> data, String sql) {
        QueryResponse response = new QueryResponse(true, "查询执行成功");
        response.setData(data);
        response.setGeneratedSql(sql);
        return response;
    }

    public static QueryResponse error(String message) {
        return new QueryResponse(false, message);
    }

    public static QueryResponse requireConfirmation(String sql, String explanation) {
        QueryResponse response = new QueryResponse(true, "需要确认执行");
        response.setGeneratedSql(sql);
        response.setExplanation(explanation);
        response.setRequiresConfirmation(true);
        return response;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getGeneratedSql() {
        return generatedSql;
    }

    public void setGeneratedSql(String generatedSql) {
        this.generatedSql = generatedSql;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public boolean isRequiresConfirmation() {
        return requiresConfirmation;
    }

    public void setRequiresConfirmation(boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }
}
