package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.VehiculePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VehiculePhotoRepository extends JpaRepository<VehiculePhoto, Long> {

    /** Which vehicles have a photo, without loading the image bytes. */
    @Query("select p.idVehicule as idVehicule, p.miseAJour as miseAJour from VehiculePhoto p")
    List<PhotoInfo> findAllInfos();

    @Query("select p.miseAJour from VehiculePhoto p where p.idVehicule = :id")
    Optional<LocalDateTime> findMiseAJour(@Param("id") Long idVehicule);

    interface PhotoInfo {
        Long getIdVehicule();

        LocalDateTime getMiseAJour();
    }
}
