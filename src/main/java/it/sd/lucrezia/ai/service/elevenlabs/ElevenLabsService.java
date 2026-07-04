package it.sd.lucrezia.ai.service.elevenlabs;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.lucrezia.ai.bean.Utente;

@Service
public class ElevenLabsService {

    @Value("${voice.elevenlabs.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String registerInboundCall(String fromNumber, String toNumber, Utente utente, int ticketAperti) throws Exception {

        String url = "https://api.elevenlabs.io/v1/convai/twilio/register-call";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("xi-api-key", apiKey);

        Map<String, Object> dynamicVariables = Map.of(
                "nome", safe(utente.getNome()),
                "condominio", safe(utente.getNomeCondominio()),
                "id_utente", utente.getId(),
                "id_condominio", utente.getIdCondominio(),
                "ticket_aperti", ticketAperti
        );

        Map<String, Object> clientData = Map.of(
                "type", "conversation_initiation_client_data",
                "dynamic_variables", dynamicVariables,
                "user_id", String.valueOf(utente.getId())
        );

        Map<String, Object> body = Map.of(
                "agent_id", utente.getElevenlabsAgentId(),
                "from_number", fromNumber,
                "to_number", toNumber,
                "direction", "inbound",
                "conversation_initiation_client_data", clientData
        );

        HttpEntity<String> request = new HttpEntity<>(
                objectMapper.writeValueAsString(body),
                headers
        );

        ResponseEntity<String> response =
                restTemplate.postForEntity(url, request, String.class);

        return response.getBody();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}