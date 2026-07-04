package it.sd.lucrezia.ai.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.sd.lucrezia.ai.bean.Utente;
import it.sd.lucrezia.ai.bean.VoiceContext;
import it.sd.lucrezia.ai.dao.TicketConversazioneDao;
import it.sd.lucrezia.ai.dao.TicketDao;
import it.sd.lucrezia.ai.dao.UtenteDao;
import it.sd.lucrezia.ai.service.elevenlabs.ElevenLabsService;
import it.sd.lucrezia.ai.service.voice.VoiceCallContextRegistry;
import it.sd.lucrezia.ai.util.PhoneUtils;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/voice")
@RequiredArgsConstructor
public class VoiceController {

    @Value("${voice.elevenlabs.beta-phone}")
    private String elevenLabsBetaPhone;
    
	private final UtenteDao utenteDao;
	private final TicketDao ticketDao;
    private final TicketConversazioneDao ticketConversazioneDao;
    private final PhoneUtils phoneUtils;
    private final VoiceCallContextRegistry voiceCallContextRegistry;
    private final ElevenLabsService elevenLabsService;

    @RequestMapping(
            value = "/incoming",
            method = {RequestMethod.GET, RequestMethod.POST},
            produces = "application/xml"
    )
    public String incomingCall(@RequestParam(value = "From", required = false) String from, @RequestParam(value = "To", required = false) String to) {

        try {
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
            
            if (phone.equals(elevenLabsBetaPhone)) {
                System.out.println("ROUTING VOICE ENGINE = ELEVENLABS");
                int ticketAperti = ticketDao.findOpenTicketsByUtente(utente.getId()).size();

                return elevenLabsService.registerInboundCall(from, to, utente ,ticketAperti);
            }

            return buildRealtimeConnectResponse(utente, phone);

        } catch (Exception e) {
            e.printStackTrace();

            return buildSayResponse(
                    "Mi dispiace, al momento Lucrezia non è disponibile. La invitiamo a riprovare più tardi."
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
                    <Say language="it-IT" voice="alice">%s</Say>
                </Response>
                """.formatted(message);
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