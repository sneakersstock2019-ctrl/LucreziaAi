package it.sd.lucrezia.ai.service.openai;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.lucrezia.ai.bean.VoiceContext;
import it.sd.lucrezia.ai.prompt.LucreziaPromptBuilder;
import it.sd.lucrezia.ai.util.CallLogger;
import it.sd.lucrezia.ai.voice.websocket.IOpenAIRealtimeAudioListener;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OpenAIRealtimeService {

    @Value("${openai.api-key}")
    private String openAiApiKey;

    private final static String OPENAI_URL_MODEL = "wss://api.openai.com/v1/realtime?model=gpt-realtime-2";
    private final static String OPENAI_MODEL = "gpt-realtime-2";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LucreziaPromptBuilder promptBuilder;

    public WebSocketClient createVoiceClient(String callSid,
                                             String nome,
                                             String condominio,
                                             IOpenAIRealtimeAudioListener listener) throws Exception {

        URI uri = new URI(OPENAI_URL_MODEL);

        WebSocketClient client = new WebSocketClient(uri) {

            @Override
            public void onOpen(ServerHandshake handshakedata) {
                CallLogger.info(callSid, "OPENAI REALTIME VOICE CONNECTED - CALL SID = " + callSid);

                try {
                    sendSessionUpdate(this, nome, condominio);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(String message) {
                try {
                    JsonNode root = objectMapper.readTree(message);
                    String type = root.path("type").asText();

                    if ("error".equals(type)) {
                        CallLogger.info(callSid, "OPENAI REALTIME: ERROR EVENT RAW = " + message);
                        listener.onError(message);
                        return;
                    }

                    if ("session.updated".equals(type)) {
                        CallLogger.info(callSid, "OPENAI REALTIME:  VOICE SESSION UPDATED");
                        listener.onSessionReady();
                        return;
                    }

                    if ("input_audio_buffer.speech_started".equals(type)) {
                        CallLogger.info(callSid, "OPENAI REALTIME: UTENTE HA INIZIATO A PARLARE");
                        listener.onUserSpeechStarted();
                        return;
                    }

                    if ("input_audio_buffer.speech_stopped".equals(type)) {
                        CallLogger.info(callSid, "OPENAI REALTIME: UTENTE HA FINITO DI PARLARE");
                        listener.onUserSpeechStopped();
                        return;
                    }

                    if ("response.function_call_arguments.done".equals(type)) {
                        String callId = root.path("call_id").asText();
                        String name = root.path("name").asText();
                        String arguments = root.path("arguments").asText();

                        CallLogger.info(callSid, "OPENAI REALTIME: FUNCTION CALL = " + name);
                        CallLogger.info(callSid, "OPENAI REALTIME: ARGUMENTS = " + arguments);

                        listener.onFunctionCall(callId, name, arguments);
                        return;
                    }

                    if ("response.output_audio.delta".equals(type)) {
                        String audioDelta = root.path("delta").asText();

                        if (audioDelta != null && !audioDelta.isBlank()) {
                            listener.onAudioDelta(audioDelta);
                        }

                        return;
                    }

                    if ("response.output_audio_transcript.delta".equals(type)) {
                        String delta = root.path("delta").asText();

                        if (delta != null && !delta.isBlank()) {
                            listener.onAssistantTranscriptDelta(delta);
                        }

                        return;
                    }

                    if ("response.output_audio_transcript.done".equals(type)) {
                        String transcript = root.path("transcript").asText();

                        if (transcript != null && !transcript.isBlank()) {
                            listener.onAssistantTranscriptDone(transcript);
                        }

                        return;
                    }
                    
                    if ("conversation.item.input_audio_transcription.completed".equals(type)) {
                        String transcript = root.path("transcript").asText();

                        if (transcript != null && !transcript.isBlank()) {
                            listener.onUserTranscriptDone(transcript);
                        }

                        return;
                    }
                    
                    if ("response.output_audio.done".equals(type)) {
                        listener.onAssistantAudioDone();
                        CallLogger.info(callSid, "OPENAI REALTIME: AUDIO DONE");
                        return;
                    }

                    if ("response.done".equals(type)) {
                        CallLogger.info(callSid, "OPENAI REALTIME: RESPONSE DONE");
                        return;
                    }
                    
                    if ("session.created".equals(type)) {
                        CallLogger.info(callSid, "OPENAI REALTIME: SESSION CREATED");
                        return;
                    }

                } catch (Exception e) {
                    CallLogger.info(callSid, "OPENAI REALTIME: RAW MESSAGE = " + message);
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(ByteBuffer bytes) {
                CallLogger.info(callSid, "OPENAI REALTIME: BINARY MESSAGE ricevuto");
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                CallLogger.info(callSid, "OPENAI REALTIME CLOSED - code=" + code + ", reason=" + reason);
            }

            @Override
            public void onError(Exception ex) {
                CallLogger.info(callSid, "OPENAI REALTIME ERROR: " + ex.getMessage());
                ex.printStackTrace();
            }
        };

        client.addHeader("Authorization", "Bearer " + openAiApiKey);

        return client;
    }
    
    public void cancelResponse(WebSocketClient client, String callSid) {

        if (client == null || !client.isOpen()) {
            return;
        }

        try {
            Map<String, Object> event = Map.of(
                    "type", "response.cancel"
            );

            client.send(objectMapper.writeValueAsString(event));

            CallLogger.info(callSid, "RESPONSE CANCEL inviato a OpenAI");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendSessionUpdate(WebSocketClient client,
                                   String nome,
                                   String condominio) throws Exception {

        Map<String, Object> event = Map.of(
                "type", "session.update",
                "session", Map.of(
                        "type", "realtime",
                        "model", OPENAI_MODEL,
                        "output_modalities", new String[]{"audio"},
                        "instructions",
                        promptBuilder.buildVoiceSystemPrompt(nome, condominio)
                                + """
								Se il condomino vuole aprire una nuova segnalazione, raccogli prima:
								- che problema c'è;
								- dove si trova;
								- se riguarda una parte comune o privata;
								- eventuale urgenza.
								
								Quando hai informazioni sufficienti, usa il tool createTicket.
								
								Non aprire una segnalazione se non è chiaro dove si trova il problema.
								Dopo aver aperto la segnalazione, comunica il numero del ticket in modo naturale.
								Se il tool createTicket restituisce richiedi_foto=true, informa sempre il condomino che riceverà un messaggio WhatsApp sullo stesso numero per inviare eventuali foto.
                                Se richiedi_foto=false, non parlare di fotografie.
                                
                                
                                
                                Quando il condomino chiede informazioni su segnalazioni aperte, stato ticket,
                                aggiornamenti, interventi previsti o richieste già inviate, usa il tool getOpenTickets.

                                Non inventare mai dati sui ticket.
                                Se il tool restituisce ticket aperti, spiegali in modo semplice e naturale.
                                Se non ci sono ticket aperti, dillo chiaramente.
                                """,
                        "tools", new Object[]{
                                Map.of(
                                        "type", "function",
                                        "name", "getOpenTickets",
                                        "description", "Recupera le segnalazioni aperte del condomino che sta chiamando.",
                                        "parameters", Map.of(
                                                "type", "object",
                                                "properties", Map.of(),
                                                "required", new String[]{}
                                        )
                                ),
                                Map.of(
                                        "type", "function",
                                        "name", "createTicket",
                                        "description", "Apre una nuova segnalazione condominiale per il condomino che sta chiamando.",
                                        "parameters", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "categoria", Map.of(
                                                                "type", "string",
                                                                "description", "Categoria della segnalazione. Valori consigliati: elettricista, idraulico, ascensore, pulizia, cancello, generico."
                                                        ),
                                                        "priorita", Map.of(
                                                                "type", "string",
                                                                "description", "Priorità della segnalazione: bassa, media oppure alta."
                                                        ),
                                                        "descrizione", Map.of(
                                                                "type", "string",
                                                                "description", "Descrizione chiara e completa del problema segnalato dal condomino."
                                                        )
                                                ),
                                                "required", new String[]{
                                                        "categoria",
                                                        "priorita",
                                                        "descrizione"
                                                }
                                        )
                                ),
                                Map.of(
                                        "type", "function",
                                        "name", "endCall",
                                        "description", "Chiude cordialmente la chiamata telefonica dopo il saluto finale.",
                                        "parameters", Map.of(
                                                "type", "object",
                                                "properties", Map.of(),
                                                "required", new String[]{}
                                        )
                                )
                        },
                        "tool_choice", "auto",
                        "reasoning", Map.of(
                                "effort", "low"
                        ),
                        "audio", Map.of(
                        		"input", Map.of(
                        		        "format", Map.of(
                        		                "type", "audio/pcmu"
                        		        ),
                        		        "transcription", Map.of(
                        		                "model", "gpt-4o-transcribe",
                        		                "language", "it",
                        		                "prompt", "Trascrizione telefonica italiana di un condomino che parla con Lucrezia, assistente del condominio."
                        		        ),
                        		        "turn_detection", Map.of(
                        		                "type", "server_vad",
                        		                "threshold", 0.5,
                        		                "prefix_padding_ms", 300,
                        		                "silence_duration_ms", 500,
                        		                "create_response", false
                        		        )
                        		),
                                "output", Map.of(
                                        "format", Map.of(
                                                "type", "audio/pcmu"
                                        ),
                                        "voice", "marin"
                                )
                        )
                )
        );

        client.send(objectMapper.writeValueAsString(event));
    }

    public void sendInitialGreeting(WebSocketClient client,
    		VoiceContext context) throws Exception {

    	String userText =
    			promptBuilder.buildInitialGreetingUserText(context);

    	String instructions =
    			promptBuilder.buildInitialGreetingInstructions(context);

    	Map<String, Object> userMessage = Map.of(
    			"type", "conversation.item.create",
    			"item", Map.of(
    					"type", "message",
    					"role", "user",
    					"content", new Object[]{
    							Map.of(
    									"type", "input_text",
    									"text", userText
    									)
    					}
    					)
    			);

    	client.send(objectMapper.writeValueAsString(userMessage));

    	Map<String, Object> responseCreate = Map.of(
    			"type", "response.create",
    			"response", Map.of(
    					"instructions", instructions
    					)
    			);

    	client.send(objectMapper.writeValueAsString(responseCreate));
    }

    public void sendAudio(WebSocketClient client, String twilioBase64Payload, String callSid) {

        if (client == null || !client.isOpen()) {
            return;
        }

        try {
            Map<String, Object> event = Map.of(
                    "type", "input_audio_buffer.append",
                    "audio", twilioBase64Payload
            );

            client.send(objectMapper.writeValueAsString(event));

        } catch (org.java_websocket.exceptions.WebsocketNotConnectedException e) {
            CallLogger.info(callSid, "OPENAI REALTIME non connesso, audio ignorato.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendFunctionOutput(WebSocketClient client,
                                   String callId,
                                   String outputJson) throws Exception {

        Map<String, Object> functionOutput = Map.of(
                "type", "conversation.item.create",
                "item", Map.of(
                        "type", "function_call_output",
                        "call_id", callId,
                        "output", outputJson
                )
        );

        client.send(objectMapper.writeValueAsString(functionOutput));

        Map<String, Object> responseCreate = Map.of(
                "type", "response.create",
                "response", Map.of(
                        "instructions", """
                                Usa il risultato del tool per rispondere al condomino.
                                Spiega lo stato delle segnalazioni in modo semplice, naturale e breve.
                                Se non ci sono ticket aperti, digli che al momento non risultano segnalazioni aperte.
                                Non usare termini tecnici.
                                """
                )
        );

        client.send(objectMapper.writeValueAsString(responseCreate));
    }
    
    public void sendUserText(WebSocketClient client, String text) {

        if (client == null || !client.isOpen()) {
            return;
        }

        try {
            Map<String, Object> userMessage = Map.of(
                    "type", "conversation.item.create",
                    "item", Map.of(
                            "type", "message",
                            "role", "user",
                            "content", new Object[] {
                                    Map.of(
                                            "type", "input_text",
                                            "text", text
                                    )
                            }
                    )
            );

            client.send(objectMapper.writeValueAsString(userMessage));

            Map<String, Object> responseCreate = Map.of(
                    "type", "response.create"
            );

            client.send(objectMapper.writeValueAsString(responseCreate));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void createResponse(WebSocketClient client) {

        if (client == null || !client.isOpen()) {
            return;
        }

        try {
            Map<String, Object> responseCreate = Map.of(
                    "type", "response.create"
            );

            client.send(objectMapper.writeValueAsString(responseCreate));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}