// CI only (.github/workflows/osv-scanner.yml): locks every configuration so `dependencies --write-locks` writes
// gradle.lockfile files for the scanner to read. Nothing is committed, so updating a dependency never needs a relock.
allprojects {
    dependencyLocking { lockAllConfigurations() }
}
