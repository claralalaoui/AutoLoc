package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.service.ClientService;
import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.web.dto.ClientDto;
import fr.ece.autoloc.web.dto.ReservationDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer space: everything here works on the logged-in user only,
 * the username always comes from the authentication, never from the request.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MonCompteController {

    private final ClientService clientService;
    private final ReservationService reservationService;

    /** Used by the web interface to check the credentials and to know the user's roles. */
    @GetMapping
    public CurrentUser me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return new CurrentUser(authentication.getName(), roles);
    }

    @GetMapping("/profil")
    public ClientDto.Response profil(Authentication authentication) {
        return clientService.findProfil(authentication.getName());
    }

    @PutMapping("/profil")
    public ClientDto.Response enregistrerProfil(Authentication authentication,
                                                @Valid @RequestBody ClientDto.Request request) {
        return clientService.enregistrerProfil(authentication.getName(), request);
    }

    @GetMapping("/reservations")
    public List<ReservationDto.Response> mesReservations(Authentication authentication) {
        return reservationService.findMine(authentication.getName());
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationDto.Response reserver(Authentication authentication,
                                           @Valid @RequestBody ReservationDto.ClientRequest request) {
        return reservationService.createForClient(authentication.getName(), request);
    }

    @PostMapping("/reservations/{id}/annulation")
    public ReservationDto.Response annuler(Authentication authentication, @PathVariable("id") Long id) {
        return reservationService.annulerMienne(authentication.getName(), id);
    }

    public record CurrentUser(String username, List<String> roles) {
    }
}
