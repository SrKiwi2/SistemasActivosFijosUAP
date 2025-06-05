package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
public class AiDescripcionService {
    
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    private static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, String> analizarDescripcion(String descripcion) throws IOException {
        OkHttpClient client = new OkHttpClient();
    
        String prompt = """
        Eres un sistema experto en extraer información estructurada a partir de descripciones variadas de activos fijos, incluso cuando los datos están dispersos, abreviados o implícitos.

        Tu tarea es identificar y extraer los siguientes campos a partir de una descripción de un activo:
        - marca (nombre del fabricante o proveedor, como MSI, HP, LG, etc.)
        - modelo (puede ser alfanumérico, como 3BA4 o DRACO XD)
        - número de serie (identificadores únicos como "N/S: BA4T683600221" o similares)
        - dimensiones (pueden venir en metros, centímetros, pulgadas; por ejemplo: "23.8\"", "1,17*0,59*0,80 M")

        Reglas:
        - Si un dato no está explícito pero puede deducirse (por ejemplo, "MONITOR DE 23.8\"" indica una dimensión de 23.8 pulgadas), dedúcelo.
        - Si un campo no aparece o no se puede deducir, colócalo como "-".
        - Devuelve únicamente un objeto JSON **válido** con esta estructura exacta, sin ningún texto adicional ni explicaciones:

        {
        "marca": "...",
        "modelo": "...",
        "numeroSerie": "...",
        "dimensiones": "..."
        }

        Descripción:
        """ + descripcion;

    
        // Crear estructura para serializar a JSON correctamente
        Map<String, Object> message = Map.of(
            "role", "user",
            "content", prompt
        );
    
        Map<String, Object> requestMap = Map.of(
            "model", "gpt-3.5-turbo",
            "messages", List.of(message)
        );
    
        String requestBody = objectMapper.writeValueAsString(requestMap); // ✅ Genera JSON válido automáticamente
    
        RequestBody body = RequestBody.create(requestBody, JSON);
    
        Request request = new Request.Builder()
                .url(OPENAI_ENDPOINT)
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();
    
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body().string();
                throw new IOException("Error OpenAI: " + errorBody);
            }
    
            String json = response.body().string();
            JsonNode root = objectMapper.readTree(json);
    
            String content = root.path("choices").get(0).path("message").path("content").asText();
    
            if (!content.trim().startsWith("{")) {
                throw new IOException("Respuesta inesperada del modelo, no es JSON puro.");
            }
    
            JsonNode contentNode = objectMapper.readTree(content);
    
            Map<String, String> resultado = new HashMap<>();
            resultado.put("marca", contentNode.path("marca").asText("-"));
            resultado.put("modelo", contentNode.path("modelo").asText("-"));
            resultado.put("numeroSerie", contentNode.path("numeroSerie").asText("-"));
            resultado.put("dimensiones", contentNode.path("dimensiones").asText("-"));
            return resultado;
        }
    }
}