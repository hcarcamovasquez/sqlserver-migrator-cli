package com.migrator;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlServerExportData {
    @JsonProperty("metadata")
    private Metadata metadata;

    @JsonProperty("schemas")
    private Map<String, SchemaInfo> schemas;

    @JsonProperty("tables")
    private Map<String, TableInfo> tables;

    @JsonProperty("data")
    private Map<String, List<Map<String, Object>>> data;

    @JsonProperty("stored_procedures")
    private Map<String, String> storedProcedures;

    @JsonProperty("functions")
    private Map<String, String> functions;

    @JsonProperty("views")
    private Map<String, String> views;

    @JsonProperty("triggers")
    private Map<String, String> triggers;

    @JsonProperty("indexes")
    private Map<String, String> indexes;

    @JsonProperty("constraints")
    private Map<String, String> constraints;

    @JsonProperty("table_order")
    private List<String> tableOrder;

    public SqlServerExportData() {
        this.metadata = new Metadata();
        this.schemas = new HashMap<>();
        this.tables = new HashMap<>();
        this.data = new HashMap<>();
        this.storedProcedures = new HashMap<>();
        this.functions = new HashMap<>();
        this.views = new HashMap<>();
        this.triggers = new HashMap<>();
        this.indexes = new HashMap<>();
        this.constraints = new HashMap<>();
        this.tableOrder = new ArrayList<>();
    }

    public static class Metadata {
        @JsonProperty("export_date")
        private LocalDateTime exportDate;

        @JsonProperty("sql_server_version")
        private String sqlServerVersion;

        @JsonProperty("database_name")
        private String databaseName;

        @JsonProperty("collation")
        private String collation;

        @JsonProperty("version")
        private String version;

        @JsonProperty("total_tables")
        private int totalTables;

        @JsonProperty("total_records")
        private long totalRecords;

        public Metadata() {
            this.exportDate = LocalDateTime.now();
            this.version = "1.0-SQLSERVER";
        }

        // Getters y setters
        public LocalDateTime getExportDate() { return exportDate; }
        public void setExportDate(LocalDateTime exportDate) { this.exportDate = exportDate; }
        public String getSqlServerVersion() { return sqlServerVersion; }
        public void setSqlServerVersion(String sqlServerVersion) { this.sqlServerVersion = sqlServerVersion; }
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        public String getCollation() { return collation; }
        public void setCollation(String collation) { this.collation = collation; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public int getTotalTables() { return totalTables; }
        public void setTotalTables(int totalTables) { this.totalTables = totalTables; }
        public long getTotalRecords() { return totalRecords; }
        public void setTotalRecords(long totalRecords) { this.totalRecords = totalRecords; }
    }

    public static class SchemaInfo {
        @JsonProperty("schema_name")
        private String schemaName;

        @JsonProperty("owner")
        private String owner;

        public SchemaInfo(String schemaName, String owner) {
            this.schemaName = schemaName;
            this.owner = owner;
        }

        public SchemaInfo() {}

        // Getters y setters
        public String getSchemaName() { return schemaName; }
        public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
        public String getOwner() { return owner; }
        public void setOwner(String owner) { this.owner = owner; }
    }

    public static class TableInfo {
        @JsonProperty("schema_name")
        private String schemaName;

        @JsonProperty("table_name")
        private String tableName;

        @JsonProperty("create_statement")
        private String createStatement;

        @JsonProperty("columns")
        private List<ColumnInfo> columns;

        @JsonProperty("primary_key")
        private List<String> primaryKey;

        @JsonProperty("foreign_keys")
        private List<ForeignKeyInfo> foreignKeys;

        @JsonProperty("row_count")
        private long rowCount;

        public TableInfo() {
            this.columns = new ArrayList<>();
            this.primaryKey = new ArrayList<>();
            this.foreignKeys = new ArrayList<>();
        }

        // Getters y setters
        public String getSchemaName() { return schemaName; }
        public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getCreateStatement() { return createStatement; }
        public void setCreateStatement(String createStatement) { this.createStatement = createStatement; }
        public List<ColumnInfo> getColumns() { return columns; }
        public void setColumns(List<ColumnInfo> columns) { this.columns = columns; }
        public List<String> getPrimaryKey() { return primaryKey; }
        public void setPrimaryKey(List<String> primaryKey) { this.primaryKey = primaryKey; }
        public List<ForeignKeyInfo> getForeignKeys() { return foreignKeys; }
        public void setForeignKeys(List<ForeignKeyInfo> foreignKeys) { this.foreignKeys = foreignKeys; }
        public long getRowCount() { return rowCount; }
        public void setRowCount(long rowCount) { this.rowCount = rowCount; }
    }

    public static class ColumnInfo {
        @JsonProperty("column_name")
        private String columnName;

        @JsonProperty("data_type")
        private String dataType;

        @JsonProperty("max_length")
        private int maxLength;

        @JsonProperty("precision")
        private int precision;

        @JsonProperty("scale")
        private int scale;

        @JsonProperty("is_nullable")
        private boolean isNullable;

        @JsonProperty("default_value")
        private String defaultValue;

        @JsonProperty("is_identity")
        private boolean isIdentity;

        @JsonProperty("identity_seed")
        private Long identitySeed;

        @JsonProperty("identity_increment")
        private Long identityIncrement;

        // Getters y setters
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public int getMaxLength() { return maxLength; }
        public void setMaxLength(int maxLength) { this.maxLength = maxLength; }
        public int getPrecision() { return precision; }
        public void setPrecision(int precision) { this.precision = precision; }
        public int getScale() { return scale; }
        public void setScale(int scale) { this.scale = scale; }
        public boolean isNullable() { return isNullable; }
        public void setNullable(boolean nullable) { isNullable = nullable; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        public boolean isIdentity() { return isIdentity; }
        public void setIdentity(boolean identity) { isIdentity = identity; }
        public Long getIdentitySeed() { return identitySeed; }
        public void setIdentitySeed(Long identitySeed) { this.identitySeed = identitySeed; }
        public Long getIdentityIncrement() { return identityIncrement; }
        public void setIdentityIncrement(Long identityIncrement) { this.identityIncrement = identityIncrement; }
    }

    public static class ForeignKeyInfo {
        @JsonProperty("constraint_name")
        private String constraintName;

        @JsonProperty("column_name")
        private String columnName;

        @JsonProperty("referenced_schema")
        private String referencedSchema;

        @JsonProperty("referenced_table")
        private String referencedTable;

        @JsonProperty("referenced_column")
        private String referencedColumn;

        @JsonProperty("delete_rule")
        private String deleteRule;

        @JsonProperty("update_rule")
        private String updateRule;

        // Getters y setters
        public String getConstraintName() { return constraintName; }
        public void setConstraintName(String constraintName) { this.constraintName = constraintName; }
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getReferencedSchema() { return referencedSchema; }
        public void setReferencedSchema(String referencedSchema) { this.referencedSchema = referencedSchema; }
        public String getReferencedTable() { return referencedTable; }
        public void setReferencedTable(String referencedTable) { this.referencedTable = referencedTable; }
        public String getReferencedColumn() { return referencedColumn; }
        public void setReferencedColumn(String referencedColumn) { this.referencedColumn = referencedColumn; }
        public String getDeleteRule() { return deleteRule; }
        public void setDeleteRule(String deleteRule) { this.deleteRule = deleteRule; }
        public String getUpdateRule() { return updateRule; }
        public void setUpdateRule(String updateRule) { this.updateRule = updateRule; }
    }

    // Getters y setters principales
    public Metadata getMetadata() { return metadata; }
    public void setMetadata(Metadata metadata) { this.metadata = metadata; }
    public Map<String, SchemaInfo> getSchemas() { return schemas; }
    public void setSchemas(Map<String, SchemaInfo> schemas) { this.schemas = schemas; }
    public Map<String, TableInfo> getTables() { return tables; }
    public void setTables(Map<String, TableInfo> tables) { this.tables = tables; }
    public Map<String, List<Map<String, Object>>> getData() { return data; }
    public void setData(Map<String, List<Map<String, Object>>> data) { this.data = data; }
    public Map<String, String> getStoredProcedures() { return storedProcedures; }
    public void setStoredProcedures(Map<String, String> storedProcedures) { this.storedProcedures = storedProcedures; }
    public Map<String, String> getFunctions() { return functions; }
    public void setFunctions(Map<String, String> functions) { this.functions = functions; }
    public Map<String, String> getViews() { return views; }
    public void setViews(Map<String, String> views) { this.views = views; }
    public Map<String, String> getTriggers() { return triggers; }
    public void setTriggers(Map<String, String> triggers) { this.triggers = triggers; }
    public Map<String, String> getIndexes() { return indexes; }
    public void setIndexes(Map<String, String> indexes) { this.indexes = indexes; }
    public Map<String, String> getConstraints() { return constraints; }
    public void setConstraints(Map<String, String> constraints) { this.constraints = constraints; }
    public List<String> getTableOrder() { return tableOrder; }
    public void setTableOrder(List<String> tableOrder) { this.tableOrder = tableOrder; }
}
