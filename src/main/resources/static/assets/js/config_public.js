function cargarTabla() {
    $.ajax({
        type: 'GET',
        url: "/informacion",
        success: function (response) {
            $("#informacionActivosFijos").html(response);
        }
    });
}

//PARA VALIDAR LOS INPUTS
function validarCampo(input, mensaje) {
    if (!input.value.trim()) {
        input.classList.add("is-invalid");
        if (
            !input.nextElementSibling ||
            !input.nextElementSibling.classList.contains("invalid-feedback")
        ) {
            const div = document.createElement("div");
            div.className = "invalid-feedback";
            div.innerText = mensaje;
            input.parentNode.appendChild(div);
        }
    } else {
        input.classList.remove("is-invalid");
        if (
            input.nextElementSibling &&
            input.nextElementSibling.classList.contains("invalid-feedback")
        ) {
            input.nextElementSibling.remove();
        }
    }
}

//FILTROS DE BUSQUEDA API
$(function () {

    $('input[name="unidad"], input[name="ubicacionActivo"]').autocomplete({
        source: function (request, response) {
            $.ajax({
                url: "/api/oficinas/sugerencias",
                data: { termino: request.term },
                success: function (data) {
                    response(data);
                },
            });
        },
        minLength: 2,
        appendTo: "#formularioModalAsignacionActivo",
        delay: 300,
    });

    $('input[name="codigoFuncionario"]').on("input", function () {
        const codigo = $(this).val();
        if (codigo.length >= 1) {
            $.get(
                "/api/responsables/datos",
                { codigo: codigo },
                function (data) {
                    if (data && data.ci) {
                        $('input[name="ci"]').val(data.ci);
                    } else {
                        $('input[name="ci"]').val("");
                    }
                }
            );
        }
    });

    $('input[name="unidadDestino"], input[name="unidadOrigen"], input[name="ubicacionOrigen"], input[name="ubicacionActual"]').autocomplete({
        source: function (request, response) {
            $.ajax({
                url: "/api/oficinas/sugerencias",
                data: { termino: request.term },
                success: function (data) {
                    response(data);
                },
            });
        },
        minLength: 2,
        appendTo: "#formularioModalTranferenciaActivo",
        delay: 300,
    });

    $('input[name="unidadPropietario"], input[name="unidadAutorizador"]').autocomplete({
        source: function (request, response) {
            $.ajax({
                url: "/api/oficinas/sugerencias",
                data: { termino: request.term },
                success: function (data) {
                    response(data);
                },
            });
        },
        minLength: 2,
        appendTo: "#formIngresoActivos",
        delay: 300,
    });

    $('input[name^="codigoFuncionario"]').on("input", function () {
        const $this = $(this);
        const codigo = $this.val();

        if (codigo.length >= 1) {
            const tipo = $this.attr("name").replace("codigoFuncionario", "");
            $.get(
                "/api/responsables/datos",
                { codigo: codigo },
                function (data) {
                    if (data && data.ci) {
                        $(`input[name="ci${tipo}"]`).val(data.ci);
                    } else {
                        $(`input[name="ci${tipo}"]`).val("");
                    }
                }
            );
        }
    });
});