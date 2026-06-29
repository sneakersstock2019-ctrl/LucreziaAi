package it.sd.demo.bot.condomini.realtime.tool;

import org.springframework.stereotype.Component;

import it.sd.demo.bot.condomini.bean.VoiceContext;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EndCallTool implements LucreziaTool {

    @Override
    public String getName() {
        return "endCall";
    }

    @Override
    public String execute(String arguments, VoiceContext context) {
        context.setEndCallRequested(true);
        
        if(context.getMotivoChiusura().equals("IN_CORSO")) {
        	context.setMotivoChiusura("LUCREZIA_HA_CHIUSO");
        }

        return """
            {"esito":"OK","messaggio":"Chiamata da chiudere dopo il saluto finale."}
            """;
    }
}