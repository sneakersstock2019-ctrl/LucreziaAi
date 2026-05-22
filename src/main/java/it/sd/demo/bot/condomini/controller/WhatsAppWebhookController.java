package it.sd.demo.bot.condomini.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import it.sd.demo.bot.condomini.service.WhatsAppService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class WhatsAppWebhookController {

	private final WhatsAppService whatsAppService;
	
    @Value("${whatsapp.verify-token}")
    private String verifyToken;

    // VERIFICA META (OBBLIGATORIA)
    @GetMapping("/webhook")
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            return ResponseEntity.ok(challenge);
        }

        return ResponseEntity.status(403).body("Forbidden");
    }

    // RICEZIONE MESSAGGI
    @PostMapping("/webhook")
    public ResponseEntity<String> receive(@RequestBody String body) {

        System.out.println("Ricevuto webhook:");
        System.out.println(body);
        
        whatsAppService.elaboraMessaggio(body);

        return ResponseEntity.ok("EVENT_RECEIVED");
        
    }
}