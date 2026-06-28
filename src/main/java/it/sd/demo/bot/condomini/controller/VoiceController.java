package it.sd.demo.bot.condomini.controller;

import java.io.File;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.sd.demo.bot.condomini.bean.AIResponse;
import it.sd.demo.bot.condomini.bean.ChatMessage;
import it.sd.demo.bot.condomini.bean.TicketChoiceIntent;
import it.sd.demo.bot.condomini.bean.TicketStatusInfo;
import it.sd.demo.bot.condomini.bean.UserSession;
import it.sd.demo.bot.condomini.bean.Utente;
import it.sd.demo.bot.condomini.bean.VoiceContext;
import it.sd.demo.bot.condomini.bean.VoiceSessionStep;
import it.sd.demo.bot.condomini.dao.CondominioAiDao;
import it.sd.demo.bot.condomini.dao.TicketConversazioneDao;
import it.sd.demo.bot.condomini.dao.TicketDao;
import it.sd.demo.bot.condomini.dao.UtenteDao;
import it.sd.demo.bot.condomini.service.OpenAIService;
import it.sd.demo.bot.condomini.service.TwilioService;
import it.sd.demo.bot.condomini.service.VoiceCallContextRegistry;
import it.sd.demo.bot.condomini.service.VoiceSessionService;
import it.sd.demo.bot.condomini.util.PhoneUtils;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/voice")
@RequiredArgsConstructor
public class VoiceController {

    private static final String TWILIO_VOICE = "Polly.Bianca-Neural";

    private final OpenAIService openAIService;
    private final VoiceSessionService voiceSessionService;
    private final UtenteDao utenteDao;
    private final TicketDao ticketDao;
    private final CondominioAiDao condominioAiDao;
    private final TicketConversazioneDao ticketConversazioneDao;
    private final PhoneUtils phoneUtils;
    private final TwilioService twilioService;
    private final VoiceCallContextRegistry voiceCallContextRegistry;
    
    private static final String TEST_MEDIA_STREAM_PHONE = "3492123304";

    @RequestMapping(
            value = "/incoming",
            method = {RequestMethod.GET, RequestMethod.POST},
            produces = "application/xml"
    )
    public String incomingCall(@RequestParam(value = "From", required = false) String from) {

        String phone = phoneUtils.normalizePhone(from);

        System.out.println("############################");
        System.out.println("TWILIO INCOMING CALL");
        System.out.println("FROM = " + from);
        System.out.println("PHONE = " + phone);
        System.out.println("############################");

        Utente utente = utenteDao.findCondominoByTelefono(phone);

        if (utente == null) {
            return buildSayResponse(
                    "Buongiorno, sono Lucrezia. Il numero da cui sta chiamando non risulta abilitato al servizio."
            );
        }
        
        UserSession session = voiceSessionService.getOrCreateVoiceSession(phone);

        session.nome = utente.getNome();
        session.setCondominoId(utente.getId());
        session.primoMessaggio = true;
        session.tentativiComprensione = 0;
        session.cronologiaMessaggi.clear();

        if (session.registrazioniAudio != null) {
            session.registrazioniAudio.clear();
        }

        session.ultimaRegistrazioneAudio = null;

        List<TicketStatusInfo> ticketAperti = ticketDao.findOpenTicketsByUtente(utente.getId());

        if (!ticketAperti.isEmpty()) {

            session.setVoiceSessionStep(VoiceSessionStep.WAIT_TICKET_CHOICE);
            session.setOpenTicketIds(
                    ticketAperti.stream()
                            .map(TicketStatusInfo::getId)
                            .toList()
            );
            
            if (TEST_MEDIA_STREAM_PHONE.equals(phone)) {
                return buildRealtimeConnectResponse(utente, phone);
            }

            return buildRecordResponse(
            	    "Ciao " + utente.getNome()
            	    + ". Vedo che hai una segnalazione aperta. "
            	    + "Puoi dire stato segnalazione per ricevere aggiornamenti sul ticket esistente, "
            	    + "oppure nuova segnalazione per comunicarne una nuova."
            	);
        }

        session.setVoiceSessionStep(VoiceSessionStep.NEW_TICKET);
        
        if (TEST_MEDIA_STREAM_PHONE.equals(phone)) {
            return buildRealtimeConnectResponse(utente, phone);
        }

        return buildRecordResponse(
                "Ciao " + utente.getNome()
                        + ", sono Lucrezia. Mi descriva pure il problema."
        );
        
    }

