// Portada restaurada con "Atrás" desde la caché del navegador: se pide de nuevo, así el
// servidor decide (con sesión abierta lleva a la pantalla de inicio, sin sesión la muestra).
window.addEventListener('pageshow', function (e) {
    if (e.persisted) window.location.reload();
});

$(document).ready(function () {

    // 0. Por qué se llegó acá (lo pone el servidor al cerrar la sesión)
    (function avisoSesion() {
        const params = new URLSearchParams(window.location.search);
        const motivo = params.get('sesion');
        if (!motivo) return;
        // Se limpia la URL: recargar o compartirla no debe repetir el aviso.
        params.delete('sesion');
        const resto = params.toString();
        history.replaceState(null, '', window.location.pathname + (resto ? '?' + resto : '') + window.location.hash);

        if (motivo === 'inactividad') {
            $('<div id="loginInfo" class="alert alert-warning small text-start py-2 px-3 mb-4" style="border-radius: 8px;">' +
              '<i class="ti ti-clock-exclamation me-2"></i>Su sesión se cerró por inactividad. Vuelva a ingresar para continuar.' +
              '</div>').insertBefore('#loginAlert');
            $('#modalLogin').modal('show');
        } else if (motivo === 'revocada') {
            $('<div id="loginInfo" class="alert alert-warning small text-start py-2 px-3 mb-4" style="border-radius: 8px;">' +
              '<i class="ti ti-lock me-2"></i>Su sesión en este equipo se cerró (desde otro equipo o por el administrador). Vuelva a ingresar.' +
              '</div>').insertBefore('#loginAlert');
            $('#modalLogin').modal('show');
        } else if (motivo === 'cerrada' && window.Swal) {
            Swal.fire({ toast: true, position: 'top-end', icon: 'success', timer: 3000,
                showConfirmButton: false, title: 'Sesión cerrada correctamente' });
        }
    })();

    // 1. Mostrar/Ocultar Contraseña (Versión única y limpia)
    $("#togglePassword").on("click", function () {
        const passwordInput = $("#contrasena");
        const iconToggle = $("#iconToggle");
        
        if (passwordInput.attr("type") === "password") {
            passwordInput.attr("type", "text");
            iconToggle.removeClass("ti-eye-off").addClass("ti-eye");
        } else {
            passwordInput.attr("type", "password");
            iconToggle.removeClass("ti-eye").addClass("ti-eye-off");
        }
    });

    // 2. Manejo del Formulario de Login
    $("#formularioLogin").on("submit", function (e) {
        e.preventDefault();
        
        const form = $(this);
        const btnSubmit = form.find('button[type="submit"]');
        const alertBox = $("#loginAlert"); // Nuevo contenedor para errores

        // Ocultar alerta previa y resetear validación
        alertBox.slideUp();
        
        if (!this.checkValidity()) {
            form.addClass("was-validated");
            return;
        }

        // Estado de carga en el botón
        const originalBtnText = btnSubmit.html();
        btnSubmit.html('<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>Validando...').prop('disabled', true);

        const formData = new FormData(this);

        $.ajax({
            type: "POST",
            url: this.action,
            data: formData,
            contentType: false,
            processData: false,
            success: function (response) {
                // Diccionario actualizado (Asegúrate de incluir tu "Nuevo Rol" o "Apoyo" aquí)
                const rutasDestino = {
                    "Iniciando Session": "/adm/inicio",
                    "Inicio Responsable": "/adm/responsable",
                    "Inicio Recepcion": "/administracion/hoja-ruta/vista",
                    "Inicio Contador": "/contabilidad/inicio",
                    // Añade más roles si los configuraste en el controlador
                };

                if (rutasDestino.hasOwnProperty(response)) {
                    // Éxito: Redirigir (El SweetAlert aquí sí está bien, es para transición exitosa)
                    $("#modalLogin").modal("hide");
                    const destino = rutasDestino[response];
                    
                    Swal.fire({
                        title: "Acceso Concedido",
                        text: "Iniciando entorno de trabajo...",
                        icon: "success",
                        showConfirmButton: false,
                        timer: 1500,
                        timerProgressBar: true,
                        didOpen: () => {
                            Swal.showLoading();
                        }
                    }).then(() => {
                        // replace: la portada no queda en el historial, así "Atrás" desde el
                        // sistema no vuelve a ella con la sesión abierta.
                        window.location.replace(destino);
                    });
                } else {
                    // ERROR: Mostrar alerta DENTRO del modal, no con SweetAlert molesto
                    btnSubmit.html(originalBtnText).prop('disabled', false);
                    alertBox.html('<i class="ti ti-alert-circle me-2"></i>' + response).slideDown();
                    // Limpiar solo la contraseña para que el usuario intente de nuevo rápido
                    $("#contrasena").val('').focus();
                }
            },
            error: function () {
                btnSubmit.html(originalBtnText).prop('disabled', false);
                alertBox.html('<i class="ti ti-wifi-off me-2"></i>Error de conexión con el servidor.').slideDown();
            }
        });
    });

    // 3. Limpiar el formulario al cerrar el modal
    $('#modalLogin').on('hidden.bs.modal', function () {
        $("#formularioLogin").removeClass("was-validated")[0].reset();
        $("#loginAlert").hide();
        $("#loginInfo").remove();
    });
});