// Casos de teste do pacote ts-clean-code. Cada "ruleid:/ok:" vale para a linha seguinte.
export class ContaComponent {
  // ruleid: sentinel-tsp-privado-sem-underscore
  private idInstituicao: number;
  // ok: sentinel-tsp-privado-sem-underscore
  private _idContaCorrente: number;
  // ruleid: sentinel-tsp-privado-sem-underscore
  private readonly contaService: ContaService;
  // ok: sentinel-tsp-privado-sem-underscore
  private readonly _dialog: MatDialog;

  // ruleid: sentinel-tsp-publico-com-underscore
  public _contaForm: FormControl;
  // ok: sentinel-tsp-publico-com-underscore
  public contaForm: FormControl;

  // ruleid: sentinel-tsp-metodo-sem-modificador
  getContas(): void {
    this._buscar();
  }

  // ruleid: sentinel-tsp-metodo-sem-modificador
  async carregar(filter: FilterModel): Promise<void> {
    this._buscar();
  }

  // ok: sentinel-tsp-metodo-sem-modificador
  public getMensagemErro(): string {
    return '';
  }

  // ok: sentinel-tsp-metodo-sem-modificador
  private _buscar(): void {
    if (this.contaForm.invalid) {
      this.contaForm.markAllAsTouched();
    }
  }

  // ok: sentinel-tsp-metodo-sem-modificador
  public async carregarTudo(): Promise<void> {
    for (const item of this._itens) {
      this._processar(item);
    }
  }
}

// ruleid: sentinel-tsp-enum-sem-sufixo
export enum TipoPessoa {
  PESSOA_FISICA = 0,
}

// ok: sentinel-tsp-enum-sem-sufixo
export enum TipoPessoaEnum {
  PESSOA_FISICA = 0,
}

// ruleid: sentinel-tsp-interface-sem-padrao
export interface Status {
  idGrupo: number;
}

// ok: sentinel-tsp-interface-sem-padrao
export interface StatusInterface {
  idGrupo: number;
}

// ok: sentinel-tsp-interface-sem-padrao
export interface IContaTarifaNaoPaga {
  conta: number;
}

export class CicloComponent {
  // ruleid: sentinel-tsp-ciclo-de-vida-com-logica
  public ngOnInit(): void {
    this.contaForm = new FormControl(null);
    this._getUsuarioLogado();
  }

  // ok: sentinel-tsp-ciclo-de-vida-com-logica
  public ngAfterViewInit(): void {
    this._initForm();
    this._getUsuarioLogado();
  }

  // ok: sentinel-tsp-ciclo-de-vida-com-logica
  public ngOnDestroy(): void {
  }

  // espaços não separáveis (U+00A0, comuns ao copiar de documentos) no recuo
  // ok: sentinel-tsp-ciclo-de-vida-com-logica
  public ngOnChanges(): void {
    this._initForm();
  }
}

// ruleid: sentinel-tsp-abreviacao
const numContaCorrente = 10;
// ruleid: sentinel-tsp-abreviacao
const filtro = { codTarifaLegado: 1 };
// ok: sentinel-tsp-abreviacao
const numeroContaCorrente = 10;
// ok: sentinel-tsp-abreviacao
const codigoTarifaLegado = 1;
