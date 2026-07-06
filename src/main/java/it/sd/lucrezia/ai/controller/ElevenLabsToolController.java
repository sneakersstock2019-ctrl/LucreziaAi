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
}