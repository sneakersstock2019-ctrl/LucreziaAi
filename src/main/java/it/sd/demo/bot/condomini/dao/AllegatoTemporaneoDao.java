package it.sd.demo.bot.condomini.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import it.sd.demo.bot.condomini.bean.AllegatoTemporaneo;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AllegatoTemporaneoDao {

    private final DataSource dataSource;

    public void insert(String telefono,
                       String tipo,
                       String mediaId,
                       String contentType,
                       String nomeFile) {

        String sql = """
            INSERT INTO allegati_temporanei (
                telefono,
                tipo,
                media_id,
                content_type,
                nome_file
            )
            VALUES (?, ?, ?, ?, ?)
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, telefono);
            ps.setString(2, tipo);
            ps.setString(3, mediaId);
            ps.setString(4, contentType);
            ps.setString(5, nomeFile);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<AllegatoTemporaneo> findByTelefono(String telefono) {

        List<AllegatoTemporaneo> lista = new ArrayList<>();

        String sql = """
            SELECT id, telefono, tipo, media_id, content_type, nome_file, data_creazione
            FROM allegati_temporanei
            WHERE telefono = ? AND 
                  data_creazione >= CURRENT_TIMESTAMP - INTERVAL '15 minutes'
            ORDER BY data_creazione ASC
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, telefono);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AllegatoTemporaneo a = new AllegatoTemporaneo();
                    a.setId(rs.getLong("id"));
                    a.setTelefono(rs.getString("telefono"));
                    a.setTipo(rs.getString("tipo"));
                    a.setMediaId(rs.getString("media_id"));
                    a.setContentType(rs.getString("content_type"));
                    a.setNomeFile(rs.getString("nome_file"));
                    a.setDataCreazione(rs.getTimestamp("data_creazione").toLocalDateTime());
                    lista.add(a);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return lista;
    }

    public void deleteByTelefono(String telefono) {

        String sql = """
            DELETE FROM allegati_temporanei
            WHERE telefono = ? OR 
                  data_creazione < CURRENT_TIMESTAMP - INTERVAL '15 minutes'
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, telefono);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}