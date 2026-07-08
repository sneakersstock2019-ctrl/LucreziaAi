package it.sd.lucrezia.ai.controller;

import java.util.Base64;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.sd.lucrezia.ai.dao.TelefonataDao;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/elevenlabs/webhook")
@RequiredArgsConstructor
public class ElevenLabsWebhookController {

    private final TelefonataDao telefonataDao;

    @Value("${app.public-base-url:https://demobotcondomini-production.up.railway.app}")
    private String publicBaseUrl;

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
                0,
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

        String audioUrl = publicBaseUrl + "/elevenlabs/webhook/audio/" + conversationId + ".mp3";

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