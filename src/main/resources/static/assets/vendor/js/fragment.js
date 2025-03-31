// Función para cargar un formulario dentro de un modal con verificación de sesión
function cargarFormularioAlert(urlFormulario, idContenedorModal, idFormulario) {
    // Verifica si la sesión está activa antes de cargar el formulario
    $.ajax({
        url: "/adm/cargar-datos", // URL para verificar la sesión
        method: "GET",
        success: function () {
            // Si la sesión es válida, procede a cargar el formulario
            $.ajax({
                type: 'POST',
                url: urlFormulario,  // Ruta del método del controlador en Spring Boot para cargar el formulario
                success: function (response) {
                    $(idContenedorModal).html(response);  // Actualiza el contenido del modal con la respuesta del servidor

                    // Inicializa select2 en los elementos con la clase .select2 dentro del formulario
                    $('.select2').select2({
                        dropdownParent: $(idFormulario)  // Especificar el parent para el dropdown
                    });
                },
                error: function (xhr) {
                    console.error('Error en la solicitud del formulario:', xhr);
                }
            });
        },
        error: function (xhr) {
            if (xhr.status === 401) {
                // Si la sesión ha expirado, muestra una alerta con SweetAlert2
                Swal.fire({
                    title: 'Sesión expirada',
                    text: 'Tu sesión ha expirado. Por favor, inicia sesión nuevamente.',
                    icon: 'warning',
                    confirmButtonText: 'Ir al login'
                }).then((result) => {
                    if (result.isConfirmed) {
                        window.location.href = '/form-login'; // Redirige a la página de login
                    }
                });
            }
        }
    });
}

// Función para cargar un formulario de edición con un ID específico dentro de un modal
function cargarFormularioEditAlert(id, urlFormulario, idContenedorModal, idFormulario) {
    // Verifica si la sesión está activa antes de cargar el formulario
    $.ajax({
        url: "/adm/cargar-datos", // URL para verificar la sesión
        method: "GET",
        success: function () {
            // Si la sesión es válida, procede a cargar el formulario
            $.ajax({
                type: 'POST',
                url: urlFormulario + "/" + id,  // Agrega el ID a la URL del formulario
                success: function (response) {
                    $(idContenedorModal).html(response);  // Actualiza el contenido del modal con la respuesta del servidor

                    // Inicializa select2 en los elementos con la clase .select2 dentro del formulario
                    $('.select2').select2({
                        dropdownParent: $(idFormulario)  // Especificar el parent para el dropdown
                    });
                },
                error: function (xhr) {
                    console.error('Error en la solicitud del formulario:', xhr);
                }
            });
        },
        error: function (xhr) {
            if (xhr.status === 401) {
                // Si la sesión ha expirado, muestra una alerta con SweetAlert2
                Swal.fire({
                    title: 'Sesión expirada',
                    text: 'Tu sesión ha expirado. Por favor, inicia sesión nuevamente.',
                    icon: 'warning',
                    confirmButtonText: 'Ir al login'
                }).then((result) => {
                    if (result.isConfirmed) {
                        window.location.href = '/form-login'; // Redirige a la página de login
                    }
                });
            }
        }
    });
}


