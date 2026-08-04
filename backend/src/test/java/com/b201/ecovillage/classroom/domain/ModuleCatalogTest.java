package com.b201.ecovillage.classroom.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ModuleCatalogTest {

    private static final Path LEGACY_RECOMMENDATION_SEED = Path.of(
        "src/main/resources/db/migration/V1_12__seed_course_recommendations.sql"
    );

    @Test
    void exposesOnlyCanonicalSelectableCodes() {
        assertEquals(21, ModuleCatalog.definitions().size());
        assertEquals(21, ModuleCatalog.codes().size());
        assertFalse(ModuleCatalog.isCanonical("WALLET"));
        assertFalse(ModuleCatalog.isCanonical("INCOME"));
        assertFalse(ModuleCatalog.isCanonical("TAX"));
        assertFalse(ModuleCatalog.isCanonical("SALARY"));
        assertTrue(ModuleCatalog.definitions().stream().allMatch(definition ->
            definition.prerequisites().stream().allMatch(ModuleCatalog::isCanonical)
        ));
    }

    @Test
    void normalizesLegacyAliasesButNotTechnicalInfrastructure() {
        assertEquals("ROLE_SALARY", ModuleCatalog.require("income").code());
        assertEquals("ROLE_SALARY", ModuleCatalog.require(" SALARY ").code());
        assertEquals("INCOME_TAX", ModuleCatalog.require("tax").code());
        assertTrue(ModuleCatalog.find("WALLET").isEmpty());
        assertTrue(ModuleCatalog.find("UNKNOWN_MODULE").isEmpty());
    }

    @Test
    void coversEverySelectableCodeFromTheHistoricalRecommendationSeed() throws Exception {
        String sql = Files.readString(LEGACY_RECOMMENDATION_SEED);
        Set<String> historicalCodes = Pattern
            .compile("'([A-Z][A-Z0-9_]*)',\\s*'(FOUNDATION|CORE|OPTIONAL)'")
            .matcher(sql)
            .results()
            .map(match -> match.group(1))
            .collect(Collectors.toSet());

        assertEquals(22, historicalCodes.size());
        assertTrue(historicalCodes.remove(ModuleCatalog.WALLET_INFRASTRUCTURE_CODE));
        assertTrue(historicalCodes.stream().allMatch(code -> ModuleCatalog.find(code).isPresent()));
        assertEquals(
            ModuleCatalog.codes(),
            historicalCodes.stream()
                .map(code -> ModuleCatalog.require(code).code())
                .collect(Collectors.toSet())
        );
    }
}
