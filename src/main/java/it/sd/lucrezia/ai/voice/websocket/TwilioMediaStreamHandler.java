package it.sd.lucrezia.ai.voice.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.lucrezia.ai.bean.TicketStatusInfo;
import it.sd.lucrezia.ai.bean.VoiceContext;
import it.sd.lucrezia.ai.dao.TelefonataDao;
import it.sd.lucrezia.ai.dao.TicketDao;
import it.sd.lucrezia.ai.service.openai.OpenAIRealtimeService;
import it.sd.lucrezia.ai.service.twilio.TwilioRecordingService;
import it.sd.lucrezia.ai.tool.LucreziaToolDispatcher;
import it.sd.lucrezia.ai.util.CallLogger;
import it.sd.lucrezia.ai.voice.filter.SpeechFilter;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TwilioMediaStreamHandler extends TextWebSocketHandler {

	private static final long BARGE_IN_DEBOUNCE_MS = 700;
	
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OpenAIRealtimeService openAIRealtimeClient;
    private final LucreziaToolDispatcher toolDispatcher;
    private final TicketDao ticketDao;
    private final TwilioRecordingService twilioRecordingService;
    private final TelefonataDao telefonataDao;

    private final Map<String, Integer> chunkCounter = new ConcurrentHashMap<>();
    private final Map<String, WebSocketClient> openAiClients = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> twilioSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToStreamSid = new ConcurrentHashMap<>();
    private final Map<String, Boolean> assistantSpeaking = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> silenceTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> bargeInTasks = new ConcurrentHashMap<>();
    private final Map<String, VoiceContext> voiceContexts = new ConcurrentHashMap<>();
    private final Map<String, Boolean> twilioAudioPlaying = new ConcurrentHashMap<>();

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
                boolean salutoVip = params.path("salutoVip").asBoolean(false);

                List<TicketStatusInfo> ticketAperti = new ArrayList<>();
                try {
                    ticketAperti = ticketDao.findOpenTicketsByUtente(idUtente);
                } catch (Exception e) {
                    CallLogger.info(callSid, "Errore recupero ticket aperti realtime:");
                    e.printStackTrace();
                }

                VoiceContext voiceContext = new VoiceContext();
                voiceContext.setPhone(phone);
                voiceContext.setNome(nome);
                voiceContext.setCondominio(condominio);
                voiceContext.setIdUtente(idUtente);
                voiceContext.setTicketAperti(ticketAperti);
                voiceContext.setIdCondominio(idCondominio);
                voiceContext.setCallSid(callSid);
                voiceContext.setRecordingSid(recordingSid);
                voiceContext.setSalutoVip(salutoVip);
                voiceContext.setMotivoChiusura("IN_CORSO");

                Long idTelefonata = telefonataDao.insertTelefonata(
                        callSid,
                        phone,
                        idUtente,
                        idCondominio
                );

                voiceContext.setIdTelefonata(idTelefonata);
                voiceContext.setStartCallMillis(System.currentTimeMillis());
                voiceContext.setEsitoTelefonata("IN_CORSO");

                String audioUrl = twilioRecordingService.buildRecordingMp3Url(recordingSid);
                telefonataDao.updateAudioUrl(idTelefonata, audioUrl, voiceContext.getCallSid());

                CallLogger.info(callSid, "TELEFONATA CREATA - idTelefonata = " + idTelefonata);
                CallLogger.info(callSid, "TELEFONATA AUDIO URL = " + audioUrl);

                chunkCounter.put(streamSid, 0);
                sessionToStreamSid.put(session.getId(), streamSid);
                twilioSessions.put(streamSid, session);
                voiceContexts.put(streamSid, voiceContext);
                assistantSpeaking.put(streamSid, false);
                twilioAudioPlaying.put(streamSid, false);

                CallLogger.info(callSid, "############################");
                CallLogger.info(callSid, "MEDIA STREAM EVENT: start");
                CallLogger.info(callSid, "STREAM SID = " + streamSid);
                CallLogger.info(callSid, "CALL SID = " + callSid);
                CallLogger.info(callSid, "RECORDING_SID = " + recordingSid);
                CallLogger.info(callSid, "PARAM PHONE = " + phone);
                CallLogger.info(callSid, "PARAM NOME = " + nome);
                CallLogger.info(callSid, "PARAM CONDOMINIO = " + condominio);
                CallLogger.info(callSid, "PARAM ID_UTENTE = " + idUtente);
                CallLogger.info(callSid, "PARAM ID_CONDOMINIO = " + idCondominio);
                CallLogger.info(callSid, "PARAM SALUTO_VIP = " + salutoVip);
                CallLogger.info(callSid, "TICKET APERTI = " + ticketAperti.size());
                CallLogger.info(callSid, "############################");

                CallLogger.info(callSid, "Apro connessione OpenAI Realtime Voice ...");
                IOpenAIRealtimeAudioListener listener = new IOpenAIRealtimeAudioListener() {

                    @Override
                    public void onSessionReady() {
                        try {
                            WebSocketClient client = openAiClients.get(streamSid);

                            if (client != null && client.isOpen()) {
                                openAIRealtimeClient.sendInitialGreeting(client, voiceContext);
                                CallLogger.info(callSid, "SALUTO INIZIALE INVIATO DOPO SESSION READY");
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onAssistantTranscriptDelta(String delta) {
                    }

                    @Override
                    public void onAssistantTranscriptDone(String transcript) {
                        CallLogger.info(callSid, "LUCREZIA HA DETTO:" + transcript);
                        voiceContext.setTrascrizioneChiamata(voiceContext.getTrascrizioneChiamata() + "\nLucrezia: " + transcript + "\n"
                        );
                    }
                    
                    @Override
                    public void onUserTranscriptDone(String transcript) {
                    	CallLogger.info(callSid, "UTENTE HA DETTO: " + transcript);
                    	CallLogger.info(callSid, "DURANTE QUESTO CONTESTO: " + voiceContext);
                    	
                    	if (!voiceContext.isInitialGreetingCompleted()) {
                            CallLogger.info(callSid, "INPUT UTENTE ignorato durante saluto iniziale: " + transcript);
                            return;
                        }
                    	
                        if (SpeechFilter.isNoiseOrFiller(transcript)) {
                            CallLogger.info(callSid, "INPUT UTENTE ignorato come rumore/filler: " + transcript);
                            return;
                        }

                        voiceContext.setTrascrizioneChiamata(
                                voiceContext.getTrascrizioneChiamata()
                                        + "\nCondomino: " + transcript + "\n"
                        );

                        WebSocketClient client = openAiClients.get(streamSid);
                        if (client != null && client.isOpen()) {
                            openAIRealtimeClient.createResponse(client);
                        }
                    }

                    @Override
                    public void onError(String rawMessage) {
                        CallLogger.info(callSid, "OPENAI REALTIME LISTENER ERROR: " + rawMessage);
                    }

                    @Override
                    public void onFunctionCall(String callId, String name, String arguments) {

                        try {
                            String outputJson = toolDispatcher.execute(name, arguments, voiceContext);

                            if (outputJson == null) {
                                CallLogger.info(callSid, "TOOL " + name + " eseguito senza output verso OpenAI");

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
                        sendAudioToTwilio(streamSid, base64Audio, callSid);
                    }
                    
                    @Override
                    public void onAssistantAudioDone() {

                        assistantSpeaking.put(streamSid, false);

                        sendMarkToTwilio(streamSid);

                        if (voiceContext.isEndCallRequested()) {
                            closeTwilioCall(streamSid);
                            return;
                        }

                        scheduleSilenceCheck(streamSid, voiceContext, 60000);
                    }

                    @Override
                    public void onUserSpeechStarted() {
                    	
                        voiceContext.setLastUserSpeechTime(System.currentTimeMillis());

                        ScheduledFuture<?> silenceTask = silenceTasks.remove(streamSid);
                        if (silenceTask != null) {
                            silenceTask.cancel(false);
                        }

                        if (!voiceContext.isInitialGreetingCompleted()) {
                            CallLogger.info(voiceContext, "BARGE-IN ignorato: saluto iniziale ancora in corso");
                            return;
                        }

                        boolean openAiSpeaking = Boolean.TRUE.equals(assistantSpeaking.get(streamSid));
                        boolean twilioPlaying = Boolean.TRUE.equals(twilioAudioPlaying.get(streamSid));

                        if (!openAiSpeaking && !twilioPlaying) {
                            return;
                        }

                        ScheduledFuture<?> oldBargeTask = bargeInTasks.remove(streamSid);
                        if (oldBargeTask != null) {
                            oldBargeTask.cancel(false);
                        }

                        ScheduledFuture<?> bargeTask = scheduler.schedule(() -> {

                        	boolean openAiSpeakingNow = Boolean.TRUE.equals(assistantSpeaking.get(streamSid));
                        	boolean twilioPlayingNow = Boolean.TRUE.equals(twilioAudioPlaying.get(streamSid));

                        	if (!openAiSpeakingNow && !twilioPlayingNow) {
                        	    return;
                        	}

                        	CallLogger.info(voiceContext, "BARGE-IN confermato dopo debounce");

                        	sendClearToTwilio(streamSid);

                        	if (openAiSpeakingNow) {
                        	    WebSocketClient client = openAiClients.get(streamSid);
                        	    if (client != null && client.isOpen()) {
                        	        openAIRealtimeClient.cancelResponse(client, callSid);
                        	    }
                        	} else {
                        	    CallLogger.info(voiceContext, "RESPONSE CANCEL non inviato: OpenAI non sta generando");
                        	}

                        	assistantSpeaking.put(streamSid, false);
                        	twilioAudioPlaying.put(streamSid, false);
                        	voiceContext.incrementNumeroInterruzioni();

                        }, BARGE_IN_DEBOUNCE_MS, TimeUnit.MILLISECONDS);

                        bargeInTasks.put(streamSid, bargeTask);
                    }
                    
                    @Override
                    public void onUserSpeechStopped() {

                        ScheduledFuture<?> bargeTask = bargeInTasks.remove(streamSid);

                        if (bargeTask != null) {
                            bargeTask.cancel(false);
                            CallLogger.info(voiceContext, "BARGE-IN annullato: parlato troppo breve");
                        }
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
                    openAIRealtimeClient.sendAudio(openAiClient, payload, voiceContexts.get(streamSid).getCallSid());
                }

                if (count % 100 == 0) {
                    CallLogger.info(voiceContexts.get(streamSid), "MEDIA CHUNK inviati a OpenAI per stream " + streamSid + ": " + count);
                }
            }
            
            case "mark" -> {
                String streamSid = root.path("streamSid").asText();
                VoiceContext context = voiceContexts.get(streamSid);

                CallLogger.info(context, "LUCREZIA HA FINITO DI PARLARE");
                
                twilioAudioPlaying.put(streamSid, false);

                CallLogger.info(context, "TWILIO MARK ricevuto - audio riprodotto completamente");

                if (context != null && !context.isInitialGreetingCompleted()) {
                    context.setInitialGreetingCompleted(true);
                    CallLogger.info(context, "SALUTO INIZIALE COMPLETATO - barge-in abilitato");
                }
            }

            case "stop" -> {
                String streamSid = root.path("streamSid").asText();

                Integer total = chunkCounter.remove(streamSid);

                VoiceContext context = voiceContexts.remove(streamSid);
                chiudiTelefonataSuDb(context);

                closeOpenAiClient(streamSid);
                twilioSessions.remove(streamSid);
                assistantSpeaking.remove(streamSid);
                twilioAudioPlaying.remove(streamSid);

                java.util.concurrent.ScheduledFuture<?> task = silenceTasks.remove(streamSid);
                if (task != null) {
                    task.cancel(false);
                }
                
                ScheduledFuture<?> bargeTask = bargeInTasks.remove(streamSid);
                if (bargeTask != null) {
                    bargeTask.cancel(false);
                }

                CallLogger.info(voiceContexts.get(streamSid), "############################");
                CallLogger.info(voiceContexts.get(streamSid), "MEDIA STREAM EVENT: stop");
                CallLogger.info(voiceContexts.get(streamSid), "STREAM SID = " + streamSid);
                CallLogger.info(voiceContexts.get(streamSid), "MEDIA CHUNK TOTALI = " + total);
                CallLogger.info(voiceContexts.get(streamSid), "############################");
            }

            default -> {
            	String streamSid = root.path("streamSid").asText();
                CallLogger.info(voiceContexts.get(streamSid), "MEDIA STREAM EVENT non gestito: " + event);
            }
        }
    }

    private void sendAudioToTwilio(String streamSid, String base64Audio, String callSid) {

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

            if (!Boolean.TRUE.equals(twilioAudioPlaying.get(streamSid))) {

                twilioAudioPlaying.put(streamSid, true);

                CallLogger.info(callSid, "LUCREZIA HA INIZIATO A PARLARE");
            }
            
            synchronized (twilioSession) {
                twilioSession.sendMessage(new TextMessage(json));
            }

        } catch (Exception e) {
            CallLogger.info(voiceContexts.get(streamSid), "ERRORE INVIO AUDIO A TWILIO:");
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      org.springframework.web.socket.CloseStatus status) {

        String streamSid = sessionToStreamSid.remove(session.getId());

        if (streamSid != null) {
            String callSid = "UNKNOWN";

            VoiceContext context = voiceContexts.get(streamSid);
            if (context != null && context.getCallSid() != null) {
                callSid = context.getCallSid();
            }

            CallLogger.info(callSid, "############################");
            CallLogger.info(callSid, "TWILIO MEDIA STREAM CLOSED");
            CallLogger.info(callSid, "SESSION ID = " + session.getId());
            CallLogger.info(callSid, "STATUS = " + status);
            CallLogger.info(callSid, "############################");

            closeOpenAiClient(streamSid);
            chunkCounter.remove(streamSid);
            twilioSessions.remove(streamSid);
            assistantSpeaking.remove(streamSid);
            twilioAudioPlaying.remove(streamSid);
            voiceContexts.remove(streamSid);

            java.util.concurrent.ScheduledFuture<?> task = silenceTasks.remove(streamSid);
            if (task != null) {
                task.cancel(false);
            }
            
            ScheduledFuture<?> bargeTask = bargeInTasks.remove(streamSid);
            if (bargeTask != null) {
                bargeTask.cancel(false);
            }

            return;
        }

        CallLogger.info("UNKNOWN", "TWILIO MEDIA STREAM CLOSED");
        CallLogger.info("UNKNOWN", "SESSION ID = " + session.getId());
        CallLogger.info("UNKNOWN", "STATUS = " + status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {

        String streamSid = sessionToStreamSid.remove(session.getId());

        if (streamSid != null) {
            String callSid = "UNKNOWN";

            VoiceContext context = voiceContexts.get(streamSid);
            if (context != null && context.getCallSid() != null) {
                callSid = context.getCallSid();
            }
            
            CallLogger.info(callSid, "############################");
            CallLogger.info(callSid, "TWILIO MEDIA STREAM ERROR");
            CallLogger.info(callSid, "SESSION ID = " + session.getId());
            CallLogger.info(callSid, "ERROR = " + exception.getMessage());
            CallLogger.info(callSid, "############################");
            
            closeOpenAiClient(streamSid);
            chunkCounter.remove(streamSid);
            twilioSessions.remove(streamSid);
            assistantSpeaking.remove(streamSid);
            twilioAudioPlaying.remove(streamSid);
            voiceContexts.remove(streamSid);
            
            java.util.concurrent.ScheduledFuture<?> task = silenceTasks.remove(streamSid);
            if (task != null) {
                task.cancel(false);
            }
            
            ScheduledFuture<?> bargeTask = bargeInTasks.remove(streamSid);
            if (bargeTask != null) {
                bargeTask.cancel(false);
            }
        }

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
        VoiceContext voiceContext = voiceContexts.get(streamSid);

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

            CallLogger.info(voiceContext, "CLEAR inviato a Twilio per stream " + streamSid);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void closeTwilioCall(String streamSid) {
    	VoiceContext voiceContext = voiceContexts.get(streamSid);
    	if (voiceContext != null) {
    	    chiudiTelefonataSuDb(voiceContext);
    	}
    	
        WebSocketSession twilioSession = twilioSessions.get(streamSid);

        if (twilioSession == null || !twilioSession.isOpen()) {
            return;
        }

        scheduler.schedule(() -> {

            try {

                if (twilioSession.isOpen()) {
                    twilioSession.close();
                    CallLogger.info(voiceContext, "CHIAMATA CHIUSA DA LUCREZIA - streamSid=" + streamSid);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }, 5, TimeUnit.SECONDS);
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
    
    private void chiudiTelefonataSuDb(VoiceContext voiceContext) {

        if (voiceContext == null) {
            return;
        }

        if (voiceContext.getIdTelefonata() == null) {
            return;
        }

        long durataSecondi = 0;

        if (voiceContext.getStartCallMillis() > 0) {
            durataSecondi =
                    (System.currentTimeMillis() - voiceContext.getStartCallMillis()) / 1000;
        }

        String esito = voiceContext.getEsitoTelefonata();

        if (esito == null || esito.isBlank() || "IN_CORSO".equals(esito)) {
            esito = voiceContext.getIdTicketCreato() != null
                    ? "TICKET_APERTO"
                    : "NESSUN_TICKET";
        }

        String motivoChiusura = voiceContext.getMotivoChiusura();

        if (motivoChiusura == null || motivoChiusura.isBlank() || "IN_CORSO".equals(motivoChiusura)) {
            motivoChiusura = esito;
        }

        telefonataDao.chiudiTelefonata(
                voiceContext.getIdTelefonata(),
                esito,
                motivoChiusura,
                voiceContext.getTrascrizioneChiamata(),
                durataSecondi,
                voiceContext.getNumeroInterruzioni(),
                voiceContext.getNumeroTool(),
                voiceContext.getCallSid()
        );

        CallLogger.info(voiceContext, "TELEFONATA CHIUSA - idTelefonata = "
                + voiceContext.getIdTelefonata()
                + " esito = " + esito
                + " motivoChiusura = " + motivoChiusura
                + " durataSecondi = " + durataSecondi);
    }
    
    private void sendMarkToTwilio(String streamSid) {

        WebSocketSession twilioSession = twilioSessions.get(streamSid);
        VoiceContext context = voiceContexts.get(streamSid);

        if (twilioSession == null || !twilioSession.isOpen()) {
            return;
        }

        try {
            twilioAudioPlaying.put(streamSid, true);

            Map<String, Object> event = Map.of(
                    "event", "mark",
                    "streamSid", streamSid,
                    "mark", Map.of(
                            "name", "lucrezia-audio"
                    )
            );

            synchronized (twilioSession) {
                twilioSession.sendMessage(
                        new TextMessage(objectMapper.writeValueAsString(event))
                );
            }

            CallLogger.info(context, "TWILIO MARK inviato - attendo fine riproduzione audio");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}