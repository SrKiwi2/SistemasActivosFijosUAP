package com.usic.SistemasActivosFijosUAP.model.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.dao.IDonacionActaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IReservaCorrelativoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.ReservaCorrelativo;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesActivoRepo;

import lombok.RequiredArgsConstructor;

/** Rangos visibles durante la captura. Usa el mismo turno que el guardado de cada módulo. */
@Service
@RequiredArgsConstructor
public class ReservaCorrelativoService {
    public static final String REGISTRO = "R";
    public static final String DONACION = "D";
    public record Lote(String prefijo, int cantidad) {}

    private static final long TURNO_DONACIONES = 0x5C1AFD0A1L;
    private static final int MAX_CORRELATIVO = 99_999;
    private final IReservaCorrelativoDao dao;
    private final IActivoDao activoDao;
    private final FuncionesActivoRepo funciones;
    private final IDonacionActaDao actaDao;

    private void turno(String tipo) {
        if (REGISTRO.equals(tipo)) funciones.tomarTurnoRegistro();
        else if (DONACION.equals(tipo)) actaDao.turno(TURNO_DONACIONES);
        else throw new IllegalArgumentException("Serie de correlativos inválida.");
    }

    private static void validarToken(String token) {
        if (token == null || token.isBlank() || token.length() > 40 || !token.matches("[A-Za-z0-9-]+"))
            throw new IllegalArgumentException("Marca de reserva inválida.");
    }

    private int siguienteConfirmado(String tipo, String prefijo) {
        if (DONACION.equals(tipo)) return actaDao.maxCorrelativoDonacion(prefijo) + 1;
        String[] partes = prefijo.split("-", -1);
        if (partes.length != 3) throw new IllegalArgumentException("Prefijo contable inválido.");
        String codigo = funciones.previewCodigoPorCodes(partes[0], partes[1], partes[2]);
        if (codigo == null || !codigo.matches(".*-[0-9]{5}"))
            throw new IllegalStateException("La base no pudo calcular el próximo código de activos.");
        return Integer.parseInt(codigo.substring(codigo.lastIndexOf('-') + 1));
    }

    /**
     * Al recargar (F5) una renovación tardía de la página vieja podía volver a crear su reserva
     * después de liberarla: quedaba huérfana 30 min reteniendo los códigos. La carga nueva de la
     * misma pestaña descarta lo que no sigue usando y la página reemplazada ya no reserva.
     */
    private void controlarPagina(String tipo, String token, Usuario usuario, ClienteReserva cliente) {
        cliente.exigirVersion();
        if (!cliente.conPagina()) return;
        if (dao.existsByTipoAndPaginaAndUsuarioAndCargaGreaterThan(tipo, cliente.pagina(), usuario.getUsuario(), cliente.carga()))
            throw new ClienteReserva.ReservaObsoletaException();
        dao.eliminarDeCargasAnteriores(tipo, cliente.pagina(), usuario.getUsuario(), cliente.vigentesCon(token), cliente.carga());
    }

    private static void marcarPagina(ReservaCorrelativo r, ClienteReserva cliente) {
        if (cliente.conPagina()) { r.setPagina(cliente.pagina()); r.setCarga(cliente.carga()); }
    }

    public List<ReservaCorrelativo> reservar(String tipo, String token, List<Lote> lotes, Usuario usuario) {
        return reservar(tipo, token, lotes, usuario, ClienteReserva.INTERNO);
    }

