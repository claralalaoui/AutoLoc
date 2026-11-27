package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Contrat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContratRepository extends JpaRepository<Contrat, Long> {

    @EntityGraph(attributePaths = {"paiements", "reservation", "reservation.client", "reservation.vehicule"})
    List<Contrat> findAllByOrderByDateSignatureDesc();

    @EntityGraph(attributePaths = {"paiements", "reservation", "reservation.client", "reservation.vehicule"})
    Optional<Contrat> findWithDetailsByIdContrat(Long idContrat);
}
