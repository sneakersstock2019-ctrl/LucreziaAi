package it.sd.demo.bot.condomini.service;

public interface OpenAIRealtimeAudioListener {

    void onSessionReady();

    void onAudioDelta(String base64Audio);

    void onAssistantTranscriptDelta(String delta);

    void onAssistantTranscriptDone(String transcript);

    void onError(String rawMessage);
}