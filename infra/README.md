# 인프라

Jenkins와 Terraform 등 공통 인프라 파일의 작업 영역입니다.

```text
jenkins/      테스트·빌드·배포 파이프라인
terraform/    AWS 서울 리전 인프라
```

- 실제 비밀번호, 토큰, 인증서와 Terraform 상태 파일은 커밋하지 않습니다.
- 운영 배포와 EAS 업데이트에는 승인 단계를 둡니다.
- Flyway 마이그레이션은 애플리케이션 배포와 분리해 검증합니다.
- 루트 `compose.dev.yml`은 PostgreSQL 17과 Redis의 로컬 개발용 구성입니다.
