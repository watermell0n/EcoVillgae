variable "project_name" {
  description = "AWS 리소스 이름에 사용하는 프로젝트 이름"
  type        = string
  default     = "ecovillage"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,31}$", var.project_name))
    error_message = "project_name은 영문 소문자로 시작하는 3~32자 kebab-case여야 합니다."
  }
}

variable "environment" {
  description = "배포 환경"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "stage", "prod"], var.environment)
    error_message = "environment는 dev, stage, prod 중 하나여야 합니다."
  }
}

variable "aws_region" {
  description = "기본 AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "owner" {
  description = "비용·운영 책임 태그"
  type        = string
  default     = "b201"
}

variable "monthly_budget_usd" {
  description = "예산 경보 기준 월 비용. 실제 예산 자원은 AWS 계정 확인 후 추가합니다."
  type        = number
  default     = 100

  validation {
    condition     = var.monthly_budget_usd > 0
    error_message = "monthly_budget_usd는 0보다 커야 합니다."
  }
}
