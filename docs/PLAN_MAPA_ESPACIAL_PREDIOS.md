# Mapa espacial de predios, oficinas y activos

## Decisiones confirmadas

- El piloto será **CAMPUS UNIVERSITARIO LAS PALMAS**.
- La imagen `FEXPO_2026_plano_5100x6600_600dpi222.png` es una **referencia de composición**, no un plano actual ni una fuente de medidas o asignaciones oficiales. Sus puestos y rótulos de feria no deben importarse como oficinas.
- El plano inicial será referencial. El editor debe permitir desplazar, redimensionar, cambiar altura y añadir detalles más adelante.
- Una oficina independiente puede ser una construcción de planta baja o una construcción con varios niveles; quien diseña el mapa elegirá su representación.
- Una oficina administrativa puede vincularse a uno o varios espacios dibujados. Los cuartos, baños, pasillos y accesos no se convierten automáticamente en oficinas administrativas.
- La edición estará disponible al administrador inicialmente. El permiso de edición podrá asignarse después a usuarios concretos.

## Modelo conceptual

```text
Predio existente
  MapaPredio (límite, orientación opcional, estado, versión)
    Construccion (edificio, bloque u oficina independiente; huella y altura visual)
      Nivel (planta baja, piso 1, piso 2...; orden y altura visual)
        PlanoNivel (geometría editable)
          Espacio (polígono; tipo: oficina, cuarto, pasillo, baño, otro)
            VinculoOficina (0..1 oficina existente por espacio; varios espacios pueden apuntar a la misma oficina)
            UbicacionVisualActivo (posición de un activo existente dentro del espacio)
```

Una construcción clasificada como oficina independiente también puede tener niveles. La clasificación controla el nombre y la navegación, no limita la geometría. Una oficina que ocupa más de un espacio se abre como un conjunto, con sus cuartos destacados. Los elementos abiertos del predio, como patios y accesos, viven directamente en `MapaPredio`; si una oficina funciona en una zona abierta, se puede dibujar allí un espacio vinculado sin crear una construcción ficticia.

Cada figura guarda geometría vectorial en unidades de **escena** (sin afirmar metros reales): origen, ancho, fondo, vértices y rotación. La escala física queda opcional. Cambiar el tamaño de una construcción o nivel aplica una transformación a su geometría hija, de modo que el editor pueda conservar la distribución relativa; antes de confirmar, debe mostrar una previsualización. La altura visual del nivel es editable e independiente de la escala 2D. Registrar `version` permite evitar que dos ediciones concurrentes se sobrescriban.

La geometría y las asignaciones son datos separados: la figura usa el ID del espacio; el espacio puede vincularse al ID de `Oficina`; la figura de un activo usa el ID de `Activo`. El nombre, código y oficina vigente se leen de las entidades existentes. Ningún dato de oficina o activo procedente de DBF se copia al mapa como fuente de verdad.

## Pantallas

### Diseñador

1. Seleccionar predio existente y abrir un borrador del mapa.
2. Cargar opcionalmente una imagen de fondo de referencia; moverla, rotarla y ajustar su opacidad. Guardar una versión web ligera, no servir el PNG original de 5100 × 6600 en cada visita.
3. Dibujar límite, caminos y construcciones. Elegir para cada construcción la presentación `EDIFICIO`, `BLOQUE` u `OFICINA INDEPENDIENTE`; añadir los niveles necesarios.
4. En un nivel, dibujar espacios y divisiones interiores. Buscar por nombre/código y vincular espacios con oficinas ya registradas. Mostrar oficinas sin vincular y vínculos duplicados para revisión.
5. En un espacio vinculado, buscar activos cuya oficina vigente coincida; colocarlos con figura, posición, tamaño y giro. Permitir una figura genérica cuando el tipo no esté definido.
6. Guardar borrador, deshacer/rehacer, previsualizar y publicar una versión. La versión publicada alimenta el explorador; el borrador no altera lo que ven los consultantes.

### Explorador

