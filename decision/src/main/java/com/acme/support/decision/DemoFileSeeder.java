package com.acme.support.decision;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Component
class DemoFileSeeder implements CommandLineRunner {
 private final AttachmentRepository attachments;private final TicketRepository tickets;private final ActorRepository actors;private final Path root;
 DemoFileSeeder(AttachmentRepository attachments,TicketRepository tickets,ActorRepository actors,@Value("${app.file-root}")String root){this.attachments=attachments;this.tickets=tickets;this.actors=actors;this.root=Paths.get(root).toAbsolutePath().normalize();}
 @Override @Transactional public void run(String...args)throws Exception{Files.createDirectories(root);if(attachments.count()>0)return;for(long i=1;i<=25;i++){Ticket ticket=tickets.findById(i).orElseThrow();Actor actor=actors.findById(13+((i-1)%30)).orElseThrow();byte[] content=("Demonstration attachment for "+ticket.reference+". Contains mock support evidence only.\n").getBytes(StandardCharsets.UTF_8);String stored="seed-attachment-"+i+".txt";Path target=root.resolve(stored).normalize();if(!target.startsWith(root))throw new SecurityException("Seed path escaped file root");Files.write(target,content,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);Attachment a=new Attachment();a.ticket=ticket;a.uploader=actor;a.category=i%2==0?"SUPPORT_DOCUMENT":"CUSTOMER_EVIDENCE";a.originalName=i%2==0?"support-summary-"+i+".txt":"customer-evidence-"+i+".txt";a.storedName=stored;a.contentType="text/plain";a.sizeBytes=content.length;a.sha256=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));a.createdAt=Instant.now().minusSeconds(i*3600);attachments.save(a);}}
}
