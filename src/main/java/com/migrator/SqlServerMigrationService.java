// src/main/java/com/migrator/SqlServerMigrationService.java
package com.migrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SqlServerMigrationService {

    private final ObjectMapper objectMapper;
    private static final int BATCH_SIZE = 1000;

    public SqlServerMigrationService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public String exportDatabase(SqlServerConfig config) throws Exception {
        System.out.println("🚀 Iniciando exportación de SQL Server...");
        System.out.println("📡 Conectando a: " + config.getServer() +
                (config.getInstance() != null ? "\\" + config.getInstance() : "") +
                ":" + config.getPort());
        System.out.println("🗄️  Base de datos: " + config.getDatabase());

        SqlServerExportData exportData = new SqlServerExportData();

        Connection connection = null;
        try {
            connection = DriverManager.getConnection(
                    config.buildConnectionUrl(), config.getUsername(), config.getPassword());

            // Obtener información del servidor y base de datos
            setDatabaseMetadata(connection, exportData, config);

            // Exportar esquemas
            exportSchemas(connection, exportData);

            // Obtener tablas y analizar dependencias
            List<String> tables = getUserTables(connection);
            System.out.println("📊 Encontradas " + tables.size() + " tablas de usuario");

            Map<String, List<String>> dependencies = analyzeDependencies(connection, tables);
            List<String> orderedTables = topologicalSort(dependencies, tables);
            exportData.setTableOrder(orderedTables);

            System.out.println("🔗 Orden de exportación:");
            for (int i = 0; i < orderedTables.size(); i++) {
                System.out.println("   " + (i + 1) + ". " + orderedTables.get(i));
            }

            // Exportar estructura de tablas
            exportTableStructures(connection, orderedTables, exportData);

            // Exportar datos
            exportTableData(connection, orderedTables, exportData);

            // Exportar objetos de base de datos
            exportStoredProcedures(connection, exportData);
            exportFunctions(connection, exportData);
            exportViews(connection, exportData);
            exportTriggers(connection, exportData);
            exportIndexes(connection, exportData);
            exportConstraints(connection, exportData);

            // Establecer estadísticas finales
            exportData.getMetadata().setTotalTables(tables.size());
            long totalRecords = 0;
            for (List<Map<String, Object>> tableData : exportData.getData().values()) {
                totalRecords += tableData.size();
            }
            exportData.getMetadata().setTotalRecords(totalRecords);

        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    // Ignorar errores al cerrar
                }
            }
        }

        // Comprimir y guardar
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupFile = "sqlserver_backup_" + config.getDatabase() + "_" + timestamp + ".gz";

        FileOutputStream fos = null;
        GzipCompressorOutputStream gzos = null;
        OutputStreamWriter writer = null;

        try {
            fos = new FileOutputStream(backupFile);
            gzos = new GzipCompressorOutputStream(fos);
            writer = new OutputStreamWriter(gzos, "UTF-8");

            objectMapper.writeValue(writer, exportData);

        } finally {
            if (writer != null) writer.close();
            if (gzos != null) gzos.close();
            if (fos != null) fos.close();
        }

        long fileSize = Files.size(Paths.get(backupFile)) / (1024 * 1024);
        System.out.println("✅ Exportación completada: " + backupFile + " (" + fileSize + " MB)");
        System.out.println("📈 Estadísticas: " + exportData.getMetadata().getTotalTables() +
                " tablas, " + exportData.getMetadata().getTotalRecords() + " registros totales");

        return backupFile;
    }

    public boolean importDatabase(String backupFile, SqlServerConfig config) throws Exception {
        System.out.println("🔄 Iniciando importación desde " + backupFile + "...");
        System.out.println("🎯 Destino: " + config.getServer() +
                (config.getInstance() != null ? "\\" + config.getInstance() : "") +
                ":" + config.getPort());
        System.out.println("🗄️  Base de datos: " + config.getDatabase());

        SqlServerExportData exportData;

        // Leer archivo comprimido
        FileInputStream fis = null;
        GzipCompressorInputStream gzis = null;
        InputStreamReader reader = null;

        try {
            fis = new FileInputStream(backupFile);
            gzis = new GzipCompressorInputStream(fis);
            reader = new InputStreamReader(gzis, "UTF-8");

            exportData = objectMapper.readValue(reader, SqlServerExportData.class);

        } finally {
            if (reader != null) reader.close();
            if (gzis != null) gzis.close();
            if (fis != null) fis.close();
        }

        System.out.println("📅 Backup creado: " + exportData.getMetadata().getExportDate());
        System.out.println("📊 Contiene: " + exportData.getMetadata().getTotalTables() +
                " tablas, " + exportData.getMetadata().getTotalRecords() + " registros");

        Connection connection = null;
        try {
            connection = DriverManager.getConnection(
                    config.buildConnectionUrl(), config.getUsername(), config.getPassword());

            connection.setAutoCommit(false);

            try {
                // Verificar si la base de datos está vacía
                if (!isDatabaseEmpty(connection)) {
                    System.out.println("⚠️  La base de datos no está vacía. Continuando...");
                }

                // Crear esquemas
                createSchemas(connection, exportData);

                // Crear tablas
                createTables(connection, exportData);

                // Insertar datos
                insertData(connection, exportData);

                // Crear constraints
                createConstraints(connection, exportData);

                // Crear índices
                createIndexes(connection, exportData);

                // Crear stored procedures
                createStoredProcedures(connection, exportData);

                // Crear funciones
                createFunctions(connection, exportData);

                // Crear vistas
                createViews(connection, exportData);

                // Crear triggers
                createTriggers(connection, exportData);

                connection.commit();
                System.out.println("🎉 Importación completada exitosamente");
                return true;

            } catch (Exception e) {
                connection.rollback();
                System.err.println("❌ Error durante la importación: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    // Ignorar errores al cerrar
                }
            }
        }
    }

    public boolean verifyBackup(String backupFile) throws Exception {
        System.out.println("🔍 Verificando backup: " + backupFile);

        FileInputStream fis = null;
        GzipCompressorInputStream gzis = null;
        InputStreamReader reader = null;

        try {
            fis = new FileInputStream(backupFile);
            gzis = new GzipCompressorInputStream(fis);
            reader = new InputStreamReader(gzis, "UTF-8");

            SqlServerExportData exportData = objectMapper.readValue(reader, SqlServerExportData.class);

            System.out.println("✅ Archivo válido");
            System.out.println("📅 Fecha de creación: " + exportData.getMetadata().getExportDate());
            System.out.println("🗄️  Base de datos origen: " + exportData.getMetadata().getDatabaseName());
            System.out.println("🔢 Versión SQL Server: " + exportData.getMetadata().getSqlServerVersion());
            System.out.println("📊 Tablas: " + exportData.getMetadata().getTotalTables());
            System.out.println("📈 Registros totales: " + exportData.getMetadata().getTotalRecords());
            System.out.println("🏗️  Stored Procedures: " + exportData.getStoredProcedures().size());
            System.out.println("🔧 Funciones: " + exportData.getFunctions().size());
            System.out.println("👁️  Vistas: " + exportData.getViews().size());
            System.out.println("⚡ Triggers: " + exportData.getTriggers().size());
            System.out.println("🔍 Índices: " + exportData.getIndexes().size());

            return true;

        } catch (Exception e) {
            System.err.println("❌ Error verificando backup: " + e.getMessage());
            return false;
        } finally {
            if (reader != null) reader.close();
            if (gzis != null) gzis.close();
            if (fis != null) fis.close();
        }
    }

    // Método utilitario para crear nombres de tabla quoted correctamente
    private String buildQuotedTableName(String fullTableName) {
        String[] parts = fullTableName.split("\\.");
        if (parts.length == 2) {
            return "[" + parts[0] + "].[" + parts[1] + "]";
        } else {
            return "[" + fullTableName + "]";
        }
    }

    private void setDatabaseMetadata(Connection connection, SqlServerExportData exportData, SqlServerConfig config) throws SQLException {
        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = connection.createStatement();

            // Obtener información del servidor
            rs = stmt.executeQuery("SELECT @@VERSION as server_version");
            if (rs.next()) {
                exportData.getMetadata().setSqlServerVersion(rs.getString("server_version"));
            }
            rs.close();

            // Obtener información de la base de datos
            rs = stmt.executeQuery("SELECT DB_NAME() as db_name, DATABASEPROPERTYEX(DB_NAME(), 'Collation') as collation");
            if (rs.next()) {
                exportData.getMetadata().setDatabaseName(rs.getString("db_name"));
                exportData.getMetadata().setCollation(rs.getString("collation"));
            }
            rs.close();

        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }

        String version = exportData.getMetadata().getSqlServerVersion();
        if (version != null && version.contains("\n")) {
            version = version.split("\n")[0];
        }
        System.out.println("🔢 SQL Server: " + version);
        System.out.println("🌐 Collation: " + exportData.getMetadata().getCollation());
    }

    private void exportSchemas(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("📁 Exportando esquemas...");

        String sql = "SELECT s.name as schema_name, p.name as owner_name " +
                "FROM sys.schemas s " +
                "INNER JOIN sys.database_principals p ON s.principal_id = p.principal_id " +
                "WHERE s.name NOT IN ('sys', 'INFORMATION_SCHEMA', 'guest', 'db_owner', 'db_accessadmin', " +
                "'db_securityadmin', 'db_ddladmin', 'db_backupoperator', 'db_datareader', " +
                "'db_datawriter', 'db_denydatareader', 'db_denydatawriter')";

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String schemaName = rs.getString("schema_name");
                String ownerName = rs.getString("owner_name");
                exportData.getSchemas().put(schemaName, new SqlServerExportData.SchemaInfo(schemaName, ownerName));
                System.out.println("✓ Esquema exportado: " + schemaName + " (owner: " + ownerName + ")");
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
    }

    private List<String> getUserTables(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<String>();

        String sql = "SELECT SCHEMA_NAME(t.schema_id) + '.' + t.name as full_table_name " +
                "FROM sys.tables t " +
                "WHERE t.is_ms_shipped = 0 " +
                "ORDER BY SCHEMA_NAME(t.schema_id), t.name";

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                tables.add(rs.getString("full_table_name"));
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }

        return tables;
    }

    private Map<String, List<String>> analyzeDependencies(Connection connection, List<String> tables) throws SQLException {
        System.out.println("🔗 Analizando dependencias entre tablas...");

        Map<String, List<String>> dependencies = new HashMap<String, List<String>>();

        for (String table : tables) {
            dependencies.put(table, new ArrayList<String>());
        }

        String sql = "SELECT " +
                "SCHEMA_NAME(fk.schema_id) + '.' + OBJECT_NAME(fk.parent_object_id) as dependent_table, " +
                "SCHEMA_NAME(pk.schema_id) + '.' + OBJECT_NAME(fk.referenced_object_id) as referenced_table " +
                "FROM sys.foreign_keys fk " +
                "INNER JOIN sys.tables pk ON fk.referenced_object_id = pk.object_id " +
                "WHERE fk.is_ms_shipped = 0";

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String dependentTable = rs.getString("dependent_table");
                String referencedTable = rs.getString("referenced_table");

                if (tables.contains(dependentTable) && tables.contains(referencedTable) &&
                        !dependentTable.equals(referencedTable)) {
                    dependencies.get(dependentTable).add(referencedTable);
                }
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }

        return dependencies;
    }

    private List<String> topologicalSort(Map<String, List<String>> dependencies, List<String> tables) {
        Set<String> visited = new HashSet<String>();
        Set<String> visiting = new HashSet<String>();
        List<String> result = new ArrayList<String>();

        for (String table : tables) {
            if (!visited.contains(table)) {
                topologicalSortUtil(table, dependencies, visited, visiting, result);
            }
        }

        return result;
    }

    private void topologicalSortUtil(String table, Map<String, List<String>> dependencies,
                                     Set<String> visited, Set<String> visiting, List<String> result) {

        if (visiting.contains(table)) {
            // Dependencia circular detectada, ignorar
            return;
        }

        if (visited.contains(table)) {
            return;
        }

        visiting.add(table);

        List<String> deps = dependencies.get(table);
        if (deps != null) {
            for (String dep : deps) {
                if (dependencies.containsKey(dep)) {
                    topologicalSortUtil(dep, dependencies, visited, visiting, result);
                }
            }
        }

        visiting.remove(table);
        visited.add(table);
        result.add(table);
    }

    private void exportTableStructures(Connection connection, List<String> tables, SqlServerExportData exportData) throws SQLException {
        System.out.println("🏗️  Exportando estructuras de tablas...");

        for (String fullTableName : tables) {
            String[] parts = fullTableName.split("\\.");
            String schemaName = parts[0];
            String tableName = parts[1];

            SqlServerExportData.TableInfo tableInfo = new SqlServerExportData.TableInfo();
            tableInfo.setSchemaName(schemaName);
            tableInfo.setTableName(tableName);

            // Exportar columnas
            exportTableColumns(connection, schemaName, tableName, tableInfo);

            // Exportar primary key
            exportPrimaryKey(connection, schemaName, tableName, tableInfo);

            // Exportar foreign keys
            exportForeignKeys(connection, schemaName, tableName, tableInfo);

            // Generar CREATE TABLE statement
            generateCreateTableStatement(tableInfo);

            // Obtener row count
            getRowCount(connection, fullTableName, tableInfo);

            exportData.getTables().put(fullTableName, tableInfo);
            System.out.println("✓ Estructura exportada: " + fullTableName + " (" + tableInfo.getRowCount() + " filas)");
        }
    }

    private void exportTableColumns(Connection connection, String schemaName, String tableName, SqlServerExportData.TableInfo tableInfo) throws SQLException {
        String sql = "SELECT " +
                "c.COLUMN_NAME, " +
                "c.DATA_TYPE, " +
                "c.CHARACTER_MAXIMUM_LENGTH, " +
                "c.NUMERIC_PRECISION, " +
                "c.NUMERIC_SCALE, " +
                "c.IS_NULLABLE, " +
                "c.COLUMN_DEFAULT, " +
                "COLUMNPROPERTY(OBJECT_ID(c.TABLE_SCHEMA + '.' + c.TABLE_NAME), c.COLUMN_NAME, 'IsIdentity') as IS_IDENTITY, " +
                "IDENT_SEED(c.TABLE_SCHEMA + '.' + c.TABLE_NAME) as IDENTITY_SEED, " +
                "IDENT_INCR(c.TABLE_SCHEMA + '.' + c.TABLE_NAME) as IDENTITY_INCREMENT " +
                "FROM INFORMATION_SCHEMA.COLUMNS c " +
                "WHERE c.TABLE_SCHEMA = ? AND c.TABLE_NAME = ? " +
                "ORDER BY c.ORDINAL_POSITION";

        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, schemaName);
            pstmt.setString(2, tableName);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                SqlServerExportData.ColumnInfo columnInfo = new SqlServerExportData.ColumnInfo();
                columnInfo.setColumnName(rs.getString("COLUMN_NAME"));
                columnInfo.setDataType(rs.getString("DATA_TYPE"));
                columnInfo.setMaxLength(rs.getInt("CHARACTER_MAXIMUM_LENGTH"));
                columnInfo.setPrecision(rs.getInt("NUMERIC_PRECISION"));
                columnInfo.setScale(rs.getInt("NUMERIC_SCALE"));
                columnInfo.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
                columnInfo.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
                columnInfo.setIdentity(rs.getBoolean("IS_IDENTITY"));

                if (columnInfo.isIdentity()) {
                    columnInfo.setIdentitySeed(rs.getLong("IDENTITY_SEED"));
                    columnInfo.setIdentityIncrement(rs.getLong("IDENTITY_INCREMENT"));
                }

                tableInfo.getColumns().add(columnInfo);
            }
        } finally {
            if (rs != null) rs.close();
            if (pstmt != null) pstmt.close();
        }
    }

    private void exportPrimaryKey(Connection connection, String schemaName, String tableName, SqlServerExportData.TableInfo tableInfo) throws SQLException {
        String sql = "SELECT c.COLUMN_NAME " +
                "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc " +
                "INNER JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE c ON tc.CONSTRAINT_NAME = c.CONSTRAINT_NAME " +
                "WHERE tc.TABLE_SCHEMA = ? AND tc.TABLE_NAME = ? AND tc.CONSTRAINT_TYPE = 'PRIMARY KEY' " +
                "ORDER BY c.ORDINAL_POSITION";

        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, schemaName);
            pstmt.setString(2, tableName);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                tableInfo.getPrimaryKey().add(rs.getString("COLUMN_NAME"));
            }
        } finally {
            if (rs != null) rs.close();
            if (pstmt != null) pstmt.close();
        }
    }

    private void exportForeignKeys(Connection connection, String schemaName, String tableName, SqlServerExportData.TableInfo tableInfo) throws SQLException {
        String sql = "SELECT " +
                "fk.name as CONSTRAINT_NAME, " +
                "c1.name as COLUMN_NAME, " +
                "s2.name as REFERENCED_SCHEMA, " +
                "t2.name as REFERENCED_TABLE, " +
                "c2.name as REFERENCED_COLUMN, " +
                "fk.delete_referential_action_desc as DELETE_RULE, " +
                "fk.update_referential_action_desc as UPDATE_RULE " +
                "FROM sys.foreign_keys fk " +
                "INNER JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id " +
                "INNER JOIN sys.columns c1 ON fkc.parent_object_id = c1.object_id AND fkc.parent_column_id = c1.column_id " +
                "INNER JOIN sys.columns c2 ON fkc.referenced_object_id = c2.object_id AND fkc.referenced_column_id = c2.column_id " +
                "INNER JOIN sys.tables t1 ON fk.parent_object_id = t1.object_id " +
                "INNER JOIN sys.schemas s1 ON t1.schema_id = s1.schema_id " +
                "INNER JOIN sys.tables t2 ON fk.referenced_object_id = t2.object_id " +
                "INNER JOIN sys.schemas s2 ON t2.schema_id = s2.schema_id " +
                "WHERE s1.name = ? AND t1.name = ?";

        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, schemaName);
            pstmt.setString(2, tableName);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                SqlServerExportData.ForeignKeyInfo fkInfo = new SqlServerExportData.ForeignKeyInfo();
                fkInfo.setConstraintName(rs.getString("CONSTRAINT_NAME"));
                fkInfo.setColumnName(rs.getString("COLUMN_NAME"));
                fkInfo.setReferencedSchema(rs.getString("REFERENCED_SCHEMA"));
                fkInfo.setReferencedTable(rs.getString("REFERENCED_TABLE"));
                fkInfo.setReferencedColumn(rs.getString("REFERENCED_COLUMN"));
                fkInfo.setDeleteRule(rs.getString("DELETE_RULE"));
                fkInfo.setUpdateRule(rs.getString("UPDATE_RULE"));

                tableInfo.getForeignKeys().add(fkInfo);
            }
        } finally {
            if (rs != null) rs.close();
            if (pstmt != null) pstmt.close();
        }
    }

    private void generateCreateTableStatement(SqlServerExportData.TableInfo tableInfo) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE [").append(tableInfo.getSchemaName()).append("].[").append(tableInfo.getTableName()).append("] (\n");

        List<String> columnDefs = new ArrayList<String>();

        for (SqlServerExportData.ColumnInfo column : tableInfo.getColumns()) {
            StringBuilder columnDef = new StringBuilder();
            columnDef.append("    [").append(column.getColumnName()).append("] ");
            columnDef.append(column.getDataType().toUpperCase());

            // Agregar tamaño/precisión
            if (column.getDataType().toLowerCase().contains("varchar") ||
                    column.getDataType().toLowerCase().contains("char")) {
                if (column.getMaxLength() > 0) {
                    columnDef.append("(").append(column.getMaxLength()).append(")");
                } else {
                    columnDef.append("(MAX)");
                }
            } else if (column.getDataType().toLowerCase().contains("decimal") ||
                    column.getDataType().toLowerCase().contains("numeric")) {
                columnDef.append("(").append(column.getPrecision()).append(",").append(column.getScale()).append(")");
            }

            // Identity
            if (column.isIdentity()) {
                columnDef.append(" IDENTITY(").append(column.getIdentitySeed()).append(",").append(column.getIdentityIncrement()).append(")");
            }

            // Nullable
            if (!column.isNullable()) {
                columnDef.append(" NOT NULL");
            }

            // Default
            if (column.getDefaultValue() != null && !column.getDefaultValue().isEmpty()) {
                columnDef.append(" DEFAULT ").append(column.getDefaultValue());
            }

            columnDefs.add(columnDef.toString());
        }

        sql.append(joinStringList(columnDefs, ",\n"));

        // Primary Key
        if (!tableInfo.getPrimaryKey().isEmpty()) {
            sql.append(",\n    CONSTRAINT [PK_").append(tableInfo.getTableName()).append("] PRIMARY KEY (");

            List<String> quotedPkColumns = new ArrayList<String>();
            for (String col : tableInfo.getPrimaryKey()) {
                quotedPkColumns.add("[" + col + "]");
            }
            sql.append(joinStringList(quotedPkColumns, ", "));
            sql.append(")");
        }

        sql.append("\n)");

        tableInfo.setCreateStatement(sql.toString());
    }

    private void getRowCount(Connection connection, String fullTableName, SqlServerExportData.TableInfo tableInfo) throws SQLException {
        String quotedTableName = buildQuotedTableName(fullTableName);

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery("SELECT COUNT(*) as row_count FROM " + quotedTableName);

            if (rs.next()) {
                tableInfo.setRowCount(rs.getLong("row_count"));
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
    }

    private void exportTableData(Connection connection, List<String> tables, SqlServerExportData exportData) throws SQLException {
        System.out.println("📦 Exportando datos de tablas...");

        for (String fullTableName : tables) {
            String quotedTableName = buildQuotedTableName(fullTableName);

            Statement stmt = null;
            ResultSet rs = null;

            try {
                stmt = connection.createStatement();
                rs = stmt.executeQuery("SELECT * FROM " + quotedTableName);

                ResultSetMetaData rsmd = rs.getMetaData();
                int columnCount = rsmd.getColumnCount();

                List<Map<String, Object>> tableData = new ArrayList<Map<String, Object>>();

                while (rs.next()) {
                    Map<String, Object> row = new HashMap<String, Object>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = rsmd.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        int columnType = rsmd.getColumnType(i);

                        // Convertir tipos especiales para JSON con metadatos de tipo
                        if (value == null) {
                            row.put(columnName, null);
                        } else if (value instanceof byte[]) {
                            // VARBINARY, BINARY, IMAGE - convertir a Base64 con marcador
                            Map<String, Object> binaryData = new HashMap<String, Object>();
                            binaryData.put("_type", "VARBINARY");
                            binaryData.put("_value", Base64.getEncoder().encodeToString((byte[]) value));
                            row.put(columnName, binaryData);
                        } else if (value instanceof Timestamp) {
                            Map<String, Object> timestampData = new HashMap<String, Object>();
                            timestampData.put("_type", "TIMESTAMP");
                            timestampData.put("_value", value.toString());
                            row.put(columnName, timestampData);
                        } else if (value instanceof Time) {
                            Map<String, Object> timeData = new HashMap<String, Object>();
                            timeData.put("_type", "TIME");
                            timeData.put("_value", value.toString());
                            row.put(columnName, timeData);
                        } else if (value instanceof Date) {
                            Map<String, Object> dateData = new HashMap<String, Object>();
                            dateData.put("_type", "DATE");
                            dateData.put("_value", value.toString());
                            row.put(columnName, dateData);
                        } else if (columnType == java.sql.Types.LONGVARCHAR || columnType == java.sql.Types.LONGNVARCHAR) {
                            // TEXT, NTEXT
                            Map<String, Object> textData = new HashMap<String, Object>();
                            textData.put("_type", "TEXT");
                            textData.put("_value", value.toString());
                            row.put(columnName, textData);
                        } else if (value instanceof java.sql.Clob) {
                            // CLOB data
                            java.sql.Clob clob = (java.sql.Clob) value;
                            Map<String, Object> clobData = new HashMap<String, Object>();
                            clobData.put("_type", "CLOB");
                            clobData.put("_value", clob.getSubString(1, (int) clob.length()));
                            row.put(columnName, clobData);
                        } else if (value instanceof java.sql.Blob) {
                            // BLOB data
                            java.sql.Blob blob = (java.sql.Blob) value;
                            Map<String, Object> blobData = new HashMap<String, Object>();
                            blobData.put("_type", "BLOB");
                            blobData.put("_value", Base64.getEncoder().encodeToString(blob.getBytes(1, (int) blob.length())));
                            row.put(columnName, blobData);
                        } else {
                            // Tipos normales (VARCHAR, INT, etc.)
                            row.put(columnName, value);
                        }
                    }
                    tableData.add(row);
                }

                exportData.getData().put(fullTableName, tableData);
                System.out.println("✓ Datos exportados: " + fullTableName + " (" + tableData.size() + " registros)");

            } finally {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
            }
        }
    }

    private void exportStoredProcedures(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("🏗️  Exportando stored procedures...");

        String sql = "SELECT " +
                "SCHEMA_NAME(p.schema_id) + '.' + p.name as proc_name, " +
                "m.definition " +
                "FROM sys.procedures p " +
                "INNER JOIN sys.sql_modules m ON p.object_id = m.object_id " +
                "WHERE p.is_ms_shipped = 0";

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String procName = rs.getString("proc_name");
                String definition = rs.getString("definition");
                exportData.getStoredProcedures().put(procName, definition);
                System.out.println("✓ Stored procedure exportado: " + procName);
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
    }

    private void exportFunctions(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("🔧 Exportando funciones...");

        String sql = "SELECT " +
                "SCHEMA_NAME(f.schema_id) + '.' + f.name as func_name, " +
                "m.definition " +
                "FROM sys.objects f " +
                "INNER JOIN sys.sql_modules m ON f.object_id = m.object_id " +
                "WHERE f.type IN ('FN', 'IF', 'TF') AND f.is_ms_shipped = 0";

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String funcName = rs.getString("func_name");
                String definition = rs.getString("definition");
                exportData.getFunctions().put(funcName, definition);
                System.out.println("✓ Función exportada: " + funcName);
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
    }

    private void exportViews(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("👁️  Exportando vistas...");

        String sql = "SELECT " +
                "SCHEMA_NAME(v.schema_id) + '.' + v.name as view_name, " +
                "m.definition " +
                "FROM sys.views v " +
                "INNER JOIN sys.sql_modules m ON v.object_id = m.object_id " +
                "WHERE v.is_ms_shipped = 0";

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String viewName = rs.getString("view_name");
                String definition = rs.getString("definition");
                exportData.getViews().put(viewName, definition);
                System.out.println("✓ Vista exportada: " + viewName);
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
    }

    private void exportTriggers(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("⚡ Exportando triggers...");

        String sql = "SELECT " +
                "SCHEMA_NAME(t.schema_id) + '.' + OBJECT_NAME(tr.parent_id) + '.' + tr.name as trigger_name, " +
                "m.definition " +
                "FROM sys.triggers tr " +
                "INNER JOIN sys.sql_modules m ON tr.object_id = m.object_id " +
                "INNER JOIN sys.tables t ON tr.parent_id = t.object_id " +
                "WHERE tr.is_ms_shipped = 0";

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String triggerName = rs.getString("trigger_name");
                String definition = rs.getString("definition");
                exportData.getTriggers().put(triggerName, definition);
                System.out.println("✓ Trigger exportado: " + triggerName);
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
    }

    private void exportIndexes(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("🔍 Exportando índices...");

        String sql = "SELECT " +
                "SCHEMA_NAME(t.schema_id) + '.' + t.name + '.' + i.name as index_name, " +
                "'CREATE ' + " +
                "CASE WHEN i.is_unique = 1 THEN 'UNIQUE ' ELSE '' END + " +
                "'INDEX [' + i.name + '] ON [' + SCHEMA_NAME(t.schema_id) + '].[' + t.name + '] (' + " +
                "STUFF(( " +
                "SELECT ', [' + c.name + ']' + " +
                "CASE WHEN ic.is_descending_key = 1 THEN ' DESC' ELSE ' ASC' END " +
                "FROM sys.index_columns ic " +
                "INNER JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id " +
                "WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id " +
                "ORDER BY ic.key_ordinal " +
                "FOR XML PATH('') " +
                "), 1, 2, '') + ')' as create_statement " +
                "FROM sys.indexes i " +
                "INNER JOIN sys.tables t ON i.object_id = t.object_id " +
                "WHERE i.type > 0 AND i.is_primary_key = 0 AND i.is_unique_constraint = 0 AND t.is_ms_shipped = 0";

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String indexName = rs.getString("index_name");
                String createStatement = rs.getString("create_statement");
                exportData.getIndexes().put(indexName, createStatement);
                System.out.println("✓ Índice exportado: " + indexName);
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
    }

    private void exportConstraints(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("🔒 Exportando constraints...");

        String sql = "SELECT " +
                "SCHEMA_NAME(t.schema_id) + '.' + t.name + '.' + cc.name as constraint_name, " +
                "'ALTER TABLE [' + SCHEMA_NAME(t.schema_id) + '].[' + t.name + '] ADD CONSTRAINT [' + cc.name + '] CHECK ' + cc.definition as create_statement " +
                "FROM sys.check_constraints cc " +
                "INNER JOIN sys.tables t ON cc.parent_object_id = t.object_id " +
                "WHERE t.is_ms_shipped = 0";

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String constraintName = rs.getString("constraint_name");
                String createStatement = rs.getString("create_statement");
                exportData.getConstraints().put(constraintName, createStatement);
                System.out.println("✓ Constraint exportado: " + constraintName);
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
    }

    // Métodos de importación

    private boolean isDatabaseEmpty(Connection connection) throws SQLException {
        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery("SELECT COUNT(*) as table_count FROM sys.tables WHERE is_ms_shipped = 0");

            if (rs.next()) {
                return rs.getInt("table_count") == 0;
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
        return true;
    }

    private void createSchemas(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("📁 Creando esquemas...");

        Statement stmt = null;

        try {
            stmt = connection.createStatement();
            for (SqlServerExportData.SchemaInfo schemaInfo : exportData.getSchemas().values()) {
                if (!"dbo".equals(schemaInfo.getSchemaName())) {
                    String sql = "CREATE SCHEMA [" + schemaInfo.getSchemaName() + "]";
                    try {
                        stmt.execute(sql);
                        System.out.println("✓ Esquema creado: " + schemaInfo.getSchemaName());
                    } catch (SQLException e) {
                        if (!e.getMessage().contains("already exists")) {
                            throw e;
                        }
                        System.out.println("⚠️  Esquema ya existe: " + schemaInfo.getSchemaName());
                    }
                }
            }
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    private void createTables(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("🏗️  Creando tablas...");

        Statement stmt = null;

        try {
            stmt = connection.createStatement();
            for (String tableName : exportData.getTableOrder()) {
                if (exportData.getTables().containsKey(tableName)) {
                    SqlServerExportData.TableInfo tableInfo = exportData.getTables().get(tableName);

                    try {
                        stmt.execute(tableInfo.getCreateStatement());
                        System.out.println("✓ Tabla creada: " + tableName);
                    } catch (SQLException e) {
                        System.err.println("❌ Error creando tabla " + tableName + ": " + e.getMessage());
                        throw e;
                    }
                }
            }
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    private void insertData(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("📥 Insertando datos...");

        for (String tableName : exportData.getTableOrder()) {
            if (exportData.getData().containsKey(tableName)) {
                insertTableData(connection, tableName, exportData);
            }
        }
    }

    private void insertTableData(Connection connection, String tableName, SqlServerExportData exportData) throws SQLException {
        List<Map<String, Object>> tableData = exportData.getData().get(tableName);

        if (tableData.isEmpty()) {
            return;
        }

        String quotedTableName = buildQuotedTableName(tableName);
        SqlServerExportData.TableInfo tableInfo = exportData.getTables().get(tableName);

        // Verificar si tiene columna IDENTITY
        boolean hasIdentity = false;
        for (SqlServerExportData.ColumnInfo column : tableInfo.getColumns()) {
            if (column.isIdentity()) {
                hasIdentity = true;
                break;
            }
        }

        Statement stmt = null;
        PreparedStatement pstmt = null;

        try {
            stmt = connection.createStatement();

            if (hasIdentity) {
                stmt.execute("SET IDENTITY_INSERT " + quotedTableName + " ON");
            }

            Map<String, Object> firstRow = tableData.get(0);
            List<String> columns = new ArrayList<String>(firstRow.keySet());

            List<String> quotedColumns = new ArrayList<String>();
            for (String col : columns) {
                quotedColumns.add("[" + col + "]");
            }
            String columnList = joinStringList(quotedColumns, ", ");

            List<String> placeholderList = new ArrayList<String>();
            for (int i = 0; i < columns.size(); i++) {
                placeholderList.add("?");
            }
            String placeholders = joinStringList(placeholderList, ", ");

            String insertSql = "INSERT INTO " + quotedTableName + " (" + columnList + ") VALUES (" + placeholders + ")";

            pstmt = connection.prepareStatement(insertSql);

            // Obtener información de tipos de columnas para conversión correcta
            Map<String, String> columnTypes = getColumnTypes(connection, tableInfo);

            int count = 0;

            for (Map<String, Object> row : tableData) {
                for (int i = 0; i < columns.size(); i++) {
                    String columnName = columns.get(i);
                    Object value = row.get(columnName);
                    String columnType = columnTypes.get(columnName);

                    // Convertir valores según el tipo de columna de destino
                    Object convertedValue = convertValueForColumn(value, columnType);

                    pstmt.setObject(i + 1, convertedValue);
                }
                pstmt.addBatch();
                count++;

                if (count % BATCH_SIZE == 0) {
                    pstmt.executeBatch();
                }
            }

            if (count % BATCH_SIZE != 0) {
                pstmt.executeBatch();
            }

            if (hasIdentity) {
                stmt.execute("SET IDENTITY_INSERT " + quotedTableName + " OFF");
            }

            System.out.println("✓ Datos insertados: " + tableName + " (" + tableData.size() + " registros)");

        } finally {
            if (pstmt != null) pstmt.close();
            if (stmt != null) stmt.close();
        }
    }

    private Map<String, String> getColumnTypes(Connection connection, SqlServerExportData.TableInfo tableInfo) {
        Map<String, String> columnTypes = new HashMap<String, String>();

        for (SqlServerExportData.ColumnInfo column : tableInfo.getColumns()) {
            columnTypes.put(column.getColumnName(), column.getDataType().toLowerCase());
        }

        return columnTypes;
    }

    private Object convertValueForColumn(Object value, String columnType) {
        if (value == null) {
            return null;
        }

        // Si el valor es un Map, significa que tiene metadatos de tipo
        if (value instanceof Map) {
            Map<String, Object> valueMap = (Map<String, Object>) value;
            String storedType = (String) valueMap.get("_type");
            Object storedValue = valueMap.get("_value");

            if (storedValue == null) {
                return null;
            }

            if ("VARBINARY".equals(storedType) || "BLOB".equals(storedType)) {
                // Reconvertir de Base64 a bytes
                try {
                    return Base64.getDecoder().decode((String) storedValue);
                } catch (IllegalArgumentException e) {
                    System.err.println("⚠️  Error decodificando Base64, usando valor original");
                    return storedValue;
                }
            } else if ("TIMESTAMP".equals(storedType)) {
                // Convertir string de vuelta a Timestamp
                try {
                    return Timestamp.valueOf((String) storedValue);
                } catch (IllegalArgumentException e) {
                    return storedValue;
                }
            } else if ("TIME".equals(storedType)) {
                // Convertir string de vuelta a Time
                try {
                    return Time.valueOf((String) storedValue);
                } catch (IllegalArgumentException e) {
                    return storedValue;
                }
            } else if ("DATE".equals(storedType)) {
                // Convertir string de vuelta a Date
                try {
                    return Date.valueOf((String) storedValue);
                } catch (IllegalArgumentException e) {
                    return storedValue;
                }
            } else if ("TEXT".equals(storedType) || "CLOB".equals(storedType)) {
                // Texto largo
                return storedValue;
            }

            return storedValue;
        }

        // Para valores que no tienen metadatos, hacer conversión basada en tipo de columna
        if (columnType != null) {
            if (columnType.contains("varbinary") || columnType.contains("binary") || columnType.contains("image")) {
                // Si esperamos binario pero tenemos string, intentar decodificar Base64
                if (value instanceof String && isBase64Encoded((String) value)) {
                    try {
                        return Base64.getDecoder().decode((String) value);
                    } catch (IllegalArgumentException e) {
                        System.err.println("⚠️  Error decodificando Base64 para columna " + columnType);
                        return value;
                    }
                }
            } else if (columnType.contains("datetime") || columnType.contains("timestamp")) {
                // Convertir strings a Timestamp
                if (value instanceof String) {
                    try {
                        return Timestamp.valueOf((String) value);
                    } catch (IllegalArgumentException e) {
                        return value;
                    }
                }
            } else if (columnType.contains("time") && !columnType.contains("datetime")) {
                // Convertir strings a Time
                if (value instanceof String) {
                    try {
                        return Time.valueOf((String) value);
                    } catch (IllegalArgumentException e) {
                        return value;
                    }
                }
            } else if (columnType.contains("date") && !columnType.contains("datetime")) {
                // Convertir strings a Date
                if (value instanceof String) {
                    try {
                        return Date.valueOf((String) value);
                    } catch (IllegalArgumentException e) {
                        return value;
                    }
                }
            }
        }

        return value;
    }

    private boolean isBase64Encoded(String str) {
        if (str == null || str.length() % 4 != 0) {
            return false;
        }
        return str.matches("^[A-Za-z0-9+/]*={0,2}$");
    }

    private void createConstraints(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("🔒 Creando constraints...");

        Statement stmt = null;

        try {
            stmt = connection.createStatement();
            for (Map.Entry<String, String> entry : exportData.getConstraints().entrySet()) {
                try {
                    stmt.execute(entry.getValue());
                    System.out.println("✓ Constraint creado: " + entry.getKey());
                } catch (SQLException e) {
                    System.out.println("⚠️  Error creando constraint " + entry.getKey() + ": " + e.getMessage());
                }
            }
        } finally {
            if (stmt != null) stmt.close();
        }

        // Crear foreign keys
        createForeignKeys(connection, exportData);
    }

    private void createForeignKeys(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("🔗 Creando foreign keys...");

        Statement stmt = null;

        try {
            stmt = connection.createStatement();
            for (SqlServerExportData.TableInfo tableInfo : exportData.getTables().values()) {
                String quotedTableName = "[" + tableInfo.getSchemaName() + "].[" + tableInfo.getTableName() + "]";

                for (SqlServerExportData.ForeignKeyInfo fkInfo : tableInfo.getForeignKeys()) {
                    StringBuilder sql = new StringBuilder();
                    sql.append("ALTER TABLE ").append(quotedTableName);
                    sql.append(" ADD CONSTRAINT [").append(fkInfo.getConstraintName()).append("]");
                    sql.append(" FOREIGN KEY ([").append(fkInfo.getColumnName()).append("])");
                    sql.append(" REFERENCES [").append(fkInfo.getReferencedSchema()).append("].[").append(fkInfo.getReferencedTable()).append("]");
                    sql.append(" ([").append(fkInfo.getReferencedColumn()).append("])");

                    if (!"NO_ACTION".equals(fkInfo.getDeleteRule())) {
                        sql.append(" ON DELETE ").append(fkInfo.getDeleteRule().replace("_", " "));
                    }
                    if (!"NO_ACTION".equals(fkInfo.getUpdateRule())) {
                        sql.append(" ON UPDATE ").append(fkInfo.getUpdateRule().replace("_", " "));
                    }

                    try {
                        stmt.execute(sql.toString());
                        System.out.println("✓ Foreign key creada: " + fkInfo.getConstraintName());
                    } catch (SQLException e) {
                        System.out.println("⚠️  Error creando foreign key " + fkInfo.getConstraintName() + ": " + e.getMessage());
                    }
                }
            }
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    private void createIndexes(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("🔍 Creando índices...");

        Statement stmt = null;

        try {
            stmt = connection.createStatement();
            for (Map.Entry<String, String> entry : exportData.getIndexes().entrySet()) {
                try {
                    stmt.execute(entry.getValue());
                    System.out.println("✓ Índice creado: " + entry.getKey());
                } catch (SQLException e) {
                    System.out.println("⚠️  Error creando índice " + entry.getKey() + ": " + e.getMessage());
                }
            }
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    private void createStoredProcedures(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("🏗️  Creando stored procedures...");

        Statement stmt = null;

        try {
            stmt = connection.createStatement();
            for (Map.Entry<String, String> entry : exportData.getStoredProcedures().entrySet()) {
                try {
                    stmt.execute(entry.getValue());
                    System.out.println("✓ Stored procedure creado: " + entry.getKey());
                } catch (SQLException e) {
                    System.out.println("⚠️  Error creando stored procedure " + entry.getKey() + ": " + e.getMessage());
                }
            }
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    private void createFunctions(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("🔧 Creando funciones...");

        Statement stmt = null;

        try {
            stmt = connection.createStatement();
            for (Map.Entry<String, String> entry : exportData.getFunctions().entrySet()) {
                try {
                    stmt.execute(entry.getValue());
                    System.out.println("✓ Función creada: " + entry.getKey());
                } catch (SQLException e) {
                    System.out.println("⚠️  Error creando función " + entry.getKey() + ": " + e.getMessage());
                }
            }
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    private void createViews(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("👁️  Creando vistas...");

        Statement stmt = null;

        try {
            stmt = connection.createStatement();
            for (Map.Entry<String, String> entry : exportData.getViews().entrySet()) {
                try {
                    stmt.execute(entry.getValue());
                    System.out.println("✓ Vista creada: " + entry.getKey());
                } catch (SQLException e) {
                    System.out.println("⚠️  Error creando vista " + entry.getKey() + ": " + e.getMessage());
                }
            }
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    private void createTriggers(Connection connection, SqlServerExportData exportData) throws SQLException {
        System.out.println("⚡ Creando triggers...");

        Statement stmt = null;

        try {
            stmt = connection.createStatement();
            for (Map.Entry<String, String> entry : exportData.getTriggers().entrySet()) {
                try {
                    stmt.execute(entry.getValue());
                    System.out.println("✓ Trigger creado: " + entry.getKey());
                } catch (SQLException e) {
                    System.out.println("⚠️  Error creando trigger " + entry.getKey() + ": " + e.getMessage());
                }
            }
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    // Método utilitario para unir listas de strings (Java 8 compatible)
    private String joinStringList(List<String> list, String delimiter) {
        if (list.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                result.append(delimiter);
            }
            result.append(list.get(i));
        }
        return result.toString();
    }
}