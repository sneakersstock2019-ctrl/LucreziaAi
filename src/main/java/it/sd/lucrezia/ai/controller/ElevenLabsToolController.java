package it.sd.lucrezia.ai.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import it.sd.lucrezia.ai.bean.TicketStatusInfo;
import it.sd.lucrezia.ai.dao.TicketDao;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/elevenlabs/tool")
@RequiredArgsConstructor
public class ElevenLabsToolController {

    private final TicketDao ticketDao;

    @PostMapping("/getOpenTickets")
    public Map<String, Object> getOpenTickets(@RequestBody Map<String, Object> body) {

        System.out.println("############################");
        System.out.println("ELEVENLABS TOOL: getOpenTickets");
        System.out.println("BODY = " + body);
        System.out.println("############################");

        Long idUtente = Long.valueOf(String.valueOf(body.get("id_utente")));

        List<TicketStatusInfo> tickets = ticketDao.findOpenTicketsByUtente(idUtente);

        return Map.of(
                "success", true,
                "count", tickets.size(),
                "tickets", tickets
        );
    }
    
    @PostMapping("/createTicket")
    public Map<String, Object> createTicket(@RequestBody Map<String, Object> body) {

        Long idUtente = Long.valueOf(String.valueOf(body.get("id_utente")));
        Long idCondominio = Long.valueOf(String.valueOf(body.get("id_condominio")));

        String categoria = safe(body.get("categoria"));
        String priorita = safe(body.get("priorita"));
        String area = safe(body.get("area"));
        String descrizione = safe(body.get("descrizione"));

        if (descrizione.isBlank() || categoria.isBlank() || priorita.isBlank() || area.isBlank()) {
            return Map.of(
                    "success", false,
                    "message", "Mancano informazioni obbligatorie per aprire la segnalazione."
            );
        }

        Long ticketId = ticketDao.insertTicket(
                idCondominio,
                idUtente,
                categoria,
                priorita,
                "TELEFONO",
                descrizione + " Area: " + area + "."
        );

        return Map.of(
                "success", true,
                "ticket_id", ticketId,
                "categoria", categoria,
                "priorita", priorita,
                "area", area,
                "message", "Segnalazione aperta correttamente."
        );
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase();
    }
}