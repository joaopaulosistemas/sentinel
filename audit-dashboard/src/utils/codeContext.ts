interface ICodeLine {
  n: number;
  text: string;
  hit: boolean;
}

/** Linhas do trecho com o número real no arquivo e a marca da linha do achado; vazio se não houver contexto. */
export function codeLines(context: string | null | undefined, start: number | null | undefined, hitLine: number | null | undefined): ICodeLine[] {
  if (!context || !start) return [];
  if (start < 1) return [];
  return context.split('\n').map((text, i) => ({ n: start + i, text, hit: start + i === hitLine }));
}

/** "arquivo:linha" para legenda e nome acessível do bloco. */
export function codeLocation(filePath: string, line: number | null | undefined): string {
  return line ? `${filePath}:${line}` : filePath;
}
