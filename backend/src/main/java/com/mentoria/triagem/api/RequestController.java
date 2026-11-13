package com.mentoria.triagem.api;

import com.mentoria.triagem.conversations.ConversationService;
import com.mentoria.triagem.conversations.ConversationView;
import com.mentoria.triagem.requests.Category;
import com.mentoria.triagem.requests.RequestService;
import com.mentoria.triagem.requests.RequestStatus;
import com.mentoria.triagem.requests.RequestView;
import com.mentoria.triagem.requests.ReviewDecision;
import com.mentoria.triagem.requests.Urgency;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Rotas de solicitacao (Spec 001) e leitura da conversa vinculada (FR3-018). */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/requests")
public class RequestController {

  public record CreateRequestBody(@NotBlank String text) {}

  public record ReviewBody(
      long expectedVersion, ReviewDecision decision, Category category, Urgency urgency) {}

  public record ManualClassificationBody(
      long expectedVersion, Category category, Urgency urgency, String reason) {}

  public record TransitionBody(long expectedVersion, RequestStatus toStatus, String reason) {}

  public record ClarificationBody(long expectedVersion, @NotBlank String text) {}

  public record VersionOnlyBody(long expectedVersion) {}

  private final RequestService requests;
  private final ConversationService conversations;
  private final CurrentUserResolver currentUser;

  public RequestController(
      RequestService requests,
      ConversationService conversations,
      CurrentUserResolver currentUser) {
    this.requests = requests;
    this.conversations = conversations;
    this.currentUser = currentUser;
  }

  @PostMapping
  ResponseEntity<RequestView> create(
      @PathVariable String workspaceId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody CreateRequestBody body,
      HttpServletRequest http) {
    String subject = currentUser.requireSubject(http);
    // A analise inicial e pedida por evento (outbox -> RabbitMQ): a resposta sai sem esperar
    // a IA, e o estado muda para NEEDS_REVIEW ou NEEDS_CLARIFICATION quando o worker termina.
    RequestView created = requests.create(workspaceId, subject, body.text(), idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping
  List<RequestView> list(
      @PathVariable String workspaceId,
      @RequestParam(required = false) RequestStatus status,
      HttpServletRequest http) {
    return requests.list(workspaceId, currentUser.requireSubject(http), status);
  }

  @GetMapping("/{requestId}")
  RequestView detail(
      @PathVariable String workspaceId, @PathVariable String requestId, HttpServletRequest http) {
    return requests.get(workspaceId, currentUser.requireSubject(http), requestId);
  }

  @PostMapping("/{requestId}/reviews")
  RequestView review(
      @PathVariable String workspaceId,
      @PathVariable String requestId,
      @RequestBody ReviewBody body,
      HttpServletRequest http) {
    return requests.review(
        workspaceId,
        currentUser.requireSubject(http),
        requestId,
        body.expectedVersion(),
        body.decision(),
        body.category(),
        body.urgency());
  }

  @PostMapping("/{requestId}/manual-classification")
  RequestView classifyManually(
      @PathVariable String workspaceId,
      @PathVariable String requestId,
      @RequestBody ManualClassificationBody body,
      HttpServletRequest http) {
    return requests.classifyManually(
        workspaceId,
        currentUser.requireSubject(http),
        requestId,
        body.expectedVersion(),
        body.category(),
        body.urgency(),
        body.reason());
  }

  @PostMapping("/{requestId}/status-transitions")
  RequestView transition(
      @PathVariable String workspaceId,
      @PathVariable String requestId,
      @RequestBody TransitionBody body,
      HttpServletRequest http) {
    return requests.transition(
        workspaceId,
        currentUser.requireSubject(http),
        requestId,
        body.expectedVersion(),
        body.toStatus(),
        body.reason());
  }

  @PostMapping("/{requestId}/clarifications")
  RequestView clarify(
      @PathVariable String workspaceId,
      @PathVariable String requestId,
      @RequestBody ClarificationBody body,
      HttpServletRequest http) {
    String subject = currentUser.requireSubject(http);
    return requests.addClarification(
        workspaceId, subject, requestId, body.expectedVersion(), body.text());
  }

  @PostMapping("/{requestId}/human-review-requests")
  RequestView askHuman(
      @PathVariable String workspaceId,
      @PathVariable String requestId,
      @RequestBody VersionOnlyBody body,
      HttpServletRequest http) {
    return requests.requestHumanReview(
        workspaceId, currentUser.requireSubject(http), requestId, body.expectedVersion());
  }

  /** Transcricao da conversa que originou o chamado; somente agent/admin (FR3-018). */
  @GetMapping("/{requestId}/conversation")
  ConversationView conversation(
      @PathVariable String workspaceId, @PathVariable String requestId, HttpServletRequest http) {
    return conversations.readForRequest(workspaceId, currentUser.requireSubject(http), requestId);
  }
}
