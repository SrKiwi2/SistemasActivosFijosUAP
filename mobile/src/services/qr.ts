/**
 * Lectura rápida del contenido de una etiqueta, en el propio teléfono.
 *
 * Es un espejo simplificado de `ParserQrActivoService` del backend, y existe por
 * una sola razón: **latencia**. Apenas ML Kit decodifica, la app puede vibrar y
 * pintar el código en pantalla sin esperar a la red.
 *
 * La versión que MANDA es la del servidor: ahí se compara contra la base de
 * datos y se emite el veredicto. Si el formato de etiqueta cambia, se actualiza
 * el servidor y esta función seguirá sirviendo para lo único que hace —dar una
 * respuesta inmediata—, sin necesidad de redistribuir el APK.
 */

/** Código de activo con prefijo de entidad opcional: `148-01-04-02-03609`. */
const CODIGO = /(?:(\d{2,4})-)?(\d{2})-(\d{2})-(\d{2})-(\d{3,6})/;
const CODIGO_COMPLETO = new RegExp(`^\\s*${CODIGO.source}\\s*$`);

export interface LecturaQr {
  crudo: string;
  legible: boolean;
  /** Código sin prefijo, como está en la BD. */
  codigo: string | null;
  /** Código con prefijo, como está impreso. */
  codigoVisual: string | null;
  prefijoEntidad: string | null;
  /** Textos de la etiqueta (pueden estar desactualizados). */
  siglaEntidad: string | null;
  municipio: string | null;
  predio: string | null;
  grupoContable: string | null;
  descripcion: string | null;
}

const VACIA: Omit<LecturaQr, 'crudo'> = {
  legible: false,
  codigo: null,
  codigoVisual: null,
  prefijoEntidad: null,
  siglaEntidad: null,
  municipio: null,
  predio: null,
  grupoContable: null,
  descripcion: null,
};

export function leerEtiqueta(crudo: string): LecturaQr {
  if (!crudo?.trim()) return { crudo, ...VACIA };

  const texto = crudo.trim();
  const campos = texto.includes('|') ? texto.split('|').map((c) => c.trim()) : [];

  // Un campo que sea íntegramente un código es una señal mucho más fiable que
  // rebuscar en todo el texto, donde las medidas de la descripción
  // («D:0,60*0,46*0,69») podrían confundir.
  let m: RegExpMatchArray | null = null;
  for (const campo of campos) {
    const candidato = campo.match(CODIGO_COMPLETO);
    if (candidato) {
      m = candidato;
      break;
    }
  }
  if (!m) m = texto.match(CODIGO);

  const base = {
    crudo,
    siglaEntidad: campos[0] || null,
    municipio: campos[1] || null,
    predio: campos[2] || null,
    grupoContable: campos[3] || null,
    descripcion: campos[5] || null,
  };

  if (!m) return { ...base, ...VACIA, crudo };

  const [, prefijo, mun, pred, grupo, correlativo] = m;
  const codigo = `${mun}-${pred}-${grupo}-${correlativo}`;

  return {
    ...base,
    legible: true,
    codigo,
    codigoVisual: prefijo ? `${prefijo}-${codigo}` : codigo,
    prefijoEntidad: prefijo ?? null,
  };
}

/**
 * Da formato a un código tecleado mientras se escribe: inserta los guiones por
 * posición para que se vea igual que en la etiqueta.
 */
export function formatearCodigoManual(texto: string): string {
  const digitos = texto.replace(/\D/g, '');
  if (!digitos) return '';

  // Con 12 o más dígitos hay prefijo de entidad; los 11 últimos son el código.
  const conPrefijo = digitos.length > 11;
  const prefijo = conPrefijo ? digitos.slice(0, digitos.length - 11) : '';
  const cuerpo = conPrefijo ? digitos.slice(-11) : digitos;

  const partes: string[] = [];
  if (prefijo) partes.push(prefijo);
  if (cuerpo.length > 0) partes.push(cuerpo.slice(0, 2));
  if (cuerpo.length > 2) partes.push(cuerpo.slice(2, 4));
  if (cuerpo.length > 4) partes.push(cuerpo.slice(4, 6));
  if (cuerpo.length > 6) partes.push(cuerpo.slice(6, 11));

  return partes.join('-');
}

/** Separa el código para pintarlo: prefijo atenuado + correlativo destacado. */
export function partirCodigoVisual(codigoVisual: string | null | undefined) {
  if (!codigoVisual) return { prefijo: '', medio: '', correlativo: '' };

  const partes = codigoVisual.split('-');
  if (partes.length === 5) {
    return {
      prefijo: partes[0] + '-',
      medio: partes.slice(1, 4).join('-') + '-',
      correlativo: partes[4],
    };
  }
  if (partes.length === 4) {
    return { prefijo: '', medio: partes.slice(0, 3).join('-') + '-', correlativo: partes[3] };
  }
  return { prefijo: '', medio: codigoVisual, correlativo: '' };
}
