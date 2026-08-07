package it.sd.lucrezia.ai.service.whatsapp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.lucrezia.ai.bean.FornitoreWhatsAppSession;
import it.sd.lucrezia.ai.bean.OpenAIRequestMessage;
import it.sd.lucrezia.ai.bean.TicketStatusInfo;
import it.sd.lucrezia.ai.bean.UserSession;
import it.sd.lucrezia.ai.bean.Utente;
import it.sd.lucrezia.ai.bean.WhatsAppAiResponse;
import it.sd.lucrezia.ai.bean.WhatsAppAllegatoTemporaneo;
import it.sd.lucrezia.ai.bean.WhatsAppFornitoreAiResponse;
import it.sd.lucrezia.ai.bean.WhatsAppMessage;
import it.sd.lucrezia.ai.dao.AllegatoDao;
import it.sd.lucrezia.ai.dao.AllegatoTemporaneoDao;
import it.sd.lucrezia.ai.dao.CondominioAiDao;
import it.sd.lucrezia.ai.dao.TicketConversazioneDao;
import it.sd.lucrezia.ai.dao.TicketDao;
import it.sd.lucrezia.ai.dao.UtenteDao;
import it.sd.lucrezia.ai.prompt.LucreziaPromptBuilder;
import it.sd.lucrezia.ai.service.openai.OpenAIService;
import it.sd.lucrezia.ai.util.PhoneUtils;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WhatsAppService {

    @Value("${whatsapp.token}")
    private String token;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;
    
    @Value("${whatsapp.url-api-meta-messages}")
    private String urlApiMetaMessages;

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
    private final TicketConversazioneDao ticketConversazioneDao;
    private final LucreziaPromptBuilder lucreziaPromptBuilder;
    
    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, FornitoreWhatsAppSession> sessioniFornitori = new ConcurrentHashMap<>();
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

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
        /*
         * Prima controlliamo se sta scrivendo un fornitore.
         */
        Utente fornitore = utenteDao.findFornitoreByTelefono(from);

        if (fornitore != null) {

            processaMessaggioFornitore(
                    from,
                    testoMessaggio,
                    fornitore
            );

            return;
        }

        /*
         * Altrimenti continuiamo con il normale flusso condomino.
         */
        Utente utente = utenteDao.findCondominoByTelefono(from);

        if (utente == null) {
            System.err.println("Numero " + from + " non autorizzato.");
            invioMessaggio(from, "Numero non autorizzato.");
            return;
        }

        String nomeUtente = utente.getNome();

        UserSession userSession = sessions.getOrDefault(from, new UserSession());
        sessions.putIfAbsent(from, userSession);

        if (STEP_ATTESA_ALLEGATI.equals(userSession.step)) {
            String msg = testoMessaggio.toLowerCase();

            if (msg.contains("no") || msg.contains("grazie") || msg.contains("basta")) {
                userSession.step = null;
                userSession.idTicketAperto = null;

                invioMessaggio(from, "Va bene, nessun problema 😊 La segnalazione resta comunque aperta.");
                return;
            }

            invioMessaggio(from,
                    "Se vuoi puoi inviarmi una foto o un video da allegare al ticket.\n" +
                    "Se non vuoi aggiungere allegati, puoi scrivere 'no grazie'.");
            return;
        }

        boolean haTicketAperti = ticketDao.hasTicketApertiByUtente(utente.getId());

        if (userSession.step == null 
                && userSession.cronologiaMessaggi.isEmpty()
                && userSession.tentativiComprensione == 0
                && haTicketAperti) {
            userSession.step = STEP_SCELTA_TICKET;

            invioMessaggio(from,
                    "Ciao " + nomeUtente + ", sono Lucrezia, l'assistente virtuale del tuo condominio 😊\n\n" +
                            "Vedo che hai già una o più segnalazioni aperte.\n\n" +
                            "Vuoi:\n" +
                            "1️⃣ conoscere lo stato dei ticket aperti\n" +
                            "2️⃣ aprire una nuova segnalazione?"
            );
            return;
        }

        if (STEP_SCELTA_TICKET.equals(userSession.step)) {
            gestisciSceltaTicket(from, testoMessaggio, nomeUtente, userSession, utente);
            return;
        }

        String contestoCondominio = condominioAiDao.getContestoAiByCondominio(utente.getIdCondominio());
        WhatsAppAiResponse aiResponse = askLucrezia(
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

            int allegatiCollegati = collegaAllegatiTemporanei(from, idTicket);
            ticketConversazioneDao.insertConversazione(idTicket, buildConversazioneOriginale(userSession));

            rispostaPerUtente += """
                    Ticket aperto correttamente ✅

                    Numero ticket: #%d

                    Per conoscere lo stato della segnalazione puoi scrivermi qui su WhatsApp oppure chiamarmi.
                    """.formatted(idTicket);

            resetSessioneDopoTicket(userSession);

            if (allegatiCollegati > 0) {
                rispostaPerUtente += "\n\nHo collegato al ticket anche l'allegato che mi hai inviato.";
            } else {
                userSession.step = STEP_ATTESA_ALLEGATI;
                userSession.idTicketAperto = idTicket;

                rispostaPerUtente += "\n\nSe vuoi, puoi inviarmi ora una foto o un video del problema e lo allegherò alla segnalazione.";
            }

            invioMessaggio(from, rispostaPerUtente);
            return;
        }

        userSession.step = STEP_NUOVA_SEGNALAZIONE;
        userSession.tentativiComprensione++;

        if (userSession.tentativiComprensione >= 5) {

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

            ticketConversazioneDao.insertConversazione(idTicket, buildConversazioneOriginale(userSession));
            
            rispostaPerUtente =
                    "Grazie per le informazioni 😊\n\n" +
                            "Per non farti perdere altro tempo, ho aperto una segnalazione generica riportando la descrizione che mi hai fornito.\n\n" +
                            "Ticket aperto correttamente ✅\n" +
                            "Numero ticket: #" + idTicket + "\n\n" +
                            "Per conoscere lo stato della segnalazione puoi scrivermi qui su WhatsApp oppure chiamarmi.";

            resetSessioneDopoTicket(userSession);
        }

        invioMessaggio(from, rispostaPerUtente);
    }
    
    private void processaMessaggioFornitore(
            String from,
            String testoMessaggio,
            Utente fornitore) {

        try {

            FornitoreWhatsAppSession session =
                    sessioniFornitori.computeIfAbsent(
                            from,
                            key -> new FornitoreWhatsAppSession()
                    );

            /*
             * Salviamo subito il messaggio ricevuto.
             */
            session.getCronologiaMessaggi().add(
                    new WhatsAppMessage(
                            "user",
                            testoMessaggio
                    )
            );

            List<Long> ticketIds =
                    ticketDao.findTicketAssegnatiApertiByFornitore(
                            fornitore.getId()
                    );

            if (ticketIds.isEmpty()) {

                String risposta =
                        "Ciao " + fornitore.getNome()
                        + ", al momento non risultano interventi aperti assegnati a te.";

                salvaRispostaFornitore(
                        session,
                        from,
                        risposta
                );

                return;
            }

            List<TicketStatusInfo> tickets =
                    new ArrayList<>();

            for (Long ticketId : ticketIds) {

                TicketStatusInfo ticket =
                        ticketDao.findTicketStatusById(ticketId);

                if (ticket != null) {
                    tickets.add(ticket);
                }
            }

            /*
             * Se c'è un solo ticket sappiamo già
             * di quale intervento stiamo parlando.
             */
            if (session.getIdTicket() == null
                    && tickets.size() == 1) {

                session.setIdTicket(
                        tickets.get(0).getId()
                );
            }

            WhatsAppFornitoreAiResponse aiResponse =
                    askLucreziaFornitore(
                            testoMessaggio,
                            fornitore,
                            tickets,
                            session
                    );

            if (aiResponse == null) {

                String risposta =
                        "Non sono riuscita a interpretare la risposta. "
                        + "Puoi indicarmi se puoi prendere in carico "
                        + "l'intervento e, in caso affermativo, quando potresti intervenire?";

                salvaRispostaFornitore(
                        session,
                        from,
                        risposta
                );

                return;
            }

            /*
             * L'AI non deve dimenticare il ticket
             * già individuato precedentemente.
             */
            if (aiResponse.getTicketId() == null
                    && session.getIdTicket() != null) {

                aiResponse.setTicketId(
                        session.getIdTicket()
                );
            }

            if (aiResponse.getTicketId() == null
                    && tickets.size() == 1) {

                aiResponse.setTicketId(
                        tickets.get(0).getId()
                );
            }

            if (aiResponse.getTicketId() != null) {
                session.setIdTicket(
                        aiResponse.getTicketId()
                );
            }

            gestisciRispostaAiFornitore(
                    from,
                    fornitore,
                    aiResponse,
                    tickets,
                    session
            );

        } catch (Exception e) {

            e.printStackTrace();

            invioMessaggio(
                    from,
                    "Mi dispiace, si è verificato un problema "
                    + "durante la gestione dell'intervento. Riprova tra poco."
            );
        }
    }
    
    private WhatsAppFornitoreAiResponse askLucreziaFornitore(
            String testoMessaggio,
            Utente fornitore,
            List<TicketStatusInfo> tickets,
            FornitoreWhatsAppSession session) {

        try {

            List<OpenAIRequestMessage> messages =
                    new ArrayList<>();

            messages.add(
                    new OpenAIRequestMessage(
                            "system",
                            lucreziaPromptBuilder.buildFornitorePrompt(
                                    fornitore,
                                    tickets,
                                    session
                            )
                    )
            );

            /*
             * Passiamo tutta la conversazione precedente.
             *
             * L'ultimo messaggio dell'utente è già presente
             * nella cronologia perché lo abbiamo inserito
             * prima di chiamare questo metodo.
             */
            for (WhatsAppMessage message :
                    session.getCronologiaMessaggi()) {

                messages.add(
                        new OpenAIRequestMessage(
                                message.getRole(),
                                message.getContent()
                        )
                );
            }

            return openAIService.askLucreziaFornitore(
                    messages
            );

        } catch (Exception e) {

            e.printStackTrace();
            return null;
        }
    }
    
    private void salvaRispostaFornitore(
            FornitoreWhatsAppSession session,
            String telefono,
            String risposta) {

        session.getCronologiaMessaggi().add(
                new WhatsAppMessage(
                        "assistant",
                        risposta
                )
        );

        invioMessaggio(
                telefono,
                risposta
        );
    }
    
    private void gestisciRispostaAiFornitore(
            String from,
            Utente fornitore,
            WhatsAppFornitoreAiResponse aiResponse,
            List<TicketStatusInfo> tickets,
            FornitoreWhatsAppSession session) {

        String action = aiResponse.getAction();

        if (action == null || action.isBlank()) {
            action = "UNCLEAR";
        }

        /*
         * Se l'AI non restituisce il ticket ma nella sessione
         * lo abbiamo già identificato, utilizziamo quello.
         */
        if (aiResponse.getTicketId() == null
                && session.getIdTicket() != null) {

            aiResponse.setTicketId(
                    session.getIdTicket()
            );
        }

        /*
         * Se esiste un solo ticket assegnato al fornitore,
         * non ha senso chiedergli quale ticket intende.
         */
        if (aiResponse.getTicketId() == null
                && tickets != null
                && tickets.size() == 1) {

            aiResponse.setTicketId(
                    tickets.get(0).getId()
            );

            session.setIdTicket(
                    tickets.get(0).getId()
            );
        }

        /*
         * Se l'AI ha identificato un ticket,
         * lo manteniamo nella sessione per i messaggi successivi.
         */
        if (aiResponse.getTicketId() != null) {

            session.setIdTicket(
                    aiResponse.getTicketId()
            );
        }

        switch (action.toUpperCase()) {

            case "ACCEPT" -> {

                gestisciAccettazioneFornitore(
                        from,
                        fornitore,
                        aiResponse,
                        session
                );
            }

            case "REJECT" -> {

                gestisciRifiutoFornitore(
                        from,
                        fornitore,
                        aiResponse,
                        session
                );
            }

            case "NEED_INFO" -> {

                String reply = aiResponse.getReply();

                if (reply == null || reply.isBlank()) {

                    reply =
                            "Va bene. Puoi indicarmi anche "
                            + "la prima data e l'orario in cui "
                            + "potresti effettuare l'intervento?";
                }

                /*
                 * IMPORTANTISSIMO:
                 * salviamo anche la risposta di Lucrezia nella
                 * cronologia della sessione.
                 *
                 * Così al prossimo messaggio OpenAI vedrà:
                 *
                 * Fornitore: posso venire mercoledì
                 * Lucrezia: a che ora?
                 * Fornitore: alle 16
                 */
                salvaRispostaFornitore(
                        session,
                        from,
                        reply
                );
            }

            case "UNCLEAR" -> {

                String reply = aiResponse.getReply();

                if (reply == null || reply.isBlank()) {

                    reply =
                            "Non sono sicura di aver capito. "
                            + "Puoi indicarmi se puoi prendere in carico "
                            + "l'intervento e, in caso affermativo, "
                            + "quando potresti intervenire?";
                }

                salvaRispostaFornitore(
                        session,
                        from,
                        reply
                );
            }

            default -> {

                String reply =
                        "Non sono sicura di aver capito. "
                        + "Puoi indicarmi se puoi prendere in carico "
                        + "l'intervento e, in caso affermativo, "
                        + "quando potresti intervenire?";

                salvaRispostaFornitore(
                        session,
                        from,
                        reply
                );
            }
        }
    }
    
    private void gestisciAccettazioneFornitore(
            String from,
            Utente fornitore,
            WhatsAppFornitoreAiResponse response,
            FornitoreWhatsAppSession session) {

        if (response.getTicketId() == null
                || response.getDataIntervento() == null
                || response.getDataIntervento().isBlank()) {

            invioMessaggio(
                    from,
                    "Perfetto. Puoi indicarmi anche "
                            + "la data e l'orario previsti "
                            + "per l'intervento?"
            );

            return;
        }

        LocalDateTime dataIntervento;

        try {

            dataIntervento =
                    LocalDateTime.parse(
                            response.getDataIntervento()
                    );

        } catch (Exception e) {

            invioMessaggio(
                    from,
                    "Ho capito che puoi prendere in carico "
                            + "l'intervento, ma non sono riuscita "
                            + "a interpretare la data. "
                            + "Puoi indicarmela nuovamente?"
            );

            return;
        }

        boolean updated =
                ticketDao.prendiInCaricoTicket(
                        response.getTicketId(),
                        fornitore.getId(),
                        dataIntervento
                );

        if (!updated) {

            invioMessaggio(
                    from,
                    "Non sono riuscita ad aggiornare "
                            + "la segnalazione. Riprova tra poco."
            );

            return;
        }

        invioMessaggio(
                from,
                """
                Perfetto ✅

                Ho registrato la presa in carico della segnalazione #%d.

                📅 Intervento previsto: %s

                Grazie per la disponibilità.
                """.formatted(
                        response.getTicketId(),
                        dataIntervento.format(
                                DateTimeFormatter.ofPattern(
                                        "dd/MM/yyyy 'alle' HH:mm"
                                )
                        )
                )
        );
    }
    
    private void gestisciRifiutoFornitore(
            String from,
            Utente fornitore,
            WhatsAppFornitoreAiResponse response,
            FornitoreWhatsAppSession session) {

        if (response.getTicketId() == null) {

            invioMessaggio(
                    from,
                    "Ho capito che non puoi prendere in carico "
                            + "l'intervento. Puoi indicarmi a quale "
                            + "segnalazione ti riferisci?"
            );

            return;
        }

        /*
         * QUI poi implementeremo:
         *
         * 1. salvataggio rifiuto fornitore;
         * 2. ricerca altro fornitore compatibile;
         * 3. invio automatico richiesta WhatsApp;
         * 4. escalation fino alla presa in carico.
         */

        invioMessaggio(
                from,
                """
                Va bene, grazie per avermi avvisata.

                Ho registrato che non puoi prendere in carico la segnalazione #%d.

                Provvederò a gestire la richiesta con un altro fornitore disponibile.
                """.formatted(response.getTicketId())
        );
    }

    private void gestisciSceltaTicket(String from,
                                      String testoMessaggio,
                                      String nomeUtente,
                                      UserSession userSession,
                                      Utente utente) {

        String msg = testoMessaggio.toLowerCase();

        if (msg.contains("1") || msg.contains("stato") || msg.contains("ticket")) {
        	List<Long> ticketIds = ticketDao.findTicketApertiByUtente(utente.getId());
        	
        	StringBuilder risposta = new StringBuilder();

        	risposta.append("Ecco lo stato delle tue segnalazioni aperte 😊\n\n");

        	for (Long idTicket : ticketIds) {

        	    TicketStatusInfo ticket =
        	            ticketDao.findTicketStatusById(idTicket);

        	    risposta.append("🎫 Ticket #")
        	             .append(ticket.getId())
        	             .append("\n");

        	    risposta.append("📌 Stato: ")
        	             .append(ticket.getStatoDescrizione());

        	    if (ticket.getNomeFornitore() != null
        	            && !ticket.getNomeFornitore().isBlank()) {

        	        risposta.append("\n👷 Fornitore: ")
        	                 .append(ticket.getNomeFornitore());
        	    }

        	    if (ticket.getDataInterventoPrevista() != null) {
        	        risposta.append("\n📅 Intervento previsto: ")
                    .append(
                        ticket.getDataInterventoPrevista()
                              .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    );
        	    }

        	    risposta.append("\n\n");
        	}

        	risposta.append("Se desideri aprire una nuova segnalazione, descrivimi pure il problema.");

        	invioMessaggio(from, risposta.toString());

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

        userSession.cronologiaMessaggi.add(new WhatsAppMessage("user", testoMessaggio));
        userSession.cronologiaMessaggi.add(new WhatsAppMessage("assistant", rispostaPerUtente));

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
        userSession.step = null;
        userSession.tentativiComprensione = 0;
        userSession.cronologiaMessaggi.clear();
        userSession.primoMessaggio = false;
    }

    private void invioMessaggio(String to, String testoMessaggio) {
    	ResponseEntity<String> responseEntity = null;
    	
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

            responseEntity = restTemplate.postForEntity(url, httpEntity, String.class);
            System.out.println("Response (" + responseEntity.getStatusCode() + "): " + responseEntity.getBody());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void inviaRichiestaFotoPostChiamata(String telefono,
    		String nome,
    		Long idTicket) {

    	UserSession userSession = sessions.getOrDefault(telefono, new UserSession());
    	sessions.putIfAbsent(telefono, userSession);

    	userSession.step = STEP_ATTESA_ALLEGATI;
    	userSession.idTicketAperto = idTicket;

    	invioMessaggio(
    			telefono,
    			"""
    			Ciao %s 👋

    			Come concordato telefonicamente, puoi rispondere a questo messaggio allegando una o più foto o video della segnalazione.

    			Li collegherò automaticamente al ticket #%d.

    			Se non vuoi aggiungere allegati, puoi scrivere "no grazie".
    			""".formatted(nome, idTicket)
    			);
    }
    
    private WhatsAppAiResponse askLucrezia(String messaggioUtente, UserSession session, Utente utente, String contestoCondominio) {
    	List<OpenAIRequestMessage> messaggiOpenAIRequestMessage = null;
    	WhatsAppAiResponse whatsAppAiResponse = null;
    	String systemPrompt = null;
    	
        try {
            messaggiOpenAIRequestMessage = new ArrayList<>();
            
            systemPrompt = lucreziaPromptBuilder.buildWhatsAppSystemPrompt(session, utente, contestoCondominio);
            messaggiOpenAIRequestMessage.add(new OpenAIRequestMessage(
                    "system",
                    systemPrompt
            ));

            for (WhatsAppMessage chatMessage : session.cronologiaMessaggi) {
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

            whatsAppAiResponse = openAIService.askLucrezia(messaggiOpenAIRequestMessage);
            System.out.println("whatsAppAiResponse: " + whatsAppAiResponse);

            return whatsAppAiResponse;

        } catch (Exception e) {
            e.printStackTrace();

            WhatsAppAiResponse error = new WhatsAppAiResponse();
            error.setReply(
                    "Mi dispiace, al momento non riesco a elaborare la richiesta."
            );

            return error;
        }
    }
    
    private void processaAllegato(String from, String type, JsonNode message) {

        UserSession userSession = sessions.getOrDefault(from, new UserSession());
        sessions.putIfAbsent(from, userSession);

        String mediaId = message.path(type).path("id").asText();
        String mimeType = message.path(type).path("mime_type").asText(null);
        String filename = message.path(type).path("filename").asText(null);

        String tipoAllegato = mapTipoAllegato(type);

        if (STEP_ATTESA_ALLEGATI.equals(userSession.step)
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

        userSession.step = STEP_NUOVA_SEGNALAZIONE;
        userSession.tentativiComprensione = 0;

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
    
    private int collegaAllegatiTemporanei(String telefono, Long idTicket) {

        List<WhatsAppAllegatoTemporaneo> temporanei =
                allegatoTemporaneoDao.findByTelefono(telefono);

        for (WhatsAppAllegatoTemporaneo a : temporanei) {
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

        return temporanei.size();
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
    
    private String buildConversazioneOriginale(UserSession userSession) {

        StringBuilder sb = new StringBuilder();

        if (userSession == null || userSession.cronologiaMessaggi == null) {
            return "";
        }

        for (WhatsAppMessage message : userSession.cronologiaMessaggi) {

            if ("user".equals(message.getRole())) {
                sb.append("Condomino: ");
            } else if ("assistant".equals(message.getRole())) {
                sb.append("Lucrezia: ");
            } else {
                sb.append(message.getRole()).append(": ");
            }

            sb.append(message.getContent()).append("\n\n");
        }

        return sb.toString().trim();
    }
}