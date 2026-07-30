package com.b201.ecovillage.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class CourseRecommendationSeedTest {

    private static final Path SEED = Path.of(
        "src/main/resources/db/migration/V1_12__seed_course_recommendations.sql"
    );

    @Test
    void containsTwelveGradeAndSizeCombinations() throws Exception {
        String sql = Files.readString(SEED);
        long combinations = Pattern
            .compile("'G[456]_(ONE_SESSION|ONE_WEEK|ONE_MONTH|SEMESTER)'")
            .matcher(sql)
            .results()
            .count();

        assertEquals(12, combinations);
        assertTrue(sql.contains("'G4_ONE_WEEK'"));
        assertTrue(sql.contains("'G5_SEMESTER'"));
        assertTrue(sql.contains("'G6_SEMESTER'"));
    }

    @Test
    void oneSessionRecommendationsContainOnlyQuizModules() throws Exception {
        String sql = Files.readString(SEED);
        for (String suffix : new String[] {"401", "501", "601"}) {
            Pattern moduleLine = Pattern.compile(
                "\\('c1000000-0000-4000-8000-000000000" + suffix
                    + "'::uuid, '([^']+)'"
            );
            var modules = moduleLine.matcher(sql).results()
                .map(match -> match.group(1))
                .toList();
            assertEquals(2, modules.size());
            assertTrue(modules.stream().allMatch(code -> code.startsWith("QUIZ_")));
        }
    }
}
