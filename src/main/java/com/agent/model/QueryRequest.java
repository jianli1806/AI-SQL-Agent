package com.agent.model;


public class QueryRequest {
    private String userQuery;
    private String databaseType;
    private boolean requireConfirmation;

    public QueryRequest() {
    }

    public QueryRequest(String userQuery) {
        this.userQuery = userQuery;
        this.databaseType = "mysql";
        this.requireConfirmation = false;
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }

    public boolean isRequireConfirmation() {
        return requireConfirmation;
    }

    public void setRequireConfirmation(boolean requireConfirmation) {
        this.requireConfirmation = requireConfirmation;
    }

    @Override
    public String toString() {
        return "QueryRequest{" +
                "userQuery='" + userQuery + '\'' +
                ", databaseType='" + databaseType + '\'' +
                ", requireConfirmation=" + requireConfirmation +
                '}';
    }
}