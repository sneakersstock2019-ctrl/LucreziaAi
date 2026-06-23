package it.sd.demo.bot.condomini.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import it.sd.demo.bot.condomini.bean.UserSession;
import it.sd.demo.bot.condomini.bean.VoiceSessionStep;

@Service
public class VoiceSessionService {

    private final Map<String, UserSession> sessions =
            new ConcurrentHashMap<>();

    public UserSession getOrCreateVoiceSession(String phoneNumber) {

        UserSession session = sessions.get(phoneNumber);

        if (session == null) {

            session = new UserSession();

            session.nome = "Condomino";
            session.setVoiceSessionStep(VoiceSessionStep.NEW_TICKET);
            session.primoMessaggio = true;

            sessions.put(phoneNumber, session);
        }

        return session;
    }

    public void removeSession(String phoneNumber) {
        sessions.remove(phoneNumber);
    }
}