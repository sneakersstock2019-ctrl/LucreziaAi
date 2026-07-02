package it.sd.lucrezia.ai.tool;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.lucrezia.ai.bean.VoiceContext;
import it.sd.lucrezia.ai.dao.TelefonataDao;
import it.sd.lucrezia.ai.dao.TicketConversazioneDao;
import it.sd.lucrezia.ai.dao.TicketDao;
import it.sd.lucrezia.ai.util.CallLogger;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CreateTicketTool implements LucreziaTool {

    @Value("${twilio.account-sid}")
    private String accountSid;
    
    private final TicketDao ticketDao;
    private final TicketConversazioneDao ticketConversazioneDao;
    private final TelefonataDao telefonataDao;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "createTicket";
    }

    @Override
    public String execute(String arguments, VoiceContext voiceContext) {
    	CallLogger.info(voiceContext, "TOOL createTicket - arguments=" + arguments);

        try {
            JsonNode root = objectMapper.readTree(arguments);

            String categoria = safe(root.path("categoria").asText());
            String priorita = safe(root.path("priorita").asText());
            String descrizione = safe(root.path("descrizione").asText());
            String area = safe(root.path("area").asText());

            if (descrizione.isBlank()) {
                return objectMapper.writeValueAsString(Map.of(
                        "esito", "MANCANO_INFORMAZIONI",
                        "campo", "descrizione",
                        "messaggio", "Manca la descrizione del problema."
                ));
            }

            if (area.isBlank()) {
                area = inferAreaFromDescription(descrizione);
            }

            if (area.isBlank()) {
                return objectMapper.writeValueAsString(Map.of(
                        "esito", "MANCANO_INFORMAZIONI",
                        "campo", "area",
                        "messaggio", "Manca l'indicazione se il problema riguarda una parte comune o privata."
                ));
            }

            if (categoria.isBlank()) {
                categoria = "generico";
            }

            if (priorita.isBlank()) {
                priorita = "media";
            }

            if (!priorita.equals("bassa") && !priorita.equals("media") && !priorita.equals("alta")) {
                priorita = "media";
            }

            String descrizioneCompleta = descrizione + " Area: " + area + ".";

            Long ticketId = ticketDao.insertTicket(
                    voiceContext.getIdCondominio(),
                    voiceContext.getIdUtente(),
                    categoria,
                    priorita,
                    "TELEFONO",
                    descrizioneCompleta
            );
            CallLogger.info(voiceContext, "Ticket creato - idTicket=" + ticketId);
            
            ticketConversazioneDao.insertConversazione(
                    ticketId,
                    "TELEFONO",
                    "AUDIO",
                    voiceContext.getTrascrizioneChiamata(),
                    null
            );
            
            voiceContext.setIdTicketCreato(ticketId);
            voiceContext.setEsitoTelefonata("TICKET_APERTO");
            voiceContext.setMotivoChiusura("TICKET_APERTO");

            telefonataDao.updateTicket(voiceContext.getIdTelefonata(), ticketId, voiceContext.getCallSid());
            
            if (voiceContext.getRecordingSid() != null && !voiceContext.getRecordingSid().isBlank()) {

                String audioUrl =
                        "https://api.twilio.com/2010-04-01/Accounts/"
                        + accountSid
                        + "/Recordings/"
                        + voiceContext.getRecordingSid()
                        + ".mp3";

                ticketConversazioneDao.updateAudioUrlByTicket(ticketId, audioUrl);

                CallLogger.info(voiceContext, "contextAudio registrazione associato al ticket "
                        + ticketId
                        + " = " + audioUrl);
            }
            
            boolean richiediFoto = shouldRequestPhoto(categoria, descrizioneCompleta);
            CallLogger.info(voiceContext, "Ticket creato - richiediFoto=" + richiediFoto);

            if (ticketId == null) {
                return objectMapper.writeValueAsString(Map.of(
                        "esito", "ERRORE",
                        "messaggio", "Non sono riuscita ad aprire la segnalazione."
                ));
            }

            return objectMapper.writeValueAsString(Map.of(
                    "esito", "OK",
                    "ticket_id", ticketId,
                    "categoria", categoria,
                    "priorita", priorita,
                    "descrizione", descrizioneCompleta,
                    "richiedi_foto", richiediFoto,
                    "messaggio", "Segnalazione aperta correttamente.",
                    "next_action", "ASK_IF_NEEDS_MORE_HELP"
            ));

        } catch (Exception e) {
            e.printStackTrace();

            try {
                return objectMapper.writeValueAsString(Map.of(
                        "esito", "ERRORE",
                        "messaggio", "Errore tecnico durante l'apertura della segnalazione."
                ));
            } catch (Exception ex) {
                return "{\"esito\":\"ERRORE\"}";
            }
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
    
    private String inferAreaFromDescription(String descrizione) {

        if (descrizione == null) {
            return "";
        }

        String text = descrizione.toLowerCase();

        if (text.contains("parte comune")
                || text.contains("area comune")
                || text.contains("condominiale")
                || text.contains("condominio")
                || text.contains("scale")
                || text.contains("ascensore")
                || text.contains("garage comune")
                || text.contains("androne")) {
            return "comune";
        }

        if (text.contains("privata")
                || text.contains("appartamento")
                || text.contains("casa mia")
                || text.contains("box privato")
                || text.contains("cantina privata")) {
            return "privata";
        }

        return "";
    }
    
    private boolean shouldRequestPhoto(String categoria, String descrizione) {

        String text = ((categoria == null ? "" : categoria) + " " +
                       (descrizione == null ? "" : descrizione)).toLowerCase();

        return text.contains("infiltrazione")
                || text.contains("perdita")
                || text.contains("umidità")
                || text.contains("muro")
                || text.contains("facciata")
                || text.contains("crepa")
                || text.contains("danno")
                || text.contains("sporco")
                || text.contains("rotto")
                || text.contains("guasto luce")
                || text.contains("elettricista")
                || text.contains("idraulico");
    }
}