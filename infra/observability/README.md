# 관측성 골격

개발 단계에서는 `otel-collector.yaml`이 OTLP trace·metric·log를 받아 debug
exporter로 출력합니다. 실제 AWS 배포 전 다음을 연결합니다.

- OpenTelemetry Collector → CloudWatch Logs·Metrics·X-Ray
- 학생 앱·교사 웹·백엔드·AI 서비스 → Sentry 프로젝트
- 거래번호, `request_id`, AI 실행 ID를 trace attribute로 연결
- 운영 알람 수신 채널과 개인정보 마스킹 규칙

`SENTRY_DSN`과 AWS 인증정보는 저장소에 커밋하지 않습니다.
