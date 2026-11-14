# Nitro (launcher Android TV leve)

## 1) Gerar o APK (na nuvem, sem instalar nada)
1. Crie um repositório PÚBLICO no GitHub (ex.: `nitro-tv`) e suba todo este conteúdo (inclusive a pasta `.github`).
2. Aba **Actions** > "Build APK" roda sozinho (~3 min). Se não rodar: Run workflow.
3. Link fixo do APK:
   `https://github.com/SEU_USUARIO/nitro-tv/releases/download/latest/nitro.apk`

## 2) Instalar no Mi Stick (app Downloader)
1. Instale **Downloader** (AFTVnews) pela loja do Stick.
2. Configurações do Stick > Preferências do dispositivo > Segurança > **Fontes desconhecidas** > ative para o Downloader.
3. Abra o Downloader, digite o link do APK acima e baixe > Instalar.

## 3) Usar como tela inicial (opcional, via ADB)
Teste o Nitro antes (aparece em Apps). Depois, para ele ser a Home:
    adb connect IP_DO_STICK:5555
    adb shell pm disable-user --user 0 com.google.android.tvlauncher
(Google TV: com.google.android.apps.tv.launcherx). Reverter: `pm enable --user 0 <pacote>`.

Lista de canais: troque `M3U_URL` em `Channels.java`.
