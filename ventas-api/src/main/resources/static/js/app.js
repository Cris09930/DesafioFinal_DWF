/* =====================================================================
   VentasSV - aplicación principal
   ===================================================================== */
import { api, apiRaw, session, decodeJwt, tamperToken, onUnauthorized, ApiError } from './api.js';
import {
    esc, money, fecha, iniciales, toast, success, confirmar, alertaError, validar, limpiarErrores,
    aplicarErroresServidor, cargando, marcarError, EMAIL_RE,
} from './ui.js';
import {
    particulas, maquinaEscribir, contar, escalonar, entrarVista, sacudir, resaltar, salir, activarRipple, confeti,
} from './anim.js';
import * as kc from './keycloak.js';

const $ = (sel, ctx = document) => ctx.querySelector(sel);
const $$ = (sel, ctx = document) => [...ctx.querySelectorAll(sel)];

const estado = {
    me: null,
    perms: new Set(),
    rol: null,
    productos: [],
    carrito: [],
    usuarios: [],
    vista: 'inicio',
    timer: null,
    avisoExpiracion: false,
};

const puede = p => estado.perms.has(p);
/** Acepta fechas ISO ("2026-10-27T15:00:00Z") o epoch en segundos. */
const aFecha = v => new Date(typeof v === 'number' ? v * 1000 : v);
const TITULOS = {
    inicio: 'Inicio', productos: 'Productos', pedidos: 'Pedidos', usuarios: 'Usuarios',
    sesion: 'Mi sesión', laboratorio: 'Laboratorio de seguridad',
};

let fondo = null;

/* =====================================================================
   ARRANQUE
   ===================================================================== */
document.addEventListener('DOMContentLoaded', async () => {
    aplicarTemaGuardado();
    activarRipple();
    registrarEventosGenerales();
    onUnauthorized(() => sesionExpirada());

    try {
        const kcRes = await kc.procesarRetorno();
        if (kcRes?.token) {
            session.set(kcRes.token, 'keycloak');
            toast('success', 'Sesión iniciada con Keycloak');
        }
    } catch (e) {
        await alertaError(new ApiError(401, { mensaje: e.message }), 'No se pudo iniciar sesión con Keycloak');
    }

    if (session.token && !tokenVencido(session.token)) {
        await entrarAplicacion();
    } else {
        session.clear();
        mostrarAcceso();
    }
    setTimeout(() => $('#splash').classList.add('hide'), 350);
});

function tokenVencido(token) {
    const c = decodeJwt(token);
    return !c?.exp || c.exp * 1000 <= Date.now();
}

/* =====================================================================
   ACCESO: LOGIN / REGISTRO / KEYCLOAK
   ===================================================================== */
function mostrarAcceso() {
    $('#app-view').classList.add('d-none');
    $('#auth-view').classList.remove('d-none');
    if (!fondo) {
        fondo = particulas($('#particles'));
        maquinaEscribir($('#hero-typed'), ['sin riesgos.', 'con roles claros.', 'con tokens JWT.', 'con Keycloak.']);
    } else {
        fondo.reanudar();
    }
    entrarVista($('#auth-card'));
}

function registrarEventosGenerales() {
    // Mostrar / ocultar contraseña
    $$('[data-toggle-password]').forEach(b => b.addEventListener('click', () => {
        const input = $(b.dataset.togglePassword);
        const ver = input.type === 'password';
        input.type = ver ? 'text' : 'password';
        b.innerHTML = `<i class="bi bi-eye${ver ? '-slash' : ''}"></i>`;
    }));

    // Rellenar usuario de demostración
    $$('[data-demo]').forEach(b => b.addEventListener('click', () => {
        $('#login-email').value = b.dataset.demo;
        $('#login-password').focus();
        resaltar($('#login-email'));
    }));

    $('#form-login').addEventListener('submit', login);
    $('#form-registro').addEventListener('submit', registrar);
    $('#reg-password').addEventListener('input', e => medirFortaleza(e.target.value));
    $('#btn-keycloak').addEventListener('click', loginKeycloak);

    // Navegación
    $$('[data-view]').forEach(a => a.addEventListener('click', e => { e.preventDefault(); irA(a.dataset.view); }));
    window.addEventListener('hashchange', () => { if (estado.me) irA(location.hash.slice(1) || 'inicio', false); });
    $('#btn-menu').addEventListener('click', () => alternarMenu(true));
    $('#sidebar-backdrop').addEventListener('click', () => alternarMenu(false));
    $('#btn-logout').addEventListener('click', cerrarSesion);
    $('#btn-theme').addEventListener('click', alternarTema);

    // Productos
    $('#prod-search').addEventListener('input', pintarProductos);
    $('#prod-sort').addEventListener('change', pintarProductos);
    $('#btn-new-product').addEventListener('click', () => abrirModalProducto());
    $('#form-producto').addEventListener('submit', guardarProducto);
    $('#prod-descripcion').addEventListener('input', e => { $('#desc-count').textContent = e.target.value.length; });

    // Pedidos
    $('#btn-add-item').addEventListener('click', agregarAlCarrito);
    $('#form-pedido').addEventListener('submit', registrarPedido);
    $('#btn-reload-orders').addEventListener('click', cargarPedidos);

    // Usuarios
    $('#user-search').addEventListener('input', pintarUsuarios);

    // Sesión / laboratorio
    $('#btn-copy-token').addEventListener('click', async () => {
        try { await navigator.clipboard.writeText(session.token); toast('success', 'Token copiado al portapapeles'); }
        catch { toast('error', 'No se pudo copiar el token'); }
    });
    $('#btn-run-all').addEventListener('click', ejecutarLaboratorio);
}

async function login(e) {
    e.preventDefault();
    const form = e.currentTarget;
    const email = form.email.value.trim();
    const password = form.password.value;
    const ok = validar(form, [
        { campo: 'email', valido: email.length > 0, mensaje: 'Ingresa tu correo electrónico' },
        { campo: 'email', valido: EMAIL_RE.test(email), mensaje: 'El correo no tiene un formato válido' },
        { campo: 'password', valido: password.length > 0, mensaje: 'Ingresa tu contraseña' },
    ]);
    if (!ok) { sacudir($('#auth-card')); return; }

    const btn = form.querySelector('[type=submit]');
    cargando(btn, true, 'Verificando…');
    try {
        const r = await api('/api/auth/login', { method: 'POST', body: { email, password }, auth: false });
        session.set(r.accessToken, 'api');
        form.reset();
        limpiarErrores(form);
        await entrarAplicacion();
        toast('success', `¡Bienvenido! Rol: ${r.rol}`);
    } catch (err) {
        sacudir($('#auth-card'));
        if (err.status === 401) {
            marcarError(form, 'password', 'Correo o contraseña incorrectos');
            form.querySelector('[name=email]').classList.add('is-invalid');
        } else if (!aplicarErroresServidor(form, err)) {
            alertaError(err);
        }
    } finally {
        cargando(btn, false);
    }
}

