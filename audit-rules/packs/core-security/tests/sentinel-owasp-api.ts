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
