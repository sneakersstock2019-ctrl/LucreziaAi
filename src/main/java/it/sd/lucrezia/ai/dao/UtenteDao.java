package it.sd.lucrezia.ai.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import it.sd.lucrezia.ai.bean.Utente;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UtenteDao {

    private final DataSource dataSource;

    public Utente findCondominoByTelefono(String telefono) {

        String sql = """
            SELECT
                u.id,
                u.nome,
                u.cognome,
                u.email,
                u.telefono,
                u.ruolo,
                c.id AS id_condominio,
                c.nome AS nome_condominio,
                c.codice_fiscale AS codice_fiscale_condominio,
                c.elevenlabs_branch_id
            FROM utenti u
            JOIN mappa_utenti_condomini muc
              ON muc.id_utente = u.id
            JOIN condomini c
              ON c.id = muc.id_condominio
            WHERE RIGHT(regexp_replace(u.telefono,'[^0-9]','','g'),10) = RIGHT(regexp_replace(?,'[^0-9]','','g'),10)
              AND u.ruolo = 'CONDOMINO'
            LIMIT 1
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setString(1, telefono);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                Utente utente = new Utente();

                utente.setId(rs.getLong("id"));
                utente.setNome(rs.getString("nome"));
                utente.setCognome(rs.getString("cognome"));
                utente.setEmail(rs.getString("email"));
                utente.setTelefono(rs.getString("telefono"));
                utente.setRuolo(rs.getString("ruolo"));

                utente.setIdCondominio(
                        rs.getLong("id_condominio")
                );

                utente.setNomeCondominio(
                        rs.getString("nome_condominio")
                );

                utente.setCodiceFiscaleCondominio(
                        rs.getString("codice_fiscale_condominio")
                );

                utente.setElevenlabsBranchId(
                        rs.getString("elevenlabs_branch_id")
                );

                return utente;
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Errore nella ricerca del condomino per telefono "
                            + telefono,
                    e
            );
        }
    }
}