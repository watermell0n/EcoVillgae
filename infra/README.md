# 인프라

Jenkins, Terraform과 관측 설정의 공통 작업 영역입니다. 현재는 개발 기준선과
검증 골격만 준비했으며 AWS 과금 자원은 생성하지 않았습니다.

```text
jenkins/      테스트·빌드·배포 파이프라인
terraform/    AWS 서울 리전 인프라
observability/ OpenTelemetry·Sentry·CloudWatch 연결 골격
```

- 실제 비밀번호, 토큰, 인증서와 Terraform 상태 파일은 커밋하지 않습니다.
- 운영 배포와 EAS 업데이트에는 승인 단계를 둡니다.
- Flyway 마이그레이션은 애플리케이션 배포와 분리해 검증합니다.
- 루트 `compose.yaml`은 PostgreSQL 17, Redis와 OpenTelemetry Collector의
  기본 로컬 개발 구성입니다. 운영 환경은 이 파일을 그대로 사용하지 않고
  별도 배포 명세로 관리합니다.
- 백엔드까지 실행할 때는 `docker compose --profile app up -d --build`를
  사용합니다.

범위와 구축 순서는 [인프라 기준선](../docs/INFRASTRUCTURE_BASELINE.md)을
따릅니다.
