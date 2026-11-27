package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.service.AgenceService;
import fr.ece.autoloc.web.dto.AgenceDto;
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
@RequestMapping("/api/agences")
@RequiredArgsConstructor
public class AgenceController {

    private final AgenceService agenceService;

    @GetMapping
    public List<AgenceDto.Response> findAll() {
        return agenceService.findAll();
    }

    @GetMapping("/{id}")
    public AgenceDto.Response findById(@PathVariable("id") Long id) {
        return agenceService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgenceDto.Response create(@Valid @RequestBody AgenceDto.Request request) {
        return agenceService.create(request);
    }

    @PutMapping("/{id}")
    public AgenceDto.Response update(@PathVariable("id") Long id, @Valid @RequestBody AgenceDto.Request request) {
        return agenceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        agenceService.delete(id);
    }
}