function medirFortaleza(clave) {
    let puntos = 0;
    if (clave.length >= 8) puntos++;
    if (clave.length >= 12) puntos++;
    if (/[a-z]/.test(clave) && /[A-Z]/.test(clave)) puntos++;
    if (/\d/.test(clave)) puntos++;
    if (/[^A-Za-z0-9]/.test(clave)) puntos++;
    const niveles = [
        ['0%', 'bg-secondary', 'Mínimo 8 caracteres, con letras y números'],
        ['20%', 'bg-danger', 'Muy débil'], ['40%', 'bg-danger', 'Débil'], ['60%', 'bg-warning', 'Aceptable'],
        ['80%', 'bg-info', 'Fuerte'], ['100%', 'bg-success', '¡Excelente!'],
    ];
    const [w, cls, txt] = clave ? niveles[Math.max(1, puntos)] : niveles[0];
    const bar = $('#strength-bar');
    bar.className = `progress-bar ${cls}`;
    bar.style.width = w;
    bar.style.transition = 'width .35s ease';
    $('#strength-text').textContent = txt;
}

async function registrar(e) {
    e.preventDefault();
    const form = e.currentTarget;
    const datos = {
        nombreCompleto: form.nombreCompleto.value.trim(),
        email: form.email.value.trim(),
        password: form.password.value,
    };
    const ok = validar(form, [
        { campo: 'nombreCompleto', valido: datos.nombreCompleto.length >= 3, mensaje: 'Escribe tu nombre (mínimo 3 caracteres)' },
        { campo: 'email', valido: EMAIL_RE.test(datos.email), mensaje: 'Escribe un correo válido, por ejemplo nombre@correo.com' },
        { campo: 'password', valido: datos.password.length >= 8, mensaje: 'La contraseña debe tener al menos 8 caracteres' },
        { campo: 'password', valido: /[A-Za-z]/.test(datos.password) && /\d/.test(datos.password), mensaje: 'Incluye al menos una letra y un número' },
        { campo: 'password2', valido: form.password2.value === datos.password && datos.password.length > 0, mensaje: 'Las contraseñas no coinciden' },
    ]);
    if (!ok) { sacudir($('#auth-card')); return; }

    const btn = form.querySelector('[type=submit]');
    cargando(btn, true, 'Creando cuenta…');
    try {
        await api('/api/auth/register', { method: 'POST', body: datos, auth: false });
        await success('¡Cuenta creada!', 'Ya puedes iniciar sesión con tu correo y contraseña.');
        form.reset(); limpiarErrores(form); medirFortaleza('');
        bootstrap.Tab.getOrCreateInstance($('[data-bs-target="#tab-login"]')).show();
        $('#login-email').value = datos.email;
        $('#login-password').focus();
    } catch (err) {
        sacudir($('#auth-card'));
        if (err.status === 409) marcarError(form, 'email', 'Este correo ya está registrado');
        else if (!aplicarErroresServidor(form, err)) alertaError(err);
    } finally {
        cargando(btn, false);
    }
}

async function loginKeycloak() {
    const btn = $('#btn-keycloak');
    cargando(btn, true, 'Conectando con Keycloak…');
    if (!(await kc.disponible())) {
        cargando(btn, false);
        window.Swal.fire({
            icon: 'info', title: 'Keycloak no está disponible',
            html: `No se pudo contactar a <code>${esc(kc.KEYCLOAK.url)}</code>.<br>Inícielo con el script <code>keycloak\\iniciar-keycloak.ps1</code> y vuelva a intentarlo.`,
            confirmButtonText: 'Entendido', buttonsStyling: false, customClass: { confirmButton: 'btn btn-brand btn-swal' },
        });
        return;
    }
    await kc.iniciarLogin();
}

/* =====================================================================
   SESIÓN
   ===================================================================== */
async function entrarAplicacion() {
    try {
        estado.me = await api('/api/usuarios/me');
    } catch (err) {
        session.clear();
        mostrarAcceso();
        if (err.status !== 401) alertaError(err);
        return;
    }
    estado.perms = new Set(estado.me.authorities.filter(a => !a.startsWith('ROLE_')));
    estado.rol = (estado.me.authorities.find(a => a.startsWith('ROLE_')) || 'ROLE_SIN').slice(5);

    fondo?.detener();
    $('#auth-view').classList.add('d-none');
    $('#app-view').classList.remove('d-none');

    pintarUsuarioActual();
    aplicarPermisosEnInterfaz();
    iniciarContadorToken();
    irA(location.hash.slice(1) || 'inicio', false);
}

function pintarUsuarioActual() {
    const nombre = estado.me.perfil?.nombreCompleto || estado.me.usuario;
    $('#user-name').textContent = nombre;
    $('#user-avatar').textContent = iniciales(nombre);
    const rol = $('#user-role');
    rol.textContent = estado.rol;
    rol.className = `badge rol-${estado.rol}`;
    const hora = new Date().getHours();
    $('#welcome-greeting').textContent = hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches';
    $('#welcome-name').textContent = nombre.split(' ')[0];
    $('#topbar-date').textContent = new Intl.DateTimeFormat('es-SV', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());
}

/** Oculta lo que el rol no puede usar. La API igual valida cada solicitud (esto es solo comodidad visual). */
function aplicarPermisosEnInterfaz() {
    $$('[data-perm]').forEach(el => el.classList.toggle('d-none', !puede(el.dataset.perm)));
    $$('[data-perm-any]').forEach(el => el.classList.toggle('d-none', !el.dataset.permAny.split(',').some(puede)));
}

