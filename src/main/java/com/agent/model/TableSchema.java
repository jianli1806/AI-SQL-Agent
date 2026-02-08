package com.agent.model;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TableSchema {
    private String tableName;
    private List<ColumnInfo> columns;
    private List<String> primaryKeys;
    private Map<String, String> foreignKeys; // column -> referenced table.column

    public TableSchema() {
        this.columns = new ArrayList<>();
        this.primaryKeys = new ArrayList<>();
        this.foreignKeys = new HashMap<>();
    }

    public static class ColumnInfo {
        private String columnName;
        private String dataType;
        private boolean nullable;
        private String defaultValue;
        private String comment;

        public ColumnInfo() {
        }

        public ColumnInfo(String columnName, String dataType, boolean nullable) {
            this.columnName = columnName;
            this.dataType = dataType;
            this.nullable = nullable;
        }

        // Getters and Setters
        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        public boolean isNullable() {
            return nullable;
        }

        public void setNullable(boolean nullable) {
            this.nullable = nullable;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        @Override
        public String toString() {
            return String.format("%s %s%s%s",
                    columnName,
                    dataType,
                    nullable ? "" : " NOT NULL",
                    defaultValue != null ? " DEFAULT " + defaultValue : "");
        }
    }

    // Getters and Setters
    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<ColumnInfo> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnInfo> columns) {
        this.columns = columns;
    }

    public List<String> getPrimaryKeys() {
        return primaryKeys;
    }

    public void setPrimaryKeys(List<String> primaryKeys) {
        this.primaryKeys = primaryKeys;
    }

    public Map<String, String> getForeignKeys() {
        return foreignKeys;
    }

    public void setForeignKeys(Map<String, String> foreignKeys) {
        this.foreignKeys = foreignKeys;
    }

    // 工具方法
    public ColumnInfo findColumn(String columnName) {
        return columns.stream()
                .filter(col -> col.getColumnName().equalsIgnoreCase(columnName))
                .findFirst()
                .orElse(null);
    }

    public boolean hasColumn(String columnName) {
        return findColumn(columnName) != null;
    }
}
