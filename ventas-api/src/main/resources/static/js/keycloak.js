/* =====================================================================
   Inicio de sesión con Keycloak: OAuth2 Authorization Code + PKCE.
   El navegador nunca ve la contraseña: el usuario la escribe en Keycloak.
   ===================================================================== */

export const KEYCLOAK = {
    url: 'http://localhost:8180',
    realm: 'dwf404',
    clientId: 'ventas-web',
};

const base = () => `${KEYCLOAK.url}/realms/${KEYCLOAK.realm}/protocol/openid-connect`;
const redirectUri = () => `${window.location.origin}/`;

function aleatorio(bytes = 32) {
    const a = new Uint8Array(bytes);
    crypto.getRandomValues(a);
    return base64url(a);
}

function base64url(buffer) {
    return btoa(String.fromCharCode(...new Uint8Array(buffer))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function challenge(verifier) {
    const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
    return base64url(hash);
}

/** Comprueba que Keycloak responda antes de redirigir (para mostrar un mensaje claro si está apagado). */
export async function disponible() {
    try {
        const r = await fetch(`${KEYCLOAK.url}/realms/${KEYCLOAK.realm}/.well-known/openid-configuration`, { method: 'GET' });
        return r.ok;
    } catch {
        return false;
    }
}

/** Paso 1: redirige a la página de login de Keycloak. */
export async function iniciarLogin() {
    const verifier = aleatorio(48);
    const state = aleatorio(16);
    sessionStorage.setItem('kc.verifier', verifier);
    sessionStorage.setItem('kc.state', state);
    const params = new URLSearchParams({
        client_id: KEYCLOAK.clientId,
        redirect_uri: redirectUri(),
        response_type: 'code',
        scope: 'openid profile email',
        state,
        code_challenge: await challenge(verifier),
        code_challenge_method: 'S256',
    });
    window.location.assign(`${base()}/auth?${params}`);
}

/**
 * Paso 2: al volver de Keycloak con ?code=..., cambia el código por el access token.
 * Devuelve {token, idToken} o null si la URL no trae un código.
 */
export async function procesarRetorno() {
    const url = new URL(window.location.href);
    const code = url.searchParams.get('code');
    const error = url.searchParams.get('error');
    if (!code && !error) return null;

    window.history.replaceState({}, document.title, url.pathname);
    if (error) throw new Error(url.searchParams.get('error_description') || error);

    const state = url.searchParams.get('state');
    const esperado = sessionStorage.getItem('kc.state');
    const verifier = sessionStorage.getItem('kc.verifier');
    sessionStorage.removeItem('kc.state');
    sessionStorage.removeItem('kc.verifier');
    if (!state || state !== esperado || !verifier) throw new Error('La respuesta de Keycloak no coincide con la solicitud (state inválido).');

    const r = await fetch(`${base()}/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
            grant_type: 'authorization_code', code, redirect_uri: redirectUri(),
            client_id: KEYCLOAK.clientId, code_verifier: verifier,
        }),
    });
    const data = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(data.error_description || 'Keycloak rechazó el intercambio del código.');
    if (data.id_token) sessionStorage.setItem('kc.idToken', data.id_token);
    return { token: data.access_token };
}

/** Cierra también la sesión en Keycloak (SSO). */
export function cerrarSesionKeycloak() {
    const idToken = sessionStorage.getItem('kc.idToken');
    sessionStorage.removeItem('kc.idToken');
    const params = new URLSearchParams({ client_id: KEYCLOAK.clientId, post_logout_redirect_uri: redirectUri() });
    if (idToken) params.set('id_token_hint', idToken);
    window.location.assign(`${base()}/logout?${params}`);
}
