# UI 설계 산출물

이 폴더는 이코빌리지 MVP의 FE 화면 설계 산출물을 모읍니다.

## 통합 FE 디자인 가이드

- 통합 기준 문서: [../design/FE_DESIGN_GUIDE.md](../design/FE_DESIGN_GUIDE.md)

## 교사 화면 178~185

- 설계 문서: [TEACHER_SCREENS_178_185.md](./TEACHER_SCREENS_178_185.md)
- 교사 관리 콘솔 프로토타입 v3: [prototypes/teacher-admin-console-v3.html](./prototypes/teacher-admin-console-v3.html)
- 관리 콘솔형 HTML 프로토타입 v2: [prototypes/teacher-admin-console-v2.html](./prototypes/teacher-admin-console-v2.html)
- 초기 블록형 HTML 프로토타입 v1: [prototypes/teacher-ui-prototype.html](./prototypes/teacher-ui-prototype.html)

## 학생 화면 SCR-STU-001~024

- 학생용 페이지 구상: [STUDENT_PAGE_CONCEPT.md](./STUDENT_PAGE_CONCEPT.md)
- 학생 앱 디자인 페이지: [../design/STUDENT_DESIGN_PAGE.md](../design/STUDENT_DESIGN_PAGE.md)
- 학생 태블릿 프로토타입: [prototypes/student-tablet-prototype.html](./prototypes/student-tablet-prototype.html)

## 프로토타입 여는 법

교사 화면은 `docs/ui/prototypes/teacher-admin-console-v3.html` 파일을 브라우저로 열면 됩니다.

학생 화면은 `docs/ui/prototypes/student-tablet-prototype.html` 파일을 브라우저로 열면 됩니다.

왼쪽 화면 목록에서 `T-178-01`부터 `T-185-03`까지 교사 화면 24종을 클릭해 확인할 수 있습니다.

학생 프로토타입은 왼쪽 메뉴에서 홈, 지갑, 상점, 거래, 공동, 금융, 활동 묶음을 클릭해 `SCR-STU-001`부터 `SCR-STU-024`까지 24개 화면을 확인할 수 있습니다.

학생 화면의 포인트 재화는 `비빔`으로 표시하며, `SCR-STU-008 보유 아이템·캐릭터`에서 꾸미기 아이템 구매와 보유/장착/잠금 상태를 확인할 수 있습니다.

## 적용 기준

- Jira 범위: `S15P11B201-178` ~ `S15P11B201-185`
- 학생 화면 기준: [../SCREEN_SPEC.md](../SCREEN_SPEC.md)의 `SCR-STU-001` ~ `SCR-STU-024`
- 기준 해상도: 1280x800
- 디자인 톤: 교사 화면은 장식 요소를 줄인 SaaS 관리 콘솔 기준, 학생/공용 화면은 이코빌리지 파스텔 마을 UI 기준
- 개인정보 원칙: 공용 화면에는 개인 잔액, 개인 오답, 개인 거래 내역을 노출하지 않음
