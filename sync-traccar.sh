#!/bin/bash
echo "🔄 Sincronizando com Traccar oficial..."

# Buscar atualizações
git fetch upstream

# Verificar se há novidades
UPDATES=$(git log --oneline upstream/master ^HEAD | wc -l)
if [ $UPDATES -eq 0 ]; then
    echo "✅ Já está atualizado!"
    exit 0
fi

echo "📦 Encontradas $UPDATES atualizações"

# Fazer merge
git merge upstream/master

if [ $? -eq 0 ]; then
    echo "✅ Sincronização concluída!"
    echo "🔨 Compilando para testar..."
    ./gradlew build -x test
    
    if [ $? -eq 0 ]; then
        echo "✅ Compilação OK!"
        echo "📤 Enviando para seu repositório..."
        git push origin HEAD
    else
        echo "❌ Erro na compilação - verificar código"
    fi
else
    echo "⚠️  Conflitos encontrados. Resolva manualmente e execute:"
    echo "   git add ."
    echo "   git commit"
    echo "   git push origin HEAD"
fi