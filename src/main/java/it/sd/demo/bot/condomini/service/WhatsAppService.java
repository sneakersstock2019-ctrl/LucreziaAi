package it.sd.demo.bot.condomini.service;

import java.util.List;
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
import it.sd.demo.bot.condomini.bean.AllegatoTemporaneo;
import it.sd.demo.bot.condomini.bean.ChatMessage;
import it.sd.demo.bot.condomini.bean.UserSession;
import it.sd.demo.bot.condomini.bean.Utente;
import it.sd.demo.bot.condomini.dao.AllegatoDao;
import it.sd.demo.bot.condomini.dao.AllegatoTemporaneoDao;
import it.sd.demo.bot.condomini.dao.CondominioAiDao;
import it.sd.demo.bot.condomini.dao.TicketDao;
import it.sd.demo.bot.condomini.dao.UtenteDao;
import it.sd.demo.bot.condomini.util.PhoneUtils;
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
    private static final String STEP_ATTESA_ALLEGATI = "ATTESA_ALLEGATI";

    private final OpenAIService openAIService;
    private final UtenteDao utenteDao;
    private final TicketDao ticketDao;
    private final PhoneUtils phoneUtils;
    private final CondominioAiDao condominioAiDao;
    private final AllegatoTemporaneoDao allegatoTemporaneoDao;
    private final AllegatoDao allegatoDao;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private final String urlApiMetaMessages = "https://graph.facebook.com/v25.0/{}/messages";

    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    public void elaboraMessaggio(String body) {
        try {
            JsonNode jsonRoot = objectMapper.readTree(body);

            JsonNode messageNode = jsonRoot.path("entry")
                    .get(0)
                    .path("changes")
                    .get(0)
                    .path("value");

            if (!messageNode.has("messages")) {
                System.out.println("Nessun messaggio da leggere");
                return;
            }

            JsonNode message = messageNode.path("messages").get(0);

            String from = phoneUtils.normalizePhone(message.path("from").asText());
            String type = message.path("type").asText();

            if ("image".equals(type) || "video".equals(type) || "document".equals(type)) {
                processaAllegato(from, type, message);
                return;
            }

            if (!"text".equals(type)) {
                invioMessaggio(from, "Al momento posso gestire testo, immagini, video e documenti.");
                return;
            }
            
            String testoMessaggio = message.path("text").path("body").asText();

            System.out.println("Processo Messaggio da " + from + ": " + testoMessaggio);

            processaMessaggio(from, testoMessaggio);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processaMessaggio(String from, String testoMessaggio) {

        Utente utente = utenteDao.findCondominoByTelefono(from);

        if (utente == null) {
            System.err.println("Numero " + from + " non autorizzato.");
            invioMessaggio(from, "Numero non autorizzato.");
            return;
        }

        String nomeUtente = utente.getNome();

        UserSession userSession = sessions.getOrDefault(from, new UserSession());
        sessions.putIfAbsent(from, userSession);

        userSession.nome = nomeUtente;

        boolean haTicketAperti = ticketDao.hasTicketApertiByUtente(utente.getId());
        userSession.haTicketAperti = haTicketAperti;

        if (userSession.step == null && haTicketAperti) {
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
            gestisciSceltaTicket(from, testoMessaggio, nomeUtente, userSession);
            return;
        }

        if (STEP_NUOVA_SEGNALAZIONE.equals(userSession.step)) {
            userSession.step = STEP_NUOVA_SEGNALAZIONE;
        }

        String contestoCondominio = condominioAiDao.getContestoAiByCondominio(utente.getIdCondominio());
        AIResponse aiResponse =
                openAIService.askLucrezia(
                        testoMessaggio,
                        userSession,
                        utente,
                        contestoCondominio
                );

        String rispostaPerUtente = aiResponse.getReply();

        if (rispostaPerUtente == null || rispostaPerUtente.isBlank()) {
            rispostaPerUtente = "Mi dispiace, al momento non riesco a elaborare la richiesta.";
        }

        salvaConversazione(userSession, testoMessaggio, rispostaPerUtente);

        if (aiResponse.isOpenTicket()) {
        	userSession.step = null;
        	
        	String categoria = normalizeCategoria(aiResponse.getCategory());
        	String priorita = normalizePriorita(aiResponse.getPriority());
        	String descrizioneTicket =
        	        aiResponse.getTicketDescription() != null && !aiResponse.getTicketDescription().isBlank()
        	                ? aiResponse.getTicketDescription()
        	                : testoMessaggio;
        	
        	Long idTicket = ticketDao.insertTicket(
        	        utente.getIdCondominio(),
        	        utente.getId(),
        	        categoria,
        	        priorita,
        	        "WHATSAPP",
        	        descrizioneTicket
        	);

            if (idTicket == null) {
                invioMessaggio(from,
                        "Mi dispiace, ho capito la segnalazione ma non sono riuscita ad aprire il ticket. Riprova tra poco."
                );
                return;
            }
            
            collegaAllegatiTemporanei(from, idTicket);

            rispostaPerUtente += """

                    
                    Ticket aperto correttamente ✅

                    Numero ticket: #%d

                    Monitora qui:
                    https://demo-condomini.it/ticket/%d
                    """.formatted(idTicket, idTicket);

            resetSessioneDopoTicket(userSession);

            invioMessaggio(from, rispostaPerUtente);
            
            if (Boolean.TRUE.equals(aiResponse.getNeedsAttachment())) {
                userSession.step = STEP_ATTESA_ALLEGATI;
                userSession.idTicketAperto = idTicket;
            }
            
            if (Boolean.TRUE.equals(aiResponse.getNeedsAttachment())
                    && aiResponse.getAttachmentRequest() != null
                    && !aiResponse.getAttachmentRequest().isBlank()) {

                rispostaPerUtente += "\n\n" + aiResponse.getAttachmentRequest();
            }
            
            return;
        }

        userSession.tentativiComprensione++;

        if (userSession.tentativiComprensione >= 10) {

            Long idTicket = ticketDao.insertTicket(
                    utente.getIdCondominio(),
                    utente.getId(),
                    "generico",
                    "media",
                    "WHATSAPP",
                    testoMessaggio
            );

            if (idTicket == null) {
                invioMessaggio(from,
                        "Mi dispiace, non sono riuscita ad aprire la segnalazione generica. Riprova tra poco."
                );
                return;
            }

            rispostaPerUtente =
                    "Grazie per le informazioni 😊\n\n" +
                            "Per non farti perdere altro tempo, ho aperto una segnalazione generica riportando la descrizione che mi hai fornito.\n\n" +
                            "Ticket aperto correttamente ✅\n" +
                            "Numero ticket: #" + idTicket + "\n\n" +
                            "Puoi monitorarlo qui:\n" +
                            "https://demo-condomini.it/ticket/" + idTicket;

            resetSessioneDopoTicket(userSession);
        }

        invioMessaggio(from, rispostaPerUtente);
    }

    private void gestisciSceltaTicket(String from,
                                      String testoMessaggio,
                                      String nomeUtente,
                                      UserSession userSession) {

        String msg = testoMessaggio.toLowerCase();

        if (msg.contains("1") || msg.contains("stato") || msg.contains("ticket")) {
            invioMessaggio(from,
                    "Certo 😊\n" +
                            "Puoi monitorare lo stato delle tue segnalazioni da qui:\n\n" +
                            "https://demo-condomini.it/ticket?telefono=" + from
            );

            userSession.step = null;
            return;
        }

        if (msg.contains("2") || msg.contains("nuova") || msg.contains("segnalazione")) {
            userSession.step = STEP_NUOVA_SEGNALAZIONE;
            userSession.tentativiComprensione = 0;
            userSession.cronologiaMessaggi.clear();
            userSession.primoMessaggio = false;

            invioMessaggio(from,
                    "Va bene " + nomeUtente + " 😊\n" +
                            "Descrivimi pure il nuovo problema e ti aiuterò ad aprire la segnalazione."
            );
            return;
        }

        invioMessaggio(from,
                "Puoi rispondermi con:\n" +
                        "1 per conoscere lo stato dei ticket aperti\n" +
                        "2 per aprire una nuova segnalazione"
        );
    }

    private void salvaConversazione(UserSession userSession,
                                    String testoMessaggio,
                                    String rispostaPerUtente) {

        userSession.cronologiaMessaggi.add(new ChatMessage("user", testoMessaggio));
        userSession.cronologiaMessaggi.add(new ChatMessage("assistant", rispostaPerUtente));

        userSession.primoMessaggio = false;

        if (userSession.cronologiaMessaggi.size() > 20) {
            userSession.cronologiaMessaggi =
                    userSession.cronologiaMessaggi.subList(
                            userSession.cronologiaMessaggi.size() - 20,
                            userSession.cronologiaMessaggi.size()
                    );
        }
    }

    private void resetSessioneDopoTicket(UserSession userSession) {
        userSession.haTicketAperti = true;
        userSession.step = null;
        userSession.tentativiComprensione = 0;
        userSession.cronologiaMessaggi.clear();
        userSession.primoMessaggio = false;
    }

    private void invioMessaggio(String to, String testoMessaggio) {
        try {
            Map<String, Object> text = Map.of("body", testoMessaggio);

            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "to", to,
                    "type", "text",
                    "text", text
            );

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            httpHeaders.setBearerAuth(token);

            HttpEntity<Map<String, Object>> httpEntity =
                    new HttpEntity<>(payload, httpHeaders);

            String url = urlApiMetaMessages.replace("{}", phoneNumberId);

            System.out.println("Invoco Api Meta Messages (POST): " + url);
            System.out.println("Payload: " + payload);

            restTemplate.postForEntity(url, httpEntity, String.class);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void processaAllegato(String from, String type, JsonNode message) {

        UserSession userSession = sessions.get(from);

        String mediaId = message.path(type).path("id").asText();
        String mimeType = message.path(type).path("mime_type").asText(null);
        String filename = message.path(type).path("filename").asText(null);

        String tipoAllegato = mapTipoAllegato(type);

        if (userSession != null
                && "ATTESA_ALLEGATI".equals(userSession.step)
                && userSession.idTicketAperto != null) {

            allegatoDao.insertAllegato(
                    userSession.idTicketAperto,
                    tipoAllegato,
                    filename,
                    "whatsapp-media-id:" + mediaId,
                    mimeType,
                    "WHATSAPP"
            );

            userSession.step = null;
            userSession.idTicketAperto = null;

            invioMessaggio(from, "Perfetto, ho allegato il file alla segnalazione. Grazie 😊");
            return;
        }

        allegatoTemporaneoDao.insert(
                from,
                tipoAllegato,
                mediaId,
                mimeType,
                filename
        );

        invioMessaggio(from,
                "Ho ricevuto l'allegato 😊\n" +
                "Ora descrivimi pure il problema e, se apriremo una segnalazione, lo collegherò automaticamente al ticket.");
    }
    
    private String mapTipoAllegato(String type) {

        if ("image".equals(type)) {
            return "IMMAGINE";
        }

        if ("video".equals(type)) {
            return "VIDEO";
        }

        if ("document".equals(type)) {
            return "DOCUMENTO";
        }

        return "ALTRO";
    }
    
    private void collegaAllegatiTemporanei(String telefono, Long idTicket) {

        List<AllegatoTemporaneo> temporanei =
                allegatoTemporaneoDao.findByTelefono(telefono);

        for (AllegatoTemporaneo a : temporanei) {
            allegatoDao.insertAllegato(
                    idTicket,
                    a.getTipo(),
                    a.getNomeFile(),
                    "whatsapp-media-id:" + a.getMediaId(),
                    a.getContentType(),
                    "WHATSAPP"
            );
        }

        allegatoTemporaneoDao.deleteByTelefono(telefono);
    }
    
    private String normalizeCategoria(String category) {

        if (category == null || category.isBlank()) {
            return "generico";
        }

        category = category.trim().toLowerCase();

        return switch (category) {
            case "elettricista" -> "elettricista";
            case "idraulico" -> "idraulico";
            case "ascensore" -> "ascensore";
            case "infiltrazioni" -> "infiltrazioni";
            case "amministrazione" -> "amministrazione";
            default -> "generico";
        };
    }
    
    private String normalizePriorita(String priority) {

        if (priority == null || priority.isBlank()) {
            return "media";
        }

        priority = priority.trim().toLowerCase();

        return switch (priority) {
            case "bassa" -> "bassa";
            case "alta" -> "alta";
            default -> "media";
        };
    }
}