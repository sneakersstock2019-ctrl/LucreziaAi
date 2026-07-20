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
import it.sd.lucrezia.ai.bean.VoiceConversationContext;
import it.sd.lucrezia.ai.dao.TelefonataDao;
import it.sd.lucrezia.ai.service.voice.ConversationInitializationService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/elevenlabs/webhook")
@RequiredArgsConstructor
public class ElevenLabsWebhookController {

    private final TelefonataDao telefonataDao;
    private final ConversationInitializationService initializationService;

    @Value("${voice.elevenlabs.pre-call-token}")
    private String preCallToken;
    
    @Value("${voice.elevenlabs.app-public-base-url}")
    private String publicAppBaseUrl;

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
                    initializationService.initialize(
                            request.getCallerId(),
                            request.getCalledNumber(),
                            request.getCallSid(),
                            request.getConversationId(),
                            "SIP"
                    );
            System.out.println("Recuperato Contesto: " + context);

            if (context.branchId() == null || context.branchId().isBlank()) {
                throw new IllegalStateException(
                        "Branch ElevenLabs non configurato "
                                + "per il condominio "
                                + context.utente()
                                        .getNomeCondominio()
                );
            }

            Map<String, Object> agentOverride = new LinkedHashMap<>();
            agentOverride.put(
                    "first_message",
                    context.firstMessage()
            );

            Map<String, Object> configOverride = new LinkedHashMap<>();
            configOverride.put(
                    "agent",
                    agentOverride
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put(
                    "type",
                    "conversation_initiation_client_data"
            );
            response.put(
                    "user_id",
                    String.valueOf(
                            context.utente().getId()
                    )
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

            /*
             * Non restituiamo un payload incompleto:
             * ElevenLabs non riuscirebbe comunque ad inizializzare
             * la conversazione.
             */
            return ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of(
                            "error",
                            e.getMessage()
                    ));

        } catch (Exception e) {

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

        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok("OK");
    }

    private void handleTranscription(JsonNode body) throws Exception {

        JsonNode data = body.path("data");

        String conversationId = data.path("conversation_id").asText();
        String callSid = data.path("metadata").path("phone_call").path("call_sid").asText();

        Long idTelefonata = getLong(
                data.path("conversation_initiation_client_data")
                        .path("dynamic_variables")
                        .path("id_telefonata")
                        .asText()
        );

        long durata = data.path("metadata").path("call_duration_secs").asLong(0);

        String trascrizione = buildTranscript(data.path("transcript"));

        telefonataDao.chiudiTelefonata(
                idTelefonata,
                "COMPLETATA",
                trascrizione,
                durata,
                data.path("tool_names").size(),
                callSid
        );

        telefonataDao.updateElevenLabsConversationId(
                idTelefonata,
                conversationId,
                callSid
        );

        System.out.println("ELEVENLABS TRASCRIZIONE SALVATA - conversationId=" + conversationId);
    }

    private void handleAudio(JsonNode body) {

        JsonNode data = body.path("data");

        String conversationId = data.path("conversation_id").asText();
        String fullAudio = data.path("full_audio").asText();

        if (conversationId == null || conversationId.isBlank()
                || fullAudio == null || fullAudio.isBlank()) {
            return;
        }

        String audioUrl = publicAppBaseUrl + "/elevenlabs/webhook/audio/" + conversationId + ".mp3";

        telefonataDao.updateAudioByConversationId(
                conversationId,
                fullAudio,
                audioUrl
        );

        System.out.println("ELEVENLABS AUDIO SALVATO - conversationId="
                + conversationId + " size=" + fullAudio.length());
    }

    @GetMapping("/audio/{conversationId}.mp3")
    public ResponseEntity<byte[]> audio(@PathVariable String conversationId) {

        String audioBase64 = telefonataDao.findAudioBase64ByConversationId(conversationId);

        if (audioBase64 == null || audioBase64.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        byte[] audio = Base64.getDecoder().decode(audioBase64);

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("audio/mpeg"))
                .body(audio);
    }

    private String buildTranscript(JsonNode transcriptNode) {

        if (transcriptNode == null || !transcriptNode.isArray()) {
            return "";
        }

        return java.util.stream.StreamSupport.stream(transcriptNode.spliterator(), false)
                .map(item -> {
                    String role = item.path("role").asText();
                    String message = item.path("message").asText();

                    if ("agent".equals(role)) {
                        return "Lucrezia: " + message;
                    }

                    if ("user".equals(role)) {
                        return "Condomino: " + message;
                    }

                    return role + ": " + message;
                })
                .collect(Collectors.joining("\n\n"));
    }

    private Long getLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }
}