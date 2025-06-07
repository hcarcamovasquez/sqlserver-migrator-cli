# 🗃️ Migrador de SQL Server - CLI

Herramienta de línea de comandos para migrar bases de datos de SQL Server sin necesidad de privilegios administrativos. Exporta e importa bases de datos completas incluyendo estructura, datos y objetos de base de datos.

## 📋 Tabla de Contenidos

- [Características](#-características)
- [Requisitos](#-requisitos)
- [Instalación](#-instalación)
- [Uso Básico](#-uso-básico)
- [Ejemplos Detallados](#-ejemplos-detallados)
- [Parámetros](#-parámetros)
- [Casos de Uso](#-casos-de-uso)
- [Troubleshooting](#-troubleshooting)
- [Limitaciones](#-limitaciones)

## ✨ Características

### 🔹 **Exportación Completa**
- ✅ **Esquemas**: Todos los esquemas de usuario (no solo dbo)
- ✅ **Tablas**: Estructura completa con columnas, tipos de datos, constraints
- ✅ **Datos**: Todos los registros con manejo de tipos especiales (VARBINARY, DATETIME, etc.)
- ✅ **Stored Procedures**: Procedimientos almacenados
- ✅ **Funciones**: Funciones escalares y de tabla
- ✅ **Vistas**: Todas las vistas de usuario
- ✅ **Triggers**: Triggers de tabla
- ✅ **Índices**: Índices únicos y no únicos
- ✅ **Constraints**: Check constraints y foreign keys

### 🔹 **Importación Inteligente**
- ✅ **Orden de dependencias**: Respeta las foreign keys automáticamente
- ✅ **IDENTITY**: Manejo correcto de columnas IDENTITY con SET IDENTITY_INSERT
- ✅ **Transaccional**: Rollback automático en caso de error
- ✅ **Batch insert**: Inserción optimizada por lotes (1000 registros por batch)

### 🔹 **Características Técnicas**
- ✅ **Compresión GZIP**: Archivos hasta 90% más pequeños
- ✅ **Verificación de integridad**: Validación del backup antes de usar
- ✅ **Compatible Java 1.8**: Funciona con versiones antiguas de Java
- ✅ **Sin privilegios admin**: Solo necesita permisos de lectura/escritura en objetos
- ✅ **Manejo de errores robusto**: Información detallada de errores y rollback automático

## 🔧 Requisitos

- **Java 1.8** o superior
- **Maven 3.6+** (solo para compilación)
- **Acceso a SQL Server** con permisos de lectura/escritura en objetos (no requiere sysadmin)
- **Conectividad de red** al servidor SQL Server

## 🛠️ Instalación

### 1. **Clonar o descargar el proyecto**
```bash
git clone <repository-url>
cd sql-migrator
```

### 2. **Compilar el proyecto**
```bash
mvn clean package -DskipTests
```

### 3. **Verificar que se generó el JAR**
```bash
ls -la target/sqlserver-migrator-cli-1.0.0.jar
```

## 🚀 Uso Básico

### 📤 **Exportar Base de Datos**
```bash
java -jar target/sqlserver-migrator-cli-1.0.0.jar \
  --export \
  --server=localhost \
  --port=1433 \
  --database=MiBaseDatos \
  --username=usuario \
  --password=contraseña
```

### 📥 **Importar Base de Datos**
```bash
java -jar target/sqlserver-migrator-cli-1.0.0.jar \
  --import \
  --backup-file=sqlserver_backup_MiBaseDatos_20250606_184530.gz \
  --server=localhost \
  --port=1433 \
  --database=NuevaBaseDatos \
  --username=usuario \
  --password=contraseña
```

### 🔍 **Verificar Backup**
```bash
java -jar target/sqlserver-migrator-cli-1.0.0.jar \
  --verify \
  --backup-file=sqlserver_backup_MiBaseDatos_20250606_184530.gz
```

## 📖 Ejemplos Detallados

### 🔹 **Ejemplo con instancia nombrada (SQLEXPRESS)**
```bash
# Exportar
java -jar target/sqlserver-migrator-cli-1.0.0.jar \
  --export \
  --server=localhost \
  --instance=SQLEXPRESS \
  --database=pyp \
  --username=sa \
  --password=MyPass@word

# Importar
java -jar target/sqlserver-migrator-cli-1.0.0.jar \
  --import \
  --backup-file=sqlserver_backup_pyp_20250606_184530.gz \
  --server=localhost \
  --instance=SQLEXPRESS \
  --database=pyp_restored \
  --username=sa \
  --password=MyPass@word
```

### 🔹 **Migración entre servidores**
```bash
# Exportar desde servidor origen
java -jar target/sqlserver-migrator-cli-1.0.0.jar \
  --export \
  --server=servidor-origen.empresa.com \
  --port=1433 \
  --database=Produccion \
  --username=admin \
  --password=PasswordSeguro

# Importar a servidor destino
java -jar target/sqlserver-migrator-cli-1.0.0.jar \
  --import \
  --backup-file=sqlserver_backup_Produccion_20250606_184530.gz \
  --server=servidor-desarrollo.empresa.com \
  --port=1433 \
  --database=Desarrollo \
  --username=admin \
  --password=OtraPassword
```

### 🔹 **Importación automática (sin confirmación)**
```bash
java -jar target/sqlserver-migrator-cli-1.0.0.jar \
  --import \
  --force \
  --backup-file=backup.gz \
  --server=localhost \
  --database=AutoRestore \
  --username=sa \
  --password=MyPass@word
```

### 🔹 **Con configuración avanzada**
```bash
# Con más memoria para bases de datos grandes
java -Xmx4g -jar target/sqlserver-migrator-cli-1.0.0.jar \
  --export \
  --server=localhost \
  --database=BaseDatosGrande \
  --username=sa \
  --password=MyPass@word

# Con timeout aumentado y debug
java -Dsqlserver.timeout=120 \
  -jar target/sqlserver-migrator-cli-1.0.0.jar \
  --export \
  --debug \
  --server=servidor-lento.com \
  --database=MiDB \
  --username=usuario \
  --password=password
```

## 📋 Parámetros

### **Parámetros Requeridos**

| Parámetro | Descripción | Ejemplo |
|-----------|-------------|---------|
| `--server` | Servidor SQL Server | `--server=localhost` |
| `--database` | Nombre de la base de datos | `--database=pyp` |
| `--username` | Usuario de SQL Server | `--username=sa` |
| `--password` | Contraseña | `--password=MyPass@word` |

### **Parámetros Opcionales**

| Parámetro | Descripción | Default | Ejemplo |
|-----------|-------------|---------|---------|
| `--port` | Puerto del servidor | `1433` | `--port=1434` |
| `--instance` | Instancia nombrada | `null` | `--instance=SQLEXPRESS` |
| `--backup-file` | Archivo de backup (para import/verify) | - | `--backup-file=backup.gz` |
| `--force` | No pedir confirmación | `false` | `--force` |
| `--debug` | Mostrar información detallada | `false` | `--debug` |
| `--help`, `-h` | Mostrar ayuda | - | `--help` |

### **Comandos Principales**

| Comando | Descripción |
|---------|-------------|
| `--export` | Exportar base de datos a archivo comprimido |
| `--import` | Importar base de datos desde archivo |
| `--verify` | Verificar integridad de archivo de backup |

## 🎯 Casos de Uso

### 🔄 **1. Migración Entre Ambientes**
```bash
# Producción → Desarrollo
java -jar sqlserver-migrator-cli-1.0.0.jar \
  --export \
  --server=prod-server.empresa.com \
  --database=ProdDB \
  --username=prod_user \
  --password=prod_pass

java -jar sqlserver-migrator-cli-1.0.0.jar \
  --import \
  --backup-file=sqlserver_backup_ProdDB_*.gz \
  --server=dev-server.empresa.com \
  --database=DevDB \
  --username=dev_user \
  --password=dev_pass
```

### 💾 **2. Backup Programado**
```bash
#!/bin/bash
# Script para backup diario
DATE=$(date +%Y%m%d_%H%M%S)
java -jar /ruta/sqlserver-migrator-cli-1.0.0.jar \
  --export \
  --force \
  --server=localhost \
  --database=MiBaseDatos \
  --username=backup_user \
  --password=backup_pass

# Mover a directorio de backups
mv sqlserver_backup_MiBaseDatos_*.gz /backups/
echo "Backup completado: ${DATE}"
```

### 🧪 **3. Crear Entorno de Testing**
```bash
# Exportar datos de producción
java -jar sqlserver-migrator-cli-1.0.0.jar --export \
  --server=prod-server --database=MainDB \
  --username=readonly_user --password=safe_pass

# Crear múltiples entornos de testing
for i in {1..3}; do
  java -jar sqlserver-migrator-cli-1.0.0.jar --import --force \
    --backup-file=sqlserver_backup_MainDB_*.gz \
    --server=test-server --database=TestDB_$i \
    --username=test_user --password=test_pass
done
```

### 🔍 **4. Auditoría y Verificación**
```bash
# Verificar backup antes de usar
java -jar sqlserver-migrator-cli-1.0.0.jar \
  --verify \
  --backup-file=backup_critico.gz

# Si es válido, proceder con la restauración
if [ $? -eq 0 ]; then
  echo "Backup válido, procediendo..."
  java -jar sqlserver-migrator-cli-1.0.0.jar --import --force \
    --backup-file=backup_critico.gz \
    --server=localhost --database=RestauradoDB \
    --username=sa --password=MyPass@word
fi
```

## 🚨 Troubleshooting

### **Error de conexión:**
```
SQLException: Login failed for user
```
**Solución:**
- Verificar credenciales
- Comprobar que SQL Server Authentication esté habilitado
- Verificar que el usuario tenga permisos en la base de datos

### **Error de memoria:**
```
OutOfMemoryError
```
**Solución:**
```bash
# Aumentar heap size
java -Xmx4g -jar sqlserver-migrator-cli-1.0.0.jar [parámetros]
```

### **Error de permisos:**
```
SELECT permission denied on object
```
**Solución:**
- Verificar permisos de lectura en todas las tablas
- Considerar usar un usuario con permisos `db_datareader`
- Para export: mínimo `db_datareader`
- Para import: mínimo `db_datawriter` y `db_ddladmin`

### **Timeout de conexión:**
```
Connection timeout
```
**Solución:**
```bash
# Aumentar timeout
java -Dsqlserver.timeout=120 -jar sqlserver-migrator-cli-1.0.0.jar [parámetros]
```

### **Archivo corrupto:**
```
Error verificando backup: archivo inválido
```
**Solución:**
- Re-exportar la base de datos
- Verificar que el archivo no se corrompió durante la transferencia
- Comprobar espacio en disco disponible

### **Puerto bloqueado:**
```
Connection refused
```
**Solución:**
- Verificar que SQL Server esté ejecutándose
- Comprobar que el puerto 1433 esté abierto en firewall
- Verificar configuración de TCP/IP en SQL Server Configuration Manager

## ⚠️ Limitaciones

### **❌ No incluye:**
- Usuarios, roles y permisos de SQL Server
- Datos del sistema (msdb, master, tempdb)
- Estadísticas de tablas
- Configuraciones del servidor
- Jobs de SQL Server Agent
- Linked servers

### **❌ Restricciones:**
- Requiere conectividad directa a SQL Server
- No funciona con Always Encrypted
- No soporta bases de datos con Transparent Data Encryption (TDE) activo
- Limitado por la memoria disponible en el sistema

### **✅ Recomendaciones:**
- Para bases de datos > 10GB, usar máquina con al menos 8GB RAM
- Para producción, hacer backup nativo adicional de SQL Server
- Probar en ambiente de desarrollo antes de usar en producción
- Monitorear el uso de memoria durante la migración

## 📊 Información Técnica

### **Tipos de datos soportados:**
- ✅ VARCHAR, NVARCHAR, CHAR, NCHAR
- ✅ INT, BIGINT, SMALLINT, TINYINT
- ✅ DECIMAL, NUMERIC, FLOAT, REAL
- ✅ DATETIME, DATETIME2, DATE, TIME
- ✅ BIT, UNIQUEIDENTIFIER
- ✅ VARBINARY, BINARY (convertidos a Base64)
- ✅ TEXT, NTEXT (legacy)

### **Objetos exportados:**
1. **Esquemas** (excepto system schemas)
2. **Tablas** (estructura + datos)
3. **Primary Keys**
4. **Foreign Keys** (con reglas de cascade)
5. **Check Constraints**
6. **Índices** (únicos y no únicos)
7. **Stored Procedures**
8. **Funciones** (escalares y de tabla)
9. **Vistas**
10. **Triggers**

### **Orden de creación en import:**
1. Esquemas
2. Tablas (en orden de dependencias)
3. Datos (con IDENTITY_INSERT cuando corresponde)
4. Constraints
5. Índices
6. Stored Procedures
7. Funciones
8. Vistas
9. Triggers

## 📞 Soporte

Si encuentras problemas:

1. **Ejecuta con `--debug`** para más información
2. **Verifica los logs** en la consola
3. **Comprueba permisos** del usuario de base de datos
4. **Revisa la conectividad** al servidor SQL Server

---

**💡 Tip Final**: Para bases de datos críticas, siempre haz un backup nativo de SQL Server antes de importar datos nuevos.

## 📄 Licencia

Este proyecto está bajo la Licencia MIT.

---

**Versión**: 1.0.0  
**Compatible con**: Java 1.8+, SQL Server 2008+  
**Última actualización**: Junio 2025