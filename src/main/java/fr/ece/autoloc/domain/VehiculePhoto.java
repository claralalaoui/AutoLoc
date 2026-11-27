package fr.ece.autoloc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Photo of a vehicle, kept in its own table so that the image bytes are never loaded
 * with the vehicle list. Its primary key is the vehicle id (@MapsId): at most one photo per vehicle.
 */
@Entity
@Table(name = "vehicule_photo")
@Getter
@Setter
@NoArgsConstructor
public class VehiculePhoto {

    @Id
    private Long idVehicule;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vehicule")
    private Vehicule vehicule;

    /** Detected from the file content (image/jpeg, image/png or image/webp), never taken from the client. */
    @Column(nullable = false, length = 20)
    private String contentType;

    /** Up to 5 MB, stored as MEDIUMBLOB in MySQL. */
    @Lob
    @Column(nullable = false, length = 5 * 1024 * 1024)
    private byte[] donnees;

    @Column(nullable = false)
    private LocalDateTime miseAJour;
}
