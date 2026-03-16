# Publishing to Maven Central

This guide covers publishing `coroutines-applicatives` to Maven Central via Sonatype OSSRH.

## Prerequisites

### 1. Sonatype OSSRH Account

1. Create an account at https://issues.sonatype.org/secure/Signup!default.jspa
2. Create a New Project ticket requesting access to the `org.applicative.coroutines` group ID.
   - Issue type: **Community Support - Open Source Project Repository Hosting**
   - Group Id: `org.applicative.coroutines`
   - Project URL: `https://github.com/dlattenero/coroutines-applicatives`
   - SCM URL: `https://github.com/dlattenero/coroutines-applicatives.git`
3. Sonatype will ask you to verify domain ownership (e.g., via a DNS TXT record or a GitHub repo). Follow their instructions.
4. Wait for the ticket to be resolved before attempting to publish.

### 2. GPG Signing Key

Maven Central requires all artifacts to be GPG-signed.

```bash
# Generate a key if you don't have one
gpg --full-generate-key

# List your keys to find the key ID (last 8+ hex characters of the fingerprint)
gpg --list-keys --keyid-format long

# Upload your public key to a keyserver so Maven Central can verify signatures
gpg --keyserver keyserver.ubuntu.com --send-keys <YOUR_KEY_ID>
```

### 3. Namespace Claim (Central Portal)

If using the newer Sonatype Central Portal (https://central.sonatype.com/) instead of the legacy OSSRH, you need to verify namespace ownership there. The build is configured for the `s01.oss.sonatype.org` legacy OSSRH endpoints. If Sonatype has migrated your account to the Central Portal, update the repository URLs in `build.gradle.kts` accordingly.

## Configure Credentials

Add the following to your **`~/.gradle/gradle.properties`** (create the file if it does not exist):

```properties
# Sonatype OSSRH credentials (from https://s01.oss.sonatype.org)
ossrhUsername=your-sonatype-username
ossrhPassword=your-sonatype-password

# GPG signing
signing.gnupg.keyName=YOUR_GPG_KEY_ID
signing.gnupg.passphrase=your-gpg-passphrase
```

Replace the placeholder values:

| Property                    | Value                                                        |
|-----------------------------|--------------------------------------------------------------|
| `ossrhUsername`             | Your Sonatype JIRA / OSSRH username                          |
| `ossrhPassword`            | Your Sonatype JIRA / OSSRH password (or a generated token)   |
| `signing.gnupg.keyName`    | Last 8+ hex chars of your GPG key fingerprint                |
| `signing.gnupg.passphrase` | Passphrase for your GPG key                                  |

**Do not commit `~/.gradle/gradle.properties` to version control.**

## Publishing

### Publish to Sonatype Staging

```bash
./gradlew publishAllPublicationsToSonatypeRepository
```

This will:
1. Build all Kotlin multiplatform artifacts (JVM, JS, native targets).
2. Generate Dokka HTML documentation and package it as the javadoc JAR.
3. Sign all artifacts with your GPG key.
4. Upload everything to a Sonatype staging repository.

If the version in `build.gradle.kts` ends with `-SNAPSHOT`, artifacts go to the snapshots repository instead of staging.

### Publishing a Snapshot

To publish a snapshot, change the version in `build.gradle.kts`:

```kotlin
version = "1.0.0-SNAPSHOT"
```

Then run:

```bash
./gradlew publishAllPublicationsToSonatypeRepository
```

Snapshots are available immediately at:
`https://s01.oss.sonatype.org/content/repositories/snapshots/`

### Release from Staging to Maven Central

After publishing a release (non-SNAPSHOT) version:

1. Go to https://s01.oss.sonatype.org/ and log in.
2. Click **Staging Repositories** in the left sidebar.
3. Find your staging repository (named something like `orgapplicativecoroutines-XXXX`).
4. Inspect the contents to verify all expected artifacts are present:
   - `.jar` (main artifact per target)
   - `-javadoc.jar`
   - `-sources.jar`
   - `.pom`
   - `.asc` signature files for each of the above
5. Click **Close** to trigger Sonatype's validation rules. Wait for the close operation to complete.
   - If it fails, check the **Activity** tab for details (common issues: missing javadoc JAR, invalid POM, unsigned artifacts).
6. Once closed successfully, click **Release** to promote the artifacts to Maven Central.
7. Drop the staging repository after release (or check the "Automatically Drop" option when releasing).

Artifacts typically appear on Maven Central within 10-30 minutes after release, though it may take up to 2 hours for full sync.

## Verifying the Publication

After releasing, confirm the artifacts are available:

1. **Maven Central Search**: https://search.maven.org/search?q=g:org.applicative.coroutines
2. **Direct URL**: https://repo1.maven.org/maven2/org/applicative/coroutines/coroutines-applicatives/1.0.0/

Users can then depend on the library:

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.applicative.coroutines:coroutines-applicatives:1.0.0")
}
```

## Troubleshooting

### "401 Unauthorized" during upload
- Verify `ossrhUsername` and `ossrhPassword` in `~/.gradle/gradle.properties`.
- Ensure your Sonatype JIRA ticket for the group ID has been resolved.

### "403 Forbidden" during upload
- Your account may not have deploy permissions for the group ID yet. Check the JIRA ticket status.

### Signing fails
- Ensure `gpg` is installed and on your PATH.
- Verify `signing.gnupg.keyName` matches an available secret key: `gpg --list-secret-keys`.
- Ensure the passphrase is correct.

### Staging repository close fails
- Check the Activity tab in the Sonatype UI for the specific rule that failed.
- Common failures: missing `-javadoc.jar`, missing `-sources.jar`, POM validation errors, or missing GPG signatures.

### "Could not find or load main class" for Dokka
- Ensure the Dokka plugin version in `build.gradle.kts` is compatible with your Kotlin version.
- Run `./gradlew dokkaHtml` independently to check for Dokka errors.
