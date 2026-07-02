package it.sd.lucrezia.ai.tool;

import org.springframework.stereotype.Component;

import it.sd.lucrezia.ai.bean.VoiceContext;
import it.sd.lucrezia.ai.util.CallLogger;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EndCallTool implements LucreziaTool {

    @Override
    public String getName() {
        return "endCall";
    }

    @Override
    public String execute(String arguments, VoiceContext voiceContext) {
    	CallLogger.info(voiceContext, "TOOL endCall - richiesta chiusura chiamata");
        voiceContext.setEndCallRequested(true);
        
        if (voiceContext.getMotivoChiusura() == null
                || "IN_CORSO".equals(voiceContext.getMotivoChiusura())) {
            voiceContext.setMotivoChiusura("LUCREZIA_HA_CHIUSO");
        }

        if (voiceContext.getEsitoTelefonata() == null
                || "IN_CORSO".equals(voiceContext.getEsitoTelefonata())) {
            voiceContext.setEsitoTelefonata("NESSUN_TICKET");
        }
        
        return """
            {"esito":"OK","messaggio":"Chiamata da chiudere dopo il saluto finale."}
            """;
    }
}