// Función para eliminar un registro después de verificar la sesión
function eliminarRegistroAlert(nombre, id, urlEliminar, cargarTablaFuncion) {
    // Verifica si la sesión está activa antes de intentar eliminar
    $.ajax({
        url: "/adm/cargar-datos",  // URL para verificar la sesión
        method: "GET",
        success: function () {
            // Si la sesión es válida, procede con la eliminación
            Swal.fire({
                title: 'Eliminar Registro',
                text: '¿Estás seguro de eliminar a ' + nombre + '?',
                icon: 'warning',
                showCancelButton: true,
                confirmButtonText: 'Sí, eliminar',
                cancelButtonText: 'Cancelar',
                reverseButtons: true
            }).then((result) => {
                if (result.isConfirmed) {
                    // Si el usuario confirma, realizar la llamada AJAX para eliminar
                    $.ajax({
                        url: urlEliminar + "/" + id,  // URL para eliminar el registro con el ID
                        type: 'POST',
                        success: function (response) {
                            cargarTablaFuncion();  // Recargar la tabla (función pasada como parámetro)
                            Swal.fire(
                                'Eliminado!',
                                response,
                                'success'
                            );
                        },
                        error: function (xhr, status, error) {
                            Swal.fire(
                                'Error',
                                'Hubo un problema al eliminar el registro. Por favor, inténtalo de nuevo.',
                                'error'
                            );
                        }
                    });
                }
            });
        },
        error: function (xhr) {
            if (xhr.status === 401) {
                // Si la sesión ha expirado, muestra una alerta con SweetAlert2
                Swal.fire({
                    title: 'Sesión expirada',
                    text: 'Tu sesión ha expirado. Por favor, inicia sesión nuevamente.',
                    icon: 'warning',
                    confirmButtonText: 'Ir al login'
                }).then((result) => {
                    if (result.isConfirmed) {
                        window.location.href = '/form-login';  // Redirige a la página de login
                    }
                });
            }
        }
    });
}

// Función para manejar el envio de formulario de registro
function manejarEnvioFormulario(selectorFormulario) {
    $(document).ready(function () {
        $(selectorFormulario).submit(function (event) {
            event.preventDefault();

            if (this.checkValidity() === false) {
                $(this).addClass('was-validated');
                return;
            }

            // Verifica si la sesión está activa antes de enviar el formulario
            $.ajax({
                url: "/adm/cargar-datos",
                method: "GET",
                success: function () {
                    // Si la sesión es válida, continúa con el envío del formulario
                    var form = $(selectorFormulario)[0];
                    var formData = new FormData(form);

                    $.ajax({
                        type: 'POST',
                        url: $(selectorFormulario).attr('action'),
                        data: formData,
                        contentType: false,  // No establecer el tipo de contenido aquí
                        processData: false,  // No procesar los datos
                        success: function (response) {
                            if (response === 'Se realizó el registro correctamente') {
                                cargarTabla();
                                $('.modal').modal('hide');
                                //cargarFormulario();
                                Swal.fire(
                                    'Registrado!',
                                    response + '.',
                                    'success'
                                );
                            } else if (response === 'Se realizó la modificación correctamente' ){
                                cargarTabla();
                                $('.modal').modal('hide');
                                //cargarFormulario();
                                Swal.fire(
                                    'Modificado!',
                                    response + '.',
                                    'success'
                                );
                            } else if (response === 'Odontólogo reactivado y modificado correctamente.' ){
                                cargarTabla();
                                $('.modal').modal('hide');
                                //cargarFormulario();
                                Swal.fire(
                                    'Reactivado!',
                                    response + '.',
                                    'success'
                                );
                            } else {
                                Swal.fire(
                                    'Imposible Registrar!',
                                    response + '.',
                                    'error'
                                );
                            }
                        },
                        error: function (xhr, status, error) {
                            Swal.fire(
                                'Imposible Registrar!',
                                'Ha ocurrido un error. Por favor, intenta nuevamente.' + xhr, status, error,
                                'error'
                            );
                            console.error(error);
                        }
                    });
                },
                error: function (xhr) {
                    if (xhr.status === 401) {
                        // Si la sesión ha expirado, muestra una alerta con SweetAlert2
                        Swal.fire({
                            title: 'Sesión expirada',
                            text: 'Tu sesión ha expirado. Por favor, inicia sesión nuevamente.',
                            icon: 'warning',
                            confirmButtonText: 'Ir al login'
                        }).then((result) => {
                            if (result.isConfirmed) {
                                window.location.href = '/form-login'; // Redirige a la página de login
                            }
                        });
                    }
                }
            });
        });
    });
}

