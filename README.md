# PWM Backend (Spring Boot)

## Environment Variables (Render)
```
SPRING_PROFILES_ACTIVE=docker
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<db>?sslmode=require
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<pass>
JWT_SECRET=<mind. 32 Zeichen>
APP_ENCRYPTION_KEY=<32 Byte Base64, e.g. `openssl rand -base64 32`>
CORS_ALLOWED_ORIGINS=https://your-spa.onrender.com
```

## Endpoints
- `POST /api/auth/register` `{email,password}` → `{otpauthUrl, secretBase32}`
- `POST /api/auth/login` `{email,password}` → `{tmpToken}`
- `POST /api/auth/totp-verify` `{tmpToken, code}` → `{token}`
- `GET /api/vault` (Bearer) → items
- `POST /api/vault` (Bearer)
- `DELETE /api/vault/{id}` (Bearer)
- `GET /api/health`

## Docker
```
docker build -t pwm-backend .
docker run -p 8080:8080 --env-file .env pwm-backend
```
