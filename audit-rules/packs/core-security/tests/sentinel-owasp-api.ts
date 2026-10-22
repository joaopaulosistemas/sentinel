import * as jwt from 'jsonwebtoken';

function assinar(payload: object) {
  // ruleid: sentinel-api-jwt-chave-fixa-js
  return jwt.sign(payload, 'minha-chave');
}

function assinarSeguro(payload: object) {
  // ok: sentinel-api-jwt-chave-fixa-js
  return jwt.sign(payload, process.env.JWT_KEY as string);
}

function verificar(token: string) {
  // ruleid: sentinel-api-jwt-chave-fixa-js
  return jwt.verify(token, 'minha-chave', { algorithms: ['HS256'] });
}

const modulo = {
  // ruleid: sentinel-api-jwt-chave-fixa-js
  imports: [JwtModule.register({ secret: 'abc123', signOptions: { expiresIn: '1h' } })],
};

const modulo2 = {
  // ok: sentinel-api-jwt-chave-fixa-js
  imports: [JwtModule.register({ secret: process.env.JWT_KEY })],
};

// ruleid: sentinel-api-jwt-segredo-padrao-js
const segredo = process.env.JWT_SECRET || 'padrao';
// ruleid: sentinel-api-jwt-segredo-padrao-js
const outro = process.env.APP_SECRET ?? 'padrao';
// ok: sentinel-api-jwt-segredo-padrao-js
const porta = process.env.PORT || '3000';

async function iniciar(app: any, origens: string[]) {
  // ruleid: sentinel-api-cors-aberto-js
  app.enableCors({ origin: '*' });
  // ruleid: sentinel-api-cors-aberto-js
  app.enableCors({ origin: true, credentials: true });
  // ruleid: sentinel-api-cors-aberto-js
  app.enableCors();
  // ok: sentinel-api-cors-aberto-js
  app.enableCors({ origin: origens });
  // ok: sentinel-api-cors-aberto-js
  app.enableCors({ origin: ['https://portal.exemplo.com.br'] });
}

const servidor = express();
// ruleid: sentinel-api-cors-aberto-js
servidor.use(cors());
// ruleid: sentinel-api-cors-aberto-js
servidor.use(cors({ origin: '*' }));
// ok: sentinel-api-cors-aberto-js
servidor.use(cors({ origin: 'https://portal.exemplo.com.br' }));

// ruleid: sentinel-api-cors-aberto-js
const app1 = await NestFactory.create(AppModule, { cors: true });
// ok: sentinel-api-cors-aberto-js
const app2 = await NestFactory.create(AppModule, { logger: false });

function responder(res: any) {
  // ruleid: sentinel-api-cors-aberto-js
  res.setHeader('Access-Control-Allow-Origin', '*');
  // ok: sentinel-api-cors-aberto-js
  res.setHeader('Access-Control-Allow-Origin', 'https://portal.exemplo.com.br');
}
