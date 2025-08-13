//INICIO DE SESION
$(document).ready(function () {
    $("#formularioLogin").submit(function (event) {
        event.preventDefault();
        if (this.checkValidity() === false) {
            $(this).addClass("was-validated");
            return;
        }
        var form = $(this)[0];
        var formData = new FormData(form);

        $.ajax({
            type: "POST",
            url: $(this).attr("action"),
            data: formData,
            contentType: false,
            processData: false,
            success: function (response) {
                if (response === "Iniciando Session") {
                    Swal.fire({
                        title: "Iniciando Session",
                        timerProgressBar: true,
                        didOpen: () => {
                            Swal.showLoading();
                            const timer = Swal.getPopup().querySelector("b");
                            timerInterval = setInterval(() => {
                                timer.textContent = `${Swal.getTimerLeft()}`;
                            }, 100);
                            window.location.href = "/adm/inicio";
                        },
                        willClose: () => {
                            clearInterval(timerInterval);
                        },
                    });
                } else {
                    Swal.fire("Imposible Continuar!", response + ".", "error");
                }
            },
            error: function (xhr, status, error) {
                Swal.fire(
                    "Imposible Continuar!",
                    "Ha ocurrido un error. Por favor, intenta nuevamente." + xhr,
                    status,
                    error,
                    "error"
                );
                console.error(error);
            },
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