package com.acme.support.decision;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
class WorkflowGraphTest {
 @Test void escalationStatusesAreReachable(){assertReachable("requested",WorkflowService.ESCALATION_EDGES,Set.of("requested","accepted","in_review","resolved","rejected"));}
 @Test void knowledgeStatusesAreReachable(){assertReachable("draft",WorkflowService.KB_EDGES,Set.of("draft","in_review","published","archived"));}
 private static void assertReachable(String start,Map<String,Set<String>> edges,Set<String> expected){Set<String> seen=new HashSet<>(Set.of(start));while(true){int n=seen.size();seen.stream().toList().forEach(s->seen.addAll(edges.getOrDefault(s,Set.of())));if(n==seen.size())break;}assertEquals(expected,seen);}
}
