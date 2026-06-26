package it.sd.demo.bot.condomini.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.demo.bot.condomini.bean.TicketStatusInfo;
import it.sd.demo.bot.condomini.dao.TicketDao;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LucreziaRealtimeToolService {

    private final TicketDao ticketDao;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getOpenTicketsJson(Long idUtente) {

        try {
            List<TicketStatusInfo> tickets = ticketDao.findOpenTicketsByUtente(idUtente);

            List<Map<String, Object>> ticketJson = tickets.stream()
                    .map(t -> Map.<String, Object>of(
                            "id", t.getId(),
                            "categoria", safe(t.getCategoria()),
                            "priorita", safe(t.getPriorita()),
                            "stato_codice", safe(t.getStatoCodice()),
                            "stato_descrizione", safe(t.getStatoDescrizione()),
                            "descrizione", safe(t.getDescrizione()),
                            "fornitore", safe(t.getNomeFornitore()),
                            "data_ultimo_aggiornamento",
                                    t.getDataUltimoAggiornamento() == null
                                            ? ""
                                            : t.getDataUltimoAggiornamento().toString(),
                            "data_intervento_prevista",
                                    t.getDataInterventoPrevista() == null
                                            ? ""
                                            : t.getDataInterventoPrevista().toLocalDate().toString()
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

            try {
                return objectMapper.writeValueAsString(
                        Map.of(
                                "errore", true,
                                "messaggio", "Non sono riuscita a recuperare le segnalazioni aperte."
                        )
                );
            } catch (Exception ex) {
                return "{\"errore\":true}";
            }
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}