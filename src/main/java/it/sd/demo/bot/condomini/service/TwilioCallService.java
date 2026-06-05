package it.sd.demo.bot.condomini.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class TwilioCallService {

    @Value("${TWILIO_ACCOUNT_SID}")
    private String accountSid;

    @Value("${TWILIO_AUTH_TOKEN}")
    private String authToken;
    
    @Value("${TWILIO_FROM_NUMBER}")
    private String fromNumber;

    private final RestTemplate restTemplate = new RestTemplate();

    public void notifyTicketCreated(String toNumber) {

        String message = """
                Buongiorno, sono Lucrezia.
                È stato creato un nuovo ticket per il condominio Viale Europa.
                La segnalazione riguarda un problema elettrico sulle scale condominiali, la priorità assegnata è media.
                Ti preghiamo di accedere alla Dashboard di Lucrezia per gestirlo. 
                Grazie.
                """;

        String twiml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                    <Say language="it-IT" voice="Polly.Bianca-Neural">
                        %s
                    </Say>
                </Response>
                """.formatted(escapeXml(message));

        String url = "https://api.twilio.com/2010-04-01/Accounts/"
                + accountSid
                + "/Calls.json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String auth = accountSid + ":" + authToken;
        String encodedAuth = Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        headers.set("Authorization", "Basic " + encodedAuth);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("To", toNumber);
        body.add("From", fromNumber);
        body.add("Twiml", twiml);

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(body, headers);

        System.out.println("Invio chiamata Twilio verso: " + toNumber);

        ResponseEntity<String> response =
                restTemplate.postForEntity(url, request, String.class);

        System.out.println("Response Twilio:");
        System.out.println(response.getBody());
    }

    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}