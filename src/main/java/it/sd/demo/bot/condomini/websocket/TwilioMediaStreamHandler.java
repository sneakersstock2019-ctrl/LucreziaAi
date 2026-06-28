package it.sd.demo.bot.condomini.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.demo.bot.condomini.bean.TicketStatusInfo;
import it.sd.demo.bot.condomini.bean.VoiceContext;
import it.sd.demo.bot.condomini.dao.TicketDao;
import it.sd.demo.bot.condomini.realtime.tool.LucreziaToolDispatcher;
import it.sd.demo.bot.condomini.service.OpenAIRealtimeAudioListener;
import it.sd.demo.bot.condomini.service.OpenAIRealtimeClient;
import it.sd.demo.bot.condomini.service.TwilioRecordingService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TwilioMediaStreamHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OpenAIRealtimeClient openAIRealtimeClient;
    private final LucreziaToolDispatcher toolDispatcher;
    private final TicketDao ticketDao;
    private final TwilioRecordingService twilioRecordingService;

    private final Map<String, Integer> chunkCounter = new ConcurrentHashMap<>();
    private final Map<String, WebSocketClient> openAiClients = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> twilioSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToStreamSid = new ConcurrentHashMap<>();
    private final Map<String, Boolean> assistantSpeaking = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> silenceTasks = new ConcurrentHashMap<>();

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
                String recordingSid = twilioRecordingService.startRecording(callSid);


                JsonNode params = root.path("start").path("customParameters");

                String phone = params.path("phone").asText();
                String nome = params.path("nome").asText();
                String condominio = params.path("condominio").asText();
                Long idUtente = params.path("idUtente").asLong();
                Long idCondominio = params.path("idCondominio").asLong();

                List<TicketStatusInfo> ticketAperti = new ArrayList<>();

                try {
                    ticketAperti = ticketDao.findOpenTicketsByUtente(idUtente);
                } catch (Exception e) {
                    System.out.println("Errore recupero ticket aperti realtime:");
                    e.printStackTrace();
                }

                VoiceContext context = new VoiceContext();
                context.setPhone(phone);
                context.setNome(nome);
                context.setCondominio(condominio);
                context.setIdUtente(idUtente);
                context.setTicketAperti(ticketAperti);
                context.setIdCondominio(idCondominio);
                context.setCallSid(callSid);
                context.setRecordingSid(recordingSid);

                chunkCounter.put(streamSid, 0);
                sessionToStreamSid.put(session.getId(), streamSid);
                twilioSessions.put(streamSid, session);

                System.out.println("############################");
                System.out.println("MEDIA STREAM EVENT: start");
                System.out.println("STREAM SID = " + streamSid);
                System.out.println("CALL SID = " + callSid);
                System.out.println("PARAM PHONE = " + phone);
                System.out.println("PARAM NOME = " + nome);
                System.out.println("PARAM CONDOMINIO = " + condominio);
                System.out.println("PARAM ID_UTENTE = " + idUtente);
                System.out.println("PARAM ID_CONDOMINIO = " + idCondominio);
                System.out.println("TICKET APERTI = " + ticketAperti.size());
                System.out.println("Apro connessione OpenAI Realtime Voice...");
                System.out.println("############################");

                OpenAIRealtimeAudioListener listener = new OpenAIRealtimeAudioListener() {

                    @Override
                    public void onSessionReady() {
                        try {
                            WebSocketClient client = openAiClients.get(streamSid);

                            if (client != null && client.isOpen()) {
                                openAIRealtimeClient.sendInitialGreeting(client, context);
                                System.out.println("SALUTO INIZIALE INVIATO DOPO SESSION READY");
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
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
                        context.setTrascrizioneChiamata(
                                context.getTrascrizioneChiamata()
                                        + "\nLucrezia: " + transcript + "\n"
                        );
                    }
                    
                    @Override
                    public void onUserTranscriptDone(String transcript) {
                        context.setTrascrizioneChiamata(
                                context.getTrascrizioneChiamata()
                                        + "\nCondomino: " + transcript + "\n"
                        );
                    }

                    @Override
                    public void onError(String rawMessage) {
                        System.out.println("OPENAI REALTIME LISTENER ERROR:");
                        System.out.println(rawMessage);
                    }

                    @Override
                    public void onFunctionCall(String callId, String name, String arguments) {

                        try {
                            String outputJson = toolDispatcher.execute(name, arguments, context);

                            if (outputJson == null) {
                                System.out.println("TOOL " + name + " eseguito senza output verso OpenAI");

                                if ("endCall".equals(name)) {
                                    closeTwilioCall(streamSid);
                                }

                                return;
                            }

                            WebSocketClient client = openAiClients.get(streamSid);

                            if (client != null && client.isOpen()) {
                                openAIRealtimeClient.sendFunctionOutput(client, callId, outputJson);
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    
                    @Override
                    public void onAudioDelta(String base64Audio) {
                        assistantSpeaking.put(streamSid, true);
                        sendAudioToTwilio(streamSid, base64Audio);
                    }
                    
                    @Override
                    public void onAssistantAudioDone() {

                        assistantSpeaking.put(streamSid, false);

                        if (context.isEndCallRequested()) {
                            closeTwilioCall(streamSid);
                            return;
                        }

                        scheduleSilenceCheck(streamSid, context, 15000);
                    }

                    @Override
                    public void onUserSpeechStarted() {
                    	context.setLastUserSpeechTime(System.currentTimeMillis());

                    	java.util.concurrent.ScheduledFuture<?> task = silenceTasks.remove(streamSid);
                    	if (task != null) {
                    	    task.cancel(false);
                    	}
                    	
                        if (!Boolean.TRUE.equals(assistantSpeaking.get(streamSid))) {
                            return;
                        }

                        System.out.println("BARGE-IN: utente ha interrotto Lucrezia");

                        sendClearToTwilio(streamSid);

                        WebSocketClient client = openAiClients.get(streamSid);

                        if (client != null && client.isOpen()) {
                            openAIRealtimeClient.cancelResponse(client);
                        }

                        assistantSpeaking.put(streamSid, false);
                    }
                };

                WebSocketClient openAiClient =
                        openAIRealtimeClient.createVoiceClient(
                                callSid,
                                nome,
                                condominio,
                                listener
                        );

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
                assistantSpeaking.remove(streamSid);
                java.util.concurrent.ScheduledFuture<?> task = silenceTasks.remove(streamSid);
                if (task != null) {
                    task.cancel(false);
                }

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
            assistantSpeaking.remove(streamSid);
            java.util.concurrent.ScheduledFuture<?> task = silenceTasks.remove(streamSid);
            if (task != null) {
                task.cancel(false);
            }
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
            assistantSpeaking.remove(streamSid);
            java.util.concurrent.ScheduledFuture<?> task = silenceTasks.remove(streamSid);
            if (task != null) {
                task.cancel(false);
            }
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
    
    private void sendClearToTwilio(String streamSid) {

        WebSocketSession twilioSession = twilioSessions.get(streamSid);

        if (twilioSession == null || !twilioSession.isOpen()) {
            return;
        }

        try {
            Map<String, Object> event = Map.of(
                    "event", "clear",
                    "streamSid", streamSid
            );

            synchronized (twilioSession) {
                twilioSession.sendMessage(
                        new TextMessage(objectMapper.writeValueAsString(event))
                );
            }

            System.out.println("CLEAR inviato a Twilio per stream " + streamSid);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void closeTwilioCall(String streamSid) {

        WebSocketSession twilioSession = twilioSessions.get(streamSid);

        if (twilioSession == null || !twilioSession.isOpen()) {
            return;
        }

        scheduler.schedule(() -> {

            try {

                if (twilioSession.isOpen()) {
                    twilioSession.close();
                    System.out.println("CHIAMATA CHIUSA DA LUCREZIA - streamSid=" + streamSid);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }, 2, TimeUnit.SECONDS);
    }
    
    private void scheduleSilenceCheck(String streamSid,
    		VoiceContext context,
    		long delayMs) {

    	java.util.concurrent.ScheduledFuture<?> oldTask = silenceTasks.remove(streamSid);
    	if (oldTask != null) {
    		oldTask.cancel(false);
    	}

    	long scheduledAt = System.currentTimeMillis();

    	java.util.concurrent.ScheduledFuture<?> task = scheduler.schedule(() -> {

    		silenceTasks.remove(streamSid);

    		long lastSpeech = context.getLastUserSpeechTime();

    		if (lastSpeech > scheduledAt) return;
    		if (Boolean.TRUE.equals(assistantSpeaking.get(streamSid))) return;
    		if (context.isEndCallRequested()) return;

    		WebSocketClient client = openAiClients.get(streamSid);

    		if (client != null && client.isOpen()) {
    			openAIRealtimeClient.sendUserText(
    					client,
    					"Il condomino è rimasto in silenzio. Chiedi gentilmente se è ancora in linea."
    					);
    		}

    	}, delayMs, TimeUnit.MILLISECONDS);

    	silenceTasks.put(streamSid, task);
    }
}