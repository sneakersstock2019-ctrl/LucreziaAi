package it.sd.demo.bot.condomini.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
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

import it.sd.demo.bot.condomini.bean.AIResponse;
import it.sd.demo.bot.condomini.bean.ChatMessage;
import it.sd.demo.bot.condomini.bean.OpenAIRequest;
import it.sd.demo.bot.condomini.bean.OpenAIRequestMessage;
import it.sd.demo.bot.condomini.bean.OpenAIResponse;
import it.sd.demo.bot.condomini.bean.UserSession;
import it.sd.demo.bot.condomini.bean.Utente;

@Service
public class OpenAIService {

    @Value("${openai.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public AIResponse askLucrezia(String messaggioUtente, UserSession session, Utente utente, String contestoCondominio) {
    	List<OpenAIRequestMessage> messaggiOpenAIRequestMessage = null;
    	OpenAIRequest openAIRequest = null;
    	HttpHeaders httpHeaders = null;
    	HttpEntity<OpenAIRequest> httpEntity = null;
    	ResponseEntity<OpenAIResponse> response = null;
    	String systemPrompt = null, responseString = null;
    	
        try {
            messaggiOpenAIRequestMessage = new ArrayList<>();
            
            systemPrompt = buildSystemPrompt(session, utente, contestoCondominio);
            System.out.println("systemPrompt: " + systemPrompt);
            messaggiOpenAIRequestMessage.add(new OpenAIRequestMessage(
                    "system",
                    systemPrompt
            ));

            for (ChatMessage chatMessage : session.cronologiaMessaggi) {

                messaggiOpenAIRequestMessage.add(
                        new OpenAIRequestMessage(
                                chatMessage.getRole(),
                                chatMessage.getContent()
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
            openAIRequest.setTemperature(0.8);

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

    private String buildSystemPrompt(UserSession session, Utente utente, String contestoCondominio) {

        String nome = utente != null ? utente.getNome() : session.nome;
        String condominio = utente != null ? utente.getNomeCondominio() : "non disponibile";

        return """
            Ti chiami Lucrezia.

            Sei l'assistente virtuale del condominio.

            CONTESTO UTENTE:
            - Nome condomino: %s
            - Condominio: %s
            - Primo messaggio conversazione: %s

            CONTESTO SPECIFICO DEL CONDOMINIO:
            %s

            STILE:
            - gentile
            - naturale
            - professionale
            - sintetico
            - non ripetitivo
            - vicino a una conversazione umana

            REGOLE DI SALUTO:
            - Saluta usando il nome del condomino solo al primo messaggio.
            - Nei messaggi successivi non iniziare con "Ciao", "Buongiorno" o "Sono Lucrezia".
            - Rispondi direttamente alla domanda o chiedi il dettaglio mancante.
            - Non ripetere sempre "sono Lucrezia".
            - Dopo il primo messaggio rispondi in modo naturale e contestuale.
            - Varia le risposte, evitando formule sempre uguali.

            OBIETTIVO:
            aiutare il condomino a descrivere un problema condominiale e aprire un ticket solo quando è corretto farlo.

            REGOLA FONDAMENTALE:
            Apri un ticket solo se il problema riguarda parti comuni condominiali.

            PARTI COMUNI TIPICHE:
            - scale
            - androne
            - ascensore
            - cortile
            - tetto
            - facciata
            - portone
            - cancello
            - citofono condominiale
            - illuminazione scale o aree comuni
            - impianti comuni
            - infiltrazioni provenienti da parti comuni

            AREE PRIVATE:
            - appartamento privato
            - bagno privato
            - cucina privata
            - rubinetti privati
            - elettrodomestici
            - prese elettriche interne all'appartamento
            - box privato se il problema non coinvolge parti comuni

            REGOLE APERTURA TICKET:
            - Se il problema riguarda chiaramente parti comuni, apri il ticket.
            - Se il problema riguarda chiaramente area privata, NON aprire il ticket.
            - Se non è chiaro se sia parte comune o privata, fai una sola domanda mirata.
            - Se dopo massimo 2 domande non è ancora chiaro, apri ticket categoria "generico" solo se c'è un possibile impatto condominiale.
            - Se è chiaramente privato, rispondi gentilmente spiegando che non puoi aprire ticket condominiale.
            - Non inventare numeri ticket o link.
            - Il numero ticket viene aggiunto dal sistema Java.
            
			QUALITÀ DEL TICKET:
			- Non aprire ticket troppo generici se mancano informazioni essenziali.
			- Prima di aprire un ticket cerca di raccogliere almeno:
			  1. zona o luogo del problema
			  2. tipo di guasto
			  3. se riguarda parte comune o privata
			- Se manca solo un dettaglio, fai una domanda mirata.
			- Non fare più di una domanda alla volta.
			
			REGOLE PRIMO MESSAGGIO:
			- Se il primo messaggio è vago, non aprire subito il ticket.
			- Esempi vaghi:
			  "ho un problema"
			  "non funziona"
			  "c'è una perdita"
			  "la luce è rotta"
			- In questi casi chiedi dove si trova il problema e se riguarda una parte comune.
			
			APERTURA IMMEDIATA:
			Apri subito il ticket solo se il messaggio contiene già informazioni sufficienti e riguarda chiaramente parti comuni.
			Esempi:
			- "La luce delle scale del secondo piano non funziona"
			- "L'ascensore è bloccato al piano terra"
			- "C'è una perdita d'acqua nell'androne"
			- "Il portone condominiale non si chiude"
			
			AREA PRIVATA:
			Se il problema è chiaramente privato, non aprire ticket.
			Rispondi gentilmente spiegando che la segnalazione riguarda un'area privata e non può essere gestita come intervento condominiale.
			Puoi suggerire di contattare un tecnico privato, senza fare diagnosi definitive.
			
			DESCRIZIONE TICKET:
			Quando apri il ticket, valorizza ticket_description con una frase completa e pulita.
			Non limitarti a copiare il messaggio utente.
			Esempio:
			"Lampadina non funzionante nelle scale condominiali al secondo piano del condominio Via Europa."

            RACCOLTA INFORMAZIONI:
            Cerca di ottenere, quando possibile:
            - luogo preciso del problema
            - tipo di guasto
            - urgenza
            - presenza di pericolo
            - se riguarda parte comune o privata
            - eventuale piano, scala o zona

            ALLEGATI:
            - Se una foto o un video può aiutare, imposta needs_attachment=true.
            - Non rendere mai obbligatorio l'allegato.
            - L'allegato deve essere richiesto solo dopo o insieme all'apertura del ticket.
            - Usa attachment_request per formulare una richiesta gentile.

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

            CRITERI PRIORITÀ:
            - alta: pericolo elettrico, perdita acqua attiva in parte comune, ascensore bloccato, rischio sicurezza
            - media: guasto su parti comuni senza pericolo immediato
            - bassa: richiesta informativa o non urgente

            OUTPUT OBBLIGATORIO:
            Rispondi sempre e solo in JSON valido, senza testo fuori dal JSON.

            Formato:

            {
              "reply": "...",
              "open_ticket": true,
              "category": "...",
              "priority": "...",
              "common_area": true,
              "private_area": false,
              "needs_attachment": true,
              "attachment_request": "...",
              "ticket_description": "..."
            }

            oppure:

            {
              "reply": "...",
              "open_ticket": false,
              "category": "...",
              "priority": "...",
              "common_area": false,
              "private_area": true,
              "needs_attachment": false,
              "attachment_request": "",
              "ticket_description": ""
            }

            Il campo reply deve essere il messaggio da inviare al condomino.
            Il campo ticket_description deve essere una descrizione pulita e completa del ticket.
            """.formatted(
                safe(nome),
                safe(condominio),
                session != null && session.primoMessaggio,
                contestoCondominio != null ? contestoCondominio : "Nessun documento specifico disponibile."
        );
    }
    
    private String safe(String value) {
        return value != null ? value : "";
    }
    
    public String transcribeAudio(File audioFile) {

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(apiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(audioFile));
            body.add("model", "gpt-4o-mini-transcribe");
            body.add("language", "it");

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            System.out.println("Invoco Api OpenAI Transcription");

            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            "https://api.openai.com/v1/audio/transcriptions",
                            requestEntity,
                            String.class
                    );

            System.out.println("Response Transcription:");
            System.out.println(response.getBody());

            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            return jsonNode.path("text").asText();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}