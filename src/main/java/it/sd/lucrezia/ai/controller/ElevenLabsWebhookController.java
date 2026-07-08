package it.sd.lucrezia.ai.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.sd.lucrezia.ai.service.elevenlabs.ElevenLabsService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/elevenlabs")
@RequiredArgsConstructor
public class ElevenLabsWebhookController {

    private final ElevenLabsService elevenLabsService;

    @GetMapping("/conversations")
    public String conversations() {
        return elevenLabsService.getConversations();
    }
    
}