    @Transactional
    public List<ReservaCorrelativo> reservar(String tipo, String token, List<Lote> lotes, Usuario usuario, ClienteReserva cliente) {
        validarToken(token);
        if (usuario == null || usuario.getUsuario() == null) throw new SecurityException("Inicie sesión otra vez.");
        if (lotes == null || lotes.isEmpty() || lotes.size() > 100)
            throw new IllegalArgumentException("Indique entre 1 y 100 lotes.");
        for (Lote lote : lotes) {
            if (lote.prefijo() == null || !lote.prefijo().matches("[^-]+-[^-]+-[0-9]{2,3}")
                    || lote.cantidad() < 1 || lote.cantidad() > 500)
                throw new IllegalArgumentException("El predio, grupo o cantidad de un lote no es válido.");
        }
        turno(tipo);
        LocalDateTime ahora = LocalDateTime.now();
        dao.eliminarVencidas(ahora);
        controlarPagina(tipo, token, usuario, cliente);
        List<ReservaCorrelativo> previas = dao.findByTipoAndTokenOrderByIndiceAsc(tipo, token);
        if (previas.stream().anyMatch(r -> !usuario.getUsuario().equals(r.getUsuario())))
            throw new SecurityException("La reserva pertenece a otro usuario.");
        boolean iguales = previas.size() == lotes.size();
        for (int i = 0; iguales && i < lotes.size(); i++) {
            ReservaCorrelativo r = previas.get(i); Lote l = lotes.get(i);
            iguales = r.getIndice() == i && r.getVence().isAfter(ahora)
                    && r.getPrefijo().equals(l.prefijo()) && r.getCantidad() == l.cantidad();
        }
        if (iguales) {
            previas.forEach(r -> { r.setVence(ahora.plusMinutes(30)); marcarPagina(r, cliente); });
            return dao.saveAll(previas);
        }
        if (!previas.isEmpty()) { dao.deleteAll(previas); dao.flush(); }

        Map<String, List<int[]>> ocupados = new HashMap<>();
        List<ReservaCorrelativo> nuevas = new ArrayList<>();
        for (int i = 0; i < lotes.size(); i++) {
            Lote lote = lotes.get(i);
            List<int[]> rangos = ocupados.computeIfAbsent(lote.prefijo(), p -> {
                List<int[]> existentes = new ArrayList<>();
                dao.findByTipoAndPrefijoAndVenceAfterOrderByDesdeAsc(tipo, p, ahora)
                        .forEach(r -> existentes.add(new int[] { r.getDesde(), r.getDesde() + r.getCantidad() - 1 }));
                return existentes;
            });
            int desde = siguienteConfirmado(tipo, lote.prefijo());
            rangos.sort(Comparator.comparingInt(r -> r[0]));
            for (int[] rango : rangos) {
                if (desde + lote.cantidad() - 1 < rango[0]) break;
                desde = Math.max(desde, rango[1] + 1);
            }
            if (desde + lote.cantidad() - 1 > MAX_CORRELATIVO)
                throw new IllegalStateException("La serie " + lote.prefijo() + " llegó al correlativo 99999.");
            rangos.add(new int[] { desde, desde + lote.cantidad() - 1 });
            ReservaCorrelativo r = new ReservaCorrelativo();
            r.setId(tipo + ":" + token + ":" + i); r.setToken(token); r.setIndice(i);
            r.setTipo(tipo); r.setPrefijo(lote.prefijo()); r.setDesde(desde);
            r.setCantidad(lote.cantidad()); r.setUsuario(usuario.getUsuario());
            r.setVence(ahora.plusMinutes(30)); marcarPagina(r, cliente); nuevas.add(r);
        }
        return dao.saveAllAndFlush(nuevas);
    }

    public ReservaCorrelativo reservarCodigoLibre(String token, String prefijo, int numero, Usuario usuario) {
        return reservarCodigoLibre(token, prefijo, numero, usuario, ClienteReserva.INTERNO);
    }

