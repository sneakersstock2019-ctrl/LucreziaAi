package it.sd.demo.bot.condomini.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.demo.bot.condomini.bean.AIResponse;
import it.sd.demo.bot.condomini.bean.Ticket;
import it.sd.demo.bot.condomini.bean.UserSession;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WhatsAppService {

    @Value("${whatsapp.token}")
    private String token;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;
    
    private final OpenAIService openAIService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final String urlApiMetaMessages = "https://graph.facebook.com/v25.0/{}/messages";
    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    private final Map<String, String> utenti = Map.of(
            "393492123304", "Salvatore D'Amato",
            "393282036763", "Marta Raffone"
    );

    public void elaboraMessaggio(String body) {
    	JsonNode jsonRoot = null;
    	JsonNode messageNode = null, message = null;
    	String from = null, testoMessaggio = null;
    	
    	try {
            jsonRoot = objectMapper.readTree(body);

            messageNode = jsonRoot.path("entry")
                    .get(0)
                    .path("changes")
                    .get(0)
                    .path("value");

            if (!messageNode.has("messages")) {
            	System.out.println("Nessun messaggio da leggere");
            	return;
            }

            message = messageNode.path("messages").get(0);

            from = message.path("from").asText();
            testoMessaggio = message.path("text").path("body").asText();

            processaMessaggio(from, testoMessaggio);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processaMessaggio(String from, String testoMessaggio) {
    	Ticket ticket = null;
    	UserSession userSession = null;
    	AIResponse aiResponse = null;
    	String nomeUtente = null, rispostaPerUtente = null;
    	
        nomeUtente = utenti.get(from);
        if (nomeUtente == null) {
        	System.err.println("Numero " + from + " non autorizzato.");
        	invioMessaggio(from, "Numero non autorizzato.");
            return;
        }

        userSession = sessions.getOrDefault(from, new UserSession());
        sessions.putIfAbsent(from, userSession);
        userSession.cronologiaMessaggi.add(testoMessaggio);
        
        aiResponse = openAIService.askLucrezia(testoMessaggio, userSession);
        rispostaPerUtente = aiResponse.getReply();
        
        if (aiResponse.isOpen_ticket()) {
            ticket = new Ticket();

            ticket.setId(123456L);
            ticket.setNome(nomeUtente);
            ticket.setTelefono(from);
            ticket.setDescrizione(testoMessaggio);
            ticket.setCategoria(aiResponse.getCategory());
            ticket.setStato("APERTO");


            rispostaPerUtente += """

                    Ticket aperto correttamente ✅

                    Numero ticket: #%d

                    Monitora qui:
                    https://demo-condomini.it/ticket/%d
                    """.formatted(
                    ticket.getId(),
                    ticket.getId()
            );
        }

        invioMessaggio(from, rispostaPerUtente);
        
//        // STEP 1 - saluto
//        if (userSession.step == null) {
//        	userSession.step = "ASK_PROBLEM";
//        	invioMessaggio(from, "Ciao " + nomeUtente + " 👋\nDimmi il problema.");
//            return;
//        }
//
//        // STEP 2 - ricezione problema
//        if ("ASK_PROBLEM".equals(userSession.step)) {
//            categoria = classificazione(testoMessaggio);
//
//            ticket = new Ticket();
//            ticket.setNome(nomeUtente);
//            ticket.setTelefono(from);
//            ticket.setDescrizione(testoMessaggio);
//            ticket.setCategoria(categoria);
//            ticket.setStato("APERTO");
//
//            userSession.step = "CLOSED";
//
//            invioMessaggio(from,
//                    "Grazie " + nomeUtente + " 👍\n\n" +
//                    "Ho aperto il ticket #" + ticket.getId() + "\n" +
//                    "Categoria: " + categoria + "\n\n" +
//                    "Un tecnico ti contatterà a breve. \n\n\n" + 
//                    "Per seguire lo stato della segnalazione clicca qui --> LINK");
//
//            return;
//        }

    }

    private void invioMessaggio(String to, String testoMessaggio) {
    	Map<String, Object> text = null;
    	Map<String, Object> payload = null;
    	HttpHeaders httpHeaders = null;
    	HttpEntity<Map<String, Object>> httpEntity = null;
    	
        try {
            text = Map.of("body", testoMessaggio);
            
            payload = Map.of(
                    "messaging_product", "whatsapp",
                    "to", to,
                    "type", "text",
                    "text", text
            );

            httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            httpHeaders.setBearerAuth(token);

            httpEntity = new HttpEntity<>(payload, httpHeaders);
            
            System.out.println("Invoco Api Meta Messages (POST): " + urlApiMetaMessages.replace("{}", phoneNumberId));
            System.out.println("Headers: " + httpHeaders);
            System.out.println("Payload: " + payload);
            restTemplate.postForEntity(urlApiMetaMessages.replace("{}", phoneNumberId), httpEntity, String.class);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}