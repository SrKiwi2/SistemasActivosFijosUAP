package com.usic.SistemasActivosFijosUAP.model.dto;

public class RespOption {
    private Long id;
    private String text;

    public RespOption(Long id, String text) { this.id = id; this.text = text; }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }
}