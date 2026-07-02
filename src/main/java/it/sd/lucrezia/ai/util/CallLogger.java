package it.sd.lucrezia.ai.util;

import it.sd.lucrezia.ai.bean.VoiceContext;

public final class CallLogger {

    private CallLogger() {
    }

    public static void info(VoiceContext context, String message) {
        String callSid = context != null ? context.getCallSid() : null;
        info(callSid, message);
    }

    public static void info(String callSid, String message) {
        if (callSid == null || callSid.isBlank()) {
            System.out.println("[CALL_SID=UNKNOWN] " + message);
            return;
        }

        System.out.println("[CALL_SID=" + callSid + "] " + message);
    }
}