    @PostMapping(value = "/recording", produces = "application/xml")
    public String recording(@RequestParam("RecordingUrl") String recordingUrl,
                            @RequestParam(value = "From", required = false) String from) {

        long start = System.currentTimeMillis();

        String phone = phoneUtils.normalizePhone(from);

        System.out.println("############################");
        System.out.println("TWILIO RECORDING RECEIVED");
        System.out.println("FROM = " + from);
        System.out.println("PHONE = " + phone);
        System.out.println("RecordingUrl = " + recordingUrl);
        System.out.println("############################");

        try {
            Utente utente = utenteDao.findCondominoByTelefono(phone);

            if (utente == null) {
                voiceSessionService.removeSession(phone);
                return buildSayResponse("Mi dispiace, il numero non risulta abilitato al servizio.");
            }

            UserSession session = voiceSessionService.getOrCreateVoiceSession(phone);
            session.nome = utente.getNome();
            session.ultimaRegistrazioneAudio = recordingUrl;

            if (session.registrazioniAudio != null) {
                session.registrazioniAudio.add(recordingUrl + ".mp3");
            }
            
            return buildProcessingRedirectResponse(getRandomProcessingMessage());
            
        } catch (Exception e) {
            e.printStackTrace();

            long totaleMs = System.currentTimeMillis() - start;

            System.out.println("############################");
            System.out.println("ERRORE VOICE");
            System.out.println("TOTALE MS = " + totaleMs);
            System.out.println("############################");

            return buildRecordResponse(
                    "Mi scusi, ho avuto un problema nel capire il messaggio. Può ripetere?"
            );
        }
    }
    
    @PostMapping(value = "/recording-realtime")
    public ResponseEntity<String> recordingRealtime(@RequestParam("RecordingUrl") String recordingUrl,
                                                    @RequestParam("CallSid") String callSid) {

        System.out.println("############################");
        System.out.println("TWILIO REALTIME RECORDING RECEIVED");
        System.out.println("CALL SID = " + callSid);
        System.out.println("RecordingUrl = " + recordingUrl);
        System.out.println("############################");

        VoiceContext context = voiceCallContextRegistry.get(callSid);

        if (context != null && context.getIdTicketCreato() != null) {
            ticketConversazioneDao.updateAudioUrlByTicket(
                    context.getIdTicketCreato(),
                    recordingUrl + ".mp3"
            );
        }

        return ResponseEntity.ok("OK");
    }
    
