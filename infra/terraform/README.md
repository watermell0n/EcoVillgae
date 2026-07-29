# Terraform

AWS 서울 리전 인프라의 공통 provider·환경·태그 계약입니다. 현재 구성은 인증
계정, 리전과 가용영역만 읽으며 과금 자원을 만들지 않습니다.

```bash
terraform fmt -recursive
terraform init -backend=false
terraform validate
terraform plan -var-file=dev.tfvars
```

`dev.tfvars.example`을 `dev.tfvars`로 복사해 환경값을 설정합니다. 두 파일은
환경별 로컬 설정이므로 예제만 Git으로 관리합니다.

실제 원격 상태를 사용할 때는 `backend.hcl.example`을 `backend.dev.hcl`로
복사해 상태 버킷을 입력하고 권한을 검토한 다음 아래처럼 초기화합니다.

```bash
terraform init -reconfigure -backend-config=backend.dev.hcl
```

원격 상태 파일은 S3 버전 관리를 켜고 `use_lockfile = true`를 유지합니다.

후속 리소스:

- VPC·서브넷·보안그룹·ALB
- ECS Fargate·ECR
- RDS PostgreSQL·ElastiCache Redis
- S3·CloudFront·SQS
- Secrets Manager 또는 Parameter Store
- CloudWatch·OpenTelemetry와 예산 경보

구축 순서와 선행 결정은 [인프라 기준선](../../docs/INFRASTRUCTURE_BASELINE.md)을
따릅니다. 팀 승인 전 `terraform apply`를 실행하지 않습니다.
