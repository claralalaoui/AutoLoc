package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehiculeRepository extends JpaRepository<Vehicule, Long> {

    @EntityGraph(attributePaths = "agence")
    List<Vehicule> findAllByOrderByMarqueAscModeleAsc();

    @EntityGraph(attributePaths = "agence")
    Optional<Vehicule> findWithAgenceByIdVehicule(Long idVehicule);

    boolean existsByImmatriculation(String immatriculation);

    boolean existsByImmatriculationAndIdVehiculeNot(String immatriculation, Long idVehicule);

    long countByStatut(StatutVehicule statut);
}