    @PostMapping(value = "/process", produces = "application/xml")
    public String process(@RequestParam(value = "From", required = false) String from) {

        long start = System.currentTimeMillis();

        String phone = phoneUtils.normalizePhone(from);

        System.out.println("############################");
        System.out.println("TWILIO PROCESS");
        System.out.println("FROM = " + from);
        System.out.println("PHONE = " + phone);
        System.out.println("############################");

        UserSession session = voiceSessionService.getOrCreateVoiceSession(phone);

        if (session.ultimaRegistrazioneAudio == null || session.ultimaRegistrazioneAudio.isBlank()) {

            System.out.println("Nessuna registrazione presente in sessione");

            return buildRecordResponse(
                    "Mi scusi, non ho ricevuto correttamente il messaggio. Può ripetere?"
            );
        }

        try {

            Utente utente = utenteDao.findCondominoByTelefono(phone);

            if (utente == null) {

                voiceSessionService.removeSession(phone);

                return buildSayResponse(
                        "Mi dispiace, il numero non risulta abilitato al servizio."
                );
            }

            long t1 = System.currentTimeMillis();

            File audioFile =
                    twilioService.downloadRecording(
                            session.ultimaRegistrazioneAudio
                    );

            long downloadMs = System.currentTimeMillis() - t1;

            t1 = System.currentTimeMillis();

            String speechResult = openAIService.transcribeAudio(audioFile);

            long transcriptionMs = System.currentTimeMillis() - t1;

            System.out.println("############################");
            System.out.println("TRASCRIZIONE PROCESS:");
            System.out.println(speechResult);
            System.out.println("############################");

            System.out.println("DOWNLOAD AUDIO MS = " + downloadMs);
            System.out.println("TRASCRIZIONE MS = " + transcriptionMs);

            session.ultimaRegistrazioneAudio = null;

            if (speechResult == null || speechResult.isBlank()) {

                long totaleMs =
                        System.currentTimeMillis() - start;

                System.out.println("ATTENZIONE: trascrizione vuota");
                System.out.println("TOTALE PROCESS MS = " + totaleMs);

                return buildRecordResponse(
                        "Mi scusi, non ho sentito bene. Può ripetere il problema parlando dopo il messaggio?"
                );
            }

            t1 = System.currentTimeMillis();

            String response =
                    gestisciRispostaVocale(
                            phone,
                            utente,
                            session,
                            speechResult
                    );

            long aiMs =
                    System.currentTimeMillis() - t1;

            long totaleMs =
                    System.currentTimeMillis() - start;

            System.out.println("############################");
            System.out.println("TEMPI PROCESS:");
            System.out.println("DOWNLOAD AUDIO MS = " + downloadMs);
            System.out.println("TRASCRIZIONE MS = " + transcriptionMs);
            System.out.println("AI + DB + TWIML MS = " + aiMs);
            System.out.println("TOTALE PROCESS MS = " + totaleMs);
            System.out.println("############################");

            return response;

        } catch (Exception e) {

            e.printStackTrace();

            long totaleMs =
                    System.currentTimeMillis() - start;

            System.out.println("############################");
            System.out.println("ERRORE PROCESS");
            System.out.println("TOTALE PROCESS MS = " + totaleMs);
            System.out.println("############################");

            return buildRecordResponse(
                    "Mi scusi, ho avuto un problema nel capire il messaggio. Può ripetere?"
            );
        }
    }

