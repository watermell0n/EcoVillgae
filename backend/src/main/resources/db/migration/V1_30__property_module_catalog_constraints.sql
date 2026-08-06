-- Keep database module-code constraints aligned with ModuleCatalog.
-- REAL_ESTATE and HOUSING_SUBSCRIPTION were introduced after the original
-- canonical catalog constraint in V1_19.

ALTER TABLE course_template_modules
    DROP CONSTRAINT ck_course_template_modules_canonical_code;

ALTER TABLE course_template_modules
    ADD CONSTRAINT ck_course_template_modules_canonical_code CHECK (module_code IN (
        'ROLE_SALARY', 'INCOME_TAX', 'STORE', 'BUDGET', 'PAYSLIP',
        'PUBLIC_FUND', 'PUBLIC_BUDGET', 'DEPOSIT', 'SAVINGS', 'CREDIT',
        'LOAN', 'REAL_ESTATE', 'HOUSING_SUBSCRIPTION', 'INVESTMENT',
        'RISK_MANAGEMENT', 'MARKET_PRICE', 'TRADE', 'PHYSICAL_STORE',
        'CHARACTER', 'QUIZ_GAME', 'QUIZ_TEST', 'AI_EVENT', 'CUMULATIVE_REPORT'
    ));

ALTER TABLE classroom_modules
    DROP CONSTRAINT ck_classroom_modules_canonical_code;

ALTER TABLE classroom_modules
    ADD CONSTRAINT ck_classroom_modules_canonical_code CHECK (module_code IN (
        'ROLE_SALARY', 'INCOME_TAX', 'STORE', 'BUDGET', 'PAYSLIP',
        'PUBLIC_FUND', 'PUBLIC_BUDGET', 'DEPOSIT', 'SAVINGS', 'CREDIT',
        'LOAN', 'REAL_ESTATE', 'HOUSING_SUBSCRIPTION', 'INVESTMENT',
        'RISK_MANAGEMENT', 'MARKET_PRICE', 'TRADE', 'PHYSICAL_STORE',
        'CHARACTER', 'QUIZ_GAME', 'QUIZ_TEST', 'AI_EVENT', 'CUMULATIVE_REPORT'
    ));

ALTER TABLE autonomous_schedule_modules
    DROP CONSTRAINT ck_autonomous_schedule_modules_canonical_code;

ALTER TABLE autonomous_schedule_modules
    ADD CONSTRAINT ck_autonomous_schedule_modules_canonical_code CHECK (module_code IN (
        'ROLE_SALARY', 'INCOME_TAX', 'STORE', 'BUDGET', 'PAYSLIP',
        'PUBLIC_FUND', 'PUBLIC_BUDGET', 'DEPOSIT', 'SAVINGS', 'CREDIT',
        'LOAN', 'REAL_ESTATE', 'HOUSING_SUBSCRIPTION', 'INVESTMENT',
        'RISK_MANAGEMENT', 'MARKET_PRICE', 'TRADE', 'PHYSICAL_STORE',
        'CHARACTER', 'QUIZ_GAME', 'QUIZ_TEST', 'AI_EVENT', 'CUMULATIVE_REPORT'
    ));
