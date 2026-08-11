package it.sd.lucrezia.ai.service.voice;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class UnknownUserSearchAttemptService {

    private static final int MAX_TENTATIVI = 3;

    private final Map<Long, Integer> attempts =
            new ConcurrentHashMap<>();

    public int increment(Long idTelefonata) {

        if (idTelefonata == null) {
            return 1;
        }

        return attempts.merge(
                idTelefonata,
                1,
                Integer::sum
        );
    }

    public int get(Long idTelefonata) {

        if (idTelefonata == null) {
            return 0;
        }

        return attempts.getOrDefault(
                idTelefonata,
                0
        );
    }

    public boolean maxReached(Long idTelefonata) {
        return get(idTelefonata) >= MAX_TENTATIVI;
    }

    public void clear(Long idTelefonata) {

        if (idTelefonata != null) {
            attempts.remove(idTelefonata);
        }
    }
}