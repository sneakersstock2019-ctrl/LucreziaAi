package it.sd.demo.bot.condomini.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class TwilioMediaStreamHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

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

            case "connected":
                System.out.println("MEDIA STREAM EVENT: connected");
                break;

            case "start":
                System.out.println("MEDIA STREAM EVENT: start");
                System.out.println("STREAM SID = " + root.path("start").path("streamSid").asText());
                System.out.println("CALL SID = " + root.path("start").path("callSid").asText());
                break;

            case "media":
                String payload = root.path("media").path("payload").asText();
                String track = root.path("media").path("track").asText();

                System.out.println("MEDIA CHUNK ricevuto. track=" + track + ", payloadLength=" + payload.length());
                break;

            case "stop":
                System.out.println("MEDIA STREAM EVENT: stop");
                break;

            default:
                System.out.println("MEDIA STREAM EVENT non gestito: " + event);
        }
    }
}