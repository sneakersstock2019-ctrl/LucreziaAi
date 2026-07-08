package it.sd.lucrezia.ai.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.sd.lucrezia.ai.bean.TicketStatusInfo;
import it.sd.lucrezia.ai.bean.tool.ToolNextAction;
import it.sd.lucrezia.ai.bean.tool.ToolResult;
import it.sd.lucrezia.ai.dao.TelefonataDao;
import it.sd.lucrezia.ai.dao.TicketDao;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/elevenlabs/tool")
@RequiredArgsConstructor
public class ElevenLabsToolController {

    private static final String CANALE_TELEFONO = "TELEFONO";

    private final TicketDao ticketDao;
    private final TelefonataDao telefonataDao;

    @PostMapping("/getOpenTickets")
    public ToolResult<Map<String, Object>> getOpenTickets(@RequestBody Map<String, Object> body) {

        logTool("getOpenTickets", body);

        Long idUtente = getLong(body, "id_utente");

        if (idUtente == null) {
            return missingField("id_utente");
        }

        List<TicketStatusInfo> tickets = ticketDao.findOpenTicketsByUtente(idUtente);

        return ToolResult.ok(
                "OK",
                ToolNextAction.ASK_IF_NEEDS_MORE_HELP,
                Map.of(
                        "count", tickets.size(),
                        "tickets", tickets
                )
        );
    }

    @PostMapping("/createTicket")
    public ToolResult<Map<String, Object>> createTicket(@RequestBody Map<String, Object> body) {

        logTool("createTicket", body);

        Long idUtente = getLong(body, "id_utente");
        Long idCondominio = getLong(body, "id_condominio");
        String callSid = safeRaw(body.get("call_sid"));

        String categoria = safe(body.get("categoria"));
        String priorita = safe(body.get("priorita"));
        String area = safe(body.get("area"));
        String descrizione = safe(body.get("descrizione"));

        if (idUtente == null) {
            return missingField("id_utente");
        }

        if (idCondominio == null) {
            return missingField("id_condominio");
        }

        if (categoria.isBlank()) {
            return missingField("categoria");
        }

        if (priorita.isBlank()) {
            return missingField("priorita");
        }

        if (area.isBlank()) {
            return missingField("area");
        }

        if (descrizione.isBlank()) {
            return missingField("descrizione");
        }

        Long ticketId = ticketDao.insertTicket(
                idCondominio,
                idUtente,
                categoria,
                priorita,
                CANALE_TELEFONO,
                descrizione + " Area: " + area + "."
        );
        boolean richiediFoto = shouldRequestPhoto(categoria, descrizione);
        
        telefonataDao.updateTicketByCallSid(callSid, ticketId);

        return ToolResult.ok(
                "OK",
                richiediFoto
                        ? ToolNextAction.SEND_WHATSAPP_PHOTO
                        : ToolNextAction.ASK_IF_NEEDS_MORE_HELP,
                Map.of(
                        "ticket_id", ticketId,
                        "categoria", categoria,
                        "priorita", priorita,
                        "area", area,
                        "richiedi_foto", richiediFoto
                )
        );
    }

    private ToolResult<Map<String, Object>> missingField(String field) {
        return ToolResult.error(
                "KO",
                ToolNextAction.ASK_FOR_MISSING_INFORMATION,
                Map.of(
                        "missing_field", field
                )
        );
    }

    private Long getLong(Map<String, Object> body, String key) {

        Object value = body.get(key);

        if (value == null) {
            return null;
        }

        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase();
    }

    private boolean shouldRequestPhoto(String categoria, String descrizione) {

        String text = (safe(categoria) + " " + safe(descrizione)).toLowerCase();

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

    private void logTool(String toolName, Map<String, Object> body) {

        System.out.println("############################");
        System.out.println("ELEVENLABS TOOL: " + toolName);
        System.out.println("BODY = " + body);
        System.out.println("############################");
    }
    
    private String safeRaw(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}