function iniciarContadorToken() {
    clearInterval(estado.timer);
    estado.avisoExpiracion = false;
    const c = decodeJwt(session.token) || {};
    const total = Math.max(1, (c.exp - (c.iat || c.exp - 1800)));
    $('#token-origin').textContent = session.origin === 'keycloak' ? 'Keycloak (RS256)' : 'API propia (HS256)';
    const fg = $('#token-ring-fg');
    const tick = () => {
        const resto = Math.max(0, Math.round(c.exp - Date.now() / 1000));
        const m = String(Math.floor(resto / 60)).padStart(2, '0');
        const s = String(resto % 60).padStart(2, '0');
        $('#token-ring-text').textContent = `${m}:${s}`;
        fg.style.strokeDashoffset = String(100 - (resto / total) * 100);
        fg.style.stroke = resto < 60 ? '#ff5f6d' : resto < 180 ? '#ffb020' : '';
        if (resto <= 60 && resto > 0 && !estado.avisoExpiracion) {
            estado.avisoExpiracion = true;
            toast('warning', 'Tu sesión vence en menos de 1 minuto');
        }
        if (resto === 0) { clearInterval(estado.timer); sesionExpirada(); }
    };
    tick();
    estado.timer = setInterval(tick, 1000);
}

let mostrandoExpirada = false;
async function sesionExpirada() {
    if (mostrandoExpirada || !estado.me) return;
    mostrandoExpirada = true;
    clearInterval(estado.timer);
    await window.Swal.fire({
        icon: 'warning', title: 'Tu sesión expiró',
        text: 'Por seguridad, los tokens tienen un tiempo de vida limitado. Inicia sesión de nuevo para continuar.',
        confirmButtonText: 'Iniciar sesión', allowOutsideClick: false,
        buttonsStyling: false, customClass: { confirmButton: 'btn btn-brand btn-swal' },
    });
    mostrandoExpirada = false;
    salirLocal();
}

async function cerrarSesion() {
    const ok = await confirmar({ title: '¿Cerrar sesión?', text: 'Tendrás que volver a iniciar sesión para continuar.', confirmText: 'Cerrar sesión', icon: 'question' });
    if (!ok) return;
    const eraKeycloak = session.origin === 'keycloak';
    salirLocal();
    if (eraKeycloak) kc.cerrarSesionKeycloak();
    else toast('info', 'Sesión cerrada');
}

function salirLocal() {
    clearInterval(estado.timer);
    session.clear();
    Object.assign(estado, { me: null, perms: new Set(), rol: null, productos: [], carrito: [], usuarios: [] });
    history.replaceState(null, '', location.pathname);
    mostrarAcceso();
}

/* =====================================================================
   NAVEGACIÓN
   ===================================================================== */
const PERMISO_VISTA = {
    productos: () => puede('producto:leer'),
    pedidos: () => ['pedido:crear', 'pedido:leer-propios', 'pedido:leer-todos'].some(puede),
    usuarios: () => puede('usuario:leer'),
};

function irA(vista, actualizarHash = true) {
    if (!TITULOS[vista]) vista = 'inicio';
    if (PERMISO_VISTA[vista] && !PERMISO_VISTA[vista]()) {
        toast('error', 'Tu rol no tiene acceso a esa sección');
        vista = 'inicio';
    }
    estado.vista = vista;
    if (actualizarHash && location.hash !== `#${vista}`) history.pushState(null, '', `#${vista}`);
    $$('.view').forEach(v => v.classList.add('d-none'));
    const el = $(`#view-${vista}`);
    el.classList.remove('d-none');
    entrarVista(el);
    $$('#sidebar-nav .nav-link').forEach(a => a.classList.toggle('active', a.dataset.view === vista));
    $('#topbar-title').textContent = TITULOS[vista];
    alternarMenu(false);

    ({ inicio: cargarInicio, productos: cargarProductos, pedidos: cargarVistaPedidos, usuarios: cargarUsuarios, sesion: pintarSesion, laboratorio: pintarLaboratorio })[vista]();
}

function alternarMenu(abrir) {
    $('#sidebar').classList.toggle('open', abrir);
    $('#sidebar-backdrop').classList.toggle('show', abrir);
}

/* =====================================================================
   INICIO
   ===================================================================== */
async function cargarInicio() {
    pintarPermisos();
    pintarAccionesRapidas();
    try {
        if (puede('producto:leer')) estado.productos = await api('/api/productos');
        const stock = estado.productos.reduce((s, p) => s + p.stock, 0);
        const valor = estado.productos.reduce((s, p) => s + p.stock * Number(p.precio), 0);
        contar($('#stat-productos'), estado.productos.length);
        contar($('#stat-stock'), stock);
        contar($('#stat-valor'), valor, { formato: v => money.format(v) });

        const puedePedidos = puede('pedido:leer-propios') || puede('pedido:leer-todos');
        if (puedePedidos) {
            const pedidos = await api('/api/pedidos');
            $('#stat-pedidos-label').textContent = puede('pedido:leer-todos') ? 'Pedidos totales' : 'Mis pedidos';
            contar($('#stat-pedidos'), pedidos.length);
        } else {
            $('#stat-pedidos').textContent = '—';
            $('#stat-pedidos-label').textContent = 'Pedidos (sin acceso)';
        }

        const bajos = estado.productos.filter(p => p.stock <= 5).sort((a, b) => a.stock - b.stock);
        $('#low-stock-count').textContent = bajos.length;
        $('#low-stock').innerHTML = bajos.length
            ? bajos.map(p => `<span class="low-chip"><i class="bi bi-exclamation-circle me-1"></i>${esc(p.nombre)} · ${p.stock} u.</span>`).join('')
            : '<span class="text-body-secondary small"><i class="bi bi-check-circle-fill text-success me-1"></i>Todo el inventario tiene stock suficiente.</span>';
        escalonar($$('#stats .stat-card'));
    } catch (err) {
        if (err.status !== 401) alertaError(err);
    }
}

function pintarPermisos() {
    const nombres = {
        'producto:leer': ['eye', 'Ver productos'], 'producto:escribir': ['pencil-square', 'Administrar productos'],
        'usuario:leer': ['people', 'Ver usuarios'], 'usuario:administrar': ['person-gear', 'Cambiar roles'],
        'pedido:crear': ['cart-plus', 'Registrar pedidos'], 'pedido:leer-propios': ['receipt', 'Ver mis pedidos'],
        'pedido:leer-todos': ['receipt-cutoff', 'Ver todos los pedidos'],
    };
    const chips = [...estado.perms].sort().map(p => {
        const [icono, txt] = nombres[p] || ['key', p];
        return `<span class="perm-chip" title="${esc(p)}"><i class="bi bi-${icono}"></i>${esc(txt)}</span>`;
    });
    $('#perm-chips').innerHTML = chips.join('') || '<span class="text-body-secondary small">Sin permisos asignados.</span>';
    escalonar($$('#perm-chips .perm-chip'), { retraso: 40, distancia: 8 });
}

