package com.usic.SistemasActivosFijosUAP.legacy;

import java.nio.file.Path;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;

@Controller
@RequestMapping("/legacy/ui")
public class LegacyDbfUiController {
    private final JavaDbfService dbf;

    public LegacyDbfUiController() {
        // baseDir y charset: ajusta si cambias la ruta o el charset
        this.dbf = new JavaDbfService(Path.of("/mnt/dbfwin"), "CP1252");
    }

    
}
