package it.sd.demo.bot.condomini.service;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OpenAIRealtimeClient {

    @Value("${openai.api-key}")
    private String openAiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebSocketClient createTranscriptionClient(String callSid) throws Exception {

        URI uri = new URI("wss://api.openai.com/v1/realtime?model=gpt-4o-transcribe");

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
                System.out.println("OPENAI REALTIME EVENT:");
                System.out.println(message);
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
        client.addHeader("OpenAI-Beta", "realtime=v1");

        return client;
    }

    private void sendSessionUpdate(WebSocketClient client) throws Exception {

        Map<String, Object> event = Map.of(
                "type", "transcription_session.update",
                "session", Map.of(
                        "input_audio_format", "g711_ulaw",
                        "input_audio_transcription", Map.of(
                                "model", "gpt-4o-transcribe",
                                "language", "it"
                        ),
                        "turn_detection", Map.of(
                                "type", "server_vad",
                                "threshold", 0.5,
                                "prefix_padding_ms", 300,
                                "silence_duration_ms", 500
                        )
                )
        );

        client.send(objectMapper.writeValueAsString(event));
    }

    public void sendAudio(WebSocketClient client, String twilioBase64Payload) {

        if (client == null || !client.isOpen()) {
            return;
        }

        try {
            Map<String, Object> event = Map.of(
                    "type", "input_audio_buffer.append",
                    "audio", twilioBase64Payload
            );

            client.send(objectMapper.writeValueAsString(event));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}