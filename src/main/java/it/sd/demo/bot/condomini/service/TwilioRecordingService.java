package it.sd.demo.bot.condomini.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TwilioRecordingService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    private final RestTemplate restTemplate = new RestTemplate();

    public void startRecording(String callSid) {

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
            body.add("RecordingStatusCallback", "/voice/recording");
            body.add("RecordingStatusCallbackMethod", "POST");
            body.add("Trim", "trim-silence");

            HttpEntity<MultiValueMap<String, String>> request =
                    new HttpEntity<>(body, headers);

            restTemplate.postForEntity(url, request, String.class);

            System.out.println("Registrazione Twilio avviata per CallSid = " + callSid);

        } catch (Exception e) {
            System.out.println("Errore avvio registrazione Twilio");
            e.printStackTrace();
        }
    }
}