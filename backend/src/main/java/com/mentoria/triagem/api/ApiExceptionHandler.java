package com.mentoria.triagem.api;

import com.mentoria.triagem.conversations.ConversationNotFoundException;
import com.mentoria.triagem.conversations.ConversationStateException;
import com.mentoria.triagem.identity.LastAdminException;
import com.mentoria.triagem.identity.NotAuthorizedException;
import com.mentoria.triagem.requests.IdempotencyConflictException;
import com.mentoria.triagem.requests.InvalidTransitionException;
import com.mentoria.triagem.requests.RequestNotFoundException;
import com.mentoria.triagem.requests.VersionConflictException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Envelope unico de erro do contrato: {@code {error:{code,message,correlationId,details}}}.
 * Nenhuma mensagem revela conteudo de outro workspace ou segredo.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  public record ErrorBody(String code, String message, String correlationId, Map<String, Object> details) {}

  public record ErrorEnvelope(ErrorBody error) {}

  private static ResponseEntity<ErrorEnvelope> body(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status)
        .body(new ErrorEnvelope(new ErrorBody(code, message, UUID.randomUUID().toString(), Map.of())));
  }

  @ExceptionHandler(CurrentUserResolver.UnauthenticatedException.class)
  ResponseEntity<ErrorEnvelope> unauthenticated(CurrentUserResolver.UnauthenticatedException ex) {
    return body(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", ex.getMessage());
  }

  @ExceptionHandler(NotAuthorizedException.class)
  ResponseEntity<ErrorEnvelope> notAuthorized(NotAuthorizedException ex) {
    return body(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  @ExceptionHandler({RequestNotFoundException.class, ConversationNotFoundException.class})
  ResponseEntity<ErrorEnvelope> notFound(RuntimeException ex) {
    return body(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(VersionConflictException.class)
  ResponseEntity<ErrorEnvelope> versionConflict(VersionConflictException ex) {
    return body(HttpStatus.CONFLICT, "VERSION_CONFLICT", ex.getMessage());
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  ResponseEntity<ErrorEnvelope> idempotencyConflict(IdempotencyConflictException ex) {
    return body(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", ex.getMessage());
  }

  @ExceptionHandler(InvalidTransitionException.class)
  ResponseEntity<ErrorEnvelope> invalidTransition(InvalidTransitionException ex) {
    return body(HttpStatus.CONFLICT, "INVALID_TRANSITION", ex.getMessage());
  }

  @ExceptionHandler(LastAdminException.class)
  ResponseEntity<ErrorEnvelope> lastAdmin(LastAdminException ex) {
    return body(HttpStatus.CONFLICT, "LAST_ADMIN", ex.getMessage());
  }

  @ExceptionHandler(ConversationStateException.class)
  ResponseEntity<ErrorEnvelope> conversationState(ConversationStateException ex) {
    HttpStatus status =
        ex.code().equals("VERSION_CONFLICT")
                || ex.code().equals("ALREADY_ESCALATED")
                || ex.code().equals("CONVERSATION_NOT_ACTIVE")
                || ex.code().equals("CONFIRMATION_NOT_PENDING")
                || ex.code().equals("REOPEN_WINDOW_EXPIRED")
                || ex.code().equals("ACTIVE_CONVERSATION_LIMIT")
            ? HttpStatus.CONFLICT
            : HttpStatus.UNPROCESSABLE_ENTITY;
    return body(status, ex.code(), ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ErrorEnvelope> validation(IllegalArgumentException ex) {
    return body(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", ex.getMessage());
  }
}
