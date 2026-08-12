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
    
    public Utente findFornitoreByTelefono(String telefono) {

        String sql = """
            SELECT
                u.id,
                u.nome,
                u.cognome,
                u.email,
                u.telefono,
                u.ruolo
            FROM utenti u
            WHERE RIGHT(
                      REGEXP_REPLACE(u.telefono, '[^0-9]', '', 'g'),
                      10
                  ) = RIGHT(
                      REGEXP_REPLACE(?, '[^0-9]', '', 'g'),
                      10
                  )
              AND u.ruolo = 'FORNITORE'
            LIMIT 1
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setString(1, telefono);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    Utente utente = new Utente();

                    utente.setId(rs.getLong("id"));
                    utente.setNome(rs.getString("nome"));
                    utente.setCognome(rs.getString("cognome"));
                    utente.setEmail(rs.getString("email"));
                    utente.setTelefono(rs.getString("telefono"));
                    utente.setRuolo(rs.getString("ruolo"));

                    return utente;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    
    public Utente findUtenteRegistratoByTelefono(String telefono) {

        String sql = """
            SELECT
                u.id,
                u.nome,
                u.cognome,
                u.email,
                u.telefono,
                u.ruolo,
                u.interno,
                c.id AS id_condominio,
                c.nome AS nome_condominio,
                c.indirizzo AS indirizzo_condominio
            FROM utenti u
            JOIN mappa_utenti_condomini muc
              ON muc.id_utente = u.id
            JOIN condomini c
              ON c.id = muc.id_condominio
            WHERE RIGHT(
                      REGEXP_REPLACE(COALESCE(u.telefono, ''), '[^0-9]', '', 'g'),
                      10
                  )
                  =
                  RIGHT(
                      REGEXP_REPLACE(COALESCE(?, ''), '[^0-9]', '', 'g'),
                      10
                  )
              AND u.ruolo = 'CONDOMINO'
            LIMIT 1
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setString(1, telefono);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    Utente utente = new Utente();

                    utente.setId(
                            rs.getLong("id")
                    );

                    utente.setNome(
                            rs.getString("nome")
                    );

                    utente.setCognome(
                            rs.getString("cognome")
                    );

                    utente.setEmail(
                            rs.getString("email")
                    );

                    utente.setTelefono(
                            rs.getString("telefono")
                    );

                    utente.setRuolo(
                            rs.getString("ruolo")
                    );

                    utente.setInterno(
                            rs.getString("interno")
                    );

                    utente.setIdCondominio(
                            rs.getLong("id_condominio")
                    );

                    utente.setNomeCondominio(
                            rs.getString("nome_condominio")
                    );

                    /*
                     * Se Utente non ha ancora questo campo,
                     * aggiungilo.
                     */
                    utente.setIndirizzoCondominio(
                            rs.getString("indirizzo_condominio")
                    );

                    return utente;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    
    public Utente findUtenteRegistratoByDati(
            String nome,
            String cognome,
            String indirizzoCondominio,
            String interno) {

        String sql = """
            SELECT
                u.id,
                u.nome,
                u.cognome,
                u.email,
                u.telefono,
                u.ruolo,
                u.interno,
                c.id AS id_condominio,
                c.nome AS nome_condominio,
                c.indirizzo AS indirizzo_condominio
            FROM utenti u
            JOIN mappa_utenti_condomini muc
              ON muc.id_utente = u.id
            JOIN condomini c
              ON c.id = muc.id_condominio
            WHERE LOWER(TRIM(u.nome)) = LOWER(TRIM(?))
              AND LOWER(TRIM(COALESCE(u.cognome, ''))) = LOWER(TRIM(?))
              AND LOWER(TRIM(COALESCE(c.indirizzo, ''))) = LOWER(TRIM(?))
              AND LOWER(TRIM(COALESCE(u.interno, ''))) = LOWER(TRIM(?))
              AND u.ruolo = 'CONDOMINO'
            LIMIT 1
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setString(1, nome);
            ps.setString(2, cognome);
            ps.setString(3, indirizzoCondominio);
            ps.setString(4, interno);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    Utente utente = new Utente();

                    utente.setId(
                            rs.getLong("id")
                    );

                    utente.setNome(
                            rs.getString("nome")
                    );

                    utente.setCognome(
                            rs.getString("cognome")
                    );

                    utente.setEmail(
                            rs.getString("email")
                    );

                    utente.setTelefono(
                            rs.getString("telefono")
                    );

                    utente.setRuolo(
                            rs.getString("ruolo")
                    );

                    utente.setInterno(
                            rs.getString("interno")
                    );

                    utente.setIdCondominio(
                            rs.getLong("id_condominio")
                    );

                    utente.setNomeCondominio(
                            rs.getString("nome_condominio")
                    );

                    utente.setIndirizzoCondominio(
                            rs.getString("indirizzo_condominio")
                    );

                    return utente;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    
    public Utente findById(Long idUtente) {

        if (idUtente == null) {
            return null;
        }

        String sql = """
            SELECT
                u.id,
                u.nome,
                u.cognome,
                u.email,
                u.telefono,
                u.ruolo,
                u.interno,
                c.id AS id_condominio,
                c.nome AS nome_condominio,
                c.indirizzo AS indirizzo_condominio,
                c.codice_fiscale AS codice_fiscale_condominio,
                c.elevenlabs_branch_id
            FROM utenti u
            LEFT JOIN mappa_utenti_condomini muc
                   ON muc.id_utente = u.id
            LEFT JOIN condomini c
                   ON c.id = muc.id_condominio
            WHERE u.id = ?
            LIMIT 1
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setLong(1, idUtente);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    Utente utente = new Utente();

                    utente.setId(
                            rs.getLong("id")
                    );

                    utente.setNome(
                            rs.getString("nome")
                    );

                    utente.setCognome(
                            rs.getString("cognome")
                    );

                    utente.setEmail(
                            rs.getString("email")
                    );

                    utente.setTelefono(
                            rs.getString("telefono")
                    );

                    utente.setRuolo(
                            rs.getString("ruolo")
                    );

                    utente.setInterno(
                            rs.getString("interno")
                    );

                    Long idCondominio =
                            rs.getObject(
                                    "id_condominio",
                                    Long.class
                            );

                    utente.setIdCondominio(
                            idCondominio
                    );

                    utente.setNomeCondominio(
                            rs.getString("nome_condominio")
                    );

                    utente.setIndirizzoCondominio(
                            rs.getString("indirizzo_condominio")
                    );

                    utente.setCodiceFiscaleCondominio(
                            rs.getString("codice_fiscale_condominio")
                    );

                    utente.setElevenlabsBranchId(
                            rs.getString("elevenlabs_branch_id")
                    );

                    return utente;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}