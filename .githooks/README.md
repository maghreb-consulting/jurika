# Git hooks JURIKA

## Activation (une seule fois par clone)

```bash
git config core.hooksPath .githooks
```

À partir de là, tous les hooks de ce dossier sont exécutés automatiquement.

## Hooks disponibles

| Hook | Rôle | Outil requis |
|---|---|---|
| `pre-commit` | Bloque les commits contenant des secrets (gitleaks) | `gitleaks` |

## Installer les outils

```powershell
# Windows (Chocolatey)
choco install gitleaks

# macOS (Homebrew)
brew install gitleaks

# Linux
# voir https://github.com/gitleaks/gitleaks#installing
```

## Bypass urgence

```bash
git commit --no-verify -m "..."
```

**À utiliser uniquement** sur instruction explicite (release manager / security officer).
