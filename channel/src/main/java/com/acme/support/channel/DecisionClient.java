package com.acme.support.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.*;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Component
class DecisionClient {
 private static final long SESSION_WINDOW_SECS=43_200; // desk operators stay signed in across a full support shift
 private final RestClient client;private final byte[] secret;private final ObjectMapper mapper;
 DecisionClient(RestClient.Builder builder,ObjectMapper mapper,@Value("${app.decision-url}")String url,@Value("${app.token-secret}")String secret){this.client=builder.baseUrl(url).build();this.secret=secret.getBytes(StandardCharsets.UTF_8);this.mapper=mapper;}
 <T>T get(String user,String uri,Class<T> type){return client.get().uri(uri).header("Authorization","Bearer "+token(user)).retrieve().body(type);}
 <T>T get(String user,String uri,TypeReference<T> type){String json=client.get().uri(uri).header("Authorization","Bearer "+token(user)).retrieve().body(String.class);try{return mapper.readValue(json,type);}catch(Exception e){throw new IllegalStateException(e);}}
 void post(String user,String uri,Object body){client.post().uri(uri).header("Authorization","Bearer "+token(user)).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();}
 void put(String user,String uri,Object body){client.put().uri(uri).header("Authorization","Bearer "+token(user)).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();}
 void delete(String user,String uri){client.delete().uri(uri).header("Authorization","Bearer "+token(user)).retrieve().toBodilessEntity();}
 <T>T post(String user,String uri,Object body,Class<T> type){return client.post().uri(uri).header("Authorization","Bearer "+token(user)).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(type);}
 void postWithHeader(String user,String uri,String name,String value){client.post().uri(uri).header("Authorization","Bearer "+token(user)).header(name,value).retrieve().toBodilessEntity();}
 <T>T postWithHeader(String user,String uri,String name,String value,Class<T> type){return client.post().uri(uri).header("Authorization","Bearer "+token(user)).header(name,value).retrieve().body(type);}
 <T>T sendPart(String user,String uri,MultipartFile file,Class<T> type)throws Exception{var parts=new LinkedMultiValueMap<String,Object>();parts.add("file",new ByteArrayResource(file.getBytes()){@Override public String getFilename(){return file.getOriginalFilename();}});return client.post().uri(uri).header("Authorization","Bearer "+token(user)).contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().body(type);}
 byte[] bytes(String user,String uri){return client.get().uri(uri).header("Authorization","Bearer "+token(user)).retrieve().body(byte[].class);}
 void upload(String user,String uri,String category,MultipartFile file)throws Exception{var parts=new LinkedMultiValueMap<String,Object>();parts.add("category",category);parts.add("file",new ByteArrayResource(file.getBytes()){@Override public String getFilename(){return file.getOriginalFilename();}});client.post().uri(uri).header("Authorization","Bearer "+token(user)).contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().toBodilessEntity();}
 private String token(String username){String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(username.getBytes(StandardCharsets.UTF_8));String payload=encoded+"."+Instant.now().plusSeconds(SESSION_WINDOW_SECS).getEpochSecond();try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return payload+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
