const PKCE_VERIFIER_KEY = "ecovillage.teacher.pkce_verifier";
const OIDC_STATE_KEY = "ecovillage.teacher.oidc_state";

function base64Url(bytes: Uint8Array) {
  let binary = "";
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function sha256(value: string) {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
}

function oidcConfig() {
  const authorizationUrl = process.env.NEXT_PUBLIC_OIDC_AUTHORIZATION_URL;
  const tokenUrl = process.env.NEXT_PUBLIC_OIDC_TOKEN_URL;
  const clientId = process.env.NEXT_PUBLIC_OIDC_CLIENT_ID;
  const redirectUri = process.env.NEXT_PUBLIC_OIDC_REDIRECT_URI ?? `${window.location.origin}/`;
  if (!authorizationUrl || !tokenUrl || !clientId) {
    throw new Error("교사 로그인 환경 설정이 아직 연결되지 않았어요.");
  }
  return { authorizationUrl, tokenUrl, clientId, redirectUri };
}

export function hasOidcCallback() {
  const query = new URLSearchParams(window.location.search);
  return query.has("code") || query.has("error");
}

export async function beginTeacherOidcLogin() {
  const config = oidcConfig();
  const verifier = base64Url(crypto.getRandomValues(new Uint8Array(48)));
  const state = base64Url(crypto.getRandomValues(new Uint8Array(24)));
  const challenge = base64Url(await sha256(verifier));
  sessionStorage.setItem(PKCE_VERIFIER_KEY, verifier);
  sessionStorage.setItem(OIDC_STATE_KEY, state);

  const url = new URL(config.authorizationUrl);
  url.search = new URLSearchParams({
    client_id: config.clientId,
    redirect_uri: config.redirectUri,
    response_type: "code",
    scope: process.env.NEXT_PUBLIC_OIDC_SCOPE ?? "openid profile email",
    code_challenge: challenge,
    code_challenge_method: "S256",
    state
  }).toString();
  window.location.assign(url.toString());
}

export async function consumeTeacherOidcCallback() {
  const config = oidcConfig();
  const query = new URLSearchParams(window.location.search);
  const error = query.get("error");
  if (error) throw new Error(query.get("error_description") ?? "교사 로그인이 취소되었어요.");

  const code = query.get("code");
  const state = query.get("state");
  const expectedState = sessionStorage.getItem(OIDC_STATE_KEY);
  const verifier = sessionStorage.getItem(PKCE_VERIFIER_KEY);
  if (!code || !state || !expectedState || state !== expectedState || !verifier) {
    throw new Error("로그인 확인 정보가 일치하지 않아요. 다시 로그인해 주세요.");
  }

  const response = await fetch(config.tokenUrl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      client_id: config.clientId,
      redirect_uri: config.redirectUri,
      code_verifier: verifier,
      code
    })
  });
  const tokens = await response.json();
  if (!response.ok || !tokens.id_token) {
    throw new Error(tokens.error_description ?? "교사 인증 정보를 가져오지 못했어요.");
  }

  sessionStorage.removeItem(PKCE_VERIFIER_KEY);
  sessionStorage.removeItem(OIDC_STATE_KEY);
  window.history.replaceState({}, "", window.location.pathname);
  return String(tokens.id_token);
}

