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
    
    private static final String STEP_SCELTA_TICKET = "SCELTA_TICKET";
    private static final String STEP_NUOVA_SEGNALAZIONE = "NUOVA_SEGNALAZIONE";
    
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
        
        if (userSession.step == null && userSession.haTicketAperti) {
        	userSession.step = STEP_SCELTA_TICKET;

        	invioMessaggio(from,
                    "Ciao " + nomeUtente + ", sono Lucrezia, l'assistente virtuale del condominio 😊\n\n" +
                    "Vedo che hai già una o più segnalazioni aperte.\n\n" +
                    "Vuoi:\n" +
                    "1️⃣ conoscere lo stato dei ticket aperti\n" +
                    "2️⃣ aprire una nuova segnalazione?"
            );
            return;
        }
        
        if (STEP_SCELTA_TICKET.equals(userSession.step)) {
            if (testoMessaggio.toLowerCase().contains("1") || testoMessaggio.toLowerCase().contains("stato") || testoMessaggio.toLowerCase().contains("ticket")) {
            	invioMessaggio(from,
                        "Certo 😊\n" +
                        "Puoi monitorare lo stato delle tue segnalazioni da qui:\n\n" +
                        "https://demo-condomini.it/ticket?telefono=" + from
                );

            	userSession.step = null;
                return;
            }

            if (testoMessaggio.toLowerCase().contains("2") || testoMessaggio.toLowerCase().contains("nuova") || testoMessaggio.toLowerCase().contains("segnalazione")) {
            	userSession.step = STEP_NUOVA_SEGNALAZIONE;
            	userSession.tentativiComprensione = 0;
            	userSession.cronologiaMessaggi.clear();

            	invioMessaggio(from,
                        "Va bene 😊\n" +
                        "Descrivimi pure il nuovo problema e ti aiuterò ad aprire la segnalazione."
                );
                return;
            }

            invioMessaggio(from,
                    "Puoi rispondermi con:\n" +
                    "1 per conoscere lo stato dei ticket aperti\n" +
                    "2 per aprire una nuova segnalazione"
            );
            return;
        }
        
        
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
            userSession.haTicketAperti = true;

            rispostaPerUtente += """

                    Ticket aperto correttamente ✅

                    Numero ticket: #%d

                    Monitora qui:
                    https://demo-condomini.it/ticket/%d
                    """.formatted(
                    ticket.getId(),
                    ticket.getId()
            );
        } else {
        	userSession.tentativiComprensione++;

            if (userSession.tentativiComprensione >= 3) {

                ticket = new Ticket();
                ticket.setId(123456L);
                ticket.setNome(nomeUtente);
                ticket.setTelefono(from);
                ticket.setDescrizione(testoMessaggio);
                ticket.setCategoria("generico");
                ticket.setStato("APERTO");

                userSession.haTicketAperti = true;

                rispostaPerUtente = 
                        "Grazie per le informazioni 😊\n\n" +
                        "Per non farti perdere altro tempo, ho aperto una segnalazione generica riportando la descrizione che mi hai fornito.\n\n" +
                        "Ticket aperto correttamente ✅\n" +
                        "Numero ticket: #" + ticket.getId() + "\n\n" +
                        "Puoi monitorarlo qui:\n" +
                        "https://demo-condomini.it/ticket/";

                userSession.step = null;
                userSession.tentativiComprensione = 0;
                userSession.cronologiaMessaggi.clear();
            }

        }

        invioMessaggio(from, rispostaPerUtente);
        
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