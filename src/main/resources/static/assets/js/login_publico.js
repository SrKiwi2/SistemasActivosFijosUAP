$(document).ready(function () {

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
                        window.location.href = destino;
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
    });
});