    private String gestisciRispostaVocale(String phone,
                                          Utente utente,
                                          UserSession session,
                                          String speechResult) {

        if (speechResult == null || speechResult.isBlank()) {
            session.tentativiComprensione++;

            if (session.tentativiComprensione >= 3) {
                voiceSessionService.removeSession(phone);
                return buildSayResponse(
                        "Mi dispiace, non sono riuscita a capire bene. La invito a richiamare più tardi."
                );
            }

            return buildRecordResponse(
                    "Mi scusi, non ho capito bene. Può ripetere il problema con poche parole?"
            );
        }
        
        if (session.getVoiceSessionStep() == VoiceSessionStep.WAIT_TICKET_CHOICE) {
            return gestisciSceltaTicket(phone, utente, session, speechResult);
        }

        if (session.getVoiceSessionStep() == VoiceSessionStep.WAIT_TICKET_STATUS_DETAIL) {
            return buildRecordResponse(
                    "Per ora posso leggere automaticamente lo stato solo quando c'è una singola segnalazione aperta. "
                            + "Mi dica pure se vuole aprire una nuova segnalazione."
            );
        }

        session.cronologiaMessaggi.add(new ChatMessage("user", speechResult));

        long t1 = System.currentTimeMillis();

        String contestoCondominio =
                condominioAiDao.getContestoAiByCondominio(utente.getIdCondominio());

        long contestoMs = System.currentTimeMillis() - t1;

        t1 = System.currentTimeMillis();

        AIResponse aiResponse =
                openAIService.askLucreziaVoice(
                        speechResult,
                        session,
                        utente,
                        contestoCondominio
                );

        long openAiMs = System.currentTimeMillis() - t1;

        System.out.println("############################");
        System.out.println("TEMPI DETTAGLIO VOICE:");
        System.out.println("CARICAMENTO CONTESTO DB MS = " + contestoMs);
        System.out.println("OPENAI CHAT MS = " + openAiMs);
        System.out.println("############################");

        String reply = aiResponse.getReply();

        if (reply == null || reply.isBlank()) {
            reply = "Mi scusi, può ripetere meglio il problema?";
        }

        session.cronologiaMessaggi.add(new ChatMessage("assistant", reply));
        session.primoMessaggio = false;
        trimHistory(session);

        if (!aiResponse.isOpenTicket()) {
            session.tentativiComprensione++;

            if (session.tentativiComprensione >= 3) {

                Long idTicket = creaTicketGenerico(utente, session, speechResult);
                voiceSessionService.removeSession(phone);

                if (idTicket == null) {
                    return buildSayResponse(
                            "Mi dispiace, non sono riuscita ad aprire la segnalazione. La invito a riprovare più tardi."
                    );
                }

                return buildSayResponse(
                        "Grazie, ho raccolto le informazioni principali. Ho aperto una segnalazione generica. Il numero ticket è "
                                + idTicket + "."
                );
            }

            return buildRecordResponse(reply);
        }

        String categoria = normalizeCategoria(aiResponse.getCategory());
        String priorita = normalizePriorita(aiResponse.getPriority());

        String descrizioneTicket =
                aiResponse.getTicketDescription() != null && !aiResponse.getTicketDescription().isBlank()
                        ? aiResponse.getTicketDescription()
                        : buildDescrizioneDaCronologia(session, speechResult);

        long tDb = System.currentTimeMillis();

        Long idTicket = ticketDao.insertTicket(
                utente.getIdCondominio(),
                utente.getId(),
                categoria,
                priorita,
                "TELEFONO",
                descrizioneTicket
        );

        long insertTicketMs = System.currentTimeMillis() - tDb;

        if (idTicket == null) {
            return buildSayResponse(
                    "Mi dispiace, ho capito la segnalazione ma non sono riuscita ad aprire il ticket."
            );
        }

        tDb = System.currentTimeMillis();

        ticketConversazioneDao.insertConversazione(
                idTicket,
                "TELEFONO",
                "TRASCRIZIONE",
                buildConversazioneOriginale(session),
                null
        );

        if (session.registrazioniAudio != null) {
            for (String urlAudio : session.registrazioniAudio) {
                ticketConversazioneDao.insertConversazione(
                        idTicket,
                        "TELEFONO",
                        "AUDIO",
                        null,
                        urlAudio
                );
            }
        }

        long insertConversazioniMs = System.currentTimeMillis() - tDb;

        System.out.println("############################");
        System.out.println("TEMPI DB VOICE:");
        System.out.println("INSERT TICKET MS = " + insertTicketMs);
        System.out.println("INSERT CONVERSAZIONI MS = " + insertConversazioniMs);
        System.out.println("############################");

        voiceSessionService.removeSession(phone);

        return buildSayResponse(
                "Perfetto, grazie " + utente.getNome()
                        + ". Ho registrato correttamente la segnalazione e verrà presa in carico quanto prima."
                        + ". Sarà mia premura gestirla al meglio, riceverà aggiornamenti appena possibile. "
                        + getRandomClosingMessage()
        );
    }

    private Long creaTicketGenerico(Utente utente, UserSession session, String ultimoMessaggio) {

        Long idTicket = ticketDao.insertTicket(
                utente.getIdCondominio(),
                utente.getId(),
                "generico",
                "media",
                "TELEFONO",
                buildDescrizioneDaCronologia(session, ultimoMessaggio)
        );

        if (idTicket != null) {
            ticketConversazioneDao.insertConversazione(
                    idTicket,
                    "TELEFONO",
                    "TRASCRIZIONE",
                    buildConversazioneOriginale(session),
                    null
            );

            if (session.registrazioniAudio != null) {
                for (String urlAudio : session.registrazioniAudio) {
                    ticketConversazioneDao.insertConversazione(
                            idTicket,
                            "TELEFONO",
                            "AUDIO",
                            null,
                            urlAudio
                    );
                }
            }
        }

        return idTicket;
    }

