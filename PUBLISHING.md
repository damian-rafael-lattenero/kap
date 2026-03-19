# Publicar en Maven Central — Paso a Paso

Esta guia cubre como publicar `coroutines-applicatives` en Maven Central usando el
[Central Portal](https://central.sonatype.com/) y el plugin
[vanniktech/gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin)
(recomendado por la documentacion oficial de Kotlin).

---

## Paso 1: Crear cuenta en Maven Central

1. Ir a https://central.sonatype.com/ y registrarse (se puede usar cuenta de GitHub, Google o email).
2. Una vez logueado, ir a [Namespaces](https://central.sonatype.com/publishing/namespaces).

## Paso 2: Registrar y verificar el namespace

El namespace define el `groupId` de tus artefactos (en este caso `io.github.damian-rafael-lattenero`).

**Opcion A — Usando un dominio propio:**
1. Click en "Add Namespace" y escribir `io.github.damian-rafael-lattenero`.
2. Copiar la Verification Key que aparece.
3. Crear un registro DNS TXT en tu dominio con esa key.
4. Volver a Maven Central y click en "Verify Namespace".

**Opcion B — Usando GitHub (mas facil si no tenes dominio):**
1. Click en "Add Namespace" y escribir `io.github.TU-USUARIO-GITHUB`.
2. Copiar la Verification Key.
3. En GitHub, crear un repositorio publico con ese key como nombre.
4. Volver a Maven Central y click en "Verify Namespace".
5. Despues podes borrar el repo de verificacion.

> Si usas la opcion B, vas a necesitar cambiar el `group` en `build.gradle.kts`
> a `io.github.tu-usuario`.

## Paso 3: Generar clave GPG para firmar artefactos

Maven Central requiere que todos los artefactos esten firmados con GPG.

```bash
# Instalar GPG (macOS)
brew install gpg

# Generar la clave (seguir las instrucciones interactivas)
gpg --full-generate-key

# Ver las claves generadas
gpg --list-keys --keyid-format long
```

Vas a ver algo como:
```
pub   ed25519 2026-03-19 [SC]
      F175482952A225BFD4A07A713EE6B5F76620B385CE
uid   [ultimate] Tu Nombre <tu@email.com>
```

El ID largo es `F175482952A225BFD4A07A713EE6B5F76620B385CE`.
Los ultimos 8 caracteres (`20B385CE`) son el "key ID corto" que vas a usar despues.

```bash
# Subir la clave publica a un keyserver
gpg --keyserver keyserver.ubuntu.com --send-keys TU_KEY_ID_COMPLETO

# Exportar la clave privada a un archivo (te va a pedir el passphrase)
gpg --armor --export-secret-keys TU_KEY_ID_COMPLETO > key.gpg
```

> Guardar `key.gpg` en un lugar seguro. **NUNCA** commitear este archivo.

## Paso 4: Generar token de Maven Central

1. Ir a https://central.sonatype.com/account
2. Click en "Generate User Token".
3. Copiar el `username` y `password` que genera (no se pueden ver de nuevo despues).

## Paso 5: Configurar credenciales locales

Crear o editar `~/.gradle/gradle.properties`:

```properties
mavenCentralUsername=EL_USERNAME_DEL_TOKEN
mavenCentralPassword=EL_PASSWORD_DEL_TOKEN

# GPG signing — ultimos 8 chars del key ID
signing.gnupg.keyName=20B385CE
signing.gnupg.passphrase=TU_PASSPHRASE_GPG
```

> Este archivo **NO** se commitea. Es local a tu maquina.

**Alternativa: firmar con clave en memoria** (util para CI):

```properties
mavenCentralUsername=EL_USERNAME_DEL_TOKEN
mavenCentralPassword=EL_PASSWORD_DEL_TOKEN

signingInMemoryKeyId=20B385CE
signingInMemoryKeyPassword=TU_PASSPHRASE_GPG
signingInMemoryKey=CONTENIDO_COMPLETO_DE_key.gpg
```

## Paso 6: Publicar

```bash
# Publicar y esperar aprobacion manual en el portal
./gradlew publishToMavenCentral

# O publicar y liberar automaticamente (sin intervencion manual)
./gradlew publishAndReleaseToMavenCentral
```

Si usas `publishToMavenCentral`:
1. Ir a https://central.sonatype.com/publishing/deployments
2. Esperar a que el estado sea "Validated".
3. Click en "Publish" para liberar.

Los artefactos aparecen en Maven Central en ~10-30 minutos despues de liberar.

## Paso 7: Verificar la publicacion

- **Buscar:** https://search.maven.org/search?q=g:io.github.damian-rafael-lattenero
- **Directo:** https://repo1.maven.org/maven2/io/github/damian-rafael-lattenero/kap/

Los usuarios pueden depender de la libreria asi:

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.damian-rafael-lattenero:kap:1.0.0")
}
```

---

## (Opcional) Publicar automaticamente con GitHub Actions

Agregar estos secrets en GitHub -> Settings -> Secrets and variables -> Actions:

| Secret | Valor |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Username del token |
| `MAVEN_CENTRAL_PASSWORD` | Password del token |
| `SIGNING_KEY_ID` | Ultimos 8 chars del key ID GPG |
| `SIGNING_PASSWORD` | Passphrase GPG |
| `GPG_KEY_CONTENTS` | Contenido completo de `key.gpg` |

Crear `.github/workflows/publish.yml`:

```yaml
name: Publish to Maven Central
on:
  release:
    types: [released, prereleased]

jobs:
  publish:
    runs-on: macOS-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: 21

      - name: Publish to Maven Central
        run: ./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
        env:
          ORG_GRADLE_PROJECT_mavenCentralUsername: ${{ secrets.MAVEN_CENTRAL_USERNAME }}
          ORG_GRADLE_PROJECT_mavenCentralPassword: ${{ secrets.MAVEN_CENTRAL_PASSWORD }}
          ORG_GRADLE_PROJECT_signingInMemoryKeyId: ${{ secrets.SIGNING_KEY_ID }}
          ORG_GRADLE_PROJECT_signingInMemoryKeyPassword: ${{ secrets.SIGNING_PASSWORD }}
          ORG_GRADLE_PROJECT_signingInMemoryKey: ${{ secrets.GPG_KEY_CONTENTS }}
```

Con esto, cada vez que crees un Release en GitHub, se publica automaticamente.

---

## Troubleshooting

| Problema | Solucion |
|---|---|
| 401 Unauthorized | Verificar `mavenCentralUsername`/`mavenCentralPassword` en `~/.gradle/gradle.properties` |
| 403 Forbidden | Tu namespace no esta verificado. Verificarlo en https://central.sonatype.com/publishing/namespaces |
| Firma falla | Verificar que `gpg` esta instalado y `signing.gnupg.keyName` es correcto (`gpg --list-secret-keys`) |
| Deployment rechazado | Revisar en el portal que tiene: `.jar`, `-javadoc.jar`, `-sources.jar`, `.pom`, y firmas `.asc` |
| Dokka falla | Correr `./gradlew dokkaHtml` solo para ver el error especifico |
