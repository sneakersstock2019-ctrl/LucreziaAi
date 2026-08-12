package it.sd.lucrezia.ai.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.sd.lucrezia.ai.bean.ElevenLabsPreCallRequest;
import it.sd.lucrezia.ai.bean.RetryOutboundResult;
import it.sd.lucrezia.ai.bean.VoiceConversationContext;
import it.sd.lucrezia.ai.dao.FornitoreOutboundToolDao;
import it.sd.lucrezia.ai.dao.TelefonataDao;
import it.sd.lucrezia.ai.dao.TelefonataOutboundDao;
import it.sd.lucrezia.ai.service.voice.ConversationInitializationService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/elevenlabs/webhook")
@RequiredArgsConstructor
public class ElevenLabsWebhookController {

    private final TelefonataDao telefonataDao;
    private final TelefonataOutboundDao telefonataOutboundDao;
    private final FornitoreOutboundToolDao fornitoreOutboundToolDao;
    private final ConversationInitializationService conversationInitializationService;

    @Value("${voice.elevenlabs.pre-call-token}")
    private String preCallToken;
    
    @Value("${lucrezia.api-public-base-url}")
    private String publicApiBaseUrl;

    @PostMapping("/pre-call")
    public ResponseEntity<Map<String, Object>> preCall(
            @RequestHeader(
                    name = "X-Lucrezia-Token",
                    required = false
            )
            String receivedToken,

            @RequestBody
            ElevenLabsPreCallRequest request
    ) {

        System.out.println("@PostMapping(\"/pre-call\") con i seguenti parametri:");
        System.out.println("X-Lucrezia-Token: " + receivedToken);
        System.out.println("@RequestBody: " + request);

        if (!tokenMatches(receivedToken)) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error",
                            "Unauthorized"
                    ));
        }

        try {

            VoiceConversationContext context =
                    conversationInitializationService.initialize(
                            request.getCallerId(),
                            request.getCalledNumber(),
                            request.getCallSid(),
                            request.getConversationId(),
                            "SIP"
                    );

            System.out.println("Recuperato Contesto: " + context);

            /*
             * Il branch deve esserci sempre,
             * sia per utente conosciuto sia per utente sconosciuto.
             */
            if (context.branchId() == null
                    || context.branchId().isBlank()) {

                String descrizioneContesto;

                if (context.utente() != null) {

                    descrizioneContesto =
                            "per il condominio "
                            + context.utente().getNomeCondominio();

                } else {

                    descrizioneContesto =
                            "per utente sconosciuto";
                }

                throw new IllegalStateException(
                        "Branch ElevenLabs non configurato "
                                + descrizioneContesto
                );
            }

            /*
             * Override del first message.
             */
            Map<String, Object> agentOverride =
                    new LinkedHashMap<>();

            agentOverride.put(
                    "first_message",
                    context.firstMessage()
            );

            Map<String, Object> configOverride =
                    new LinkedHashMap<>();

            configOverride.put(
                    "agent",
                    agentOverride
            );

            /*
             * Costruzione risposta ElevenLabs.
             */
            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "type",
                    "conversation_initiation_client_data"
            );

            /*
             * user_id:
             *
             * - utente conosciuto -> id DB
             * - utente sconosciuto -> identificativo temporaneo
             */
            String userId;

            if (context.utente() != null) {

                userId =
                        String.valueOf(
                                context.utente().getId()
                        );

            } else {

                String callerId =
                        request.getCallerId();

                if (callerId == null
                        || callerId.isBlank()) {

                    callerId = "UNKNOWN";

                } else {

                    callerId =
                            callerId.replaceAll(
                                    "[^0-9]",
                                    ""
                            );
                }

                userId =
                        "UNKNOWN_" + callerId;
            }

            response.put(
                    "user_id",
                    userId
            );

            response.put(
                    "branch_id",
                    context.branchId()
            );

            response.put(
                    "environment",
                    "production"
            );

            response.put(
                    "dynamic_variables",
                    context.dynamicVariables()
            );

            response.put(
                    "conversation_config_override",
                    configOverride
            );

            System.out.println("Response: " + response);

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of(
                            "error",
                            e.getMessage()
                    ));

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error",
                            "Errore durante l'inizializzazione "
                                    + "della conversazione"
                    ));
        }
    }

    private boolean tokenMatches(String receivedToken) {

        if (receivedToken == null
                || preCallToken == null) {
            return false;
        }

        return MessageDigest.isEqual(
        		preCallToken.getBytes(
                        StandardCharsets.UTF_8
                ),
                receivedToken.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    @GetMapping("/post-call")
    public ResponseEntity<String> verify() {
        return ResponseEntity.ok("OK");
    }
    
    @PostMapping("/post-call")
    public ResponseEntity<String> postCall(@RequestBody JsonNode body) {
        String type = body.path("type").asText();

        try {
            if ("post_call_transcription".equals(type)) {
                handleTranscription(body);
            }

            if ("post_call_audio".equals(type)) {
                handleAudio(body);
            }
            if ("call_initiation_failure".equals(type)) {
                handleCallFailure(body);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok("OK");
    }

    private void handleCallFailure(JsonNode body) {

        JsonNode data =
                body.path("data");

        String conversationId =
                data.path("conversation_id")
                        .asText();

        String reason =
                data.path("reason")
                        .asText("UNKNOWN");

        System.out.println(
                "ELEVENLABS CALL FAILURE"
                        + " - conversationId="
                        + conversationId
                        + " - reason="
                        + reason
        );

        Long idTelefonataOutbound =
                telefonataOutboundDao
                        .findIdByConversationId(
                                conversationId
                        );

        if (idTelefonataOutbound == null) {

            System.out.println(
                    "ELEVENLABS CALL FAILURE"
                            + " - nessuna telefonata outbound trovata"
                            + " - conversationId="
                            + conversationId
            );

            return;
        }

        try {

            RetryOutboundResult retry =
                    fornitoreOutboundToolDao
                            .programmaRetryMancataRisposta(
                                    idTelefonataOutbound,
                                    reason
                            );

            if (retry == null) {

                System.out.println(
                        "ELEVENLABS CALL FAILURE"
                                + " - retry non programmato"
                                + " - id="
                                + idTelefonataOutbound
                );

                return;
            }

            System.out.println(
                    "RETRY OUTBOUND GESTITO"
                            + " - id="
                            + idTelefonataOutbound
                            + " - tentativo="
                            + retry.getTentativi()
                            + "/"
                            + retry.getMassimoTentativi()
                            + " - stato="
                            + retry.getStato()
                            + " - prossimoTentativo="
                            + retry.getProssimoTentativo()
            );

        } catch (Exception e) {

            System.err.println(
                    "Errore gestione retry outbound"
                            + " - id="
                            + idTelefonataOutbound
                            + " - conversationId="
                            + conversationId
                            + " - reason="
                            + reason
                            + " - errore="
                            + e.getMessage()
            );

            e.printStackTrace();
        }
    }
    
    private void handleTranscription(JsonNode body)
            throws Exception {

        JsonNode data = body.path("data");

        String conversationId =
                data.path("conversation_id").asText();

        String callSid =
                data.path("metadata")
                        .path("phone_call")
                        .path("call_sid")
                        .asText();

        JsonNode dynamicVariables =
                data.path("conversation_initiation_client_data")
                        .path("dynamic_variables");

        JsonNode transcriptNode =
                data.path("transcript");

        long durata =
                data.path("metadata")
                        .path("call_duration_secs")
                        .asLong(0);

        int numeroTool =
                data.path("tool_names").size();

        /*
         * ============================================================
         * OUTBOUND
         * ============================================================
         */

        Long idTelefonataOutbound =
                telefonataOutboundDao
                        .findIdByConversationId(
                                conversationId
                        );

        /*
         * Fallback per compatibilità con il vecchio giro.
         */
        if (idTelefonataOutbound == null) {

            idTelefonataOutbound =
                    getLong(
                            dynamicVariables
                                    .path("telefonata_outbound_id")
                                    .asText()
                    );
        }

        if (idTelefonataOutbound != null) {

            String tipoChiamata =
                    telefonataOutboundDao
                            .findTipoChiamataByConversationId(
                                    conversationId
                            );

            boolean rispostaUtente =
                    haRispostaUtente(
                            transcriptNode
                    );

            System.out.println(
                    "ELEVENLABS OUTBOUND POST-CALL"
                            + " - id="
                            + idTelefonataOutbound
                            + " - tipo="
                            + tipoChiamata
                            + " - conversationId="
                            + conversationId
                            + " - durata="
                            + durata
                            + " - rispostaUtente="
                            + rispostaUtente
                            + " - numeroTool="
                            + numeroTool
            );

            /*
             * ========================================================
             * NESSUNA RISPOSTA REALE
             * ========================================================
             *
             * ElevenLabs può generare il post-call anche se
             * il destinatario non ha realmente conversato.
             *
             * In questo caso NON impostiamo COMPLETATA.
             */
            if (!rispostaUtente) {

                System.out.println(
                        "ELEVENLABS OUTBOUND SENZA RISPOSTA UTENTE"
                                + " - id="
                                + idTelefonataOutbound
                                + " - conversationId="
                                + conversationId
                                + " - programmo retry"
                );

                telefonataOutboundDao.programmaRetry(
                        idTelefonataOutbound,
                        "NESSUNA_RISPOSTA_UTENTE",
                        1
                );

                return;
            }

            /*
             * ========================================================
             * CONVERSAZIONE REALE
             * ========================================================
             */

            String nomeInterlocutore =
                    getNomeInterlocutoreOutbound(
                            tipoChiamata
                    );

            String trascrizione =
                    buildTranscript(
                            transcriptNode,
                            nomeInterlocutore
                    );

            telefonataOutboundDao.completaPostCall(
                    idTelefonataOutbound,
                    conversationId,
                    trascrizione,
                    durata,
                    numeroTool
            );

            System.out.println(
                    "ELEVENLABS OUTBOUND TRASCRIZIONE SALVATA"
                            + " - id="
                            + idTelefonataOutbound
                            + " - tipo="
                            + tipoChiamata
                            + " - conversationId="
                            + conversationId
            );

            return;
        }

        /*
         * ============================================================
         * INBOUND
         * ============================================================
         */

        Long idTelefonata =
                getLong(
                        dynamicVariables
                                .path("id_telefonata")
                                .asText()
                );

        String trascrizione =
                buildTranscript(
                        transcriptNode,
                        "Condomino"
                );

        telefonataDao.chiudiTelefonata(
                idTelefonata,
                "COMPLETATA",
                trascrizione,
                durata,
                numeroTool,
                callSid
        );

        telefonataDao.updateElevenLabsConversationId(
                idTelefonata,
                conversationId,
                callSid
        );

        System.out.println(
                "ELEVENLABS INBOUND TRASCRIZIONE SALVATA"
                        + " - conversationId="
                        + conversationId
        );
    }

    private void handleAudio(JsonNode body) {

        JsonNode data = body.path("data");

        String conversationId =
                data.path("conversation_id").asText();

        String fullAudio =
                data.path("full_audio").asText();

        if (conversationId == null
                || conversationId.isBlank()
                || fullAudio == null
                || fullAudio.isBlank()) {

            return;
        }

        String audioUrl =
                publicApiBaseUrl
                        + "/elevenlabs/webhook/audio/"
                        + conversationId
                        + ".mp3";

        /*
         * Prima cerchiamo fra le outbound.
         */
        Long idTelefonataOutbound =
                telefonataOutboundDao
                        .findIdByConversationId(
                                conversationId
                        );

        if (idTelefonataOutbound != null) {

            telefonataOutboundDao.updateAudioByConversationId(
                    conversationId,
                    fullAudio,
                    audioUrl
            );

            System.out.println(
                    "ELEVENLABS OUTBOUND AUDIO SALVATO"
                            + " - id="
                            + idTelefonataOutbound
                            + " - conversationId="
                            + conversationId
                            + " - size="
                            + fullAudio.length()
            );

            return;
        }

        /*
         * Altrimenti è inbound.
         */
        telefonataDao.updateAudioByConversationId(
                conversationId,
                fullAudio,
                audioUrl
        );

        System.out.println(
                "ELEVENLABS INBOUND AUDIO SALVATO"
                        + " - conversationId="
                        + conversationId
                        + " size="
                        + fullAudio.length()
        );
    }
    
    @GetMapping("/audio/{conversationId}.mp3")
    public ResponseEntity<byte[]> audio(
            @PathVariable String conversationId) {

        /*
         * Prima outbound.
         */
        String audioBase64 =
                telefonataOutboundDao
                        .findAudioBase64ByConversationId(
                                conversationId
                        );

        /*
         * Se non trovato, inbound.
         */
        if (audioBase64 == null || audioBase64.isBlank()) {

            audioBase64 =
                    telefonataDao
                            .findAudioBase64ByConversationId(
                                    conversationId
                            );
        }

        if (audioBase64 == null
                || audioBase64.isBlank()) {

            return ResponseEntity.notFound().build();
        }

        byte[] audio =
                Base64.getDecoder().decode(audioBase64);

        return ResponseEntity.ok()
                .contentType(
                        MediaType.valueOf("audio/mpeg")
                )
                .body(audio);
    }

    private String buildTranscript(
            JsonNode transcriptNode,
            String userLabel) {

        if (transcriptNode == null
                || !transcriptNode.isArray()) {

            return "";
        }

        return java.util.stream.StreamSupport
                .stream(
                        transcriptNode.spliterator(),
                        false
                )
                .map(item -> {

                    String role =
                            item.path("role").asText("");

                    String message =
                            item.path("message").asText("");

                    return new String[] {
                            role != null ? role.trim() : "",
                            message != null ? message.trim() : ""
                    };
                })
                .filter(
                        values ->
                                isValidTranscriptMessage(
                                        values[1]
                                )
                )
                .map(values -> {

                    String role = values[0];
                    String message = values[1];

                    if ("agent".equalsIgnoreCase(role)) {
                        return "Lucrezia: " + message;
                    }

                    if ("user".equalsIgnoreCase(role)) {
                        return userLabel + ": " + message;
                    }

                    if (role.isBlank()) {
                        return message;
                    }

                    return role + ": " + message;
                })
                .collect(
                        Collectors.joining("\n\n")
                );
    }
    
    private String getNomeInterlocutoreOutbound(
            String tipoChiamata) {

        if (tipoChiamata == null
                || tipoChiamata.isBlank()) {

            return "Interlocutore";
        }

        return switch (tipoChiamata) {

            case "FORNITORE" ->
                    "Fornitore";

            case "APPROVAZIONE_UTENTE" ->
                    "Condomino";

            default ->
                    "Interlocutore";
        };
    }
    
    private boolean haRispostaUtente(
            JsonNode transcript) {

        if (transcript == null
                || !transcript.isArray()
                || transcript.isEmpty()) {

            return false;
        }

        for (JsonNode item : transcript) {

            String role =
                    item.path("role")
                            .asText();

            if ("user".equalsIgnoreCase(role)) {

                String message =
                        item.path("message")
                                .asText();

                /*
                 * Se ElevenLabs ha davvero trascritto
                 * qualcosa detto dal destinatario,
                 * consideriamo la chiamata risposta.
                 */
                if (message != null
                        && !message.isBlank()) {

                    return true;
                }
            }
        }

        return false;
    }

    private boolean isValidTranscriptMessage(String message) {

        return message != null
                && !message.isBlank()
                && !"null".equalsIgnoreCase(message.trim())
                && !"undefined".equalsIgnoreCase(message.trim());
    }

    private Long getLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }
}