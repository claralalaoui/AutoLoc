package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.web.dto.ContratDto;
import fr.ece.autoloc.web.dto.ReservationDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping
    public List<ReservationDto.Response> findAll() {
        return reservationService.findAll();
    }

    @GetMapping("/{id}")
    public ReservationDto.Response findById(@PathVariable("id") Long id) {
        return reservationService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationDto.Response create(@Valid @RequestBody ReservationDto.Request request) {
        return reservationService.create(request);
    }

    @PatchMapping("/{id}/statut")
    public ReservationDto.Response changerStatut(@PathVariable("id") Long id,
                                                 @Valid @RequestBody ReservationDto.StatutRequest request) {
        return reservationService.changerStatut(id, request.statut());
    }

    @PostMapping("/{id}/contrat")
    @ResponseStatus(HttpStatus.CREATED)
    public ContratDto.Response creerContrat(@PathVariable("id") Long id) {
        return reservationService.creerContrat(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        reservationService.delete(id);
    }
}