function pintarAccionesRapidas() {
    const acciones = [
        puede('producto:escribir') && ['plus-square', 'Nuevo producto', 'Agregar al catálogo', () => { irA('productos'); setTimeout(() => abrirModalProducto(), 300); }],
        puede('pedido:crear') && ['cart-plus', 'Nuevo pedido', 'Registrar una venta', () => irA('pedidos')],
        puede('producto:leer') && ['box-seam', 'Ver catálogo', 'Productos disponibles', () => irA('productos')],
        puede('usuario:leer') && ['people', 'Usuarios', 'Gestionar roles', () => irA('usuarios')],
        ['shield-shaded', 'Laboratorio', 'Probar la seguridad', () => irA('laboratorio')],
        ['person-badge', 'Mi sesión', 'Ver mi token', () => irA('sesion')],
    ].filter(Boolean).slice(0, 4);
    const cont = $('#quick-actions');
    cont.innerHTML = acciones.map(([i, t, s], n) => `
        <div class="col-sm-6"><button class="quick-action" data-qa="${n}">
            <i class="bi bi-${i}"></i><span><span class="d-block fw-semibold">${t}</span><small class="text-body-secondary">${s}</small></span>
        </button></div>`).join('');
    $$('[data-qa]', cont).forEach(b => b.addEventListener('click', () => acciones[b.dataset.qa][3]()));
}

/* =====================================================================
   PRODUCTOS
   ===================================================================== */
async function cargarProductos() {
    try {
        estado.productos = await api('/api/productos');
        pintarProductos();
    } catch (err) {
        if (err.status !== 401) alertaError(err);
    }
}

const ICONOS = [
    [/caf[eé]|t[eé]\b|taza/i, 'cup-hot-fill'], [/hamaca|cama|silla|mueble/i, 'house-heart-fill'],
    [/plancha|pupus|cocina|olla|sart[eé]n/i, 'fire'], [/tel[eé]fono|celular|m[oó]vil/i, 'phone-fill'],
    [/laptop|computadora|pc/i, 'laptop-fill'], [/camisa|ropa|zapato/i, 'bag-fill'], [/libro|cuaderno/i, 'book-fill'],
];
const GRADIENTES = [
    ['#4facfe', '#2b5cff'], ['#43e97b', '#11998e'], ['#f6d365', '#ff8a3d'], ['#b06ab3', '#7b3fe4'],
    ['#ff758c', '#ff7eb3'], ['#00c2a8', '#0b6b78'], ['#667eea', '#764ba2'],
];

function portada(p) {
    const icono = (ICONOS.find(([re]) => re.test(p.nombre)) || [null, 'box-seam-fill'])[1];
    const [a, b] = GRADIENTES[p.id % GRADIENTES.length];
    return { icono, fondo: `linear-gradient(135deg, ${a}, ${b})` };
}

function stockBadge(stock) {
    if (stock === 0) return '<span class="stock-badge stock-out"><i class="bi bi-x-circle me-1"></i>Agotado</span>';
    if (stock <= 5) return `<span class="stock-badge stock-low"><i class="bi bi-exclamation-triangle me-1"></i>Quedan ${stock}</span>`;
    return `<span class="stock-badge stock-ok"><i class="bi bi-check-circle me-1"></i>${stock} en stock</span>`;
}

function pintarProductos() {
    const q = $('#prod-search').value.trim().toLowerCase();
    const orden = $('#prod-sort').value;
    let lista = estado.productos.filter(p => `${p.nombre} ${p.descripcion || ''}`.toLowerCase().includes(q));
    const cmp = {
        nombre: (a, b) => a.nombre.localeCompare(b.nombre),
        'precio-asc': (a, b) => a.precio - b.precio,
        'precio-desc': (a, b) => b.precio - a.precio,
        'stock-asc': (a, b) => a.stock - b.stock,
    }[orden];
    lista = [...lista].sort(cmp);

    const admin = puede('producto:escribir');
    $('#product-grid').innerHTML = lista.map(p => {
        const { icono, fondo } = portada(p);
        return `
        <div class="col-sm-6 col-lg-4 col-xxl-3" data-prod="${p.id}">
            <div class="product-card">
                <div class="product-cover" style="background:${fondo}">
                    <i class="bi bi-${icono}"></i>
                    <span class="price-tag">${money.format(p.precio)}</span>
                </div>
                <div class="card-body">
                    <h3 class="h6 fw-bold mb-1 text-truncate" title="${esc(p.nombre)}">${esc(p.nombre)}</h3>
                    <p class="small text-body-secondary mb-3" style="min-height:2.6em">${esc(p.descripcion || 'Sin descripción')}</p>
                    <div class="d-flex justify-content-between align-items-center">
                        ${stockBadge(p.stock)}
                        ${admin ? `<div class="product-actions">
                            <button class="btn btn-sm btn-outline-primary" data-edit="${p.id}" title="Editar"><i class="bi bi-pencil"></i></button>
                            <button class="btn btn-sm btn-outline-danger" data-del="${p.id}" title="Eliminar"><i class="bi bi-trash"></i></button>
                        </div>` : ''}
                    </div>
                </div>
            </div>
        </div>`;
    }).join('');
    $('#product-empty').classList.toggle('d-none', lista.length > 0);
    $$('[data-edit]').forEach(b => b.addEventListener('click', () => abrirModalProducto(estado.productos.find(p => p.id == b.dataset.edit))));
    $$('[data-del]').forEach(b => b.addEventListener('click', () => eliminarProducto(estado.productos.find(p => p.id == b.dataset.del))));
    escalonar($$('#product-grid > div'), { retraso: 45 });
}

function abrirModalProducto(p = null) {
    const form = $('#form-producto');
    form.reset();
    limpiarErrores(form);
    form.id.value = p?.id ?? '';
    form.nombre.value = p?.nombre ?? '';
    form.descripcion.value = p?.descripcion ?? '';
    form.precio.value = p?.precio ?? '';
    form.stock.value = p?.stock ?? '';
    $('#desc-count').textContent = form.descripcion.value.length;
    $('#modal-producto-title').innerHTML = p
        ? '<i class="bi bi-pencil-square me-2 text-primary"></i>Editar producto'
        : '<i class="bi bi-plus-square me-2 text-primary"></i>Nuevo producto';
    bootstrap.Modal.getOrCreateInstance($('#modal-producto')).show();
    setTimeout(() => form.nombre.focus(), 400);
}

