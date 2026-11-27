package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.service.ClientService;
import fr.ece.autoloc.web.dto.ClientDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public List<ClientDto.Response> findAll() {
        return clientService.findAll();
    }

    @GetMapping("/{id}")
    public ClientDto.Response findById(@PathVariable("id") Long id) {
        return clientService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientDto.Response create(@Valid @RequestBody ClientDto.Request request) {
        return clientService.create(request);
    }

    @PutMapping("/{id}")
    public ClientDto.Response update(@PathVariable("id") Long id, @Valid @RequestBody ClientDto.Request request) {
        return clientService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        clientService.delete(id);
    }
}