1. Buscar predio y ver el mapa general 2D de inmediato. Cargar la vista 3D vectorial solo al abrirla, sin exigir WebGL.
2. Filtrar por construcción, nivel, nombre/código de oficina o código de activo. Resaltar resultados sin cargar todo el detalle del inventario.
3. Seleccionar construcción → nivel → espacio/oficina → activo. Una oficina vinculada a varios espacios resalta todos ellos.
4. Al seleccionar un activo, mostrar datos básicos y enlace a su ficha autorizada. Indicar claramente cuando un espacio o activo todavía no tiene posición dibujada.

## Reglas de consistencia y permisos

- Colocar o mover una figura **solo cambia la ubicación visual**. Transferir un activo a otra oficina requiere el flujo administrativo existente.
- Si la sincronización o una operación cambia `Activo.oficina`, la ubicación visual anterior pasa a estado `POR_REVISAR`; no se muestra como ubicación vigente ni se recoloca automáticamente.
- Un activo puede tener, como máximo, una ubicación visual vigente. No se permite colocarlo en un espacio vinculado a otra oficina. La asociación se valida en el servidor en cada guardado.
- Distinguir permisos `VER_MAPA`, `EDITAR_MAPA` y `PUBLICAR_MAPA`; inicialmente `EDITAR_MAPA` y `PUBLICAR_MAPA` quedan para administradores, con posibilidad de asignación explícita por usuario. Validarlos en servicio/controlador, además del menú, porque las rutas `/api/**` están abiertas por configuración general.
- Registrar quién cambió geometría, vínculos, posiciones y publicaciones. Conservar versiones publicadas para recuperación.

## Estado del primer corte implementado

- Pantallas nuevas en el menú: **Diseñar mapa espacial** y **Explorar mapa espacial**. El diseñador permite nombrar la construcción, clasificarla como edificio u oficina independiente, agregar pisos y espacios, y vincular una oficina real buscándola por nombre o código. La oficina se obtiene de la tabla `oficina`, filtrada por el predio elegido.
- Cada espacio vinculado permite buscar activos vigentes de esa oficina y colocar figuras rectangulares, moverlas, ajustar sus dimensiones y giro. El explorador muestra el código y la descripción del activo; también señala figuras cuya oficina cambió después de guardar.
- El plano se guarda en `mapa_espacial`, separado de las tablas sincronizadas con DBF. Se usa una versión para detectar ediciones concurrentes y se valida en servidor la pertenencia de oficinas y activos.
- El primer 3D es una **vista isométrica SVG ligera** del predio, con pisos y altura visual. Sirve para recorrer construcciones sin requerir WebGL. Rotación libre de cámara, paredes interiores extruidas, fondo de imagen, publicación de borradores e historial de versiones quedan para iteraciones posteriores.
- No se precarga una distribución de Las Palmas: el editor selecciona ese predio por nombre si está disponible, y el usuario dibuja el primer edificio. La imagen de FEXPO aún no se carga como fondo dentro de la aplicación.

## Evolución y rendimiento

- Backend en el Spring Boot existente, con tablas nuevas en PostgreSQL y endpoints JSON por predio, construcción, nivel y espacio. No agregar estos campos al DBF.
- SVG interactivo para mapas y planos 2D. La vista isométrica inicial usa SVG y no requiere WebGL. Si luego se necesita rotar la cámara o mostrar volúmenes interiores, evaluar Three.js y cargarlo solo al solicitar esa vista; conservar siempre la navegación 2D.
- En el primer corte se carga un documento de geometría por predio; las búsquedas de oficinas y activos sí se paginan a 30 resultados. Para un predio completo con muchos edificios, separar el documento en geometría por construcción/nivel y cargarla al abrirlo.
- Limitar detalles geométricos, comprimir JSON e imágenes, y cachear versiones publicadas. Renderizar al cambiar la vista, no en un ciclo continuo cuando la escena está quieta.
- La vista debe integrarse con las pestañas existentes: eventos y recursos encapsulados por módulo, y liberar el contexto 3D al cerrar la pestaña.

## Primer corte verificable

