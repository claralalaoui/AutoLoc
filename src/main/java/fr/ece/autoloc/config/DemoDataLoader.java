package fr.ece.autoloc.config;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.VehiculeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataLoader implements CommandLineRunner {

    private final VehiculeRepository vehiculeRepository;

    @Override
    public void run(String... args) {
        if (vehiculeRepository.count() > 0) {
            log.info("Demo data already present, nothing inserted");
            return;
        }

        vehiculeRepository.saveAll(List.of(
                new Vehicule(null, "AB-123-CD", "Renault", "Clio",
                        CategorieVehicule.CITADINE, new BigDecimal("35.00"), StatutVehicule.DISPONIBLE),
                new Vehicule(null, "EF-456-GH", "Peugeot", "508",
                        CategorieVehicule.BERLINE, new BigDecimal("60.00"), StatutVehicule.DISPONIBLE),
                new Vehicule(null, "IJ-789-KL", "Dacia", "Duster",
                        CategorieVehicule.SUV, new BigDecimal("50.00"), StatutVehicule.MAINTENANCE)
        ));

        log.info("{} demo vehicles inserted", vehiculeRepository.count());
    }
}
