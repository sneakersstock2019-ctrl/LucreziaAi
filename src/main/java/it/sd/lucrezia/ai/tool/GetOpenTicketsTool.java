package it.sd.lucrezia.ai.tool;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.lucrezia.ai.bean.TicketStatusInfo;
import it.sd.lucrezia.ai.bean.VoiceContext;
import it.sd.lucrezia.ai.dao.TelefonataDao;
import it.sd.lucrezia.ai.dao.TicketDao;
import it.sd.lucrezia.ai.util.CallLogger;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GetOpenTicketsTool implements LucreziaTool {

    private final TicketDao ticketDao;
    private final TelefonataDao telefonataDao;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "getOpenTickets";
    }

    @Override
    public String execute(String arguments, VoiceContext voiceContext) {

        try {
        	CallLogger.info(voiceContext, "TOOL getOpenTickets");
        	
        	voiceContext.setEsitoTelefonata("STATO_TICKET");
        	voiceContext.setMotivoChiusura("STATO_TICKET");
        	telefonataDao.updateEsito(voiceContext.getIdTelefonata(), "STATO_TICKET", "STATO_TICKET", voiceContext.getCallSid());
        	
            List<TicketStatusInfo> tickets = ticketDao.findOpenTicketsByUtente(voiceContext.getIdUtente());
            CallLogger.info(voiceContext, "Ticket aperti recuperati=" + tickets.size());

            List<Map<String, Object>> ticketJson = tickets.stream()
                    .map(t -> Map.<String, Object>of(
                            "id", t.getId(),
                            "categoria", safe(t.getCategoria()),
                            "priorita", safe(t.getPriorita()),
                            "stato_codice", safe(t.getStatoCodice()),
                            "stato_descrizione", safe(t.getStatoDescrizione()),
                            "descrizione", safe(t.getDescrizione()),
                            "fornitore", safe(t.getNomeFornitore()),
                            "next_action", "ASK_IF_NEEDS_MORE_HELP",
                            "data_ultimo_aggiornamento",
                            t.getDataUltimoAggiornamento() == null ? "" : t.getDataUltimoAggiornamento().toString(),
                            "data_intervento_prevista",
                            t.getDataInterventoPrevista() == null ? "" : t.getDataInterventoPrevista().toLocalDate().toString()
                    ))
                    .toList();

            return objectMapper.writeValueAsString(
                    Map.of(
                            "numero_ticket_aperti", ticketJson.size(),
                            "ticket", ticketJson
                    )
            );

        } catch (Exception e) {
            e.printStackTrace();
            return """
                {"errore":true,"messaggio":"Non sono riuscita a recuperare le segnalazioni aperte."}
                """;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}