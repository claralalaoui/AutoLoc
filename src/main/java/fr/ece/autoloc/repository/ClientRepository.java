package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    List<Client> findAllByOrderByNomAscPrenomAsc();

    List<Client> findByUsernameOrderByNomAscPrenomAsc(String username);

    Optional<Client> findByIdClientAndUsername(Long idClient, String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdClientNot(String email, Long idClient);

    boolean existsByNumPermis(String numPermis);

    boolean existsByNumPermisAndIdClientNot(String numPermis, Long idClient);
}
