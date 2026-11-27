package fr.ece.autoloc.service;

/** Uploaded file refused: empty, too large or not an accepted image format. */
public class FichierInvalideException extends RuntimeException {

    public FichierInvalideException(String message) {
        super(message);
    }
}