async function guardarProducto(e) {
    e.preventDefault();
    const form = e.currentTarget;
    const precioTxt = form.precio.value.trim();
    const stockTxt = form.stock.value.trim();
    const datos = {
        nombre: form.nombre.value.trim(),
        descripcion: form.descripcion.value.trim() || null,
        precio: Number(precioTxt),
        stock: Number(stockTxt),
    };
    const ok = validar(form, [
        { campo: 'nombre', valido: datos.nombre.length >= 2, mensaje: 'El nombre debe tener al menos 2 caracteres' },
        { campo: 'nombre', valido: datos.nombre.length <= 120, mensaje: 'Máximo 120 caracteres' },
        { campo: 'descripcion', valido: (datos.descripcion || '').length <= 500, mensaje: 'Máximo 500 caracteres' },
        { campo: 'precio', valido: precioTxt !== '' && datos.precio > 0, mensaje: 'Ingresa un precio mayor que $0.00' },
        { campo: 'precio', valido: /^\d+(\.\d{1,2})?$/.test(precioTxt), mensaje: 'Usa máximo 2 decimales' },
        { campo: 'precio', valido: datos.precio <= 99999.99, mensaje: 'El precio no puede superar $99,999.99' },
        { campo: 'stock', valido: stockTxt !== '' && Number.isInteger(datos.stock), mensaje: 'Ingresa una cantidad entera' },
        { campo: 'stock', valido: datos.stock >= 0, mensaje: 'El stock no puede ser negativo' },
        { campo: 'stock', valido: datos.stock <= 100000, mensaje: 'Máximo 100,000 unidades' },
    ]);
    if (!ok) { sacudir(form); return; }

    const id = form.id.value;
    const btn = form.querySelector('[type=submit]');
    cargando(btn, true, 'Guardando…');
    try {
        await api(id ? `/api/productos/${id}` : '/api/productos', { method: id ? 'PUT' : 'POST', body: datos });
        bootstrap.Modal.getInstance($('#modal-producto')).hide();
        toast('success', id ? 'Producto actualizado' : 'Producto creado');
        await cargarProductos();
        const card = $(`[data-prod="${id || Math.max(...estado.productos.map(p => p.id))}"] .product-card`);
        resaltar(card);
    } catch (err) {
        sacudir(form);
        if (!aplicarErroresServidor(form, err)) alertaError(err);
    } finally {
        cargando(btn, false);
    }
}

async function eliminarProducto(p) {
    const ok = await confirmar({
        title: '¿Eliminar producto?', icon: 'warning', danger: true, confirmText: 'Sí, eliminar',
        text: `Se eliminará <strong>${esc(p.nombre)}</strong>. Esta acción no se puede deshacer.`,
    });
    if (!ok) return;
    try {
        await api(`/api/productos/${p.id}`, { method: 'DELETE' });
        await salir($(`[data-prod="${p.id}"]`));
        estado.productos = estado.productos.filter(x => x.id !== p.id);
        pintarProductos();
        toast('success', 'Producto eliminado');
    } catch (err) {
        alertaError(err, err.status === 500 ? 'No se pudo eliminar' : undefined);
    }
}

/* =====================================================================
   PEDIDOS
   ===================================================================== */
async function cargarVistaPedidos() {
    const crea = puede('pedido:crear');
    $('#order-builder-col').classList.toggle('d-none', !crea);
    $('#orders-col').className = crea ? 'col-xl-7' : 'col-12';
    if (crea) {
        try {
            estado.productos = await api('/api/productos');
            pintarSelectorProductos();
            pintarCarrito();
        } catch (err) { if (err.status !== 401) alertaError(err); }
    }
    await cargarPedidos();
}

function pintarSelectorProductos() {
    const sel = $('#ped-producto');
    const actual = sel.value;
    sel.innerHTML = '<option value="">Selecciona…</option>' + estado.productos.map(p => {
        const disp = p.stock - (estado.carrito.find(i => i.id === p.id)?.cantidad || 0);
        return `<option value="${p.id}" ${disp <= 0 ? 'disabled' : ''}>${esc(p.nombre)} — ${money.format(p.precio)} (${disp} disp.)</option>`;
    }).join('');
    sel.value = actual;
}

function agregarAlCarrito() {
    const err = $('#item-error');
    err.textContent = '';
    const sel = $('#ped-producto');
    const cant = $('#ped-cantidad');
    sel.classList.remove('is-invalid'); cant.classList.remove('is-invalid');
    const p = estado.productos.find(x => x.id == sel.value);
    const n = Number(cant.value);
    const enCarrito = estado.carrito.find(i => i.id === p?.id)?.cantidad || 0;

    let msg = null;
    if (!p) { msg = 'Selecciona un producto'; sel.classList.add('is-invalid'); }
    else if (!Number.isInteger(n) || n < 1) { msg = 'La cantidad debe ser un número entero mayor que cero'; cant.classList.add('is-invalid'); }
    else if (n + enCarrito > p.stock) { msg = `Solo hay ${p.stock - enCarrito} unidades disponibles de "${p.nombre}"`; cant.classList.add('is-invalid'); }
    else if (n + enCarrito > 1000) { msg = 'La cantidad máxima por producto es 1,000'; cant.classList.add('is-invalid'); }
    if (msg) { err.textContent = msg; sacudir($('#form-pedido')); return; }

    const linea = estado.carrito.find(i => i.id === p.id);
    if (linea) linea.cantidad += n;
    else estado.carrito.push({ id: p.id, nombre: p.nombre, precio: Number(p.precio), cantidad: n });
    cant.value = 1;
    sel.value = '';
    $('[data-for="items"]').textContent = '';
    pintarCarrito(p.id);
    pintarSelectorProductos();
}

