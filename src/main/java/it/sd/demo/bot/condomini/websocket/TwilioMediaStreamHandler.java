package it.sd.demo.bot.condomini.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.java_websocket.client.WebSocketClient;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.demo.bot.condomini.service.OpenAIRealtimeAudioListener;
import it.sd.demo.bot.condomini.service.OpenAIRealtimeClient;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TwilioMediaStreamHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAIRealtimeClient openAIRealtimeClient;
    private final Map<String, Boolean> greetingSent = new ConcurrentHashMap<>();
    private final Map<String, Integer> chunkCounter = new ConcurrentHashMap<>();
    private final Map<String, WebSocketClient> openAiClients = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> twilioSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToStreamSid = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("############################");
        System.out.println("TWILIO MEDIA STREAM CONNECTED");
        System.out.println("SESSION ID = " + session.getId());
        System.out.println("############################");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        JsonNode root = objectMapper.readTree(message.getPayload());

        String event = root.path("event").asText();

        switch (event) {

            case "connected" -> {
                System.out.println("MEDIA STREAM EVENT: connected");
            }

            case "start" -> {
                String streamSid = root.path("start").path("streamSid").asText();
                String callSid = root.path("start").path("callSid").asText();
                
                JsonNode params = root.path("start").path("customParameters");
                String phone = params.path("phone").asText();
                String nome = params.path("nome").asText();
                String condominio = params.path("condominio").asText();

                chunkCounter.put(streamSid, 0);
                sessionToStreamSid.put(session.getId(), streamSid);
                twilioSessions.put(streamSid, session);
                greetingSent.put(streamSid, false);

                System.out.println("############################");
                System.out.println("MEDIA STREAM EVENT: start");
                System.out.println("STREAM SID = " + streamSid);
                System.out.println("CALL SID = " + callSid);
                System.out.println("PARAM PHONE = " + phone);
                System.out.println("PARAM NOME = " + nome);
                System.out.println("PARAM CONDOMINIO = " + condominio);
                System.out.println("Apro connessione OpenAI Realtime Voice...");
                System.out.println("############################");

                OpenAIRealtimeAudioListener listener = new OpenAIRealtimeAudioListener() {

                	@Override
                	public void onSessionReady() {
                	    try {
                	        WebSocketClient client = openAiClients.get(streamSid);

                	        if (client != null && client.isOpen()) {
                	            openAIRealtimeClient.sendInitialGreeting(client);
                	            System.out.println("SALUTO INIZIALE INVIATO DOPO SESSION READY");
                	        }
                	    } catch (Exception e) {
                	        e.printStackTrace();
                	    }
                	}
                	
                    @Override
                    public void onAudioDelta(String base64Audio) {
                        sendAudioToTwilio(streamSid, base64Audio);
                    }

                    @Override
                    public void onAssistantTranscriptDelta(String delta) {
                        System.out.print(delta);
                    }

                    @Override
                    public void onAssistantTranscriptDone(String transcript) {
                        System.out.println();
                        System.out.println("############################");
                        System.out.println("LUCREZIA REALTIME:");
                        System.out.println(transcript);
                        System.out.println("############################");
                    }

                    @Override
                    public void onError(String rawMessage) {
                        System.out.println("OPENAI REALTIME LISTENER ERROR:");
                        System.out.println(rawMessage);
                    }
                };

                WebSocketClient openAiClient = openAIRealtimeClient.createVoiceClient(callSid, nome, condominio, listener);

                openAiClients.put(streamSid, openAiClient);

                openAiClient.connect();
            }

            case "media" -> {
                String streamSid = root.path("streamSid").asText();

                if (streamSid == null || streamSid.isBlank()) {
                    return;
                }

                String payload = root.path("media").path("payload").asText();

                if (payload == null || payload.isBlank()) {
                    return;
                }

                int count = chunkCounter.merge(streamSid, 1, Integer::sum);

                WebSocketClient openAiClient = openAiClients.get(streamSid);

                if (openAiClient != null && openAiClient.isOpen()) {

                    if (!Boolean.TRUE.equals(greetingSent.get(streamSid))) {
                        greetingSent.put(streamSid, true);

                        try {
                            openAIRealtimeClient.sendInitialGreeting(openAiClient);
                            System.out.println("SALUTO INIZIALE INVIATO A OPENAI");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    openAIRealtimeClient.sendAudio(openAiClient, payload);
                }

                if (count % 100 == 0) {
                    System.out.println("MEDIA CHUNK inviati a OpenAI per stream " + streamSid + ": " + count);
                }
            }

            case "stop" -> {
                String streamSid = root.path("streamSid").asText();

                Integer total = chunkCounter.remove(streamSid);

                closeOpenAiClient(streamSid);
                twilioSessions.remove(streamSid);
                greetingSent.remove(streamSid);

                System.out.println("############################");
                System.out.println("MEDIA STREAM EVENT: stop");
                System.out.println("STREAM SID = " + streamSid);
                System.out.println("MEDIA CHUNK TOTALI = " + total);
                System.out.println("############################");
            }

            default -> {
                System.out.println("MEDIA STREAM EVENT non gestito: " + event);
            }
        }
    }

    private void sendAudioToTwilio(String streamSid, String base64Audio) {

        if (streamSid == null || streamSid.isBlank()) {
            return;
        }

        if (base64Audio == null || base64Audio.isBlank()) {
            return;
        }

        WebSocketSession twilioSession = twilioSessions.get(streamSid);

        if (twilioSession == null || !twilioSession.isOpen()) {
            return;
        }

        try {
            Map<String, Object> event = Map.of(
                    "event", "media",
                    "streamSid", streamSid,
                    "media", Map.of(
                            "payload", base64Audio
                    )
            );

            String json = objectMapper.writeValueAsString(event);

            synchronized (twilioSession) {
                twilioSession.sendMessage(new TextMessage(json));
            }

        } catch (Exception e) {
            System.out.println("ERRORE INVIO AUDIO A TWILIO:");
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      org.springframework.web.socket.CloseStatus status) {

        String streamSid = sessionToStreamSid.remove(session.getId());

        if (streamSid != null) {
            closeOpenAiClient(streamSid);
            chunkCounter.remove(streamSid);
            twilioSessions.remove(streamSid);
            greetingSent.remove(streamSid);
        }

        System.out.println("############################");
        System.out.println("TWILIO MEDIA STREAM CLOSED");
        System.out.println("SESSION ID = " + session.getId());
        System.out.println("STATUS = " + status);
        System.out.println("############################");
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {

        String streamSid = sessionToStreamSid.remove(session.getId());

        if (streamSid != null) {
            closeOpenAiClient(streamSid);
            chunkCounter.remove(streamSid);
            twilioSessions.remove(streamSid);
            greetingSent.remove(streamSid);
        }

        System.out.println("############################");
        System.out.println("TWILIO MEDIA STREAM ERROR");
        System.out.println("SESSION ID = " + session.getId());
        System.out.println("ERROR = " + exception.getMessage());
        System.out.println("############################");

        exception.printStackTrace();
    }

    private void closeOpenAiClient(String streamSid) {

        if (streamSid == null || streamSid.isBlank()) {
            return;
        }

        WebSocketClient client = openAiClients.remove(streamSid);

        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}