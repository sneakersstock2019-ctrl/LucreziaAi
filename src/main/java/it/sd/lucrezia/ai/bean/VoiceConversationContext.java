package it.sd.lucrezia.ai.bean;

import java.util.Map;

public record VoiceConversationContext(
        Long idTelefonata,
        Utente utente,
        int ticketAperti,
        String branchId,
        String firstMessage,
        Map<String, Object> dynamicVariables
) {
}