1. Seleccionar el predio real de Las Palmas por ID, sin inventar un registro nuevo.
2. Crear un mapa de borrador desde cero o con la imagen FEXPO como fondo marcado `REFERENCIAL`.
3. Dibujar dos construcciones de prueba: una oficina independiente configurable y un edificio con planta baja y dos niveles. Dibujar espacios en un nivel y vincular al menos una oficina real.
4. Colocar algunos activos reales de esa oficina, abrirlos desde el explorador y comprobar que el detalle coincide con inventario.
5. Modificar ancho, fondo y altura; comprobar que el editor previsualiza el cambio y conserva los vínculos.
6. Probar consulta 2D con WebGL deshabilitado, consulta 3D en equipo modesto y carga con red limitada. Medir tiempos y ajustar el presupuesto de geometría antes de ampliar a todo el predio.

## Insumos pendientes para representar el campus real

- Confirmar cuáles estructuras de la imagen FEXPO corresponden hoy a construcciones permanentes del campus; los rótulos de feria no bastan para deducirlo.
- Obtener, cuando existan, nombres de edificios, pisos y croquis; mientras tanto, el editor permitirá crearlos manualmente y marcar su precisión como `REFERENCIAL`.
- Confirmar en la interfaz el registro exacto de `Predio` para Las Palmas y vincular las oficinas existentes a los espacios dibujados. La imagen no determina esos vínculos.

## Hoja de ruta acordada para la siguiente iteración

La versión anterior es un **prototipo funcional**, no un editor CAD completo. Los rectángulos y la vista isométrica sirven para verificar vínculos con la BD, pero no pueden reproducir las huellas irregulares del plano FEXPO ni un edificio 3D manipulable. Cada fase termina con demostración, pruebas sobre Las Palmas, correcciones y aprobación del usuario antes de iniciar la siguiente.

| Fase | Entrega | Criterio para cerrar la fase |
|---|---|---|
| **1. Estructura y flujo** | Editor claro `predio → construcción → piso → espacio`; edificio nombrado de solo planta baja o varios pisos; varias oficinas reales en una planta; espacios sin oficina (pasillos, baños); vínculos múltiples de una oficina con varios cuartos; navegación y listado visibles. | Crear un edificio con planta baja, tres oficinas de la BD y un pasillo; guardar, recargar y explorar sin perder vínculos. |
| **2. Dibujo 2D avanzado** | Huellas poligonales irregulares, mover vértices, añadir/quitar lados, paredes y divisiones, puertas, ventanas, escaleras, cuadrícula, ajuste, zoom, deshacer/rehacer, capas y fondo de plano opcional. Migración de los rectángulos actuales a polígonos. | Reproducir de forma referencial al menos una construcción irregular de Las Palmas y modificarla sin redibujar todo ni perder oficinas. |
| **3. Modelo 3D navegable** | Generar volúmenes desde los polígonos y alturas; órbita, acercamiento, selección, ocultar techo/pisos y resaltar oficinas. Carga diferida, calidad adaptable y recorrido 2D permanente. | Abrir y recorrer el edificio piloto en un equipo antiguo representativo; seleccionar piso y oficina y obtener el mismo resultado que en 2D. Medir tiempos y memoria. |
| **4. Interiores y activos** | Editar cuartos dentro de oficinas; catálogo ligero de figuras (mesa, silla, equipo, estante, etc.), tamaño, giro, ubicación y ficha del activo real. Muebles sin activo inventariable se distinguen de los activos vinculados. | Dibujar una oficina con dos cuartos, colocar mobiliario y varios activos reales; comprobar búsquedas, desplazamiento y cambios de oficina por sincronización. |
| **5. Publicación y ampliación** | Borrador/publicación, historial y recuperación, permisos por usuario, importación de planos referenciales, optimización por edificio/piso, auditoría y extensión al resto del predio. | Administrador publica una versión revisada; usuarios consultan sin editar; pruebas de red lenta y navegador/equipo definidos por la UAP. |

### Reglas técnicas que guían todas las fases

