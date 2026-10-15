package com.usic.SistemasActivosFijosUAP.model.service.donacion;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itextpdf.text.BaseColor;
import com.usic.SistemasActivosFijosUAP.model.dao.IDonacionActaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IDonacionBienDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.DonacionActa;
import com.usic.SistemasActivosFijosUAP.model.entity.DonacionBien;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.service.PdfEtiquetasQrService;

import lombok.RequiredArgsConstructor;

/**
 * Etiquetas con QR de los bienes de un acta de donación (hoja de {@link PdfEtiquetasQrService}).
 * Cada etiqueta dice además «DONACIÓN» para distinguirla a simple vista.
 */
@Service
@RequiredArgsConstructor
public class PdfEtiquetasDonacionService {

    private static final BaseColor VIOLETA = new BaseColor(124, 58, 237);

    private final IDonacionActaDao actaDao;
    private final IDonacionBienDao bienDao;
    private final PdfEtiquetasQrService hoja;

    @Transactional(readOnly = true)
    public byte[] generar(Long idDonacion) {
        DonacionActa acta = actaDao.buscarConDestino(idDonacion)
                .orElseThrow(() -> new IllegalArgumentException("La donación no existe."));
        if (DonacionActa.ANULADA.equals(acta.getEstado())) {
            throw new IllegalArgumentException("La donación " + acta.getNumero() + " está anulada: no se imprimen etiquetas.");
        }
        List<DonacionBien> bienes = bienDao.listarDeActa(idDonacion).stream()
                .filter(b -> Activo.ESTADO_ACTIVO.equals(b.getActivo().getEstado()) && b.getActivo().getCodigo() != null)
                .toList();
        if (bienes.isEmpty()) throw new IllegalArgumentException("La donación no tiene bienes vigentes para etiquetar.");

        Predio predio = acta.getOficina() != null ? acta.getOficina().getPredio() : null;
        List<PdfEtiquetasQrService.Etiqueta> etiquetas = bienes.stream()
                .map(b -> PdfEtiquetasQrService.deActivo(b.getActivo(), predio, "DONACIÓN", acta.getNumero()))
                .toList();
        return hoja.generar("Etiquetas " + acta.getNumero(), etiquetas, VIOLETA);
    }
}
