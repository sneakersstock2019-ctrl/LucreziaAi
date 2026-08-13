package it.sd.lucrezia.ai.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.sd.lucrezia.ai.bean.CreatePendingTicketRequest;
import it.sd.lucrezia.ai.bean.CreatePendingTicketResponse;
import it.sd.lucrezia.ai.bean.FindRegisteredUserRequest;
import it.sd.lucrezia.ai.bean.FindRegisteredUserResponse;
import it.sd.lucrezia.ai.bean.ManageUserApprovalRequest;
import it.sd.lucrezia.ai.bean.ManageUserApprovalResponse;
import it.sd.lucrezia.ai.bean.RetryOutboundResult;
import it.sd.lucrezia.ai.bean.SendApprovalRequest;
import it.sd.lucrezia.ai.bean.SendApprovalResponse;
import it.sd.lucrezia.ai.bean.TicketStatusInfo;
import it.sd.lucrezia.ai.bean.ToolNextAction;
import it.sd.lucrezia.ai.bean.ToolResult;
import it.sd.lucrezia.ai.bean.Utente;
import it.sd.lucrezia.ai.dao.FornitoreOutboundToolDao;
import it.sd.lucrezia.ai.dao.TelefonataDao;
import it.sd.lucrezia.ai.dao.TicketDao;
import it.sd.lucrezia.ai.dao.UtenteDao;
import it.sd.lucrezia.ai.service.voice.UnknownUserApprovalService;
import it.sd.lucrezia.ai.service.voice.UnknownUserService;
import it.sd.lucrezia.ai.service.voice.UnknownUserTicketService;
import it.sd.lucrezia.ai.service.voice.UserApprovalService;
import it.sd.lucrezia.ai.service.whatsapp.WhatsAppService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/elevenlabs/tool")
@RequiredArgsConstructor
public class ElevenLabsToolController {

	private static final String CANALE_TELEFONO = "TELEFONO";
	
    @Value("${lucrezia.numero-voce}")
    private String numeroLucrezia;
    

