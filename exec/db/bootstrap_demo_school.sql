-- 개인정보가 없는 초기 학교 레코드입니다.
-- 빈 DB 복원 후 최초 교사 회원가입에 사용할 학교 코드: ECO-DEMO
INSERT INTO schools (
    id,
    school_code,
    name,
    status,
    created_at,
    updated_at
) VALUES (
    '019fed00-0000-7000-8000-000000000001',
    'ECO-DEMO',
    '이코빌리지 데모 학교',
    'ACTIVE',
    now(),
    now()
) ON CONFLICT (school_code) DO NOTHING;
