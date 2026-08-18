package com.innowise.paymentservice.config;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.liquibase", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class MongoLiquibaseConfig {

    private static final String CLASSPATH_PREFIX = "classpath:";

    @Bean
    public InitializingBean mongoLiquibaseUpdate(@Value("${spring.mongodb.uri}") String mongoUri,
                                                 @Value("${spring.liquibase.change-log}") String changeLog) {
        return () -> {
            String changeLogPath = changeLog.startsWith(CLASSPATH_PREFIX)
                    ? changeLog.substring(CLASSPATH_PREFIX.length())
                    : changeLog;
            ResourceAccessor resourceAccessor =
                    new ClassLoaderResourceAccessor(getClass().getClassLoader());
            Database database = DatabaseFactory.getInstance()
                    .openDatabase(mongoUri, null, null, null, resourceAccessor);

            try (Liquibase liquibase = new Liquibase(changeLogPath, resourceAccessor, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
            log.info("Liquibase changelog {} applied to MongoDB", changeLogPath);
        };
    }
}