- La **planta baja es un nivel**, no un tipo especial de edificio. Puede contener tantas oficinas como se dibujen; el edificio puede tener solo ese nivel o añadir pisos después.
- Las oficinas administrativas se eligen siempre del catálogo existente de `Oficina` del predio. Un espacio físico puede vincular una oficina; varios espacios pueden vincular la misma oficina. Pasillos y servicios pueden quedar sin vínculo.
- El editor guarda geometría vectorial y relaciones por ID. El modelo debe permitir polígonos, huecos y líneas de pared; no conviene seguir agregando casos al esquema de rectángulos. La escena 3D se genera de la geometría 2D validada.
- Los elementos como mesa o silla pueden ser **mobiliario de referencia** sin registro de activo, o representar un `Activo` real. Solo los vinculados muestran código e información oficial.
- Las alturas y distancias seguirán marcadas como referenciales hasta tener medidas. Reescalar una construcción deberá transformar su contenido con previsualización.
- Antes de cerrar cada fase se mostrará al usuario la pantalla funcionando y una lista breve de criterios cumplidos, defectos conocidos y ajustes pedidos. Su confirmación inicia la fase siguiente.

### Guion de aceptación de la fase 1

1. Reiniciar la aplicación para cargar las nuevas opciones del menú. Entrar con una cuenta administradora y abrir **Ámbito geográfico → Diseñar mapa espacial**.
2. Elegir **CAMPUS UNIVERSITARIO LAS PALMAS**. Pulsar **Crear edificio** y cambiar el nombre `NUEVO EDIFICIO` por `EDIFICIO PILOTO LAS PALMAS` (u otro nombre de prueba). El edificio nace con **PLANTA BAJA**; no agregar otro piso para esta comprobación.
3. Pulsar **Entrar a PLANTA BAJA**. Usar **Añadir oficina** tres veces. En cada espacio, buscar por código o nombre y elegir tres oficinas existentes de ese predio. Usar **Pasillo** una vez y dejarlo sin vínculo de oficina.
4. Si una oficina ocupa más de un cuarto, entrar en uno de sus espacios vinculados y pulsar **Cuarto de esta oficina**. El nuevo cuarto debe compartir el mismo código de oficina.
5. Pulsar **Guardar plano**. Salir del módulo y volver a entrar; comprobar que nombre, planta, oficinas, cuarto y pasillo permanecen.
6. Abrir **Explorar mapa espacial**, elegir el mismo predio y recorrer edificio → PLANTA BAJA → cada espacio. Las oficinas deben mostrar el código y nombre que aparecen en la BD; el pasillo no debe mostrar una oficina inventada.

**Visto bueno:** el usuario confirma el guion cuando el recorrido le resulte claro y los vínculos se conserven. Si algo falla, informa el paso exacto, el resultado observado y, si puede, una captura. Se corrige dentro de la fase 1 antes de comenzar los polígonos de la fase 2.

### Correcciones tras la primera prueba de la fase 1

- Los rótulos largos del edificio y de los espacios se acortan y se recortan dentro de sus figuras; el nombre completo sigue disponible en el panel y al pasar el cursor.
- Al abrir un espacio vinculado se consulta automáticamente el inventario vigente de su oficina, paginado, con código, descripción, responsable y estado físico. Se diferencia **sin posición dibujada** de **ubicado**. El administrador puede colocar una figura desde la misma fila sin buscar el activo por segunda vez. Colocar una figura no cambia la oficina oficial del activo.
- El plano ajusta el encuadre a las construcciones visibles; las vistas de piso y espacio se amplían automáticamente. Existe el botón **Ampliar vista** en ambos módulos. El panel lateral deja de tener desplazamiento vertical propio; en pantallas pequeñas la página puede desplazarse normalmente.
- `X`, `Y`, ancho, fondo y altura quedan bajo **Posición y tamaño (opcional)**, con nombres descriptivos y la aclaración de que son unidades visuales referenciales.
- El contorno libre, las manijas de estiramiento, el giro de edificios y las herramientas de dibujo avanzadas pertenecen a la **fase 2**. La cámara 3D manipulable pertenece a la **fase 3**.

**Nueva comprobación breve:** abrir una oficina vinculada sin figuras y verificar que aparezca la lista automática. Pulsar **Ubicar en este espacio** para un activo, guardarlo, recargar, y comprobar que se ve tanto la figura como la fila de inventario con su responsable. Revisar un nombre largo y pulsar **Ampliar vista** en ambos módulos.

### Fase 2 implementada: prueba de dibujo 2D

