package com.migrator;

public class SqlServerConfig {
    private String server;
    private int port;
    private String database;
    private String username;
    private String password;
    private String instance;

    public SqlServerConfig() {
        // Constructor vacío para JSON
    }

    public SqlServerConfig(String server, int port, String database, String username, String password, String instance) {
        this.server = server;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.instance = instance;
    }

    public String buildConnectionUrl() {
        StringBuilder url = new StringBuilder("jdbc:sqlserver://");
        url.append(server);

        if (instance != null && !instance.isEmpty()) {
            url.append("\\").append(instance);
        }

        url.append(":").append(port);
        url.append(";databaseName=").append(database);
        url.append(";encrypt=false");
        url.append(";trustServerCertificate=true");
        url.append(";loginTimeout=30");
        url.append(";socketTimeout=0");

        return url.toString();
    }

    public void validateConfig() {
        if (server == null || server.trim().isEmpty()) {
            throw new IllegalArgumentException("El servidor es requerido");
        }
        if (database == null || database.trim().isEmpty()) {
            throw new IllegalArgumentException("La base de datos es requerida");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("El usuario es requerido");
        }
        if (password == null) {
            throw new IllegalArgumentException("La contraseña es requerida");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535");
        }
    }

    // Getters y setters
    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    @Override
    public String toString() {
        return "SqlServerConfig{" +
                "server='" + server + '\'' +
                ", port=" + port +
                ", database='" + database + '\'' +
                ", username='" + username + '\'' +
                ", instance='" + instance + '\'' +
                '}';
    }
}