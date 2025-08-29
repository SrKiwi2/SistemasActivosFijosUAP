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
                if (response === "Iniciando Session" || response === "Inicio Responsable") {
                    // 1) Cerrar el modal de login
                    $("#modalLogin").modal("hide");

                    // 2) Mostrar loader por encima y redirigir inmediatamente
                    const destino = (response === "Inicio Responsable")
                        ? "/adm/responsable"    // o /adm/responsable si esa es tu ruta real
                        : "/adm/inicio"; // o /adm/inicio

                    Swal.fire({
                        title: "Iniciando sesión…",
                        allowOutsideClick: false,
                        allowEscapeKey: false,
                        showConfirmButton: false,
                        didOpen: () => {
                            Swal.showLoading();
                            // Redirige YA; no esperes cierre del Swal
                            window.location.href = destino;
                        }
                    });
                } else {
                    Swal.fire("Imposible continuar", response + ".", "error");
                }
            },
            error: function (xhr) {
                let msg = "Ha ocurrido un error. Por favor, intenta nuevamente.";
                try {
                    const json = xhr.responseJSON || JSON.parse(xhr.responseText);
                    if (json && json.message) msg = json.message;
                } catch (e) { }
                Swal.fire("Imposible continuar", msg, "error");
            }
        });
    });
});

//FIN INICIO DE SESION

// VER CONTRASEÑA MODAL LOGIN
const togglePassword = document.getElementById("togglePassword");
const passwordInput = document.getElementById("contrasena");
const iconToggle = document.getElementById("iconToggle");

togglePassword.addEventListener("click", () => {
    const isPassword = passwordInput.getAttribute("type") === "password";
    passwordInput.setAttribute("type", isPassword ? "text" : "password");
    iconToggle.className = isPassword ? "ti ti-eye" : "ti ti-eye-off";
});