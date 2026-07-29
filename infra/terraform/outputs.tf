output "aws_account_id" {
  description = "현재 Terraform 인증이 가리키는 AWS 계정"
  value       = data.aws_caller_identity.current.account_id
}

output "aws_region" {
  description = "현재 AWS 리전"
  value       = data.aws_region.current.region
}

output "available_zone_names" {
  description = "네트워크 설계에 사용할 수 있는 가용 영역"
  value       = data.aws_availability_zones.available.names
}

output "name_prefix" {
  description = "후속 AWS 리소스의 공통 이름 접두사"
  value       = local.name_prefix
}
