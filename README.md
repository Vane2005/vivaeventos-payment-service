# vivaeventos-payment-service

Microservicio de pagos (US-08). Puerto **8084**, BD PostgreSQL **5435**.

Integración con **Stripe Checkout** (sandbox/test). El email del cliente se obtiene de **order-service** (registro del usuario en el flujo de compra).

## Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/v1/payments` | Inicia el pago de una orden (`{"orderId": "uuid"}`) |
| `GET` | `/api/v1/payments/{paymentId}` | Consulta un pago |
| `GET` | `/api/v1/payments/callback/success?session_id=` | Retorno de Stripe tras pago exitoso (confirma y redirige al frontend) |
| `GET` | `/api/v1/payments/callback/cancel` | Retorno de Stripe si el usuario cancela |
| `GET` | `/api/v1/payments/stripe/config` | Clave publicable de Stripe para el frontend |
| `POST` | `/api/v1/payments/callback/stripe` | Webhook de Stripe (producción / Stripe CLI) |
| `POST` | `/api/v1/payments/callback/confirm?transactionId=` | Confirmación manual por ID de sesión |

## Contrato con order-service

- `GET /api/v1/orders/{orderId}` → `{ id, status, totalPrice, customerEmail, currency }`
  - `customerEmail` y `currency` son obligatorios (el email viene del usuario al crear la orden).
- `PATCH /api/v1/orders/{orderId}/confirm` → confirma el pago de la orden

Estados válidos para iniciar pago: `PENDING`.

## Mensajería RabbitMQ

| Routing key | Consumidor | Cuándo |
|-------------|------------|--------|
| `pago.aprobado` | ticket-service | Pago aprobado → generar boletas |
| `pago.fallido` | notification-service | Pago rechazado → notificar cliente |

Exchange: `vivaeventos.events`

## Pasarela Stripe (modo prueba)

1. Crea cuenta gratuita en [Stripe](https://dashboard.stripe.com/register).
2. En [API keys (test)](https://dashboard.stripe.com/test/apikeys) copia la **Secret key** (`sk_test_...`).
3. Para webhooks locales, instala [Stripe CLI](https://stripe.com/docs/stripe-cli) y ejecuta:

```bash
stripe listen --forward-to localhost:8084/api/v1/payments/callback/stripe
```

Copia el `whsec_...` que muestra el CLI a tu `.env`.

Variables en `.env`:

```env
PAYMENT_GATEWAY_PROVIDER=stripe
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...          # opcional en local (ver abajo)
STRIPE_SUCCESS_URL=http://localhost:8084/api/v1/payments/callback/success?session_id={CHECKOUT_SESSION_ID}
STRIPE_CANCEL_URL=http://localhost:8084/api/v1/payments/callback/cancel
STRIPE_RETURN_URL=http://localhost:3000/payment/success?session_id={CHECKOUT_SESSION_ID}
STRIPE_CANCEL_RETURN_URL=http://localhost:3000/payment/cancel
```

**Flujo sin Stripe CLI:** Stripe redirige al backend (`/callback/success`), el servicio confirma el pago consultando la sesión y redirige al frontend con `session_id`.

**Con webhook (recomendado en producción):** `stripe listen --forward-to localhost:8084/api/v1/payments/callback/stripe`

Tarjeta de prueba: `4242 4242 4242 4242` — [más opciones](https://docs.stripe.com/testing#cards).

Desarrollo sin credenciales: `PAYMENT_GATEWAY_PROVIDER=sandbox` (checkout simulado; confirmar con `/callback/confirm`).

## Levantar el servicio

```bash
cp .env.example .env
docker compose up --build
```

Sin Docker: `./mvnw spring-boot:run` (con variables del `.env` exportadas).

Tests:

```bash
./mvnw test
```

## CI (GitHub Actions)

En cada push/PR a `main`, `develop` o `feature/**` se ejecuta `./mvnw test` y build Docker (Java 21).
