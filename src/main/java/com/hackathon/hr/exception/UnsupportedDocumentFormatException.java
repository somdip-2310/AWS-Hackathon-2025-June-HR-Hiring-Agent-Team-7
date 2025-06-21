package com.hackathon.hr.exception;

public class UnsupportedDocumentFormatException extends RuntimeException {
    public UnsupportedDocumentFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}