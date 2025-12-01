$(function () {
    $("#formularioLogin").on("submit", function (e) {
        e.preventDefault();
        
        if (!this.checkValidity()) {
            $(this).addClass("was-validated");
            return;
        }

        const formData = new FormData(this);

        $.ajax({
            type: "POST",
            url: this.action,
            data: formData,
            contentType: false,
            processData: false,
            success: function (response) {
                // Mapeo de respuestas a rutas de destino
                const rutasDestino = {
                    "Iniciando Session": "/adm/inicio",           // ADMINISTRADOR
                    "Inicio Responsable": "/adm/responsable",     // RESPONSABLE
                    "Inicio Recepcion": "/administracion/hoja-ruta/vista",  // RECEPCION
                    "Inicio Contador": "/contabilidad/inicio"     // CONTADOR
                };

                // Verificar si la respuesta corresponde a un inicio de sesión exitoso
                if (rutasDestino.hasOwnProperty(response)) {
                    // 1) Cerrar el modal de login si existe
                    $("#modalLogin").modal("hide");

                    // 2) Obtener la ruta de destino
                    const destino = rutasDestino[response];

                    // 3) Mostrar loader y redirigir
                    Swal.fire({
                        title: "Iniciando sesión…",
                        text: "Redirigiendo al sistema",
                        allowOutsideClick: false,
                        allowEscapeKey: false,
                        showConfirmButton: false,
                        didOpen: () => {
                            Swal.showLoading();
                            // Redirige inmediatamente
                            window.location.href = destino;
                        }
                    });
                } else {
                    // Es un mensaje de error
                    Swal.fire({
                        icon: "error",
                        title: "Imposible continuar",
                        text: response + ".",
                        confirmButtonText: "Aceptar"
                    });
                }
            },
            error: function (xhr) {
                let msg = "Ha ocurrido un error. Por favor, intenta nuevamente.";
                
                try {
                    const json = xhr.responseJSON || JSON.parse(xhr.responseText);
                    if (json && json.message) {
                        msg = json.message;
                    }
                } catch (e) {
                    // Si no se puede parsear, usar mensaje por defecto
                }
                
                Swal.fire({
                    icon: "error",
                    title: "Imposible continuar",
                    text: msg,
                    confirmButtonText: "Aceptar"
                });
            }
        });
    });

    // Toggle para mostrar/ocultar contraseña
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
});


//FIN INICIO DE SESION

// VER CONTRASEÑA MODAL LOGIN
// /assets/js/login_publico.js
document.addEventListener('DOMContentLoaded', () => {
  const togglePassword = document.getElementById('togglePassword');
  const passwordInput  = document.getElementById('contrasena');
  const iconToggle     = document.getElementById('iconToggle');

  if (!togglePassword || !passwordInput || !iconToggle) return;

  togglePassword.addEventListener('click', () => {
    const isPassword = passwordInput.type === 'password';
    passwordInput.type = isPassword ? 'text' : 'password';
    iconToggle.className = isPassword ? 'ti ti-eye' : 'ti ti-eye-off';
  });
});
