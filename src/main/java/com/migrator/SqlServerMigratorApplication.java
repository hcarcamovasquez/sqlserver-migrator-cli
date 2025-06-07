package com.migrator;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = {WebMvcAutoConfiguration.class})
public class SqlServerMigratorApplication {

    public static void main(String[] args) {
        // Configurar Spring Boot para no levantar servidor web
        System.setProperty("spring.main.web-application-type", "NONE");

        SpringApplication app = new SpringApplication(SqlServerMigratorApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        app.run(args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(SqlServerMigrationService migrationService) {
        return new CommandLineRunner() {
            @Override
            public void run(String... args) throws Exception {

                if (args.length == 0) {
                    printUsageInstructions();
                    return;
                }

                try {
                    handleCommandLineArgs(args, migrationService);
                } catch (Exception e) {
                    handleError(e, args);
                }
            }
        };
    }

    private void printUsageInstructions() {
        System.out.println("📖 USO:");
        System.out.println(" ");
        System.out.println("🔹 EXPORTAR BASE DE DATOS:");
        System.out.println("  java -jar sqlserver-migrator-cli.jar \\");
        System.out.println("    --export \\");
        System.out.println("    --server=localhost \\");
        System.out.println("    --database=MiBaseDatos \\");
        System.out.println("    --username=usuario \\");
        System.out.println("    --password=contraseña");
        System.out.println(" ");
        System.out.println("🔹 EXPORTAR CON INSTANCIA NOMBRADA:");
        System.out.println("  java -jar sqlserver-migrator-cli.jar \\");
        System.out.println("    --export \\");
        System.out.println("    --server=localhost \\");
        System.out.println("    --instance=SQLEXPRESS \\");
        System.out.println("    --database=MiBaseDatos \\");
        System.out.println("    --username=usuario \\");
        System.out.println("    --password=contraseña");
        System.out.println(" ");
        System.out.println("🔹 IMPORTAR BASE DE DATOS:");
        System.out.println("  java -jar sqlserver-migrator-cli.jar \\");
        System.out.println("    --import \\");
        System.out.println("    --backup-file=backup_20240315_143022.gz \\");
        System.out.println("    --server=localhost \\");
        System.out.println("    --database=NuevaBaseDatos \\");
        System.out.println("    --username=usuario \\");
        System.out.println("    --password=contraseña");
        System.out.println(" ");
        System.out.println("🔹 VERIFICAR ARCHIVO DE BACKUP:");
        System.out.println("  java -jar sqlserver-migrator-cli.jar \\");
        System.out.println("    --verify \\");
        System.out.println("    --backup-file=backup_20240315_143022.gz");
        System.out.println(" ");
        System.out.println("📋 PARÁMETROS:");
        System.out.println("  --server=hostname          Servidor SQL Server (requerido)");
        System.out.println("  --port=1433                Puerto (opcional, default: 1433)");
        System.out.println("  --instance=SQLEXPRESS      Instancia nombrada (opcional)");
        System.out.println("  --database=nombre          Nombre de la base de datos (requerido)");
        System.out.println("  --username=usuario         Usuario de SQL Server (requerido)");
        System.out.println("  --password=contraseña      Contraseña (requerido)");
        System.out.println("  --backup-file=archivo.gz   Archivo de backup (requerido para import/verify)");
        System.out.println("  --force                    No pedir confirmación en import");
        System.out.println("  --debug                    Mostrar información detallada de errores");
        System.out.println("  --help, -h                 Mostrar esta ayuda");
        System.out.println(" ");
        System.out.println("💡 EJEMPLOS AVANZADOS:");
        System.out.println("  # Exportar con puerto personalizado");
        System.out.println("  java -jar sqlserver-migrator-cli.jar --export --server=192.168.1.10 --port=1434 ...");
        System.out.println(" ");
        System.out.println("  # Importar sin confirmación");
        System.out.println("  java -jar sqlserver-migrator-cli.jar --import --force --backup-file=backup.gz ...");
        System.out.println(" ");
        System.out.println("  # Exportar con timeout aumentado");
        System.out.println("  java -Dsqlserver.timeout=120 -jar sqlserver-migrator-cli.jar --export ...");
    }

    private void handleCommandLineArgs(String[] args, SqlServerMigrationService migrationService) throws Exception {
        if (hasArg(args, "--help") || hasArg(args, "-h")) {
            printUsageInstructions();
            return;
        }

        if (hasArg(args, "--export")) {
            handleExportCommand(args, migrationService);

        } else if (hasArg(args, "--import")) {
            handleImportCommand(args, migrationService);

        } else if (hasArg(args, "--verify")) {
            handleVerifyCommand(args, migrationService);

        } else {
            System.err.println("❌ Comando no reconocido.");
            System.err.println("💡 Usa --help para ver las opciones disponibles.");
            System.exit(1);
        }
    }

    private void handleExportCommand(String[] args, SqlServerMigrationService migrationService) throws Exception {
        System.out.println("🚀 INICIANDO EXPORTACIÓN");
        System.out.println(repeatString("═", 50));

        // Validar argumentos requeridos
        validateRequiredArgs(args, new String[]{"--server", "--database", "--username", "--password"});

        SqlServerConfig config = buildConfigFromArgs(args);
        config.validateConfig();

        // Mostrar configuración
        printConnectionInfo("EXPORTACIÓN", config);

        // Confirmar operación
        if (!hasArg(args, "--force")) {
            confirmOperation("exportar la base de datos '" + config.getDatabase() + "'");
        }

        // Ejecutar exportación
        long startTime = System.currentTimeMillis();
        String backupFile = migrationService.exportDatabase(config);
        long duration = System.currentTimeMillis() - startTime;

        // Mostrar resultados
        printSuccessResult("EXPORTACIÓN", backupFile, duration);

        System.out.println("💡 Para importar este backup usa:");
        System.out.println("   java -jar sqlserver-migrator-cli.jar --import --backup-file=" + backupFile + " [otros-parametros]");
    }

    private void handleImportCommand(String[] args, SqlServerMigrationService migrationService) throws Exception {
        System.out.println("🔄 INICIANDO IMPORTACIÓN");
        System.out.println(repeatString("═", 50));

        // Validar argumentos requeridos
        validateRequiredArgs(args, new String[]{"--backup-file", "--server", "--database", "--username", "--password"});

        String backupFile = getArgValue(args, "--backup-file");
        SqlServerConfig config = buildConfigFromArgs(args);
        config.validateConfig();

        // Verificar archivo
        java.io.File file = new java.io.File(backupFile);
        if (!file.exists()) {
            throw new IllegalArgumentException("El archivo de backup no existe: " + backupFile);
        }

        // Mostrar configuración
        printConnectionInfo("IMPORTACIÓN", config);
        System.out.println("📂 Archivo de backup: " + backupFile);
        System.out.println("📏 Tamaño del archivo: " + formatFileSize(file.length()));

        // Confirmar operación peligrosa
        if (!hasArg(args, "--force")) {
            System.out.println(" ");
            System.out.println("⚠️  ADVERTENCIA IMPORTANTE:");
            System.out.println("   Esta operación MODIFICARÁ la base de datos '" + config.getDatabase() + "'");
            System.out.println("   Si contiene datos existentes, podrían ser afectados o perdidos.");
            System.out.println("   Se recomienda hacer un backup de la base de datos destino antes de continuar.");
            System.out.println(" ");
            confirmOperation("continuar con la importación");
        }

        // Ejecutar importación
        long startTime = System.currentTimeMillis();
        boolean success = migrationService.importDatabase(backupFile, config);
        long duration = System.currentTimeMillis() - startTime;

        if (success) {
            printSuccessResult("IMPORTACIÓN", config.getDatabase(), duration);
            System.out.println("✅ La base de datos ha sido restaurada correctamente");
        } else {
            System.out.println(" ");
            System.out.println("❌ ERROR EN LA IMPORTACIÓN");
            System.out.println("🔍 Revisa los mensajes anteriores para más detalles");
            System.exit(1);
        }
    }

    private void handleVerifyCommand(String[] args, SqlServerMigrationService migrationService) throws Exception {
        System.out.println("🔍 INICIANDO VERIFICACIÓN");
        System.out.println(repeatString("═", 50));

        // Validar argumentos requeridos
        validateRequiredArgs(args, new String[]{"--backup-file"});

        String backupFile = getArgValue(args, "--backup-file");

        // Verificar archivo
        java.io.File file = new java.io.File(backupFile);
        if (!file.exists()) {
            throw new IllegalArgumentException("El archivo de backup no existe: " + backupFile);
        }

        System.out.println("📂 Archivo: " + backupFile);
        System.out.println("📏 Tamaño: " + formatFileSize(file.length()));
        System.out.println(" ");

        // Ejecutar verificación
        long startTime = System.currentTimeMillis();
        boolean valid = migrationService.verifyBackup(backupFile);
        long duration = System.currentTimeMillis() - startTime;

        System.out.println(" ");
        if (valid) {
            System.out.println("✅ VERIFICACIÓN EXITOSA");
            System.out.println("⏱️  Tiempo transcurrido: " + formatDuration(duration));
            System.out.println("🎯 El archivo de backup es válido y puede ser importado");
        } else {
            System.out.println("❌ VERIFICACIÓN FALLIDA");
            System.out.println("⚠️  El archivo de backup está corrupto o es inválido");
            System.exit(1);
        }
    }

    private SqlServerConfig buildConfigFromArgs(String[] args) {
        String server = getArgValue(args, "--server");
        String port = getArgValueOrDefault(args, "--port", "1433");
        String database = getArgValue(args, "--database");
        String username = getArgValue(args, "--username");
        String password = getArgValue(args, "--password");
        String instance = getArgValueOrDefault(args, "--instance", null);

        return new SqlServerConfig(server, Integer.parseInt(port), database, username, password, instance);
    }

    private void printConnectionInfo(String operation, SqlServerConfig config) {
        System.out.println(" ");
        System.out.println("📡 CONFIGURACIÓN DE " + operation);
        System.out.println(repeatString("─", 40));
        System.out.println("🖥️  Servidor: " + config.getServer() +
                (config.getInstance() != null ? "\\" + config.getInstance() : " ") +
                ":" + config.getPort());
        System.out.println("🗄️  Base de datos: " + config.getDatabase());
        System.out.println("👤 Usuario: " + config.getUsername());
        System.out.println("🔐 Contraseña: " + maskPassword(config.getPassword()));
        System.out.println(" ");
    }

    private void printSuccessResult(String operation, String result, long duration) {
        System.out.println(" ");
        System.out.println("🎉 " + operation + " COMPLETADA EXITOSAMENTE");
        System.out.println(repeatString("═", 50));
        System.out.println("📁 Resultado: " + result);
        System.out.println("⏱️  Tiempo transcurrido: " + formatDuration(duration));
        System.out.println("📅 Fecha/Hora: " + java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        System.out.println(" ");
    }

    private void confirmOperation(String action) {
        System.out.println("❓ ¿Estás seguro de que deseas " + action + "?");
        System.out.println("   Escribe 'SI' para continuar o cualquier otra cosa para cancelar:");
        System.out.print("👉 ");

        java.util.Scanner scanner = new java.util.Scanner(System.in);
        String response = scanner.nextLine().trim().toUpperCase();

        if (!response.equals("SI") && !response.equals("SÍ") && !response.equals("YES") && !response.equals("S")) {
            System.out.println(" ");
            System.out.println("❌ Operación cancelada por el usuario");
            System.exit(0);
        }

        System.out.println(" ");
    }

    private void validateRequiredArgs(String[] args, String[] requiredArgs) {
        java.util.List<String> missingArgs = new java.util.ArrayList<String>();

        for (String requiredArg : requiredArgs) {
            try {
                getArgValue(args, requiredArg);
            } catch (IllegalArgumentException e) {
                missingArgs.add(requiredArg);
            }
        }

        if (!missingArgs.isEmpty()) {
            System.err.println("❌ Argumentos requeridos faltantes:");
            for (String arg : missingArgs) {
                System.err.println("   " + arg);
            }
            System.err.println(" ");
            System.err.println("💡 Usa --help para ver la ayuda completa");
            System.exit(1);
        }
    }

    private void handleError(Exception e, String[] args) {
        System.err.println(" ");
        System.err.println("❌ ERROR: " + e.getMessage());

        if (hasArg(args, "--debug")) {
            System.err.println(" ");
            System.err.println("🔍 STACK TRACE DETALLADO:");
            e.printStackTrace();
        } else {
            System.err.println("💡 Usa --debug para ver el stack trace completo");
        }

        System.err.println(" ");
        System.err.println("🆘 Si el problema persiste:");
        System.err.println("   1. Verifica las credenciales de conexión");
        System.err.println("   2. Confirma que el servidor SQL Server esté accesible");
        System.err.println("   3. Asegúrate de tener permisos suficientes en la base de datos");
        System.err.println("   4. Usa --debug para obtener más información");

        System.exit(1);
    }

    // Métodos utilitarios (compatibles con Java 1.8)

    private boolean hasArg(String[] args, String arg) {
        for (String a : args) {
            if (a.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private String getArgValue(String[] args, String key) {
        for (String arg : args) {
            if (arg.startsWith(key + "=")) {
                return arg.substring(key.length() + 1);
            }
        }
        throw new IllegalArgumentException("Argumento requerido: " + key);
    }

    private String getArgValueOrDefault(String[] args, String key, String defaultValue) {
        try {
            return getArgValue(args, key);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    private String repeatString(String str, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            result.append(str);
        }
        return result.toString();
    }

    private String maskPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "(vacía)";
        }
        if (password.length() <= 2) {
            StringBuilder masked = new StringBuilder();
            for (int i = 0; i < password.length(); i++) {
                masked.append("*");
            }
            return masked.toString();
        }

        StringBuilder masked = new StringBuilder();
        masked.append(password.charAt(0));
        for (int i = 1; i < password.length() - 1; i++) {
            masked.append("*");
        }
        masked.append(password.charAt(password.length() - 1));
        return masked.toString();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return String.format("%d min %d seg", minutes, seconds);
        } else {
            return String.format("%d seg", seconds);
        }
    }
}