/* =====================================================================
   Cliente HTTP de la API: agrega el token Bearer y normaliza los errores.
   ===================================================================== */

const TOKEN_KEY = 'ventassv.token';
const ORIGIN_KEY = 'ventassv.origin';

/**
 * El token se guarda en sessionStorage: vive solo mientras la pestaña esté abierta
 * y no se comparte con otras pestañas. Nunca se guarda la contraseña.
 */
export const session = {
    get token() { return leer(TOKEN_KEY); },
    get origin() { return leer(ORIGIN_KEY) || 'api'; },
    set(token, origin = 'api') { escribir(TOKEN_KEY, token); escribir(ORIGIN_KEY, origin); },
    clear() { borrar(TOKEN_KEY); borrar(ORIGIN_KEY); },
};

function leer(k) { try { return sessionStorage.getItem(k); } catch { return null; } }
function escribir(k, v) { try { sessionStorage.setItem(k, v); } catch { /* almacenamiento no disponible */ } }
function borrar(k) { try { sessionStorage.removeItem(k); } catch { /* ignorar */ } }

/** Error de la API con el código HTTP y el cuerpo JSON (ApiError del backend). */
export class ApiError extends Error {
    constructor(status, data) {
        super((data && data.mensaje) || `Error HTTP ${status}`);
        this.status = status;
        this.data = data || {};
        this.detalles = Array.isArray(this.data.detalles) ? this.data.detalles : [];
    }
}

let alNoAutorizado = null;
/** Se invoca cuando una llamada autenticada recibe 401 (token vencido o inválido). */
export function onUnauthorized(fn) { alNoAutorizado = fn; }

/** Petición "cruda": devuelve {status, data} sin lanzar excepción. La usa el laboratorio. */
export async function apiRaw(path, { method = 'GET', body, auth = true, token } = {}) {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const bearer = token !== undefined ? token : (auth ? session.token : null);
    if (bearer) headers.Authorization = `Bearer ${bearer}`;

    let res;
    try {
        res = await fetch(path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
    } catch {
        return { status: 0, data: { mensaje: 'No se pudo conectar con el servidor. Verifique que la API esté en ejecución.' } };
    }
    let data = null;
    const texto = await res.text();
    if (texto) {
        try { data = JSON.parse(texto); } catch { data = { mensaje: texto }; }
    }
    return { status: res.status, data, headers: res.headers };
}

/** Petición normal: devuelve el JSON o lanza ApiError. */
export async function api(path, opciones = {}) {
    const { status, data } = await apiRaw(path, opciones);
    if (status >= 200 && status < 300) return data;
    const usaSesion = opciones.auth !== false && opciones.token === undefined;
    if (status === 401 && usaSesion && session.token && alNoAutorizado) alNoAutorizado(data);
    throw new ApiError(status, data);
}

/** Decodifica (sin verificar) la parte central del JWT, solo para mostrarla en pantalla. */
export function decodeJwt(token) {
    try {
        const parte = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        const json = decodeURIComponent(atob(parte).split('').map(c => '%' + c.charCodeAt(0).toString(16).padStart(2, '0')).join(''));
        return JSON.parse(json);
    } catch {
        return null;
    }
}

/** Devuelve una copia del token con el rol cambiado a ADMIN pero con la firma original (para la prueba 4). */
export function tamperToken(token) {
    const [h, p, s] = token.split('.');
    const claims = decodeJwt(token) || {};
    claims.roles = ['ADMIN'];
    if (claims.realm_access) claims.realm_access.roles = ['ADMIN'];
    const nuevo = btoa(unescape(encodeURIComponent(JSON.stringify(claims))))
        .replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
    return [h, nuevo, s].join('.');
}
