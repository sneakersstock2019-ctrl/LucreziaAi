package it.sd.lucrezia.ai.service.openai;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.lucrezia.ai.bean.OpenAIRequest;
import it.sd.lucrezia.ai.bean.OpenAIRequestMessage;
import it.sd.lucrezia.ai.bean.OpenAIResponse;
import it.sd.lucrezia.ai.bean.WhatsAppAiResponse;
import it.sd.lucrezia.ai.bean.WhatsAppFornitoreAiResponse;

@Service
public class OpenAIService {

    @Value("${openai.api-key}")
    private String apiKey;
    
    private static final String OPENAI_MODEL= "gpt-4.1-mini";
    private static final String OPENAI_API= "https://api.openai.com/v1/chat/completions";
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public WhatsAppAiResponse askLucrezia(List<OpenAIRequestMessage> messaggiOpenAIRequestMessage) {
    	OpenAIRequest openAIRequest = null;
    	HttpHeaders httpHeaders = null;
    	HttpEntity<OpenAIRequest> httpEntity = null;
    	ResponseEntity<OpenAIResponse> response = null;
    	String responseString = null;
    	
        try {
            openAIRequest = new OpenAIRequest();
            openAIRequest.setModel(OPENAI_MODEL);
            openAIRequest.setMessages(messaggiOpenAIRequestMessage);
            openAIRequest.setTemperature(0.8);

            httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            httpHeaders.setBearerAuth(apiKey);

            httpEntity = new HttpEntity<>(openAIRequest, httpHeaders);

            System.out.println("Invoco Api OpenAI Messages (POST): " + OPENAI_API);
            response = restTemplate.postForEntity(
            				OPENAI_API,
                            httpEntity,
                            OpenAIResponse.class
                    );

            responseString = response.getBody()
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();

            System.out.println("Response Api OpenAI:");
            System.out.println(responseString);

            return objectMapper.readValue(responseString, WhatsAppAiResponse.class);

        } catch (Exception e) {
            e.printStackTrace();

            WhatsAppAiResponse error = new WhatsAppAiResponse();
            error.setReply(
                    "Mi dispiace, al momento non riesco a elaborare la richiesta."
            );

            return error;
        }
    }
    
    public WhatsAppFornitoreAiResponse askLucreziaFornitore(
            List<OpenAIRequestMessage> messaggiOpenAIRequestMessage) {

        OpenAIRequest openAIRequest = null;
        HttpHeaders httpHeaders = null;
        HttpEntity<OpenAIRequest> httpEntity = null;
        ResponseEntity<OpenAIResponse> response = null;
        String responseString = null;

        try {

            openAIRequest = new OpenAIRequest();
            openAIRequest.setModel(OPENAI_MODEL);
            openAIRequest.setMessages(messaggiOpenAIRequestMessage);

            // Qui vogliamo interpretazione precisa, non creatività
            openAIRequest.setTemperature(0.1);

            httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            httpHeaders.setBearerAuth(apiKey);

            httpEntity = new HttpEntity<>(
                    openAIRequest,
                    httpHeaders
            );

            System.out.println(
                    "Invoco Api OpenAI Fornitore (POST): "
                            + OPENAI_API
            );

            response = restTemplate.postForEntity(
                    OPENAI_API,
                    httpEntity,
                    OpenAIResponse.class
            );

            if (response.getBody() == null
                    || response.getBody().getChoices() == null
                    || response.getBody().getChoices().isEmpty()) {

                throw new IllegalStateException(
                        "Risposta OpenAI vuota"
                );
            }

            responseString = response.getBody()
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();

            System.out.println("Response Api OpenAI Fornitore:");
            System.out.println(responseString);

            /*
             * Nel caso il modello restituisca:
             *
             * ```json
             * {...}
             * ```
             *
             * ripuliamo i delimitatori markdown.
             */
            responseString =
                    cleanJsonResponse(responseString);

            return objectMapper.readValue(
                    responseString,
                    WhatsAppFornitoreAiResponse.class
            );

        } catch (Exception e) {

            e.printStackTrace();

            WhatsAppFornitoreAiResponse error =
                    new WhatsAppFornitoreAiResponse();

            error.setAction("UNCLEAR");
            error.setComplete(false);
            error.setReply(
                    "Non sono riuscita a interpretare la risposta."
            );

            return error;
        }
    }
    
    private String cleanJsonResponse(String response) {

        if (response == null) {
            return "";
        }

        response = response.trim();

        if (response.startsWith("```json")) {
            response = response.substring(7);
        } else if (response.startsWith("```")) {
            response = response.substring(3);
        }

        if (response.endsWith("```")) {
            response = response.substring(
                    0,
                    response.length() - 3
            );
        }

        return response.trim();
    }
}