    private String buildRecordResponse(String message) {

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response>
                <Say language="it-IT" voice="%s">%s</Say>
                <Record action="/voice/recording"
	                    method="POST"
	                    maxLength="30"
	                    timeout="2"
	                    playBeep="false"
	                    trim="trim-silence"/>
            </Response>
            """.formatted(
                TWILIO_VOICE,
                escapeXml(message)
        );
    }
    
    private String buildRecordResponseWithMediaStream(String message) {

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response>
                <Start>
                    <Stream url="wss://demobotcondomini-production.up.railway.app/voice/media-stream">
                        <Parameter name="source" value="LucreziaVoce"/>
                    </Stream>
                </Start>
                <Say language="it-IT" voice="%s">%s</Say>
                <Record action="/voice/recording"
                        method="POST"
                        maxLength="30"
                        timeout="2"
                        playBeep="false"
                        trim="trim-silence"/>
            </Response>
            """.formatted(
                TWILIO_VOICE,
                escapeXml(message)
        );
    }
    
    private String buildRealtimeConnectResponse(Utente utente, String phone) {

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response>
                <Connect>
                    <Stream url="wss://demobotcondomini-production.up.railway.app/voice/media-stream">
                        <Parameter name="phone" value="%s"/>
                        <Parameter name="nome" value="%s"/>
                        <Parameter name="condominio" value="%s"/>
                        <Parameter name="idUtente" value="%s"/>
                        <Parameter name="idCondominio" value="%s"/>
                    </Stream>
                </Connect>
            </Response>
            """.formatted(
                escapeXml(phone),
                escapeXml(utente.getNome()),
                escapeXml(utente.getNomeCondominio()),
                escapeXml(utente.getId().toString()),
                escapeXml(utente.getIdCondominio().toString())
        );
    }

    private String buildSayResponse(String message) {

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response>
                <Say language="it-IT" voice="%s">%s</Say>
            </Response>
            """.formatted(
                TWILIO_VOICE,
                escapeXml(message)
        );
    }

    private String buildConversazioneOriginale(UserSession session) {

        StringBuilder sb = new StringBuilder();

        if (session == null || session.cronologiaMessaggi == null) {
            return "";
        }

        for (ChatMessage m : session.cronologiaMessaggi) {
            if ("user".equals(m.getRole())) {
                sb.append("Condomino: ");
            } else {
                sb.append("Lucrezia: ");
            }

            sb.append(m.getContent()).append("\n\n");
        }

        return sb.toString().trim();
    }

    private String buildDescrizioneDaCronologia(UserSession session, String ultimoMessaggio) {

        StringBuilder sb = new StringBuilder();

        if (session != null && session.cronologiaMessaggi != null) {
            for (ChatMessage m : session.cronologiaMessaggi) {
                if ("user".equals(m.getRole())) {
                    sb.append(m.getContent()).append(" ");
                }
            }
        }

        if (sb.isEmpty() && ultimoMessaggio != null) {
            sb.append(ultimoMessaggio);
        }

        return sb.toString().trim();
    }