1. Reiniciar la aplicación y abrir **Diseñar mapa espacial**. Elegir Las Palmas. Los edificios anteriores se convierten a contornos de cuatro puntos al cargarlos; guardar el plano para persistir la conversión.
2. En **Predio y edificios**, pulsar **Dibujar edificio**, marcar al menos tres puntos sobre el plano y pulsar **Terminar contorno** (o volver a pulsar el primer punto). Asignar nombre. El edificio nace con **PLANTA BAJA**.
3. Abrir el edificio, pulsar **Editar contorno** y arrastrar un punto naranja. Pulsar un círculo blanco  entre dos puntos para crear otro lado. Seleccionar un punto y pulsar **Quitar punto seleccionado**. En el panel, probar **Giro de toda la construcción**, ancho, fondo y altura visual.
4. Abrir la planta baja. Pulsar **Dibujar oficina**, marcar su contorno y terminarlo. Entrar en esa oficina, vincularla a un registro real de la base de datos y volver al piso. Para cambiar la forma, entrar de nuevo y usar **Editar contorno en el piso**.
5. En el piso, usar **Pared**, **Puerta**, **Ventana** y **Escalera**: marcar dos extremos para cada detalle. Eliminarlos desde la lista lateral. Probar **Cuadrícula**, **Ajustar a cuadrícula**, **Zoom**, **Mover plano**, **Capas**, **Deshacer** y **Rehacer**.
6. Opcionalmente, en la vista de predio subir un PNG/JPG de referencia. El servidor conserva un JPG optimizado con máximo 2400 px por lado; la posición, dimensiones, opacidad y giro de la imagen se ajustan desde el panel. La imagen FEXPO se debe tratar únicamente como referencia visual, no como distribución actual del campus.
7. Pulsar **Guardar plano**, recargar ambos módulos y comprobar contornos, detalles, vínculos de oficina y figuras de activos. El explorador muestra esos mismos contornos 2D. La vista isométrica representa la huella irregular y la altura visual de forma aproximada.

**Para el visto bueno de la fase 2:** dibujar y modificar al menos una construcción irregular del campus sin perder una oficina vinculada, guardar, recargar y verificarla en el explorador. La cámara 3D libre, paredes extruidas e interiores con mobiliario quedan en las fases 3 y 4. La imagen de fondo se almacena en `uploads/mapa-espacial` en el servidor; esa carpeta debe persistir en el despliegue.

### Ajustes de la fase 2 tras la prueba de Biblioteca Central

- Los nombres se centran y ajustan al tamaño visible del plano; siguen la orientación del contorno y admiten un ángulo manual. Edificios y espacios tienen color configurable y una paleta rápida.
- En el piso, un clic sobre una oficina la selecciona para moverla, estirarla, girarla o editar sus vértices. El panel muestra posición X/Y, ancho, fondo y acceso a la oficina con sus activos. Los círculos blancos agregan vértices para formar entrantes, pasillos o recortes.
- El encuadre del predio se puede guardar con inicio X/Y, ancho y fondo visibles. El zoom llega al 10 % y también responde a la rueda; el panel derecho tiene desplazamiento vertical propio.
- El diseñador incorpora una vista 3D referencial SVG: arrastrar gira la cámara, la rueda acerca y aleja, y la herramienta «Mover edificio en 3D» desplaza su posición. Al seleccionarlo se pueden cambiar color, giro y altura desde el panel. Es una vista de volumen exterior; la extrusión de interiores y la cámara 3D completa siguen en la fase 3.

**Comprobación propuesta:** dibujar Biblioteca Central con su huella irregular, asignarle un color y orientar el nombre. En planta baja, seleccionar y remodelar dos oficinas y un pasillo desde el plano, entrar en una oficina y ubicar un activo. Alejar la vista del predio, guardar, recargar y repetir el recorrido en el explorador.

### Corrección tras probar el fondo de plano

La forma del edificio se edita en la fase 2. En **Predio y edificios**, un clic sobre la construcción la selecciona sin ocultar la imagen de referencia. La construcción seleccionada se vuelve semitransparente: arrastra sus puntos naranjas para modificar la huella, usa los círculos blancos para añadir lados y selecciona un punto para quitarlo desde el panel. El botón **Abrir pisos y oficinas** continúa al interior. Un edificio nuevo, incluso si comienza rectangular, queda seleccionado de inmediato para poder convertirlo en una huella irregular. Guarda el plano y recárgalo para verificar la forma.