    private final UtenteDao utenteDao;
    private final TicketDao ticketDao;
    private final TelefonataDao telefonataDao;
    private final FornitoreOutboundToolDao fornitoreOutboundToolDao;
    private final UnknownUserService unknownUserService;
    private final UserApprovalService userApprovalService;
    private final UnknownUserApprovalService unknownUserApprovalService;
    private final UnknownUserTicketService unknownUserTicketService;
    private final WhatsAppService whatsAppService;

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
    public ToolResult<Map<String, Object>> createTicket(
            @RequestBody Map<String, Object> body) {

        logTool(
                "createTicket",
                body
        );

        Long idUtente =
                getLong(
                        body,
                        "id_utente"
                );

        Long idCondominio =
                getLong(
                        body,
                        "id_condominio"
                );

        String callSid =
                safeRaw(
                        body.get("call_sid")
                );

        String categoria =
                safe(
                        body.get("categoria")
                );

        String priorita =
                safe(
                        body.get("priorita")
                );

        String area =
                safe(
                        body.get("area")
                );

        String descrizione =
                safe(
                        body.get("descrizione")
                );

        if (idUtente == null) {
            return missingField(
                    "id_utente"
            );
        }

        if (idCondominio == null) {
            return missingField(
                    "id_condominio"
            );
        }

        if (categoria.isBlank()) {
            return missingField(
                    "categoria"
            );
        }

        if (priorita.isBlank()) {
            return missingField(
                    "priorita"
            );
        }

        if (area.isBlank()) {
            return missingField(
                    "area"
            );
        }

        if (descrizione.isBlank()) {
            return missingField(
                    "descrizione"
            );
        }

        /*
         * ============================================================
         * CREAZIONE TICKET
         * ============================================================
         */

        Long ticketId =
                ticketDao.insertTicket(
                        idCondominio,
                        idUtente,
                        categoria,
                        priorita,
                        CANALE_TELEFONO,
                        descrizione
                                + " Area: "
                                + area
                                + "."
                );

        if (ticketId == null) {

            return ToolResult.error(
                    "TICKET_CREATE_ERROR",
                    "Non è stato possibile creare la segnalazione.",
                    null
            );
        }

        /*
         * Associazione ticket alla telefonata.
         */
        telefonataDao.updateTicketByCallSid(
                callSid,
                ticketId
        );

        /*
         * ============================================================
         * TEMPLATE WHATSAPP
         * ============================================================
         */

        try {

            Utente utente =
                    utenteDao.findById(
                            idUtente
                    );

            if (utente != null) {

                boolean templateInviato =
                        whatsAppService
                                .inviaTemplateSegnalazioneAperta(
                                        utente,
                                        ticketId,
                                        descrizione,
                                        numeroLucrezia
                                );

                System.out.println(
                        "TEMPLATE segnalazione_aperta"
                                + " - ticket="
                                + ticketId
                                + " - inviato="
                                + templateInviato
                );
            }

        } catch (Exception e) {

            /*
             * Il ticket è già stato creato.
             * Un errore WhatsApp non deve far fallire il tool.
             */
            System.err.println(
                    "Ticket "
                            + ticketId
                            + " creato correttamente, "
                            + "ma invio template WhatsApp fallito: "
                            + e.getMessage()
            );

            e.printStackTrace();
        }

        /*
         * ============================================================
         * FOTO
         * ============================================================
         */

        boolean richiediFoto =
                shouldRequestPhoto(
                        categoria,
                        descrizione
                );

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
    
    @PostMapping("/endCall")
    public ToolResult<Map<String, Object>> endCall(@RequestBody Map<String, Object> body) {

        logTool("endCall", body);

        Long idTelefonata = getLong(body, "id_telefonata");
        String callSid = safeRaw(body.get("call_sid"));

        if (idTelefonata == null) {
            return missingField("id_telefonata");
        }

        telefonataDao.updateEsito(
                idTelefonata,
                "COMPLETATA",
                "CHIUSURA_LUCREZIA",
                callSid
        );

        return ToolResult.ok(
                "OK",
                ToolNextAction.END_CALL,
                Map.of(
                        "id_telefonata", idTelefonata
                )
        );
    }
    
    @PostMapping("/scheduleIntervention")
    public ToolResult<Map<String, Object>> scheduleIntervention(
            @RequestBody Map<String, Object> body) {

        logTool(
                "scheduleIntervention",
                body
        );

        Long idTelefonataOutbound =
                getLong(
                        body,
                        "telefonata_outbound_id"
                );

        String dataInterventoRaw =
                safeRaw(
                        body.get("data_intervento")
                );

        String nota =
                safeRaw(
                        body.get("nota")
                );

        if (idTelefonataOutbound == null) {

            return missingField(
                    "telefonata_outbound_id"
            );
        }

        if (dataInterventoRaw.isBlank()) {

            return missingField(
                    "data_intervento"
            );
        }

        LocalDateTime dataIntervento;

        try {

            dataIntervento =
                    LocalDateTime.parse(
                            dataInterventoRaw
                    );

        } catch (DateTimeParseException e) {

            return ToolResult.error(
                    "KO",
                    ToolNextAction.ASK_FOR_MISSING_INFORMATION,
                    Map.of(
                            "invalid_field",
                            "data_intervento",

                            "expected_format",
                            "yyyy-MM-dd'T'HH:mm:ss",

                            "received_value",
                            dataInterventoRaw
                    )
            );
        }

        if (dataIntervento.isBefore(
                LocalDateTime.now())) {

            return ToolResult.error(
                    "KO",
                    ToolNextAction.ASK_FOR_MISSING_INFORMATION,
                    Map.of(
                            "invalid_field",
                            "data_intervento",

                            "reason",
                            "La data dell'intervento è nel passato"
                    )
            );
        }

        /*
         * ============================================================
         * PROGRAMMAZIONE INTERVENTO
         * ============================================================
         */

        boolean aggiornato =
                fornitoreOutboundToolDao
                        .programmaIntervento(
                                idTelefonataOutbound,
                                dataIntervento,
                                nota
                        );

        if (!aggiornato) {

            return ToolResult.error(
                    "KO",
                    ToolNextAction.ASK_FOR_MISSING_INFORMATION,
                    Map.of(
                            "telefonata_outbound_id",
                            idTelefonataOutbound,

                            "reason",
                            "Telefonata outbound non trovata"
                    )
            );
        }

        /*
         * ============================================================
         * RECUPERO TICKET
         * ============================================================
         */

        Long idTicket =
                fornitoreOutboundToolDao
                        .findIdTicketByTelefonataOutbound(
                                idTelefonataOutbound
                        );

        /*
         * ============================================================
         * NOTIFICA WHATSAPP AL CONDOMINO
         * ============================================================
         *
         * Non facciamo fallire il tool se WhatsApp non funziona.
         */

        if (idTicket != null) {

            try {

                whatsAppService
                        .inviaAggiornamentoPresaInCaricoTicket(
                                idTicket,
                                dataIntervento
                        );

            } catch (Exception e) {

                System.err.println(
                        "Intervento programmato correttamente"
                                + " - ticket="
                                + idTicket
                                + " - ma notifica WhatsApp fallita: "
                                + e.getMessage()
                );

                e.printStackTrace();
            }

        } else {

            System.err.println(
                    "scheduleIntervention"
                            + " - impossibile recuperare idTicket"
                            + " dalla telefonata outbound "
                            + idTelefonataOutbound
            );
        }

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern(
                        "dd/MM/yyyy 'alle' HH:mm"
                );

        return ToolResult.ok(
                "OK",
                ToolNextAction.CONFIRM_APPOINTMENT,
                Map.of(
                        "telefonata_outbound_id",
                        idTelefonataOutbound,

                        "ticket_id",
                        idTicket != null
                                ? idTicket
                                : 0L,

                        "data_intervento",
                        dataIntervento.toString(),

                        "data_intervento_formattata",
                        dataIntervento.format(
                                formatter
                        ),

                        "nota",
                        nota,

                        "esito",
                        "INTERVENTO_PROGRAMMATO"
                )
        );
    }
    
    @PostMapping("/rejectAssignment")
    public ToolResult<Map<String, Object>> rejectAssignment(
            @RequestBody Map<String, Object> body) {

        logTool("rejectAssignment", body);

        Long idTelefonataOutbound =
                getLong(body, "telefonata_outbound_id");

        String motivo =
                safeRaw(body.get("motivo"));

        if (idTelefonataOutbound == null) {
            return missingField("telefonata_outbound_id");
        }

        if (motivo.isBlank()) {
            return missingField("motivo");
        }

        boolean aggiornato =
                fornitoreOutboundToolDao.rifiutaAssegnazione(
                        idTelefonataOutbound,
                        motivo
                );

        if (!aggiornato) {
            return ToolResult.error(
                    "KO",
                    ToolNextAction.ASK_FOR_MISSING_INFORMATION,
                    Map.of(
                            "telefonata_outbound_id",
                            idTelefonataOutbound,
                            "reason",
                            "Telefonata outbound non trovata"
                    )
            );
        }

        return ToolResult.ok(
                "OK",
                ToolNextAction.ASSIGNMENT_REJECTED,
                Map.of(
                        "telefonata_outbound_id",
                        idTelefonataOutbound,
                        "motivo",
                        motivo,
                        "esito",
                        "TICKET_RIFIUTATO",
                        "ticket_riaperto", true
                )
        );
    }
    
    @PostMapping("/scheduleCallback")
    public ToolResult<Map<String, Object>> scheduleCallback(
            @RequestBody Map<String, Object> body) {

        logTool("scheduleCallback", body);

        Long idTelefonataOutbound =
                getLong(body, "telefonata_outbound_id");

        String dataRichiamataRaw =
                safeRaw(body.get("data_richiamata"));

        String nota =
                safeRaw(body.get("nota"));

        if (idTelefonataOutbound == null) {
            return missingField("telefonata_outbound_id");
        }

        if (dataRichiamataRaw.isBlank()) {
            return missingField("data_richiamata");
        }

        LocalDateTime dataRichiamata;

        try {
            dataRichiamata =
                    LocalDateTime.parse(dataRichiamataRaw);

        } catch (DateTimeParseException e) {

            return ToolResult.error(
                    "KO",
                    ToolNextAction.ASK_FOR_MISSING_INFORMATION,
                    Map.of(
                            "invalid_field", "data_richiamata",
                            "expected_format",
                            "yyyy-MM-dd'T'HH:mm:ss",
                            "received_value",
                            dataRichiamataRaw
                    )
            );
        }

        if (dataRichiamata.isBefore(
                LocalDateTime.now().plusMinutes(1))) {

            return ToolResult.error(
                    "KO",
                    ToolNextAction.ASK_FOR_MISSING_INFORMATION,
                    Map.of(
                            "invalid_field", "data_richiamata",
                            "reason",
                            "La richiamata deve essere programmata "
                                    + "nel futuro"
                    )
            );
        }

        Long idRichiamata =
                fornitoreOutboundToolDao.programmaRichiamata(
                        idTelefonataOutbound,
                        dataRichiamata,
                        nota
                );

        if (idRichiamata == null) {
            return ToolResult.error(
                    "KO",
                    ToolNextAction.ASK_FOR_MISSING_INFORMATION,
                    Map.of(
                            "telefonata_outbound_id",
                            idTelefonataOutbound,
                            "reason",
                            "Telefonata outbound non trovata"
                    )
            );
        }

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern(
                        "dd/MM/yyyy 'alle' HH:mm"
                );

        return ToolResult.ok(
                "OK",
                ToolNextAction.CALLBACK_SCHEDULED,
                Map.of(
                        "telefonata_outbound_id",
                        idTelefonataOutbound,
                        "richiamata_outbound_id",
                        idRichiamata,
                        "data_richiamata",
                        dataRichiamata.toString(),
                        "data_richiamata_formattata",
                        dataRichiamata.format(formatter),
                        "nota",
                        nota,
                        "esito",
                        "RICHIAMATA_RICHIESTA"
                )
        );
    }
    
    @PostMapping("/manageDigitalChannel")
    public ToolResult<Map<String, Object>> manageDigitalChannel(
            @RequestBody Map<String, Object> body) {

        logTool("manageDigitalChannel", body);

        Long idTelefonataOutbound =
                getLong(body, "telefonata_outbound_id");

        String canale =
                safeRaw(body.get("canale")).toUpperCase();

        if (idTelefonataOutbound == null) {
            return missingField("telefonata_outbound_id");
        }

        if (canale.isBlank()) {
            return missingField("canale");
        }

        if (!"WHATSAPP".equals(canale)
                && !"DASHBOARD".equals(canale)) {

            return ToolResult.error(
                    "KO",
                    ToolNextAction.ASK_FOR_MISSING_INFORMATION,
                    Map.of(
                            "invalid_field", "canale",
                            "allowed_values",
                            List.of("WHATSAPP", "DASHBOARD"),
                            "received_value", canale
                    )
            );
        }

        boolean aggiornato =
                fornitoreOutboundToolDao.selezionaGestioneDigitale(
                        idTelefonataOutbound,
                        canale
                );

        if (!aggiornato) {
            return ToolResult.error(
                    "KO",
                    ToolNextAction.ASK_FOR_MISSING_INFORMATION,
                    Map.of(
                            "telefonata_outbound_id",
                            idTelefonataOutbound,
                            "reason",
                            "Telefonata outbound non trovata"
                    )
            );
        }

        return ToolResult.ok(
                "OK",
                ToolNextAction.DIGITAL_MANAGEMENT_SELECTED,
                Map.of(
                        "telefonata_outbound_id",
                        idTelefonataOutbound,
                        "canale", canale,
                        "esito",
                        "WHATSAPP".equals(canale)
                                ? "GESTIONE_WHATSAPP"
                                : "GESTIONE_DASHBOARD"
                )
        );
    }
    
    @PostMapping("/closeOutboundCall")
    public ToolResult<Map<String, Object>> closeOutboundCall(
            @RequestBody Map<String, Object> body) {

        logTool("closeOutboundCall", body);

        Long idTelefonataOutbound =
                getLong(body, "telefonata_outbound_id");

        String esitoChiamata =
                safeRaw(body.get("esito_chiamata"))
                        .toUpperCase();

        String motivoChiusura =
                safeRaw(body.get("motivo_chiusura"));

        if (idTelefonataOutbound == null) {
            return missingField("telefonata_outbound_id");
        }

        if (esitoChiamata.isBlank()) {
            return missingField("esito_chiamata");
        }

        if ("SEGRETERIA".equals(esitoChiamata)
                || "MANCATA_RISPOSTA".equals(esitoChiamata)) {

            RetryOutboundResult retry =
                    fornitoreOutboundToolDao
                            .programmaRetryMancataRisposta(
                                    idTelefonataOutbound,
                                    motivoChiusura
                            );

            if (retry == null) {
                return ToolResult.error(
                        "KO",
                        ToolNextAction.ASK_FOR_MISSING_INFORMATION,
                        Map.of(
                                "telefonata_outbound_id",
                                idTelefonataOutbound,
                                "reason",
                                "Telefonata outbound non trovata"
                        )
                );
            }

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "telefonata_outbound_id",
                    idTelefonataOutbound
            );

            result.put("stato", retry.getStato());
            result.put("tentativi", retry.getTentativi());

            result.put(
                    "massimo_tentativi",
                    retry.getMassimoTentativi()
            );

            result.put(
                    "prossimo_tentativo",
                    retry.getProssimoTentativo()
            );

            result.put(
                    "retry_programmato",
                    retry.getProssimoTentativo() != null
            );

            return ToolResult.ok(
                    "OK",
                    retry.getProssimoTentativo() != null
                            ? ToolNextAction.OUTBOUND_RETRY_SCHEDULED
                            : ToolNextAction.OUTBOUND_CALL_CLOSED,
                    result
            );
        }

        if (!"COMPLETATA".equals(esitoChiamata)) {
            return ToolResult.error(
                    "KO",
                    ToolNextAction.ASK_FOR_MISSING_INFORMATION,
                    Map.of(
                            "invalid_field",
                            "esito_chiamata",
                            "allowed_values",
                            List.of(
                                    "COMPLETATA",
                                    "SEGRETERIA",
                                    "MANCATA_RISPOSTA"
                            ),
                            "received_value",
                            esitoChiamata
                    )
            );
        }

        boolean aggiornata =
                fornitoreOutboundToolDao
                        .chiudiTelefonataOutbound(
                                idTelefonataOutbound,
                                motivoChiusura
                        );

        if (!aggiornata) {
            return ToolResult.error(
                    "KO",
                    ToolNextAction.ASK_FOR_MISSING_INFORMATION,
                    Map.of(
                            "telefonata_outbound_id",
                            idTelefonataOutbound,
                            "reason",
                            "Telefonata outbound non trovata"
                    )
            );
        }

        return ToolResult.ok(
                "OK",
                ToolNextAction.OUTBOUND_CALL_CLOSED,
                Map.of(
                        "telefonata_outbound_id",
                        idTelefonataOutbound,
                        "stato",
                        "COMPLETATA"
                )
        );
    }
    
