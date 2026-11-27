package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {

    @Query("select coalesce(sum(p.montant), 0) from Paiement p")
    BigDecimal totalEncaisse();
}
