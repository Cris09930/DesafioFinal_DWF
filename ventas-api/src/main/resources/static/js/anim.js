/* =====================================================================
   Animaciones con JavaScript (Web Animations API + requestAnimationFrame)
   ===================================================================== */

const reducido = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Fondo de partículas conectadas en la pantalla de acceso. */
export function particulas(canvas) {
    const ctx = canvas.getContext('2d');
    let puntos = [];
    let ancho = 0;
    let alto = 0;
    let activo = true;
    const mouse = { x: -999, y: -999 };

    function ajustar() {
        const dpr = window.devicePixelRatio || 1;
        ancho = canvas.clientWidth; alto = canvas.clientHeight;
        canvas.width = ancho * dpr; canvas.height = alto * dpr;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        const n = Math.min(90, Math.floor((ancho * alto) / 16000));
        puntos = Array.from({ length: n }, () => ({
            x: Math.random() * ancho, y: Math.random() * alto,
            vx: (Math.random() - .5) * .5, vy: (Math.random() - .5) * .5, r: Math.random() * 2 + 1,
        }));
    }

    function dibujar() {
        if (!activo) return;
        ctx.clearRect(0, 0, ancho, alto);
        for (const p of puntos) {
            p.x += p.vx; p.y += p.vy;
            if (p.x < 0 || p.x > ancho) p.vx *= -1;
            if (p.y < 0 || p.y > alto) p.vy *= -1;
            const dm = Math.hypot(p.x - mouse.x, p.y - mouse.y);
            if (dm < 120) { p.x += (p.x - mouse.x) / dm; p.y += (p.y - mouse.y) / dm; }
            ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
            ctx.fillStyle = 'rgba(160, 230, 255, .75)'; ctx.fill();
        }
        for (let i = 0; i < puntos.length; i++) {
            for (let j = i + 1; j < puntos.length; j++) {
                const a = puntos[i]; const b = puntos[j];
                const d = Math.hypot(a.x - b.x, a.y - b.y);
                if (d < 130) {
                    ctx.strokeStyle = `rgba(160, 230, 255, ${(1 - d / 130) * .35})`;
                    ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke();
                }
            }
        }
        requestAnimationFrame(dibujar);
    }

    window.addEventListener('resize', ajustar);
    window.addEventListener('mousemove', e => { mouse.x = e.clientX; mouse.y = e.clientY; });
    ajustar();
    if (!reducido) dibujar();
    return { detener() { activo = false; }, reanudar() { if (!activo) { activo = true; dibujar(); } } };
}

/** Efecto máquina de escribir que rota entre varias palabras. */
export function maquinaEscribir(el, palabras, { escribir = 85, borrar = 45, pausa = 1600 } = {}) {
    let i = 0; let j = 0; let borrando = false;
    function paso() {
        const palabra = palabras[i];
        el.textContent = palabra.slice(0, j);
        if (!borrando && j < palabra.length) { j++; setTimeout(paso, escribir); }
        else if (!borrando) { borrando = true; setTimeout(paso, pausa); }
        else if (j > 0) { j--; setTimeout(paso, borrar); }
        else { borrando = false; i = (i + 1) % palabras.length; setTimeout(paso, 300); }
    }
    paso();
}

/** Cuenta animada desde el valor actual hasta "hasta". */
export function contar(el, hasta, { formato = v => Math.round(v).toLocaleString('es-SV'), duracion = 1100 } = {}) {
    const desde = Number(el.dataset.counter || 0);
    el.dataset.counter = hasta;
    if (reducido) { el.textContent = formato(hasta); return; }
    const inicio = performance.now();
    function frame(t) {
        const p = Math.min(1, (t - inicio) / duracion);
        const e = 1 - Math.pow(1 - p, 3);
        el.textContent = formato(desde + (hasta - desde) * e);
        if (p < 1) requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
}

/** Entrada escalonada de una lista de elementos (tarjetas, filas…). */
export function escalonar(elementos, { retraso = 60, distancia = 18 } = {}) {
    if (reducido) return;
    [...elementos].forEach((el, i) => el.animate(
        [{ opacity: 0, transform: `translateY(${distancia}px) scale(.98)` }, { opacity: 1, transform: 'none' }],
        { duration: 450, delay: i * retraso, easing: 'cubic-bezier(.2,.8,.2,1)', fill: 'backwards' },
    ));
}

/** Transición al mostrar una vista. */
export function entrarVista(el) {
    if (reducido) return;
    el.animate([{ opacity: 0, transform: 'translateY(12px)' }, { opacity: 1, transform: 'none' }],
        { duration: 380, easing: 'cubic-bezier(.2,.8,.2,1)' });
}

/** Sacudida para indicar error en un formulario. */
export function sacudir(el) {
    if (reducido || !el) return;
    el.animate([
        { transform: 'translateX(0)' }, { transform: 'translateX(-10px)' }, { transform: 'translateX(9px)' },
        { transform: 'translateX(-6px)' }, { transform: 'translateX(4px)' }, { transform: 'translateX(0)' },
    ], { duration: 420, easing: 'ease-in-out' });
}

/** Pequeño "pop" para resaltar un cambio. */
export function resaltar(el) {
    if (reducido || !el) return;
    el.animate([{ transform: 'scale(1)' }, { transform: 'scale(1.12)' }, { transform: 'scale(1)' }], { duration: 350, easing: 'ease-out' });
}

/** Salida animada antes de quitar un elemento del DOM. */
export function salir(el) {
    if (reducido || !el) return Promise.resolve();
    return el.animate([{ opacity: 1, transform: 'none' }, { opacity: 0, transform: 'scale(.92)' }],
        { duration: 260, easing: 'ease-in', fill: 'forwards' }).finished;
}

/** Efecto "ripple" (onda) al hacer clic en los botones principales. */
export function activarRipple() {
    document.addEventListener('click', e => {
        const btn = e.target.closest('.btn-brand');
        if (!btn || reducido) return;
        const r = btn.getBoundingClientRect();
        const s = Math.max(r.width, r.height);
        const span = document.createElement('span');
        span.className = 'ripple';
        Object.assign(span.style, { width: `${s}px`, height: `${s}px`, left: `${e.clientX - r.left - s / 2}px`, top: `${e.clientY - r.top - s / 2}px` });
        btn.appendChild(span);
        span.animate([{ transform: 'scale(0)', opacity: .6 }, { transform: 'scale(2.6)', opacity: 0 }], { duration: 600, easing: 'ease-out' })
            .finished.then(() => span.remove());
    });
}

/** Confeti sencillo para celebrar un pedido registrado. */
export function confeti() {
    if (reducido) return;
    const colores = ['#2b5cff', '#7b3fe4', '#00c2a8', '#ff8a3d', '#ffd36e', '#ff5f6d'];
    for (let i = 0; i < 70; i++) {
        const p = document.createElement('div');
        Object.assign(p.style, {
            position: 'fixed', top: '-12px', left: `${Math.random() * 100}vw`, width: '9px', height: '14px', zIndex: 3000,
            background: colores[i % colores.length], borderRadius: '2px', pointerEvents: 'none',
        });
        document.body.appendChild(p);
        const caida = window.innerHeight + 40;
        p.animate([
            { transform: 'translate(0,0) rotate(0)', opacity: 1 },
            { transform: `translate(${(Math.random() - .5) * 220}px, ${caida}px) rotate(${Math.random() * 720}deg)`, opacity: .9 },
        ], { duration: 1600 + Math.random() * 1400, easing: 'cubic-bezier(.25,.6,.4,1)', delay: Math.random() * 300 })
            .finished.then(() => p.remove());
    }
}
