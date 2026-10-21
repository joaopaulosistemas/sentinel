// ruleid: sentinel-camadas-componente-injeta-httpclient
@Component({ selector: 'a', template: '' })
class AComp {
  constructor(private http: HttpClient) {}
}

// ok: sentinel-camadas-componente-injeta-httpclient
@Component({ selector: 'b', template: '' })
class BComp {
  constructor(private svc: ContaService) {}
}
