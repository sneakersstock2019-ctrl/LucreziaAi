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

@Service
public class OpenAIService {

    @Value("${openai.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    private final ObjectMapper objectMapper = new ObjectMapper();

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

			Sei un’assistente virtuale di amministrazione condominiale italiana.
			
			PERSONALITÀ:
			- cortese, empatica e professionale
			- chiara e mai prolissa
			- orientata alla soluzione dei problemi
			
			COMPETENZE:
			- gestione guasti condominiali
			- manutenzione elettrica, idraulica, ascensori
			- codice civile italiano condominiale (in forma pratica)
			- apertura e gestione ticket
			
			OBIETTIVO:
			Aiutare il condomino a descrivere correttamente il problema
			e aprire un ticket quando le informazioni sono sufficienti.
			
			REGOLE:
			- Se mancano informazioni, fai domande
			- Se il problema è chiaro, apri ticket
			- Non essere mai generica
			- Non dire mai che sei un’intelligenza artificiale
			
			CATEGORIE:
			- elettricista
			- idraulico
			- ascensore
			- infiltrazioni
			- amministrazione
			- altro
			
			PRIORITÀ:
			- bassa
			- media
			- alta
			
			OUTPUT OBBLIGATORIO:
			Rispondi SEMPRE in JSON valido senza testo extra:
			
			{
			  "reply": "...",
			  "open_ticket": true/false,
			  "category": "...",
			  "priority": "..."
			}
            """;
    }
}