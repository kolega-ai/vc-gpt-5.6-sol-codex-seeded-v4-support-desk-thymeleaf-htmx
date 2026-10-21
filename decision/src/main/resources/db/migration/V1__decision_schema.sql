CREATE TABLE organization (
 id BIGSERIAL PRIMARY KEY, name VARCHAR(160) NOT NULL, domain VARCHAR(160) NOT NULL UNIQUE,
 tier VARCHAR(30) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE support_team (
 id BIGSERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE, queue_key VARCHAR(60) NOT NULL UNIQUE,
 description VARCHAR(300) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE actor (
 id BIGSERIAL PRIMARY KEY, username VARCHAR(120) NOT NULL UNIQUE, display_name VARCHAR(120) NOT NULL,
 email VARCHAR(200) NOT NULL, role VARCHAR(30) NOT NULL, organization_id BIGINT REFERENCES organization(id),
 team_id BIGINT REFERENCES support_team(id), enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE category (
 id BIGSERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE, description VARCHAR(300) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE sla_policy (
 id BIGSERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE, priority VARCHAR(20) NOT NULL,
 first_response_minutes INTEGER NOT NULL CHECK(first_response_minutes > 0),
 resolution_minutes INTEGER NOT NULL CHECK(resolution_minutes > 0), active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE ticket (
 id BIGSERIAL PRIMARY KEY, reference VARCHAR(24) NOT NULL UNIQUE, organization_id BIGINT NOT NULL REFERENCES organization(id),
 requester_id BIGINT NOT NULL REFERENCES actor(id), assigned_agent_id BIGINT REFERENCES actor(id), team_id BIGINT NOT NULL REFERENCES support_team(id),
 category_id BIGINT NOT NULL REFERENCES category(id), sla_policy_id BIGINT NOT NULL REFERENCES sla_policy(id),
 subject VARCHAR(180) NOT NULL, description TEXT NOT NULL, priority VARCHAR(20) NOT NULL, status VARCHAR(30) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 first_response_due_at TIMESTAMPTZ NOT NULL, resolution_due_at TIMESTAMPTZ NOT NULL,
 first_responded_at TIMESTAMPTZ, resolved_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ticket_scope_idx ON ticket(organization_id, team_id, status);
CREATE TABLE message (
 id BIGSERIAL PRIMARY KEY, ticket_id BIGINT NOT NULL REFERENCES ticket(id) ON DELETE CASCADE,
 author_id BIGINT NOT NULL REFERENCES actor(id), kind VARCHAR(20) NOT NULL, body VARCHAR(5000) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE attachment (
 id BIGSERIAL PRIMARY KEY, ticket_id BIGINT NOT NULL REFERENCES ticket(id) ON DELETE CASCADE,
 uploader_id BIGINT NOT NULL REFERENCES actor(id), category VARCHAR(30) NOT NULL, original_name VARCHAR(255) NOT NULL,
 stored_name VARCHAR(100) NOT NULL UNIQUE, content_type VARCHAR(100) NOT NULL, size_bytes BIGINT NOT NULL,
 sha256 VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE escalation (
 id BIGSERIAL PRIMARY KEY, ticket_id BIGINT NOT NULL REFERENCES ticket(id), owner_id BIGINT REFERENCES actor(id),
 reason VARCHAR(1000) NOT NULL, status VARCHAR(30) NOT NULL, resolution VARCHAR(2000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE knowledge_article (
 id BIGSERIAL PRIMARY KEY, title VARCHAR(180) NOT NULL, slug VARCHAR(200) NOT NULL UNIQUE,
 summary VARCHAR(400) NOT NULL, body TEXT NOT NULL, status VARCHAR(30) NOT NULL, visibility VARCHAR(30) NOT NULL,
 author_id BIGINT NOT NULL REFERENCES actor(id), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), published_at TIMESTAMPTZ
);
CREATE TABLE audit_event (
 id BIGSERIAL PRIMARY KEY, actor_id BIGINT REFERENCES actor(id), actor_name VARCHAR(120) NOT NULL,
 action VARCHAR(100) NOT NULL, resource_type VARCHAR(60) NOT NULL, resource_id VARCHAR(80),
 before_value TEXT, after_value TEXT, authorization_basis VARCHAR(300) NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE notification (
 id BIGSERIAL PRIMARY KEY, actor_id BIGINT NOT NULL REFERENCES actor(id), title VARCHAR(180) NOT NULL,
 body VARCHAR(500) NOT NULL, target_path VARCHAR(300) NOT NULL, read_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE api_key (
 id BIGSERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL, key_prefix VARCHAR(16) NOT NULL UNIQUE,
 key_hash VARCHAR(64) NOT NULL UNIQUE, active BOOLEAN NOT NULL DEFAULT TRUE, created_by BIGINT NOT NULL REFERENCES actor(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_used_at TIMESTAMPTZ
);
CREATE TABLE webhook_subscription (
 id BIGSERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL, target_url VARCHAR(500) NOT NULL,
 event_type VARCHAR(60) NOT NULL, signing_secret_hash VARCHAR(64) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE outbox (
 id BIGSERIAL PRIMARY KEY, channel VARCHAR(20) NOT NULL, recipient VARCHAR(200) NOT NULL,
 subject VARCHAR(200) NOT NULL, body TEXT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), sent_at TIMESTAMPTZ
);
INSERT INTO webhook_subscription(name,target_url,event_type,signing_secret_hash,active) VALUES
 ('Local ticket events','http://core-mock:8082/mock/webhooks','ticket.changed',repeat('0',64),TRUE);

INSERT INTO organization(name,domain,tier) SELECT 'Customer '||g, 'customer'||g||'.example.test', CASE WHEN g<=2 THEN 'enterprise' WHEN g<=6 THEN 'growth' ELSE 'standard' END FROM generate_series(1,10) g;
INSERT INTO support_team(name,queue_key,description) VALUES
 ('Platform Support','platform','Product and availability issues'),('Billing Care','billing','Invoices and subscription support'),('Customer Success','success','Onboarding and account guidance');
INSERT INTO category(name,description) VALUES ('Account access','Sign-in and account recovery'),('Billing','Invoices and subscriptions'),('Product issue','Unexpected product behaviour'),('How-to','Product guidance'),('Security','Security and privacy concerns');
INSERT INTO sla_policy(name,priority,first_response_minutes,resolution_minutes) VALUES
 ('Urgent response','urgent',15,240),('High response','high',60,480),('Normal response','normal',240,1440),('Low response','low',480,2880);
INSERT INTO actor(username,display_name,email,role,organization_id,team_id)
VALUES ('admin@desk.test','Morgan Admin','admin@desk.test','SUPPORT_ADMIN',NULL,1),
 ('lead@desk.test','Riley Lead','lead@desk.test','TEAM_LEAD',NULL,1),
 ('agent@desk.test','Avery Agent','agent@desk.test','SUPPORT_AGENT',NULL,1);
INSERT INTO actor(username,display_name,email,role,organization_id,team_id)
SELECT 'agent'||g||'@desk.test','Support Agent '||g,'agent'||g||'@desk.test',CASE WHEN g%4=0 THEN 'TEAM_LEAD' ELSE 'SUPPORT_AGENT' END,NULL,((g-1)%3)+1 FROM generate_series(4,12) g;
INSERT INTO actor(username,display_name,email,role,organization_id,team_id)
SELECT CASE WHEN g=1 THEN 'customer@desk.test' ELSE 'customer'||g||'@desk.test' END,'Customer User '||g,CASE WHEN g=1 THEN 'customer@desk.test' ELSE 'customer'||g||'@desk.test' END,'CUSTOMER_USER',((g-1)%10)+1,NULL FROM generate_series(1,30) g;
INSERT INTO ticket(reference,organization_id,requester_id,assigned_agent_id,team_id,category_id,sla_policy_id,subject,description,priority,status,created_at,updated_at,first_response_due_at,resolution_due_at,first_responded_at,resolved_at)
SELECT 'SUP-'||to_char(g,'FM00000'),((g-1)%10)+1,13+((g-1)%30),1+((g-1)%12),((g-1)%3)+1,((g-1)%5)+1,
 CASE WHEN g%9=0 THEN 1 WHEN g%4=0 THEN 2 ELSE 3 END,
 CASE (g%8) WHEN 0 THEN 'Cannot sign in after password change' WHEN 1 THEN 'Invoice total needs clarification' WHEN 2 THEN 'Export takes too long' WHEN 3 THEN 'Team member invitation failed' WHEN 4 THEN 'Dashboard data is delayed' WHEN 5 THEN 'Need help configuring notifications' WHEN 6 THEN 'Unexpected permission warning' ELSE 'Subscription renewal question' END,
 'The customer supplied reproducible details and requested a clear next step. This is seeded demonstration data only.',
 CASE WHEN g%9=0 THEN 'urgent' WHEN g%4=0 THEN 'high' ELSE 'normal' END,
 (ARRAY['new','open','pending_customer','pending_internal','escalated','resolved','closed'])[1+(g%7)],
 now()-(g||' hours')::interval,now()-(g||' minutes')::interval,
 now()-((g%8-2)||' hours')::interval,now()+((g%12-4)||' hours')::interval,
 CASE WHEN g%5<>0 THEN now()-((g%10)||' hours')::interval END,
 CASE WHEN g%7 IN (5,6) THEN now()-((g%6)||' hours')::interval END
FROM generate_series(1,70) g;
INSERT INTO message(ticket_id,author_id,kind,body,created_at)
SELECT ((g-1)%70)+1,CASE WHEN g%3=0 THEN 3 ELSE 13+((g-1)%30) END,CASE WHEN g%4=0 THEN 'INTERNAL_NOTE' ELSE 'PUBLIC_REPLY' END,
 CASE WHEN g%4=0 THEN 'Internal triage note: validate the account state before replying.' ELSE 'Thanks for the update. We are reviewing the details and will keep you informed.' END,now()-(g||' minutes')::interval FROM generate_series(1,90) g;
INSERT INTO escalation(ticket_id,owner_id,reason,status,resolution,created_at,updated_at)
SELECT g,2,'Customer impact requires lead review.',(ARRAY['requested','accepted','in_review','resolved','rejected'])[1+(g%5)],CASE WHEN g%5=3 THEN 'Reviewed and corrective action agreed.' END,now()-(g||' hours')::interval,now() FROM generate_series(1,20) g;
INSERT INTO knowledge_article(title,slug,summary,body,status,visibility,author_id,published_at)
SELECT 'Support guide '||g,'support-guide-'||g,'Practical steps for a common support question.','Follow these verified steps. If the issue continues, open a ticket and include the time and error shown.',
(ARRAY['draft','in_review','published','archived'])[1+(g%4)],CASE WHEN g%3=0 THEN 'STAFF' ELSE 'PUBLIC' END,3,CASE WHEN g%4=2 THEN now()-(g||' days')::interval END FROM generate_series(1,20) g;
INSERT INTO audit_event(actor_id,actor_name,action,resource_type,resource_id,before_value,after_value,authorization_basis,occurred_at)
SELECT 1,'Morgan Admin','TICKET_STATUS_CHANGED','Ticket',g::text,'open','pending_customer','role=SUPPORT_ADMIN',now()-(g||' hours')::interval FROM generate_series(1,30) g;
INSERT INTO notification(actor_id,title,body,target_path) SELECT g,'Queue update','A ticket in your queue needs attention.','/tickets' FROM generate_series(1,12) g;
