/* =====================================================================
   Utilidades de interfaz: alertas amigables (SweetAlert2), formularios,
   validación visual y formato.
   ===================================================================== */

const Swal = window.Swal;

/* ---------------------------- Formato ---------------------------- */
export const money = new Intl.NumberFormat('es-SV', { style: 'currency', currency: 'USD' });
export const fecha = new Intl.DateTimeFormat('es-SV', { dateStyle: 'medium', timeStyle: 'short' });

/** Escapa texto antes de insertarlo como HTML (evita XSS con datos de usuarios). */
export function esc(valor) {
    return String(valor ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

export function iniciales(nombre) {
    return String(nombre || '?').trim().split(/\s+/).slice(0, 2).map(p => p[0]).join('').toUpperCase() || '?';
}

/* ---------------------------- Alertas ---------------------------- */
const base = {
    buttonsStyling: false,
    customClass: { confirmButton: 'btn btn-brand btn-swal', cancelButton: 'btn btn-light btn-swal-cancel ms-2' },
    showClass: { popup: 'swal2-show' },
};

const toastMixin = Swal.mixin({
    toast: true, position: 'top-end', showConfirmButton: false, timer: 3200, timerProgressBar: true,
    didOpen: t => { t.addEventListener('mouseenter', Swal.stopTimer); t.addEventListener('mouseleave', Swal.resumeTimer); },
});

export function toast(icon, title) { return toastMixin.fire({ icon, title }); }

export function success(title, text) {
    return Swal.fire({ ...base, icon: 'success', title, text, timer: 2200, showConfirmButton: false });
}

export function confirmar({ title, text, confirmText = 'Sí, continuar', icon = 'question', danger = false }) {
    return Swal.fire({
        ...base, icon, title, html: text, showCancelButton: true, confirmButtonText: confirmText, cancelButtonText: 'Cancelar',
        reverseButtons: true, focusCancel: danger,
        customClass: { ...base.customClass, confirmButton: danger ? 'btn btn-danger btn-swal-cancel' : base.customClass.confirmButton },
    }).then(r => r.isConfirmed);
}

/** Traduce un error de la API a un mensaje comprensible para cualquier usuario. */
export function mensajeAmigable(err) {
    const s = err?.status;
    if (s === 0) return { icon: 'error', title: 'Sin conexión', text: err.message };
    if (s === 400) return { icon: 'warning', title: 'Revisa los datos', text: err.data?.mensaje || 'Hay campos con información inválida.' };
    if (s === 401) return { icon: 'warning', title: 'Necesitas iniciar sesión', text: err.data?.mensaje || 'Tu sesión no es válida o ya expiró.' };
    if (s === 403) return { icon: 'error', title: 'Acceso denegado', text: err.data?.mensaje || 'Tu rol no tiene permiso para esta operación.' };
    if (s === 404) return { icon: 'info', title: 'No encontrado', text: err.data?.mensaje || 'El recurso ya no existe.' };
    if (s === 409) return { icon: 'warning', title: 'Dato duplicado', text: err.data?.mensaje || 'Ya existe un registro con esos datos.' };
    return { icon: 'error', title: 'Algo salió mal', text: err?.data?.mensaje || 'Ocurrió un error inesperado. Intenta de nuevo.' };
}

export function alertaError(err, titulo) {
    const m = mensajeAmigable(err);
    const lista = (err?.detalles || []).map(d => `<li>${esc(d.replace(/^[^:]+:\s*/, ''))}</li>`).join('');
    return Swal.fire({
        ...base, icon: m.icon, title: titulo || m.title,
        html: `<p class="mb-2">${esc(m.text)}</p>${lista ? `<ul class="text-start small mb-0">${lista}</ul>` : ''}`
            + (err?.status ? `<div class="mt-3"><span class="badge text-bg-light">Código HTTP ${err.status}</span></div>` : ''),
        confirmButtonText: 'Entendido',
    });
}

/* ---------------------------- Formularios ---------------------------- */
export function limpiarErrores(form) {
    form.querySelectorAll('.is-invalid').forEach(e => e.classList.remove('is-invalid'));
    form.querySelectorAll('.is-valid').forEach(e => e.classList.remove('is-valid'));
    form.querySelectorAll('[data-for]').forEach(e => { e.textContent = ''; });
}

/** Marca un campo como inválido y muestra el mensaje debajo. */
export function marcarError(form, campo, mensaje) {
    const input = form.querySelector(`[name="${campo}"]`);
    const fb = form.querySelector(`[data-for="${campo}"]`);
    if (input) input.classList.add('is-invalid');
    if (fb) { fb.textContent = mensaje; fb.classList.add('d-block'); }
}

export function marcarValido(form, campo) {
    const input = form.querySelector(`[name="${campo}"]`);
    if (input) { input.classList.remove('is-invalid'); input.classList.add('is-valid'); }
    const fb = form.querySelector(`[data-for="${campo}"]`);
    if (fb) { fb.textContent = ''; fb.classList.remove('d-block'); }
}

/**
 * Muestra bajo cada campo los mensajes de validación que devolvió Java (Bean Validation).
 * El backend responde detalles como "precio: El precio debe ser mayor que $0.00".
 * Devuelve true si al menos un mensaje se pudo ubicar en un campo.
 */
export function aplicarErroresServidor(form, err) {
    let ubicados = 0;
    for (const d of err?.detalles || []) {
        const i = d.indexOf(':');
        if (i < 0) continue;
        const campo = d.slice(0, i).trim().replace(/\[\d+\].*$/, '');
        if (form.querySelector(`[data-for="${campo}"]`)) { marcarError(form, campo, d.slice(i + 1).trim()); ubicados++; }
    }
    return ubicados > 0;
}

/** Ejecuta una lista de reglas {campo, valido, mensaje}; marca errores y devuelve si todo es válido. */
export function validar(form, reglas) {
    limpiarErrores(form);
    let ok = true;
    const vistos = new Set();
    for (const r of reglas) {
        if (vistos.has(r.campo)) continue;
        if (!r.valido) { marcarError(form, r.campo, r.mensaje); vistos.add(r.campo); ok = false; }
    }
    for (const r of reglas) if (!vistos.has(r.campo)) marcarValido(form, r.campo);
    if (!ok) {
        form.querySelector('.is-invalid')?.focus();
    }
    return ok;
}

/** Muestra un spinner en el botón mientras se espera la respuesta. */
export function cargando(boton, activo, texto = 'Procesando…') {
    if (!boton) return;
    if (activo) {
        boton.dataset.html = boton.innerHTML;
        boton.disabled = true;
        boton.innerHTML = `<span class="spinner-border spinner-border-sm me-2"></span>${esc(texto)}`;
    } else if (boton.dataset.html) {
        boton.disabled = false;
        boton.innerHTML = boton.dataset.html;
    }
}

export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
