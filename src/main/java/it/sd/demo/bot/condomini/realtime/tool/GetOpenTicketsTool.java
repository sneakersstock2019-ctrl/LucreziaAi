package it.sd.demo.bot.condomini.realtime.tool;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.demo.bot.condomini.bean.TicketStatusInfo;
import it.sd.demo.bot.condomini.bean.VoiceContext;
import it.sd.demo.bot.condomini.dao.TelefonataDao;
import it.sd.demo.bot.condomini.dao.TicketDao;
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
    public String execute(String arguments, VoiceContext context) {

        try {
        	context.setEsitoTelefonata("STATO_TICKET");
        	context.setMotivoChiusura("STATO_TICKET");
        	telefonataDao.updateEsito(
        	        context.getIdTelefonata(),
        	        "STATO_TICKET",
        	        "STATO_TICKET"
        	);
        	
            List<TicketStatusInfo> tickets =
                    ticketDao.findOpenTicketsByUtente(context.getIdUtente());

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