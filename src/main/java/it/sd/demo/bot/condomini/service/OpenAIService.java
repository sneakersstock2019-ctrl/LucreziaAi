package it.sd.demo.bot.condomini.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.demo.bot.condomini.bean.AIResponse;
import it.sd.demo.bot.condomini.bean.OpenAIRequest;
import it.sd.demo.bot.condomini.bean.OpenAIRequestMessage;
import it.sd.demo.bot.condomini.bean.OpenAIResponse;
import it.sd.demo.bot.condomini.bean.UserSession;
import jakarta.annotation.PostConstruct;

@Service
public class OpenAIService {

    @Value("${openai.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    public void debug() {
        System.out.println("OPENAI KEY = " + apiKey);
    }

    public AIResponse askLucrezia(String messaggioUtente, UserSession session) {
    	List<OpenAIRequestMessage> messaggiOpenAIRequestMessage = null;
    	OpenAIRequest openAIRequest = null;
    	HttpHeaders httpHeaders = null;
    	HttpEntity<OpenAIRequest> httpEntity = null;
    	ResponseEntity<OpenAIResponse> response = null;
    	String systemPrompt = null, responseString = null;
    	
        try {
            messaggiOpenAIRequestMessage = new ArrayList<>();
            
            systemPrompt = buildSystemPrompt();
            messaggiOpenAIRequestMessage.add(new OpenAIRequestMessage(
                    "system",
                    systemPrompt
            ));

            for (String msg : session.cronologiaMessaggi) {
                messaggiOpenAIRequestMessage.add(
                        new OpenAIRequestMessage(
                                "user",
                                msg
                        )
                );
            }

            messaggiOpenAIRequestMessage.add(
                    new OpenAIRequestMessage(
                            "user",
                            messaggioUtente
                    )
            );

            openAIRequest = new OpenAIRequest();
            openAIRequest.setModel("gpt-4.1-mini");
            openAIRequest.setMessages(messaggiOpenAIRequestMessage);
            openAIRequest.setTemperature(0.3);

            httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            httpHeaders.setBearerAuth(apiKey);

            httpEntity = new HttpEntity<>(openAIRequest, httpHeaders);

            System.out.println("Invoco Api OpenAI Messages (POST): https://api.openai.com/v1/chat/completions");
            System.out.println("Headers: " + httpHeaders);
            System.out.println("Payload: " + openAIRequest);
            response = restTemplate.postForEntity(
                            "https://api.openai.com/v1/chat/completions",
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

            return objectMapper.readValue(responseString, AIResponse.class);

        } catch (Exception e) {
            e.printStackTrace();

            AIResponse error = new AIResponse();
            error.setReply(
                    "Mi dispiace, al momento non riesco a elaborare la richiesta."
            );

            return error;
        }
    }

    private String buildSystemPrompt() {

        return """
            Ti chiami Lucrezia.

	        Sei l'assistente virtuale del condominio.
	        Devi presentarti sempre in modo gentile come Lucrezia, assistente virtuale.
	
	        Sei competente in:
	        - amministrazione condominiale italiana
	        - gestione segnalazioni condominiali
	        - manutenzione elettrica, idraulica, ascensori, infiltrazioni
	        - codice civile italiano in materia condominiale
	
	        TONO:
	        - gentile
	        - disponibile
	        - professionale
	        - rassicurante
	        - sintetico
	
	        OBIETTIVO:
	        aiutare il condomino a descrivere il problema e aprire una segnalazione.
	
	        REGOLE:
	        - Se il problema è chiaro, apri subito il ticket.
	        - Se mancano informazioni essenziali, fai UNA sola domanda mirata.
	        - Non continuare a fare troppe domande.
	        - Se la segnalazione non è chiara, usa categoria "generico".
	        - Non dire mai che sei una intelligenza artificiale.
	        - Non inventare numeri ticket o link.
	        - Il link ticket viene aggiunto dal sistema Java.
	        - Non dare consulenze legali definitive; usa formule come "in linea generale".
	
	        CATEGORIE:
	        - elettricista
	        - idraulico
	        - ascensore
	        - infiltrazioni
	        - amministrazione
	        - generico
	
	        PRIORITÀ:
	        - bassa
	        - media
	        - alta
	
	        OUTPUT OBBLIGATORIO:
	        Rispondi sempre e solo in JSON valido, senza testo fuori dal JSON.
	
	        Formato:
	        {
	          "reply": "...",
	          "open_ticket": true,
	          "category": "...",
	          "priority": "..."
	        }
	
	        oppure:
	
	        {
	          "reply": "...",
	          "open_ticket": false,
	          "category": "...",
	          "priority": "..."
	        }
            """;
    }
}