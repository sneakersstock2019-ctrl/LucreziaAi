package it.sd.demo.bot.condomini.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.demo.bot.condomini.bean.VoiceContext;
import it.sd.demo.bot.condomini.dao.TicketConversazioneDao;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TwilioRecordingService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    private final TicketConversazioneDao ticketConversazioneDao;
    
    private final RestTemplate restTemplate = new RestTemplate();

    public String startRecording(String callSid) {

        try {
            String url = "https://api.twilio.com/2010-04-01/Accounts/"
                    + accountSid
                    + "/Calls/"
                    + callSid
                    + "/Recordings.json";

            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(accountSid, authToken);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("RecordingStatusCallback", "/voice/recording-realtime");
            body.add("RecordingStatusCallbackMethod", "POST");
            body.add("Trim", "trim-silence");

            HttpEntity<MultiValueMap<String, String>> request =
                    new HttpEntity<>(body, headers);

            restTemplate.postForEntity(url, request, String.class);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, request, String.class);

            JsonNode root = new ObjectMapper().readTree(response.getBody());
            String recordingSid = root.path("sid").asText();

            System.out.println("Registrazione Twilio avviata per CallSid = " + callSid);
            System.out.println("RecordingSid = " + recordingSid);

            return recordingSid;

        } catch (Exception e) {
            System.out.println("Errore avvio registrazione Twilio");
            e.printStackTrace();
            return null;
        }
    }
    
    public void tryAttachRecordingToTicket(VoiceContext context) {

        if (context == null ||
            context.getIdTicketCreato() == null ||
            context.getRecordingSid() == null) {
            return;
        }

        String audioUrl =
                "https://api.twilio.com/2010-04-01/Accounts/"
                + accountSid
                + "/Recordings/"
                + context.getRecordingSid()
                + ".mp3";

        ticketConversazioneDao.updateAudioUrlByTicket(
                context.getIdTicketCreato(),
                audioUrl
        );

        System.out.println("Audio registrazione associato al ticket "
                + context.getIdTicketCreato()
                + " = " + audioUrl);
    }
    
    public String buildRecordingMp3Url(String recordingSid) {

        if (recordingSid == null || recordingSid.isBlank()) {
            return null;
        }

        String baseUrl = "https://api.twilio.com/2010-04-01/Accounts/%s/Recordings/%s";

        return String.format(baseUrl, accountSid, recordingSid) + ".mp3";
    }
}