    @Transactional
    public ReservaCorrelativo reservarCodigoLibre(String token, String prefijo, int numero, Usuario usuario, ClienteReserva cliente) {
        validarToken(token);
        if (usuario == null || usuario.getUsuario() == null) throw new SecurityException("Inicie sesión otra vez.");
        if (prefijo == null || !prefijo.matches("[^-]+-[^-]+-[0-9]{2,3}") || numero < 1 || numero > MAX_CORRELATIVO)
            throw new IllegalArgumentException("Código libre inválido.");
        turno(REGISTRO);
        controlarPagina(REGISTRO, token, usuario, cliente);
        List<ReservaCorrelativo> previas = dao.findByTipoAndTokenOrderByIndiceAsc(REGISTRO, token);
        if (previas.stream().anyMatch(r -> !usuario.getUsuario().equals(r.getUsuario())))
            throw new SecurityException("La reserva pertenece a otro usuario.");
        if (previas.size() == 1 && previas.get(0).getPrefijo().equals(prefijo)
                && previas.get(0).getDesde() == numero && previas.get(0).getCantidad() == 1
                && previas.get(0).getVence().isAfter(LocalDateTime.now())) {
            previas.get(0).setVence(LocalDateTime.now().plusMinutes(30));
            marcarPagina(previas.get(0), cliente);
            return dao.save(previas.get(0));
        }
        if (!previas.isEmpty()) { dao.deleteAll(previas); dao.flush(); }
        String codigo = prefijo + "-" + String.format("%05d", numero);
        if (activoDao.findByCodigo(codigo).isPresent()
                || dao.contarOcupando(REGISTRO, prefijo, numero, LocalDateTime.now()) > 0)
            throw new IllegalStateException("El código " + codigo + " ya fue tomado o reservado. Elija otro.");
        ReservaCorrelativo r = new ReservaCorrelativo();
        r.setId(REGISTRO + ":" + token + ":0"); r.setToken(token); r.setIndice(0);
        r.setTipo(REGISTRO); r.setPrefijo(prefijo); r.setDesde(numero); r.setCantidad(1);
        r.setUsuario(usuario.getUsuario()); r.setVence(LocalDateTime.now().plusMinutes(30));
        marcarPagina(r, cliente);
        return dao.saveAndFlush(r);
    }

    /** El guardado ya tomó el turno del módulo. Si la pantalla reservó, debe respetar ese rango. */
    public List<ReservaCorrelativo> validarParaGuardar(String tipo, String token, List<Lote> lotes, Usuario usuario) {
        validarToken(token);
        List<ReservaCorrelativo> reservas = dao.findByTipoAndTokenOrderByIndiceAsc(tipo, token);
        if (reservas.size() != lotes.size()) throw new IllegalStateException("La reserva de códigos venció. Revise los códigos antes de guardar.");
        for (int i = 0; i < lotes.size(); i++) {
            ReservaCorrelativo r = reservas.get(i); Lote l = lotes.get(i);
            if (!r.getUsuario().equals(usuario.getUsuario()) || r.getIndice() != i
                    || !r.getVence().isAfter(LocalDateTime.now())
                    || !r.getPrefijo().equals(l.prefijo()) || r.getCantidad() != l.cantidad())
                throw new IllegalStateException("La reserva de códigos cambió. Revise los códigos antes de guardar.");
        }
        return reservas;
    }

    public int siguienteSinReserva(String tipo, String prefijo) {
        return Math.max(siguienteConfirmado(tipo, prefijo),
                dao.maxReservado(tipo, prefijo, LocalDateTime.now()) + 1);
    }

    public boolean estaReservado(String tipo, String prefijo, int numero) {
        return dao.contarOcupando(tipo, prefijo, numero, LocalDateTime.now()) > 0;
    }

    public void consumir(List<ReservaCorrelativo> reservas) {
        if (reservas != null && !reservas.isEmpty()) dao.deleteAll(reservas);
    }

    @Transactional
    public void liberar(String tipo, String token, Usuario usuario) {
        validarToken(token); turno(tipo);
        List<ReservaCorrelativo> reservas = dao.findByTipoAndTokenOrderByIndiceAsc(tipo, token);
        if (reservas.stream().anyMatch(r -> !r.getUsuario().equals(usuario.getUsuario())))
            throw new SecurityException("La reserva pertenece a otro usuario.");
        dao.deleteAll(reservas);
    }
}