    @PostMapping("/findRegisteredUser")
    public ResponseEntity<FindRegisteredUserResponse> findRegisteredUser(
            @RequestBody FindRegisteredUserRequest request) {

        System.out.println(
                "TOOL findRegisteredUser"
                + " - idTelefonata="
                + request.getIdTelefonata()
                + " request="
                + request
        );

        FindRegisteredUserResponse response =
                unknownUserService.findRegisteredUser(
                        request
                );

        System.out.println(
                "TOOL findRegisteredUser"
                + " - response="
                + response
        );

        return ResponseEntity.ok(
                response
        );
    }
    
    @PostMapping("/sendApprovalRequest")
    public ResponseEntity<SendApprovalResponse> sendApprovalRequest(
            @RequestBody SendApprovalRequest request) {

        System.out.println(
                "TOOL sendApprovalRequest - request="
                        + request
        );

        SendApprovalResponse response =
                unknownUserApprovalService
                        .sendApprovalRequest(
                                request
                        );

        System.out.println(
                "TOOL sendApprovalRequest - response="
                        + response
        );

        return ResponseEntity.ok(
                response
        );
    }
    
    @PostMapping("/manageUserApproval")
    public ResponseEntity<ManageUserApprovalResponse> manageUserApproval(
            @RequestBody ManageUserApprovalRequest request) {

        System.out.println(
                "TOOL manageUserApproval - request="
                        + request
        );

        ManageUserApprovalResponse response =
                userApprovalService.manageApproval(
                        request
                );

        System.out.println(
                "TOOL manageUserApproval - response="
                        + response
        );

        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/createPendingTicket")
    public ResponseEntity<CreatePendingTicketResponse>
            createPendingTicket(
                    @RequestBody
                    CreatePendingTicketRequest request) {

        System.out.println(
                "TOOL createPendingTicket - request="
                        + request
        );

        CreatePendingTicketResponse response =
                unknownUserTicketService
                        .createPendingTicket(
                                request
                        );

        System.out.println(
                "TOOL createPendingTicket - response="
                        + response
        );

        return ResponseEntity.ok(
                response
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