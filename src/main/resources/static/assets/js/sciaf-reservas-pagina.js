/* Una reserva pertenece a la pestaña que la solicitó. Al recargar se liberan primero
 * sus tokens anteriores; pagehide acelera la liberación si el navegador lo permite. */
window.SciafReservasPagina = window.SciafReservasPagina || {
    abrir: function (nombre, url) {
        const clave = 'sciaf:reservas-pagina:' + nombre;
        let anteriores = [];
        try { anteriores = JSON.parse(sessionStorage.getItem(clave) || '[]'); } catch (e) { /* almacenamiento deshabilitado */ }
        const activos = new Set(Array.isArray(anteriores) ? anteriores.filter(t => typeof t === 'string' && t.length <= 40) : []);
        function guardar() {
            try {
                if (activos.size) sessionStorage.setItem(clave, JSON.stringify([...activos]));
                else sessionStorage.removeItem(clave);
            } catch (e) { /* almacenamiento deshabilitado */ }
        }
        async function liberar(token) {
            if (!token || !activos.has(token)) return true;
            try {
                const respuesta = await fetch(url, { method: 'POST', credentials: 'same-origin',
                    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }) });
                if (respuesta.ok && (await respuesta.json()).ok) {
                    activos.delete(token);
                    guardar();
                    return true;
                }
            } catch (e) { /* se reintentará en la próxima carga o al vencer la reserva */ }
            return false;
        }
        const inicio = Promise.allSettled([...activos].map(liberar));
        window.addEventListener('pagehide', function () {
            if (!navigator.sendBeacon) return;
            activos.forEach(function (token) {
                navigator.sendBeacon(url, new Blob([JSON.stringify({ token })], { type: 'application/json' }));
            });
        });
        return {
            inicio: inicio,
            registrar: function (token) { if (token) { activos.add(token); guardar(); } },
            liberar: liberar,
            consumir: function (token) { activos.delete(token); guardar(); }
        };
    }
};
