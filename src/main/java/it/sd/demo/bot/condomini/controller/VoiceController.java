package it.sd.demo.bot.condomini.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import it.sd.demo.bot.condomini.bean.AIResponse;
import it.sd.demo.bot.condomini.bean.ChatMessage;
import it.sd.demo.bot.condomini.bean.UserSession;
import it.sd.demo.bot.condomini.service.OpenAIService;
import it.sd.demo.bot.condomini.service.VoiceSessionService;

@RestController
@RequestMapping("/voice")
public class VoiceController {

    private static final String TWILIO_VOICE = "Polly.Bianca-Neural";
    
    private final Map<String, String> utenti = Map.of(
            "+393492123304", "Salvatore",
            "+393282036763", "Marta",
            "+393382702339", "Renato"
    );

    @Autowired
    private OpenAIService openAIService;

    @Autowired
    private VoiceSessionService voiceSessionService;

    @RequestMapping(
            value = "/incoming",
            method = {RequestMethod.GET, RequestMethod.POST},
            produces = "application/xml"
    )
    public String incomingCall(@RequestParam(value = "From", required = false) String from) {

        System.out.println("############################");
        System.out.println("TWILIO INCOMING CALL");
        System.out.println("FROM = " + from);
        System.out.println("############################");

        UserSession session = voiceSessionService.getOrCreateVoiceSession(normalizePhone(from));
        session.primoMessaggio = true;

        return buildGatherResponse(
                "Buongiorno " + utenti.get(from) + ", sono Lucrezia. Mi descriva pure il problema e la aiuterò ad aprire una segnalazione."
        );
    }

    @PostMapping(value = "/gather", produces = "application/xml")
    public String gather(@RequestParam(value = "SpeechResult", required = false) String speechResult,
                         @RequestParam(value = "From", required = false) String from) {

        String phone = normalizePhone(from);

        System.out.println("############################");
        System.out.println("TWILIO SPEECH RESULT");
        System.out.println("FROM = " + phone);
        System.out.println("SpeechResult = " + speechResult);
        System.out.println("############################");

        UserSession session = voiceSessionService.getOrCreateVoiceSession(phone);

        if (speechResult == null || speechResult.isBlank()) {
            session.tentativiComprensione++;

            if (session.tentativiComprensione >= 3) {
                voiceSessionService.removeSession(phone);

                return buildSayResponse(
                        "Mi dispiace, non sono riuscita a capire bene la richiesta. La invito a richiamare più tardi oppure a inviare un messaggio su WhatsApp."
                );
            }

            return buildGatherResponse(
                    "Mi scusi, non ho capito bene. Può ripetere il problema con poche parole?"
            );
        }

        session.cronologiaMessaggi.add(
                new ChatMessage("user", speechResult)
        );

        AIResponse aiResponse = openAIService.askLucrezia(speechResult, session);

        String reply = aiResponse.getReply();

        if (reply == null || reply.isBlank()) {
            reply = "Mi scusi, ho avuto un problema nel capire la richiesta. Può ripetere?";
        }

        session.cronologiaMessaggi.add(
                new ChatMessage("assistant", reply)
        );

        session.primoMessaggio = false;

        trimHistory(session);

        if (!aiResponse.isOpen_ticket()) {
            session.tentativiComprensione++;

            if (session.tentativiComprensione >= 3) {
                voiceSessionService.removeSession(phone);

                return buildSayResponse(
                        "Grazie, ho raccolto le informazioni principali. Per non farle perdere altro tempo, aprirò una segnalazione generica e verrà ricontattato se serviranno ulteriori dettagli."
                );
            }

            return buildGatherResponse(reply);
        }

        /*
         * TODO apertura ticket reale.
         *
         * Esempio:
         *
         * Ticket ticket = ticketService.createTicket(
         *      phone,
         *      speechResult,
         *      aiResponse.getCategory(),
         *      aiResponse.getPriority(),
         *      "APERTO"
         * );
         */

        voiceSessionService.removeSession(phone);

        String categoria = safe(aiResponse.getCategory(), "generico");
        String priorita = safe(aiResponse.getPriority(), "media");

        return buildSayResponse(
                "Perfetto, grazie. Ho raccolto le informazioni necessarie e ho aperto una segnalazione di categoria "
                        + categoria
                        + " con priorità "
                        + priorita
                        + ". Riceverà aggiornamenti appena possibile."
        );
    }

    private String buildGatherResponse(String message) {

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response>
                <Gather input="speech"
                        language="it-IT"
                        speechTimeout="auto"
                        timeout="7"
                        action="/voice/gather"
                        method="POST">
                    <Say language="it-IT" voice="%s">
                        %s
                    </Say>
                </Gather>

                <Say language="it-IT" voice="%s">
                    Non ho sentito nessuna risposta. Può richiamarmi quando vuole.
                </Say>
            </Response>
            """.formatted(
                TWILIO_VOICE,
                escapeXml(message),
                TWILIO_VOICE
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
            """.formatted(
                TWILIO_VOICE,
                escapeXml(message)
        );
    }

    private String normalizePhone(String phone) {

        if (phone == null || phone.isBlank()) {
            return "UNKNOWN";
        }

        return phone.replace("+", "").trim();
    }

    private String safe(String value, String defaultValue) {

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
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
}