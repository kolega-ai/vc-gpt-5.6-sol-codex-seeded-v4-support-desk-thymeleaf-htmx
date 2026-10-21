package com.acme.support.decision;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {
 @Mock TicketRepository tickets;@Mock MessageRepository messages;@Mock AttachmentRepository attachments;@Mock EscalationRepository escalations;@Mock CategoryRepository categories;@Mock SlaRepository slas;@Mock TeamRepository teams;@Mock ActorRepository actors;@Mock AuditService audit;
 @org.junit.jupiter.api.io.TempDir Path files;TicketService service;
 @BeforeEach void setUp()throws Exception{service=new TicketService(tickets,messages,attachments,escalations,categories,slas,teams,actors,audit,files.toString());}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void customerCannotReadAnotherOrganizationsTicket(){Actor customer=actor(Role.CUSTOMER_USER,1L,null);Ticket ticket=ticket(2L,1L);authenticate(customer);when(tickets.findById(9L)).thenReturn(Optional.of(ticket));assertThrows(AccessDeniedException.class,()->service.visibleTicket(9));}
 @Test void agentCannotReadAnotherTeamsTicket(){Actor agent=actor(Role.SUPPORT_AGENT,null,1L);Ticket ticket=ticket(1L,2L);authenticate(agent);when(tickets.findById(4L)).thenReturn(Optional.of(ticket));assertThrows(AccessDeniedException.class,()->service.visibleTicket(4));}
 @Test void customerCannotCreateInternalNote(){Actor customer=actor(Role.CUSTOMER_USER,1L,null);Ticket ticket=ticket(1L,1L);authenticate(customer);when(tickets.findById(3L)).thenReturn(Optional.of(ticket));assertThrows(AccessDeniedException.class,()->service.message(3,new AddMessage("private","INTERNAL_NOTE")));verifyNoInteractions(messages);}
 @Test void statusGraphMakesEveryDeclaredStatusReachable(){Set<String> seen=new HashSet<>(Set.of("new"));while(true){int before=seen.size();seen.stream().toList().forEach(s->seen.addAll(TicketService.ALLOWED_TRANSITIONS.getOrDefault(s,Set.of())));if(seen.size()==before)break;}assertEquals(Set.of("new","open","pending_customer","pending_internal","escalated","resolved","closed"),seen);}
 private static void authenticate(Actor actor){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));}
 private static Actor actor(Role role,Long orgId,Long teamId){Actor a=new Actor();a.id=1L;a.role=role;if(orgId!=null){a.organization=new Organization();a.organization.id=orgId;}if(teamId!=null){a.team=new SupportTeam();a.team.id=teamId;}return a;}
 private static Ticket ticket(Long orgId,Long teamId){Ticket t=new Ticket();t.organization=new Organization();t.organization.id=orgId;t.team=new SupportTeam();t.team.id=teamId;return t;}
}
