package com.usic.SistemasActivosFijosUAP.model.dto;

import java.util.List;

import lombok.Data;

@Data
public class PerfilDTO {
  // Encabezado
  private String nombreCompleto;
  private String ci;
  private String rol;
  private String usuario;
  private String correo;
  private String nacionalidad;
  private String genero;
  private String avatarIniciales;

  // Métricas
  private Long   activosTotal;
  private String costoTotal; // formateado p.ej. "12.345,67"

  // Listas
  private List<ResponsableDTO>    responsables;
  private List<OficinaDTO>  oficinas;
}