function pintarCarrito(nuevoId) {
    const body = $('#cart-body');
    body.innerHTML = estado.carrito.map(i => `
        <tr data-line="${i.id}">
            <td class="small fw-medium">${esc(i.nombre)}</td>
            <td class="text-center"><span class="badge text-bg-light">${i.cantidad}</span></td>
            <td class="text-end small">${money.format(i.precio * i.cantidad)}</td>
            <td class="text-end"><button type="button" class="btn btn-sm btn-link text-danger p-0" data-quitar="${i.id}" title="Quitar"><i class="bi bi-x-circle"></i></button></td>
        </tr>`).join('');
    $('#cart-empty').classList.toggle('d-none', estado.carrito.length > 0);
    const total = estado.carrito.reduce((s, i) => s + i.precio * i.cantidad, 0);
    contar($('#cart-total'), total, { formato: v => money.format(v), duracion: 500 });
    $$('[data-quitar]', body).forEach(b => b.addEventListener('click', async () => {
        await salir(b.closest('tr'));
        estado.carrito = estado.carrito.filter(i => i.id != b.dataset.quitar);
        pintarCarrito(); pintarSelectorProductos();
    }));
    if (nuevoId) escalonar([$(`[data-line="${nuevoId}"]`)].filter(Boolean));
}

async function registrarPedido(e) {
    e.preventDefault();
    const form = e.currentTarget;
    limpiarErrores(form);
    const cliente = form.cliente.value.trim();
    if (cliente.length > 120) { marcarError(form, 'cliente', 'Máximo 120 caracteres'); sacudir(form); return; }
    if (estado.carrito.length === 0) {
        $('[data-for="items"]').textContent = 'Agrega al menos un producto al pedido';
        sacudir(form);
        return;
    }
    const ok = await confirmar({
        title: 'Confirmar pedido', icon: 'question', confirmText: 'Registrar',
        text: `${estado.carrito.length} producto(s) por un total de <strong>${money.format(estado.carrito.reduce((s, i) => s + i.precio * i.cantidad, 0))}</strong>`,
    });
    if (!ok) return;

    const btn = form.querySelector('[type=submit]');
    cargando(btn, true, 'Registrando…');
    try {
        const pedido = await api('/api/pedidos', {
            method: 'POST',
            body: { cliente: cliente || null, items: estado.carrito.map(i => ({ productoId: i.id, cantidad: i.cantidad })) },
        });
        confeti();
        await success(`¡Pedido #${pedido.id} registrado!`, `Total: ${money.format(pedido.total)}`);
        estado.carrito = [];
        form.reset();
        await cargarVistaPedidos();
    } catch (err) {
        sacudir(form);
        if (!aplicarErroresServidor(form, err)) alertaError(err, err.status === 400 ? 'No se pudo registrar el pedido' : undefined);
        if (err.status === 400) { estado.productos = await api('/api/productos').catch(() => estado.productos); pintarSelectorProductos(); }
    } finally {
        cargando(btn, false);
    }
}

async function cargarPedidos() {
    const lista = $('#orders-list');
    const puedeVer = puede('pedido:leer-propios') || puede('pedido:leer-todos');
    $('#orders-noperm').classList.toggle('d-none', puedeVer);
    $('#orders-title').textContent = puede('pedido:leer-todos') ? 'Todos los pedidos' : 'Mis pedidos';
    if (!puedeVer) { lista.innerHTML = ''; $('#orders-empty').classList.add('d-none'); return; }
    try {
        const pedidos = await api('/api/pedidos');
        lista.innerHTML = pedidos.map(p => `
            <div class="order-item d-flex align-items-center gap-3" data-order="${p.id}">
                <div class="order-number">#${p.id}</div>
                <div class="flex-grow-1 min-w-0">
                    <div class="fw-semibold text-truncate">${esc(p.cliente || 'Cliente general')}</div>
                    <div class="small text-body-secondary">${fecha.format(aFecha(p.fecha))} · ${p.items.length} producto(s) · por ${esc(p.creadoPor)}</div>
                </div>
                <div class="fw-bold">${money.format(p.total)}</div>
                <i class="bi bi-chevron-right text-body-secondary"></i>
            </div>`).join('');
        $('#orders-empty').classList.toggle('d-none', pedidos.length > 0);
        $$('[data-order]', lista).forEach(el => el.addEventListener('click', () => verPedido(pedidos.find(p => p.id == el.dataset.order))));
        escalonar($$('.order-item', lista), { retraso: 40 });
    } catch (err) {
        if (err.status !== 401) alertaError(err);
    }
}

function verPedido(p) {
    const filas = p.items.map(i => `<tr><td class="text-start">${esc(i.producto)}</td><td>${i.cantidad}</td><td class="text-end">${money.format(i.precioUnitario)}</td><td class="text-end">${money.format(i.subtotal)}</td></tr>`).join('');
    window.Swal.fire({
        title: `Pedido #${p.id}`,
        html: `<div class="text-start small mb-3"><div><strong>Cliente:</strong> ${esc(p.cliente || 'Cliente general')}</div>
                <div><strong>Fecha:</strong> ${fecha.format(aFecha(p.fecha))}</div><div><strong>Registrado por:</strong> ${esc(p.creadoPor)}</div></div>
               <table class="table table-sm small"><thead><tr><th class="text-start">Producto</th><th>Cant.</th><th class="text-end">P. unit.</th><th class="text-end">Subtotal</th></tr></thead>
               <tbody>${filas}</tbody><tfoot><tr><th colspan="3" class="text-end">Total</th><th class="text-end">${money.format(p.total)}</th></tr></tfoot></table>`,
        width: 620, confirmButtonText: 'Cerrar', buttonsStyling: false, customClass: { confirmButton: 'btn btn-brand btn-swal' },
    });
}

/* =====================================================================
   USUARIOS
   ===================================================================== */
async function cargarUsuarios() {
    try {
        estado.usuarios = await api('/api/usuarios');
        pintarUsuarios();
    } catch (err) {
        if (err.status !== 401) alertaError(err);
    }
}

