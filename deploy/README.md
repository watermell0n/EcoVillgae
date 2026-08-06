# SSAFY EC2 배포

`i15b201.p.ssafy.io` 단일 Ubuntu 서버용 배포 구성입니다. 외부에는 Caddy의
80·443 포트만 공개하고 Spring, FastAPI, PostgreSQL, Redis는 Docker 네트워크 안에
둡니다. 기존 Gerrit·Apache 포트 8988·8989·29418은 변경하지 않습니다.

```bash
sh deploy/bootstrap-env.sh .env.prod
sudo docker compose --env-file .env.prod -f compose.prod.yaml build
sudo docker compose --env-file .env.prod -f compose.prod.yaml up -d
sudo docker compose --env-file .env.prod -f compose.prod.yaml ps
```

최초 배포는 `FAKE` AI 공급자로 시작합니다. 실제 OpenAI/GMS 연결은 서버의
`.env.prod`에서 공급자와 자격증명을 설정한 뒤 AI와 백엔드 컨테이너만 재시작합니다.

학생 앱 빌드에는 다음 공개 주소를 사용합니다.

```text
EXPO_PUBLIC_API_BASE_URL=https://i15b201.p.ssafy.io/api
EXPO_PUBLIC_WEBSOCKET_URL=wss://i15b201.p.ssafy.io/ws/v1
```
