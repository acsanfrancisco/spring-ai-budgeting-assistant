#!/usr/bin/env bash
# Double-click in Finder: macOS opens .command files in Terminal.
cd -- "$(dirname -- "$0")" || exit 1
bash ./scripts/iniciar.sh
result=$?
echo
# Keep the window open on success too, so the Swagger URL stays readable.
read -r -p 'Pressione Enter para fechar...' _
exit "$result"
