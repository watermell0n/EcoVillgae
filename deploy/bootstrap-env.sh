#!/usr/bin/env sh
set -eu

environment_file="${1:-.env.prod}"

if [ -f "$environment_file" ]; then
  echo "$environment_file already exists; keeping the existing secrets."
  exit 0
fi

umask 077
postgres_password="$(openssl rand -hex 32)"
jwt_secret="$(openssl rand -hex 48)"
token_pepper="$(openssl rand -hex 48)"
ai_internal_token="$(openssl rand -hex 48)"

{
  echo "DOMAIN=i15b201.p.ssafy.io"
  echo "POSTGRES_DB=ecovillage"
  echo "POSTGRES_USER=ecovillage"
  echo "POSTGRES_PASSWORD=$postgres_password"
  echo "AUTH_JWT_SECRET=$jwt_secret"
  echo "AUTH_TOKEN_PEPPER=$token_pepper"
  echo "ECOVILLAGE_AI_INTERNAL_SERVICE_TOKEN=$ai_internal_token"
  echo "ECOVILLAGE_AI_PROVIDER=FAKE"
  echo "ECOVILLAGE_AI_MODEL_ID="
  echo "ECOVILLAGE_AI_OPENAI_ENABLED=false"
  echo "ECOVILLAGE_AI_OPENAI_MODEL_ID="
  echo "OPENAI_API_KEY="
  echo "OPENAI_BASE_URL="
  echo "TEACHER_OIDC_ENABLED=false"
  echo "TEACHER_OIDC_ISSUER="
  echo "TEACHER_OIDC_JWK_SET_URI="
  echo "TEACHER_OIDC_CLIENT_ID="
} > "$environment_file"

echo "Created $environment_file with local production secrets and the FAKE AI provider."
