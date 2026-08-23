package com.fintech.ledger.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.sql.DataSource;
import java.io.InputStream;
import java.util.Properties;

public class DatabaseConfig {

    private final DataSource dataSource;
    private final JedisPool jedisPool;

    public DatabaseConfig() {
        Properties props = loadProperties();

        // Priorytet mają zmienne środowiskowe z kontenera, a w razie ich braku application.properties
        String dbUrl = getEnvOrDefault("DB_URL", props.getProperty("db.url", "jdbc:postgresql://localhost:5435/ledger_db"));
        String dbUser = getEnvOrDefault("DB_USER", props.getProperty("db.user", "ledger_user"));
        String dbPassword = getEnvOrDefault("DB_PASSWORD", props.getProperty("db.password", "ledger_password"));
        int poolSize = Integer.parseInt(getEnvOrDefault("DB_POOL_SIZE", props.getProperty("db.pool.size", "10")));

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(dbUrl);
        hikariConfig.setUsername(dbUser);
        hikariConfig.setPassword(dbPassword);
        hikariConfig.setMaximumPoolSize(poolSize);
        hikariConfig.setAutoCommit(false);

        this.dataSource = new HikariDataSource(hikariConfig);

        String redisHost = getEnvOrDefault("REDIS_HOST", props.getProperty("redis.host", "localhost"));
        int redisPort = Integer.parseInt(getEnvOrDefault("REDIS_PORT", props.getProperty("redis.port", "6379")));
        this.jedisPool = new JedisPool(new JedisPoolConfig(), redisHost, redisPort);
    }

    private String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not load application.properties", e);
        }
        return props;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }
}