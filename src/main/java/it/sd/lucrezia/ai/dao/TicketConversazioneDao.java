package it.sd.lucrezia.ai.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TicketConversazioneDao {

    private final DataSource dataSource;

    public void insertConversazione(Long idTicket, String contenuto) {

        String sql = """
            INSERT INTO whatsapp (
                id_ticket,
                contenuto
            )
            VALUES (?, ?)
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, idTicket);
            ps.setString(2, contenuto);

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