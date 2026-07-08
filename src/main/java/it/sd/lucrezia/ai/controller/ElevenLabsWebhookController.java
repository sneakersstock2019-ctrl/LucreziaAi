package it.sd.lucrezia.ai.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.sd.lucrezia.ai.dao.TelefonataDao;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/elevenlabs/webhook")
@RequiredArgsConstructor
public class ElevenLabsWebhookController {

    private final TelefonataDao telefonataDao;

    @PostMapping("/post-call")
    public ResponseEntity<String> postCall(@RequestBody Map<String, Object> body) {

        System.out.println("############################");
        System.out.println("ELEVENLABS POST CALL WEBHOOK");
        System.out.println("BODY = " + body);
        System.out.println("############################");

        // Primo giro: logghiamo il payload reale.
        // Dopo il primo test mappiamo precisamente conversation_id, transcript, duration, audio.

        return ResponseEntity.ok("OK");
    }
}