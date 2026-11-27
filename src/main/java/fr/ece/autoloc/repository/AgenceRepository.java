package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Agence;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgenceRepository extends JpaRepository<Agence, Long> {

    @EntityGraph(attributePaths = "vehicules")
    List<Agence> findAllByOrderByNomAsc();
}