### Ajuste de escala sobre el plano de fondo

Al seleccionar un edificio en **Predio y edificios**, el panel ofrece **Escala proporcional** con reducciones y aumentos rápidos, además de un porcentaje escrito. El cuadrado azul junto al extremo inferior derecho del contorno permite cambiar el tamaño arrastrándolo. La escala conserva la forma, deja fija la esquina superior izquierda del encuadre y transforma también oficinas, activos dibujados y trazos interiores. El **zoom de vista** solo cambia el acercamiento en pantalla, sin alterar los tamaños guardados. Si la reducción dejara una figura demasiado pequeña o fuera de límites, el editor recupera el tamaño anterior. Para alinearlo con el fondo, mueve el edificio arrastrando su interior y ajusta el tamaño con el tirador o los porcentajes; guarda y recarga para verificarlo.

El giro también se puede hacer sin salir del predio: al seleccionar el edificio aparece un círculo verde encima de su contorno. Arrastrarlo rota el edificio sobre su centro; el panel ofrece pasos de 15° y 90° en ambos sentidos y un ángulo numérico. La transformación incluye las oficinas, detalles y activos dibujados. Si una figura quedara fuera de límites, se deshace el giro y se muestra el motivo.

### Fase 3: trazado por puntos y modelo 3D por pisos

- **Crear edificio** y **Crear oficina independiente** inician un trazado sobre la imagen del predio. Cada clic marca un vértice; **Terminar contorno** rellena la figura, permite asignar su nombre y crea la planta baja. Ya no se genera un rectángulo como punto de partida. El ajuste a cuadrícula empieza desactivado para calcar el fondo con precisión y se puede activar si conviene. Los edificios antiguos conservan sus contornos y vínculos.
- En **Diseñar**, el modelo 3D vectorial se descarga al pulsar **Abrir modelo 3D**; en **Explorar**, se abre automáticamente. Extruye el polígono irregular por bandas cuya altura viene de cada piso. La altura total se calcula como suma de las alturas de los niveles; el campo de altura independiente del edificio se retira de la interfaz para evitar dos fuentes de verdad.
- En 3D se puede arrastrar para girar la cámara, usar rueda o botones para acercar, ocultar el techo, seleccionar un piso en su banda o desde la lista, y ver un corte sin los pisos superiores. El administrador modifica la altura de ese piso con el control verde o el campo numérico. Las oficinas del piso seleccionado se resaltan; al pulsarlas se abre el plano 2D de la oficina con el inventario de la base de datos que ya carga automáticamente.
- La vista 3D usa SVG proyectado desde coordenadas tridimensionales y se mantiene ligera sin WebGL ni dependencias externas. Cuando hay más de 35 construcciones, los edificios no seleccionados se simplifican y el seleccionado conserva sus pisos. La vista 2D permanece disponible desde el mismo botón y como recorrido principal para navegadores sin soporte gráfico avanzado.
- La oficina abierta en 2D recibe un lienzo más alto para trabajar sus detalles. El catálogo de mobiliario, el trazado interior más fino y las figuras específicas de activos siguen en la **fase 4**; las figuras de activos y la carga automática del inventario existentes siguen operativas.

### Fase 4 implementada para revisión

- Dentro de un espacio se dibujan cuartos interiores con puntos editables, sin crear una nueva oficina administrativa. Una misma oficina también puede ocupar varios espacios en la planta.
- El panel interior permite agregar mesas, sillas, equipos, estantes, archivadores y otras figuras como mobiliario de referencia. Se arrastran en el plano y se ajustan por ancho, fondo y giro.
- Los activos se eligen del inventario oficial de la oficina. Su figura vectorial puede cambiar de tipo, tamaño, giro y posición; al seleccionarla se consulta su ficha actual (código, descripción, responsable, estado físico, situación y oficina). Si la oficina oficial cambió, se señala la discrepancia y el guardado obliga a reubicar o quitar la figura.
- Las nuevas figuras se incluyen en el guardado validado, en el historial de deshacer y rehacer y en el escalado y giro de la construcción. El plano almacena solo geometría y el ID de cada activo; no modifica su ficha oficial.