    private void trimHistory(UserSession session) {

        if (session.cronologiaMessaggi != null && session.cronologiaMessaggi.size() > 20) {
            session.cronologiaMessaggi =
                    session.cronologiaMessaggi.subList(
                            session.cronologiaMessaggi.size() - 20,
                            session.cronologiaMessaggi.size()
                    );
        }
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

    private String escapeXml(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
    
    private String buildProcessingRedirectResponse(String message) {

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response>
                <Say language="it-IT" voice="%s">%s</Say>
                <Redirect method="POST">/voice/process</Redirect>
            </Response>
            """.formatted(
                TWILIO_VOICE,
                escapeXml(message)
        );
    }
    
    private String getRandomProcessingMessage() {
        return PROCESSING_MESSAGES.get(ThreadLocalRandom.current().nextInt(PROCESSING_MESSAGES.size()));
    }
    
    private static final List<String> PROCESSING_MESSAGES = List.of(
            "Perfetto, controllo subito.",
            "Va bene, verifico un attimo.",
            "Ho capito, dammi solo qualche secondo.",
            "Ricevuto, controllo subito la situazione.",
            "Grazie, verifico subito i dettagli.",
            "Perfetto, sto controllando.",
            "Va bene, vedo subito come aiutarti.",
            "Ho preso nota, un momento soltanto.",
            "D'accordo, faccio una verifica veloce.",
            "Ok, controllo e ti rispondo subito."
    		);

    private String getRandomClosingMessage() {
        return CLOSING_MESSAGES.get(
                ThreadLocalRandom.current().nextInt(CLOSING_MESSAGES.size())
        );
    }
    
    private static final List<String> CLOSING_MESSAGES = List.of(

    	    "È stato un piacere aiutarla. Per qualsiasi necessità potrà richiamarmi oppure scrivermi tramite WhatsApp. Le auguro una buona giornata.",

    	    "Grazie per avermi contattata. Rimango a disposizione per qualsiasi segnalazione o richiesta relativa al condominio. Buona giornata.",

    	    "Perfetto, ho registrato tutto correttamente. Quando desidera può ricontattarmi telefonicamente oppure tramite WhatsApp. Arrivederci.",

    	    "Sono stata felice di aiutarla. Se dovesse avere altre necessità, potrà contattarmi in qualsiasi momento. Buona giornata.",

    	    "La ringrazio per la chiamata. Rimango a disposizione per qualsiasi esigenza relativa al condominio. Arrivederci.",

    	    "Grazie per le informazioni. Se avrà bisogno di ulteriori aggiornamenti o nuove segnalazioni, potrà sempre contattarmi anche tramite WhatsApp. Buona giornata.",

    	    "Perfetto, la sua richiesta è stata registrata. Rimango a disposizione per qualsiasi altra necessità. Arrivederci.",

    	    "La ringrazio per avermi contattata. Per qualsiasi necessità futura potrà richiamarmi oppure scrivermi su WhatsApp. Buona giornata.",
    	    
    	    "Le auguro una buona giornata e resto a disposizione per qualsiasi necessità.",
    	    
    	    "Sono stata felice di aiutarla. Quando vuole può richiamarmi oppure scrivermi tramite WhatsApp."
    	);
    
    private String getRandomClosingStatusMessage() {
        return CLOSING_STATUS_MESSAGES.get(
                ThreadLocalRandom.current().nextInt(CLOSING_STATUS_MESSAGES.size())
        );
    }
    
    private static final List<String> CLOSING_STATUS_MESSAGES = List.of(
    	    "Spero di esserle stata utile. Per qualsiasi aggiornamento può ricontattarmi quando desidera. Buona giornata.",

    	    "Grazie per avermi contattata. Rimango a disposizione per qualsiasi segnalazione o richiesta sul condominio.",

    	    "Se avrà bisogno di ulteriori aggiornamenti, potrà richiamarmi in qualsiasi momento oppure scrivermi su WhatsApp. Buona giornata.",

    	    "È stato un piacere aiutarla. Se dovesse avere altre necessità, potrà contattarmi in qualsiasi momento.",
    	    
    	    "Sono stata felice di aiutarla. Quando vuole può richiamarmi oppure scrivermi tramite WhatsApp.",
    	    
    	    "Le auguro una buona giornata e resto a disposizione per qualsiasi necessità."
    	);
    
    private String gestisciSceltaTicket(String phone,
    		Utente utente,
    		UserSession session,
    		String speechResult) {

    	TicketChoiceIntent intent = classifyTicketChoice(speechResult);

    	switch (intent) {

    	case STATUS:
    		return handleTicketStatus(phone, session);

    	case NEW_TICKET:
    		session.setVoiceSessionStep(VoiceSessionStep.NEW_TICKET);
    		session.tentativiComprensione = 0;
    		session.cronologiaMessaggi.clear();

    		return buildRecordResponse(
    				"Va bene, mi descriva pure la nuova segnalazione."
    				);

    	default:
    		session.tentativiComprensione++;

    		if (session.tentativiComprensione >= 3) {
    			voiceSessionService.removeSession(phone);
    			return buildSayResponse(
    					"Mi dispiace, non sono riuscita a capire la scelta. La invito a richiamare più tardi."
    					);
    		}

    		return buildRecordResponse(
    				"Mi scusi, non ho capito bene. Vuole sapere lo stato della segnalazione aperta oppure aprirne una nuova?"
    				);
    	}
    }

    private TicketChoiceIntent classifyTicketChoice(String text) {

    	if (text == null || text.isBlank()) {
    		return TicketChoiceIntent.UNKNOWN;
    	}

    	String normalized = text.toLowerCase();

    	if (normalized.contains("stato")
    	        || normalized.contains("aggiornamento")
    	        || normalized.contains("aggiornamenti")
    	        || normalized.contains("a che punto")
    	        || normalized.contains("ticket")
    	        || normalized.contains("segnalazione aperta")
    	        || normalized.contains("come procede")
    	        || normalized.contains("intervento")
    	        || normalized.contains("news")
    	        || normalized.contains("novità")) {
    	    return TicketChoiceIntent.STATUS;
    	}

    	if (normalized.contains("nuova")
    	        || normalized.contains("nuovo")
    	        || normalized.contains("altro problema")
    	        || normalized.contains("aprire")
    	        || normalized.contains("segnalazione")
    	        || normalized.contains("voglio segnalare")
    	        || normalized.contains("devo segnalare")) {
    	    return TicketChoiceIntent.NEW_TICKET;
    	}

    	return TicketChoiceIntent.UNKNOWN;
    }

    private String handleTicketStatus(String phone, UserSession session) {

    	if (session.getOpenTicketIds() == null || session.getOpenTicketIds().isEmpty()) {
    		voiceSessionService.removeSession(phone);
    		return buildSayResponse(
    				"Al momento non risultano segnalazioni aperte a suo nome."
    				);
    	}

    	if (session.getOpenTicketIds().size() > 1) {
    		session.setVoiceSessionStep(VoiceSessionStep.WAIT_TICKET_STATUS_DETAIL);

    		return buildRecordResponse(
    				"Vedo che ha più segnalazioni aperte. "
    						+ "Può dirmi a quale si riferisce? Ad esempio acqua, luce, ascensore, cancello o altro?"
    				);
    	}

    	Long idTicket = session.getOpenTicketIds().get(0);

    	TicketStatusInfo ticket = ticketDao.findTicketStatusById(idTicket);

    	voiceSessionService.removeSession(phone);

    	if (ticket == null) {
    		return buildSayResponse(
    				"Mi dispiace, non sono riuscita a recuperare lo stato della segnalazione."
    				);
    	}

    	return buildSayResponse(buildTicketStatusVoiceMessage(ticket) + " " + getRandomClosingStatusMessage());
    }

    private String buildTicketStatusVoiceMessage(TicketStatusInfo ticket) {

    	String stato = ticket.getStatoDescrizione();

    	if (stato == null || stato.isBlank()) {
    		stato = ticket.getStatoCodice();
    	}

    	if (stato == null || stato.isBlank()) {
    		stato = "in lavorazione";
    	}

    	StringBuilder sb = new StringBuilder();

    	sb.append("La sua segnalazione");

    	if (ticket.getCategoria() != null && !ticket.getCategoria().isBlank()) {
    		sb.append(" relativa a ").append(ticket.getCategoria());
    	}

    	sb.append(" risulta ").append(stato.toLowerCase()).append(". ");

    	if (ticket.getNomeFornitore() != null && !ticket.getNomeFornitore().isBlank()) {
    		sb.append("È stata assegnata al fornitore ")
    		.append(ticket.getNomeFornitore())
    		.append(". ");
    	}

    	if (ticket.getDataInterventoPrevista() != null) {
    		sb.append("L'intervento è previsto per ")
    		.append(formatDate(ticket.getDataInterventoPrevista()))
    		.append(". ");
    	} else {
    		sb.append("Al momento non risulta ancora una data di intervento confermata. ");
    	}

    	sb.append("Riceverà aggiornamenti appena ci saranno novità.");

    	return sb.toString();
    }

    private String formatDate(java.time.LocalDateTime dateTime) {
    	return dateTime.format(
    			java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
    			);
    }
    
}