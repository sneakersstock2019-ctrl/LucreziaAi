package it.sd.demo.bot.condomini.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TicketConversazioneDao {

    private final DataSource dataSource;

    public void insertConversazione(Long idTicket,
                                    String canale,
                                    String tipo,
                                    String contenuto,
                                    String urlAudio) {

        String sql = """
            INSERT INTO ticket_conversazioni (
                id_ticket,
                canale,
                tipo,
                contenuto,
                url_audio
            )
            VALUES (?, ?, ?, ?, ?)
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, idTicket);
            ps.setString(2, canale);
            ps.setString(3, tipo);
            ps.setString(4, contenuto);
            ps.setString(5, urlAudio);

            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void updateAudioUrlByTicket(Long idTicket, String audioUrl) {
        String sql = """
            UPDATE ticket_conversazioni
            SET url_audio = ?
            WHERE id_ticket = ?
              AND canale = 'TELEFONO'
        """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, audioUrl);
            ps.setLong(2, idTicket);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}