**Prueba de aceptación:** abrir una oficina del edificio piloto, trazar dos cuartos y ajustar sus puntos; añadir una mesa de referencia y un activo real, moverlos, cambiar figura y giro, guardar y recargar. Abrir el mismo espacio en el explorador, verificar la ficha del activo y que el mobiliario carece de código inventariable. Si un activo cambia de oficina en la BD, refrescar su ficha y comprobar el aviso. No se ha validado aún el rendimiento en un equipo antiguo real; esa prueba forma parte de la fase 5.

**Ajustes de revisión solicitados:** la vista 3D del explorador dibuja las paredes y cubiertas opacas, con pisos diferenciados por bandas y rótulos, y arranca con una perspectiva que muestra volumen. Se orbita con arrastre izquierdo, se desplaza con arrastre derecho/central o Mayús + arrastre, se acerca con la rueda y se ajusta con flechas. La lista extensa del predio se sustituyó por búsqueda por nombre o código de edificio/oficina vinculada, limitada al predio; seleccionar un resultado enfoca y resalta la construcción. Un edificio de planta baja abre directamente el piso 2D; uno de varios pisos permite elegir el nivel. Las oficinas de su interior solo aparecen en el plano al entrar. En el diseñador se puede vincular una oficina del catálogo filtrado por predio desde el propio piso o desde el espacio; al elegirla se aplican su código y nombre oficiales, con un nombre visible editable.

**Corrección de alineación posterior:** la imagen de referencia sobrescribía las variables del centro de proyección en el renderizador 3D. Las huellas de los edificios y el plano de fondo se proyectaban con centros distintos. Se separaron ambos centros y se agregó una prueba automática que compara un vértice de la huella con la misma coordenada transformada sobre la imagen.
- Tras la primera prueba se redujo la exageración vertical de los pisos y se estabilizó el tamaño de rótulos y del control de altura al acercar la cámara. El encuadre inicial del predio abre al 125 %, y al seleccionar una construcción el 3D centra esa construcción.
- La imagen de referencia se proyecta sobre el suelo del 3D con su posición y giro guardados en 2D. La subida conserva hasta 3200 píxeles en el lado mayor, ajusta con interpolación de calidad y comprime JPG al 91 %. Si el fondo ya se había subido antes de esta corrección, hay que volver a subir el original para recuperar nitidez. Los cambios de ancho o fondo mantienen la proporción de la imagen.
- **Explorar** recibe primero solo huellas, pisos y recuentos; la geometría interior y los datos asociados se consultan cuando se selecciona un edificio. En 3D se muestra el predio y su referencia antes de descargar oficinas o activos de las demás construcciones.
- En la revisión de navegación de la fase 3, el zoom llega al 3200 % con control progresivo y la rueda acerca bajo el cursor. La rueda pulsada o **Espacio + arrastrar** desplaza el plano o la cámara; el encuadre inicial del diseñador se acerca a los edificios dibujados y **Centrar edificios** recupera ese encuadre. El punto naranja arrastrado modifica un vértice; el punto blanco pulsado añade un vértice y arrastrado mueve el lado completo. **Mayús** restringe el arrastre de un punto o lado a un eje.
- La cámara 3D gira durante el arrastre con menor sensibilidad y un marco estable para que el modelo no cambie de escala a cada movimiento. **Vista inicial** restablece orientación y zoom. La barra agrupa edición, vista, cámara y referencia; incluye ayuda visible para gestos y atajos. Con el lienzo enfocado funcionan **Ctrl + Z/Y**, **Ctrl + S**, **+ / −**, **F**, **M**, **V** y **Esc**.

**Prueba de aceptación pendiente:** volver a subir el plano original de Las Palmas y comprobar su proporción y nitidez en 2D y 3D; marcar una huella irregular y cerrarla; asignar nombre, añadir al menos dos pisos y ajustar sus alturas desde 3D. Comprobar zoom bajo el cursor por encima de 800 %, desplazamiento con Espacio o rueda pulsada, arrastre de vértice y lado, restricción de eje con Mayús, deshacer/rehacer y guardado por teclado. Girar la cámara sin saltos, usar los botones de giro fino y **Vista inicial**; ocultar techo, seleccionar un piso y una oficina, comprobar la lista automática de activos, guardar y recargar. En **Explorar mapa espacial**, verificar que abre en 3D y que las oficinas aparecen al seleccionar el edificio. Medir fluidez en el equipo y navegador reales antes del visto bueno de la fase 3.

