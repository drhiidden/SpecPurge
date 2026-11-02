# Casos declarativos, esquema y modo diferencial

SpecSurge tiene tres modos de uso complementarios.

| Modo | Cuándo | Comando base |
|------|--------|--------------|
| **Generado** | Smoke de todos los endpoints del OpenAPI (sin 5xx) | `--spec …` |
| **Casos** | Peticiones con IDs reales y aserciones de negocio | `--cases casos.yml` |
| **Diferencial** | Comparar un candidato contra un baseline (regresión) | `--compare-base-url …` |

Se combinan: `--cases` + `--validate-schema` + `--compare-base-url` en una sola ejecución.

## Fichero de casos (YAML)

```yaml
vars:                                  # variables; --param las sobrescribe
  accountId: "${env:MY_ACCOUNT_ID}"      # ${env:NOMBRE} lee del entorno
defaults:
  schema: true                         # valida la respuesta contra el OpenAPI (requiere --spec)
  headers: { Accept-Language: es }
cases:
  - name: pedido existente
    operationId: getOrder              # enlaza el esquema de respuesta (o method+path idénticos al OpenAPI)
    method: GET                        # por defecto GET
    path: /v1/accounts/{accountId}/orders/{orderId}
    pathParams: { orderId: "${orderId}" }
    query: { color: "${color}" }
    headers: {}
    expect:
      status: 200                      # entero o lista [200, 204]
      schema: true                     # sobrescribe el valor por defecto
      jsonPath:
        - { path: "$.locked", equals: true }
        - { path: "$.versions[?(@.default==true)]", count: 1 }
    ignore: [generatedAt]              # campos ignorados en el modo diferencial
```

- Un caso cuyas variables no se resuelven (o con valores `CHANGE_ME…`) **se omite** y se lista; no falla la ejecución.
- Operadores de `jsonPath`: `exists`, `notExists`, `equals`, `notEquals`, `count`, `minCount`, `maxCount`, `notEmpty`, `matches`, `contains`.
- Las expresiones son [JsonPath](https://github.com/json-path/JsonPath) (filtros incluidos).
- Con `--read-only` los casos con método distinto de GET/HEAD se omiten.

## Validación contra el esquema (`--validate-schema`)

Comprueba cada respuesta 2xx contra el esquema de respuesta del OpenAPI: tipo, propiedades requeridas, `enum`, `items` y `oneOf/anyOf`. Es tolerante a propósito: ignora `additionalProperties`, formatos y `null` en propiedades no requeridas. Sirve para detectar deriva del contrato.

## Modo diferencial (`--compare-base-url`)

Ejecuta cada petición contra `--base-url` (candidato) y contra `--compare-base-url` (baseline) y lista las diferencias por ruta (`$.a.b[2]: baseline=… candidato=…`), además del estado HTTP. Las aserciones y el esquema se aplican al candidato. Por defecto las diferencias son informativas (un fix las produce a propósito); `--fail-on-diff` las convierte en fallo. Usa `ignore:` para campos volátiles (marcas de tiempo, ids generados).

## Seguridad

- El token **solo** se lee de una variable de entorno (`--bearer-env`); nunca va por argumento ni aparece en informes (se enmascara) ni en el `curl` de reproducción (aparece como `$VARIABLE`).
- Se lee la caducidad (`exp`) del JWT sin verificar firma: un token caducado aborta con un mensaje claro en lugar de producir un 401 confuso.
- Con token o escrituras, un host que no sea local exige `--allow-host <host>`.
- Con token, `--read-only` es obligatorio salvo `--allow-writes`.
- `--max-requests` (500 por defecto) y `--delay-ms` acotan el impacto.

## Salidas

`--format html,junit,json` (por defecto `html`). Cada fallo del informe HTML incluye violaciones, diferencias y una línea `curl` para reproducirlo.
