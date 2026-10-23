/* Una reserva pertenece a la pestaña que la solicitó. Al recargar se liberan primero
 * sus tokens anteriores; pagehide acelera la liberación si el navegador lo permite. */
window.SciafReservasPagina = window.SciafReservasPagina || {
    /** @param opciones.conservar no libera al recargar ni al salir: la pantalla repone sus
     *  reservas con su estado guardado y suelta las que ya no use con liberarSobrantes. */
    abrir: function (nombre, url, opciones) {
        const conservar = !!(opciones && opciones.conservar);
        const clave = 'sciaf:reservas-pagina:' + nombre;
        // La pestaña conserva su marca al recargar; cada carga de la página suma uno. El
        // servidor descarta lo que reservaron las cargas anteriores de esta misma pestaña.
        let pagina = null, carga = 1;
        try {
            pagina = sessionStorage.getItem('sciaf:pagina');
            if (!pagina) {
                pagina = (window.crypto && crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2)).slice(0, 40);
                sessionStorage.setItem('sciaf:pagina', pagina);
            }
            carga = (parseInt(sessionStorage.getItem(clave + ':carga'), 10) || 0) + 1;
            sessionStorage.setItem(clave + ':carga', String(carga));
        } catch (e) { pagina = null; /* almacenamiento deshabilitado: sin esta protección */ }
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
        const inicio = conservar ? Promise.resolve([]) : Promise.allSettled([...activos].map(liberar));
        if (!conservar) window.addEventListener('pagehide', function () {
            if (!navigator.sendBeacon) return;
            activos.forEach(function (token) {
                navigator.sendBeacon(url, new Blob([JSON.stringify({ token })], { type: 'application/json' }));
            });
        });
        return {
            pagina: pagina,
            carga: carga,
            inicio: inicio,
            registrar: function (token) { if (token) { activos.add(token); guardar(); } },
            liberar: liberar,
            /** Libera lo que reservaron cargas anteriores de esta pestaña y ya no se usa. */
            liberarSobrantes: function (vigentes) {
                const usar = new Set(vigentes || []);
                return Promise.allSettled([...activos].filter(t => !usar.has(t)).map(liberar));
            },
            consumir: function (token) { activos.delete(token); guardar(); }
        };
    }
};
