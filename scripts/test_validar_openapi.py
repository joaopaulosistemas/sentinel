import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location("validar", pathlib.Path(__file__).with_name("validar-openapi.py"))
validar = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validar)

OK = {
    "openapi": "3.1.0",
    "info": {"title": "T", "version": "1"},
    "paths": {"/a": {"get": {"operationId": "a", "responses": {"200": {"content": {"application/json": {"schema": {"$ref": "#/components/schemas/X"}}}}}}}},
    "components": {"schemas": {"X": {"type": "object"}}},
}


def com(**mudancas):
    doc = {**OK, **mudancas}
    return validar.problemas(doc)


class ValidarOpenApi(unittest.TestCase):
    def test_documento_completo_nao_tem_problema(self):
        self.assertEqual([], validar.problemas(OK))
        self.assertEqual(1, validar.contar(OK))

    def test_versao_fora_da_3x(self):
        self.assertTrue(any("3.x" in p for p in com(openapi="2.0")))

    def test_sem_info_ou_sem_rotas(self):
        self.assertTrue(any("info" in p for p in com(info={})))
        self.assertTrue(any("não tem rotas" in p for p in com(paths={})))

    def test_operacao_sem_respostas(self):
        self.assertTrue(any("sem respostas" in p for p in com(paths={"/a": {"get": {"responses": {}}}})))

    def test_operation_id_repetido(self):
        dup = {"/a": {"get": {"operationId": "x", "responses": {"200": {}}}}, "/b": {"get": {"operationId": "x", "responses": {"200": {}}}}}
        self.assertTrue(any("operationId repetido" in p for p in com(paths=dup)))

    def test_referencia_quebrada(self):
        self.assertTrue(any("referência quebrada: #/components/schemas/X" in p for p in com(components={"schemas": {}})))


if __name__ == "__main__":
    unittest.main()