function pintarUsuarios() {
    const q = $('#user-search').value.trim().toLowerCase();
    const admin = puede('usuario:administrar');
    const lista = estado.usuarios.filter(u => `${u.nombreCompleto} ${u.email} ${u.rol}`.toLowerCase().includes(q));
    $('#users-body').innerHTML = lista.map(u => {
        const yo = u.email === estado.me.usuario;
        const selector = admin && !yo
            ? `<select class="form-select form-select-sm w-auto ms-auto" data-rol-user="${u.id}">
                 ${['ADMIN', 'EMPLEADO', 'USUARIO'].map(r => `<option ${r === u.rol ? 'selected' : ''}>${r}</option>`).join('')}
               </select>`
            : `<span class="small text-body-secondary">${yo ? 'Tu cuenta' : 'Solo lectura'}</span>`;
        return `<tr>
            <td><div class="d-flex align-items-center gap-2"><span class="avatar" style="width:34px;height:34px;font-size:.8rem">${esc(iniciales(u.nombreCompleto))}</span>
                <span class="fw-medium">${esc(u.nombreCompleto)}</span></div></td>
            <td class="small">${esc(u.email)}</td>
            <td><span class="badge rol-${esc(u.rol)}">${esc(u.rol)}</span></td>
            <td class="text-end">${selector}</td></tr>`;
    }).join('') || '<tr><td colspan="4" class="text-center text-body-secondary py-4">Sin resultados</td></tr>';
    $$('[data-rol-user]').forEach(s => s.addEventListener('change', () => cambiarRol(s)));
    escalonar($$('#users-body tr'), { retraso: 35, distancia: 10 });
}

async function cambiarRol(select) {
    const u = estado.usuarios.find(x => x.id == select.dataset.rolUser);
    const nuevo = select.value;
    const ok = await confirmar({
        title: 'Cambiar rol', icon: 'warning', confirmText: 'Cambiar rol',
        text: `¿Asignar el rol <span class="badge rol-${nuevo}">${nuevo}</span> a <strong>${esc(u.nombreCompleto)}</strong>?<br><small class="text-body-secondary">El cambio aplica en su próximo inicio de sesión.</small>`,
    });
    if (!ok) { select.value = u.rol; return; }
    try {
        const r = await api(`/api/usuarios/${u.id}/rol`, { method: 'PATCH', body: { rol: nuevo } });
        u.rol = r.rol;
        pintarUsuarios();
        toast('success', `Rol actualizado a ${r.rol}`);
    } catch (err) {
        select.value = u.rol;
        alertaError(err);
    }
}

/* =====================================================================
   MI SESIÓN
   ===================================================================== */
function pintarSesion() {
    const c = decodeJwt(session.token) || {};
    const nombre = estado.me.perfil?.nombreCompleto || c.name || estado.me.usuario;
    $('#profile-avatar').textContent = iniciales(nombre);
    $('#profile-name').textContent = nombre;
    $('#profile-email').textContent = estado.me.perfil?.email || c.email || estado.me.usuario;
    const rol = $('#profile-role');
    rol.textContent = estado.rol; rol.className = `badge fs-6 rol-${estado.rol}`;
    $('#profile-issuer-type').textContent = session.origin === 'keycloak' ? 'Keycloak · OAuth2 (RS256)' : 'API propia · JWT (HS256)';
    $('#profile-iat').textContent = c.iat ? fecha.format(new Date(c.iat * 1000)) : '—';
    $('#profile-exp').textContent = c.exp ? fecha.format(new Date(c.exp * 1000)) : '—';
    $('#profile-claims').innerHTML = resaltarJson(c);
    resaltar($('#profile-avatar'));
}

function resaltarJson(obj) {
    return esc(JSON.stringify(obj, null, 2))
        .replace(/(&quot;[^&]*?&quot;)(\s*:)/g, '<span class="k">$1</span>$2')
        .replace(/:\s(&quot;.*?&quot;)/g, ': <span class="s">$1</span>')
        .replace(/:\s(\d+)/g, ': <span class="n">$1</span>');
}

/* =====================================================================
   LABORATORIO DE SEGURIDAD (escenarios del Módulo 5)
   ===================================================================== */
const TOKEN_EXP_KEY = 'ventassv.tokenParaExpirar';
const guardado = {
    leer() { try { return localStorage.getItem(TOKEN_EXP_KEY); } catch { return null; } },
    escribir(t) { try { localStorage.setItem(TOKEN_EXP_KEY, t); return true; } catch { return false; } },
};

function pruebas() {
    const sinPermiso = !puede('producto:escribir')
        ? { desc: 'POST /api/productos — tu rol no puede crear productos', run: () => apiRaw('/api/productos', { method: 'POST', body: { nombre: 'Prueba de seguridad', precio: 1, stock: 1 } }) }
        : { desc: 'POST /api/pedidos — el rol ADMIN no registra pedidos', run: () => apiRaw('/api/pedidos', { method: 'POST', body: { items: [{ productoId: 1, cantidad: 1 }] } }) };

    return [
        { n: 1, titulo: 'Solicitud sin token', desc: 'GET /api/productos sin el header Authorization', esperado: 401,
            run: () => apiRaw('/api/productos', { auth: false }) },
        { n: 2, titulo: 'Token válido con permisos', desc: 'GET /api/usuarios/me con tu token actual', esperado: 200,
            run: () => apiRaw('/api/usuarios/me') },
        { n: 3, titulo: 'Token válido sin permisos', desc: sinPermiso.desc, esperado: 403, run: sinPermiso.run },
        { n: 4, titulo: 'Token alterado', desc: 'Se cambia el rol a ADMIN dentro del token sin volver a firmarlo', esperado: 401,
            run: () => apiRaw('/api/usuarios', { token: tamperToken(session.token) }) },
        { n: '4b', titulo: 'Token falsificado', desc: 'Se envía un texto que no es un JWT firmado', esperado: 401,
            run: () => apiRaw('/api/productos', { token: 'eyJhbGciOiJub25lIn0.eyJzdWIiOiJoYWNrZXIifQ.firma-falsa' }) },
        { n: 5, titulo: 'Token expirado', esperado: 401, expirado: true,
            desc: 'Usa un token guardado previamente que ya venció' },
        { n: 6, titulo: 'Token emitido por Keycloak', esperado: 200, keycloak: true,
            desc: session.origin === 'keycloak' ? 'GET /api/productos con el token RS256 de Keycloak' : 'Inicia sesión con Keycloak para ejecutar esta prueba',
            run: () => apiRaw('/api/productos') },
        { n: 7, titulo: 'Registro público pidiendo ADMIN', desc: 'POST /api/auth/register con "rol": "ADMIN"', esperado: 403,
            run: () => apiRaw('/api/auth/register', { method: 'POST', auth: false,
                body: { nombreCompleto: 'Intruso de prueba', email: `intruso${Date.now()}@test.sv`, password: 'Clave-Segura-1', rol: 'ADMIN' } }) },
    ];
}

