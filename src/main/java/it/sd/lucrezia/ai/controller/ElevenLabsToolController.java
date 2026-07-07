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

        System.out.println("############################");
        System.out.println("ELEVENLABS TOOL: createTicket");
        System.out.println("BODY = " + body);
        System.out.println("############################");

        Long idUtente = Long.valueOf(String.valueOf(body.get("id_utente")));
        Long idCondominio = Long.valueOf(String.valueOf(body.get("id_condominio")));

        String problema = String.valueOf(body.getOrDefault("problema", ""));
        String luogo = String.valueOf(body.getOrDefault("luogo", ""));
        String urgenza = String.valueOf(body.getOrDefault("urgenza", ""));
        String note = String.valueOf(body.getOrDefault("note", ""));

        String descrizione = """
                Problema: %s
                Luogo: %s
                Urgenza riferita: %s
                Note: %s
                """.formatted(problema, luogo, urgenza, note);

        // TODO qui colleghiamo il tuo metodo reale di creazione ticket
        // Long idTicket = ticketDao.createTicket(...);

        return Map.of(
                "success", true,
                "message", "Segnalazione ricevuta correttamente.",
                "problema", problema,
                "luogo", luogo,
                "urgenza", urgenza,
                "descrizione", descrizione
        );
    }
}