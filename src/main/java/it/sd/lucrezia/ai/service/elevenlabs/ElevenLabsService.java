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
                "id_utente", String.valueOf(utente.getId()),
                "id_condominio", String.valueOf(utente.getIdCondominio()),
                "nome", safe(utente.getNome()),
                "telefono", safe(fromNumber),
                "condominio", safe(utente.getNomeCondominio()),
                "ticket_aperti", ticketAperti,
                "first_message", buildFirstMessage(utente, ticketAperti)
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
    
    private String buildFirstMessage(Utente utente, int ticketAperti) {

        String nome = safe(utente.getNome());

        if (ticketAperti <= 0) {
            return "Ciao " + nome + ", sono Lucrezia. Come posso aiutarti oggi?";
        }

        if (ticketAperti == 1) {
            return "Ciao " + nome + ", sono Lucrezia. Ho visto che hai una segnalazione ancora aperta. Vuoi che ti aggiorni sul suo stato?";
        }

        return "Ciao " + nome + ", sono Lucrezia. Ho visto che hai alcune segnalazioni ancora aperte. Vuoi che ti aggiorni sul loro stato?";
    }
}