function pintarLaboratorio() {
    const lista = pruebas();
    $('#lab-grid').innerHTML = lista.map((t, i) => `
        <div class="col-md-6 col-xxl-4">
            <div class="lab-card card" id="lab-${i}">
                <div class="card-body">
                    <div class="d-flex gap-3 align-items-start mb-3">
                        <div class="lab-num">${t.n}</div>
                        <div class="flex-grow-1">
                            <h3 class="h6 fw-bold mb-1">${esc(t.titulo)}</h3>
                            <div class="small text-body-secondary">${esc(t.desc)}</div>
                        </div>
                    </div>
                    <div class="d-flex align-items-center gap-2 mb-2 small">
                        <span>Esperado</span><span class="code-pill">${t.esperado}</span>
                        <i class="bi bi-arrow-right text-body-secondary"></i>
                        <span>Obtenido</span><span class="code-pill" data-obtenido>—</span>
                        <span class="ms-auto fs-5" data-icono></span>
                    </div>
                    <div class="lab-detail mb-3" data-detalle></div>
                    <div class="d-flex gap-2">
                        <button class="btn btn-sm btn-outline-primary" data-run="${i}"><i class="bi bi-play-fill"></i> Ejecutar</button>
                        ${t.expirado ? '<button class="btn btn-sm btn-outline-secondary" data-guardar><i class="bi bi-bookmark-plus"></i> Guardar token actual</button>' : ''}
                    </div>
                </div>
            </div>
        </div>`).join('');
    $$('[data-run]').forEach(b => b.addEventListener('click', () => ejecutarPrueba(Number(b.dataset.run))));
    $('[data-guardar]')?.addEventListener('click', () => {
        if (guardado.escribir(session.token)) {
            const c = decodeJwt(session.token);
            toast('success', `Token guardado. Vence a las ${new Date(c.exp * 1000).toLocaleTimeString('es-SV')}`);
            pintarEstadoExpirado();
        } else toast('error', 'El navegador no permitió guardar el token');
    });
    pintarEstadoExpirado();
    escalonar($$('#lab-grid > div'), { retraso: 50 });
}

function pintarEstadoExpirado() {
    const i = pruebas().findIndex(t => t.expirado);
    const det = $(`#lab-${i} [data-detalle]`);
    if (!det) return;
    const t = guardado.leer();
    const c = t && decodeJwt(t);
    if (!c) det.textContent = 'Paso 1: pulsa "Guardar token actual". Paso 2: espera a que venza y pulsa "Ejecutar".';
    else if (c.exp * 1000 > Date.now() - 60000) det.textContent = `Token guardado; vence a las ${new Date(c.exp * 1000).toLocaleTimeString('es-SV')} (+60 s de tolerancia).`;
    else det.textContent = 'Hay un token guardado que ya expiró. ¡Listo para ejecutar!';
}

async function ejecutarPrueba(i) {
    const t = pruebas()[i];
    const card = $(`#lab-${i}`);
    const pill = $('[data-obtenido]', card);
    const icono = $('[data-icono]', card);
    const det = $('[data-detalle]', card);
    card.classList.remove('pass', 'fail');
    card.classList.add('running');
    pill.className = 'code-pill'; pill.textContent = '…';
    icono.innerHTML = '<span class="spinner-border spinner-border-sm text-primary"></span>';

    let r;
    if (t.keycloak && session.origin !== 'keycloak') {
        card.classList.remove('running');
        pill.textContent = 'N/A'; icono.innerHTML = '<i class="bi bi-dash-circle text-secondary"></i>';
        det.textContent = 'Cierra sesión e ingresa con el botón "Iniciar sesión con Keycloak".';
        return null;
    }
    if (t.expirado) {
        const tok = guardado.leer();
        const c = tok && decodeJwt(tok);
        if (!c || c.exp * 1000 > Date.now() - 60000) {
            card.classList.remove('running');
            pill.textContent = 'N/A'; icono.innerHTML = '<i class="bi bi-hourglass-split text-warning"></i>';
            pintarEstadoExpirado();
            return null;
        }
        r = await apiRaw('/api/productos', { token: tok });
    } else {
        r = await t.run();
    }

    const pasa = r.status === t.esperado;
    card.classList.remove('running');
    card.classList.add(pasa ? 'pass' : 'fail');
    pill.textContent = r.status || 'ERR';
    pill.classList.add(pasa ? 'ok' : 'bad');
    icono.innerHTML = pasa ? '<i class="bi bi-check-circle-fill text-success"></i>' : '<i class="bi bi-x-circle-fill text-danger"></i>';
    det.textContent = r.data?.mensaje || (Array.isArray(r.data) ? `${r.data.length} registro(s) devueltos` : r.data?.usuario ? `Usuario: ${r.data.usuario}` : '');
    resaltar(pill);
    return pasa;
}

async function ejecutarLaboratorio() {
    const btn = $('#btn-run-all');
    cargando(btn, true, 'Ejecutando…');
    const total = pruebas().length;
    let ok = 0; let omitidas = 0;
    for (let i = 0; i < total; i++) {
        const r = await ejecutarPrueba(i);
        if (r === true) ok++;
        if (r === null) omitidas++;
        await new Promise(res => setTimeout(res, 250));
    }
    cargando(btn, false);
    const ejecutadas = total - omitidas;
    window.Swal.fire({
        icon: ok === ejecutadas ? 'success' : 'warning',
        title: `${ok} de ${ejecutadas} pruebas correctas`,
        text: omitidas ? `${omitidas} prueba(s) requieren un paso previo (ver tarjetas).` : 'La API respondió exactamente como se esperaba.',
        confirmButtonText: 'Ver resultados', buttonsStyling: false, customClass: { confirmButton: 'btn btn-brand btn-swal' },
    });
}

/* =====================================================================
   TEMA CLARO / OSCURO
   ===================================================================== */
function aplicarTemaGuardado() {
    let tema = null;
    try { tema = localStorage.getItem('ventassv.tema'); } catch { /* sin almacenamiento */ }
    if (!tema) tema = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    ponerTema(tema);
}

function ponerTema(tema) {
    document.documentElement.setAttribute('data-bs-theme', tema);
    $('#btn-theme').innerHTML = `<i class="bi bi-${tema === 'dark' ? 'sun' : 'moon-stars'}"></i>`;
}

function alternarTema() {
    const nuevo = document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
    ponerTema(nuevo);
    try { localStorage.setItem('ventassv.tema', nuevo); } catch { /* sin almacenamiento */ }
    resaltar($('#btn-theme'));
}
