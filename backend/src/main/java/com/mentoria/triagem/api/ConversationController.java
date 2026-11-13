package com.mentoria.triagem.api;

import com.mentoria.triagem.conversations.ConversationService;
import com.mentoria.triagem.conversations.ConversationView;
import com.mentoria.triagem.conversations.TranscriptAccess;
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
import org.springframework.web.bind.annotation.RestController;

/** Rotas da conversa assistida (Spec 003). */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/conversations")
public class ConversationController {

  public record OpenConversationRequest(@NotBlank String text) {}

  public record MessageRequest(long expectedVersion, @NotBlank String text) {}

  public record HandoffRequest(long expectedVersion, String reason) {}

  public record ConfirmationRequest(long expectedVersion, boolean resolved) {}

  private final ConversationService conversations;
  private final CurrentUserResolver currentUser;

  public ConversationController(ConversationService conversations, CurrentUserResolver currentUser) {
    this.conversations = conversations;
    this.currentUser = currentUser;
  }

  @PostMapping
  ResponseEntity<ConversationView> open(
      @PathVariable String workspaceId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody OpenConversationRequest body,
      HttpServletRequest http) {
    String subject = currentUser.requireSubject(http);
    ConversationView view = conversations.open(workspaceId, subject, body.text(), idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @GetMapping
  List<ConversationView> list(@PathVariable String workspaceId, HttpServletRequest http) {
    return conversations.listForAuthor(workspaceId, currentUser.requireSubject(http));
  }

  @GetMapping("/{conversationId}")
  ConversationView detail(
      @PathVariable String workspaceId,
      @PathVariable String conversationId,
      HttpServletRequest http) {
    return conversations.get(workspaceId, currentUser.requireSubject(http), conversationId);
  }

  @PostMapping("/{conversationId}/messages")
  ConversationView sendMessage(
      @PathVariable String workspaceId,
      @PathVariable String conversationId,
      @RequestBody MessageRequest body,
      HttpServletRequest http) {
    return conversations.sendMessage(
        workspaceId,
        currentUser.requireSubject(http),
        conversationId,
        body.expectedVersion(),
        body.text());
  }

  @PostMapping("/{conversationId}/human-handoffs")
  ResponseEntity<ConversationView> requestHuman(
      @PathVariable String workspaceId,
      @PathVariable String conversationId,
      @RequestBody HandoffRequest body,
      HttpServletRequest http) {
    ConversationView view =
        conversations.requestHuman(
            workspaceId,
            currentUser.requireSubject(http),
            conversationId,
            body.expectedVersion(),
            body.reason());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @PostMapping("/{conversationId}/resolution-confirmations")
  ConversationView confirm(
      @PathVariable String workspaceId,
      @PathVariable String conversationId,
      @RequestBody ConfirmationRequest body,
      HttpServletRequest http) {
    return conversations.confirmResolution(
        workspaceId,
        currentUser.requireSubject(http),
        conversationId,
        body.expectedVersion(),
        body.resolved());
  }

  @GetMapping("/{conversationId}/access-log")
  List<TranscriptAccess> accessLog(
      @PathVariable String workspaceId,
      @PathVariable String conversationId,
      HttpServletRequest http) {
    return conversations.accessLog(workspaceId, currentUser.requireSubject(http), conversationId);
  }
}
