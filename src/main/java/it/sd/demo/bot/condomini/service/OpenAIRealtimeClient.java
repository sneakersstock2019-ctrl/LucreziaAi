package it.sd.demo.bot.condomini.service;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OpenAIRealtimeClient {

    @Value("${openai.api-key}")
    private String openAiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebSocketClient createTranscriptionClient(String callSid) throws Exception {

        URI uri = new URI("wss://api.openai.com/v1/realtime?intent=transcription");

        WebSocketClient client = new WebSocketClient(uri) {

            @Override
            public void onOpen(ServerHandshake handshakedata) {
                System.out.println("OPENAI REALTIME CONNECTED - CALL SID = " + callSid);

                try {
                    sendSessionUpdate(this);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(String message) {
                try {
                    JsonNode root = objectMapper.readTree(message);
                    String type = root.path("type").asText();

                    System.out.println("OPENAI REALTIME TYPE = " + type);

                    if (root.has("transcript")) {
                        System.out.println("############################");
                        System.out.println("OPENAI REALTIME TRANSCRIPT:");
                        System.out.println(root.path("transcript").asText());
                        System.out.println("############################");
                    }

                    if (root.has("delta")) {
                        String delta = root.path("delta").asText();
                        if (delta != null && !delta.isBlank()) {
                            System.out.println("OPENAI REALTIME DELTA = " + delta);
                        }
                    }

                    if ("error".equals(type)) {
                        System.out.println("OPENAI REALTIME ERROR EVENT:");
                        System.out.println(message);
                    }

                } catch (Exception e) {
                    System.out.println("OPENAI REALTIME RAW MESSAGE:");
                    System.out.println(message);
                }
            }

            @Override
            public void onMessage(ByteBuffer bytes) {
                System.out.println("OPENAI REALTIME BINARY MESSAGE ricevuto");
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("OPENAI REALTIME CLOSED - code=" + code + ", reason=" + reason);
            }

            @Override
            public void onError(Exception ex) {
                System.out.println("OPENAI REALTIME ERROR: " + ex.getMessage());
                ex.printStackTrace();
            }
        };

        client.addHeader("Authorization", "Bearer " + openAiApiKey);

        return client;
    }

    private void sendSessionUpdate(WebSocketClient client) throws Exception {

        Map<String, Object> event = Map.of(
                "type", "session.update",
                "session", Map.of(
                        "type", "transcription",
                        "audio", Map.of(
                                "input", Map.of(
                                        "format", Map.of(
                                                "type", "audio/pcmu"
                                        ),
                                        "transcription", Map.of(
                                                "model", "gpt-realtime-whisper",
                                                "language", "it",
                                                "prompt", "Trascrizione telefonica italiana di un condomino che parla con Lucrezia, assistente del condominio."
                                        ),
                                        "turn_detection", Map.of(
                                                "type", "server_vad",
                                                "threshold", 0.5,
                                                "prefix_padding_ms", 300,
                                                "silence_duration_ms", 500
                                        )
                                )
                        )
                )
        );

        client.send(objectMapper.writeValueAsString(event));
    }

    public void sendAudio(WebSocketClient client, String twilioBase64Payload) {

        if (client == null) {
            return;
        }

        if (!client.isOpen()) {
            return;
        }

        try {
            Map<String, Object> event = Map.of(
                    "type", "input_audio_buffer.append",
                    "audio", twilioBase64Payload
            );

            client.send(objectMapper.writeValueAsString(event));

        } catch (org.java_websocket.exceptions.WebsocketNotConnectedException e) {
            // OpenAI ha già chiuso la socket: non è un errore bloccante per la demo.
            System.out.println("OPENAI REALTIME non connesso, audio ignorato.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}