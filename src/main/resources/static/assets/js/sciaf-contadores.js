/**
 * sciaf-contadores.js
 * Contador de caracteres para los campos cuyo largo lo impone el VSIAF.
 *
 * En el VSIAF cada columna tiene un largo fijo y lo que sobra se pierde sin avisar: el
 * nombre de la oficina admite 65 caracteres y el nombre completo del responsable 35. Antes
 * el usuario escribía de más y se enteraba (o no) cuando el dato ya estaba recortado allá.
 *
 * Uso:
 *   sciafContador.simple('#inputNombre', 65);
 *   sciafContador.combinado(['#txtNombre', '#txtPaterno', '#txtMaterno'], 35,
 *                           { etiqueta: 'Nombre completo para el VSIAF' });
 *
 * El combinado es para el nombre del responsable: en el VSIAF los tres campos viajan
 * juntos en una sola columna, así que lo que importa es la suma.
 */
(function () {
    'use strict';

    function estilos() {
        if (document.getElementById('sciaf-contadores-css')) return;
        const st = document.createElement('style');
        st.id = 'sciaf-contadores-css';
        st.textContent = `
            .sciaf-contador {
                display: flex; align-items: center; gap: 6px; justify-content: flex-end;
                font-size: .72rem; color: #8a93a8; margin-top: 3px; line-height: 1.3;
            }
            .sciaf-contador .sciaf-contador-num { font-family: 'JetBrains Mono', monospace; font-weight: 600; }
            .sciaf-contador .sciaf-contador-txt { margin-right: auto; }
            .sciaf-contador.cerca { color: #f79009; }
            .sciaf-contador.pasado { color: #f04438; font-weight: 600; }
            .sciaf-contador-barra { width: 54px; height: 4px; border-radius: 3px; background: #e4e8f0; overflow: hidden; }
            .sciaf-contador-barra i { display: block; height: 100%; background: currentColor; width: 0; transition: width .15s; }`;
        document.head.appendChild(st);
    }

    function nodo(etiqueta) {
        const d = document.createElement('div');
        d.className = 'sciaf-contador';
        d.innerHTML = `<span class="sciaf-contador-txt">${etiqueta || ''}</span>
                       <span class="sciaf-contador-barra"><i></i></span>
                       <span class="sciaf-contador-num">0/0</span>`;
        return d;
    }

    function pintar($cont, usado, max) {
        const pct = Math.min(100, Math.round((usado / max) * 100));
        $cont.querySelector('.sciaf-contador-num').textContent = usado + '/' + max;
        $cont.querySelector('.sciaf-contador-barra i').style.width = pct + '%';
        $cont.classList.toggle('cerca', usado > max * 0.85 && usado <= max);
        $cont.classList.toggle('pasado', usado > max);
    }

    function elemento(sel) {
        return (typeof sel === 'string') ? document.querySelector(sel) : sel;
    }

    window.sciafContador = {
        /** Un solo campo: además se le pone el tope real para que no se pueda pasar. */
        simple(sel, max, opciones) {
            estilos();
            const el = elemento(sel);
            if (!el || el.dataset.sciafContador) return null;
            el.dataset.sciafContador = '1';
            el.setAttribute('maxlength', max);

            const cont = nodo((opciones && opciones.etiqueta) || '');
            (opciones && opciones.contenedor ? elemento(opciones.contenedor) : el.parentNode)
                .insertBefore(cont, el.nextSibling);

            const actualizar = () => pintar(cont, (el.value || '').trim().length, max);
            el.addEventListener('input', actualizar);
            actualizar();
            return { actualizar, largo: () => (el.value || '').trim().length };
        },

        /**
         * Varios campos que en el VSIAF viajan como un solo texto (nombre + apellidos).
         * Al llegar al tope deja de aceptar lo que se escribe (sin deshabilitar el campo:
         * se puede seguir borrando, corrigiendo o acortando).
         *
         * Un nombre que YA venía largo de antes no se recorta solo al abrirlo: cortarle
         * letras a un dato existente sin que nadie lo pida sería peor. Queda en rojo y se
         * bloquea al guardar hasta que la persona lo abrevie.
         */
        combinado(selectores, max, opciones) {
            estilos();
            const els = selectores.map(elemento).filter(Boolean);
            if (!els.length) return null;
            const ultimo = els[els.length - 1];
            if (ultimo.dataset.sciafContadorCombinado) return null;
            ultimo.dataset.sciafContadorCombinado = '1';

            const cont = nodo((opciones && opciones.etiqueta) || 'Nombre completo para el VSIAF');
            const destino = (opciones && opciones.contenedor) ? elemento(opciones.contenedor) : ultimo.parentNode;
            if (opciones && opciones.contenedor) destino.appendChild(cont);
            else destino.insertBefore(cont, ultimo.nextSibling);

            const texto = () => els.map(e => (e.value || '').trim()).filter(Boolean).join(' ');
            const actualizar = () => pintar(cont, texto().length, max);

            /**
             * Quita lo que se acaba de escribir (o pegar) en ESTE campo cuando la suma se
             * pasa del tope. Se recorta justo antes del cursor y el cursor se queda ahí,
             * así que escribir de más simplemente "no entra", en vez de mover el texto.
             * Nunca toca los otros campos: si el excedente viene de ellos, la persona
             * decide cuál abreviar.
             */
            function limitar(el) {
                const exceso = texto().length - max;
                if (exceso <= 0) return;
                const v = el.value;
                const pos = (el.selectionStart == null) ? v.length : el.selectionStart;
                const corte = Math.max(0, pos - exceso);
                el.value = v.slice(0, corte) + v.slice(pos);
                try { el.setSelectionRange(corte, corte); } catch (e) { /* campo sin cursor */ }
            }

            els.forEach(e => e.addEventListener('input', function () {
                limitar(e);
                actualizar();
            }));
            actualizar();

            return {
                actualizar,
                largo: () => texto().length,
                texto,
                excede: () => texto().length > max,
                /** Mensaje listo para mostrar cuando alguien intenta guardar de más. */
                mensaje: () => `El nombre completo tiene ${texto().length} caracteres y el VSIAF admite ${max}. `
                             + `Abrevie el nombre para que no se recorte allá.`
            };
        }
    };
})();
