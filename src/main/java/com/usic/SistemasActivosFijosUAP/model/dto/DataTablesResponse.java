package com.usic.SistemasActivosFijosUAP.model.dto;

import java.util.List;

import org.apache.poi.ss.formula.functions.T;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class DataTablesResponse<T> {
    private long recordsTotal;
    private long recordsFiltered;
    private List<T> data;
}
