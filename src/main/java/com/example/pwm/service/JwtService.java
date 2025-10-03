package com.example.pwm.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class JwtService {
  private final byte[] secret;
  public JwtService(org.springframework.core.env.Environment env){
    String s = env.getProperty("jwt.secret");
    if(s == null || s.length() < 32) throw new IllegalStateException("JWT_SECRET fehlt");
    this.secret = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }
  public String create(Map<String,Object> claims){
    return Jwts.builder().claims(claims).signWith(Keys.hmacShaKeyFor(secret)).compact();
  }
  public Jws<Claims> verify(String token){ return Jwts.parserBuilder().setSigningKey(secret).build().parseClaimsJws(token); }
}
