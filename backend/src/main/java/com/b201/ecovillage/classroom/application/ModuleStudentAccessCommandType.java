package com.b201.ecovillage.classroom.application;

/**
 * SUSPEND는 모듈을 켠 상태로 두고 학생 화면에서만 잠급니다.
 * RESUME은 다시 학생이 쓸 수 있게 엽니다. 둘 다 설정과 데이터를 지우지 않습니다.
 */
public enum ModuleStudentAccessCommandType {
    SUSPEND,
    RESUME
}