### Fase 5: publicación e historial, lista para revisión

- **Guardar borrador** conserva el trabajo del editor sin alterar lo que ven los usuarios en **Explorar**. **Publicar** guarda una instantánea de la geometría y del plano de fondo; el explorador y su búsqueda leen exclusivamente esa versión. La consulta inicial trae solo el resumen; las oficinas y los activos se cargan por piso al seleccionarlo, incluso en edificios con varios niveles. Se habilitó compresión HTTP para las respuestas de texto superiores a 2 KB y caché privada por número de publicación para el fondo del explorador.
- El administrador puede publicar. Un usuario al que se concedan los permisos **Diseñar mapa espacial** y **Publicar mapa espacial** también puede hacerlo. La autorización se verifica en el servidor. Los demás editores pueden guardar borradores y recuperar una versión en su borrador, sin cambiar el mapa publicado.
- **Publicación e historial** muestra las versiones y permite **Recuperar en borrador**. Recuperar conserva intacta la versión visible hasta pulsar **Publicar** de nuevo. Las operaciones comprueban la versión de la fila para evitar sobrescribir cambios simultáneos. Se registran usuario y fecha de edición/publicación.
- Los mapas guardados antes de esta fase siguen visibles. Al primer guardado o sustitución de su fondo se crea una copia de esa versión anterior antes de modificar el borrador. Los mapas nuevos empiezan sin publicación.
- La carpeta `uploads/mapa-espacial` ahora incluye `versiones/`; debe mantenerse junto con la base de datos en copias de seguridad y despliegues. Un historial de publicaciones conserva imágenes referenciales optimizadas en esa carpeta.

**Prueba de aceptación de esta entrega:** en Las Palmas, guardar un cambio sin publicar; abrir el explorador con otro usuario y comprobar que la vista y búsqueda siguen mostrando el mapa anterior. Publicar con administrador y comprobar que aparece el cambio. Cambiar otra vez el dibujo y el fondo sin publicar y verificar que el explorador conserva la instantánea. Recuperar una versión anterior en el borrador, comprobar que el explorador aún no cambia y publicarla si corresponde. Confirmar que un usuario sin permiso de publicación no ve el botón ni puede usar la API, y que un usuario habilitado sí puede. Falta medir tiempos y memoria en la red, equipo y navegador concretos de la UAP para cerrar la fase 5.

### Ajustes del estudio tras la primera prueba de publicación

- **Publicar versión** y **Recuperar en borrador** presentan un diálogo propio con el alcance de cada acción. El historial está en la pestaña **Historial** del panel derecho, con contador de versiones y botón de recuperación por fila.
- El explorador abierto se actualiza cuando llega una nueva publicación. El mismo navegador recibe un evento local; otras sesiones conectadas reciben el aviso por el SSE existente del sistema una vez confirmada la transacción. Se vuelve a cargar el predio y cambia el número de versión de la imagen en caché.
- En escritorio, **Diseñar mapa espacial** abre un estudio de pantalla completa. El plano ocupa el centro; las herramientas quedan a la izquierda y las propiedades, elementos, activos, ajustes e historial se separan en pestañas a la derecha. **Salir del estudio** devuelve la vista integrada al módulo. La pantalla pequeña conserva el recorrido vertical.
- Las acciones de dibujar edificio, oficina y cuarto, añadir oficina o pasillo y terminar o cancelar un contorno aparecen sobre el propio plano según el nivel. La lista de construcciones tiene una pestaña y un filtro propios. Las medidas y la presentación avanzadas quedan plegadas bajo sus nombres, accesibles cuando se necesiten.

**Comprobación específica:** con editor y explorador abiertos a la vez, publicar un cambio y observar la actualización del explorador sin recargar la página. En el estudio, crear una oficina en un piso usando la acción sobre el plano; terminar el contorno sin desplazarse por la página, vincular la oficina desde **Editar** y comprobar los activos desde **Activos**. Abrir **Elementos** para filtrar construcciones y **Historial** para recuperar una versión.
