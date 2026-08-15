# Vazio, e de propósito.
#
# O `isMinifyEnabled = false` do `build.gradle.kts` diz por quê: o R8 entra
# quando houver modelo de dados pra proteger e um APK de release conferido no
# aparelho. Encher este arquivo de regras preventivas agora seria escrever
# proteção pra classe que ainda não existe — e regras que ninguém testou dão a
# impressão de que o release foi pensado.
#
# Quando ele entrar, o que vai precisar de regra aqui, quase certo:
#
#   - os modelos de resposta da API (kotlinx.serialization por reflexão)
#   - o que o Media3 carrega por nome (extractors, renderers)
