package com.mentoria.triagem.support;

import com.mentoria.triagem.conversations.ChatProvider;
import com.mentoria.triagem.conversations.ConversationJudge;
import com.mentoria.triagem.conversations.ConversationLimits;
import com.mentoria.triagem.conversations.ConversationService;
import com.mentoria.triagem.conversations.internal.ConversationTransactions;
import com.mentoria.triagem.conversations.internal.FakeChatProvider;
import com.mentoria.triagem.conversations.internal.FakeConversationJudge;
import com.mentoria.triagem.identity.Membership;
import com.mentoria.triagem.identity.MembershipService;
import com.mentoria.triagem.identity.Role;
import com.mentoria.triagem.requests.RequestService;
import com.mentoria.triagem.triage.TriageProvider;
import com.mentoria.triagem.triage.TriageService;
import com.mentoria.triagem.triage.internal.FakeTriageProvider;

/**
 * Monta o dominio sem Spring e sem infraestrutura, com relogio controlado e dados sinteticos.
 * Workspaces "ws-a" e "ws-b" existem para exercitar isolamento.
 */
public class DomainFixture {

  public static final String WS_A = "ws-a";
  public static final String WS_B = "ws-b";
  public static final String ANA = "requester-ana";
  public static final String BRUNO = "agent-bruno";
  public static final String CARLA = "admin-carla";
  public static final String DANI = "requester-dani";

  public final MutableClock clock = new MutableClock();
  public final InMemoryStores.Memberships membershipRepository = new InMemoryStores.Memberships();
  public final MembershipService memberships = new MembershipService(membershipRepository);
  public final InMemoryStores.Requests requestStore = new InMemoryStores.Requests();
  public final InMemoryStores.Conversations conversationStore = new InMemoryStores.Conversations();
  public final InlineEventPublisher events = new InlineEventPublisher();
  public final RequestService requests;
  public final TriageService triage;
  public final ConversationService conversations;
  public final CountingJudge judge;
  public final ChatProvider chatProvider;

  public DomainFixture() {
    this(ConversationLimits.defaults(), new FakeChatProvider(), new FakeConversationJudge());
  }

  public DomainFixture(ConversationLimits limits, ChatProvider chat, ConversationJudge judge) {
    this.requests = new RequestService(requestStore, memberships, events, clock);
    TriageProvider triageProvider = new FakeTriageProvider();
    this.triage = new TriageService(triageProvider, requests, clock);
    this.judge = new CountingJudge(judge);
    this.chatProvider = chat;

    ConversationTransactions transactions =
        new ConversationTransactions(conversationStore, requests, events, clock);
    this.conversations =
        new ConversationService(
            conversationStore, memberships, requests, chat, this.judge, limits, transactions, clock);

    // A avaliacao do JEV chega por evento. No teste de dominio ela e entregue na hora, mas
    // continua passando por applyJudgeVerdict, que e o metodo que o consumidor real chama.
    events.onEvaluationRequested(
        event ->
            conversations.applyJudgeVerdict(
                event.workspaceId(),
                event.payload().get("conversationId"),
                Integer.parseInt(event.payload().get("turnSequence"))));

    memberships.upsert(new Membership(WS_A, CARLA, Role.ADMIN, true));
    memberships.upsert(new Membership(WS_A, BRUNO, Role.AGENT, true));
    memberships.upsert(new Membership(WS_A, ANA, Role.REQUESTER, true));
    memberships.upsert(new Membership(WS_B, DANI, Role.REQUESTER, true));
    memberships.upsert(new Membership(WS_B, "admin-eva", Role.ADMIN, true));
  }
}
