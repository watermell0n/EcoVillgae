package com.b201.ecovillage.common.config;

import java.sql.Timestamp;
import java.time.Instant;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

/**
 * SQL 파라미터로 넘어온 {@link Instant}를 Timestamp로 바꿔 주는 JdbcTemplate이다.
 *
 * <p>PostgreSQL 드라이버는 Instant를 받으면 "Can't infer the SQL type"으로 거절한다.
 * 이 프로젝트는 도메인 시각을 모두 Instant로 다루므로, 서비스마다 변환을 기억해야 하는
 * 구조에서는 한 곳만 빠뜨려도 그 요청이 통째로 500이 된다. 실제로 부동산·청약 쓰기,
 * 자리 계약 신청, 대출 상환 스케줄러가 모두 이 이유로 실패했다.
 *
 * <p>그래서 서비스가 아니라 JdbcTemplate 한 곳에서 바꾼다. 앞으로 새 코드가 Instant를
 * 그대로 넘겨도 같은 문제가 생기지 않는다.
 */
@Configuration
public class JdbcInstantConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "instantAwareJdbcTemplate")
    public JdbcTemplate instantAwareJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource) {
            @Override
            protected PreparedStatementSetter newArgPreparedStatementSetter(Object[] args) {
                return super.newArgPreparedStatementSetter(toSqlArguments(args));
            }
        };
    }

    /** Instant만 Timestamp로 바꾸고 나머지는 그대로 둔다. */
    private static Object[] toSqlArguments(Object[] args) {
        if (args == null) {
            return null;
        }
        Object[] converted = new Object[args.length];
        for (int index = 0; index < args.length; index++) {
            converted[index] = args[index] instanceof Instant value
                ? Timestamp.from(value)
                : args[index];
        }
        return converted;
    }
}
