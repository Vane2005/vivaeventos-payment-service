# vivaeventos-payment-service

Microservicio de pagos (US-08). Puerto **8084**, BD PostgreSQL **5435**.

## Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/v1/payments` | Inicia el pago de una orden (`{"orderId": "uuid"}`) |
| `GET` | `/api/v1/payments/{paymentId}` | Consulta un pago |
| `POST` | `/api/v1/payments/callback/wompi` | Webhook de Wompi |
| `POST` | `/api/v1/payments/callback/confirm?transactionId=` | Confirmación manual por ID de transacción |

## Contrato con order-service

- `GET /api/v1/orders/{orderId}` → `{ id, status, totalPrice, customerEmail, currency }`
  - `customerEmail` y `currency` son obligatorios para iniciar el pago (pasarela Wompi).
- `PATCH /api/v1/orders/{orderId}/confirm` → confirma el pago de la orden

Estados válidos para iniciar pago en payment-service: `PENDING` (respuesta de order-service).

## Mensajería RabbitMQ

| Routing key | Consumidor | Cuándo |
|-------------|------------|--------|
| `pago.aprobado` | ticket-service | Pago aprobado → generar boletas |
| `pago.fallido` | notification-service | Pago rechazado → notificar cliente |

Exchange: `vivaeventos.payments`

## Pasarela Wompi (sandbox real)

```properties
PAYMENT_GATEWAY_PROVIDER=wompi
WOMPI_PRIVATE_KEY=prv_test_...
WOMPI_PUBLIC_KEY=pub_test_...
WOMPI_CALLBACK_URL=http://localhost:8084/api/v1/payments/callback/wompi
```

Desarrollo local sin credenciales: `PAYMENT_GATEWAY_PROVIDER=sandbox` (por defecto).

## Levantar el servicio

Copia `.env.example` a `.env` en la raíz (contraseñas y secretos solo ahí; no van en `application.properties`).

```bash
cp .env.example .env
docker compose up --build
```

Para ejecutar sin Docker: `cp .env.example .env` y luego `./mvnw spring-boot:run` (Spring carga variables desde el entorno; en Windows puedes usar herramientas como `dotenv` o exportar las variables del `.env`).

Tests:

```bash
./mvnw test
```

## CI (GitHub Actions)

En cada push/PR a `main`, `develop` o `feature/**` se ejecuta:

- `./mvnw test` y empaquetado del JAR (Java 21)
- build de la imagen Docker (sin publicar a un registry)
