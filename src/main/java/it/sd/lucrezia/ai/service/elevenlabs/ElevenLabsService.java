package it.sd.lucrezia.ai.service.elevenlabs;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ElevenLabsService {

    @Value("${voice.elevenlabs.api-key}")
    private String apiKey;

    @Value("${voice.elevenlabs.agent-id}")
    private String agentId;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String registerTwilioCall(String fromNumber, String toNumber) throws Exception {

        String url = "https://api.elevenlabs.io/v1/convai/twilio/register-call";
        
        System.out.println("apiKey: " + apiKey);
        System.out.println("agentId: " + agentId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("xi-api-key", apiKey);

        Map<String, Object> body = Map.of(
                "agent_id", agentId,
                "from_number", fromNumber,
                "to_number", toNumber,
                "direction", "inbound"
        );

        HttpEntity<String> request = new HttpEntity<>(
                objectMapper.writeValueAsString(body),
                headers
        );

        ResponseEntity<String> response =
                restTemplate.postForEntity(url, request, String.class);

        return response.getBody();
    }
}