package it.sd.demo.bot.condomini.controller;

import java.io.File;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.sd.demo.bot.condomini.bean.AIResponse;
import it.sd.demo.bot.condomini.bean.ChatMessage;
import it.sd.demo.bot.condomini.bean.UserSession;
import it.sd.demo.bot.condomini.bean.Utente;
import it.sd.demo.bot.condomini.dao.CondominioAiDao;
import it.sd.demo.bot.condomini.dao.TicketConversazioneDao;
import it.sd.demo.bot.condomini.dao.TicketDao;
import it.sd.demo.bot.condomini.dao.UtenteDao;
import it.sd.demo.bot.condomini.service.OpenAIService;
import it.sd.demo.bot.condomini.service.TwilioService;
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

    @RequestMapping(
            value = "/incoming",
            method = {RequestMethod.GET, RequestMethod.POST},
            produces = "application/xml"
    )
    public String incomingCall(@RequestParam(value = "From", required = false) String from) {

        String phone = phoneUtils.normalizePhone(from);

        Utente utente = utenteDao.findCondominoByTelefono(phone);

        if (utente == null) {
            return buildSayResponse(
                    "Buongiorno, sono Lucrezia. Il numero da cui sta chiamando non risulta abilitato al servizio."
            );
        }

        UserSession session = voiceSessionService.getOrCreateVoiceSession(phone);
        session.nome = utente.getNome();
        session.step = "VOICE";
        session.primoMessaggio = true;
        session.tentativiComprensione = 0;
        session.cronologiaMessaggi.clear();

        return buildRecordResponse(
                "Buongiorno " + utente.getNome() +
                ", sono Lucrezia. Vedo che sta chiamando per il condominio " +
                utente.getNomeCondominio() +
                ". Mi descriva pure il problema."
        );
    }

    @PostMapping(value = "/gather", produces = "application/xml")
    public String gather(@RequestParam(value = "SpeechResult", required = false) String speechResult,
                         @RequestParam(value = "From", required = false) String from) {

        String phone = phoneUtils.normalizePhone(from);

        Utente utente = utenteDao.findCondominoByTelefono(phone);

        if (utente == null) {
            voiceSessionService.removeSession(phone);
            return buildSayResponse("Mi dispiace, il numero non risulta abilitato al servizio.");
        }

        UserSession session = voiceSessionService.getOrCreateVoiceSession(phone);
        session.nome = utente.getNome();

        if (speechResult == null || speechResult.isBlank()) {
            session.tentativiComprensione++;

            if (session.tentativiComprensione >= 3) {
                voiceSessionService.removeSession(phone);
                return buildSayResponse("Mi dispiace, non sono riuscita a capire bene. La invito a richiamare più tardi.");
            }

            return buildRecordResponse("Mi scusi, non ho capito bene. Può ripetere il problema con poche parole?");
        }

        session.cronologiaMessaggi.add(new ChatMessage("user", speechResult));

        String contestoCondominio =
                condominioAiDao.getContestoAiByCondominio(utente.getIdCondominio());

        AIResponse aiResponse =
                openAIService.askLucreziaVoice(
                        speechResult,
                        session,
                        utente,
                        contestoCondominio
                );

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

        Long idTicket = ticketDao.insertTicket(
                utente.getIdCondominio(),
                utente.getId(),
                categoria,
                priorita,
                "TELEFONO",
                descrizioneTicket
        );

        if (idTicket == null) {
            return buildSayResponse(
                    "Mi dispiace, ho capito la segnalazione ma non sono riuscita ad aprire il ticket."
            );
        }

        ticketConversazioneDao.insertConversazione(
                idTicket,
                "TELEFONO",
                "TRASCRIZIONE",
                buildConversazioneOriginale(session),
                null
        );

        voiceSessionService.removeSession(phone);

        return buildSayResponse(
                "Perfetto, grazie " + utente.getNome() +
                ". Ho aperto il ticket numero " + idTicket +
                " per il condominio " + utente.getNomeCondominio() +
                ". Categoria " + categoria +
                ", priorità " + priorita +
                ". Riceverà aggiornamenti appena possibile."
        );
    }
    
    @PostMapping(value = "/recording", produces = "application/xml")
    public String recording(@RequestParam("RecordingUrl") String recordingUrl,
                            @RequestParam(value = "From", required = false) String from) {

        String phone = phoneUtils.normalizePhone(from);
        Utente utente = utenteDao.findCondominoByTelefono(phone);

        if (utente == null) {
            return buildSayResponse("Mi dispiace, il numero non risulta abilitato al servizio.");
        }

        UserSession session = voiceSessionService.getOrCreateVoiceSession(phone);
        session.nome = utente.getNome();
        session.registrazioniAudio.add(recordingUrl + ".mp3");

        try {
            File audioFile = twilioService.downloadRecording(recordingUrl);
            String speechResult = openAIService.transcribeAudio(audioFile);

            return gestisciRispostaVocale(phone, utente, session, speechResult);

        } catch (Exception e) {
            e.printStackTrace();
            return buildRecordResponse("Mi scusi, non sono riuscita a capire bene. Può ripetere il problema?");
        }
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
        }

        return idTicket;
    }

    private String buildRecordResponse(String message) {

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response>
                <Say language="it-IT" voice="%s">
                    %s
                </Say>
                <Record action="/voice/recording"
                        method="POST"
                        maxLength="30"
                        playBeep="false"
                        trim="trim-silence" />
            </Response>
            """.formatted(
                TWILIO_VOICE,
                escapeXml(message)
        );
    }

    private String buildSayResponse(String message) {

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response>
                <Say language="it-IT" voice="%s">
                    %s
                </Say>
            </Response>
            """.formatted(TWILIO_VOICE, escapeXml(message));
    }

    private String buildConversazioneOriginale(UserSession session) {

        StringBuilder sb = new StringBuilder();

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

        for (ChatMessage m : session.cronologiaMessaggi) {
            if ("user".equals(m.getRole())) {
                sb.append(m.getContent()).append(" ");
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
    
    private String gestisciRispostaVocale(String phone,
    		Utente utente,
    		UserSession session,
    		String speechResult) {

    	session.cronologiaMessaggi.add(new ChatMessage("user", speechResult));

    	String contestoCondominio =
    			condominioAiDao.getContestoAiByCondominio(utente.getIdCondominio());

    	AIResponse aiResponse =
    			openAIService.askLucreziaVoice(
    					speechResult,
    					session,
    					utente,
    					contestoCondominio
    					);

    	String reply = aiResponse.getReply();

    	if (reply == null || reply.isBlank()) {
    		reply = "Mi scusi, può ripetere meglio il problema?";
    	}

    	session.cronologiaMessaggi.add(new ChatMessage("assistant", reply));

    	if (!aiResponse.isOpenTicket()) {
    		return buildRecordResponse(reply);
    	}

    	Long idTicket = ticketDao.insertTicket(
    			utente.getIdCondominio(),
    			utente.getId(),
    			normalizeCategoria(aiResponse.getCategory()),
    			normalizePriorita(aiResponse.getPriority()),
    			"TELEFONO",
    			aiResponse.getTicketDescription()
    			);

    	ticketConversazioneDao.insertConversazione(
    			idTicket,
    			"TELEFONO",
    			"TRASCRIZIONE",
    			buildConversazioneOriginale(session),
    			null
    			);

    	for (String urlAudio : session.registrazioniAudio) {
    		ticketConversazioneDao.insertConversazione(
    				idTicket,
    				"TELEFONO",
    				"AUDIO",
    				null,
    				urlAudio
    				);
    	}

    	voiceSessionService.removeSession(phone);

    	return buildSayResponse(
    			"Perfetto, grazie " + utente.getNome() +
    			". Ho aperto il ticket numero " + idTicket +
    			". Riceverà aggiornamenti appena possibile."
    			);
    }
}