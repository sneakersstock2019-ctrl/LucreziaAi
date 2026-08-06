package it.sd.lucrezia.ai.service.elevenlabs;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.lucrezia.ai.bean.ElevenLabsSipCallResult;
import it.sd.lucrezia.ai.bean.Utente;

@Service
public class ElevenLabsService {

    @Value("${voice.elevenlabs.api-key}")
    private String apiKey;
    
	@Value("${voice.elevenlabs.outbound-url}")
    private String outboundUrl;
    
    @Value("${voice.elevenlabs.fornitore-agent-id}")
    private String agentId;

    @Value("${voice.elevenlabs.agent-phone-number-id}")
    private String agentPhoneNumberId;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String registerInboundCall(String fromNumber, String toNumber, String callSid, Long idTelefonata, Utente utente, int ticketAperti) throws Exception {

        String url = "https://api.elevenlabs.io/v1/convai/twilio/register-call";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("xi-api-key", apiKey);

        Map<String, Object> dynamicVariables = Map.of(
                "call_sid", safe(callSid),
                "id_telefonata", String.valueOf(idTelefonata),
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
            return "Ciao " + nome + ", sono Lucrezia. Ho visto che hai una segnalazione ancora aperta. Vuoi che ti aggiorni sul suo stato oppure vuoi segnalarmi altro?";
        }

        return "Ciao " + nome + ", sono Lucrezia. Ho visto che hai alcune segnalazioni ancora aperte. Vuoi che ti aggiorni sul loro stato oppure vuoi segnalarmi altro?";
    }
    
    public String getConversations() {

        String url = "https://api.elevenlabs.io/v1/convai/conversations";

        HttpHeaders headers = new HttpHeaders();
        headers.set("xi-api-key", apiKey);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.exchange(url, HttpMethod.GET, request, String.class);

        return response.getBody();
    }
    
    public ElevenLabsSipCallResult avviaChiamata(
            String toNumber,
            String userId,
            Map<String, Object> dynamicVariables) {

        String numeroNormalizzato = normalizePhone(toNumber);

        Map<String, Object> initiationData =
                new LinkedHashMap<>();

        initiationData.put("user_id", userId);
        initiationData.put(
                "dynamic_variables",
                dynamicVariables
        );

        Map<String, Object> payload =
                new LinkedHashMap<>();

        payload.put("agent_id", agentId);
        payload.put(
                "agent_phone_number_id",
                agentPhoneNumberId
        );
        payload.put("to_number", numeroNormalizzato);
        payload.put(
                "conversation_initiation_client_data",
                initiationData
        );

        /*
         * Il timeout di squillo è supportato dalla configurazione
         * telephony_call_config della specifica ElevenLabs.
         */
        payload.put(
                "telephony_call_config",
                Map.of(
                        "ringing_timeout_secs",
                        45
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("xi-api-key", apiKey);

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            outboundUrl,
                            new HttpEntity<>(payload, headers),
                            String.class
                    );

            return parseResponse(response.getBody());

        } catch (HttpStatusCodeException e) {

            throw new RuntimeException(
                    "Errore ElevenLabs HTTP "
                            + e.getStatusCode()
                            + ": "
                            + e.getResponseBodyAsString(),
                    e
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore avvio chiamata SIP ElevenLabs",
                    e
            );
        }
    }

    public String getAgentId() {
        return agentId;
    }

    public String getAgentPhoneNumberId() {
        return agentPhoneNumberId;
    }

    private ElevenLabsSipCallResult parseResponse(
            String responseBody) throws Exception {

        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException(
                    "Risposta ElevenLabs vuota"
            );
        }

        JsonNode root =
                objectMapper.readTree(responseBody);

        ElevenLabsSipCallResult result =
                new ElevenLabsSipCallResult();

        result.setSuccess(
                root.path("success").asBoolean(false)
        );

        result.setMessage(
                root.path("message").asText(null)
        );

        result.setConversationId(
                root.path("conversation_id").asText(null)
        );

        result.setSipCallId(
                root.path("sip_call_id").asText(null)
        );

        if (!result.isSuccess()) {
            throw new IllegalStateException(
                    "ElevenLabs non ha avviato la chiamata: "
                            + result.getMessage()
            );
        }

        return result;
    }

    private String normalizePhone(String phone) {

        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException(
                    "Numero destinatario non configurato"
            );
        }

        String normalized = phone.replaceAll("\\D", "");

        if (normalized.startsWith("00")) {
            normalized = normalized.substring(2);
        }

        if (!normalized.startsWith("39")) {
            normalized = "39" + normalized;
        }

        return "+" + normalized;
    }
}