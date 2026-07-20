CREATE TABLE student_role_catalog (
    participant_number INTEGER PRIMARY KEY CHECK (participant_number BETWEEN 1 AND 99),
    role_name VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO student_role_catalog (participant_number, role_name) VALUES
    (1, '라이츄'),
    (2, '창체의 신'),
    (3, '미술 부장'),
    (4, '출석/배움공책'),
    (5, '태블릿'),
    (6, '바람'),
    (7, '벌레의 지배자'),
    (8, '입체 기동 장치'),
    (9, '마동석 부장'),
    (10, '럭키비키 다육이'),
    (11, '사회 부장'),
    (12, '수학 부장'),
    (13, '영체 비빔밥'),
    (14, '수리, 뒷게시판'),
    (15, '쓰레기 검사'),
    (16, '지우개'),
    (17, '입체 기동 장치'),
    (19, '실과 부장'),
    (20, '뽀롱 불을 꺼라'),
    (21, '식단표'),
    (22, '가방 검사'),
    (23, '무단 버스킹 아저씨'),
    (24, '시간의 지배자'),
    (28, '국수');
